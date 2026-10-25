package com.example.chat.ui.profile

import kotlin.math.max

/**
 * Pure geometry of the Dart ImageEditorCropperDialog crop math
 * (lib/features/profile/image_editor_cropper_dialog.dart), extracted so the
 * Compose screen and the off-screen capture share one verified formula set.
 */
object ProfileCropGeometry {

    /** Capture raster density, Dart boundary.toImage(pixelRatio: 2.5). */
    const val CAPTURE_PIXEL_RATIO: Float = 2.5f

    /** InteractiveViewer minScale / maxScale. */
    const val MIN_SCALE: Float = 1.0f
    const val MAX_SCALE: Float = 4.0f

    /** Crop window size (Dart MediaQuery width - 48; banner height = w * 9/16). */
    fun cropBoxWidth(screenWidth: Float): Float = screenWidth - 48f

    fun cropBoxHeight(cropBoxWidth: Float, isAvatar: Boolean): Float =
        if (isAvatar) cropBoxWidth else cropBoxWidth * 9f / 16f

    class Layout(
        val childW: Float,
        val childH: Float,
        val footW: Float,
        val footH: Float,
    )

    /**
     * Cover-fit display sizing (Dart build() block): the RotatedBox child is
     * the un-rotated cover-fit size; with odd quarter turns the RotatedBox
     * footprint in the InteractiveViewer space swaps width/height.
     */
    fun layout(srcW: Float, srcH: Float, quarterRotations: Int, cropW: Float, cropH: Float): Layout {
        val isRotated = quarterRotations % 2 == 1
        val rotSrcW = if (isRotated) srcH else srcW
        val rotSrcH = if (isRotated) srcW else srcH
        val coverScale = max(cropW / rotSrcW, cropH / rotSrcH)
        val rotDispW = rotSrcW * coverScale
        val rotDispH = rotSrcH * coverScale
        val childW = if (isRotated) rotDispH else rotDispW
        val childH = if (isRotated) rotDispW else rotDispH
        val footW = if (isRotated) childH else childW
        val footH = if (isRotated) childW else childH
        return Layout(childW, childH, footW, footH)
    }

    /** InteractiveViewer scale clamp. */
    fun clampScale(scale: Float): Float = scale.coerceIn(MIN_SCALE, MAX_SCALE)

    /**
     * InteractiveViewer with boundaryMargin zero, constrained:false,
     * alignment center: pan is clamped so the scaled child footprint always
     * covers the crop window (a blank crop is impossible).
     */
    fun clampOffset(offsetX: Float, offsetY: Float, scale: Float, footW: Float, footH: Float, cropW: Float, cropH: Float): Pair<Float, Float> {
        val maxX = ((footW * scale - cropW) / 2f).coerceAtLeast(0f)
        val maxY = ((footH * scale - cropH) / 2f).coerceAtLeast(0f)
        return Pair(offsetX.coerceIn(-maxX, maxX), offsetY.coerceIn(-maxY, maxY))
    }

    /** Output raster size of the captured PNG (crop window * 2.5). */
    fun captureSize(cropW: Float, cropH: Float): Pair<Int, Int> =
        Pair((cropW * CAPTURE_PIXEL_RATIO).toInt().coerceAtLeast(1), (cropH * CAPTURE_PIXEL_RATIO).toInt().coerceAtLeast(1))
}
