package com.example.chat.ui.chats.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.LooksOne
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

internal data class MediaReviewItem(val source: String, val file: PickedFile, val type: String)
internal data class ReviewedMedia(val items: List<MediaReviewItem>, val caption: String, val viewOnce: Boolean)

/** Picking and editing stay local. No upload starts until Send is explicitly tapped. */
@Composable
internal fun MediaReviewScreen(items: List<MediaReviewItem>, onCancel: () -> Unit, onSend: (ReviewedMedia) -> Unit) {
    if (items.isEmpty()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            Text("No attachments selected")
            TextButton(onClick = onCancel) { Text("Back") }
        }
        return
    }
    var current by remember(items) { mutableStateOf(items) }
    var index by remember { mutableIntStateOf(0) }
    var caption by remember { mutableStateOf("") }
    var viewOnce by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var trimming by remember { mutableStateOf(false) }
    val ownedEdits = remember(items) { mutableListOf<File>() }
    var submitted by remember(items) { mutableStateOf(false) }
    DisposableEffect(items) { onDispose { if (!submitted) ownedEdits.forEach { it.delete() } } }
    val context = LocalContext.current
    // GB/stock parity: the sender sees the view-once explainer exactly once.
    val viewOnceNuxStore = remember(context) { com.example.chat.data.media.ViewOnceNuxStore(context) }
    var viewOnceSenderNux by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(onBack = onCancel)
    val item = current[index.coerceIn(current.indices)]
    if (editing) {
        ImageEditorScreen(item.source, onCancel = { editing = false }, onApply = { file ->
            ownedEdits += file
            current = current.mapIndexed { i, old -> if (i == index) old.copy(source = file.toURI().toString(), file = PickedFile(name = file.name, path = file.path, mimeType = "image/jpeg")) else old }
            editing = false
        })
        return
    }
    if (trimming) {
        MediaTrimScreen(item.source, true, onCancel = { trimming = false }, onApply = { result ->
            ownedEdits += result.file
            current = current.mapIndexed { i, old -> if (i == index) old.copy(source = result.file.toURI().toString(),
                file = PickedFile(name = result.file.name, path = result.file.path, mimeType = "video/mp4")) else old }
            trimming = false
        })
        return
    }
    Column(Modifier.fillMaxSize().background(Color.Black)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        .imePadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onCancel) { Text("Cancel") }
            Text("Review", color = Color.White, modifier = Modifier.weight(1f))
            if (item.type == "video") TextButton({ trimming = true }) { Text("Trim a copy") }
            if (item.type == "image" && item.file.mimeType !in setOf("image/gif", "image/webp")) TextButton({ editing = true }) { Text("Edit") }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (item.type == "image") AsyncImage(model = item.source, contentDescription = "Photo preview", modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            else androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx -> android.widget.VideoView(ctx).apply { setMediaController(android.widget.MediaController(ctx).also { it.setAnchorView(this) }) } },
                update = { view -> if (view.tag != item.source) { view.tag = item.source; view.setVideoURI(android.net.Uri.parse(item.source)); view.setOnPreparedListener { view.seekTo(1) } } },
                modifier = Modifier.fillMaxSize(),
                onRelease = { it.stopPlayback() },
            )
        }
        if (current.size > 1) LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(current) { i, _ -> TextButton({ index = i }) { Text(if (i == index) "[${i + 1}]" else "${i + 1}") } }
        }
        if (viewOnce) Text("Requests view once. This build cannot prevent capture or enforce deletion on every recipient.", color = Color(0xFFFFD180), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall)
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(caption, { caption = it }, modifier = Modifier.weight(1f), placeholder = { Text("Add a caption") }, maxLines = 3,
                trailingIcon = {
                    IconToggleButton(viewOnce, { enabled ->
                        if (enabled && viewOnceNuxStore.needsSenderNux()) viewOnceSenderNux = true
                        viewOnce = enabled
                    }) { Icon(Icons.Rounded.LooksOne, if (viewOnce) "View once on" else "View once off", tint = if (viewOnce) Color(0xFF00D9A0) else Color.Gray) }
                    if (viewOnceSenderNux) {
                        AlertDialog(
                            onDismissRequest = { viewOnceSenderNux = false; viewOnceNuxStore.markSenderSeen() },
                            confirmButton = {
                                TextButton(onClick = { viewOnceSenderNux = false; viewOnceNuxStore.markSenderSeen() }) { Text("Got it") }
                            },
                            title = { Text("View once") },
                            text = { Text("Recipients are asked to open this photo or video only once. It expires on their device after the first open unless their own settings keep opened items. People can still take screenshots or photos of the screen.") },
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
            )
            FilledIconButton(onClick = {
                // Keep only final files for the receiving upload flow; release superseded local edits.
                val finalPaths = current.mapNotNull { it.file.path }.toSet()
                ownedEdits.filter { it.path !in finalPaths }.forEach { it.delete() }
                onSend(ReviewedMedia(current, caption.trim(), viewOnce)); submitted = true
            }, modifier = Modifier.padding(start = 8.dp).size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.Send, "Send attachment")
            }
        }
    }
}
