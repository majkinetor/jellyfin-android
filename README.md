# Jellyfin for Android, with fixes

A fork of [jellyfin-android](https://github.com/jellyfin/jellyfin-android) with fixes and additions for music listening. It is a personal build, used daily on an Android 15 phone, and shared as is. It is not an official Jellyfin release.

**Download:** the newest APK under [Releases](https://github.com/majkinetor/jellyfin-android/releases). What changed: [CHANGELOG.md](CHANGELOG.md).

## What's different

<!-- differences:start -->

### [Silent playback after microphone use](https://github.com/majkinetor/jellyfin-android/issues/1)

*Being tested.*

**Upstream:** After another app uses the microphone (dictation, a voice message, a recorder, a call), the music keeps "playing" in silence until the app is restarted.  
**This fork:** The music pauses while the microphone is in use and continues once it and the headphones are free. If the sound breaks anyway, the player gets it back by itself.

### [Album started from its cover stays paused when server is slow](https://github.com/majkinetor/jellyfin-android/issues/2)

**Upstream:** An album started from its cover stays paused when the server is slow to answer, for example while its disk spins up.  
**This fork:** The album starts playing as soon as the server answers.

### [Diagnostic log in app](https://github.com/majkinetor/jellyfin-android/issues/3)

**Upstream:** The app's log can only be read with a computer.  
**This fork:** *Client settings → Diagnostics → Diagnostic log* shows the log on the phone, with Copy and Share.

### [Continue after restart, recently played list](https://github.com/majkinetor/jellyfin-android/issues/5)

*Being tested.*

**Upstream:** Closing the app loses the queue, the track and the position, and there is no list of what was played.  
**This fork:** The queue continues after a restart at the same track and second, on its own if *Continue playing on reopen* is on. *Recently played* in the side menu lists albums, playlists and tracks, newest first, each starting where it was left.

<!-- differences:end -->

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
