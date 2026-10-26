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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    val config = LocalConfiguration.current
    val width = (config.screenWidthDp - 32).coerceIn(240, 600)
    // Max 80% of the screen height, never taller than the safe window (system bars/cutout) minus the 16dp dialog
    // padding on each side. Content-sized below that: short lists stay short, long lists scroll in the middle
    // while the title/X and Cancel/Apply stay fixed.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val insets = androidx.compose.foundation.layout.WindowInsets.safeDrawing
    val safeHeightDp = with(density) { config.screenHeightDp - (insets.getTop(this) + insets.getBottom(this)).toDp().value }
    val height = minOf(config.screenHeightDp * 0.80f, safeHeightDp - 32f).coerceAtLeast(160f)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).imePadding().sizeIn(maxWidth = width.dp, maxHeight = height.dp)
                .fillMaxWidth().shadow(20.dp, ChatyModalShape).clip(ChatyModalShape)
                .background(colors.surfaceElevated).border(1.dp, colors.border, ChatyModalShape),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), color = colors.foreground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Close $title", tint = colors.foregroundSecondary) }
            }
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            Box(Modifier.weight(1f, fill = false).fillMaxWidth()) { content() }
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChatyChoiceTile("Cancel", onDismiss, Modifier.weight(1f))
                ChatyChoiceTile(applyLabel, onApply, Modifier.weight(1f), enabled = applyEnabled, prominent = true)
            }
        }
    }
}
