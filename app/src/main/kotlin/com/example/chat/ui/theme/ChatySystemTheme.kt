package com.example.chat.ui.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.chat.core.theme.ThemeController

/** Keep system-follow live; the controller itself protects explicitly chosen/custom themes. */
@Composable
fun SyncChatySystemTheme(controller: ThemeController, revision: Int) {
    val configuration = LocalConfiguration.current
    val isLight = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    // Revision also covers restoration/reset-to-defaults after the first composition.
    LaunchedEffect(controller, isLight, revision) { controller.syncWithSystem(isLight) }
    DisposableEffect(controller, context, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val light = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
                controller.syncWithSystem(light)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
