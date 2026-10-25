package com.example.chat.ui.island

import androidx.compose.ui.graphics.Color

/** Exact port of lib/ui/core/design_system/chaty_island_tokens.dart.
 *
 *  Design tokens for the Chaty Island matching docs/island/specs/island_tokens.json.
 */
object IslandTokens {
    const val compactHeightDp = 36.0
    const val compactMinWidthDp = 112.0
    const val compactDefaultWidthDp = 126.0
    const val compactStackedWidthDp = 142.0
    const val compactCornerRadiusDp = 18.0

    const val expandedMaxWidthDp = 384.0
    const val expandedHorizontalMarginDp = 16.0
    const val expandedCornerRadiusDp = 32.0

    const val expandedNotificationHeightDp = 88.0
    const val expandedMediaHeightDp = 168.0
    const val expandedCallHeightDp = 148.0
    const val expandedChargingHeightDp = 136.0
    const val expandedReplyHeightDp = 156.0
    const val minimalDotSizeDp = 16.0

    val compactBackground = Color(0xFF000000)
    val expandedBackground = Color(0xFF0B0B0E)
    val expandedStroke = Color(0x1AFFFFFF)
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFF9E9EA7)
    val textTertiary = Color(0xFF63636E)
    val accentCallGreen = Color(0xFF34D399)
    val accentChargingGreen = Color(0xFF10B981)
    val accentActionBlue = Color(0xFF3B82F6)
    val accentDestructiveRed = Color(0xFFEF4444)

    // Springs
    const val popInStiffness = 420.0
    const val popInDamping = 0.72
    const val expandStiffness = 380.0
    const val expandDamping = 0.78
    const val collapseStiffness = 520.0
    const val collapseDamping = 0.90
}
