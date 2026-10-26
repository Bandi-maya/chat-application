package com.example.chat.ui.chats.detail

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Export-copy editor, honest about keyframe alignment and compatible codecs. */
@Composable
internal fun MediaTrimScreen(source: String, video: Boolean, onCancel: () -> Unit, onApply: (MediaTrimResult) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var duration by remember { mutableLongStateOf(0) }
    var range by remember { mutableStateOf(0f..1f) }
    var exportJob by remember { mutableStateOf<Job?>(null) }
    var active by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { active = false; exportJob?.cancel() } }
    fun cancelTrim() { exportJob?.cancel(); onCancel() }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(source) {
        try {
            duration = withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try { retriever.setDataSource(context, Uri.parse(source)); retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0 }
                finally { retriever.release() }
            }
            check(duration > 0) { "Media duration is unavailable." }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Cannot read media" }
    }
    androidx.activity.compose.BackHandler(onBack = ::cancelTrim)
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(::cancelTrim) { Text("Cancel") }
                Text("Trim a copy", Modifier.padding(12.dp))
                TextButton(onClick = {
                    busy = true
                    exportJob = scope.launch {
                        var output: MediaTrimResult? = null
                        var handedOff = false
                        try {
                            val start = (duration * range.start).toLong()
                            val end = (duration * range.endInclusive).toLong()
                            output = trimMediaCopy(context, source, start, end, video)
                            coroutineContext.ensureActive()
                            if (active) { onApply(output!!); handedOff = true }
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message ?: "Unable to trim. Original kept unchanged." }
                        finally { if (!handedOff) output?.file?.delete(); busy = false }
                    }
                }, enabled = duration > 0 && !busy && range.endInclusive - range.start > 0.01f) { Text(if (busy) "Exporting" else "Apply") }
            }
            Text(if (video) "MP4 video/AAC audio copy. Video starts at the previous keyframe, so the cut may begin slightly earlier." else "AAC audio to M4A copy. Other audio formats are kept unchanged and report unsupported.")
            Spacer(Modifier.height(24.dp))
            if (duration > 0) {
                Text("Start: ${"%.2f".format(duration * range.start / 1000)} s   End: ${"%.2f".format(duration * range.endInclusive / 1000)} s")
                RangeSlider(value = range, onValueChange = { range = it }, enabled = !busy, valueRange = 0f..1f)
            } else if (error == null) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 16.dp)) }
            Spacer(Modifier.weight(1f))
            Text("Apply creates a new local file. Your original message and file are not changed.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
