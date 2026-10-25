package com.example.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.chat.data.services.ProtectedResourceGate
import com.example.chat.ui.AppRoot
import kotlinx.coroutines.launch

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
        setContent {
            AppRoot()
        }
    }

    /** Dart _ChatyAppState.didChangeAppLifecycleState (main.dart:669):
     *  backgrounding invalidates every protected-resource session. */
    override fun onStop() {
        super.onStop()
        ProtectedResourceGate.invalidateAllSessions()
    }

    /** Dart resumed branch: refresh the API plane and best-effort register
     *  the current device. Auto-lock timing lives with the AppRoot overlay
     *  slice (still open); the gate invalidation above is its session side. */
    override fun onStart() {
        super.onStart()
        val backend = ServiceLocator.apiBackend
        if (backend.isAuthenticated) {
            backend.onAppResumed()
            lifecycleScope.launch {
                try {
                    ServiceLocator.contactRelationships.registerCurrentDevice()
                } catch (_: Exception) {
                }
            }
        }
    }
}
