package com.example.chat.ui.core.system

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.settings.rememberPreferencesVersion

/**
 * main.dart universal.statusBarColor / navigationBarColor (SystemUiOverlayStyle via AnnotatedRegion). Call once at the app root
 * (inside ChatyTheme) with the current ThemeConfig.surfaceColor (ARGB Long). The bars are painted by drawing behind them
 * (edge-to-edge is already on in MainActivity), so this sets window.statusBarColor/navigationBarColor plus icon appearance.
 * On API 35+ system bar colour setters are ignored by the platform (edge-to-edge enforced), so there only the icon
 * appearance applies; a scrim drawn in Compose would be needed for the colour (not provided here).
 */
@Composable
fun ChatySystemBarsEffect(preferences: ChatyPreferencesController, themeSurfaceArgb: Long) {
    rememberPreferencesVersion(preferences)
    val u = preferences.universal
    val style = SystemBarsPolicy.resolve(u.statusBarColor, u.navigationBarColor, themeSurfaceArgb)
    val view = LocalView.current
    SideEffect {
        // DIAG-50g TEMP: runtime environment for the bar-paint path (removed before delivery).
        android.util.Log.w("BarDiag", "effect sdk=${android.os.Build.VERSION.SDK_INT} rootStatusTopPx=${androidx.core.view.ViewCompat.getRootWindowInsets(view)?.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())?.top} rootNavBottomPx=${androidx.core.view.ViewCompat.getRootWindowInsets(view)?.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())?.bottom} status=${java.lang.Long.toHexString(style.statusBarColor)} nav=${java.lang.Long.toHexString(style.navigationBarColor)} surface=${java.lang.Long.toHexString(themeSurfaceArgb)}")
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        @Suppress("DEPRECATION") run {
            // The window-level status bar background can be painted by the decor past
            // the bar region and cover app content (measured: a 2x status-inset band,
            // top 256px at a 128px status inset, on the CI emulator). ChatySystemBarScrims
            // already paints the resolved colour under the bar, so the window setter
            // stays transparent and Compose owns the bar painting.
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = style.navigationBarColor.toInt()
        }
        val c = WindowCompat.getInsetsController(window, view)
        c.isAppearanceLightStatusBars = !style.lightStatusIcons
        c.isAppearanceLightNavigationBars = !style.lightNavIcons
    }
}

/**
 * main.dart presence switches: airplane / ghost / always-online on preference change and app lifecycle.
 * [authenticated] and [publish] come from the root (publish(online) -> PresenceService.setPresence(online/offline));
 * this composable owns only the Dart decision logic (PresencePolicy) and the lifecycle/preference wiring.
 */
@Composable
fun ChatyPresenceEffect(
    preferences: ChatyPreferencesController,
    authenticated: () -> Boolean,
    publish: (online: Boolean) -> Unit,
) {
    rememberPreferencesVersion(preferences)
    val auth by rememberUpdatedState(authenticated)
    val pub by rememberUpdatedState(publish)
    var wasActive by remember { mutableStateOf(false) }
    fun flags(): Triple<Boolean, Boolean, Boolean> {
        val p = preferences
        return Triple(
            p.home.airplaneModeSimulator || p.gbBool("yo_want_airplanemode"),
            p.home.ghostMode || p.gbBool("yo_want_ghostmode"),
            p.universal.enableAlwaysOnline || p.gbBool("always_online"),
        )
    }
    val owner = ProcessLifecycleOwner.get()
    fun apply(d: PresenceDecision) {
        wasActive = d.overrideWasActive
        when (d.action) {
            PresenceAction.Offline -> pub(false)
            PresenceAction.Online -> pub(true)
            PresenceAction.None -> {}
        }
    }
    // Preference-change handler (runs on every preference recomposition, like the Dart listener).
    val (air, ghost, _) = flags()
    androidx.compose.runtime.LaunchedEffect(air, ghost) {
        apply(PresencePolicy.onPreferenceChange(air, ghost, wasActive, auth(), owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)))
    }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            val st = when (e) {
                Lifecycle.Event.ON_RESUME -> AppLifecycle.resumed
                Lifecycle.Event.ON_PAUSE -> AppLifecycle.inactive
                Lifecycle.Event.ON_STOP -> AppLifecycle.paused
                Lifecycle.Event.ON_DESTROY -> AppLifecycle.detached
                else -> return@LifecycleEventObserver
            }
            val (a, g, ao) = flags()
            apply(PresencePolicy.onLifecycle(st, a, g, ao, auth(), wasActive))
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}
