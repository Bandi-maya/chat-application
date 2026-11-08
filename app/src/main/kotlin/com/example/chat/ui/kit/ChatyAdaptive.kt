package com.example.chat.ui.kit

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Responsive foundation (approved plan 8 Nov 2026, rules R1-R3, R7).
 *  The window is measured once at the root and passed down; no screen
 *  assumes a fixed phone width. Rotation, split-screen and folding
 *  re-measure automatically. */

data class ChatyWindowMetrics(
    val widthDp: Int,
    val heightDp: Int,
    val bucket: ChatyWidthBucket,
)

val LocalChatyWindowMetrics = compositionLocalOf {
    ChatyWindowMetrics(0, 0, ChatyWidthBucket.Compact)
}

/** R5: in-app chat font size (Small 0.85 / Default 1.0 / Large 1.15),
 *  applied to message text and timestamps only. Stored locally; the rest
 *  of the app follows the system font size. */
val LocalChatFontScale = compositionLocalOf { 1.0f }

/** R4: clamp a label's font scale so large system fonts cannot break its
 *  container: scale up to [maxScale], then clamp (caller ellipsizes). */
@Composable
fun clampedFontScale(maxScale: Float = 1.3f): Float =
    androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtMost(maxScale)

/** R4: fixed-container labels (header username, home tab labels, capsule
 *  chips) follow the system font size up to [maxScale], then clamp; the
 *  caller ellipsizes. The caller's own multiplier (e.g. theme.fontScale)
 *  stays untouched - only the system contribution is clamped. */
@Composable
fun clampedLabelSp(baseSp: Float, maxScale: Float = 1.3f): TextUnit {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val clamped = density.fontScale.coerceAtMost(maxScale)
    return (baseSp * clamped / density.fontScale).sp
}

/** R1: measure the current window once; re-measures on rotate/split/fold. */
@Composable
fun ChatyWindowMetricsProvider(content: @Composable (ChatyWindowMetrics) -> Unit) {
    BoxWithConstraints {
        val metrics = ChatyWindowMetrics(
            widthDp = maxWidth.value.toInt(),
            heightDp = maxHeight.value.toInt(),
            bucket = ChatyAdaptiveLogic.bucketOf(maxWidth.value.toInt()),
        )
        androidx.compose.runtime.CompositionLocalProvider(LocalChatyWindowMetrics provides metrics) {
            content(metrics)
        }
    }
}

/** R2/R3: cap content at [maxDp] and center it inside the available width.
 *  Compact phones fill exactly as before (cap >= their width). Center with
 *  a parent Box(contentAlignment = Alignment.TopCenter) or
 *  Column(horizontalAlignment = Alignment.CenterHorizontally). */
fun Modifier.adaptiveColumn(maxDp: Int): Modifier =
    this.fillMaxWidth().widthIn(max = maxDp.dp)
