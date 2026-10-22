---
id: 5
title: Continue where I left off; recently played, newest first
status: open
branch: none yet
upstream: not reported yet
created: 2026-10-22
---

# Continue where I left off; recently played, newest first

Comes before #4.

## What I want

1. **Restarting the app keeps what I was listening to.** Today a restart (by me, or Android killing the app) loses the queue, the track and the position, and I have to find the album or playlist again.
2. **Option: "On reopen, continue playing the last thing".** When on, the app starts playing that queue at the saved track and position as soon as it opens. When off, the app only offers it (it is there, one tap to play).
3. **A recently played list, newest first by default.** Whatever I played last (album, playlist, track) at the top, without picking a sort order every time.

## Today

- Music plays in the web client inside the app (the WebView). The queue exists only in the page's memory (`playbackManager`), so closing the app or Android killing the process drops it. Nothing saves it.
- The server knows when each **track** was last played (`UserData.LastPlayedDate`, sort `DatePlayed`). Jellyfin-web uses it in one place: the music library's **Suggestions** tab has a *Recently played* row, tracks only, sorted by `DatePlayed` descending, limited to one row and with no "show all" (`musicrecommended.js`, `loadRecentlyPlayed`).
- The *Songs* tab can sort by *Date played*, but that has to be picked by hand, and it lists single tracks, not the albums or playlists they came from.

## Where it would be fixed

- **1 and 2: in the Android app** (an `own/` branch at first). The app already injects its own scripts into the web client (`assets/native/*.js`) and gets every track change and position through `NativeInterface.updateMediaSession`. The plan:
  - On each track change, and every few seconds while playing, save what is playing: queue item IDs, current index, position, shuffle/repeat. Read it from `playbackManager.getPlaylist()` / `getCurrentPlaylistIndex()` and store it in the app's preferences (native side, so it survives the WebView's storage being cleared).
  - On reopen, once the web client is ready, restore it with `playbackManager.play({ ids, startIndex, startPositionTicks })`. Depending on the new setting, it either starts playing or stays paused and ready.
  - A setting under *Music player*: "On reopen, continue playing the last thing".
- **3: a web client view** (jellyfin-web, served by the server), unless the app adds its own screen. Options:
  - a. Show the full *Recently played* list (the Suggestions row's "more"), sorted by `DatePlayed` descending.
  - b. Recently played *albums and playlists*: the server has no "last played" date for an album. It could be derived from its tracks' dates (query played tracks by `DatePlayed` descending, group by album), or the app could keep its own history of what it started (album or playlist ID + date), which is simpler and knows about playlists too.
  - Whether this is about tracks or about albums/playlists is still to be decided.

## Next steps

1. Build 1 + 2 in the app: save the queue, restore it on reopen, add the setting. Check with the diagnostic log that a killed app (swipe away, then reopen) comes back at the right track and position.
2. Decide what "recently played" lists (tracks, or albums/playlists) and where it lives (web view or app screen).
