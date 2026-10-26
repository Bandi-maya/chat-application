package com.example.chat.ui.camera

/** Capture settings describe real operations, not advertised OEM modes. */
enum class CaptureAspect(val label: String, val portraitRatio: Float?) {
    square("1:1", 1f), standard("4:3", 3f / 4f), wide("16:9", 9f / 16f), full("Full", null),
}
data class CameraCaptureOptions(
    val aspect: CaptureAspect = CaptureAspect.full,
    val timerSeconds: Int = 0,
    val grid: Boolean = false,
    val level: Boolean = false,
    val stabilization: Boolean = false,
    val watermark: Boolean = false,
    val clarity: Boolean = false,
)
