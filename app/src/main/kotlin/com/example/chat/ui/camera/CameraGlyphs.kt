package com.example.chat.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DocumentScanner
import com.example.chat.ui.icons.ChatyIcon
import com.example.chat.ui.icons.ChatyIconId
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Exact port of the private painters in camera_capture_screen.dart:
 *  _CameraGlyphPainter, _EffectGlyphPainter, _DocumentGuidePainter.
 *  CI-compile only (Compose). Geometry transliterated 1:1 from Dart
 *  (all glyphs drawn in a 32x32 viewport, scaled). */

@Composable
fun CameraGlyph(kind: String, color: Color, size: Dp = 24.dp) {
    val iconId = when (kind) {
        "gallery" -> ChatyIconId.gallery
        "flip" -> ChatyIconId.cameraFlip
        "flash" -> ChatyIconId.cameraFlashOn
        "camera" -> ChatyIconId.camera
        "close" -> ChatyIconId.close
        else -> null
    }
    if (iconId != null) {
        ChatyIcon(iconId, size = size, color = color)
        return
    }
    Canvas(modifier = Modifier.size(size)) {
        withTransform({ scale(this.size.width / 32f, this.size.height / 32f) }) {
            val line = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            when (kind) {
                "close" -> {
                    drawLine(color, Offset(10f, 10f), Offset(22f, 22f), strokeWidth = 2.2f, cap = StrokeCap.Round)
                    drawLine(color, Offset(22f, 10f), Offset(10f, 22f), strokeWidth = 2.2f, cap = StrokeCap.Round)
                }
                "flash" -> {
                    drawPath(Path().apply {
                        moveTo(18f, 3f); lineTo(8f, 17f); lineTo(15f, 17f); lineTo(13f, 29f)
                        lineTo(24f, 13f); lineTo(17f, 13f); close()
                    }, color)
                }
                "filters" -> {
                    drawCircle(color, 3f, Offset(11f, 21f))
                    drawCircle(color, 2.3f, Offset(22f, 12f))
                    drawPath(Path().apply {
                        moveTo(16f, 2f); lineTo(18f, 8f); lineTo(24f, 10f); lineTo(18f, 12f)
                        lineTo(16f, 18f); lineTo(14f, 12f); lineTo(8f, 10f); lineTo(14f, 8f); close()
                    }, color, style = line)
                }
                "camera" -> {
                    drawRoundRect(color, Offset(3f, 9f), Size(26f, 19f), CornerRadius(4f, 4f), style = line)
                    drawPath(Path().apply {
                        moveTo(9f, 9f); lineTo(12f, 5f); lineTo(20f, 5f); lineTo(23f, 9f)
                    }, color, style = line)
                    drawCircle(color, 5.2f, Offset(16f, 18f), style = line)
                }
                "document" -> {
                    drawPath(Path().apply {
                        moveTo(8f, 3f); lineTo(20f, 3f); lineTo(25f, 8f); lineTo(25f, 29f)
                        lineTo(7f, 29f); lineTo(7f, 4f); close()
                    }, color, style = line)
                    drawLine(color, Offset(11f, 14f), Offset(21f, 14f), strokeWidth = 2.2f, cap = StrokeCap.Round)
                    drawLine(color, Offset(11f, 19f), Offset(21f, 19f), strokeWidth = 2.2f, cap = StrokeCap.Round)
                    drawLine(color, Offset(11f, 24f), Offset(18f, 24f), strokeWidth = 2.2f, cap = StrokeCap.Round)
                    drawLine(color, Offset(20f, 3f), Offset(20f, 8f), strokeWidth = 2.2f, cap = StrokeCap.Round)
                    drawLine(color, Offset(20f, 8f), Offset(25f, 8f), strokeWidth = 2.2f, cap = StrokeCap.Round)
                }
                "gallery" -> {
                    drawRoundRect(color, Offset(3f, 4f), Size(26f, 24f), CornerRadius(4f, 4f), style = line)
                    drawCircle(color, 2.1f, Offset(11f, 11f), style = line)
                    drawPath(Path().apply {
                        moveTo(5f, 24f); lineTo(13f, 16f); lineTo(18f, 21f); lineTo(22f, 17f); lineTo(28f, 24f)
                    }, color, style = line)
                }
                "flip" -> {
                    drawArc(color, 0.15f * 180f, 1.28f * 180f, false, Offset(3f, 5f), Size(20f, 20f), style = line)
                    drawPath(Path().apply { moveTo(19f, 3f); lineTo(26f, 6f); lineTo(21f, 12f) }, color, style = line)
                    drawArc(color, 1.12f * 180f, 1.25f * 180f, false, Offset(9f, 9f), Size(20f, 20f), style = line)
                    drawPath(Path().apply { moveTo(13f, 29f); lineTo(6f, 26f); lineTo(11f, 20f) }, color, style = line)
                }
                else -> {
                    drawCircle(color, 10f, Offset(16f, 16f), style = line)
                    drawCircle(color, 3f, Offset(16f, 16f))
                }
            }
        }
    }
}

