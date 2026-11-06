package com.example.chat.ui.chats.detail

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.chat.domain.models.ChatMessage

@Composable
internal fun CompactMediaAlbum(
    messages: List<ChatMessage>, binding: AttachmentConsumerBinding?, remoteImage: RemoteImageSlot?,
    resolveUrl: (suspend (String) -> String)?,
    onOpen: (ChatMessage, Rect?) -> Unit, onMenu: (ChatMessage, Rect?) -> Unit,
    onReply: (ChatMessage) -> Unit = {}, onReact: ((ChatMessage) -> Unit)? = null,
) {
    Column(Modifier.widthIn(max = 300.dp).fillMaxWidth().clip(RoundedCornerShape(9.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        var start = 0
        for (columns in mediaAlbumColumns(messages.size)) {
            val members = messages.subList(start, start + columns)
            start += columns
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (message in members) key(message.id) {
                    var bounds by remember { mutableStateOf<Rect?>(null) }
                    Box(Modifier.weight(1f).aspectRatio(if (columns == 1) 2f else 1f)
                        .onGloballyPositioned { bounds = it.boundsInWindow() }
                        .semantics { contentDescription = "Album photo ${messages.indexOf(message) + 1} of ${messages.size}" }
                        .pointerInput(message.id) { detectTapGestures(
                            onTap = { onOpen(message, bounds) }, onLongPress = { onMenu(message, bounds) },
                            onDoubleTap = onReact?.let { callback -> { callback(message) } }) }
                        .pointerInput(message.id) {
                            var distance = 0f
                            detectHorizontalDragGestures(onDragStart = { distance = 0f },
                                onDragEnd = { if (kotlin.math.abs(distance) >= 56.dp.toPx()) onReply(message) },
                                onHorizontalDrag = { change, amount -> change.consume(); distance += amount })
                        }) {
                        MediaAttachmentPreview(message.attachment!!, remoteImage, resolveUrl, null, false,
                            message, binding, fixedRatio = if (columns == 1) 2f else 1f)
                    }
                }
            }
        }
    }
}
