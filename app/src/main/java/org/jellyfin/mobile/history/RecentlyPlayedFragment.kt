package org.jellyfin.mobile.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.jellyfin.mobile.ui.ComposeFragment
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
            imageUrl = { itemId -> imageUrl(itemId) },
            onContinue = {
                webappFunctionChannel.call("window.ContinuePlugin && window.ContinuePlugin.resume(true);")
                close()
            },
            onPlay = { entry ->
                // IDs are hex; anything else is not put into a script
                if (entry.id.matches(ITEM_ID)) {
                    webappFunctionChannel.call("window.ContinuePlugin && window.ContinuePlugin.playEntry('${entry.id}');")
                }
                close()
            },
            onRemove = { entry -> playHistory.remove(entry.id) },
            onClear = { playHistory.clear() },
            onBackPressed = { close() },
        )
    }

    private fun imageUrl(itemId: String): String? {
        val baseUrl = apiClient.baseUrl ?: return null
        if (itemId.isEmpty()) return null
        return "$baseUrl/Items/$itemId/Images/Primary?fillHeight=160&fillWidth=160&quality=90"
    }

    private fun close() {
        if (isAdded && !parentFragmentManager.isStateSaved) parentFragmentManager.popBackStackImmediate()
    }

    companion object {
        private val ITEM_ID = Regex("^[0-9a-fA-F-]{32,36}$")
    }
}
