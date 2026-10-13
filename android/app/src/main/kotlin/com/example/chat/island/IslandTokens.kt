package com.example.chat.island

import android.graphics.Color

/**
 * Auto-synchronized design tokens matching `docs/island/specs/island_tokens.json`.
 */
object IslandTokens {
    const val COMPACT_HEIGHT_DP = 36
    const val COMPACT_MIN_WIDTH_DP = 112
    const val COMPACT_DEFAULT_WIDTH_DP = 126
    const val COMPACT_STACKED_WIDTH_DP = 142
    const val COMPACT_CORNER_RADIUS_DP = 18f

    const val EXPANDED_MAX_WIDTH_DP = 384
    const val EXPANDED_HORIZONTAL_MARGIN_DP = 16
    const val EXPANDED_CORNER_RADIUS_DP = 32f

    const val EXPANDED_NOTIFICATION_HEIGHT_DP = 88
    const val EXPANDED_MEDIA_HEIGHT_DP = 168
    const val EXPANDED_CALL_HEIGHT_DP = 148
    const val EXPANDED_CHARGING_HEIGHT_DP = 136
    const val EXPANDED_REPLY_HEIGHT_DP = 156
    const val MINIMAL_DOT_SIZE_DP = 16

    val COLOR_COMPACT_BG = Color.parseColor("#000000")
    val COLOR_EXPANDED_BG = Color.parseColor("#0B0B0E")
    val COLOR_EXPANDED_STROKE = Color.argb(26, 255, 255, 255) // 10% white
    val COLOR_TEXT_PRIMARY = Color.parseColor("#FFFFFF")
    val COLOR_TEXT_SECONDARY = Color.parseColor("#9E9EA7")
    val COLOR_TEXT_TERTIARY = Color.parseColor("#63636E")
    val COLOR_CALL_GREEN = Color.parseColor("#34D399")
    val COLOR_CHARGING_GREEN = Color.parseColor("#10B981")
    val COLOR_ACTION_BLUE = Color.parseColor("#3B82F6")
    val COLOR_DESTRUCTIVE_RED = Color.parseColor("#EF4444")

    // Spring physics configuration
    const val SPRING_POPIN_STIFFNESS = 420f
    const val SPRING_POPIN_DAMPING = 0.72f
    const val SPRING_EXPAND_STIFFNESS = 380f
    const val SPRING_EXPAND_DAMPING = 0.78f
    const val SPRING_COLLAPSE_STIFFNESS = 520f
    const val SPRING_COLLAPSE_DAMPING = 0.90f
    const val SPRING_DISMISS_STIFFNESS = 600f
    const val SPRING_DISMISS_DAMPING = 1.0f
    const val SPRING_PRESS_STIFFNESS = 900f
    const val SPRING_PRESS_DAMPING = 0.85f
}
