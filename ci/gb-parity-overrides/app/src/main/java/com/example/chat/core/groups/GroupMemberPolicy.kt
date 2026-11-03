package com.example.chat.core.groups

import com.example.chat.domain.models.UserProfile
import java.util.Locale

/** Only actual conversation membership is evidence. A cached profile is not membership. */
object GroupMemberPolicy {
    fun members(ids: List<String>, profiles: List<UserProfile>, adminIds: List<String>, currentUserId: String, query: String): List<UserProfile> {
        val allowed = ids.filter { it.isNotBlank() }.toSet()
        val q = query.trim().lowercase(Locale.ROOT).removePrefix("@")
        return profiles.distinctBy { it.id }.filter { it.id in allowed }
            .filter { q.isBlank() || it.displayName.lowercase(Locale.ROOT).contains(q) || it.username.lowercase(Locale.ROOT).contains(q) }
            .sortedWith(compareBy<UserProfile> { if (it.id == currentUserId) 0 else if (it.id in adminIds) 1 else 2 }
                .thenBy { it.displayName.lowercase(Locale.ROOT) }.thenBy { it.id })
    }
    fun knownCount(ids: List<String>) = ids.filter { it.isNotBlank() }.toSet().size
}
