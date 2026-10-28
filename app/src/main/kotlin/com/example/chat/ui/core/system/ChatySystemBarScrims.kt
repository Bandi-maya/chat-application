package com.example.chat.ui.core.system

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxScope
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.settings.rememberPreferencesVersion

/**
 * Paints the resolved system bar colours under the bars. The window itself stays transparent
 * (ChatySystemBarsEffect): from Android 15 (targetSdk 35 here) the platform enforces edge-to-edge
 * and ignores Window.setStatusBarColor/setNavigationBarColor, so Compose owns the bar painting.
 * universal.statusBarColor / navigationBarColor win when the user picked a colour; when unset the
 * bar takes the theme surface colour (SystemBarsPolicy, the same fallback the window setter applies
 * on API <= 34) so the strip always matches the app theme and the icon contrast resolved for it,
 * instead of leaking the decor background through (white strip under light icons in dark themes).
 * Draw this as the LAST child of the app root Box (above content, below nothing); it ignores
 * touches because the bar areas are not interactive.
 */
@Composable
fun BoxScope.ChatySystemBarScrims(preferences: ChatyPreferencesController, themeSurfaceArgb: Long) {
    rememberPreferencesVersion(preferences)
    val u = preferences.universal
    val style = SystemBarsPolicy.resolve(u.statusBarColor, u.navigationBarColor, themeSurfaceArgb)
    val density = LocalDensity.current
    val statusTopPx = WindowInsets.statusBars.getTop(density)
    val statusH = with(density) { statusTopPx.toDp() }
    // DIAG-50g TEMP: magenta marker + runtime geometry. Restore Color(style.statusBarColor.toInt()) before delivery.
    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(statusH).background(Color(0xFFFF00FF)).onGloballyPositioned { c ->
        android.util.Log.w("BarDiag", "statusScrim window=${c.boundsInWindow()} parent=${c.parentLayoutCoordinates?.boundsInWindow()} statusTopPx=$statusTopPx resolved=${java.lang.Long.toHexString(style.statusBarColor)} surface=${java.lang.Long.toHexString(themeSurfaceArgb)} customStatus=${u.statusBarColor != null}")
    })
    val navBottomPx = WindowInsets.navigationBars.getBottom(density)
    val navH = with(density) { navBottomPx.toDp() }
    // DIAG-50g TEMP: cyan marker. Restore Color(style.navigationBarColor.toInt()) before delivery.
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(navH).background(Color(0xFF00FFFF)).onGloballyPositioned { c ->
        android.util.Log.w("BarDiag", "navScrim window=${c.boundsInWindow()} navBottomPx=$navBottomPx resolved=${java.lang.Long.toHexString(style.navigationBarColor)}")
    })
}
