---
id: 4
title: File system playlist support
status: open
branch: none yet (server / web, not the Android app)
upstream: not reported yet
created: 2026-10-22
---

# File system playlist support

## What I want

My playlists are files (`.m3u8`, `.pls`, …) in a folder, `M:\audio\playlists`. I keep them there because Jellyfin's own playlists are hard to maintain. In Jellyfin they should:

1. Show up under **Playlists** like Jellyfin's own playlists, without going through the folder view.
2. Be fully manageable from Jellyfin: create, rename, add/remove/reorder tracks, delete. Each change writes back to the file.

**Not wanted: Jellyfin's internal playlists** (the ones the server keeps in its SQLite database). The files are the only source of truth. A **New Playlist** (and *Add to playlist*) should make or change a file in the playlist folder, never an internal playlist. Ideally internal playlists can be hidden or switched off altogether.

## Today

- The folder is added as a library: **My Playlists**, type *Music*.
- The playlists *are* recognized: in the folder view (*Display a folder view to show plain media folders*), each one shows as a playlist with a 4-cover collage (`2026-10-18 unexpect…`, `2026-08-25 afro crazy`, … 41 in all).
- The music library's **Playlists** tab says *Nothing here*.
- So the only way to reach them is: enable the folder view, open *Folders*, then browse to the playlist folder. That's cumbersome.

## What the code says (first look, 2026-10-22)

- Server, `Emby.Server.Implementations/Library/Resolvers/PlaylistResolver.cs`: a file with a playlist extension (`Playlist.SupportedExtensions`) in a library whose collection type is *music* (or mixed) resolves as a `Playlist`. A folder named `… [playlist]`, or one that holds a `playlist.xml`, also counts. That's why they show in the folder view.
- Web, `src/apps/legacy/controllers/music/musicplaylists.js`: the Playlists tab asks `/Items` for `IncludeItemTypes=Playlist, Recursive=true` with **no parent**. So the file playlists are dropped on the server side, by how `/Items` handles a playlist-only query, by the playlist visibility rules (owner/shares; file playlists have no owner), or by both. **To check next.**
- Editing: the server writes `.m3u`-type playlists back when a playlist changes (`PlaylistManager` saves to the file for file-based playlists). Whether add/remove/reorder/delete all go back to the file, and whether *create* can make a new file in the library folder, is still to be checked.

## Already upstream (searched 2026-10-22)

The request exists; nobody has built it.

- **Feature request [features.jellyfin.org #1769](https://features.jellyfin.org/posts/1769) "Create/Edit M3U Playlists for Music"** (2022, open, 17 votes). It is almost exactly this: a per-library setting for the folder new `.m3u` playlists go in, and add/remove writing back to the file with relative paths, so they work in other players. **Vote / comment here** rather than file a new one.
- **Discussion [jellyfin#11744](https://github.com/orgs/jellyfin/discussions/11744) "Allow admins to edit playlists stored in the filesystem"** (2024, 22 upvotes, last comment 2025-12).
- **[jellyfin#11741](https://github.com/jellyfin/jellyfin/issues/11741)** (closed, not planned): editing `.m3u` playlists **worked in 10.8** and was removed in 10.9. 10.9 added playlist owners and permissions; file playlists became global with nobody allowed to edit them, because "Jellyfin usually does not allow direct file system modification". A maintainer (Shadowghost) suggested admin editing as an acceptable way back. That is the opening for a PR.
- [jellyfin#11152](https://github.com/jellyfin/jellyfin/issues/11152) (closed): since 10.9 a music library's Playlists tab shows playlists *from that library's files*. Jellyfin-made ones show only in the global Playlists view.
- [features #2603](https://features.jellyfin.org/posts/2603) "Complete playlist overhaul" (declined for bundling many requests in one).

### Possibly a plain bug behind "Nothing here"

- **[jellyfin#18169](https://github.com/jellyfin/jellyfin/issues/18169)**: on **12.1**, `.m3u`/`.m3u8` files in a music library become playlists with **zero tracks**, which are hidden. When no playlist is left, the whole Playlists view disappears. **Fixed in 12.2** (2026-10-05, PR #18104).
- My nav bar has no *Playlists* view either, which fits. The collages in the folder view could be images left from before. **Check the server version; if it is 12.1, update to 12.2 and rescan the playlist library** before anything else.
- Otherwise the cause may be which tab I looked at. Per #11152, a library's Playlists tab shows the playlists in *that* library. Mine live in the separate **My Playlists** library, so the **Music** library's tab would be empty.

## Where it would be fixed

The Jellyfin **server** (`jellyfin/jellyfin`), and maybe **jellyfin-web**. Not the Android app: it shows the server's web client. A local fix needs a server clone and build (.NET), tried against a copy of the server's config, never the live one.

## Next steps

0. Server version: if 12.1, update to 12.2 and rescan (#18169). Then look at **My Playlists → Playlists** and the global Playlists view again.
1. If they are still missing, find why `/Items?IncludeItemTypes=Playlist` leaves out file playlists: query the server directly, then read `ItemsController` and `Playlist.IsVisible`.
2. Find where *New Playlist* decides to make an internal playlist (`PlaylistManager.CreatePlaylist`, which always makes an internal one). A setting could point it at a library folder and a format (`.m3u8`) instead.
3. Try the existing edit paths on one file playlist (add a track, remove one, reorder) and see what changes in the `.m3u8` file.
4. Upstream searched (see above). A fix would follow #11741 / #11744: admins edit file playlists, written back to the file, plus #1769: a folder for new ones.
