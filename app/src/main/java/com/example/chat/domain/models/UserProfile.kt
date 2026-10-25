package com.example.chat.domain.models

import com.example.chat.core.util.parseIsoOrNow
import java.time.Instant

enum class PresenceState { online, away, offline, typing }

data class UserProfile(
    val id: String,
    val displayName: String,
    val username: String,
    val avatarInitials: String,
    val avatarColorHex: String,
    val about: String,
    val presence: PresenceState = PresenceState.offline,
    val lastSeenAt: Instant,
    val isVerified: Boolean = false,
    val email: String = "",
    val phone: String = "",
    // Populated only when real device-to-device verification exists.
    val safetyNumber: String = "",
    /** Remote photo/banner URLs (public profile-media objects); null when the
     *  user has not uploaded media yet. */
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
) {
    /** Exact port of UserProfile.toJson (same keys, presence as enum name). */
    fun toJson(): Map<String, Any?> = mapOf(
        "id" to id,
        "displayName" to displayName,
        "username" to username,
        "avatarInitials" to avatarInitials,
        "avatarColorHex" to avatarColorHex,
        "about" to about,
        "presence" to presence.name,
        "lastSeenAt" to lastSeenAt.toString(),
        "isVerified" to isVerified,
        "email" to email,
        "phone" to phone,
        "safetyNumber" to safetyNumber,
        "avatarUrl" to avatarUrl,
        "bannerUrl" to bannerUrl,
    )

    companion object {
        /** Exact port of UserProfile.fromJson. */
        fun fromJson(json: Map<String, Any?>): UserProfile = UserProfile(
            id = json["id"]?.toString() ?: "",
            displayName = json["displayName"]?.toString() ?: "",
            username = json["username"]?.toString() ?: "",
            avatarInitials = json["avatarInitials"]?.toString() ?: "U",
            avatarColorHex = json["avatarColorHex"]?.toString() ?: "0xFF6366F1",
            about = json["about"]?.toString() ?: "",
            presence = PresenceState.entries.firstOrNull { it.name == json["presence"] }
                ?: PresenceState.offline,
            lastSeenAt = parseIsoOrNow(json["lastSeenAt"]?.toString()),
            isVerified = json["isVerified"] == true,
            email = json["email"]?.toString() ?: "",
            phone = json["phone"]?.toString() ?: "",
            safetyNumber = json["safetyNumber"]?.toString() ?: "",
            avatarUrl = json["avatarUrl"]?.toString(),
            bannerUrl = json["bannerUrl"]?.toString(),
        )
    }
}
