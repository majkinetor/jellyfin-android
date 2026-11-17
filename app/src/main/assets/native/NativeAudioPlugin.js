/**
 * Plays the web client's music through the app's native player (ExoPlayer).
 *
 * The web client keeps the queue, the UI and the reporting to the server: it hands this player one
 * stream URL at a time, the same way it uses its own <audio> player. The native side reports back
 * through onNativeEvent.
 */
export class NativeAudioPlugin {
    constructor({ events, appHost }) {
        window['NativeAudioPlugin'] = this;

        this.events = events;
        this.appHost = appHost;

        this.name = 'Native Audio Player';
        this.type = 'mediaplayer';
        this.id = 'nativeaudioplayer';

        // Ahead of the web client's audio player (priority 1)
        this.priority = 0;
        this.isLocalPlayer = true;

        this._nativePlayer = window['NativeAudioPlayer'];
        this._enabled = this._nativePlayer.isEnabled();
        this._currentSrc = null;
        this._currentTime = 0;
        this._duration = null;
        this._paused = true;
    }

    canPlayMediaType(mediaType) {
        return this._enabled && (mediaType || '').toLowerCase() === 'audio';
    }

    canPlayItem() {
        return this._enabled;
    }

    getDeviceProfile(item) {
        // The same profile as the web client's audio player: everything it plays, ExoPlayer plays
        return this.appHost.getDeviceProfile(item);
    }

    async play(options) {
        const url = options.url;
        const startMs = Math.floor((options.playerStartPositionTicks || 0) / 10000);
        const mediaSource = options.mediaSource || {};
        const isHls = mediaSource.TranscodingSubProtocol === 'hls' || url.includes('.m3u8');

        this._currentSrc = url;
        this._currentTime = startMs;
        this._duration = mediaSource.RunTimeTicks ? mediaSource.RunTimeTicks / 10000 : null;
        this._paused = false;
        this._nativePlayer.play(url, startMs, isHls);
    }

    onNativeEvent(event, ...args) {
        switch (event) {
            case 'timeupdate':
                this._currentTime = args[0];
                if (args[1] > 0) this._duration = args[1];
                this.events.trigger(this, 'timeupdate');
                break;
            case 'playing':
                this.events.trigger(this, 'playing');
                break;
            case 'pause':
                // Also a pause by the system, like for a call
                this._paused = true;
                this.events.trigger(this, 'pause');
                break;
            case 'unpause':
                this._paused = false;
                this.events.trigger(this, 'unpause');
                break;
            case 'ended':
                this._onStopped();
                break;
            case 'error':
                this.events.trigger(this, 'error', [{ type: args[0] }]);
                break;
        }
    }

    _onStopped() {
        const src = this._currentSrc;
        this._currentSrc = null;
        this._paused = true;
        this.events.trigger(this, 'stopped', [{ src }]);
    }

    async stop(destroyPlayer) {
        this._nativePlayer.stop();
        if (this._currentSrc) this._onStopped();
        if (destroyPlayer) this.destroy();
    }

    destroy() {
        this._nativePlayer.destroy();
    }

    currentSrc() {
        return this._currentSrc;
    }

    currentTime(ms) {
        if (ms != null) {
            this._currentTime = ms;
            this._nativePlayer.seek(ms);
            return;
        }
        return this._currentTime;
    }

    duration() {
        return this._duration;
    }

    seekable() {
        return Boolean(this._duration);
    }

    paused() {
        return this._paused;
    }

    pause() {
        this._paused = true;
        // The native side reports the pause back
        this._nativePlayer.pause();
    }

    resume() {
        this.unpause();
    }

    unpause() {
        this._paused = false;
        this._nativePlayer.resume();
    }

    // The system volume controls the native player
    getVolume() {
        return 100;
    }

    setVolume() {}

    volumeUp() {}

    volumeDown() {}

    isMuted() {
        return false;
    }

    setMute() {}

    supports() {
        return false;
    }
}
