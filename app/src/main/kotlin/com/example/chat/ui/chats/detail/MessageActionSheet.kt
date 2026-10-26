package com.example.chat.ui.chats.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.Shortcut
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.domain.models.ChatMessage

/** Port of lib/features/messages/message_action_sheet.dart's Android
 *  presentation (bottom sheet; the iOS-style floating menu in the Dart file
 *  is a preference-gated variant of the same actions). Quick emoji rail,
 *  grouped action list, destructive section. */
val quickSheetEmojis = listOf("👍", "❤️", "🔥", "🎉", "👀", "🚀")

@Composable
fun MessageActionSheetContent(
    message: ChatMessage,
    isMe: Boolean,
    theme: ThemeConfig,
    onReact: (String) -> Unit,
    onOpenAllReactions: () -> Unit,
    onAction: (MessageActionType) -> Unit,
) {
    val surface = Color(theme.surfaceColor)
    val surfaceSecondary = Color(argbWithAlpha(theme.secondaryTextColor, 0.12))
    val foreground = Color(theme.primaryTextColor)
    val foregroundSecondary = Color(theme.secondaryTextColor)
    val accent = Color(theme.accentColor)
    val danger = Color(theme.dangerColor)
    val actions = MessageActionRegistry.getAvailableActions(message = message, isMe = isMe)

    Column(
        Modifier
            .fillMaxWidth()
            .background(surface, com.example.chat.ui.kit.ChatyModalShape)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Drag handle
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .width(38.dp)
                .height(4.5.dp)
                .background(
                    Color(argbWithAlpha(theme.secondaryTextColor, 0.2)),
                    RoundedCornerShape(999.dp),
                ),
        )
        Box(Modifier.height(16.dp))
        // Quick emoji rail
        Row(
            Modifier
                .fillMaxWidth()
                .background(surfaceSecondary, RoundedCornerShape(999.dp))
                .padding(vertical = 6.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            quickSheetEmojis.forEach { emoji ->
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .pointerInput(emoji) { detectTapGestures(onTap = { onReact(emoji) }) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emoji, fontSize = 28.sp)
                }
            }
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(argbWithAlpha(theme.secondaryTextColor, 0.18)))
                    .pointerInput("more") { detectTapGestures(onTap = { onOpenAllReactions() }) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "More reactions", tint = foreground,
                    modifier = Modifier.size(22.dp))
            }
        }
        Box(Modifier.height(16.dp))

        // Primary + secondary actions (grouped card)
        ActionGroupCard(theme) {
            actions.filter { !it.isDestructive }.forEach { action ->
                ActionRow(
                    icon = actionIcon(action, message),
                    label = actionSheetLabel(action),
                    color = when (action.type) {
                        MessageActionType.reply, MessageActionType.forward,
                        MessageActionType.task -> accent
                        MessageActionType.star -> Color(0xFFFFC107)
                        else -> foregroundSecondary
                    },
                    textColor = foreground,
                    onTap = { onAction(action.type) },
                )
            }
        }
        Box(Modifier.height(10.dp))
        // Destructive + report section
        ActionGroupCard(theme) {
            actions.filter { it.isDestructive }.forEach { action ->
                ActionRow(
                    icon = actionIcon(action, message),
                    label = actionSheetLabel(action),
                    color = danger,
                    textColor = danger,
                    onTap = { onAction(action.type) },
                )
            }
            ActionRow(
                icon = Icons.Outlined.Flag,
                label = "Report Message",
                color = foregroundSecondary,
                textColor = foregroundSecondary,
                onTap = { onAction(MessageActionType.report) },
            )
        }
        Box(Modifier.height(8.dp))
    }
}

/** Dart sheet labels differ from the registry labels for a few actions. */
private fun actionSheetLabel(action: MessageActionDescriptor): String = when (action.type) {
    MessageActionType.task -> "Create Task from Message"
    MessageActionType.pin -> if (action.label == "Unpin") "Unpin Message" else "Pin Message"
    MessageActionType.star -> if (action.label == "Unstar") "Unstar Message" else "Star Message"
    MessageActionType.copy -> "Copy Text"
    else -> action.label
}

fun actionIcon(action: MessageActionDescriptor, message: ChatMessage): ImageVector =
    when (action.type) {
        MessageActionType.reply -> Icons.Rounded.Reply
        MessageActionType.forward -> Icons.Rounded.Shortcut
        MessageActionType.task -> Icons.Rounded.TaskAlt
        MessageActionType.pin -> Icons.Outlined.PushPin
        MessageActionType.star -> if (message.isStarred) Icons.Rounded.StarBorder else Icons.Rounded.Star
        MessageActionType.copy -> Icons.Rounded.ContentCopy
        MessageActionType.edit -> Icons.Rounded.Edit
        MessageActionType.select -> Icons.Rounded.Checklist
        MessageActionType.info -> Icons.Outlined.Info
        MessageActionType.deleteForMe -> Icons.Outlined.DeleteOutline
        MessageActionType.deleteForEveryone -> Icons.Outlined.DeleteForever
        MessageActionType.report -> Icons.Outlined.Flag
        else -> Icons.Outlined.Info
    }

@Composable
private fun ActionGroupCard(theme: ThemeConfig, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Color(argbWithAlpha(theme.secondaryTextColor, 0.08)),
                RoundedCornerShape(16.dp),
            ),
    ) {
        content()
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    color: Color,
    textColor: Color,
    onTap: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(label) { detectTapGestures(onTap = { onTap() }) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Box(Modifier.width(12.dp))
        Text(label, color = textColor, fontWeight = FontWeight.W600, fontSize = 15.sp)
    }
}

/** Anchored context-menu variant (Dart AppContextMenu.showWithReactionRail):
 *  a compact card with the quick-reaction rail on top and registry actions
 *  below. Shown in a Popup positioned at the bubble by the caller. */
