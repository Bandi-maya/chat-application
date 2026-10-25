package com.example.chat.ui.chats.home

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The four ChatyGlyph definitions the home header uses, ported exactly from
 *  lib/ui/core/design_system/components/chaty_glyph.dart. That file owns the
 *  glyphs as inline SVG markup (viewBox 0 0 24 24) rendered by a tiny
 *  path/circle/rect painter - there is NO icon font or binary asset anywhere
 *  in the original project, so nothing needs to be shipped in res/. The
 *  remaining 21 glyphs plus the shared renderer belong to the design-system
 *  slice; these four are drawn as the same paths with the same stroke
 *  semantics (stroke-width scaled by defaultStrokeWidth/1.8, round caps and
 *  joins, fill pass then stroke pass, canvas scaled size/24). */
enum class HomeGlyph { camera, qrScan, ghostActive, ghostInactive }

@Composable
fun HomeGlyphIcon(
    glyph: HomeGlyph,
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 1.8f,
    semanticLabel: String? = null,
) {
    val sem = if (!semanticLabel.isNullOrEmpty()) {
        Modifier.semantics { contentDescription = semanticLabel }
    } else {
        Modifier
    }
    androidx.compose.foundation.Canvas(modifier.size(size).then(sem)) {
        val sx = this.size.width / 24f
        val sy = this.size.height / 24f
        withTransform({ scale(sx, sy, pivot = Offset.Zero) }) {
            when (glyph) {
                HomeGlyph.camera -> drawCameraGlyph(color, strokeWidth)
                HomeGlyph.qrScan -> drawQrScanGlyph(color, strokeWidth)
                HomeGlyph.ghostActive -> drawGhostActiveGlyph(color, strokeWidth)
                HomeGlyph.ghostInactive -> drawGhostInactiveGlyph(color, strokeWidth)
            }
        }
    }
}

private fun DrawScope.stroke(width: Float, default: Float): Stroke =
    Stroke(width = if (width == 0f) default else width * (default / 1.8f),
        cap = androidx.compose.ui.graphics.StrokeCap.Round,
        join = androidx.compose.ui.graphics.StrokeJoin.Round)

private fun DrawScope.drawGhostActiveGlyph(color: Color, default: Float) {
    val body = Path().apply {
        moveTo(12f, 3f)
        cubicTo(7.5f, 3f, 5f, 6.5f, 5f, 11f)
        lineTo(5f, 20f)
        lineTo(7.5f, 18f)
        lineTo(10f, 20f)
        lineTo(12f, 18f)
        lineTo(14f, 20f)
        lineTo(16.5f, 18f)
        lineTo(19f, 20f)
        lineTo(19f, 11f)
        cubicTo(19f, 6.5f, 16.5f, 3f, 12f, 3f)
        close()
    }
    // Fill pass (fill="none" on the body, so only the eyes fill), then stroke.
    drawCircle(color, radius = 1.5f, center = Offset(9.5f, 10f))
    drawCircle(color, radius = 1.5f, center = Offset(14.5f, 10f))
    drawPath(body, color, style = stroke(1.8f, default))
}

private fun DrawScope.drawGhostInactiveGlyph(color: Color, default: Float) {
    val eye = Path().apply {
        moveTo(12f, 4.5f)
        cubicTo(7.5f, 4.5f, 4f, 8f, 2f, 12f)
        cubicTo(4f, 16f, 7.5f, 19.5f, 12f, 19.5f)
        cubicTo(16.5f, 19.5f, 20f, 16f, 22f, 12f)
        cubicTo(20f, 8f, 16.5f, 4.5f, 12f, 4.5f)
        close()
    }
    drawPath(eye, color, style = stroke(1.8f, default))
    drawCircle(color, radius = 3f, center = Offset(12f, 12f), style = stroke(1.8f, default))
}

private fun DrawScope.drawCameraGlyph(color: Color, default: Float) {
    val body = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                androidx.compose.ui.geometry.Rect(3.5f, 7.5f, 20.5f, 19.5f),
                androidx.compose.ui.geometry.CornerRadius(2.6f, 2.6f),
            ),
        )
    }
    drawPath(body, color, style = stroke(1.8f, default))
    val hood = Path().apply {
        moveTo(8f, 7.5f)
        lineTo(9.7f, 4.8f)
        lineTo(14.5f, 4.8f)
        lineTo(16.2f, 7.5f)
    }
    drawPath(hood, color, style = stroke(1.8f, default))
    drawCircle(color, radius = 3.1f, center = Offset(12f, 13.5f), style = stroke(1.8f, default))
    drawCircle(color, radius = 0.8f, center = Offset(17.6f, 10f))
}

private fun DrawScope.drawQrScanGlyph(color: Color, default: Float) {
    val corners = Path().apply {
        moveTo(9f, 4f); lineTo(4f, 4f); lineTo(4f, 9f)
        moveTo(15f, 4f); lineTo(20f, 4f); lineTo(20f, 9f)
        moveTo(20f, 15f); lineTo(20f, 20f); lineTo(15f, 20f)
        moveTo(9f, 20f); lineTo(4f, 20f); lineTo(4f, 15f)
    }
    drawPath(corners, color, style = stroke(1.8f, default))
    // three 4x4 stroked squares at (7,7) (13,7) (7,13)
    listOf(Offset(7f, 7f), Offset(13f, 7f), Offset(7f, 13f)).forEach { topLeft ->
        drawRect(
            color,
            topLeft = topLeft,
            size = androidx.compose.ui.geometry.Size(4f, 4f),
            style = stroke(1.6f, default),
        )
    }
    val mark = Path().apply {
        moveTo(13f, 13f); lineTo(15f, 13f); lineTo(15f, 15f); lineTo(13f, 15f); close()
        moveTo(17f, 16f); lineTo(18.8f, 16f); lineTo(18.8f, 18.8f); lineTo(16f, 18.8f); lineTo(16f, 17f)
    }
    drawPath(mark, color)
}
