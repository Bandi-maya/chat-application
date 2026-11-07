package com.example.chat.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.chat.ui.theme.LocalAppColors

/** Centered settings selector. Header/footer never share the body's scroll container. */
@Composable
fun ChatySelectionModal(
    title: String,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    applyEnabled: Boolean = true,
    applyLabel: String = "Apply",
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    val layoutShape = androidx.compose.foundation.shape.RoundedCornerShape(LocalReferenceLayoutPresentation.current.menuCornerDp.dp)
    val config = LocalConfiguration.current
    val width = (config.screenWidthDp - 32).coerceIn(240, 600)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
    )) {
        val cardBounds = remember { arrayOf(Rect.Zero) }
        val viewport = remember { arrayOfNulls<LayoutCoordinates>(1) }
        // Resolve insets inside the dialog window, not the underlying activity.
        // One full-window owner consumes safe drawing + IME before measuring the card.
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier.fillMaxSize()
                .onGloballyPositioned { viewport[0] = it }
                .pointerInput(onDismiss) {
                    detectTapGestures { point ->
                        val windowPoint = viewport[0]?.localToWindow(point)
                        if (windowPoint != null && !cardBounds[0].contains(windowPoint)) onDismiss()
                    }
                }
                .safeDrawingPadding().imePadding().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val height = minOf(config.screenHeightDp * 0.80f, maxHeight.value).dp
            val compactChrome = maxHeight < 220.dp && maxWidth >= 480.dp
            Column(
                Modifier.onGloballyPositioned { cardBounds[0] = it.boundsInWindow() }
                    .sizeIn(maxWidth = width.dp, maxHeight = height)
                    .fillMaxWidth().shadow(20.dp, layoutShape).clip(layoutShape)
                    .background(colors.surfaceElevated).border(1.dp, colors.border, layoutShape),
            ) {
                if (compactChrome) Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(title, Modifier.weight(1f), color = colors.foreground, fontSize = 16.sp,
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    ChatyChoiceTile("Cancel", onDismiss, Modifier.weight(0.8f))
                    ChatyChoiceTile(applyLabel, onApply, Modifier.weight(0.8f), enabled = applyEnabled, prominent = true, leadingIcon = com.example.chat.ui.icons.ChatyIconId.check)
                    IconButton(onClick = onDismiss) { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.close, semanticLabel = "Close $title", color = colors.foregroundSecondary) }
                } else Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), color = colors.foreground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.close, semanticLabel = "Close $title", color = colors.foregroundSecondary) }
                }
                Spacer(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                Box(Modifier.weight(1f, fill = false).fillMaxWidth()) { content() }
                if (!compactChrome) {
                Spacer(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChatyChoiceTile("Cancel", onDismiss, Modifier.weight(1f))
                    ChatyChoiceTile(applyLabel, onApply, Modifier.weight(1f), enabled = applyEnabled, prominent = true, leadingIcon = com.example.chat.ui.icons.ChatyIconId.check)
                }
                }
            }
        }
    }
}
