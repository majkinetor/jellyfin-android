package org.jellyfin.mobile.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.jellyfin.mobile.R
import org.jellyfin.mobile.utils.AndroidVersion
import org.jellyfin.mobile.utils.DiagnosticLog

/**
 * Shows the newest part of the diagnostic log, with buttons to share, copy or clear it.
 */
object DiagnosticLogDialog {
    private const val SHOWN_SIZE = 100_000
    private const val TEXT_SIZE_SP = 11f
    private const val PADDING_HORIZONTAL = 32
    private const val PADDING_VERTICAL = 16

    fun show(context: Context) {
        val log = DiagnosticLog.read().ifEmpty { context.getString(R.string.diagnostic_log_empty) }
        val text = TextView(context).apply {
            // The newest lines are the interesting ones
            text = if (log.length > SHOWN_SIZE) log.takeLast(SHOWN_SIZE) else log
            textSize = TEXT_SIZE_SP
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(PADDING_HORIZONTAL, PADDING_VERTICAL, PADDING_HORIZONTAL, PADDING_VERTICAL)
        }
        val scroll = ScrollView(context).apply { addView(text) }
        AlertDialog.Builder(context)
            .setTitle(R.string.pref_diagnostic_log_title)
            .setView(scroll)
            .setPositiveButton(R.string.diagnostic_log_share) { _, _ -> share(context) }
            .setNegativeButton(R.string.diagnostic_log_copy) { _, _ -> copy(context) }
            .setNeutralButton(R.string.diagnostic_log_clear) { _, _ -> DiagnosticLog.clear() }
            .show()
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun share(context: Context) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.diagnostic_log_subject))
            putExtra(Intent.EXTRA_TEXT, DiagnosticLog.readForSharing(context))
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.diagnostic_log_share_title)))
    }

    private fun copy(context: Context) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val label = context.getString(R.string.diagnostic_log_subject)
        clipboard.setPrimaryClip(ClipData.newPlainText(label, DiagnosticLog.readForSharing(context)))
        // Android 13 and later confirm a copy themselves
        if (!AndroidVersion.isAtLeastT) {
            Toast.makeText(context, R.string.diagnostic_log_copied, Toast.LENGTH_SHORT).show()
        }
    }
}
