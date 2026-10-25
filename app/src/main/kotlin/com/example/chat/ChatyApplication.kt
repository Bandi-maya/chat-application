package com.example.chat

import android.app.Application
import com.example.chat.core.crypto.uniffi.UniffiMlsEngine
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
        // Dart main() awaits ThemeController.init() before runApp so the first
        // frame already reflects the user's theme; restore it off the main
        // thread into the controller's own scope before AppRoot reads it.
        ServiceLocator.appScope.launch { ServiceLocator.themeController.init() }
    }
}
