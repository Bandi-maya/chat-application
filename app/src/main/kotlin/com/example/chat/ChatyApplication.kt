package com.example.chat

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.chat.core.crypto.uniffi.UniffiMlsEngine
import com.example.chat.data.services.ProtectedResourceGate
import kotlinx.coroutines.launch

/** Mirrors main.dart's process-level bootstrap order: native MLS engine
 *  factory install (the Dart side loads the native library at service first
 *  use; installing at app start keeps first-use latency off the UI path),
 *  then locator setup + persisted auth session restore. */
class ChatyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        UniffiMlsEngine.install()
        ServiceLocator.init(this)
        // Dynamic Island: restore the island engine when the
        // user left it on and the required permissions are still granted. The accessibility
        // service alone survives reboot; this restarts the keep-alive foreground service.
        try {
            com.example.chat.island.core.IslandSettings.init(this)
            if (DynamicIslandNotificationListener.isEnabled(this) &&
                com.example.chat.island.core.Perms.coreOk(this)
            ) {
                com.example.chat.island.core.IslandControl.start(this)
            }
        } catch (e: Exception) {
            android.util.Log.w("ChatyApplication", "Dynamic Island bootstrap skipped: $e")
        }
        // Clear a service left running by an older process/build. Idle background
        // delivery now uses FCM; only an accepted active call may own its service.
        com.example.chat.notifications.ChatyRealtimeForegroundService.stop(this)
        // Ring / notify even when no screen is open (process started by a push or kept alive).
        try {
            ServiceLocator.startCallNotifications()
        } catch (e: Exception) {
            android.util.Log.w("ChatyApplication", "call notifications not started: $e")
        }
        // Dart main() awaits ThemeController.init() before runApp so the first
        // frame already reflects the user's theme; restore it off the main
        // thread into the controller's own scope before AppRoot reads it.
        ServiceLocator.appScope.launch {
            ServiceLocator.themeController.init()
            // Restore template configuration without replacing the user's saved Home layout.
            // Theme init is already awaited; only an explicit template Apply changes presentation.
            ServiceLocator.templateController.init(ServiceLocator.templateRuntime, restorePresentation = false)
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(foregroundObserver)
    }

    /** Dart _ChatyAppState.didChangeAppLifecycleState (main.dart:669), matched
     *  at the same granularity Flutter uses: process-level visibility via
     *  ProcessLifecycleOwner. ON_STOP corresponds to Flutter's
     *  paused/hidden/detached background branch (invalidates every
     *  protected-resource session); ON_START corresponds to resumed
     *  (refresh API plane + best-effort device registration). Using the
     *  process owner rather than Activity.onStop/onStart keeps Dart's
     *  behavior for pickers and permission screens: a permission dialog
     *  never stops the process, and an external full-screen picker stops it
     *  in both worlds. The auto-lock timing side of Dart's resumed branch
     *  (_applyAutoLockOnResume) remains with the AppRoot overlay slice. */
    private val foregroundObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            com.example.chat.notifications.ChatyNotifications.appInForeground = false
            ServiceLocator.refreshCallAlert()
            ProtectedResourceGate.invalidateAllSessions()
            ServiceLocator.appLock.onBackgrounded()
        }

        override fun onStart(owner: LifecycleOwner) {
            com.example.chat.notifications.ChatyNotifications.appInForeground = true
            ServiceLocator.refreshCallAlert()
            ServiceLocator.appScope.launch { ServiceLocator.appLock.onResumed() }
            val backend = ServiceLocator.apiBackend
            if (backend.isAuthenticated && !backend.isDemoSession) {
                backend.onAppResumed()
                try { ServiceLocator.callLifecycle.onAppResumed() } catch (e: Exception) { android.util.Log.w("ChatyApplication", "call resume sync failed: $e") }
                ServiceLocator.appScope.launch {
                    try {
                        ServiceLocator.contactRelationships.registerCurrentDevice()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }
}
