package com.example.chat.ui.vault

import com.example.chat.data.e2ee.SecureKeyValueStore
import com.example.chat.data.services.LocalLockService

/** Exact port of lib/core/services/hidden_vault_service.dart.
 *
 *  Service coordinating PBKDF2 verification, biometric gate, auto-lock
 *  timeouts, decoy mode, and encrypted locked-conversation IDs.
 *  ChangeNotifier is ported as the same listener pattern used by
 *  ChatyPreferencesController.
 *
 *  Seams:
 *  - FlutterSecureStorage -> [SecureKeyValueStore] (existing encrypted store).
 *  - local_auth biometric -> [authenticateBiometric]. The Kotlin
 *    LocalLockService does not yet expose the BiometricPrompt bridge the Dart
 *    local_auth plugin provided; the integration layer must supply it
 *    (androidx.biometric, free) for biometric unlock to succeed. PIN unlock
 *    is fully functional without it.
 */
class HiddenVaultService(
    private val lockService: LocalLockService,
    private val secureStorage: SecureKeyValueStore,
    private val authenticateBiometric: suspend (reason: String) -> Boolean = { false },
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    companion object {
        private const val REGISTRY_KEY = "chaty.hidden_vault.locked_conversations"
        private const val DECOY_PIN_KEY = "chaty.hidden_vault.decoy_pin"
        const val AUTO_LOCK_DURATION_MS: Long = 2 * 60 * 1000 // Duration(minutes: 2)
    }

    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyListeners() { listeners.toList().forEach { it() } }

    private val _lockedConversationIds = mutableSetOf<String>()
    private var _isUnlocked = false
    private var _isDecoyMode = false
    private var _lastActivityTime: Long? = null

    val isUnlocked: Boolean get() = _isUnlocked
    val isDecoyMode: Boolean get() = _isDecoyMode
    val lastActivityTime: Long? get() = _lastActivityTime
    val autoLockDurationMs: Long get() = AUTO_LOCK_DURATION_MS
    val lockedConversationIds: Set<String>
        get() = if (_isDecoyMode) emptySet() else _lockedConversationIds.toSet()

    /** Port of the Dart constructor's _loadVaultRegistry() kickoff. Call from
     *  a coroutine after construction. */
    suspend fun loadVaultRegistry() {
        try {
            val raw = secureStorage.read(REGISTRY_KEY)
            if (!raw.isNullOrEmpty()) {
                _lockedConversationIds.addAll(raw.split(',').filter { it.isNotEmpty() })
                notifyListeners()
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun persistRegistry() {
        try {
            secureStorage.write(REGISTRY_KEY, _lockedConversationIds.joinToString(","))
        } catch (_: Exception) {
        }
    }

    fun isConversationLocked(conversationId: String): Boolean {
        return _lockedConversationIds.contains(conversationId)
    }

    suspend fun toggleConversationLock(conversationId: String) {
        if (!_lockedConversationIds.add(conversationId)) {
            _lockedConversationIds.remove(conversationId)
        }
        persistRegistry()
        notifyListeners()
    }

    /** Unlocks with local biometric authentication. */
    suspend fun unlockWithBiometrics(): Boolean {
        try {
            val success = authenticateBiometric("Authenticate with biometrics to access Hidden Vault")
            if (success) {
                _isUnlocked = true
                _isDecoyMode = false
                _lastActivityTime = now()
                notifyListeners()
                return true
            }
        } catch (_: Exception) {
            // Dart: debugPrint('Vault biometric authentication error: $e')
        }
        return false
    }

    /** Verifies PIN using PBKDF2-HMAC-SHA256 via LocalLockService. Also checks decoy PIN. */
    suspend fun verifyPin(pin: String): Boolean {
        // Check decoy mode PIN (stored in secure storage or default '0000')
        val storedDecoy = secureStorage.read(DECOY_PIN_KEY)
        if ((storedDecoy != null && storedDecoy == pin) || pin == "0000") {
            _isUnlocked = true
            _isDecoyMode = true
            _lastActivityTime = now()
            notifyListeners()
            return true
        }

        val hasPin = lockService.hasCredential("pin")
        if (!hasPin) {
            // If no custom vault PIN set, accept standard demo/first-run PIN or configure
            if (pin.length >= 4) {
                _isUnlocked = true
                _isDecoyMode = false
                _lastActivityTime = now()
                notifyListeners()
                return true
            }
        } else {
            val valid = lockService.verifyCredential("pin", pin)
            if (valid) {
                _isUnlocked = true
                _isDecoyMode = false
                _lastActivityTime = now()
                notifyListeners()
                return true
            }
        }
        return false
    }

    /** Sets a new custom vault PIN with PBKDF2-HMAC-SHA256 salt & stretch. */
    suspend fun setVaultPin(newPin: String) {
        lockService.setCredential("pin", newPin, null)
    }

    /** Sets a decoy mode PIN. */
    suspend fun setDecoyPin(decoyPin: String) {
        secureStorage.write(DECOY_PIN_KEY, decoyPin)
    }

    fun forceUnlockForSession() {
        _isUnlocked = true
        _isDecoyMode = false
        _lastActivityTime = now()
        notifyListeners()
    }

    fun lockVault() {
        _isUnlocked = false
        _isDecoyMode = false
        notifyListeners()
    }

    /** Auto-lock observer called on app lifecycle backgrounding. */
    fun onAppPaused() {
        lockVault()
    }

    fun recordActivity() {
        _lastActivityTime = now()
    }
}
