package com.example.chat.data.services

import com.example.chat.core.util.parseIsoToInstant
import com.example.chat.data.messaging.SupabaseRpc
import com.example.chat.data.remote.PostgrestQueryClient
import com.example.chat.domain.models.ContactConnectionStatus
import com.example.chat.domain.models.ContactPrivacyOverride
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
 * Exact port of lib/data/services/contact_relationship_service.dart:
 * per-contact privacy overrides, block/unblock, connection status, and the
 * linked-device registry (installation device id, register, list, revoke).
 * Platform is always "Android" in the Kotlin port (Dart's _platformLabel
 * switch has exactly one mobile target here).
 */
class ContactRelationshipService(
    private val queryClient: PostgrestQueryClient,
    private val rpc: SupabaseRpc,
    private val currentUserId: () -> String?,
    private val secureStore: SecureValueStore,
) {
    private var cachedDeviceId: String? = null
    private val deviceIdMutex = Mutex()

    private fun userId(): String = currentUserId()
        ?: throw IllegalStateException("Authentication required.")

    /** Exact port of privacyFor (default override when no row). */
    suspend fun privacyFor(targetUserId: String): ContactPrivacyOverride {
        val rows = queryClient.from("contact_privacy_overrides")
            .select()
            .eq("owner_user_id", userId())
            .eq("target_user_id", targetUserId)
            .limit(1)
            .execute()
        val row = rows.firstOrNull()
            ?: return ContactPrivacyOverride(
                ownerUserId = userId(),
                targetUserId = targetUserId,
            )
        return ContactPrivacyOverride.fromDatabaseMap(row)
    }

    /** Exact port of savePrivacy (owner guard + upsert on the pair key). */
    suspend fun savePrivacy(value: ContactPrivacyOverride) {
        if (value.ownerUserId != userId()) {
            throw IllegalStateException("Cannot change another user's privacy settings.")
        }
        queryClient.from("contact_privacy_overrides")
            .upsert(value.toDatabaseMap(), onConflict = "owner_user_id,target_user_id")
    }

    /** Exact port of isBlocked. */
    suspend fun isBlocked(targetUserId: String): Boolean {
        val rows = queryClient.from("blocked_users")
            .select("blocked_id")
            .eq("blocker_id", userId())
            .eq("blocked_id", targetUserId)
            .limit(1)
            .execute()
        return rows.isNotEmpty()
    }

    /** Exact port of setBlocked (block_user / unblock_user RPCs). */
    suspend fun setBlocked(targetUserId: String, blocked: Boolean) {
        rpc.rpc(
            if (blocked) "block_user" else "unblock_user",
            mapOf("p_user_id" to targetUserId),
        )
    }

    /** Exact port of connectionStatus (fail-open default on error/empty). */
    suspend fun connectionStatus(otherUserId: String): ContactConnectionStatus {
        val raw = try {
            rpc.rpc(
                "get_contact_connection_status",
                mapOf("p_other_user_id" to otherUserId),
            )
        } catch (error: Throwable) {
            return ContactConnectionStatus(myAccepted = true, otherAccepted = true)
        }
        val rows = raw as? List<*>
        if (!rows.isNullOrEmpty() && rows.first() is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            return ContactConnectionStatus.fromDatabaseMap(
                (rows.first() as Map<String, Any?>),
            )
        }
        if (raw is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            return ContactConnectionStatus.fromDatabaseMap(raw as Map<String, Any?>)
        }
        return ContactConnectionStatus(myAccepted = true, otherAccepted = true)
    }

    /** Exact port of acceptConnection. */
    suspend fun acceptConnection(otherUserId: String): String {
        val raw = rpc.rpc(
            "accept_contact_connection",
            mapOf("p_other_user_id" to otherUserId),
        )
        val id = raw?.toString() ?: ""
        if (id.isEmpty()) {
            throw IllegalStateException("Unable to create or accept the contact connection.")
        }
        return id
    }

    /** Exact port of currentDeviceId/_performCurrentDeviceId (cached,
     *  single-flight via mutex, persisted in secure storage). */
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

    /** Exact port of registerCurrentDevice (platform label "Android"). */
    suspend fun registerCurrentDevice() {
        val deviceId = currentDeviceId()
        queryClient.from("linked_devices").upsert(
            mapOf(
                "user_id" to userId(),
                "device_id" to deviceId,
                "device_name" to "Android Chaty device",
                "platform" to "Android",
                "location" to "",
                "last_active_at" to Instant.now().toString(),
                "revoked_at" to null,
            ),
            onConflict = "user_id,device_id",
        )
    }

    /** Exact port of linkedDevices (active rows, newest activity first). */
    suspend fun linkedDevices(): List<LinkedDevice> {
        val current = currentDeviceId()
        val rows = queryClient.from("linked_devices")
            .select()
            .eq("user_id", userId())
            .isNull("revoked_at")
            .order("last_active_at", ascending = false)
            .execute()
        return rows.map { row ->
            val id = row["device_id"]?.toString() ?: ""
            LinkedDevice(
                id = id,
                deviceName = row["device_name"]?.toString() ?: "Chaty device",
                platform = row["platform"]?.toString() ?: "Unknown",
                location = row["location"]?.toString() ?: "",
                lastActiveAt = parseIsoToInstant(row["last_active_at"]?.toString())
                    ?: Instant.now(),
                isCurrentDevice = id == current,
            )
        }
    }

    /** Exact port of revokeDevice, including the zero-affected-rows guard:
     *  never report revocation as successful unless this authenticated
     *  user's active device row changed. */
    suspend fun revokeDevice(deviceId: String) {
        val current = currentDeviceId()
        if (deviceId == current) {
            throw IllegalStateException(
                "The current device cannot revoke itself from this screen.",
            )
        }
        val revoked = queryClient.from("linked_devices")
            .eq("user_id", userId())
            .eq("device_id", deviceId)
            .isNull("revoked_at")
            .updateReturning(mapOf("revoked_at" to Instant.now().toString()))
        if (revoked.isEmpty()) {
            throw IllegalStateException(
                "This linked device is no longer active or cannot be revoked.",
            )
        }
    }

    companion object {
        const val DEVICE_ID_STORAGE_KEY = "chaty.installation_device_id.v1"
    }
}
