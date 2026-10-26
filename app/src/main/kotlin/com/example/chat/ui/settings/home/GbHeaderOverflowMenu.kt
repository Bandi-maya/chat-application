package com.example.chat.ui.settings.home

import androidx.compose.ui.draw.clip

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.ReplyAll
import androidx.compose.material.icons.outlined.ScheduleSend
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ForwardToInbox
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.ui.theme.LocalAppColors

/** Callbacks of GbHeaderOverflowMenu; null = item does nothing (as in Dart). */
data class GbHeaderOverflowActions(
    val onGbSettings: (() -> Unit)? = null,
    val onMessageScheduler: (() -> Unit)? = null,
    val onAutoReply: (() -> Unit)? = null,
    val onRestart: (() -> Unit)? = null,
    val onMessageNumber: (() -> Unit)? = null,
    val onMassMessage: (() -> Unit)? = null,
    val onNewGroup: (() -> Unit)? = null,
    val onNewCommunity: (() -> Unit)? = null,
    val onBroadcastLists: (() -> Unit)? = null,
    val onLinkedDevices: (() -> Unit)? = null,
    val onStarred: (() -> Unit)? = null,
    val onPayments: (() -> Unit)? = null,
    val onReadAll: (() -> Unit)? = null,
    val onSettings: (() -> Unit)? = null,
)

/**
 * Port of GbHeaderOverflowMenu.show (showGeneralDialog: barrier shadow@0.38, 180ms fade + scale
 * 0.92->1.0 easeOutCubic anchored top-right, menu at top = statusBar + 42, right 12) and the
 * 250dp menu itself. Every item dismisses first, then calls its callback. UNCOMPILED (Compose).
 * onBroadcastLists/onPayments exist in Dart but have no menu item; kept for parity.
 */
@Composable
fun GbHeaderOverflowMenuDialog(t: ThemeConfig, actions: GbHeaderOverflowActions, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val p by animateFloatAsState(
        if (shown) 1f else 0f, tween(180, easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)), label = "overflow",
    )
    fun pick(f: (() -> Unit)?): () -> Unit = { onDismiss(); f?.invoke() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier.fillMaxSize().alpha(p)
                .background(colors.shadow.copy(alpha = 0.38f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            Column(
                Modifier.align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 42.dp, end = 12.dp)
                    .graphicsLayer {
                        val s = 0.92f + 0.08f * p
                        scaleX = s; scaleY = s
                        transformOrigin = TransformOrigin(1f, 0f)
                    }
                    .widthIn(max = (LocalConfiguration.current.screenWidthDp.dp - 24.dp).coerceAtLeast(1.dp))
                    .width(IntrinsicSize.Max)
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.8f).dp)
                    .shadow(12.dp, com.example.chat.ui.kit.ChatyModalShape, ambientColor = Color(t.backgroundColor).copy(alpha = 0.55f), spotColor = Color(t.backgroundColor).copy(alpha = 0.55f))
                    .background(Color(t.surfaceColor), com.example.chat.ui.kit.ChatyModalShape)
                    .border(1.dp, Color(t.cardColor), com.example.chat.ui.kit.ChatyModalShape)
                    .clip(com.example.chat.ui.kit.ChatyModalShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                Item(t, Icons.Rounded.Tune, "Header & Navigation", pick(actions.onGbSettings))
                Item(t, Icons.Outlined.ScheduleSend, "Message Scheduler", pick(actions.onMessageScheduler))
                Item(t, Icons.Outlined.ReplyAll, "Auto Reply", pick(actions.onAutoReply))
                Item(t, Icons.Rounded.RestartAlt, "Restart Chaty", pick(actions.onRestart))
                Item(t, Icons.Rounded.EditNote, "Message a number", pick(actions.onMessageNumber))
                Item(t, Icons.Rounded.ForwardToInbox, "Mass Message Sender", pick(actions.onMassMessage))
                Item(t, Icons.Outlined.GroupAdd, "New group", pick(actions.onNewGroup))
                Item(t, Icons.Outlined.Groups, "New community", pick(actions.onNewCommunity))
                Item(t, Icons.Rounded.Devices, "Linked devices", pick(actions.onLinkedDevices))
                Item(t, Icons.Outlined.StarOutline, "Starred", pick(actions.onStarred))
                Item(t, Icons.Rounded.DoneAll, "Read all", pick(actions.onReadAll))
                Item(t, Icons.Outlined.Settings, "Settings", pick(actions.onSettings))
            }
        }
    }
}

@Composable
private fun Item(t: ThemeConfig, icon: ImageVector, label: String, onTap: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onTap).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color(t.secondaryTextColor), modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = Color(t.primaryTextColor), fontSize = 14.5.sp, fontWeight = FontWeight.W500, letterSpacing = (-0.1).sp, modifier = Modifier.weight(1f))
    }
}
