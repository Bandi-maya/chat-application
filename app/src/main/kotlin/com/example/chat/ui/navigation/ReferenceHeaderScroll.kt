package com.example.chat.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf

/** Finger-driven expanded-header fraction shared by the actual root shell and Home header.
 * Scroll does not write a style preference or replace a destination's real data/actions.
 */
val LocalReferenceHeaderCollapse = staticCompositionLocalOf { 0f }

