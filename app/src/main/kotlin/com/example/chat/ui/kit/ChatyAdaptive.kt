package com.example.chat.ui.kit

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
