package com.example.chat.ui.icons

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.LocalContentColor
import com.example.chat.core.preferences.ChatyPreferencesController
import androidx.compose.ui.geometry.Offset as CxOffset
import androidx.compose.ui.geometry.Rect as CxRect
import androidx.compose.ui.geometry.RoundRect as CxRoundRect
import androidx.compose.ui.geometry.CornerRadius as CxCornerRadius
import androidx.compose.ui.graphics.Canvas as CxCanvas
import androidx.compose.ui.graphics.Color as CxColor
import androidx.compose.ui.graphics.Paint as CxPaint
import androidx.compose.ui.graphics.Path as CxPath
import androidx.compose.ui.graphics.PaintingStyle as CxStyle
import androidx.compose.ui.graphics.StrokeCap as CxCap
import androidx.compose.ui.graphics.StrokeJoin as CxJoin

/** Active icon theme (Dart: prefs.universal.iconTheme resolved via ChatyIconThemeId.fromString). */
val LocalChatyIconTheme = compositionLocalOf { ChatyIconThemeId.chatySigil }

/** Observes preferences.universal.iconTheme and provides it to descendants. Place once near the app root. */
@Composable
fun ProvideChatyIconTheme(preferences: ChatyPreferencesController, content: @Composable () -> Unit) {
    var version by remember(preferences) { mutableIntStateOf(0) }
    DisposableEffect(preferences) {
        val l: () -> Unit = { version += 1 }
        preferences.addListener(l)
        onDispose { preferences.removeListener(l) }
    }
    @Suppress("UNUSED_EXPRESSION") version
    val theme = ChatyIconThemeId.fromString(preferences.universal.iconTheme)
    CompositionLocalProvider(LocalChatyIconTheme provides theme, content = content)
}

private fun Color.toCx() = CxColor(r.toFloat(), g.toFloat(), b.toFloat(), a.toFloat())

private fun Paint.toCx(): CxPaint = CxPaint().also {
    it.color = color.toCx()
    it.style = if (style == PaintingStyle.stroke) CxStyle.Stroke else CxStyle.Fill
    it.strokeWidth = strokeWidth.toFloat()
    it.strokeCap = when (strokeCap) { StrokeCap.butt -> CxCap.Butt; StrokeCap.round -> CxCap.Round; StrokeCap.square -> CxCap.Square }
    it.strokeJoin = when (strokeJoin) { StrokeJoin.miter -> CxJoin.Miter; StrokeJoin.round -> CxJoin.Round; StrokeJoin.bevel -> CxJoin.Bevel }
    it.isAntiAlias = isAntiAlias
}

private fun Rect.toCx() = CxRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
private fun Offset.toCx() = CxOffset(dx.toFloat(), dy.toFloat())
private fun RRect.toCx() = CxRoundRect(rect.toCx(), CxCornerRadius(radius.x.toFloat(), radius.x.toFloat()))
private fun Double.deg() = (this * 180.0 / Math.PI).toFloat()

private fun Path.toCx(): CxPath {
    val p = CxPath()
    for (op in ops) when (op) {
        is PathOp.MoveTo -> p.moveTo(op.x.toFloat(), op.y.toFloat())
        is PathOp.LineTo -> p.lineTo(op.x.toFloat(), op.y.toFloat())
        is PathOp.QuadTo -> p.quadraticBezierTo(op.x1.toFloat(), op.y1.toFloat(), op.x2.toFloat(), op.y2.toFloat())
        is PathOp.CubicTo -> p.cubicTo(op.x1.toFloat(), op.y1.toFloat(), op.x2.toFloat(), op.y2.toFloat(), op.x3.toFloat(), op.y3.toFloat())
        PathOp.Close -> p.close()
        is PathOp.AddOval -> p.addOval(op.rect.toCx())
        is PathOp.AddRRect -> p.addRoundRect(op.rrect.toCx())
        is PathOp.AddArc -> p.arcTo(op.rect.toCx(), op.start.deg(), op.sweep.deg(), true)
    }
    return p
}

