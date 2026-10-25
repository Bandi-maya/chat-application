package com.example.chat.data.services

import java.util.concurrent.ConcurrentHashMap

/** Port of lib/data/services/protected_resource_gate.dart: the centralized
 *  authorization gate for protected resources - session unlock caching with
 *  the configured auto-lock timeout, credential-setup routing when no
 *  credential exists, and invalidation on background/lock.
 *
 *  The Dart gate is BuildContext-driven (it shows LockCredentialSetupModal
 *  and AppLockOverlayModal inline). Here the two UI interactions are
 *  injected suspend seams so the authorization policy itself stays
 *  JVM-testable; the AppRoot wires them to the real overlays. */
object ProtectedResourceGate {

    /** Shows the credential-setup flow; returns true when a credential was
     *  created (Dart LockCredentialSetupModal.show). */
    fun interface SetupPrompt {
        suspend fun show(method: String, pinLength: Int): Boolean
    }

    /** Shows the unified lock prompt; returns true on verified local
     *  authentication (Dart AppLockOverlayModal.show). */
    fun interface LockPrompt {
        suspend fun show(title: String, reason: String): Boolean
    }

    private val unlockedConversations = ConcurrentHashMap<String, Long>()

    @Volatile
    private var lastAppUnlockTime: Long? = null

    val hasRecentAppUnlock: Boolean get() = lastAppUnlockTime != null

    /** Dart isConversationSessionActive. Unprotected conversations are
     *  always active; "Immediately" (zero timeout) never is. */
    fun isConversationSessionActive(
        conversationId: String,
        isProtected: Boolean,
        autoLockTimeout: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!isProtected) return true
        val unlockedAt = unlockedConversations[conversationId] ?: return false
        val timeoutMillis = parseTimeoutMillis(autoLockTimeout)
        if (timeoutMillis == 0L) return false
        return nowMillis - unlockedAt < timeoutMillis
    }

    /** Dart authorizeConversation: instant allow for unprotected or
     *  in-session conversations; otherwise credential setup when the method
     *  is unconfigured, then the unified lock prompt. Returns true only on
     *  verified local authentication. */
    suspend fun authorizeConversation(
        conversationId: String,
        isProtected: Boolean,
        autoLockTimeout: String,
        lockMethod: String,
        lockService: LocalLockService,
        setupPrompt: SetupPrompt,
        lockPrompt: LockPrompt,
        title: String = "Locked Chat",
        reason: String = "Authenticate to open this conversation",
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!isProtected) return true
        if (isConversationSessionActive(conversationId, isProtected, autoLockTimeout, nowMillis)) {
            return true
        }
        if (!isMethodConfigured(lockService, lockMethod)) {
            val pinLength = lockService.getPinLength()
            if (!setupPrompt.show(lockMethod, pinLength)) return false
        }
        if (!lockPrompt.show(title, reason)) return false
        unlockedConversations[conversationId] = nowMillis
        lastAppUnlockTime = nowMillis
        return true
    }

    /** Dart authorizeGeneralAction: no credential ever configured means no
     *  check at all; otherwise the unified lock prompt guards the change. */
    suspend fun authorizeGeneralAction(
        lockMethod: String,
        lockService: LocalLockService,
        lockPrompt: LockPrompt,
        title: String,
        reason: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!isMethodConfigured(lockService, lockMethod)) return true
        if (!lockPrompt.show(title, reason)) return false
        lastAppUnlockTime = nowMillis
        return true
    }

    /** Dart invalidateAllSessions (app background / lock). */
    fun invalidateAllSessions() {
        unlockedConversations.clear()
        lastAppUnlockTime = null
    }

    /** Dart invalidateConversationSession (leaving a chat). */
    fun invalidateConversationSession(conversationId: String) {
        unlockedConversations.remove(conversationId)
    }

    /** Dart _isMethodConfigured. */
    suspend fun isMethodConfigured(service: LocalLockService, method: String): Boolean =
        when (method) {
            "PIN", "Pattern", "Password" -> service.hasCredential(method)
            "Biometric" -> service.canUseBiometrics()
            "Device Credential" -> true
            else -> false
        }

    /** Dart _parseTimeout: unknown/"Immediately" maps to zero. */
    fun parseTimeoutMillis(value: String): Long = when (value) {
        "15s" -> 15_000L
        "30s" -> 30_000L
        "1m" -> 60_000L
        "5m" -> 300_000L
        "15m" -> 900_000L
        else -> 0L
    }
}
