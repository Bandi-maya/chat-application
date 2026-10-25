package com.example.chat.ui.chats.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.ui.kit.ChatyHapticsPort

/** Port of lib/ui/core/design_system/components/media_draft_tray.dart:
 *  the post-pick / pre-send review surface. Remove and send stay explicit;
 *  destructive actions stay local to the draft.
 *
 *  colorScheme mapping: surface -> theme.surfaceColor, onSurface ->
 *  primaryTextColor, surfaceContainerHighest -> cardColor, primary ->
 *  accentColor, onSurfaceVariant -> secondaryTextColor.
 *
 *  DEVIATION (flagged): the Dart footer always shows a lock icon +
 *  'Encrypted before upload'. The Kotlin REST port makes no E2EE assertion
 *  (standing rule), so the badge renders only when [showEncryptionBadge] is
 *  explicitly set by a caller that has verified the upload path encrypts.
 *  Haptics via canonical ui.kit.ChatyHapticsPort (warning on remove,
 *  success on send). */
data class ChatyMediaDraftItem(
    val id: String,
    val preview: @Composable () -> Unit,
    val label: String,
    val subtitle: String? = null,
)

@Composable
fun ChatyMediaDraftTray(
    items: List<ChatyMediaDraftItem>,
    theme: ThemeConfig,
    onRemove: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    onCaptionChange: (String) -> Unit = {},
    onAddMore: (() -> Unit)? = null,
    sending: Boolean = false,
    showEncryptionBadge: Boolean = false,
) {
    if (items.isEmpty()) return
    val view = LocalView.current
    val surface = Color(theme.surfaceColor)
    val onSurface = Color(theme.primaryTextColor)
    val containerHigh = Color(theme.cardColor)
    val primary = Color(theme.accentColor)
    val onSurfaceVariant = Color(theme.secondaryTextColor)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = surface,
    ) {
        Column(
            Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (items.size == 1) "Ready to send" else "${items.size} items ready",
                    color = onSurface,
                    fontWeight = FontWeight.W800,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                if (onAddMore != null) {
                    IconButton(
                        onClick = onAddMore,
                        enabled = !sending,
                        modifier = Modifier.semantics {
                            contentDescription = "Add more attachments"
                        },
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = onSurface)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(104.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    DraftTile(
                        item = item,
                        enabled = !sending,
                        containerColor = containerHigh,
                        onRemove = {
                            ChatyHapticsPort.warning(view)
                            onRemove(item.id)
                        },
                    )
                }
            }
            if (caption != null) {
                Spacer(Modifier.height(10.dp))
                BasicTextField(
                    value = caption,
                    onValueChange = onCaptionChange,
                    enabled = !sending,
                    minLines = 1,
                    maxLines = 3,
                    textStyle = TextStyle(color = onSurface, fontSize = 14.sp),
                    cursorBrush = SolidColor(primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(containerHigh, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    decorationBox = { inner ->
                        Box {
                            if (caption.isEmpty()) {
                                Text("Add a caption…", color = onSurfaceVariant, fontSize = 14.sp)
                            }
                            inner()
                        }
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showEncryptionBadge) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Encrypted before upload",
                        color = onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.W600,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Button(
                    onClick = {
                        ChatyHapticsPort.success(view)
                        onSend()
                    },
                    enabled = !sending,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    if (sending) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (sending) "Sending" else "Send")
                }
            }
        }
    }
}

/** Dart _DraftTile: 96dp-wide preview tile with bottom label pill and a
 *  top-right remove affordance. */
@Composable
private fun DraftTile(
    item: ChatyMediaDraftItem,
    enabled: Boolean,
    containerColor: Color,
    onRemove: () -> Unit,
) {
    Box(Modifier.width(96.dp).fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(containerColor),
        ) {
            item.preview()
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                .fillMaxWidth()
                .background(Color(0x94000000), RoundedCornerShape(10.dp)) // black @ .58
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Text(
                item.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.W700,
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 4.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(0xB8000000))
                .semantics { contentDescription = "Remove ${item.label}" }
                .clickable(enabled = enabled, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}
