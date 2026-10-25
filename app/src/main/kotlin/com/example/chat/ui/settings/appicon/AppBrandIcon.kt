package com.example.chat.ui.settings.appicon

import com.example.chat.ui.core.controllers.BrandIconSource
import com.example.chat.ui.core.controllers.LauncherIconVariant
import com.example.chat.ui.core.controllers.title

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File

/** Exact port of lib/ui/core/widgets/app_brand_icon.dart and
 *  lib/ui/core/widgets/flying_bird_icon.dart (CustomPainter geometry). */

enum class ChatyBrandIconVariant { OUTLINE, FILLED }

/** ChatyBrandGlyphPainter.paint - canonical Chaty communication brand glyph.
 *  Rounded conversation mark with signature dynamic tail and 3-dot motif. */
fun DrawScope.drawChatyBrandGlyph(
    variant: ChatyBrandIconVariant,
    color: Color,
    strokeWidth: Float = 3f,
) {
    val w = size.width
    val h = size.height

    val cx = w * 0.52f
    val cy = h * 0.44f
    val radius = w * 0.36f

    val bubblePath = Path().apply {
        addOval(Rect(center = Offset(cx, cy), radius = radius))
    }
    // Signature tapered triangular dynamic tail at bottom-left.
    val tailPath = Path().apply {
        moveTo(cx - radius * 0.72f, cy + radius * 0.65f)
        lineTo(w * 0.16f, h * 0.88f)
        lineTo(cx - radius * 0.10f, cy + radius * 0.95f)
        close()
    }
    val combinedPath = Path.combine(PathOperation.Union, bubblePath, tailPath)

    when (variant) {
        ChatyBrandIconVariant.FILLED -> drawPath(combinedPath, color)
        ChatyBrandIconVariant.OUTLINE -> drawPath(
            combinedPath,
            color,
            style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
        )
    }

    // 3-dot communicative face / connection motif.
    val dotRadius = w * 0.052f
    val dotColor = if (variant == ChatyBrandIconVariant.FILLED) {
        if (color.luminance() > 0.6f) Color(0xFF1E1E1E) else Color.White
    } else {
        color
    }
    drawCircle(dotColor, dotRadius, Offset(cx, cy + h * 0.04f))
    drawCircle(dotColor, dotRadius, Offset(cx - w * 0.12f, cy - h * 0.02f))
    drawCircle(dotColor, dotRadius, Offset(cx + w * 0.12f, cy - h * 0.02f))
}

/** FlyingBirdPainter.paint - aerodynamic bird silhouette, normalized
 *  100x100 coordinate space. */
fun DrawScope.drawFlyingBird(color: Color) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.18f, h * 0.42f)
        cubicTo(w * 0.36f, h * 0.46f, w * 0.48f, h * 0.52f, w * 0.53f, h * 0.57f)
        cubicTo(w * 0.49f, h * 0.58f, w * 0.45f, h * 0.58f, w * 0.40f, h * 0.59f)
        cubicTo(w * 0.48f, h * 0.56f, w * 0.52f, h * 0.54f, w * 0.55f, h * 0.52f)
        cubicTo(w * 0.62f, h * 0.58f, w * 0.70f, h * 0.57f, w * 0.74f, h * 0.53f)
        lineTo(w * 0.82f, h * 0.53f)
        lineTo(w * 0.75f, h * 0.51f)
        cubicTo(w * 0.70f, h * 0.50f, w * 0.67f, h * 0.47f, w * 0.66f, h * 0.44f)
        cubicTo(w * 0.67f, h * 0.41f, w * 0.70f, h * 0.38f, w * 0.74f, h * 0.41f)
        cubicTo(w * 0.69f, h * 0.46f, w * 0.64f, h * 0.48f, w * 0.60f, h * 0.50f)
        cubicTo(w * 0.54f, h * 0.47f, w * 0.42f, h * 0.43f, w * 0.18f, h * 0.42f)
        close()
    }
    drawPath(path, color)
}

/** _LauncherStyle from app_brand_icon.dart. */
class LauncherStyle(
    val bgColor: Color,
    val brush: Brush,
    val glyphColor: Color,
    val accentColor: Color,
)

