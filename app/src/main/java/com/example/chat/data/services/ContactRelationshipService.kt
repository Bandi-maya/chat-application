package com.example.chat.data.services

import com.example.chat.data.api.ApiContactsClient
import com.example.chat.domain.models.ContactConnectionStatus
import com.example.chat.domain.models.ContactPrivacyOverride
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Ported LinkedDevice (other_models.dart). */
data class LinkedDevice(
    val id: String,
    val deviceName: String,
    val platform: String,
    val location: String,
    val lastActiveAt: Instant,
    val isCurrentDevice: Boolean,
)

/** Secure key-value seam for the installation device id
 *  (FlutterSecureStorage on the Dart side; SecureKeyValueStoreImpl here). */
interface SecureValueStore {
    suspend fun read(key: String): String?
    suspend fun write(key: String, value: String)
}

/**
 * Contact relationships ride the Chaty backend contacts plane
 * (/contacts, /contacts/requests[+outgoing], /contacts/respond,
 * /block[s]). The retired Supabase contact_privacy_overrides and
 * linked_devices tables are gone:
 *  - Per-contact privacy overrides have no backend plane yet, so the
 *    entry point is hidden (ReleaseFeatures.CONTACT_PRIVACY_OVERRIDES)
 *    and these methods fail closed instead of pretending to persist.
 *  - Linked devices stay dormant behind ReleaseFeatures.LINKED_DEVICES;
 *    registration is a no-op rather than a write to a dead table.
 */
class ContactRelationshipService(
    private val contacts: () -> ApiContactsClient,
    private val currentUserId: () -> String?,
    private val secureStore: SecureValueStore,
) {
    private var cachedDeviceId: String? = null
    private val deviceIdMutex = Mutex()

    private fun userId(): String = currentUserId()
        ?: throw IllegalStateException("Authentication required.")

    /** No backend plane for per-contact overrides yet: return the
     *  nothing-hidden default. The screen that edits these is hidden, so
     *  this default is never user-editable state. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun privacyFor(targetUserId: String): ContactPrivacyOverride =
        ContactPrivacyOverride(ownerUserId = userId(), targetUserId = targetUserId)

    /** Fail closed: never pretend a privacy choice persisted when no
     *  server-side enforcement exists. */
    suspend fun savePrivacy(value: ContactPrivacyOverride) {
        throw IllegalStateException(
            "Individual privacy controls are not available on this server yet.",
        )
    }

    suspend fun isBlocked(targetUserId: String): Boolean = withContext(Dispatchers.IO) {
        contacts().blocks().any { it.userId == targetUserId }
    }

    /** Silent both ways, exactly like the backend /block semantics. */
    suspend fun setBlocked(targetUserId: String, blocked: Boolean) = withContext(Dispatchers.IO) {
        if (blocked) contacts().blockUser(targetUserId) else contacts().unblock(targetUserId)
    }

    /** Derive the connection state from the contacts plane. Errors
     *  propagate (callers keep their previous state); a pre-plane server
     *  (no outgoing route) reads as not-connected, never as accepted. */
    suspend fun connectionStatus(otherUserId: String): ContactConnectionStatus = withContext(Dispatchers.IO) {
        val plane = contacts()
        if (plane.contacts().any { it.userId == otherUserId }) {
            return@withContext ContactConnectionStatus(myAccepted = true, otherAccepted = true)
        }
        if (plane.inbox().any { it.from.userId == otherUserId }) {
            return@withContext ContactConnectionStatus(myAccepted = false, otherAccepted = true)
        }
        val outgoing = try {
            plane.outgoing()
        } catch (e: ApiContactsClient.PlaneUnavailable) {
            return@withContext ContactConnectionStatus()
        }
        if (outgoing.any { it.from.userId == otherUserId }) {
            return@withContext ContactConnectionStatus(myAccepted = true, otherAccepted = false)
        }
        ContactConnectionStatus()
    }

    /** Accept their pending incoming request. Throws when none exists -
     *  the old path created connections out of thin air; the backend
     *  contact model requires their request first. */
    suspend fun acceptConnection(otherUserId: String): String = withContext(Dispatchers.IO) {
        val plane = contacts()
        val incoming = plane.inbox().firstOrNull { it.from.userId == otherUserId }
            ?: throw IllegalStateException("Unable to create or accept the contact connection.")
        plane.respond(incoming.requestId, "accept")
        incoming.requestId
    }

    /** Cached, single-flight installation device id (local only). */
    suspend fun currentDeviceId(): String {
        cachedDeviceId?.takeIf { it.isNotEmpty() }?.let { return it }
        return deviceIdMutex.withLock {
            cachedDeviceId?.takeIf { it.isNotEmpty() }?.let { return@withLock it }
            var value = secureStore.read(DEVICE_ID_STORAGE_KEY)
            if (value.isNullOrEmpty()) {
                value = UUID.randomUUID().toString()
                secureStore.write(DEVICE_ID_STORAGE_KEY, value)
            }
            cachedDeviceId = value
            value
        }
    }

    /** Linked devices are dormant (ReleaseFeatures.LINKED_DEVICES = false):
     *  no registration write to any backend. */
    suspend fun registerCurrentDevice() {
        currentDeviceId() // keep the id warm for when the feature ships
    }

    /** Dormant feature: no rows exist server-side. */
    suspend fun linkedDevices(): List<LinkedDevice> = emptyList()

    /** Dormant feature: fail closed rather than pretend a revocation. */
    suspend fun revokeDevice(deviceId: String) {
        throw IllegalStateException("Linked devices are not available on this server yet.")
    }

    companion object {
        const val DEVICE_ID_STORAGE_KEY = "chaty.installation_device_id.v1"
    }
}
