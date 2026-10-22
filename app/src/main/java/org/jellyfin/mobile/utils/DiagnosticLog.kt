package org.jellyfin.mobile.utils

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.webkit.WebViewCompat
import org.jellyfin.mobile.BuildConfig
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Keeps the app's log in a file, so it can be read and shared from the settings without adb.
 * Two files of up to [MAX_SIZE] each: when the current one is full, it becomes the previous one.
 */
object DiagnosticLog {
    private const val MAX_SIZE = 1024 * 1024L
    private const val SHARE_SIZE = 200 * 1024

    private val executor = Executors.newSingleThreadExecutor()
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private lateinit var current: File
    private lateinit var previous: File

    fun init(context: Context) {
        val dir = File(context.filesDir, "logs").apply { mkdirs() }
        current = File(dir, "diagnostic.log")
        previous = File(dir, "diagnostic.1.log")
        Timber.plant(FileTree())
        Timber.tag("DiagnosticLog").i("app start: %s", deviceInfo(context).replace('\n', ' '))
    }

    fun deviceInfo(context: Context): String {
        val webView = WebViewCompat.getCurrentWebViewPackage(context)
        return buildString {
            appendLine("App: ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            append("Device: ${Build.MANUFACTURER} ${Build.MODEL}, ")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            append("WebView: ${webView?.packageName} ${webView?.versionName}")
        }
    }

    /** The whole log, oldest first (blocks while pending lines are written). */
    fun read(): String = executor.submit<String> {
        listOf(previous, current).filter { it.exists() }.joinToString("") { it.readText() }
    }.get()

    /** The newest part of the log, small enough to share as text. */
    fun readForSharing(context: Context): String {
        val log = read()
        return deviceInfo(context) + "\n\n" + if (log.length > SHARE_SIZE) log.takeLast(SHARE_SIZE) else log
    }

    fun clear() {
        executor.execute {
            previous.delete()
            current.delete()
        }
    }

    private class FileTree : Timber.Tree() {
        override fun isLoggable(tag: String?, priority: Int) = priority >= Log.INFO

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            val time = Date()
            executor.execute {
                val level = when (priority) {
                    Log.INFO -> 'I'
                    Log.WARN -> 'W'
                    Log.ERROR -> 'E'
                    Log.ASSERT -> 'A'
                    else -> 'D'
                }
                @Suppress("TooGenericExceptionCaught", "SwallowedException")
                try {
                    if (current.length() > MAX_SIZE) {
                        previous.delete()
                        current.renameTo(previous)
                    }
                    current.appendText("${timeFormat.format(time)} $level ${tag ?: "-"}: $message\n")
                    if (t != null) current.appendText(Log.getStackTraceString(t) + "\n")
                } catch (e: Exception) {
                    // Logging must never break the app
                }
            }
        }
    }
}
