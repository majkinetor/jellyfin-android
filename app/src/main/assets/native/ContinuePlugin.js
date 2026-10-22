/**
 * Remembers what is playing so it can continue after the app restarts, and keeps a history of
 * what was started (album, playlist, track, ...) for the app's "Recently played" screen.
 * State and history are stored by the app (NativeInterface), not in the page.
 */
export class ContinuePlugin {
    constructor({ events, playbackManager, globalize }) {
        window['ContinuePlugin'] = this;

        this.name = 'ContinuePlugin';

        this.events = events;
        this.playbackManager = playbackManager;
        this.globalize = globalize;
        this.restoring = false;
        this.lastSaved = '';
        // The history entry the current queue was started from; its position is kept with it
        this.currentEntry = null;

        this.wrapPlay();
        events.on(playbackManager, 'playbackstart', () => this.save());
        setInterval(() => this.save(), 5000);
        this.addMenuLink();
        this.restoreOnStart();
    }

    log(...args) {
        console.log('[ContinuePlugin]', ...args);
    }

    // History: every play() the web client starts is one entry
    wrapPlay() {
        const playbackManager = this.playbackManager;
        const play = playbackManager.play;
        playbackManager.play = (options) => {
            if (!this.restoring) {
                this.record(options).catch(e => this.log('record failed', e));
            }
            return play.call(playbackManager, options);
        };
    }

    async record(options) {
        let items = options.items;
        if (!items && options.ids && options.ids.length) {
            const apiClient = window.ApiClient;
            const item = await apiClient.getItem(apiClient.getCurrentUserId(), options.ids[0]);
            items = [item];
        }
        if (!items || !items.length) return;

        const entry = this.entryFor(items, options.startIndex || 0);
        this.currentEntry = entry && { id: entry.id, kind: entry.kind };
        if (entry) {
            window.NativeInterface.recordPlayed(JSON.stringify(entry));
        }
    }

    entryFor(items, startIndex) {
        const first = items[0];
        const imageId = (item) => (item.ImageTags && item.ImageTags.Primary) ? item.Id : (item.AlbumId || item.Id);
        const base = (item, kind) => ({
            id: item.Id,
            kind,
            name: item.Name || '',
            subtitle: item.AlbumArtist || (item.Artists || []).join(', ') || '',
            imageId: imageId(item),
            serverId: item.ServerId || (window.ApiClient && window.ApiClient.serverId()) || ''
        });

        if (items.length === 1) {
            switch (first.Type) {
                case 'MusicAlbum': return base(first, 'album');
                case 'Playlist': return base(first, 'playlist');
                case 'Audio': return base(first, 'track');
                case 'MusicArtist': return { ...base(first, 'artist'), subtitle: '' };
                case 'MusicGenre': return { ...base(first, 'genre'), subtitle: '' };
                case 'Folder':
                case 'CollectionFolder': return { ...base(first, 'folder'), subtitle: '' };
                default: return null; // video and the rest are not music history
            }
        }

        if (!items.every(item => item.Type === 'Audio')) return null;
        // A track list from one album (the album page): the album is what was played
        const albumId = first.AlbumId;
        if (albumId && items.every(item => item.AlbumId === albumId)) {
            return {
                id: albumId,
                kind: 'album',
                name: first.Album || '',
                subtitle: first.AlbumArtist || '',
                imageId: albumId,
                serverId: first.ServerId || ''
            };
        }
        // Any other list: the track it started from
        return base(items[Math.min(startIndex, items.length - 1)], 'track');
    }

    // Continue: the queue, the current track and the position, saved while music plays
    async save() {
        const playbackManager = this.playbackManager;
        if (this.restoring || !playbackManager.isPlaying()) return;
        const player = playbackManager.getCurrentPlayer();
        if (!player || !player.isLocalPlayer) return;
        const current = playbackManager.currentItem(player);
        if (!current || current.MediaType !== 'Audio') return;

        const playlist = await playbackManager.getPlaylist(player);
        const ids = playlist.map(item => item.Id).slice(0, 1000);
        const index = playbackManager.getCurrentPlaylistIndex(player);
        if (!ids.length || index < 0) return;

        // A single track entry only owns the position while that track plays
        const entry = this.currentEntry;
        const ownsQueue = entry && (entry.kind !== 'track' || current.Id === entry.id);
        const state = {
            serverId: current.ServerId || window.ApiClient.serverId(),
            ids,
            index: Math.min(index, ids.length - 1),
            positionMs: Math.floor(playbackManager.currentTime(player) || 0),
            durationMs: current.RunTimeTicks ? Math.floor(current.RunTimeTicks / 10000) : 0,
            entryId: ownsQueue ? entry.id : null,
            repeat: playbackManager.getRepeatMode(player),
            name: current.Name || '',
            subtitle: current.AlbumArtist || (current.Artists || []).join(', ') || '',
            album: current.Album || '',
            imageId: (current.ImageTags && current.ImageTags.Primary) ? current.Id : (current.AlbumId || current.Id),
            savedAt: Date.now()
        };
        // Position changes every time; only skip writes when nothing moved at all
        const json = JSON.stringify(state);
        const key = json.replace(/"savedAt":\d+/, '');
        if (key === this.lastSaved) return;
        this.lastSaved = key;
        window.NativeInterface.saveContinueState(json);
    }

    async restoreOnStart() {
        if (!window.NativeInterface.getContinueOnReopen()) return;
        // Wait for a signed-in client; give up after a minute
        for (let i = 0; i < 120; i++) {
            const apiClient = window.ApiClient;
            if (apiClient && apiClient.getCurrentUserId() && apiClient.accessToken()) {
                if (!this.playbackManager.isPlaying()) {
                    this.log('continuing on reopen');
                    await this.resume(false);
                }
                return;
            }
            await new Promise(resolve => setTimeout(resolve, 500));
        }
    }

