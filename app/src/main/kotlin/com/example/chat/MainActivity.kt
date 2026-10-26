package com.example.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.chat.ui.AppRoot

/**
 * Native Kotlin entry point for the Chaty port.
 *
 * Parity target: the Flutter app's lib/main.dart on the api branch
 * (commit 3bee780d). AppRoot wraps itself in ChatyTheme driven by the ported
 * ThemeController; see docs/kotlin-parity.md for the tracking matrix.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // registerForActivityResult must run before STARTED (launcher-icon pickers).
        ServiceLocator.appIconBridge.register(this)
        setContent {
            AppRoot()
        }
    }

    // Dart native_call_pip_service modeChanges: forward the system PiP state.
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        ServiceLocator.nativeCallPip.onPipModeChanged(isInPictureInPictureMode)
    }
}
