package com.example.chat.core.preferences

import com.example.chat.domain.models.ConversationPreferences
import com.example.chat.domain.models.HomePreferences
import com.example.chat.domain.models.MessageAutomationPreferences
import com.example.chat.domain.models.messageAutomationPreferencesFromMap
import com.example.chat.domain.models.toMap
import com.example.chat.domain.models.NavigationEffectPreferences
import com.example.chat.domain.models.NotificationPreferences
import com.example.chat.domain.models.NotificationToastPreferences
import com.example.chat.domain.models.PrivacyPreferences
import com.example.chat.domain.models.SecurityPreferences
import com.example.chat.domain.models.StatusPreferences
import com.example.chat.domain.models.UniversalPreferences
import java.time.OffsetDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class PreferenceHistoryEntry(
    val key: String,
    val title: String,
    val previousValue: Any?,
    val newValue: Any?,
    val timestampEpochMs: Long,
)

/** Local blob persistence seam (LocalPreferencesStorage on Android). */
interface PreferencesBlobStore {
    suspend fun load(userId: String?): Map<String, Any?>
    suspend fun hasScoped(userId: String): Boolean
    suspend fun save(userId: String?, data: Map<String, Any?>)
    suspend fun clearGlobal()
    suspend fun storedUserId(): String?
    suspend fun setStoredUserId(userId: String?)
}

/** user_feature_settings row seam. */
data class RemoteSettingsRow(val settings: Map<String, Any?>?, val updatedAt: String?)

interface RemoteSettingsStore {
    /** Null = no row. Throws on transport failure (caller treats local as
     *  authoritative, mirroring the Dart catch-all). */
    suspend fun fetch(userId: String): RemoteSettingsRow?
    suspend fun push(userId: String, snapshot: Map<String, Any?>)
}

/** Platform secure-storage seam (LocalLockService on Android). */
interface LockCredentialStore {
    suspend fun hasCredential(method: String): Boolean
    suspend fun setCredential(method: String, secret: String, pinLength: Int? = null)
}

/** Auth seam: current user id plus change notifications (null = signed out). */
interface PreferencesAuthSource {
    fun currentUserId(): String?
    fun addListener(listener: (String?) -> Unit)
    fun removeListener(listener: (String?) -> Unit)
}

/**
 * Exact port of lib/ui/core/controllers/preferences_controller.dart
 * (ChatyPreferencesController). All storage/remote/lock/auth touchpoints are
 * injected seams so the full behavior is JVM-testable; the Android adapters
 * are thin delegations wired in ServiceLocator.
 */
