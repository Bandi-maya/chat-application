package com.example.chat.ui.calls

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.calls.bundles.CallBundleRegistry
import com.example.chat.ui.calls.bundles.CallBundleId

/** User theme owns color; bundle owns geometry and gesture identity. */
@Composable
fun themedCallBundle(id: CallBundleId) = CallBundleRegistry.getTokens(id).let { geometry ->
    val c = LocalAppColors.current
    geometry.copy(primaryAccent = c.accent, acceptColor = c.success, declineColor = c.error,
        surfaceBackground = c.background, cardBackground = c.surface, primaryText = c.foreground,
        secondaryText = c.foregroundSecondary, controlBarBackground = c.surface)
}

/** Semantic overlays deliberately use theme surfaces instead of fixed black/white. */
internal val LocalIncomingVideoBackdrop = androidx.compose.runtime.staticCompositionLocalOf { false }
@Composable fun callBackdrop(): Color = if (LocalIncomingVideoBackdrop.current) Color.Transparent else LocalAppColors.current.background
@Composable fun callSurface(): Color = LocalAppColors.current.surface
@Composable fun callForeground(): Color = LocalAppColors.current.foreground
@Composable fun callSecondary(): Color = LocalAppColors.current.foregroundSecondary
@Composable fun callAccent(): Color = LocalAppColors.current.accent
