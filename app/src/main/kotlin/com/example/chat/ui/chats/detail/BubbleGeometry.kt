package com.example.chat.ui.chats.detail

import com.example.chat.core.theme.BubbleStyleId

/** Exact port of lib/ui/core/bubbles/bubble_geometry.dart (layout data) and
 *  bubble_style_registry.dart (id -> geometry mapping). The vector path
 *  builder lives in BubblePainter.kt (Compose); this file stays JVM-pure so
 *  the geometry table can be unit-tested. */

data class InsetsD(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    companion object {
        fun symmetric(horizontal: Double, vertical: Double) =
            InsetsD(horizontal, vertical, horizontal, vertical)
    }
}

data class BubbleGeometry(
    val styleId: BubbleStyleId,
    val contentPadding: InsetsD = InsetsD.symmetric(horizontal = 12.0, vertical = 8.0),
    val bubbleMargin: InsetsD = InsetsD.symmetric(horizontal = 8.0, vertical = 2.0),
    val tailWidth: Double = 6.0,
    val tailHeight: Double = 10.0,
    val hasCustomPainter: Boolean = false,
    val isOutlined: Boolean = false,
    val strokeWidth: Double = 1.5,
    val hasDropShadow: Boolean = false,
    val shadowElevation: Double = 2.0,
)

object BubbleStyleRegistry {

    fun getGeometry(id: BubbleStyleId): BubbleGeometry = buildGeometry(id)

    private fun buildGeometry(id: BubbleStyleId): BubbleGeometry = when (id) {
        BubbleStyleId.threeD, BubbleStyleId.threeDV2 -> BubbleGeometry(
            styleId = id,
            hasDropShadow = true,
            shadowElevation = 3.5,
            contentPadding = InsetsD(14.0, 10.0, 14.0, 10.0),
        )

        BubbleStyleId.gabiOutline, BubbleStyleId.rcSamsBord,
        BubbleStyleId.rcLine, BubbleStyleId.aranbor -> BubbleGeometry(
            styleId = id,
            contentPadding = InsetsD(13.0, 8.0, 13.0, 8.0),
        )

        // True domes have no painted body beside their centered crown until y=26dp.
        // Text must start below that shoulder, unlike the flat rounded/chamfered styles above.
        BubbleStyleId.rcPal, BubbleStyleId.rcImess -> BubbleGeometry(
            styleId = id,
            contentPadding = InsetsD(13.0, 30.0, 13.0, 9.0),
        )

        BubbleStyleId.rcImLine, BubbleStyleId.rcTranLine -> BubbleGeometry(
            styleId = id,
            isOutlined = true,
            strokeWidth = 1.5,
            contentPadding = InsetsD(13.0, 28.0, 13.0, 9.0),
        )

        BubbleStyleId.rcIosLine, BubbleStyleId.gabiMess -> BubbleGeometry(
            styleId = id,
            isOutlined = true,
            strokeWidth = 1.5,
            contentPadding = InsetsD(13.0, 9.0, 13.0, 9.0),
            bubbleMargin = InsetsD(16.0, 2.0, 16.0, 10.0),
        )

        BubbleStyleId.transparent -> BubbleGeometry(
            styleId = id,
            isOutlined = true,
            strokeWidth = 1.2,
            contentPadding = InsetsD(12.0, 8.0, 12.0, 8.0),
        )

        BubbleStyleId.kitty, BubbleStyleId.amor, BubbleStyleId.gabiDot,
        BubbleStyleId.gabiDot2, BubbleStyleId.ilkhang, BubbleStyleId.rcFancy -> BubbleGeometry(
            styleId = id,
            hasCustomPainter = true,
            contentPadding = InsetsD(14.0, 9.0, 14.0, 9.0),
        )

        BubbleStyleId.goSms, BubbleStyleId.rcGoSms -> BubbleGeometry(
            styleId = id,
            contentPadding = InsetsD(14.0, 9.0, 14.0, 9.0),
            bubbleMargin = InsetsD(16.0, 2.0, 16.0, 10.0),
        )

        BubbleStyleId.textra -> BubbleGeometry(
            styleId = id,
            hasCustomPainter = true,
            contentPadding = InsetsD(14.0, 9.0, 14.0, 9.0),
            bubbleMargin = InsetsD(16.0, 2.0, 16.0, 10.0),
        )

        BubbleStyleId.roundle, BubbleStyleId.gabyRon, BubbleStyleId.rcGoogleAssistan ->
            BubbleGeometry(
                styleId = id,
                contentPadding = InsetsD(16.0, 10.0, 16.0, 10.0),
            )

        BubbleStyleId.fold, BubbleStyleId.foldV2, BubbleStyleId.waPaperRedesigned ->
            BubbleGeometry(
                styleId = id,
                hasCustomPainter = true,
                contentPadding = InsetsD(13.0, 10.0, 13.0, 10.0),
            )

        else -> BubbleGeometry(
            styleId = id,
            contentPadding = InsetsD(12.0, 8.0, 12.0, 8.0),
        )
    }
}
