package com.example.chat.ui.chats.detail

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.ui.theme.ChatyAsyncImage
import java.io.File
import kotlin.math.floor
import kotlin.math.sin

/** Exact port of lib/ui/core/widgets/chat_wallpaper.dart. Rendered behind the
 *  message list; every branch maps to a real visual driven by persisted
 *  settings:
 *  - Solid       flat tint blended from the active theme surfaces.
 *  - Gradient    three-stop diagonal gradient from theme surfaces.
 *  - Pattern     deterministic vector pattern chosen by [patternId]
 *                ('subtle_dots', 'geometric', 'gradient_mesh',
 *                'constellation'; 'none' = plain).
 *  - Image       the user-picked picture stored at [imagePath]; falls back to
 *                the gradient when the file is missing or unreadable.
 *  - ProfileBlur soft blurred color fields derived from the contact's avatar
 *                color. */
@Composable
fun ChatWallpaper(
    theme: ThemeConfig,
    wallpaperType: String,
    patternId: String = "subtle_dots",
    profileColorHex: String? = null,
    imagePath: String? = null,
    profilePicWallpaper: Boolean = false,
    avatarUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val gradientColors = listOf(
        Color(theme.surfaceColor),
        Color(theme.backgroundColor),
        lerpColor(theme.backgroundColor, theme.cardColor, 0.6f),
    )
    val gradientBox: @Composable () -> Unit = {
        // Dart: LinearGradient(topLeft -> bottomRight, stops 0 / 0.55 / 1).
        Box(
            modifier.fillMaxSize().drawBehind {
                drawRect(
                    Brush.linearGradient(
                        0f to gradientColors[0],
                        0.55f to gradientColors[1],
                        1f to gradientColors[2],
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
            },
        )
    }

    if (profilePicWallpaper && !avatarUrl.isNullOrEmpty()) {
        if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
            ChatyAsyncImage(
                url = avatarUrl,
                contentDescription = null,
                modifier = modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                error = gradientBox,
            )
            return
        }
        val fileBitmap = remember(avatarUrl) {
            if (File(avatarUrl).exists()) {
                BitmapFactory.decodeFile(avatarUrl)?.asImageBitmap()
            } else {
                null
            }
        }
        if (fileBitmap != null) {
            Image(
                bitmap = fileBitmap,
                contentDescription = null,
                modifier = modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            return
        }
    }

    when (wallpaperType) {
        "Solid" -> Box(
            modifier
                .fillMaxSize()
                .background(lerpColor(theme.backgroundColor, theme.surfaceColor, 0.5f)),
        )
        "Gradient" -> gradientBox()
        "Image" -> {
            val bitmap = remember(imagePath) {
                val path = imagePath
                if (!path.isNullOrEmpty() && File(path).exists()) {
                    BitmapFactory.decodeFile(path)?.asImageBitmap()
                } else {
                    null
                }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                // Nothing picked yet, or the file vanished: honest fallback.
                gradientBox()
            }
        }
        "ProfileBlur" -> {
            val profile = parseWallpaperHex(profileColorHex) ?: Color(theme.accentColor)
            Box(
                modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(Color(theme.backgroundColor))
                        profileGlow(Offset(size.width * 0.5f, size.height * 0.22f),
                            size.width * 0.42f, profile)
                        profileGlow(Offset(size.width * 0.12f, size.height * 0.78f),
                            size.width * 0.30f,
                            lerpColorLong(profile, theme.accentColor, 0.5f))
                        profileGlow(Offset(size.width * 0.88f, size.height * 0.62f),
                            size.width * 0.26f,
                            lerpColorLong(profile, theme.surfaceColor, 0.55f))
                    },
            )
        }
        "Pattern" -> {
            if (patternId == "none") {
                Box(modifier.fillMaxSize().background(Color(theme.backgroundColor)))
            } else {
                Box(
                    modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawWallpaperPattern(
                                patternId = patternId,
                                ink = Color(theme.primaryTextColor),
                                accent = Color(theme.accentColor),
                                success = Color(theme.successColor),
                                bubble = Color(theme.incomingBubbleColor),
                            )
                        },
                )
            }
        }
        else -> {
            if (patternId == "none") {
                Box(modifier.fillMaxSize().background(Color(theme.backgroundColor)))
            } else {
                Box(
                    modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawWallpaperPattern(
                                patternId = patternId,
                                ink = Color(theme.primaryTextColor),
                                accent = Color(theme.accentColor),
                                success = Color(theme.successColor),
                                bubble = Color(theme.incomingBubbleColor),
                            )
                        },
                )
            }
        }
    }
}