/** Dart _compositionFor variant table (colors verbatim). */
fun launcherStyleFor(variant: LauncherIconVariant): LauncherStyle = when (variant) {
    LauncherIconVariant.bird -> LauncherStyle(
        bgColor = Color(0xFFFFFFFF),
        brush = Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF3F4F6)), start = Offset.Zero, end = Offset.Infinite),
        glyphColor = Color(0xFF111827),
        accentColor = Color(0xFF1F2937),
    )
    LauncherIconVariant.warm -> LauncherStyle(
        bgColor = Color(0xFFF4EFE6),
        brush = Brush.linearGradient(listOf(Color(0xFFFAF7F2), Color(0xFFE8E0D2)), start = Offset.Zero, end = Offset.Infinite),
        glyphColor = Color(0xFFF9F6F0),
        accentColor = Color(0xFF8C6239),
    )
    LauncherIconVariant.outline -> LauncherStyle(
        bgColor = Color(0xFF141414),
        brush = Brush.linearGradient(listOf(Color(0xFF1E1E1E), Color(0xFF0F0F0F)), start = Offset.Zero, end = Offset(0f, Float.POSITIVE_INFINITY)),
        glyphColor = Color(0xFFFAF7F2),
        accentColor = Color(0xFF7D756D),
    )
    LauncherIconVariant.obsidian -> LauncherStyle(
        bgColor = Color(0xFF141414),
        brush = Brush.linearGradient(listOf(Color(0xFF1E1E1E), Color(0xFF0F0F0F)), start = Offset.Zero, end = Offset.Infinite),
        glyphColor = Color(0xFFFAF7F2),
        accentColor = Color(0xFF8C6239),
    )
    LauncherIconVariant.glass -> LauncherStyle(
        bgColor = Color(0xFF1C1917),
        brush = Brush.radialGradient(listOf(Color(0xFF292524), Color(0xFF141210))),
        glyphColor = Color.White,
        accentColor = Color(0xFFD4A373),
    )
    LauncherIconVariant.signal -> LauncherStyle(
        bgColor = Color(0xFF0D1D33),
        brush = Brush.linearGradient(listOf(Color(0xFF142A47), Color(0xFF07111F)), start = Offset.Zero, end = Offset.Infinite),
        glyphColor = Color(0xFFF8FBFF),
        accentColor = Color(0xFF60A5FA),
    )
    LauncherIconVariant.fold -> LauncherStyle(
        bgColor = Color(0xFF1C1917),
        brush = Brush.linearGradient(listOf(Color(0xFF292524), Color(0xFF1C1917)), start = Offset.Zero, end = Offset.Infinite),
        glyphColor = Color(0xFFD4A373),
        accentColor = Color(0xFF8C5E3C),
    )
}

