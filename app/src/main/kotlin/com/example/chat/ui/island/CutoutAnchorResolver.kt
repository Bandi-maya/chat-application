package com.example.chat.ui.island

import kotlin.math.max

/** Exact port of lib/features/island/widgets/cutout_anchor_resolver.dart.
 *
 *  Resolves hardware camera-cutout geometry and status-bar heights to
 *  position the Dynamic Island across diverse hardware. Pure math over an
 *  input snapshot, so it is JVM-testable; the Compose/Android adapter that
 *  fills [CutoutAnchorInput] from WindowInsets/displayCutout lives with the
 *  overlay host (device-side seam). */

enum class CutoutAlignment { topLeft, topCenter, topRight }

data class CutoutAnchorMetrics(
    val topOffset: Double,
    val cutoutWidth: Double,
    val cutoutHeight: Double,
    val alignment: CutoutAlignment,
    val hasCutout: Boolean,
)

/** Snapshot of the MediaQuery values the Dart resolver reads. */
data class CutoutAnchorInput(
    val viewPaddingTop: Double,
    val paddingTop: Double,
    val screenWidth: Double,
    /** Bounds (left, top, right, bottom) of each DisplayFeatureType.cutout. */
    val cutoutBounds: List<DoubleArray> = emptyList(),
)

object CutoutAnchorResolver {

    fun resolve(input: CutoutAnchorInput): CutoutAnchorMetrics {
        val topPadding = if (input.viewPaddingTop > 0) input.viewPaddingTop else input.paddingTop

        // First cutout whose top edge sits within the status-bar band.
        var cutoutRect: DoubleArray? = null
        for (bounds in input.cutoutBounds) {
            if (bounds[1] < topPadding + 16) {
                cutoutRect = bounds
                break
            }
        }

        if (cutoutRect != null && !(cutoutRect[0] >= cutoutRect[2] || cutoutRect[1] >= cutoutRect[3])) {
            val width = cutoutRect[2] - cutoutRect[0]
            val height = cutoutRect[3] - cutoutRect[1]
            val centerX = cutoutRect[0] + width / 2
            var align = CutoutAlignment.topCenter
            if (centerX < input.screenWidth * 0.35) {
                align = CutoutAlignment.topLeft
            } else if (centerX > input.screenWidth * 0.65) {
                align = CutoutAlignment.topRight
            }

            // Vertical centering over the hardware punch-hole or notch.
            val verticalCenter = cutoutRect[1] + height / 2
            val topOffset = max(2.0, verticalCenter - 18.0)

            return CutoutAnchorMetrics(
                topOffset = topOffset,
                cutoutWidth = width,
                cutoutHeight = height,
                alignment = align,
                hasCutout = true,
            )
        }

        // Default status-bar centered fallback.
        val effectiveTop = if (topPadding > 0) {
            max(6.0, (topPadding - 36.0) / 2 + 3.0)
        } else {
            10.0
        }

        return CutoutAnchorMetrics(
            topOffset = effectiveTop,
            cutoutWidth = 32.0,
            cutoutHeight = 32.0,
            alignment = CutoutAlignment.topCenter,
            hasCutout = false,
        )
    }
}
