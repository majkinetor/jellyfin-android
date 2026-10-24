package org.jellyfin.mobile.webapp

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresApi
import org.jellyfin.mobile.utils.AndroidVersion
import timber.log.Timber

/**
 * Pauses the web player while another app uses the microphone (a recording or a call/VoIP audio mode)
 * and resumes it afterwards.
 *
 * The web player's audio stream lives inside the WebView; when the audio mode or routing changes under it
 * (voice input, voice messages, calls), it can end up "playing" without any sound until the app is restarted.
 * Pausing first keeps the stream out of the switch, and resuming once the route has settled opens a fresh one.
 */
@RequiresApi(Build.VERSION_CODES.O)
class MicrophoneWatcher(
    private val audioManager: AudioManager,
    private val isPlaying: () -> Boolean,
    private val pause: () -> Unit,
    private val play: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    private var recording = false
    private var mode = AudioManager.MODE_NORMAL
    private var busy = false
    private var pausedByUs = false
    private var pausedAt = 0L
    private var busySince = 0L
    private var wasPlaying = false
    private var stoppedPlayingAt = 0L

    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
            Timber.tag(TAG).i("recordings: %d active %s", configs.size, configs.map { it.clientAudioSource })
            recording = configs.isNotEmpty()
            update()
        }
    }

    private val modeListener: Any? = if (AndroidVersion.isAtLeastS) {
        AudioManager.OnModeChangedListener { newMode ->
            Timber.tag(TAG).i("audio mode: %s", modeName(newMode))
            mode = newMode
            update()
        }
    } else {
        null
    }

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            Timber.tag(TAG).i("players: %s", configs.map { usageName(it.audioAttributes.usage) })
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            Timber.tag(TAG).i("devices added: %s", addedDevices.map { "${it.type}:${it.productName}" })
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            Timber.tag(TAG).i("devices removed: %s", removedDevices.map { "${it.type}:${it.productName}" })
        }
    }

    private var busyEndedAt = 0L
    private var freeSince = 0L
    private var settledSince = 0L
    private var lastRoute = ""

    /**
     * Checked every [POLL_MS] once the microphone is free: resumes when the audio route has settled,
     * that is no call mode and no Bluetooth headset (SCO) link, for [SETTLE_MS]. A Bluetooth headset
     * keeps its telephone link a while after the microphone is released, and music started on it
     * sounds like a phone call and breaks when the headset switches back.
     */
    private val resumeRunnable = object : Runnable {
        override fun run() {
            if (busy || !pausedByUs) return
            val now = SystemClock.elapsedRealtime()
            val route = routeState()
            if (route != lastRoute) {
                Timber.tag(TAG).i("route: %s", route)
                lastRoute = route
            }
            if (route.isNotEmpty()) settledSince = 0L else if (settledSince == 0L) settledSince = now
            val settled = settledSince != 0L && now - settledSince >= SETTLE_MS
            val waitedTooLong = now - freeSince >= MAX_WAIT_MS
            if (!settled && !waitedTooLong) {
                handler.postDelayed(this, POLL_MS)
                return
            }
            pausedByUs = false
            if (!isPlaying()) {
                Timber.tag(TAG).i("microphone free, resuming%s", if (settled) "" else " (route never settled)")
                play()
            }
        }
    }

    /** What still holds the audio route for a call, empty when nothing does. */
    @Suppress("DEPRECATION")
    private fun routeState(): String {
        val holders = mutableListOf<String>()
        if (audioManager.mode != AudioManager.MODE_NORMAL) holders += "mode ${modeName(audioManager.mode)}"
        if (audioManager.isBluetoothScoOn) holders += "bluetooth sco"
        if (AndroidVersion.isAtLeastS) {
            val type = audioManager.communicationDevice?.type
            if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                holders += "communication device $type"
            }
        }
        return holders.joinToString()
    }

    /** True while the microphone is in use and for a while after, when headsets drop their call link. */
    fun recentlyBusy(): Boolean = busy || SystemClock.elapsedRealtime() - busyEndedAt < RECENTLY_BUSY_MS

    fun start() {
        if (started) return
        started = true
        recording = audioManager.activeRecordingConfigurations.isNotEmpty()
        mode = audioManager.mode
        busy = isBusy()
        Timber.tag(TAG).i("start: mode %s, recording %s", modeName(mode), recording)
        audioManager.registerAudioRecordingCallback(recordingCallback, handler)
        audioManager.registerAudioPlaybackCallback(playbackCallback, handler)
        audioManager.registerAudioDeviceCallback(deviceCallback, handler)
        if (AndroidVersion.isAtLeastS) {
            val listener = modeListener as AudioManager.OnModeChangedListener
            audioManager.addOnModeChangedListener({ handler.post(it) }, listener)
        }
    }

    fun stop() {
        if (!started) return
        started = false
        handler.removeCallbacks(resumeRunnable)
        pausedByUs = false
        audioManager.unregisterAudioRecordingCallback(recordingCallback)
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        if (AndroidVersion.isAtLeastS) {
            audioManager.removeOnModeChangedListener(modeListener as AudioManager.OnModeChangedListener)
        }
    }

    /**
     * Called with each state report from the web player; a play while the microphone is still in use
     * (after our own pause has settled) is the user's choice, so we no longer resume on our own.
     */
    fun onPlayerState(playing: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (wasPlaying && !playing) {
            stoppedPlayingAt = now
            // The pause was reported just after the microphone was taken: the WebView paused
            // by itself on the recorder's audio focus request, so it is still ours to resume.
            if (busy && !pausedByUs && now - busySince < FOCUS_PAUSE_WINDOW_MS) {
                Timber.tag(TAG).i("paused by the system as the microphone was taken")
                pausedByUs = true
                pausedAt = now
            }
        }
        wasPlaying = playing
        if (playing && pausedByUs && now - pausedAt > PAUSE_SETTLE_MS) {
            Timber.tag(TAG).i("user resumed while microphone busy")
            pausedByUs = false
            handler.removeCallbacks(resumeRunnable)
        }
    }

    private fun isBusy() = recording || mode != AudioManager.MODE_NORMAL

    private fun update() {
        val nowBusy = isBusy()
        if (nowBusy == busy) return
        busy = nowBusy
        if (busy) {
            val now = SystemClock.elapsedRealtime()
            busySince = now
            handler.removeCallbacks(resumeRunnable)
            if (isPlaying()) {
                Timber.tag(TAG).i("microphone in use, pausing")
                pausedByUs = true
                pausedAt = now
                pause()
            } else if (!pausedByUs && now - stoppedPlayingAt < FOCUS_PAUSE_WINDOW_MS) {
                // A recorder asks for audio focus before it opens the microphone; the WebView
                // pauses on that a moment earlier. Resume afterwards as if we had paused.
                Timber.tag(TAG).i("microphone in use, already paused by the system %d ms ago", now - stoppedPlayingAt)
                pausedByUs = true
                pausedAt = stoppedPlayingAt
            }
        } else {
            busyEndedAt = SystemClock.elapsedRealtime()
            if (pausedByUs) {
                Timber.tag(TAG).i("microphone free, waiting for the audio route to settle")
                freeSince = busyEndedAt
                settledSince = 0L
                lastRoute = ""
                handler.post(resumeRunnable)
            }
        }
    }

    private fun usageName(usage: Int) = when (usage) {
        android.media.AudioAttributes.USAGE_MEDIA -> "media"
        android.media.AudioAttributes.USAGE_GAME -> "game"
        android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION -> "voice"
        android.media.AudioAttributes.USAGE_NOTIFICATION -> "notification"
        android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE -> "ringtone"
        android.media.AudioAttributes.USAGE_ALARM -> "alarm"
        android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION -> "sonification"
        android.media.AudioAttributes.USAGE_ASSISTANT -> "assistant"
        else -> usage.toString()
    }

    private fun modeName(mode: Int) = when (mode) {
        AudioManager.MODE_NORMAL -> "NORMAL"
        AudioManager.MODE_RINGTONE -> "RINGTONE"
        AudioManager.MODE_IN_CALL -> "IN_CALL"
        AudioManager.MODE_IN_COMMUNICATION -> "IN_COMMUNICATION"
        AudioManager.MODE_CALL_SCREENING -> "CALL_SCREENING"
        else -> mode.toString()
    }

    companion object {
        private const val TAG = "JellyfinAudio"
        private const val PAUSE_SETTLE_MS = 2000L
        private const val POLL_MS = 500L
        private const val SETTLE_MS = 1000L
        private const val MAX_WAIT_MS = 15_000L
        private const val RECENTLY_BUSY_MS = 20_000L
        private const val FOCUS_PAUSE_WINDOW_MS = 1500L
    }
}