/** _LauncherCompositionPainter.paint. */
fun DrawScope.drawLauncherComposition(
    variant: LauncherIconVariant,
    iconColor: Color,
    accentColor: Color,
) {
    val w = size.width
    val h = size.height
    when (variant) {
        LauncherIconVariant.bird -> drawFlyingBird(iconColor)

        LauncherIconVariant.warm -> {
            // Drop shadow
            withTransform({ translate(0f, h * 0.05f) }) {
                drawChatyBrandGlyph(ChatyBrandIconVariant.FILLED, Color(0x332B2219))
            }
            // 3D base bevel
            withTransform({ translate(0f, h * 0.02f) }) {
                drawChatyBrandGlyph(ChatyBrandIconVariant.FILLED, accentColor)
            }
            // Metallic outer stroke rim
            drawChatyBrandGlyph(ChatyBrandIconVariant.OUTLINE, Color(0xFFC89666), strokeWidth = w * 0.08f)
            // Warm ivory front face
            drawChatyBrandGlyph(ChatyBrandIconVariant.FILLED, iconColor)
        }

        LauncherIconVariant.outline -> {
            drawChatyBrandGlyph(ChatyBrandIconVariant.OUTLINE, iconColor, strokeWidth = w * 0.09f)
        }

        LauncherIconVariant.obsidian -> {
            withTransform({ translate(0f, h * 0.05f) }) {
                drawChatyBrandGlyph(ChatyBrandIconVariant.FILLED, Color(0x66000000))
            }
            withTransform({ translate(0f, h * 0.02f) }) {
                drawChatyBrandGlyph(ChatyBrandIconVariant.FILLED, accentColor)
            }
            drawChatyBrandGlyph(ChatyBrandIconVariant.FILLED, iconColor)
        }

        LauncherIconVariant.glass -> {
            drawRoundRect(
                color = accentColor.copy(alpha = 0.25f),
                topLeft = Offset(w * 0.1f, h * 0.1f),
                size = Size(w * 0.8f, h * 0.8f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.28f, w * 0.28f),
            )
            drawChatyBrandGlyph(ChatyBrandIconVariant.OUTLINE, Color.White.copy(alpha = 0.85f), strokeWidth = w * 0.07f)
        }

        LauncherIconVariant.signal -> {
            drawCircle(
                color = accentColor.copy(alpha = 0.35f),
                radius = w * 0.44f,
                center = Offset(w * 0.5f, h * 0.44f),
                style = Stroke(width = w * 0.07f),
            )
            drawChatyBrandGlyph(ChatyBrandIconVariant.FILLED, iconColor)
        }

        LauncherIconVariant.fold -> {
            val foldPath1 = Path().apply {
                moveTo(w * 0.18f, h * 0.22f); lineTo(w * 0.82f, h * 0.22f); lineTo(w * 0.5f, h * 0.54f); close()
            }
            val foldPath2 = Path().apply {
                moveTo(w * 0.18f, h * 0.22f); lineTo(w * 0.5f, h * 0.54f); lineTo(w * 0.18f, h * 0.74f); close()
            }
            val foldPath3 = Path().apply {
                moveTo(w * 0.82f, h * 0.22f); lineTo(w * 0.5f, h * 0.54f); lineTo(w * 0.82f, h * 0.74f); close()
            }
            val foldPath4 = Path().apply {
                moveTo(w * 0.18f, h * 0.74f); lineTo(w * 0.5f, h * 0.54f); lineTo(w * 0.82f, h * 0.74f); lineTo(w * 0.30f, h * 0.90f); close()
            }
            drawPath(foldPath1, iconColor)
            drawPath(foldPath2, accentColor.copy(alpha = 0.65f))
            drawPath(foldPath3, accentColor.copy(alpha = 0.85f))
            drawPath(foldPath4, iconColor)
        }
    }
}

/** ChatyBrandMark: glyph painter inside a square box. */
@Composable
fun ChatyBrandMark(
    modifier: Modifier = Modifier,
    variant: ChatyBrandIconVariant = ChatyBrandIconVariant.FILLED,
    size: Dp = 28.dp,
    color: Color = com.example.chat.ui.theme.LocalAppColors.current.primary,
) {
    Canvas(modifier.size(size)) {
        drawChatyBrandGlyph(variant, color, strokeWidth = this.size.width * 0.08f)
    }
}

/** LauncherIconPreview: gradient tile + composition painter at 62% size. */
@Composable
fun LauncherIconPreview(
    variant: LauncherIconVariant,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    borderRadius: Dp = 14.dp,
) {
    val style = launcherStyleFor(variant)
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(borderRadius))
            .background(style.brush),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.62f)) {
            drawLauncherComposition(variant, style.glyphColor, style.accentColor)
        }
    }
}

/** ChatyBrandIcon: custom image when a custom brand icon is active,
 *  otherwise the bundled launcher preview (Dart Image.file with
 *  errorBuilder fallback). */
@Composable
fun ChatyBrandIcon(
    controller: AppIconController,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    borderRadius: Dp = 14.dp,
) {
    val customPath = controller.customBrandIconPath
    val useCustom = controller.brandIconSource == BrandIconSource.custom &&
        !customPath.isNullOrEmpty() && File(customPath).exists()
    Box(modifier.size(size)) {
        if (useCustom) {
            coil.compose.AsyncImage(
                model = File(customPath!!),
                contentDescription = "Chaty app icon",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(RoundedCornerShape(borderRadius)),
            )
        } else {
            LauncherIconPreview(
                variant = controller.launcherIcon,
                size = size,
                borderRadius = borderRadius,
            )
        }
    }
}
