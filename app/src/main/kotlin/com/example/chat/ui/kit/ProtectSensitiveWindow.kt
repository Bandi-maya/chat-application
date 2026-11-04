package com.example.chat.ui.kit

import android.app.Activity
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/** Scoped screenshot/recording/recents protection. Never changes lock or crypto state. */
@Composable
fun ProtectSensitiveWindow(active: Boolean = true) {
    val context = LocalContext.current
    val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
        .firstOrNull { it is Activity } as? Activity
    val window = activity?.window
    DisposableEffect(window, active) {
        val release = if (active && window != null) SensitiveWindowLeases.acquire(window) else ({})
        onDispose { release() }
    }
}

private object SensitiveWindowLeases {
    private data class Lease(var count: Int, val alreadySecure: Boolean)
    private val leases = java.util.WeakHashMap<Window, Lease>()
    fun acquire(window: Window): () -> Unit {
        val existing = leases[window]
        if (existing != null) existing.count++
        else {
            val secure = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
            leases[window] = Lease(1, secure)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        var released = false
        return {
            if (!released) {
                released = true
                val lease = leases[window]
                if (lease != null && --lease.count == 0) {
                    leases.remove(window)
                    if (!lease.alreadySecure) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
    }
}