private fun parseWallpaperHex(hex: String?): Color? {
    if (hex.isNullOrEmpty()) return null
    val value = hex.removePrefix("0x").removePrefix("0X").removePrefix("#").toLongOrNull(16)
        ?: return null
    return if (hex.removePrefix("0x").removePrefix("0X").removePrefix("#").length <= 6) {
        Color(0xFF000000L or value)
    } else {
        Color(value)
    }
}

/** Dart Color.lerp over ARGB longs. */
private fun lerpColor(a: Long, b: Long, t: Float): Color = lerpColorLong(Color(a), b, t)

private fun lerpColorLong(a: Color, b: Long, t: Float): Color {
    val bc = Color(b)
    return Color(
        red = a.red + (bc.red - a.red) * t,
        green = a.green + (bc.green - a.green) * t,
        blue = a.blue + (bc.blue - a.blue) * t,
        alpha = a.alpha + (bc.alpha - a.alpha) * t,
    )
}

/** Dart _ProfileGlowPainter glow(): alpha 0.30, BlurStyle.normal sigma 60.
 *  Blur goes through the framework paint (Compose Paint has no maskFilter). */
private fun DrawScope.profileGlow(center: Offset, radius: Float, color: Color) {
    drawIntoCanvas { canvas ->
        val frameworkPaint = android.graphics.Paint().apply {
            this.color = color.copy(alpha = 0.30f).toArgb()
            maskFilter = android.graphics.BlurMaskFilter(
                60f,
                android.graphics.BlurMaskFilter.Blur.NORMAL,
            )
        }
        canvas.nativeCanvas.drawCircle(center.x, center.y, radius, frameworkPaint)
    }
}

/** Deterministic hash in [0, 1) so the constellation layout never flickers. */
private fun hash2(x: Int, y: Int): Float {
    val value = sin(x * 127.1 + y * 311.7) * 43758.5453
    return (value - floor(value)).toFloat()
}

/** Dart _WallpaperPatternPainter. */
private fun DrawScope.drawWallpaperPattern(
    patternId: String,
    ink: Color,
    accent: Color,
    success: Color,
    bubble: Color,
) {
    when (patternId) {
        "subtle_dots" -> {
            val paintColor = ink.copy(alpha = 0.06f)
            val spacing = 26f
            var x = spacing / 2
            while (x < size.width) {
                var y = spacing / 2
                while (y < size.height) {
                    drawCircle(paintColor, radius = 1.3f, center = Offset(x, y))
                    y += spacing
                }
                x += spacing
            }
        }
        "geometric" -> {
            val paintColor = ink.copy(alpha = 0.05f)
            val step = 34f
            var x = -size.height
            while (x < size.width) {
                drawLine(paintColor, Offset(x, 0f), Offset(x + size.height, size.height), strokeWidth = 1f)
                drawLine(paintColor, Offset(x + size.height, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
        }
        "gradient_mesh" -> {
            val rect = Rect(Offset.Zero, size)
            fun blob(center: Offset, radius: Float, color: Color) {
                val paint = Paint().apply {
                    shader = RadialGradientShader(
                        colors = listOf(color.copy(alpha = 0.12f), color.copy(alpha = 0f)),
                        center = center,
                        radius = radius,
                    )
                }
                drawIntoCanvas { canvas ->
                    canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, paint)
                }
            }
            blob(Offset(size.width * 0.15f, size.height * 0.1f), size.width * 0.9f, accent)
            blob(Offset(size.width * 0.9f, size.height * 0.35f), size.width * 0.8f, bubble)
            blob(Offset(size.width * 0.4f, size.height * 0.95f), size.width * 0.9f, success)
        }
        "constellation" -> {
            val starColor = ink.copy(alpha = 0.11f)
            val linkColor = ink.copy(alpha = 0.05f)
            val cell = 72f
            val cols = (size.width / cell).toInt() + 3
            val rows = (size.height / cell).toInt() + 3
            fun point(cx: Int, cy: Int): Offset {
                val jitterX = hash2(cx, cy)
                val jitterY = hash2(cy, cx)
                return Offset(
                    (cx - 0.5f + jitterX * 0.8f) * cell,
                    (cy - 0.5f + jitterY * 0.8f) * cell,
                )
            }
            val grid = Array(rows) { cy -> Array(cols) { cx -> point(cx, cy) } }
            for (cy in 0 until rows) {
                for (cx in 0 until cols) {
                    val p = grid[cy][cx]
                    if (cx + 1 < cols) {
                        drawLine(linkColor, p, grid[cy][cx + 1], strokeWidth = 0.8f)
                    }
                    if (cy + 1 < rows) {
                        drawLine(linkColor, p, grid[cy + 1][cx], strokeWidth = 0.8f)
                    }
                    drawCircle(starColor, radius = 0.8f + hash2(cx * 7, cy * 13) * 1.4f, center = p)
                }
            }
        }
        else -> Unit
    }
}
