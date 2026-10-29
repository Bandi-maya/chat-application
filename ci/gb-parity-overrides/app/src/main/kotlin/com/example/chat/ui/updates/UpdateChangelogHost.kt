package com.example.chat.ui.updates

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.chat.BuildConfig

private const val PREFS = "chaty_release_notes"
private const val LAST_SEEN_VERSION = "last_seen_version"

private data class ReleaseNote(val title: String, val detail: String)

private val currentReleaseNotes = listOf(
    ReleaseNote("Camera zoom controls", "Tap a zoom preset directly or expand the circular dial for finer control. Available zoom is limited by the active camera."),
    ReleaseNote("Camera interaction polish", "Zoom controls expose clear selected states and accessible labels."),
    ReleaseNote("Settings and color feedback", "Selected color swatches now show a clear selection marker, and modal dialogs no longer use the top accent strip or full outline border."),
    ReleaseNote("Startup lock reliability", "Initial app-lock scheduling retries when restored authentication and saved lock preferences become available."),
)

@Composable
fun UpdateChangelogHost() {
    val context = LocalContext.current
    var show by remember { mutableStateOf(false) }
    var resolved by remember { mutableStateOf(false) }
    val versionName = BuildConfig.VERSION_NAME.ifBlank { "current build" }
    val packageUpdatedAt = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime }.getOrDefault(0L)
    }
    val installVersion = "$versionName (${BuildConfig.VERSION_CODE}) @$packageUpdatedAt"

    LaunchedEffect(installVersion) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getString(LAST_SEEN_VERSION, null)
        show = previous != installVersion
        resolved = true
    }

    if (!resolved || !show) return

    AlertDialog(
        onDismissRequest = { markSeen(context, installVersion); show = false },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("What’s new in Chaty")
                Text("Version $versionName", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                items(currentReleaseNotes) { note ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(note.title, style = MaterialTheme.typography.titleSmall)
                            Text(note.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { Spacer(Modifier.height(2.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { markSeen(context, installVersion); show = false }) { Text("Continue") }
        },
    )
}

private fun markSeen(context: Context, versionName: String) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(LAST_SEEN_VERSION, versionName)
        .apply()
}
