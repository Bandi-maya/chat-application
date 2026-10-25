package com.example.chat.ui.vault

/** Exact port of lib/features/chats/hidden_vault_controller.dart.
 *
 *  Controller managing Hidden / Locked Chats Vault access and biometric
 *  authorization. Backed by HiddenVaultService. ChangeNotifier is ported as
 *  the same listener pattern used by ChatyPreferencesController.
 */
class HiddenVaultController(
    val service: HiddenVaultService,
) {
    private val listeners = mutableListOf<() -> Unit>()
    private val onServiceUpdated: () -> Unit = {
        listeners.toList().forEach { it() }
    }

    init {
        service.addListener(onServiceUpdated)
    }

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }

    val isUnlocked: Boolean get() = service.isUnlocked
    val isDecoyMode: Boolean get() = service.isDecoyMode
    val lockedConversationIds: Set<String> get() = service.lockedConversationIds

    fun isConversationLocked(conversationId: String): Boolean {
        return service.isConversationLocked(conversationId)
    }

    suspend fun unlockVault(): Boolean = service.unlockWithBiometrics()

    suspend fun verifyPin(pin: String): Boolean = service.verifyPin(pin)

    fun forceUnlockForSession() = service.forceUnlockForSession()

    fun lockVault() = service.lockVault()

    suspend fun toggleConversationLock(conversationId: String) =
        service.toggleConversationLock(conversationId)

    fun dispose() {
        service.removeListener(onServiceUpdated)
    }
}
