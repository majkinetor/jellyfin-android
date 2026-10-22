package org.jellyfin.mobile.ui.screens.history

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jellyfin.mobile.history.ContinueState
import org.jellyfin.mobile.history.HistoryEntry

private enum class Filter(val label: String, val kinds: Set<String>?) {
    ALL("All", null),
    ALBUMS("Albums", setOf("album")),
    PLAYLISTS("Playlists", setOf("playlist")),
    TRACKS("Tracks", setOf("track")),
    OTHER("Other", setOf("artist", "genre", "folder")),
}

private fun kindLabel(kind: String) = when (kind) {
    "album" -> "Album"
    "playlist" -> "Playlist"
    "track" -> "Track"
    "artist" -> "Artist"
    "genre" -> "Genre"
    "folder" -> "Folder"
    else -> kind
}

@Composable
fun RecentlyPlayedScreen(
    entries: List<HistoryEntry>,
    continueState: ContinueState?,
    imageUrl: (String) -> String?,
    onContinue: () -> Unit,
    onPlay: (HistoryEntry) -> Unit,
    onRemove: (HistoryEntry) -> Unit,
    onClear: () -> Unit,
    onBackPressed: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val shown = remember(entries, filter) {
        filter.kinds?.let { kinds -> entries.filter { it.kind in kinds } } ?: entries
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("Removes every entry from Recently played. The saved queue stays.") },
            confirmButton = {
                TextButton(onClick = { onClear(); confirmClear = false }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        topBar = {
            TopAppBar(
                title = { Text("Recently played") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(onClick = { showMenu = false; confirmClear = true }) {
                                Text("Clear history")
                            }
                        }
                    }
                },
                backgroundColor = Color.Transparent,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (continueState != null) {
                item(key = "continue") {
                    ContinueCard(continueState, imageUrl, onContinue)
                }
            }
            item(key = "filters") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Filter.entries.forEach { option ->
                        FilterChip(label = option.label, selected = option == filter) { filter = option }
                    }
                }
            }
            if (shown.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = if (entries.isEmpty()) "Nothing played yet" else "Nothing of this kind played yet",
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(shown, key = { it.id }) { entry ->
                HistoryRow(entry, imageUrl, onPlay, onRemove)
            }
        }
    }
}

@Composable
private fun ContinueCard(state: ContinueState, imageUrl: (String) -> String?, onContinue: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        elevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(imageUrl(state.imageId), size = 64)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Continue", style = MaterialTheme.typography.overline)
                Text(
                    text = state.name,
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOf(state.subtitle, state.album).filter { it.isNotEmpty() }.joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(text = details, style = MaterialTheme.typography.body2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    text = "Track ${state.index + 1} of ${state.ids.size} · ${formatPosition(state.positionMs)} · " +
                        relativeTime(state.savedAt),
                    style = MaterialTheme.typography.caption,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onContinue) { Text("Play") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    imageUrl: (String) -> String?,
    onPlay: (HistoryEntry) -> Unit,
    onRemove: (HistoryEntry) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { onPlay(entry) }, onLongClick = { showMenu = true })
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(imageUrl(entry.imageId), size = 56)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.subtitle1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOf(kindLabel(entry.kind), entry.subtitle).filter { it.isNotEmpty() }.joinToString(" · ")
                Text(text = details, style = MaterialTheme.typography.body2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = relativeTime(entry.playedAt), style = MaterialTheme.typography.caption)
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(onClick = { showMenu = false; onPlay(entry) }) { Text("Play") }
            DropdownMenuItem(onClick = { showMenu = false; onRemove(entry) }) { Text("Remove from history") }
        }
    }
}

@Composable
private fun Cover(url: String?, size: Int) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colors.onSurface.copy(alpha = 0.08f)),
    )
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colors
    Text(
        text = label,
        style = MaterialTheme.typography.body2,
        color = if (selected) colors.onPrimary else colors.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.primary else colors.onSurface.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

private fun formatPosition(ms: Long): String {
    val seconds = ms / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun relativeTime(time: Long): String =
    if (time <= 0) "" else DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
