---
id: 1
title: Web player keeps "playing" without sound after the microphone is used
status: done
branch: fix/mic-silence
upstream: not reported yet (related to jellyfin/jellyfin-android#2203)
created: 2026-10-22
resolved: 2026-10-22
---

# Web player keeps "playing" without sound after the microphone is used

## Environment

- Device: Honor Magic V5, MagicOS, Android 15
- App: release build from the store; fix made against `master` @ `3e660a8` (2026-10-16)
- Player: web player (default), music
- Other players on the same phone (foobar2000) recover fine after microphone use

## Steps

1. Play an album in the app.
2. Use the microphone in another app (voice input, voice message, assistant, call).
3. Go back to the music.

## Expected

Playback carries on with sound, or pauses and can be resumed.

## Actual

Almost every time, the player says it is playing and the position moves, but there is no sound. Pausing, playing and starting another track don't help. Only killing and restarting the app brings sound back.

This looks related to #2203 (playback can't be restored after a call, Samsung A56, Android 16).

## Cause (likely)

Music plays through the WebView's `<audio>`. The app doesn't react when another app takes the microphone: it doesn't pause, and the WebView's audio stream keeps running while the system audio mode or routing changes under it (`MODE_IN_COMMUNICATION`, recording). After that the stream in the app process is stuck, and since the WebView's audio service lives in the app process, only a restart clears it. Native players pause when the microphone is taken and open a new stream when they resume, so they don't hit this.

## Fix

`RemotePlayerService` now watches microphone use while the local web player is active (new `webapp/MicrophoneWatcher.kt`):

- `AudioManager.AudioRecordingCallback` (another app records) and `OnModeChangedListener` (API 31+: call or VoIP mode)
- when the microphone becomes busy and the web player is playing → `playbackManager.pause()`
- when the microphone is free again → `playbackManager.play()`, at least 1 s later and no sooner than 6 s after the pause, so the WebView has closed its old stream (Chromium closes idle output streams after about 5 s) and opens a new one
- if the user presses play while the microphone is still busy, the app leaves it alone and doesn't resume later

It also logs recording, audio-mode, player and device changes under the `JellyfinAudio` tag.

## Test log

<!-- Settings → Diagnostics → Diagnostic log → Share; paste the JellyfinAudio lines around the repro -->

## Result

2026-10-22: first tests on the phone (build `2.7.3-local.1`) are positive. Resolved. Staying in daily use for a week or two before it goes upstream.

Observed: using the microphone pauses the music, and it carries on once the microphone is free. This is the intended behaviour.
