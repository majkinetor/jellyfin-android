package org.jellyfin.mobile.bridge

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import org.jellyfin.mobile.app.AppPreferences
import org.jellyfin.mobile.webapp.WebappFunctionChannel
import org.json.JSONObject
import timber.log.Timber

/**
 * Plays the web client's music with ExoPlayer instead of the WebView's `<audio>` element.
 *
 * The web client keeps the queue and the UI: `NativeAudioPlugin.js` registers as its audio player
 * and hands over one stream URL at a time. ExoPlayer handles audio focus and becoming noisy itself,
 * so playback pauses for a call and resumes as soon as it ends.
 */
@Suppress("unused")
class NativeAudioPlayer(
    private val context: Context,
    private val appPreferences: AppPreferences,
    private val dataSourceFactory: DataSource.Factory,
    private val webappFunctionChannel: WebappFunctionChannel,
) : Player.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var reportedPaused = true

    /** True while a track is loaded, so other code can leave pausing and resuming to ExoPlayer. */
    @Volatile
    var isActive = false
        private set

    private val progressRunnable = object : Runnable {
        override fun run() {
            val player = player ?: return
            emit("timeupdate", player.currentPosition, player.duration.takeIf { it != C.TIME_UNSET } ?: 0)
            if (player.isPlaying) handler.postDelayed(this, PROGRESS_INTERVAL_MS)
        }
    }

    @JavascriptInterface
    fun isEnabled(): Boolean = appPreferences.musicNativePlayer

    @JavascriptInterface
    fun play(url: String, startMs: Long, isHls: Boolean) = onMain {
        val player = player ?: createPlayer().also { player = it }
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .apply { if (isHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        isActive = true
        reportedPaused = false
        player.setMediaItem(mediaItem, startMs)
        player.prepare()
        player.playWhenReady = true
    }

    @JavascriptInterface
    fun pause() = onMain { player?.playWhenReady = false }

    @JavascriptInterface
    fun resume() = onMain { player?.playWhenReady = true }

    @JavascriptInterface
    fun seek(ms: Long) = onMain {
        val player = player ?: return@onMain
        player.seekTo(ms)
        emit("timeupdate", ms, player.duration.takeIf { it != C.TIME_UNSET } ?: 0)
    }

    @JavascriptInterface
    fun stop() = onMain {
        isActive = false
        handler.removeCallbacks(progressRunnable)
        player?.stop()
        player?.clearMediaItems()
    }

    @JavascriptInterface
    fun destroy() = onMain {
        stop()
        player?.removeListener(this)
        player?.release()
        player = null
    }

    private fun createPlayer(): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .also { it.addListener(this) }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (!isActive) return
        // A pause for a call keeps playWhenReady and sets a suppression reason instead
        val paused = !player.playWhenReady ||
            player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE
        if (paused != reportedPaused) {
            reportedPaused = paused
            Timber.tag(LOG_TAG).i(if (paused) "paused" else "playing")
            emit(if (paused) "pause" else "unpause")
        }
        if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
            handler.removeCallbacks(progressRunnable)
            if (player.isPlaying) {
                emit("playing")
                progressRunnable.run()
            }
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED && isActive) {
            isActive = false
            handler.removeCallbacks(progressRunnable)
            emit("ended")
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        Timber.tag(LOG_TAG).e(error, "playback error")
        if (!isActive) return
        isActive = false
        handler.removeCallbacks(progressRunnable)
        val type = when (error.errorCode) {
            in NETWORK_ERRORS -> "NetworkError"
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            -> "MediaNotSupported"
            else -> "MediaDecodeError"
        }
        emit("error", JSONObject.quote(type))
    }

    private fun emit(event: String, vararg args: Any) {
        val arguments = (listOf(JSONObject.quote(event)) + args.map(Any::toString)).joinToString(",")
        webappFunctionChannel.call("window.NativeAudioPlugin && window.NativeAudioPlugin.onNativeEvent($arguments);")
    }

    private fun onMain(block: () -> Unit) {
        handler.post(block)
    }

    companion object {
        private const val LOG_TAG = "JellyfinAudio"
        private const val PROGRESS_INTERVAL_MS = 500L
        private val NETWORK_ERRORS = PlaybackException.ERROR_CODE_IO_UNSPECIFIED..PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
    }
}
