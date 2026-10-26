package com.example.chat.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf

/** Destination identified by a visible shell heading/top tab; null in standalone/bottom-nav screens. */
val LocalDestinationTitlePresentation = staticCompositionLocalOf<String?> { null }
