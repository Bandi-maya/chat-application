package com.example.chat.ui.kit

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize

/** What the global host draws behind its activity slots: the recorded routed content and where the host sits in the window. */
@Stable
class ChatyBackdropState(val layer: GraphicsLayer) {
    var hostOrigin by mutableStateOf(Offset.Zero)
}

val LocalChatyBackdrop = staticCompositionLocalOf<ChatyBackdropState?> { null }

private val blurSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Dart BackdropFilter(ImageFilter.blur(sigma)) clipped to the surrounding shape: blurs what the host painted BEHIND this
 * element. Free, no library: the host records its content into a GraphicsLayer, and this modifier replays the region
 * under the element into its own layer carrying a RenderEffect blur (Android 12 / API 31+ only; below that nothing is
 * drawn, so the element's translucent fill shows the unblurred content). The layer is padded by 3 sigma so edge pixels
 * blur against real neighbours like Flutter's full-backdrop blur. Apply AFTER .clip(shape).
 */
@Composable
fun Modifier.chatyBackdropBlur(sigma: Dp): Modifier {
    val backdrop = LocalChatyBackdrop.current ?: return this
    if (!blurSupported) return this
    val density = LocalDensity.current
    val blurLayer = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val sigmaPx = with(density) { sigma.toPx() }
    return this
        .onGloballyPositioned { origin = it.positionInWindow() }
        .drawBehind {
            val margin = 3f * sigmaPx
            val rel = origin - backdrop.hostOrigin
            blurLayer.renderEffect = BlurEffect(sigmaPx, sigmaPx, TileMode.Clamp)
            blurLayer.record(size = IntSize((size.width + 2 * margin).toInt(), (size.height + 2 * margin).toInt())) {
                translate(margin - rel.x, margin - rel.y) { drawLayer(backdrop.layer) }
            }
            translate(-margin, -margin) { drawLayer(blurLayer) }
        }
}

/**
 * Dart BoxShadow(color, blurRadius, offset) for a rounded rect, drawn with a blurred layer (sigma = blurRadius / 2, as in
 * Skia). Painted behind the element's own fill and NOT clipped here: put it before/after .clip() exactly like the Dart
 * widget tree decides. (In ChatyCallActivityCapsule the Dart ClipRRect wraps the DecoratedBox, so its shadow is clipped
 * inside the capsule.) API 31+ only; nothing below.
 */
@Composable
fun Modifier.chatyBoxShadow(color: Color, blurRadius: Dp, offsetX: Dp, offsetY: Dp, cornerRadius: Dp): Modifier {
    if (!blurSupported) return this
    val density = LocalDensity.current
    val layer = rememberGraphicsLayer()
    val sigmaPx = with(density) { (blurRadius / 2).toPx() }
    val dx = with(density) { offsetX.toPx() }
    val dy = with(density) { offsetY.toPx() }
    val r = with(density) { cornerRadius.toPx() }
    return this.drawBehind {
        val margin = 3f * sigmaPx
        val outer = size
        layer.renderEffect = BlurEffect(sigmaPx, sigmaPx, TileMode.Decal)
        layer.record(size = IntSize((size.width + 2 * margin).toInt(), (size.height + 2 * margin).toInt())) {
            drawRoundRect(color, Offset(margin + dx, margin + dy), Size(outer.width, outer.height), CornerRadius(r))
        }
        translate(-margin, -margin) { drawLayer(layer) }
    }
}