    /**
     * Where to start from a saved spot. A spot in the last seconds of a track means the track was done:
     * starting there asks the server for a stream piece past the end (HTTP 400). Start the next track
     * instead, or from the top when it was the last one.
     */
    startPoint(index, positionMs, durationMs, count) {
        if (durationMs && positionMs > durationMs - 5000) {
            return index + 1 < count ? { index: index + 1, positionMs: 0 } : { index: 0, positionMs: 0 };
        }
        return { index, positionMs };
    }

    /** Plays the saved queue at its track and position. Called by the app too. */
    async resume(fullscreen = false) {
        const json = window.NativeInterface.getContinueState();
        if (!json) return;
        const state = JSON.parse(json);
        const apiClient = window.ApiClient;
        if (!apiClient || state.serverId !== apiClient.serverId()) {
            this.log('saved queue belongs to another server');
            return;
        }
        const start = this.startPoint(state.index, state.positionMs || 0, state.durationMs, state.ids.length);

        // Started from an album, playlist or track: play it the way the web client does
        if (state.entryId) {
            this.log('resuming', state.entryId, 'at', start.index, start.positionMs, 'ms');
            await this.playEntry(state.entryId, start.index, start.positionMs, fullscreen);
            if (state.repeat) this.playbackManager.setRepeatMode(state.repeat);
            return;
        }

        this.restoring = true;
        try {
            // Fetch the items in chunks (a long Ids list would not fit in one URL), then put them back in order
            const byId = new Map();
            for (let i = 0; i < state.ids.length; i += 100) {
                const chunk = [...new Set(state.ids.slice(i, i + 100))];
                const result = await apiClient.getItems(apiClient.getCurrentUserId(), {
                    Ids: chunk.join(','),
                    Fields: 'Chapters,MediaSources,Trickplay',
                    ExcludeLocationTypes: 'Virtual',
                    EnableTotalRecordCount: false
                });
                for (const item of result.Items) byId.set(item.Id, item);
            }
            const items = [];
            let startIndex = 0;
            state.ids.forEach((id, i) => {
                const item = byId.get(id);
                if (!item) return;
                if (i === start.index) startIndex = items.length;
                items.push(item);
            });
            if (!items.length) return;

            this.log('resuming', items.length, 'items at', startIndex, start.positionMs, 'ms');
            await this.playbackManager.play({
                items,
                startIndex,
                startPositionTicks: start.positionMs * 10000,
                fullscreen
            });
            if (state.repeat) this.playbackManager.setRepeatMode(state.repeat);
        } catch (e) {
            this.log('resume failed', e);
        } finally {
            this.restoring = false;
        }
    }

    /**
     * Plays a history entry (by item ID) from a track index and position.
     * Called by the app's "Recently played" screen, with the entry's saved spot.
     */
    async playEntry(id, index = 0, positionMs = 0, fullscreen = true) {
        const apiClient = window.ApiClient;
        try {
            await this.playbackManager.play({
                ids: [id],
                serverId: apiClient.serverId(),
                startIndex: index || 0,
                startPositionTicks: (positionMs || 0) * 10000,
                fullscreen
            });
        } catch (e) {
            this.log('play failed', e);
        }
    }

    /** Plays a history entry from its saved spot, skipping a finished track. */
    playEntryFrom(id, index, positionMs, durationMs, count) {
        const start = this.startPoint(index, positionMs, durationMs, count || index + 1);
        this.log('playing', id, 'at', start.index, start.positionMs, 'ms');
        return this.playEntry(id, start.index, start.positionMs);
    }

    // "Recently played" in the side menu, for both layouts of the web client
    addMenuLink() {
        const label = this.globalize.translate('HeaderRecentlyPlayed');
        const open = (e) => {
            e.preventDefault();
            e.stopPropagation();
            window.NativeInterface.openRecentlyPlayed();
        };

        // Legacy layout: the drawer has a container for custom links
        const addLegacy = () => {
            const container = document.querySelector('.customMenuOptions');
            if (!container || container.querySelector('.lnkRecentlyPlayed')) return;
            const link = document.createElement('a');
            link.setAttribute('is', 'emby-linkbutton');
            link.className = 'navMenuOption lnkMediaFolder lnkRecentlyPlayed';
            link.href = '#';
            link.innerHTML = '<span class="material-icons navMenuOptionIcon history" aria-hidden="true"></span>'
                + '<span class="navMenuOptionText"></span>';
            link.querySelector('.navMenuOptionText').textContent = label;
            link.addEventListener('click', open);
            container.insertBefore(link, container.firstChild);
        };

        // Modern layout (MUI drawer): a copy of the Favorites entry, placed after it
        const addModern = () => {
            const favorites = document.querySelector('a[href$="/home?tab=1"]');
            const item = favorites && favorites.closest('li');
            if (!item || !item.parentElement || item.parentElement.querySelector('.lnkRecentlyPlayed')) return;
            const copy = item.cloneNode(true);
            copy.classList.add('lnkRecentlyPlayed');
            const link = copy.querySelector('a');
            link.removeAttribute('href');
            link.classList.remove('Mui-selected');
            link.addEventListener('click', open);
            const text = copy.querySelector('.MuiListItemText-primary') || copy.querySelector('.MuiTypography-root');
            if (text) text.textContent = label;
            const icon = copy.querySelector('.MuiListItemIcon-root');
            if (icon) icon.innerHTML = '<span class="material-icons" aria-hidden="true">history</span>';
            item.after(copy);
        };

        const add = () => {
            addLegacy();
            addModern();
        };
        new MutationObserver(add).observe(document.body, { childList: true, subtree: true });
        add();
    }
}
