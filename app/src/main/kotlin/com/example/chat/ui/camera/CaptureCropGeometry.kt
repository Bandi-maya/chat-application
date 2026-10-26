package com.example.chat.ui.camera

import kotlin.math.roundToInt

/** x,y,width,height bounded inside an existing raster. */
fun centerCropBounds(width: Int, height: Int, ratio: Float): IntArray {
    require(width > 0 && height > 0 && ratio.isFinite() && ratio > 0)
    val cropWidth = minOf(width, (height * ratio).roundToInt().coerceAtLeast(1))
    val cropHeight = minOf(height, (width / ratio).roundToInt().coerceAtLeast(1))
    return intArrayOf((width-cropWidth)/2, (height-cropHeight)/2, cropWidth, cropHeight)
}
