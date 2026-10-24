package org.jellyfin.mobile.ui.screens.history

import android.text.format.DateUtils
import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jellyfin.mobile.R
import org.jellyfin.mobile.history.ContinueState
import org.jellyfin.mobile.history.HistoryEntry

private enum class Filter(@StringRes val label: Int, val kinds: Set<String>?) {
    ALL(R.string.recently_played_filter_all, null),
    ALBUMS(R.string.recently_played_filter_albums, setOf("album")),
    PLAYLISTS(R.string.recently_played_filter_playlists, setOf("playlist")),
    TRACKS(R.string.recently_played_filter_tracks, setOf("track")),
    OTHER(R.string.recently_played_filter_other, setOf("artist", "genre", "folder")),
}

@Composable
fun RecentlyPlayedScreen(
    entries: List<HistoryEntry>,
    continueState: ContinueState?,
    actions: RecentlyPlayedActions,
) {
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var confirmClear by remember { mutableStateOf(false) }
    val shown = remember(entries, filter) {
        filter.kinds?.let { kinds -> entries.filter { it.kind in kinds } } ?: entries
    }

    if (confirmClear) {
        ClearHistoryDialog(
            onConfirm = {
                actions.onClear()
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        topBar = { RecentlyPlayedTopBar(onBack = actions::onBack, onClear = { confirmClear = true }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (continueState != null) {
                item(key = "continue") {
                    ContinueCard(continueState, actions)
                }
            }
            item(key = "filters") {
                FilterRow(selected = filter, onSelect = { filter = it })
            }
            if (shown.isEmpty()) {
                item(key = "empty") {
                    val message = when {
                        entries.isEmpty() -> R.string.recently_played_empty
                        else -> R.string.recently_played_empty_filter
                    }
                    Text(
                        text = stringResource(message),
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(shown, key = { it.id }) { entry ->
                HistoryRow(entry, actions)
            }
        }
    }
}

@Composable
private fun RecentlyPlayedTopBar(onBack: () -> Unit, onClear: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(text = stringResource(R.string.recently_played)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
            }
        },
        actions = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        onClick = {
                            showMenu = false
                            onClear()
                        },
                    ) {
                        Text(text = stringResource(R.string.recently_played_clear))
                    }
                }
            }
        },
        backgroundColor = Color.Transparent,
    )
}

@Composable
private fun ClearHistoryDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.recently_played_clear_confirm_title)) },
        text = { Text(text = stringResource(R.string.recently_played_clear_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.recently_played_clear_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun FilterRow(selected: Filter, onSelect: (Filter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Filter.entries.forEach { option ->
            FilterChip(label = stringResource(option.label), selected = option == selected) { onSelect(option) }
        }
    }
}

@Composable
private fun ContinueCard(state: ContinueState, actions: RecentlyPlayedActions) {
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
            Cover(actions.imageUrl(state.imageId), size = 64.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.recently_played_continue),
                    style = MaterialTheme.typography.overline,
                )
                Text(
                    text = state.name,
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                SingleLine(listOf(state.subtitle, state.album).filter { it.isNotEmpty() }.joinToString(" · "))
                val position = DateUtils.formatElapsedTime(state.positionMs / DateUtils.SECOND_IN_MILLIS)
                val track = stringResource(
                    R.string.recently_played_track_position,
                    state.index + 1,
                    state.ids.size,
                    position,
                )
                Text(
                    text = "$track · ${relativeTime(state.savedAt)}",
                    style = MaterialTheme.typography.caption,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = actions::onContinue) {
                Text(text = stringResource(R.string.recently_played_play))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(entry: HistoryEntry, actions: RecentlyPlayedActions) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { actions.onPlay(entry) }, onLongClick = { showMenu = true })
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(actions.imageUrl(entry.imageId), size = 56.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.subtitle1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val kind = kindLabel(entry.kind)?.let { stringResource(it) } ?: entry.kind
                SingleLine(listOf(kind, entry.subtitle).filter { it.isNotEmpty() }.joinToString(" · "))
                ResumeSpot(entry)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = relativeTime(entry.playedAt), style = MaterialTheme.typography.caption)
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                onClick = {
                    showMenu = false
                    actions.onPlay(entry)
                },
            ) {
                Text(text = stringResource(R.string.recently_played_play))
            }
            DropdownMenuItem(
                onClick = {
                    showMenu = false
                    actions.onRemove(entry)
                },
            ) {
                Text(text = stringResource(R.string.recently_played_remove))
            }
        }
    }
}

@Composable
private fun ResumeSpot(entry: HistoryEntry) {
    if (entry.resumeIndex == 0 && entry.resumePositionMs == 0L) return
    val position = DateUtils.formatElapsedTime(entry.resumePositionMs / DateUtils.SECOND_IN_MILLIS)
    val text = when {
        entry.kind == "track" -> position
        else -> stringResource(R.string.recently_played_resume_at, entry.resumeIndex + 1, position)
    }
    Text(text = text, style = MaterialTheme.typography.caption)
}

@Composable
private fun SingleLine(text: String) {
    if (text.isEmpty()) return
    Text(text = text, style = MaterialTheme.typography.body2, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Cover(url: String?, size: Dp) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
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
