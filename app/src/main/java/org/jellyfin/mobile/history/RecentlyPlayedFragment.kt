package org.jellyfin.mobile.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.jellyfin.mobile.ui.ComposeFragment
import org.jellyfin.mobile.ui.screens.history.RecentlyPlayedActions
import org.jellyfin.mobile.ui.screens.history.RecentlyPlayedScreen
import org.jellyfin.mobile.webapp.WebappFunctionChannel
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.android.ext.android.inject

class RecentlyPlayedFragment : ComposeFragment() {
    private val playHistory: PlayHistory by inject()
    private val apiClient: ApiClient by inject()
    private val webappFunctionChannel: WebappFunctionChannel by inject()

    @Composable
    override fun Content() {
        val entries by playHistory.entries.collectAsState()
        val continueState by playHistory.continueState.collectAsState()

        RecentlyPlayedScreen(
            entries = entries,
            continueState = continueState,
            actions = actions,
        )
    }

    private val actions = object : RecentlyPlayedActions {
        override fun imageUrl(itemId: String): String? {
            val baseUrl = apiClient.baseUrl
            if (baseUrl == null || itemId.isEmpty()) return null
            return "$baseUrl/Items/$itemId/Images/Primary?fillHeight=160&fillWidth=160&quality=90"
        }

        override fun onContinue() {
            webappFunctionChannel.call("window.ContinuePlugin && window.ContinuePlugin.resume(true);")
            close()
        }

        override fun onPlay(entry: HistoryEntry) {
            // IDs are hex; anything else is not put into a script
            if (entry.id.matches(ITEM_ID)) {
                val spot = with(entry) { "$resumeIndex, $resumePositionMs, $resumeDurationMs, $trackCount" }
                webappFunctionChannel.call(
                    "window.ContinuePlugin && window.ContinuePlugin.playEntryFrom('${entry.id}', $spot);",
                )
            }
            close()
        }

        override fun onRemove(entry: HistoryEntry) = playHistory.remove(entry.id)

        override fun onClear() = playHistory.clear()

        override fun onBack() = close()
    }

    private fun close() {
        if (isAdded && !parentFragmentManager.isStateSaved) parentFragmentManager.popBackStackImmediate()
    }

    companion object {
        private val ITEM_ID = Regex("^[0-9a-fA-F-]{32,36}$")
    }
}
