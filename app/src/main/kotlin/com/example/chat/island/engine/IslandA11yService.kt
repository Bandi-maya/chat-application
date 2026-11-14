package com.example.chat.island.engine

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.example.chat.DynamicIslandNotificationListener
import com.example.chat.island.core.IslandSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** A window host only. The manifest disables content retrieval and gesture injection. */
class IslandA11yService : AccessibilityService() {
    private var binding: Binding? = null

    private inner class Binding {
        val window = IslandOverlay(this@IslandA11yService, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        val scope = MainScope()
        val watch: Job = scope.launch {
            IslandSettings.enabled.collect { requested ->
                val masterEnabled = DynamicIslandNotificationListener.isEnabled(this@IslandA11yService)
                when {
                    requested && masterEnabled -> window.show()
                    else -> window.hide()
                }
            }
        }

        fun close() {
            watch.cancel()
            scope.cancel()
            window.hide()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        releaseWindow()
        IslandSettings.init(applicationContext)
        IslandController.attach(applicationContext)
        binding = Binding()
        connected.value = true
        // The accessibility host is live; a second foreground keep-alive/overlay host is redundant.
        stopService(Intent(this, IslandService::class.java))
    }

    private fun releaseWindow(allowFallback: Boolean = false) {
        val wasConnected = connected.value
        val previous = binding
        binding = null
        previous?.close()
        connected.value = false
        if (allowFallback && wasConnected) com.example.chat.island.core.IslandControl.ensureHost(applicationContext)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        releaseWindow(allowFallback = true)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        releaseWindow(allowFallback = true)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        val connected = MutableStateFlow(false)
    }
}