/** Exact port of _EffectGlyphPainter (category-specific swatch line art). */
@Composable
fun EffectGlyph(id: String, category: EffectCategory, color: Color, size: Dp = 24.dp) {
    Canvas(modifier = Modifier.size(size)) {
        withTransform({ scale(this.size.width / 32f, this.size.height / 32f) }) {
            val line = Stroke(width = 2.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            when (category) {
                EffectCategory.beauty, EffectCategory.glow,
                EffectCategory.faceAR, EffectCategory.celebration -> {
                    for (p in listOf(Offset(9f, 9f), Offset(23f, 7f), Offset(22f, 23f), Offset(9f, 24f))) {
                        drawCircle(color, if (id == "none") 2f else 2.3f, p)
                    }
                    drawLine(color, Offset(16f, 1f), Offset(16f, 31f), strokeWidth = 2.1f, cap = StrokeCap.Round)
                    drawLine(color, Offset(1f, 16f), Offset(31f, 16f), strokeWidth = 2.1f, cap = StrokeCap.Round)
                }
                EffectCategory.portrait -> {
                    drawOval(color, Offset(7f, 3f), Size(18f, 26f), style = line)
                    drawCircle(color, 1.8f, Offset(12f, 13f))
                    drawCircle(color, 1.8f, Offset(20f, 13f))
                    drawArc(color, 0f, 180f, false, Offset(11f, 15f), Size(10f, 8f), style = line)
                }
                EffectCategory.cinematic -> {
                    drawRoundRect(color, Offset(3f, 6f), Size(26f, 22f), CornerRadius(3f, 3f), style = line)
                    drawLine(color, Offset(3f, 13f), Offset(29f, 13f), strokeWidth = 2.1f, cap = StrokeCap.Round)
                    for (x in listOf(6f, 14f, 22f)) {
                        drawLine(color, Offset(x, 6f), Offset(x + 4, 13f), strokeWidth = 2.1f, cap = StrokeCap.Round)
                    }
                }
                EffectCategory.retro, EffectCategory.film -> {
                    drawCircle(color, 7f, Offset(16f, 16f), style = line)
                    for (i in 0 until 8) {
                        val angle = i * Math.PI / 4
                        drawLine(
                            color,
                            Offset((16 + cos(angle) * 11).toFloat(), (16 + sin(angle) * 11).toFloat()),
                            Offset((16 + cos(angle) * 14).toFloat(), (16 + sin(angle) * 14).toFloat()),
                            strokeWidth = 2.1f, cap = StrokeCap.Round,
                        )
                    }
                }
                EffectCategory.color -> {
                    for (i in 0 until 3) {
                        val left = 5f + i * 8.5f
                        val top = 5f + (i % 2) * 4f
                        val height = 23f - (i % 2) * 8f
                        drawRoundRect(color, Offset(left, top), Size(6f, height), CornerRadius(2f, 2f))
                    }
                }
                EffectCategory.cyber -> {
                    drawPath(Path().apply {
                        moveTo(3f, 7f); lineTo(12f, 7f); lineTo(16f, 13f); lineTo(24f, 13f)
                        lineTo(29f, 20f); lineTo(18f, 20f); lineTo(14f, 26f); lineTo(4f, 26f)
                    }, color, style = line)
                    drawCircle(color, 13f, Offset(16f, 16f), style = line)
                }
                EffectCategory.background -> {
                    drawPath(Path().apply {
                        moveTo(2f, 27f); lineTo(10f, 14f); lineTo(16f, 22f); lineTo(22f, 9f)
                        lineTo(30f, 27f); close()
                    }, color, style = line)
                    drawLine(color, Offset(2f, 28f), Offset(30f, 28f), strokeWidth = 2.1f, cap = StrokeCap.Round)
                }
            }
        }
    }
}

/** Exact port of _DocumentGuidePainter (corner brackets + inner frame). */
@Composable
fun DocumentGuideOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val pageWidth = min(size.width * 0.78f, size.height * 0.58f / 1.414f)
        val pageHeight = pageWidth * 1.414f
        val left = (size.width - pageWidth) / 2f
        val top = size.height * 0.47f - pageHeight / 2f
        val right = left + pageWidth
        val bottom = top + pageHeight
        val cornerLength = min(28f, pageWidth * 0.12f)
        val stroke = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val color = Color(0xFFF8FAFC)

        val corners = Path().apply {
            moveTo(left, top + cornerLength); lineTo(left, top); lineTo(left + cornerLength, top)
            moveTo(right - cornerLength, top); lineTo(right, top); lineTo(right, top + cornerLength)
            moveTo(right, bottom - cornerLength); lineTo(right, bottom); lineTo(right - cornerLength, bottom)
            moveTo(left + cornerLength, bottom); lineTo(left, bottom); lineTo(left, bottom - cornerLength)
        }
        drawPath(corners, color, style = stroke)

        drawRoundRect(
            color = Color(0xFF60A5FA).copy(alpha = 0.7f),
            topLeft = Offset(left + 5, top + 5),
            size = Size(pageWidth - 10, pageHeight - 10),
            cornerRadius = CornerRadius(3f, 3f),
            style = Stroke(width = 1f),
        )
    }
}

/** Document scan effect constant from camera_capture_screen.dart. */
val DOCUMENT_SCAN_EFFECT = ChatyCameraEffect(
    id = "document_scan",
    name = "Document Scan",
    category = EffectCategory.color,
    icon = Icons.Rounded.DocumentScanner,
    previewColor = Color(0xFFCBD5E1),
    colorMatrix = listOf(
        0.48, 0.94, 0.08, 0.0, -45.0,
        0.48, 0.94, 0.08, 0.0, -45.0,
        0.48, 0.94, 0.08, 0.0, -45.0,
        0.0, 0.0, 0.0, 1.0, 0.0,
    ),
)

