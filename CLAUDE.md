# Jellyfin Android: local interventions

Local fixes to [jellyfin-android](https://github.com/jellyfin/jellyfin-android), built and installed on the owner's phone (Honor Magic V5, MagicOS, Android 15). Some may go upstream later.

**Required reading before changing anything in `jellyfin-android/`: [UPSTREAM.md](UPSTREAM.md)** (upstream's conventions, its LLM policy and how its maintainer applies it, and the PR checklist). Follow those conventions on every branch, `own/*` included, so any of it can go upstream later. Never write upstream issue or PR text for posting: the owner writes it in his own words.

## Layout

| Path                | What                                                                       |
| ------------------- | -------------------------------------------------------------------------- |
| `jellyfin-android/` | the clone; remote `upstream` = jellyfin/jellyfin-android, no fork, nothing pushed |
| `issues/`           | one markdown per issue, the local issue tracker ([`issues/README.md`](issues/README.md)) |
| `build/`            | `Dockerfile` (JDK 21 + Android SDK), `build.sh`, signing keystore, build counter |
| `out/`              | built APKs                                                                 |

## Branches (in `jellyfin-android/`)

- `master`: upstream/master, never committed to.
- `fix/<slug>`: one per issue, off upstream/master, meant to be upstreamable (no local-only code).
- `own/<slug>`: local-only additions (e.g. the diagnostic log).
- `build`: upstream/master merged with every `fix/*` and `own/*`. Generated, never edited by hand.

New issue: add `issues/NNN-slug.md`, branch `fix/<slug>` from `upstream/master`, commit, then `build/build.sh sync`.

## Building

- `bash build/build.sh`: builds `build` as is. `bash build/build.sh sync`: fetches upstream, rebases all `fix/*` / `own/*` on it, recreates `build`, then builds.
- No local JDK or Android SDK: everything runs in Docker (`jf-android-build` image, Gradle cache in volume `jf-gradle-cache`). The first build takes about 6 minutes.
- Output: `out/jellyfin-<tag>-local.<n>.apk`, the proprietary release flavor, app ID `org.jellyfin.mobile`, signed with `build/jelly-local.keystore`.
  - **Keep that keystore.** Without it, a new build won't install over the old one, and the app (with its login and settings) would have to be uninstalled.
  - The official app has a different signature, so it was uninstalled once to make room. Store updates no longer apply to it.
- `<n>` comes from `build/build-number` and counts up, so each build installs over the last one. Reset it to 0 when upstream tags a new release.
- Git Bash mangles quotes and backslashes in inline scripts: write scripts to a file first. Every text file is LF (`core.autocrlf false` in the clone).

## Debugging on the phone

Settings (from the web client's menu → *Client settings*) → **Diagnostics → Diagnostic log**: view or share the log. Audio events use the tag `JellyfinAudio`, web console lines use `WebView`. No adb needed.

## Facts

- Music plays in the WebView (`<audio>` in jellyfin-web, loaded from the server). ExoPlayer is used only for video ("Integrated player") and Android Auto (`LibraryService`).
- `RemotePlayerService` mirrors the web player's state into a MediaSession and notification. The web player reports its state through `NativeInterface.updateMediaSession`, and the app controls it with `WebappFunctionChannel` (JavaScript run on `window.NavigationHelper.playbackManager`).
