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
    AlertDialog(
        shape = com.example.chat.ui.kit.ChatyModalShape,
        onDismissRequest = { close() },
        title = { Text("Chaty changelog") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (historyReadOnly) item {
                    Text("Stored update history could not be read. Existing records have been kept, but this update cannot be saved to history yet.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(history, key = { it.identity }) { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Version ${entry.versionName} (${entry.versionCode})", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary)
                        Text(if (entry.identity == current.identity) "Latest changes" else "Earlier update", style = MaterialTheme.typography.labelMedium)
                        entry.changes.forEach { change ->
                            Text("\u2022 $change", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    Text("Earlier versions appear here as they are recorded on this device. App data removal also removes this local history.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { ChatyTextButton(onClick = { close() }, label = "Close") },
    )
}

