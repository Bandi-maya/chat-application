package com.example.chat.ui.core.system

import android.os.Build
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxScope
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.settings.rememberPreferencesVersion

/**
 * API 35+ fallback for universal.statusBarColor / navigationBarColor: from Android 15 (targetSdk 35 here) the platform
 * enforces edge-to-edge and ignores Window.setStatusBarColor/setNavigationBarColor, so the chosen colour has to be drawn
 * under the bars. Draw this as the LAST child of the app root Box (above content, below nothing), it ignores touches
 * because the bar areas are not interactive. Only draws for bars where the user actually picked a colour (null = Dart
 * default, which Flutter itself also cannot apply on API 35, so nothing is added). On API <= 34 ChatySystemBarsEffect
 * already paints the real window colours and this draws nothing.
 */
@Composable
fun BoxScope.ChatySystemBarScrims(preferences: ChatyPreferencesController) {
    if (Build.VERSION.SDK_INT < 35) return
    rememberPreferencesVersion(preferences)
    val u = preferences.universal
    val density = LocalDensity.current
    u.statusBarColor?.let { c ->
        val h = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(h).background(Color(c.toInt())))
    }
    u.navigationBarColor?.let { c ->
        val h = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(h).background(Color(c.toInt())))
    }
}