class ChatyPreferencesController(
    private val store: PreferencesBlobStore,
    private val remote: RemoteSettingsStore?,
    private val lockStore: LockCredentialStore,
    private val auth: PreferencesAuthSource,
    private val scope: CoroutineScope,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val remoteSyncDebounceMs: Long = 650L,
) {
    var privacy = PrivacyPreferences(); private set
    var security = SecurityPreferences(); private set
    var home = HomePreferences(); private set
    var conversation = ConversationPreferences(); private set
    var notification = NotificationPreferences(); private set
    var automation = MessageAutomationPreferences(); private set
    var effects = NavigationEffectPreferences(); private set
    var universal = UniversalPreferences(); private set
    var status = StatusPreferences(); private set
    var toasts = NotificationToastPreferences(); private set
    var gbFeatures: Map<String, Any?> = GbFeatureCatalog.defaults; private set

    private val starredFavorites = mutableSetOf<String>()
    private val historyEntries = mutableListOf<PreferenceHistoryEntry>()
    private var scopeUserId: String? = null
    private var remoteSyncDebounce: Job? = null
    private var disposed = false
    var preferencesReady = false; private set

    // MUST stay above init{}: init launches initLoad and registers the auth listener, which may
    // run inline (Unconfined/Main.immediate) before later field initialisers would execute.
    private val localWriter = OrderedBlobWriter { uid, data -> store.save(uid, data) }
    private val saveTracker = PreferencesSaveTracker { now() }
    private val loadGeneration = java.util.concurrent.atomic.AtomicLong(0)

    /** Observable local-save outcome; UI shows an inline row only when Failed. */
    val saveState: kotlinx.coroutines.flow.StateFlow<SaveState> get() = saveTracker.state

    private val listeners = mutableListOf<() -> Unit>()

    private val authListener: (String?) -> Unit = { newUserId -> handleAuthStateChange(newUserId) }

    init {
        scope.launch { initLoad() }
        auth.addListener(authListener)
    }

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyListeners() {
        if (disposed) return
        for (l in listeners.toList()) l()
    }

    fun favorites(): Set<String> = starredFavorites.toSet()
    fun history(): List<PreferenceHistoryEntry> = historyEntries.toList()

    @Suppress("UNCHECKED_CAST")
    fun <T> gbValue(key: String): T? {
        val value = gbFeatures[key]
        return try { value as T? } catch (_: ClassCastException) { null }
    }

    fun gbBool(key: String, fallback: Boolean = false): Boolean {
        return when (val value = gbFeatures[key]) {
            is Boolean -> value
            is String -> value.lowercase() == "true"
            is Number -> value.toDouble() != 0.0
            else -> fallback
        }
    }

    fun gbString(key: String, fallback: String = ""): String =
        gbFeatures[key]?.toString() ?: fallback

    fun gbDouble(key: String, fallback: Double = 0.0): Double {
        val value = gbFeatures[key]
        if (value is Number) return value.toDouble()
        return value?.toString()?.toDoubleOrNull() ?: fallback
    }

    fun gbInt(key: String, fallback: Int = 0): Int {
        val value = gbFeatures[key]
        if (value is Int) return value
        if (value is Number) return value.toDouble().let { Math.round(it).toInt() }
        return value?.toString()?.toIntOrNull() ?: fallback
    }

    /** Dart gbColor: non-zero int, or '#'/0x string; 6-digit hex gains full
     *  alpha. Returns ARGB long. */
    fun gbColor(key: String): Long? {
        when (val raw = gbFeatures[key]) {
            is Number -> if (raw.toLong() != 0L) return raw.toLong()
            is String -> {
                val normalized = raw.replaceFirst("#", "").replaceFirst("0x", "")
                val value = normalized.toLongOrNull(16)
                if (value != null) {
                    return if (normalized.length <= 6) 0xFF000000L or value else value
                }
            }
        }
        return null
    }

    // ── init + auth ───────────────────────────────────────────────────────

    private suspend fun initLoad() {
        scopeUserId = auth.currentUserId()
        saveTracker.resetScope(scopeUserId)
        loadForUser(scopeUserId, loadGeneration.incrementAndGet())
    }

    /** Reacts to sign-in, sign-out, and account switches. On any change of
     *  the authenticated user the previous account's in-memory state is
     *  dropped before the new scope is loaded, so preferences can never
     *  bleed across accounts on a shared device. */
    private fun handleAuthStateChange(newUserId: String?) {
        if (newUserId == scopeUserId) {
            // Same session (e.g. a token refresh) — just reconcile.
            if (newUserId != null) scope.launch { syncFromRemote() }
            return
        }
        scopeUserId = newUserId
        saveTracker.resetScope(newUserId)
        val gen = loadGeneration.incrementAndGet()
        preferencesReady = false
        resetInMemory()
        notifyListeners()
        scope.launch { loadForUser(newUserId, gen) }
    }

    /** Loads, migrates, and applies the preference blob for [userId] (null
     *  when signed out), then reconciles with the backend. */
    private suspend fun loadForUser(userId: String?, gen: Long) {
        val raw = resolveLocalBlob(userId, gen)
        // Account switched (or a newer load started) while we awaited storage: drop this result
        // before it can touch in-memory state, the store or readiness.
        if (gen != loadGeneration.get()) return

        val migration = PreferencesMigrator.migrate(raw)
        if (raw.isNotEmpty()) applyMap(migration.data)
        if (migration.migratedSecrets) migrateLegacySecrets(migration, gen)
        if (gen != loadGeneration.get()) return

        // Rewrite the cleaned/upgraded blob so stripped plaintext secrets and
        // stale schema versions never survive to a second load.
        if (raw.isNotEmpty() &&
            (migration.migratedSecrets || migration.fromVersion < PreferenceKeys.currentSchemaVersion)
        ) {
            store.save(userId, snapshot())
        }

        if (scopeUserId == userId) preferencesReady = true
        notifyListeners()
        syncFromRemote()
    }

    /** Resolves the correct local blob for [userId], performing a one-time
     *  adoption of a pre-namespacing legacy global blob into the user's
     *  namespace when it is safe to do so. */
    private suspend fun resolveLocalBlob(userId: String?, gen: Long): Map<String, Any?> {
        fun stale() = gen != loadGeneration.get()
        if (userId.isNullOrEmpty()) {
            // Signed-out / device-global bucket.
            return store.load(null)
        }

        if (!store.hasScoped(userId)) {
            val legacy = store.load(null)
            // Generation re-checked immediately before every mutation below. This is a
            // check-then-act, not an atomic transaction: a switch landing between the
            // check and the store call can still slip through (see report).
            if (stale()) return emptyMap()
            if (legacy.isNotEmpty()) {
                // Only adopt the un-owned/legacy blob if it is not already
                // claimed by a different account; otherwise a second account
                // could inherit the first account's settings.
                val owner = store.storedUserId()
                if (stale()) return emptyMap()
                if (owner == null || owner == userId) {
                    store.save(userId, legacy)
                }
                if (stale()) return emptyMap()
                // Retire the un-namespaced blob regardless, so it can never
                // be adopted again by a different account.
                store.clearGlobal()
            }
        }

        if (stale()) return emptyMap()
        store.setStoredUserId(userId)
        return store.load(userId)
    }

    /** Moves any plaintext credentials found in a pre-v2 blob into platform
     *  secure storage. Fail-safe: an existing secure credential is never
     *  overwritten, and a legacy secret that no longer passes validation is
     *  dropped (the user simply re-sets their lock). */
    private suspend fun migrateLegacySecrets(migration: MigrationResult, gen: Long = loadGeneration.get()) {
        val methodByLabel = mapOf("PIN" to "pin", "Pattern" to "pattern", "Password" to "password")
        for ((label, secret) in migration.legacySecrets) {
            val method = methodByLabel[label] ?: continue
            if (gen != loadGeneration.get()) return
            try {
                if (lockStore.hasCredential(method)) continue
                // Re-check after the await, immediately before the mutation.
                if (gen != loadGeneration.get()) return
                lockStore.setCredential(
                    method,
                    secret,
                    pinLength = if (method == "pin") migration.legacyPinLength else null,
                )
            } catch (_: Exception) {
                // Invalid legacy secret; plaintext is already stripped, safe.
            }
        }
    }

    private fun resetInMemory() {
        privacy = PrivacyPreferences()
        security = SecurityPreferences()
        home = HomePreferences()
        conversation = ConversationPreferences()
        notification = NotificationPreferences()
        automation = MessageAutomationPreferences()
        effects = NavigationEffectPreferences()
        gbFeatures = GbFeatureCatalog.defaults
        starredFavorites.clear()
        historyEntries.clear()
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyMap(data: Map<String, Any?>) {
        fun apply(key: String, applyGroup: (Map<String, Any?>) -> Unit) {
            val value = data[key]
            if (value !is Map<*, *>) return
            try {
                applyGroup(value as Map<String, Any?>)
            } catch (_: Exception) {
                // A single corrupt group must not abort the whole load; leave
                // it at its current (default) value and continue.
            }
        }

        apply(PreferenceKeys.privacy) { privacy = PrivacyPreferences.fromMap(it) }
        apply(PreferenceKeys.security) { security = SecurityPreferences.fromMap(it) }
        apply(PreferenceKeys.home) { home = HomePreferences.fromMap(it) }
        apply(PreferenceKeys.conversation) { conversation = ConversationPreferences.fromMap(it) }
        apply(PreferenceKeys.notification) { notification = NotificationPreferences.fromMap(it) }
        apply(PreferenceKeys.automation) { automation = messageAutomationPreferencesFromMap(it) }
        apply(PreferenceKeys.effects) { effects = NavigationEffectPreferences.fromMap(it) }
        apply(PreferenceKeys.universal) { universal = UniversalPreferences.fromMap(it) }
        apply(PreferenceKeys.status) { status = StatusPreferences.fromMap(it) }
        apply(PreferenceKeys.toasts) { toasts = NotificationToastPreferences.fromMap(it) }

        val gb = data[PreferenceKeys.gbFeatures]
        if (gb is Map<*, *>) {
            gbFeatures = GbFeatureCatalog.defaults + (gb as Map<String, Any?>)
        }
        // One-time heal: the header/list visibility keys (PicProf, NameProf, …)
        // were exposed as Feature-Center toggles BEFORE they had consumers, so
        // devices can carry stored `false` values that hide the contact name /
        // avatar / call buttons / presence line even though the intended
        // default is visible. Clear them once so they return to unset=visible.
        // The marker persists inside this user's blob, so deliberate later
        // choices are never touched again.
        val visibilityHealMarker = "chaty_visibility_heal_v1"
        if (gbFeatures[visibilityHealMarker] != true) {
            val visibilityKeys = listOf(
                "PicProf", "NameProf", "Conv_call_btn",
                "statuschat", "onlinechat", "onlineDotchat",
            )
            var healedAny = false
            val mutable = gbFeatures.toMutableMap()
            for (key in visibilityKeys) {
                if (mutable.containsKey(key)) {
                    mutable.remove(key)
                    healedAny = true
                }
            }
            mutable[visibilityHealMarker] = true
            gbFeatures = mutable
            if (healedAny) persist()
        }
        val favorites = data[PreferenceKeys.favorites]
        if (favorites is List<*>) {
            starredFavorites.clear()
            starredFavorites.addAll(favorites.map { it.toString() })
        }
    }

    private var lastLocalMutation: Long = now()

    fun snapshot(): Map<String, Any?> = mapOf(
        PreferenceKeys.schemaVersion to PreferenceKeys.currentSchemaVersion,
        "local_updated_at" to lastLocalMutation,
        PreferenceKeys.privacy to privacy.toMap(),
        PreferenceKeys.security to security.toMap(),
        PreferenceKeys.home to home.toMap(),
        PreferenceKeys.conversation to conversation.toMap(),
        PreferenceKeys.notification to notification.toMap(),
        PreferenceKeys.automation to automation.toMap(),
        PreferenceKeys.effects to effects.toMap(),
        PreferenceKeys.universal to universal.toMap(),
        PreferenceKeys.status to status.toMap(),
        PreferenceKeys.toasts to toasts.toMap(),
        PreferenceKeys.gbFeatures to gbFeatures,
        PreferenceKeys.favorites to starredFavorites.toList(),
    )

    /** Re-saves the current settings (used by an inline Retry action). */
    fun retrySave() { if (preferencesReady) persist() }

    private fun persist() {
        lastLocalMutation = now()
        val snap = snapshot()
        // Capture the account at call time (not when the coroutine runs) and order
        // writes so an older snapshot can never overwrite a newer one.
        val uid = scopeUserId
        val seq = localWriter.nextSeq()
        val gen = saveTracker.currentGeneration()
        scope.launch {
            val wrote = localWriter.write(seq, uid, snap)
            saveTracker.onWriteFinished(gen, uid, wrote, localWriter.hasUnsavedFailure(uid))
        }
        remoteSyncDebounce?.cancel()
        if (remote != null) {
            remoteSyncDebounce = scope.launch {
                delay(remoteSyncDebounceMs)
                pushRemote(snap)
            }
        }
    }

    suspend fun syncFromRemote() {
        val remoteStore = remote ?: return
        val userId = auth.currentUserId() ?: return
        try {
            val row = remoteStore.fetch(userId)
            if (row == null || row.settings !is Map<*, *>) {
                pushRemote(snapshot())
                return
            }
            @Suppress("UNCHECKED_CAST")
            val remoteRaw = row.settings as Map<String, Any?>

            // Timestamp reconciliation: if local mutation is strictly newer
            // than remote updated_at, push local state to remote rather than
            // overwriting fresh local choices with stale remote data.
            val remoteUpdatedAtStr = row.updatedAt ?: remoteRaw["local_updated_at"]?.toString()
            if (remoteUpdatedAtStr != null) {
                val remoteTime = parseRemoteTime(remoteUpdatedAtStr)
                if (remoteTime > 0 && lastLocalMutation > remoteTime + 2000) {
                    // Local changes are strictly newer; push to server.
                    pushRemote(snapshot())
                    return
                }
            }

            val needsPurge = PreferencesMigrator.rawHasLegacySecrets(remoteRaw)
            val migration = PreferencesMigrator.migrate(remoteRaw)
            applyMap(migration.data)
            if (migration.migratedSecrets) migrateLegacySecrets(migration)
            run {
                // Same enqueue-time capture as persist(); tracker ignores it if the account changed.
                val gen = saveTracker.currentGeneration()
                val uid = scopeUserId
                val wrote = localWriter.write(localWriter.nextSeq(), uid, snapshot())
                saveTracker.onWriteFinished(gen, uid, wrote, localWriter.hasUnsavedFailure(uid))
            }
            // Overwrite any cloud-stored plaintext or stale-schema blob with
            // the clean one.
            if (needsPurge || migration.fromVersion < PreferenceKeys.currentSchemaVersion) {
                pushRemote(snapshot())
            }
            notifyListeners()
        } catch (_: Exception) {
            // Local preferences remain authoritative when the network is
            // unavailable.
        }
    }

    private suspend fun pushRemote(snap: Map<String, Any?>) {
        val remoteStore = remote ?: return
        val userId = auth.currentUserId() ?: return
        try {
            remoteStore.push(userId, snap)
        } catch (_: Exception) {
            // Keep the local copy; the next mutation retries the account sync.
        }
    }

    companion object {
        /** Dart: DateTime.tryParse(s)?.millisecondsSinceEpoch ?? int.tryParse(s) ?? 0. */
        fun parseRemoteTime(raw: String): Long {
            try {
                return OffsetDateTime.parse(raw).toInstant().toEpochMilli()
            } catch (_: Exception) {
            }
            try {
                return java.time.Instant.parse(raw).toEpochMilli()
            } catch (_: Exception) {
            }
            return raw.toLongOrNull() ?: 0L
        }
    }

    // ── GB semantic aliases (exact port of _applyGbSemanticAlias) ─────────

    private fun applyGbSemanticAlias(key: String, value: Any?) {
        val flag = when (value) {
            is Boolean -> value
            is Number -> value.toDouble() != 0.0
            else -> value?.toString()?.lowercase() == "true"
        }
        val text = value?.toString() ?: ""
        when (key) {
            "yoHideSeen" -> privacy = privacy.copy(readReceipts = !flag)
            "anti_vw_once" -> privacy = privacy.copy(antiViewOnce = flag)
            "yoDisableFwd" -> privacy = privacy.copy(disableForwardedLabel = flag)
            "yoCallsPrivacy" -> privacy = privacy.copy(whoCanCallMe = text)
            "Saleh_HidePrivacy" -> privacy = privacy.copy(hidePrivacyOption = flag)
            "yoHideStatViewV2" -> privacy = privacy.copy(hideViewStatus = flag)
            "yoAntiRevokeStatus" -> privacy = privacy.copy(antiDeleteStatus = flag)
            "AntiRevokeStatusNotif" -> privacy = privacy.copy(statusRevocationAlert = flag)
            "key_chat_editview" -> privacy = privacy.copy(showEditedMessage = flag)
            "yoAntiRevoke" -> privacy = privacy.copy(antiDeleteMessages = flag)
            "AntiRevokeMsgNotif" -> privacy = privacy.copy(messageRevokeAlert = flag)
            "yoBlueOnReply" -> privacy = privacy.copy(showBlueTicksAfterReply = flag)
            "home_stories_key" -> home = home.copy(enableStoriesStrip = flag)
            "home_stories_style" -> home = home.copy(storiesStyle = HomePresetNormalizer.storiesStyle(text))
            "enable_grp_separationV2" -> home = home.copy(separateChatsAndGroups = flag)
            "my_name" -> home = home.copy(myNameOverride = text)
            "yo_want_ghostmode" -> {
                home = home.copy(ghostMode = flag)
                if (flag) {
                    privacy = privacy.copy(
                        freezeLastSeen = true,
                        readReceipts = false,
                        typingIndicators = false,
                        recordingIndicators = false,
                        hideViewStatus = true,
                    )
                }
            }
            "yo_want_airplanemode" -> home = home.copy(airplaneModeSimulator = flag)
            "yo_want_toolbar_cam" -> home = home.copy(showCameraIcon = flag)
            "ui_home_styleV3" -> home = home.copy(homeStyle = HomePresetNormalizer.homeStyle(text))
            "bubble_style" -> conversation = conversation.copy(bubbleStyle = text)
            "tick_style" -> conversation = conversation.copy(tickStyle = text)
            "abu_saleh_quickcontact" -> conversation = conversation.copy(
                enableQuickContactSidebar = text != "Off",
                sidebarPosition = if (text == "Left") "Left" else "Right",
            )
            "tap_effect_enabled" -> effects = effects.copy(enableClickParticles = flag)
            "tap_emoji" -> effects = effects.copy(clickParticleSymbol = text)
            "fall_effect_enabled" -> effects = effects.copy(enableFallingParticles = flag)
            "fall_emoji" -> effects = effects.copy(fallingParticleObject = text)
            "Pop_Heds" -> notification = notification.copy(enableGlobalNotifications = flag)
            "abu_saleh_toast_online" -> notification = notification.copy(notifyContactOnline = flag)
            "abu_saleh_toast_status" -> notification = notification.copy(notifyStatusViewed = flag)
            "abu_saleh_toast_typing" -> notification = notification.copy(notifyTypingStarted = flag)
        }
    }

    // ── updates (exact port of the update* family) ────────────────────────

    fun updateGbFeature(key: String, value: Any?, logTitle: String? = null) {
        val previous = gbFeatures[key]
        gbFeatures = gbFeatures + (key to value)
        applyGbSemanticAlias(key, value)
        logHistory(
            "gb:" + key,
            logTitle ?: (GbFeatureCatalog.byKey(key)?.title ?: key),
            previous,
            value,
        )
        persist()
        notifyListeners()
    }

    fun updateGbFeatures(values: Map<String, Any?>, logTitle: String = "GB feature bundle") {
        val previous = gbFeatures.toMap()
        gbFeatures = gbFeatures + values
        for ((k, v) in values) applyGbSemanticAlias(k, v)
        logHistory("gb:bundle", logTitle, previous, values)
        persist()
        notifyListeners()
    }

    fun updatePrivacy(newPrefs: PrivacyPreferences, logTitle: String? = null, prevVal: Any? = null, newVal: Any? = null) {
        privacy = newPrefs
        if (logTitle != null) logHistory("privacy", logTitle, prevVal, newVal)
        persist(); notifyListeners()
    }

    fun updateSecurity(newPrefs: SecurityPreferences, logTitle: String? = null, prevVal: Any? = null, newVal: Any? = null) {
        security = newPrefs
        if (logTitle != null) logHistory("security", logTitle, prevVal, newVal)
        persist(); notifyListeners()
    }

    fun updateHome(newPrefs: HomePreferences, logTitle: String? = null, prevVal: Any? = null, newVal: Any? = null) {
        home = newPrefs
        if (logTitle != null) logHistory("home", logTitle, prevVal, newVal)
        persist(); notifyListeners()
    }

    fun updateConversation(newPrefs: ConversationPreferences, logTitle: String? = null, prevVal: Any? = null, newVal: Any? = null) {
        conversation = newPrefs
        if (logTitle != null) logHistory("conversation", logTitle, prevVal, newVal)
        persist(); notifyListeners()
    }

    fun updateNotification(newPrefs: NotificationPreferences, logTitle: String? = null, prevVal: Any? = null, newVal: Any? = null) {
        notification = newPrefs
        if (logTitle != null) logHistory("notification", logTitle, prevVal, newVal)
        persist(); notifyListeners()
    }

    fun updateAutomation(newPrefs: MessageAutomationPreferences, logTitle: String? = null, prevVal: Any? = null, newVal: Any? = null) {
        automation = newPrefs
        if (logTitle != null) logHistory("automation", logTitle, prevVal, newVal)
        persist(); notifyListeners()
    }

    fun updateUniversal(newPrefs: UniversalPreferences, logTitle: String? = null, prevVal: Any? = null, newVal: Any? = null) {
        universal = newPrefs
        if (logTitle != null) logHistory("universal", logTitle, prevVal, newVal)
        persist(); notifyListeners()
    }

    fun updateStatus(newPrefs: StatusPreferences, logTitle: String? = null, prevVal: Any? = null, newVal: Any? = null) {
        status = newPrefs
        if (logTitle != null) logHistory("status", logTitle, prevVal, newVal)
        persist(); notifyListeners()
    }

    fun updateToasts(newPrefs: NotificationToastPreferences, logTitle: String? = null, prevVal: Any? = null, newVal: Any? = null) {
        toasts = newPrefs
        if (logTitle != null) logHistory("toasts", logTitle, prevVal, newVal)
        persist(); notifyListeners()
    }

    fun updateEffects(newPrefs: NavigationEffectPreferences, logTitle: String? = null, prevVal: Any? = null, newVal: Any? = null) {
        effects = newPrefs
        if (logTitle != null) logHistory("effects", logTitle, prevVal, newVal)
        persist(); notifyListeners()
    }

    // ── favorites ─────────────────────────────────────────────────────────

    fun toggleFavorite(settingKey: String) {
        if (!starredFavorites.remove(settingKey)) starredFavorites.add(settingKey)
        persist()
        notifyListeners()
    }

    fun isFavorite(settingKey: String): Boolean = starredFavorites.contains(settingKey)

    // ── conversation locks (exact port) ───────────────────────────────────

    fun isConversationLocked(conversationId: String): Boolean =
        security.lockedConversationIds.contains(conversationId)

    fun isConversationHidden(conversationId: String): Boolean =
        security.hiddenConversationIds.contains(conversationId)

    fun isConversationProtected(conversationId: String): Boolean =
        isConversationLocked(conversationId) || isConversationHidden(conversationId)

    fun toggleLockConversation(conversationId: String, lock: Boolean? = null) {
        val list = security.lockedConversationIds.toMutableList()
        val shouldLock = lock ?: !list.contains(conversationId)
        if (shouldLock) {
            if (!list.contains(conversationId)) list.add(conversationId)
        } else {
            list.remove(conversationId)
        }
        updateSecurity(
            security.copy(lockedConversationIds = list),
            logTitle = if (shouldLock) "Lock Chat" else "Unlock Chat",
        )
    }

    fun toggleHideConversation(conversationId: String, hide: Boolean? = null) {
        val list = security.hiddenConversationIds.toMutableList()
        val lockedList = security.lockedConversationIds.toMutableList()
        val shouldHide = hide ?: !list.contains(conversationId)
        if (shouldHide) {
            if (!list.contains(conversationId)) list.add(conversationId)
            // Hiding a chat also marks it locked.
            if (!lockedList.contains(conversationId)) lockedList.add(conversationId)
        } else {
            list.remove(conversationId)
        }
        updateSecurity(
            security.copy(hiddenConversationIds = list, lockedConversationIds = lockedList),
            logTitle = if (shouldHide) "Hide Locked Chat" else "Unhide Chat",
        )
    }

    fun unlockConversationCompletely(conversationId: String) {
        val hiddenList = security.hiddenConversationIds.toMutableList().also { it.remove(conversationId) }
        val lockedList = security.lockedConversationIds.toMutableList().also { it.remove(conversationId) }
        updateSecurity(
            security.copy(hiddenConversationIds = hiddenList, lockedConversationIds = lockedList),
            logTitle = "Unlock Chat Permanently",
        )
    }

    // ── history ───────────────────────────────────────────────────────────

    fun clearPreferenceHistory() {
        historyEntries.clear()
        notifyListeners()
    }

    private fun logHistory(key: String, title: String, prevVal: Any?, newVal: Any?) {
        historyEntries.add(
            0,
            PreferenceHistoryEntry(
                key = key,
                title = title,
                previousValue = prevVal,
                newValue = newVal,
                timestampEpochMs = now(),
            ),
        )
        if (historyEntries.size > 50) historyEntries.removeAt(historyEntries.size - 1)
    }

    // ── resets (exact port) ───────────────────────────────────────────────

    fun resetPrivacy() { privacy = PrivacyPreferences(); persist(); notifyListeners() }
    fun resetHome() { home = HomePreferences(); persist(); notifyListeners() }
    fun resetConversation() { conversation = ConversationPreferences(); persist(); notifyListeners() }
    fun resetNotifications() { notification = NotificationPreferences(); persist(); notifyListeners() }
    fun resetEffects() { effects = NavigationEffectPreferences(); persist(); notifyListeners() }
    fun resetGbFeatures() { gbFeatures = GbFeatureCatalog.defaults; persist(); notifyListeners() }

    fun resetAll() {
        privacy = PrivacyPreferences()
        security = SecurityPreferences()
        home = HomePreferences()
        conversation = ConversationPreferences()
        notification = NotificationPreferences()
        automation = MessageAutomationPreferences()
        effects = NavigationEffectPreferences()
        gbFeatures = GbFeatureCatalog.defaults
        starredFavorites.clear()
        historyEntries.clear()
        persist()
        notifyListeners()
    }

    fun dispose() {
        disposed = true
        remoteSyncDebounce?.cancel()
        auth.removeListener(authListener)
    }
}
