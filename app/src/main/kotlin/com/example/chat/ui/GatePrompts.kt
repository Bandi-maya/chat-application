package com.example.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.chat.ServiceLocator
import com.example.chat.data.services.ProtectedResourceGate
import com.example.chat.ui.settings.security.AppLockOverlay
import com.example.chat.ui.settings.security.LockCredentialSetupSheet
import com.example.chat.ui.settings.security.LockSetupServiceAdapter
import kotlinx.coroutines.CompletableDeferred

/**
 * Compose-side implementation of ProtectedResourceGate's two UI seams
 * (Dart: LockCredentialSetupModal and AppLockOverlayModal shown over the
 * current route). [setupPrompt]/[lockPrompt] suspend until the user finishes
 * or cancels; [Host] renders whichever request is pending.
 */
class GatePrompts {
    private class LockReq(val title: String, val reason: String, val result: CompletableDeferred<Boolean>)
    private class SetupReq(val method: String, val pinLength: Int, val result: CompletableDeferred<Boolean>)

    private var lockReq by mutableStateOf<LockReq?>(null)
    private var setupReq by mutableStateOf<SetupReq?>(null)

    val setupPrompt = ProtectedResourceGate.SetupPrompt { method, pinLength ->
        val d = CompletableDeferred<Boolean>()
        setupReq = SetupReq(method, pinLength, d)
        try { d.await() } finally { setupReq = null }
    }

    val lockPrompt = ProtectedResourceGate.LockPrompt { title, reason ->
        val d = CompletableDeferred<Boolean>()
        lockReq = LockReq(title, reason, d)
        try { d.await() } finally { lockReq = null }
    }

    @Composable
    fun Host() {
        val setup = setupReq
        if (setup != null) {
            LockCredentialSetupSheet(
                method = setup.method,
                pinLength = setup.pinLength,
                service = LockSetupServiceAdapter(ServiceLocator.localLockService),
                replaceExisting = false,
                onResult = { setup.result.complete(it) },
            )
        }
        val lock = lockReq
        if (lock != null && setup == null) {
            // Dart AppLockOverlayModal: barrierDismissible=false and PopScope(canPop=false);
            // the only exit is a verified unlock, so back is swallowed and there is no cancel.
            BackHandler { }
            Box(Modifier.fillMaxSize()) {
                AppLockOverlay(
                    preferences = ServiceLocator.preferencesController,
                    lockService = ServiceLocator.localLockService,
                    onUnlocked = { lock.result.complete(true) },
                    title = lock.title,
                    reason = lock.reason,
                )
            }
        }
    }
}
