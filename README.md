# Jellyfin for Android, with fixes

A fork of [jellyfin-android](https://github.com/jellyfin/jellyfin-android) with fixes and additions for music listening. It is a personal build, used daily on an Android 15 phone, and shared as is. It is not an official Jellyfin release.

**Download:** the newest APK under [Releases](https://github.com/majkinetor/jellyfin-android/releases). What changed: [CHANGELOG.md](CHANGELOG.md).

## What's different

| | |
| --- | --- |
| **Sound after using the microphone** | The music pauses while another app uses the microphone (voice messages, dictation, recorder, calls) and continues afterwards, instead of "playing" in silence until the app is restarted. [#1](https://github.com/majkinetor/jellyfin-android/issues/1) |
| **Albums start playing** | An album started from its cover no longer stays paused when the server is slow to answer (a sleeping disk). [#2](https://github.com/majkinetor/jellyfin-android/issues/2) |
| **Continue where you left off** | The queue, track and position survive an app restart. Optionally they continue on their own when the app opens (*Client settings → Music player*). [#5](https://github.com/majkinetor/jellyfin-android/issues/5) |
| **Recently played** | Albums, playlists and tracks you played, newest first, each from where you left it. In the side menu under *Favorites*. [#5](https://github.com/majkinetor/jellyfin-android/issues/5) |
| **Diagnostic log** | *Client settings → Diagnostics*: read, copy or share the app's log without a computer. [#3](https://github.com/majkinetor/jellyfin-android/issues/3) |

## Installing

The APK is signed with this fork's own key, not Jellyfin's, so Android won't install it over the official app:

1. Uninstall the official Jellyfin app. That removes its login and settings.
2. Install the APK from Releases. Later releases install over it.

Store updates of the official app no longer apply. To go back, uninstall this one and install the official app again.

## The code

| Branch | What |
| --- | --- |
| [`build`](https://github.com/majkinetor/jellyfin-android/tree/build) | what the releases are built from: upstream `master` plus every branch below |
| `fix/*` | one fix per branch, kept ready to go upstream |
| `own/*` | additions for this fork |
| `master` | upstream, unchanged |
| `meta` (this page) | notes, build scripts and agent instructions; no app code |

Issues and ideas: [Issues](https://github.com/majkinetor/jellyfin-android/issues). Licensed like Jellyfin itself, under GPL-2.0.
