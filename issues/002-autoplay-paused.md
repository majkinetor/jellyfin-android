---
id: 2
title: Album started from its thumbnail stays paused when the server is slow to answer
status: testing
branch: fix/autoplay-paused
upstream: not reported yet
created: 2026-10-22
---

# Album started from its thumbnail stays paused when the server is slow to answer

## Environment

- Device: Honor Magic V5, MagicOS, Android 15
- App: release build from the store; fix made against `master` @ `3e660a8` (2026-10-16)
- Player: web player (default), music

## Steps

1. Let the server's music drive spin down.
2. Press play on an album thumbnail (without opening the album).

## Actual

The player opens on the first track but stays paused; pressing play starts it. With the drive already spinning, it plays right away.

## Cause

The WebView runs with the default `mediaPlaybackRequiresUserGesture = true`. jellyfin-web calls `audio.play()` only after the server answers the PlaybackInfo / stream request. When the drive has to spin up first, that answer arrives after the tap's user activation has expired, so Chromium rejects `play()` with `NotAllowedError`. `htmlMediaHelper.playWithPromise` swallows that error on purpose, which leaves the element paused with no error shown.

## Fix

```kotlin
fun WebSettings.applyDefault() {
    ...
    mediaPlaybackRequiresUserGesture = false
}
```

The page is the app's own web client. The app already advertises the `htmlaudioautoplay` / `htmlvideoautoplay` features, so allowing autoplay matches what it says it supports.
