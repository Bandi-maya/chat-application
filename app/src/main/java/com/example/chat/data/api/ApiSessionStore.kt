package com.example.chat.data.api

import org.json.JSONObject

/** Persistence seam for the REST auth session (SharedPreferences on Android,
 *  keys api_hub_token / api_hub_user exactly as the Dart side). */
interface ApiSessionStore {
    fun loadToken(): String?
    fun loadUserJson(): String?
    fun save(token: String, userJson: String)
    /** Atomic encrypted implementations persist the complete identity pair before publication. */
    fun saveSession(token: String, userJson: String, refreshToken: String?) {
        save(token, userJson)
        saveRefreshToken(refreshToken)
    }
    fun loadRefreshToken(): String? = null
    fun saveRefreshToken(token: String?) { }
    fun clear()
}

fun apiUserToJson(user: ApiUserProfile): String = JSONObject()
    .put("id", user.id)
    .put("username", user.username)
    .put("displayName", user.displayName)
    .put("avatarInitials", user.avatarInitials)
    .put("avatarColorHex", user.avatarColorHex)
    .put("about", user.about)
    .put("email", user.email)
    .put("phone", user.phone)
    .put("avatarUrl", user.avatarUrl ?: JSONObject.NULL)
    .toString()

fun apiUserFromJson(raw: String): ApiUserProfile {
    val obj = JSONObject(raw)
    return ApiUserProfile(
        id = obj.optString("id"),
        username = obj.optString("username"),
        displayName = obj.optString("displayName"),
        avatarInitials = obj.optString("avatarInitials"),
        avatarColorHex = obj.optString("avatarColorHex", "0xFF6366F1"),
        about = obj.optString("about"),
        email = obj.optString("email"),
        phone = obj.optString("phone"),
        avatarUrl = if (obj.isNull("avatarUrl")) null else obj.optString("avatarUrl"),
    )
}

/** Context-free holder mirroring ApiBackendService's session surface:
 *  token + current user, isAuthenticated = token != null && user != null. */
data class ApiAuthSnapshot(val user: ApiUserProfile?, val token: String?, val generation: Long)

class ApiAuthSession(private val store: ApiSessionStore, private val scopeLock: Any = Any(), private val onTransition: () -> Unit = {}) {
    @Volatile private var state = ApiAuthSnapshot(null, null, 0)
    fun snapshot(): ApiAuthSnapshot = state
    val token: String? get() = state.token
    val currentUser: ApiUserProfile? get() = state.user

    val isAuthenticated: Boolean get() = snapshot().let { it.token != null && it.user != null }

    /** ApiBackendService.initialize's session restore. */
    fun restore() = synchronized(scopeLock) {
        val savedToken = store.loadToken()
        val savedUser = store.loadUserJson()
        var user = if (!savedToken.isNullOrBlank() && !savedUser.isNullOrBlank()) {
            runCatching { apiUserFromJson(savedUser) }.getOrNull()
        } else null
        if (user != null && user.id.trim().isEmpty()) {
            try {
                val jwt = ApiAuthClient({ "" }, { "" }, ApiHttpTransport { _, _, _, _ -> 0 to "" }).decodeJwt(savedToken!!)
                val uid = (if (jwt.has("uid")) jwt.get("uid") else jwt.optString("sub")).toString()
                if (uid.isNotEmpty()) user = user.copy(id = uid)
            } catch (_: Exception) { }
        }
        // Mixed demo/real identities are quarantined in memory; keep persisted bytes intact.
        if (com.example.chat.data.demo.DemoMode.isMixedRestSession(savedToken, user?.id)) {
            state = ApiAuthSnapshot(null, null, state.generation + 1)
            onTransition()
            return@synchronized
        }
        if ((!savedToken.isNullOrBlank() || !savedUser.isNullOrBlank()) &&
            (user == null || user.id.isBlank() || savedToken.isNullOrBlank())
        ) {
            // A partial/corrupt identity pair must not remain eligible for a
            // future restore or block safe re-authentication indefinitely.
            store.clear()
            user = null
        }
        state = ApiAuthSnapshot(user, savedToken.takeIf { user != null }, state.generation + 1)
        onTransition()
    }

    fun adopt(result: ApiAuthResult) = synchronized(scopeLock) {
        // Commit the complete token/user/refresh-token tuple before publishing
        // the new identity to listeners or request owners.
        store.saveSession(result.token, apiUserToJson(result.user), result.refreshToken)
        state = ApiAuthSnapshot(result.user, result.token, state.generation + 1)
        onTransition()
    }

    /** Dart updateProfile local side effect: apply provided fields, derive
     *  initials from the effective display name's first char, persist. */
    fun updateProfileFields(
        displayName: String?,
        about: String?,
        username: String?,
        avatarUrl: String?,
    ): ApiUserProfile = synchronized(scopeLock) {
        val current = currentUser ?: throw Exception("Not authenticated")
        val effectiveName = if (!displayName.isNullOrBlank()) displayName.trim() else current.displayName
        val initials = if (effectiveName.isNotEmpty()) effectiveName.substring(0, 1).uppercase()
            else current.avatarInitials
        val updated = current.copy(
            displayName = effectiveName,
            username = if (!username.isNullOrBlank()) username.trim() else current.username,
            about = about?.trim() ?: current.about,
            avatarUrl = avatarUrl?.trim() ?: current.avatarUrl,
            avatarInitials = initials,
        )
        val currentToken = token
        if (!currentToken.isNullOrBlank()) {
            // Profile updates must keep the current refresh token; otherwise
            // the next access-token expiry unexpectedly signs the user out.
            val refreshToken = store.loadRefreshToken()
            store.saveSession(currentToken, apiUserToJson(updated), refreshToken)
        }
        state = state.copy(user = updated)
        updated
    }

    /**
     * Drops the active in-memory identity without touching persisted credentials.
     * Used only when rejecting an ephemeral/demo session that must not revoke or
     * erase a separate real account's stored session.
     */
    fun clearInMemoryOnly() = synchronized(scopeLock) {
        if (state.token == null && state.user == null) return@synchronized
        state = ApiAuthSnapshot(null, null, state.generation + 1)
        onTransition()
    }

    fun signOut() = synchronized(scopeLock) {
        state = ApiAuthSnapshot(null, null, state.generation + 1)
        onTransition()
        store.clear()
    }
}
