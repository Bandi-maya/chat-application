package com.example.chat.ui.chats.detail

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * WhatsApp/Telegram-style message interaction anchor computation.
 *
 * Given the bubble's coordinates in the message region viewport:
 * 1. Reaction rail is anchored ABOVE the bubble if there is sufficient headroom (>= railHeight + margin),
 *    otherwise it is placed immediately BELOW the bubble.
 * 2. Contextual action menu is anchored BELOW the reaction rail (or below the bubble).
 * 3. Menu and rail coordinates are strictly clamped within [0, availableWidth] and [0, availableHeight]
 *    without double-subtracting IME insets (since the parent message region already applies imePadding).
 */
data class MessageInteractionPlacement(
    val reactionRailTopDp: Dp,
    val reactionRailLeftDp: Dp,
    val menuTopDp: Dp,
    val menuLeftDp: Dp,
    val menuMaxHeightDp: Dp,
)

object MessageInteractionAnchorPolicy {

    fun computePlacement(
        bubbleBounds: Rect?,
        viewportWidthDp: Dp,
        viewportHeightDp: Dp,
        density: Float,
        isOutgoing: Boolean,
        railHeightDp: Dp = 48.dp,
        menuWidthDp: Dp = 260.dp,
        marginDp: Dp = 8.dp,
    ): MessageInteractionPlacement {
        if (density <= 0f || viewportWidthDp <= 0.dp || viewportHeightDp <= 0.dp) {
            return MessageInteractionPlacement(
                reactionRailTopDp = marginDp,
                reactionRailLeftDp = marginDp,
                menuTopDp = marginDp + railHeightDp + marginDp,
                menuLeftDp = marginDp,
                menuMaxHeightDp = (viewportHeightDp - (marginDp * 3) - railHeightDp).coerceAtLeast(100.dp),
            )
        }

        val bubbleLeftDp = if (bubbleBounds != null) (bubbleBounds.left / density).dp else marginDp
        val bubbleRightDp = if (bubbleBounds != null) (bubbleBounds.right / density).dp else viewportWidthDp - marginDp
        val bubbleTopDp = if (bubbleBounds != null) (bubbleBounds.top / density).dp else (viewportHeightDp * 0.35f)
        val bubbleBottomDp = if (bubbleBounds != null) (bubbleBounds.bottom / density).dp else bubbleTopDp + 60.dp

        // Compute horizontal alignment for menu and rail
        val idealLeftDp = if (isOutgoing) {
            (bubbleRightDp - menuWidthDp).coerceAtLeast(marginDp)
        } else {
            bubbleLeftDp.coerceAtLeast(marginDp)
        }
        val clampedMenuLeftDp = idealLeftDp.coerceIn(marginDp, (viewportWidthDp - menuWidthDp - marginDp).coerceAtLeast(marginDp))

        // Check if there is enough vertical space above the bubble for reaction rail
        val spaceAbove = bubbleTopDp - marginDp
        val railAbove = spaceAbove >= (railHeightDp + marginDp)

        val railTopDp: Dp
        val menuTopDp: Dp

        if (railAbove) {
            // Reaction rail sits above the bubble
            railTopDp = (bubbleTopDp - railHeightDp - marginDp).coerceAtLeast(marginDp)
            // Menu sits below the bubble
            menuTopDp = (bubbleBottomDp + marginDp).coerceAtMost((viewportHeightDp - 120.dp).coerceAtLeast(marginDp))
        } else {
            // Both rail and menu must go below the bubble
            railTopDp = (bubbleBottomDp + marginDp).coerceAtMost((viewportHeightDp - railHeightDp - marginDp).coerceAtLeast(marginDp))
            menuTopDp = (railTopDp + railHeightDp + marginDp).coerceAtMost((viewportHeightDp - 60.dp).coerceAtLeast(marginDp))
        }

        val availableMenuHeight = (viewportHeightDp - menuTopDp - marginDp).coerceAtLeast(120.dp)

        return MessageInteractionPlacement(
            reactionRailTopDp = railTopDp,
            reactionRailLeftDp = clampedMenuLeftDp,
            menuTopDp = menuTopDp,
            menuLeftDp = clampedMenuLeftDp,
            menuMaxHeightDp = availableMenuHeight,
        )
    }
}
