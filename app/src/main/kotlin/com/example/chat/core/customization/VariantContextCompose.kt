package com.example.chat.core.customization

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.chat.ui.theme.LocalThemeConfig

/** Compose analogue of VariantContext.fromContext (component_capabilities.dart): width/height from the window
 *  (or [localMaxWidthDp]/[localMaxHeightDp] = Dart localConstraints), brightness from the active ThemeConfig,
 *  text scale from the density font scale, RTL from the layout direction. highContrast / reduceMotion have no
 *  MediaQuery equivalent wired here: pass them explicitly (default false, same as Dart when MediaQuery is absent). */
@Composable
@ReadOnlyComposable
fun rememberVariantContext(
    localMaxWidthDp: Double? = null,
    localMaxHeightDp: Double? = null,
    isHighContrast: Boolean = false,
    isReducedMotion: Boolean = false,
): VariantContext {
    val cfg = LocalConfiguration.current
    return VariantContext(
        availableWidth = localMaxWidthDp ?: cfg.screenWidthDp.toDouble(),
        availableHeight = localMaxHeightDp ?: cfg.screenHeightDp.toDouble(),
        brightness = if (LocalThemeConfig.current.isDark) Brightness.dark else Brightness.light,
        isHighContrast = isHighContrast,
        isReducedMotion = isReducedMotion,
        textScaleFactor = LocalDensity.current.fontScale.toDouble(),
        isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl,
    )
}

/** Port of ComponentVariantResolver.resolveForContext. */
@Composable
fun resolveComponentForContext(
    componentId: ComponentId,
    snapshot: CustomizationSnapshot,
    scope: ComponentScope = ComponentScope.globalUser,
    previewSnapshot: CustomizationSnapshot? = null,
    localMaxWidthDp: Double? = null,
    localMaxHeightDp: Double? = null,
): ComponentSpec = ComponentVariantResolver().resolve(
    componentId, snapshot, rememberVariantContext(localMaxWidthDp, localMaxHeightDp), scope, previewSnapshot,
)
