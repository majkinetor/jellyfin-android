package org.jellyfin.mobile.history

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/** Something the user started: an album, a playlist, a track, ... */
@Serializable
data class HistoryEntry(
    val id: String,
    val kind: String,
    val name: String = "",
    val subtitle: String = "",
    val imageId: String = "",
    val serverId: String = "",
    val playedAt: Long = 0,
)

/** The queue that was playing, to continue it after a restart. */
@Serializable
data class ContinueState(
    val serverId: String,
    val ids: List<String>,
    val index: Int,
    val positionMs: Long = 0,
    val repeat: String? = null,
    val name: String = "",
    val subtitle: String = "",
    val album: String = "",
    val imageId: String = "",
    val savedAt: Long = 0,
)

/**
 * The play history and the saved queue, kept in the app's own preferences file, so they survive restarts
 * and don't depend on the WebView's storage.
 */
class PlayHistory(context: Context) {
    private val preferences = context.getSharedPreferences("play_history", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _entries = MutableStateFlow(loadEntries())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    private val _continueState = MutableStateFlow(loadContinueState())
    val continueState: StateFlow<ContinueState?> = _continueState.asStateFlow()

    /** The raw JSON as the web client saved it, handed back to it unchanged. */
    val continueStateJson: String? get() = preferences.getString(KEY_CONTINUE, null)

    @Synchronized
    fun record(entryJson: String) {
        val entry = try {
            json.decodeFromString<HistoryEntry>(entryJson).copy(playedAt = System.currentTimeMillis())
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Invalid history entry")
            return
        }
        // Newest first, one row per item
        val entries = listOf(entry) + _entries.value.filterNot { it.id == entry.id }
        save(entries.take(MAX_ENTRIES))
    }

    @Synchronized
    fun remove(id: String) = save(_entries.value.filterNot { it.id == id })

    @Synchronized
    fun clear() = save(emptyList())

    fun saveContinueState(stateJson: String) {
        val state = try {
            json.decodeFromString<ContinueState>(stateJson)
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Invalid continue state")
            return
        }
        preferences.edit { putString(KEY_CONTINUE, stateJson) }
        _continueState.value = state
    }

    private fun save(entries: List<HistoryEntry>) {
        preferences.edit { putString(KEY_ENTRIES, json.encodeToString(entries)) }
        _entries.value = entries
    }

    private fun loadEntries(): List<HistoryEntry> = try {
        preferences.getString(KEY_ENTRIES, null)?.let { json.decodeFromString<List<HistoryEntry>>(it) }.orEmpty()
    } catch (e: IllegalArgumentException) {
        Timber.e(e, "Invalid history")
        emptyList()
    }

    private fun loadContinueState(): ContinueState? = try {
        preferences.getString(KEY_CONTINUE, null)?.let { json.decodeFromString<ContinueState>(it) }
    } catch (e: IllegalArgumentException) {
        Timber.e(e, "Invalid continue state")
        null
    }

    companion object {
        private const val KEY_ENTRIES = "entries"
        private const val KEY_CONTINUE = "continue"
        private const val MAX_ENTRIES = 300
    }
}
