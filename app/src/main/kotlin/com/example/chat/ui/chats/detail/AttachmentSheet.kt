package com.example.chat.ui.chats.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PinDrop
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha

/** Port of lib/features/messages/attachment_sheet.dart: one predictable grid
 *  of attachment actions plus the demo sandbox dock. Callbacks are owned by
 *  the screen; the upload pipeline itself is the attachment-service slice. */

private val gradientGallery = listOf(Color(0xFFE91E63), Color(0xFFC2185B))
private val gradientVideo = listOf(Color(0xFFFF5722), Color(0xFFE64A19))
private val gradientDocument = listOf(Color(0xFF7F66FF), Color(0xFF5E35B1))
private val gradientAudio = listOf(Color(0xFFFF9800), Color(0xFFF57C00))
private val gradientLocation = listOf(Color(0xFF2E7D32), Color(0xFF1B5E20))
private val gradientContact = listOf(Color(0xFF0097A7), Color(0xFF006064))
private val gradientPoll = listOf(Color(0xFF0288D1), Color(0xFF01579B))
private val gradientTask = listOf(Color(0xFF00BFA5), Color(0xFF00897B))

enum class AttachmentAction { image, video, document, audio, location, contact, poll, task,
    demoDocument, demoAudio, demoCall, demoTask, demoLocation }

private data class AttachmentItem(
    val icon: ImageVector,
    val label: String,
    val gradient: List<Color>,
    val action: AttachmentAction,
)

@Composable
fun AttachmentSheetContent(
    theme: ThemeConfig,
    onAction: (AttachmentAction) -> Unit,
) {
    val accent = Color(theme.accentColor)
    val foreground = Color(theme.primaryTextColor)
    val foregroundSecondary = Color(theme.secondaryTextColor)

    val items = listOf(
        AttachmentItem(Icons.Rounded.PhotoLibrary, "Gallery", gradientGallery, AttachmentAction.image),
        AttachmentItem(Icons.Rounded.Videocam, "Video", gradientVideo, AttachmentAction.video),
        AttachmentItem(Icons.Rounded.InsertDriveFile, "Document", gradientDocument, AttachmentAction.document),
        AttachmentItem(Icons.Rounded.GraphicEq, "Audio", gradientAudio, AttachmentAction.audio),
        AttachmentItem(Icons.Rounded.LocationOn, "Location", gradientLocation, AttachmentAction.location),
        AttachmentItem(Icons.Rounded.Person, "Contact", gradientContact, AttachmentAction.contact),
        AttachmentItem(Icons.Rounded.Poll, "Poll", gradientPoll, AttachmentAction.poll),
        AttachmentItem(Icons.Rounded.TaskAlt, "Task", gradientTask, AttachmentAction.task),
    )

    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Color(theme.surfaceColor),
                com.example.chat.ui.kit.ChatyModalShape,
            )
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .width(36.dp)
                .height(4.dp)
                .background(
                    Color(argbWithAlpha(theme.secondaryTextColor, 0.25)),
                    RoundedCornerShape(999.dp),
                ),
        )
        Box(Modifier.height(12.dp))

        // Demo sandbox dock
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .background(accent.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                .border(1.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))),
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Science, contentDescription = null, tint = Color.White,
                        modifier = Modifier.size(12.dp))
                    Box(Modifier.width(4.dp))
                    Text(
                        "DEMO SANDBOX",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.W800,
                        letterSpacing = 0.5.sp,
                    )
                }
                Box(Modifier.width(8.dp))
                Text(
                    "1-Tap test chat components & island hooks",
                    color = foregroundSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W600,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                DemoPill(Icons.Rounded.Description, "Test Doc (PDF)", gradientDocument) {
                    onAction(AttachmentAction.demoDocument)
                }
                Box(Modifier.width(8.dp))
                DemoPill(Icons.Rounded.GraphicEq, "Test Music (MP3)", gradientAudio) {
                    onAction(AttachmentAction.demoAudio)
                }
                Box(Modifier.width(8.dp))
                DemoPill(Icons.Rounded.CallEnd, "Test Call Card", gradientTask) {
                    onAction(AttachmentAction.demoCall)
                }
                Box(Modifier.width(8.dp))
                DemoPill(Icons.Rounded.TaskAlt, "Test Task Card",
                    listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))) {
                    onAction(AttachmentAction.demoTask)
                }
                Box(Modifier.width(8.dp))
                DemoPill(Icons.Rounded.PinDrop, "Test Location",
                    listOf(Color(0xFF10B981), Color(0xFF059669))) {
                    onAction(AttachmentAction.demoLocation)
                }
            }
        }

        // 3-column grid (Dart: 3 columns when width >= 220 and textScale <= 1.6)
        val rows = items.chunked(3)
        rows.forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { item ->
                    AttachmentGridTile(item, Modifier.weight(1f), accent, foreground) {
                        onAction(item.action)
                    }
                }
                repeat(3 - rowItems.size) { Box(Modifier.weight(1f)) }
            }
            Box(Modifier.height(12.dp))
        }
        // The Dart sheet's "encrypted on this device" footer is dropped on
        // purpose: the Kotlin REST backend has no E2EE, so the claim would be
        // false here (per integration-owner instruction).
    }
}

@Composable
private fun DemoPill(
    icon: ImageVector,
    label: String,
    gradient: List<Color>,
    onTap: () -> Unit,
) {
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .background(gradient.first().copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .border(1.dp, gradient.first().copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .pointerInput(label) { detectTapGestures(onTap = { onTap() }) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .background(Brush.linearGradient(gradient), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Box(Modifier.width(8.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.1).sp)
    }
}

@Composable
private fun AttachmentGridTile(
    item: AttachmentItem,
    modifier: Modifier,
    accent: Color,
    foreground: Color,
    onTap: () -> Unit,
) {
    Column(
        modifier
            .heightIn(min = 76.dp)
            .pointerInput(item.label) { detectTapGestures(onTap = { onTap() }) }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(54.dp)
                .background(accent.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = item.label, tint = accent, modifier = Modifier.size(28.dp))
        }
        Box(Modifier.height(7.dp))
        Text(
            item.label,
            color = foreground,
            fontSize = 12.sp,
            fontWeight = FontWeight.W700,
            letterSpacing = (-0.1).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