/** Replays IconCanvas calls onto a Compose Canvas. */
private class ComposeIconCanvas(private val c: CxCanvas) : IconCanvas {
    override fun drawPath(path: Path, paint: Paint) = c.drawPath(path.toCx(), paint.toCx())
    override fun drawLine(p1: Offset, p2: Offset, paint: Paint) = c.drawLine(p1.toCx(), p2.toCx(), paint.toCx())
    override fun drawRect(rect: Rect, paint: Paint) = c.drawRect(rect.toCx(), paint.toCx())
    override fun drawRRect(rrect: RRect, paint: Paint) {
        val r = rrect.rect
        c.drawRoundRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(),
            rrect.radius.x.toFloat(), rrect.radius.x.toFloat(), paint.toCx())
    }
    override fun drawCircle(center: Offset, radius: Number, paint: Paint) = c.drawCircle(center.toCx(), radius.toFloat(), paint.toCx())
    override fun drawOval(rect: Rect, paint: Paint) = c.drawOval(rect.toCx(), paint.toCx())
    override fun drawArc(rect: Rect, startAngle: Number, sweepAngle: Number, useCenter: Boolean, paint: Paint) =
        c.drawArc(rect.toCx(), startAngle.toDouble().deg(), sweepAngle.toDouble().deg(), useCenter, paint.toCx())
    override fun save() = c.save()
    override fun restore() = c.restore()
    override fun translate(dx: Number, dy: Number) = c.translate(dx.toFloat(), dy.toFloat())
    override fun scale(sx: Number, sy: Number) = c.scale(sx.toFloat(), sy.toFloat())
    override fun rotate(radians: Number) = c.rotate(radians.toDouble().deg())
}

/** Port of ChatyIcon: pure vector icon, theme from [themeOverride] ?: LocalChatyIconTheme, color from
 *  [color] ?: LocalContentColor. Optional semantic label. */
@Composable
fun ChatyIcon(
    icon: ChatyIconId,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: CxColor? = null,
    themeOverride: ChatyIconThemeId? = null,
    semanticLabel: String? = null,
) {
    val theme = themeOverride ?: LocalChatyIconTheme.current
    val resolved = color ?: LocalContentColor.current
    val px = with(LocalDensity.current) { size.toPx() }.toDouble()
    val sem = if (!semanticLabel.isNullOrEmpty()) Modifier.semantics { contentDescription = semanticLabel } else Modifier
    Canvas(modifier.size(size).then(sem)) {
        drawIntoCanvas { canvas ->
            ChatyIconPainter.paint(
                ComposeIconCanvas(canvas), px, icon, theme,
                Color(resolved.alpha.toDouble(), resolved.red.toDouble(), resolved.green.toDouble(), resolved.blue.toDouble()),
            )
        }
    }
}

/** Port of ChatyAnimatedIcon: scale to 0.93 on press over 160ms with Cubic(0.23, 1, 0.32, 1); onTap fires on release. */
@Composable
fun ChatyAnimatedIcon(
    icon: ChatyIconId,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: CxColor? = null,
    themeOverride: ChatyIconThemeId? = null,
    onTap: (() -> Unit)? = null,
    tooltip: String? = null,
) {
    var pressed by remember { mutableStateOf(false) }
    val s by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = tween(160, easing = CubicBezierEasing(0.23f, 1.0f, 0.32f, 1.0f)),
        label = "chatyIconScale",
    )
    val gesture = if (onTap != null) Modifier.pointerInput(onTap) {
        detectTapGestures(
            onPress = {
                pressed = true
                val released = tryAwaitRelease()
                pressed = false
                if (released) onTap()
            },
        )
    } else Modifier
    ChatyIcon(icon, modifier.then(gesture).scale(s), size, color, themeOverride, tooltip)
}

/** Port of main_navigation_shell._registryIconIdForNavDestination; null keeps the template glyph. */
fun chatyIconIdForNavDestination(destinationId: String): ChatyIconId? = when (destinationId) {
    "chats" -> ChatyIconId.chats
    "updates" -> ChatyIconId.updates
    "calls" -> ChatyIconId.calls
    "tasks" -> ChatyIconId.tasks
    "settings" -> ChatyIconId.settings
    "groups" -> ChatyIconId.groups
    "desktop" -> ChatyIconId.desktop
    else -> null
}
