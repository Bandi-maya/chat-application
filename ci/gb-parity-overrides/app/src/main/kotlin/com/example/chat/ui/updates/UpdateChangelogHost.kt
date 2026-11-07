package com.example.chat.ui.updates

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import com.example.chat.ui.kit.ChatyTextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.BuildConfig

private const val PREFS = "chaty_release_notes"
private const val LAST_SEEN_IDENTITY = "last_seen_release_identity_v2"
private const val HISTORY = "release_history_v1"
private const val QUARANTINE = "release_history_quarantine_v1"

/** Uses only this app's notes. No network requests, promotional links or copied reference data. */
@Composable
fun UpdateChangelogHost(onFinished: () -> Unit = {}, forceShow: Boolean = false) {
    val context = LocalContext.current
    val current = remember { ReleaseNotesPolicy.current(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong()) }
    var show by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(emptyList<ReleaseHistoryEntry>()) }
    var historyReadOnly by remember { mutableStateOf(false) }

    LaunchedEffect(current.identity) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val rawHistory = prefs.getString(HISTORY, null)
        val recovered = ReleaseHistoryCodec.recover(rawHistory, prefs.getString(QUARANTINE, null))
        history = ReleaseNotesPolicy.history(current, recovered.entries)
        historyReadOnly = !recovered.canWrite
        if (recovered.canWrite) {
            val edit = prefs.edit().putString(HISTORY, ReleaseHistoryCodec.write(history))
            recovered.quarantine?.let { edit.putString(QUARANTINE, it) }
            edit.apply()
        }
        // The old last_seen_version value includes installation time and cannot identify notes.
        // Leave it intact; this release introduces the stable identity once, without fake backfill.
        show = forceShow || ReleaseNotesPolicy.shouldShow(prefs.getString(LAST_SEEN_IDENTITY, null), current)
        if (!show) onFinished()
    }

    if (!show) return
    fun close() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(LAST_SEEN_IDENTITY, current.identity).apply()
        show = false
        onFinished()
    }
    com.example.chat.ui.kit.ChatyReviewModal("Chaty changelog", "Release history", ::close) {
        val colors = com.example.chat.ui.theme.LocalAppColors.current
        if (historyReadOnly) Text("Stored update history could not be read. Existing records have been kept, but this update cannot be saved to history yet.", color = colors.foregroundSecondary)
        history.forEach { entry ->
            Text("Version ${entry.versionName} (${entry.versionCode})", color = colors.primary,
                style = MaterialTheme.typography.titleLarge)
            Text(if (entry.identity == current.identity) "Latest changes" else "Earlier update", color = colors.foregroundSecondary)
            androidx.compose.material3.HorizontalDivider(modifier = Modifier.fillMaxWidth(), color = colors.borderSubtle)
            entry.changes.forEach { change ->
                Text("\u2022 $change", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge,
                    color = colors.foreground, lineHeight = 24.sp)
                androidx.compose.foundation.layout.Spacer(Modifier.heightIn(min = 14.dp))
            }
        }
        Text("Earlier versions appear here as they are recorded on this device. App data removal also removes this local history.",
            style = MaterialTheme.typography.labelSmall, color = colors.foregroundSecondary)
    }
}
