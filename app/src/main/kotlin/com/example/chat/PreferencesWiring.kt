package com.example.chat

import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.core.preferences.LocalPreferencesStorage
import com.example.chat.core.preferences.PreferencesAuthSource
import com.example.chat.core.preferences.PreferencesBlobStore
import com.example.chat.core.preferences.RemoteSettingsRow
import com.example.chat.core.preferences.RemoteSettingsStore
import com.example.chat.data.auth.AuthSessionManager
import com.example.chat.data.realtime.RealtimePreferences
import com.example.chat.data.remote.PostgrestQueryClient
import com.example.chat.data.services.PresenceService
import com.example.chat.domain.models.PrivacyPreferences
import com.example.chat.domain.models.PrivacyPreferencesSnapshot
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** PreferencesBlobStore over the Android SharedPreferences-backed storage. */
class AndroidPreferencesBlobStore(
    private val storage: LocalPreferencesStorage,
) : PreferencesBlobStore {
    override suspend fun load(userId: String?) = storage.loadPreferences(userId)
    override suspend fun hasScoped(userId: String) = storage.hasScopedPreferences(userId)
    override suspend fun save(userId: String?, data: Map<String, Any?>) {
        // A false commit must surface as a failure so the ordered writer retries and
        // never marks the snapshot as written.
        if (!storage.savePreferences(data, userId)) throw java.io.IOException("preferences save failed")
    }
    override suspend fun clearGlobal() = storage.clearGlobalPreferences()
    override suspend fun storedUserId(): String? = storage.getStoredUserId()
    override suspend fun setStoredUserId(userId: String?) {
        if (userId == null) storage.clearStoredUserId() else storage.setStoredUserId(userId)
    }
}

/** user_feature_settings seam over PostgREST, mirroring the Dart controller's
 *  client.from('user_feature_settings') calls. */
