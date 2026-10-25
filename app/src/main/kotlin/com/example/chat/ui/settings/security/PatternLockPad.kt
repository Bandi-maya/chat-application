package com.example.chat.ui.settings.security

import com.example.chat.ui.kit.ChatyHapticsPort
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.min

/** Geometry shared by hit-testing and painting (3x3 grid, centers at cell mid). */
internal object PatternGeometry {
    fun centers(width: Float, height: Float): List<Offset> {
        val cw = width / 3f
        val ch = height / 3f
        return List(9) { i -> Offset(cw * ((i % 3) + 0.5f), ch * ((i / 3) + 0.5f)) }
    }

    fun hitTest(p: Offset, width: Float, height: Float): Int? {
        val radius = min(width, height) / 7f
        centers(width, height).forEachIndexed { i, c ->
            if (hypot(c.x - p.x, c.y - p.y) <= radius) return i
        }
        return null
    }
}

/** Equivalent of PatternLockPadState (currentPattern, reset). */
class PatternLockPadState {
    internal val selected: SnapshotStateList<Int> = mutableStateListOf()
    internal var pointer by mutableStateOf<Offset?>(null)
    var onReset: (() -> Unit)? = null
    val currentPattern: List<Int> get() = selected.toList()
    fun reset() {
        selected.clear()
        pointer = null
        onReset?.invoke()
    }
}

/** Port of PatternLockPad / _PatternPainter (pattern_lock_pad.dart). */
@Composable
fun PatternLockPad(
    onPatternComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
    onPatternReset: (() -> Unit)? = null,
    hideTrace: Boolean = false,
    enableHaptics: Boolean = true,
    size: Dp = 280.dp,
    clearOnFinish: Boolean = true,
    state: PatternLockPadState = remember { PatternLockPadState() },
) {
    state.onReset = onPatternReset
    val view = androidx.compose.ui.platform.LocalView.current
    val activeColor = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val availableWidth = (LocalConfiguration.current.screenWidthDp - 32).coerceIn(168, 280).dp
    val effective = if (size < availableWidth) size else availableWidth

    fun selectAt(p: Offset, w: Float, h: Float) {
        val hit = PatternGeometry.hitTest(p, w, h) ?: return
        if (state.selected.contains(hit)) return
        if (enableHaptics) ChatyHapticsPort.selection(view)
        state.selected.add(hit)
        state.pointer = p
    }

    fun finish() {
        if (state.selected.isNotEmpty()) onPatternComplete(state.selected.joinToString("-"))
        state.pointer = null
    }

    Canvas(
        modifier.size(effective)
            .semantics { contentDescription = "Pattern lock grid 3 by 3" }
            .pointerInput(clearOnFinish, enableHaptics) {
                detectDragGestures(
                    onDragStart = { p ->
                        if (clearOnFinish) state.reset()
                        selectAt(p, this.size.width.toFloat(), this.size.height.toFloat())
                    },
                    onDrag = { change, _ ->
                        selectAt(change.position, this.size.width.toFloat(), this.size.height.toFloat())
                        state.pointer = change.position
                    },
                    onDragEnd = { finish() },
                    onDragCancel = { finish() },
                )
            },
    ) {
        val centers = PatternGeometry.centers(this.size.width, this.size.height)
        val selected = state.selected
        if (!hideTrace && selected.isNotEmpty()) {
            val path = Path().apply {
                moveTo(centers[selected.first()].x, centers[selected.first()].y)
                selected.drop(1).forEach { lineTo(centers[it].x, centers[it].y) }
                state.pointer?.let { lineTo(it.x, it.y) }
            }
            drawPath(
                path, activeColor,
                style = Stroke(width = 4.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        for (i in centers.indices) {
            val isSelected = selected.contains(i)
            if (isSelected) {
                if (!hideTrace) {
                    drawCircle(activeColor.copy(alpha = 0.72f), 18.dp.toPx(), centers[i], style = Stroke(width = 1.4.dp.toPx()))
                }
                drawCircle(
                    if (!hideTrace) activeColor else muted,
                    (if (!hideTrace) 6.5f else 5.5f).dp.toPx(), centers[i], style = Fill,
                )
            } else {
                drawCircle(muted.copy(alpha = 0.22f), 18.dp.toPx(), centers[i], style = Stroke(width = 1.dp.toPx()))
                drawCircle(muted.copy(alpha = 0.7f), 4.5.dp.toPx(), centers[i], style = Fill)
            }
        }
    }
}
