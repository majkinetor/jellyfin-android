# Jellyfin Android: local interventions

Fixes and additions to [jellyfin-android](https://github.com/jellyfin/jellyfin-android), kept in the fork **[majkinetor/jellyfin-android](https://github.com/majkinetor/jellyfin-android)** and used daily on the owner's phone (Honor Magic V5, MagicOS, Android 15). Some may go upstream later.

**Required reading before changing anything in `jellyfin-android/`: [UPSTREAM.md](UPSTREAM.md)**. It covers upstream's conventions, its LLM policy and how its maintainer applies it, and the PR checklist.
- Follow those conventions on every branch, `own/*` included, so any of it can go upstream later.
- **Code branches carry app code only.** Agent files, notes, analysis and scripts live here, on `meta`.
- Never write upstream issue or PR text for posting: the owner writes it in his own words.

## Where things are

| What                 | Where |
| -------------------- | ----- |
| issues (the tracker) | [the fork's GitHub issues](https://github.com/majkinetor/jellyfin-android/issues); labels `status: open/testing/done`, `kind: fix/own/server` |
| builds               | [the fork's GitHub releases](https://github.com/majkinetor/jellyfin-android/releases), one per build |
| this folder          | `C:\Work\jelly`, pushed as the fork's **`meta`** branch (its own history, nothing shared with the code) |
| the code             | `jellyfin-android/` here (ignored by `meta`), a clone with remotes `upstream` (jellyfin) and `origin` (the fork) |
| build tooling        | `build/`: `Dockerfile` (JDK 21 + Android SDK), `build.sh`, `check.sh`, `release.sh`; the keystore and build counter stay local |
| built APKs           | `out/` (local) |

## Branches (in `jellyfin-android/`, all pushed to `origin`)

- `master`: upstream/master, never committed to.
- `fix/<slug>`: one per issue, off upstream/master, meant to be upstreamable.
- `own/<slug>`: additions for this fork (the diagnostic log, continue/recently played). Written to upstream's standard all the same.
- `build`: upstream/master merged with every `fix/*` and `own/*`. Generated, never edited by hand; releases are tagged on it.

**New issue**:
1. Open it on the fork's GitHub issues.
2. Branch `fix/<slug>` (or `own/<slug>`) from `upstream/master` and commit.
3. Run `bash build/build.sh sync`.
4. Try it on the phone, then run `bash build/release.sh "notes"`.
5. Comment on the issue with what changed and the release link.

The commit messages here keep the AI trailers. An upstream PR is made from a fresh branch by the owner (see [UPSTREAM.md](UPSTREAM.md)).

## Building and releasing

**CI** ([`.github/workflows/build.yaml`](.github/workflows/build.yaml) on `meta`, *Fork / Build*):
- Runs daily at 04:17 UTC and on demand: `gh workflow run build.yaml -R majkinetor/jellyfin-android` (`-f release=true` to publish even when nothing changed).
- Merges upstream `master` with every `fix/*` and `own/*` from the fork into `build` (`build/ci-merge.sh`). It never rewrites `fix/*` or `own/*`: when one stops merging, CI fails and you rebase it locally (`build.sh sync`).
- When the merged tree changed, it runs upstream's checks (detekt, lint, tests), builds the signed APK, force-pushes `build` and publishes a release.
- A failure opens (or comments on) an issue labelled `ci`.
- It only lives on `meta`: GitHub runs a workflow only on pushes to the branch holding it, so pushing to `fix/*` / `own/*` doesn't start it. Start it after a push.
- The signing key is in the fork's Actions secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`), the same key as `build/jelly-local.keystore`, so CI and local builds install over each other.
- Release numbers come from the fork's releases (`local.<n>`, one above the newest), locally too (`build.sh` takes the larger of that and `build/build-number`).

**Locally:**

- `bash build/check.sh [branch]`: upstream's CI checks (detekt, lint, unit tests) in Docker. Add no new detekt findings in touched files.
- `bash build/build.sh`: builds `build` as it is. `bash build/build.sh sync`: fetches upstream, rebases all `fix/*` / `own/*` onto it, recreates `build`, then builds.
- `bash build/release.sh "notes"`: pushes the branches and publishes the newest `out/*.apk` as a GitHub release (tag `v<version>`, on the `build` commit).
- No local JDK or Android SDK: everything runs in Docker (`jf-android-build` image; Gradle cache in volume `jf-gradle-cache`). The first build takes about 6 minutes.
- Output: `out/jellyfin-<tag>-local.<n>.apk`, the proprietary release flavor, app ID `org.jellyfin.mobile`, signed with `build/jelly-local.keystore`.
  - **Keep that keystore, and never publish it.** Without it, a new build won't install over the old one, and the app (with its login and settings) has to be uninstalled.
  - The official app has a different signature: it has to be uninstalled once, and store updates no longer apply.
- `<n>` comes from `build/build-number` and counts up, so each build installs over the last one. Reset it to 0 when upstream tags a new release.
- Git Bash mangles quotes and backslashes in inline scripts: write scripts to a file first. Every text file is LF (`core.autocrlf false` in the clone).

## Debugging on the phone

In the app's settings (the web client's menu → *Client settings*), open **Diagnostics → Diagnostic log** to view, copy or share the log. No adb needed. Tags:
- `JellyfinAudio`: audio events
- `WebView`: the web client's console, `[ContinuePlugin]` lines included

## Facts

- Music plays in the WebView (`<audio>` in jellyfin-web, loaded from the server). ExoPlayer is used only for video ("Integrated player") and Android Auto (`LibraryService`).
- `RemotePlayerService` mirrors the web player's state into a MediaSession and notification. The web player reports its state through `NativeInterface.updateMediaSession`, and the app controls it with `WebappFunctionChannel` (JavaScript run on `window.NavigationHelper.playbackManager`).
- Plugins in `assets/native/*Plugin.js` (listed in `nativeshell.js`) get `{ events, playbackManager, globalize, appRouter, … }` from the web client's plugin manager.
- The owner's server runs 12.1 with the web client's **modern** (MUI) layout: anything injected into the web UI must handle that layout and the legacy one.
- Starting audio at the very end of a track makes the server answer 400 for the HLS segment. Skip to the next track instead (`ContinuePlugin.startPoint`).