class SupabaseRemoteSettingsStore(
    private val queryClient: PostgrestQueryClient,
) : RemoteSettingsStore {
    override suspend fun fetch(userId: String): RemoteSettingsRow? {
        val rows = queryClient.from("user_feature_settings")
            .select("settings, updated_at")
            .eq("user_id", userId)
            .limit(1)
            .execute()
        val row = rows.firstOrNull() ?: return null
        @Suppress("UNCHECKED_CAST")
        return RemoteSettingsRow(
            settings = row["settings"] as? Map<String, Any?>,
            updatedAt = row["updated_at"]?.toString(),
        )
    }

    override suspend fun push(userId: String, snapshot: Map<String, Any?>) {
        queryClient.from("user_feature_settings").upsert(
            mapOf(
                "user_id" to userId,
                "settings" to snapshot,
                "updated_at" to OffsetDateTime.now(java.time.ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            ),
            onConflict = "user_id",
        )
    }
}

/** Auth seam over AuthSessionManager's listener surface; null = signed out,
 *  mirroring supabase_flutter's AuthState.session?.user.id. */
class SessionAuthSource(
    private val sessionManager: AuthSessionManager,
) : PreferencesAuthSource {
    private val wrappers = java.util.concurrent.ConcurrentHashMap<
        (String?) -> Unit,
        (com.example.chat.data.auth.AuthChangeEvent, AuthSessionManager.ActiveSession?) -> Unit,
    >()

    override fun currentUserId(): String? = sessionManager.currentUserId()
    override fun addListener(listener: (String?) -> Unit) {
        val wrapper: (com.example.chat.data.auth.AuthChangeEvent, AuthSessionManager.ActiveSession?) -> Unit =
            { _, session -> listener(session?.user?.id) }
        wrappers[listener] = wrapper
        sessionManager.addAuthStateListener(wrapper)
    }
    override fun removeListener(listener: (String?) -> Unit) {
        wrappers.remove(listener)?.let { sessionManager.removeAuthStateListener(it) }
    }
}

/** One sign-in plane's identity surface (GoTrue or REST). Extracted so the
 *  composite below is unit-testable without Android auth stacks. */
interface AuthPlaneSource {
    fun currentUserId(): String?
    /** Fires whenever the plane's identity MAY have changed. */
    fun addPlaneListener(listener: () -> Unit)
    fun removePlaneListener(listener: () -> Unit)
}

/** Auth seam that follows the LIVE sign-in plane: the GoTrue session when one
 *  exists, otherwise the custom-API (REST) session. Before this, REST-only
 *  accounts scoped to null and shared the device-global preference bucket, so
 *  their settings were never restored on re-login and mixed with whatever the
 *  previous session wrote. Bandi approved per-account buckets on 2026-11-04
 *  (WhatsApp, from_handle +919951672843: "3.yes"). The controller's existing
 *  one-time adoption migrates the legacy global blob into the first scoped
 *  bucket it sees; later accounts start clean, and every account's bucket is
 *  kept across sign-out and account switches. */
class SessionAndApiAuthSource(
    private val goTrue: AuthPlaneSource,
    private val rest: AuthPlaneSource,
) : PreferencesAuthSource {
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(String?) -> Unit>()
    private val forwardLock = Any()
    private var lastForwarded: String? = null
    private var attached = false

    /** GoTrue wins when present (existing SessionAuthSource semantics); the
     *  REST identity is the fallback so REST-only accounts get a real scope. */
    private fun effectiveUserId(): String? =
        goTrue.currentUserId() ?: rest.currentUserId()

    /** Parity with SessionAuthSource: forward every GoTrue session event (the
     *  controller treats a same-id event as a remote reconcile). */
    private val goTrueListener: () -> Unit = {
        val id = effectiveUserId()
        synchronized(forwardLock) { lastForwarded = id }
        listeners.forEach { runCatching { it(id) } }
    }

    /** The REST scope listener also fires on tenant/routing changes that keep
     *  the same user; only an effective-id change is a preference event. */
    private val restListener: () -> Unit = {
        val id = effectiveUserId()
        val changed = synchronized(forwardLock) {
            if (id == lastForwarded) false else { lastForwarded = id; true }
        }
        if (changed) listeners.forEach { runCatching { it(id) } }
    }

    override fun currentUserId(): String? = effectiveUserId()

    override fun addListener(listener: (String?) -> Unit) {
        listeners.addIfAbsent(listener)
        if (!attached) {
            // ApiBackendService.addAuthScopeListener fires immediately with the
            // current scope; pre-seed the dedupe so that immediate fire is a
            // no-op and the controller's own initLoad remains the initial read.
            synchronized(forwardLock) { lastForwarded = effectiveUserId() }
            attached = true
            goTrue.addPlaneListener(goTrueListener)
            rest.addPlaneListener(restListener)
        }
    }

    override fun removeListener(listener: (String?) -> Unit) {
        listeners.remove(listener)
        if (listeners.isEmpty() && attached) {
            attached = false
            goTrue.removePlaneListener(goTrueListener)
            rest.removePlaneListener(restListener)
        }
    }
}

/** GoTrue plane over AuthSessionManager's (event, session) listener surface. */
class GoTrueAuthPlane(
    private val sessionManager: AuthSessionManager,
) : AuthPlaneSource {
    private val wrappers = java.util.concurrent.ConcurrentHashMap<
        () -> Unit,
        (com.example.chat.data.auth.AuthChangeEvent, AuthSessionManager.ActiveSession?) -> Unit,
    >()
    override fun currentUserId(): String? = sessionManager.currentUserId()
    override fun addPlaneListener(listener: () -> Unit) {
        val wrapper: (com.example.chat.data.auth.AuthChangeEvent, AuthSessionManager.ActiveSession?) -> Unit =
            { _, _ -> listener() }
        wrappers[listener] = wrapper
        sessionManager.addAuthStateListener(wrapper)
    }
    override fun removePlaneListener(listener: () -> Unit) {
        wrappers.remove(listener)?.let { sessionManager.removeAuthStateListener(it) }
    }
}

/** REST plane over ApiBackendService's auth-scope listener surface. */
class ApiBackendAuthPlane(
    private val apiBackend: com.example.chat.data.api.ApiBackendService,
) : AuthPlaneSource {
    private val wrappers = java.util.concurrent.ConcurrentHashMap<
        () -> Unit,
        (com.example.chat.data.api.ApiAccountScope) -> Unit,
    >()
    override fun currentUserId(): String? = apiBackend.authSnapshot().userId
    override fun addPlaneListener(listener: () -> Unit) {
        val wrapper: (com.example.chat.data.api.ApiAccountScope) -> Unit = { listener() }
        wrappers[listener] = wrapper
        apiBackend.addAuthScopeListener(wrapper)
    }
    override fun removePlaneListener(listener: () -> Unit) {
        wrappers.remove(listener)?.let { apiBackend.removeAuthScopeListener(it) }
    }
}

/** RealtimePreferences backed by the live controller, replacing the
 *  temporary Dart-defaults shim. */
class ControllerRealtimePreferences(
    private val controller: ChatyPreferencesController,
) : RealtimePreferences {
    override val enableGlobalNotifications: Boolean get() = controller.notification.enableGlobalNotifications
    override val showMessagePreview: Boolean get() = controller.notification.showMessagePreview
    override val notifyContactOnline: Boolean get() = controller.notification.notifyContactOnline
    override val notifyTypingStarted: Boolean get() = controller.notification.notifyTypingStarted
    override val notifyMessageDeleted: Boolean get() = controller.notification.notifyMessageDeleted
    override val messageRevokeAlert: Boolean get() = controller.privacy.messageRevokeAlert
    override val freezeLastSeen: Boolean get() = controller.privacy.freezeLastSeen
    override val hideLastSeenAudience: String get() = controller.privacy.hideLastSeenAudience
    override val hideOnlineAudience: String get() = controller.privacy.hideOnlineAudience
    override val recordingIndicators: Boolean get() = controller.privacy.recordingIndicators
    override val ghostMode: Boolean get() = controller.home.ghostMode
    override fun gbBool(key: String, fallback: Boolean): Boolean = controller.gbBool(key, fallback)
    override fun gbColorArgb(key: String): Long? = controller.gbColor(key)
}

/** Mappers from the typed privacy model to the service seams. */
fun PrivacyPreferences.toPresenceSnapshot(): PresenceService.PrivacySnapshot =
    PresenceService.PrivacySnapshot(
        freezeLastSeen = freezeLastSeen,
        hideLastSeenAudience = hideLastSeenAudience,
        hideOnlineAudience = hideOnlineAudience,
    )

fun PrivacyPreferences.toPublicationSnapshot(): PrivacyPreferencesSnapshot =
    PrivacyPreferencesSnapshot(
        readReceipts = readReceipts,
        typingIndicators = typingIndicators,
        recordingIndicators = recordingIndicators,
        hideViewStatus = hideViewStatus,
        freezeLastSeen = freezeLastSeen,
        hideLastSeenAudience = hideLastSeenAudience,
        hideOnlineAudience = hideOnlineAudience,
    )
