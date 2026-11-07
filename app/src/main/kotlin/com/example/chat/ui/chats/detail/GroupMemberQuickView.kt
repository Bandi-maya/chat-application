package com.example.chat.ui.chats.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Videocam
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.kit.ChatyConfirmationDialog
import com.example.chat.ui.theme.LocalAppColors

/** Chaty #29: group sender avatar quick view.
 *
 *  Every action is an optional host seam: a button renders only when the host
 *  wired that callback, so the card never shows a dead control. Navigation,
 *  call setup and contact routing belong to the integration owner; this file
 *  is presentation only. */
data class GroupMemberQuickActions(
    val message: ((String) -> Unit)? = null,
    val voiceCall: ((String) -> Unit)? = null,
    val videoCall: ((String) -> Unit)? = null,
    val info: ((String) -> Unit)? = null,
) {
    val any: Boolean get() = message != null || voiceCall != null || videoCall != null || info != null
}

@Composable
fun GroupMemberQuickViewDialog(
    senderId: String,
    senderName: String,
    actions: GroupMemberQuickActions,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    ChatyConfirmationDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                // Square avatar card with the same initials treatment as the
                // in-timeline group avatar (no invented photo or badge).
                Box(
                    Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        senderName.split(" ").filter { it.isNotEmpty() }
                            .take(2).joinToString("") { it[0].toString() },
                        color = colors.onAccent,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    senderName,
                    color = colors.foreground,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.W700,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        text = {
            if (actions.any) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions.message?.let { action ->
                        QuickViewAction("Message", Icons.Outlined.ChatBubbleOutline) { action(senderId) }
                    }
                    actions.voiceCall?.let { action ->
                        QuickViewAction("Voice", Icons.Outlined.Call) { action(senderId) }
                    }
                    actions.videoCall?.let { action ->
                        QuickViewAction("Video", Icons.Outlined.Videocam) { action(senderId) }
                    }
                    actions.info?.let { action ->
                        QuickViewAction("Info", Icons.Outlined.Info) { action(senderId) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun QuickViewAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(colors.surfaceElevated, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.accent)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = colors.foregroundSecondary, fontSize = 12.sp)
    }
}
