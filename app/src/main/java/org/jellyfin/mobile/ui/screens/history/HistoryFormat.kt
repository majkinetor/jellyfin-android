package org.jellyfin.mobile.ui.screens.history

import android.text.format.DateUtils
import androidx.annotation.StringRes
import org.jellyfin.mobile.R

@StringRes
internal fun kindLabel(kind: String): Int? = when (kind) {
    "album" -> R.string.recently_played_kind_album
    "playlist" -> R.string.recently_played_kind_playlist
    "track" -> R.string.recently_played_kind_track
    "artist" -> R.string.recently_played_kind_artist
    "genre" -> R.string.recently_played_kind_genre
    "folder" -> R.string.recently_played_kind_folder
    else -> null
}

internal fun relativeTime(time: Long): String = when {
    time <= 0 -> ""
    else -> DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
}
