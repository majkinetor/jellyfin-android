package org.jellyfin.mobile.ui.screens.history

import org.jellyfin.mobile.history.HistoryEntry

/** What the screen asks of its host. */
interface RecentlyPlayedActions {
    fun imageUrl(itemId: String): String?
    fun onContinue()
    fun onPlay(entry: HistoryEntry)
    fun onRemove(entry: HistoryEntry)
    fun onClear()
    fun onBack()
}
