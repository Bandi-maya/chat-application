package com.example.chat.ui.navigation

import kotlin.math.min

/** Keep a reachable 48dp dismissal/foreground lane without changing normal sizes. */
internal object DrawerLayoutMetrics {
    const val dismissalLane = 48f
    fun modernWidth(available: Float): Float = min(300f, (available - dismissalLane).coerceAtLeast(0f))
    fun perspectiveWidth(available: Float): Float = min(210f, (available - dismissalLane).coerceAtLeast(0f))
    fun perspectiveTranslation(available: Float): Float =
        min(220f, (available - dismissalLane).coerceAtLeast(0f))
}
