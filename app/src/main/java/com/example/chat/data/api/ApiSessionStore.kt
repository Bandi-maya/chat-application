package com.example.chat.data.api

import org.json.JSONObject

/** Persistence seam for the REST auth session (SharedPreferences on Android,
 *  keys api_hub_token / api_hub_user exactly as the Dart side). */
interface ApiSessionStore {
    fun loadToken(): String?
    fun loadUserJson(): String?
    fun save(token: String, userJson: String)
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
class ApiAuthSession(private val store: ApiSessionStore) {
    var token: String? = null
        private set
    var currentUser: ApiUserProfile? = null
        private set

    val isAuthenticated: Boolean get() = token != null && currentUser != null

    /** ApiBackendService.initialize's session restore. */
    fun restore() {
        val savedToken = store.loadToken() ?: return
        val savedUser = store.loadUserJson() ?: return
        token = savedToken
        var user = apiUserFromJson(savedUser)
        // Dart: recover an empty id from the JWT uid/sub claim.
        if (user.id.trim().isEmpty()) {
            try {
                val jwt = ApiAuthClient({ "" }, { "" }, ApiHttpTransport { _, _, _, _ -> 0 to "" })
                    .decodeJwt(savedToken)
                val uid = (if (jwt.has("uid")) jwt.get("uid") else jwt.optString("sub")).toString()
                if (uid.isNotEmpty()) user = user.copy(id = uid)
            } catch (_: Exception) {
            }
        }
        currentUser = user
    }

    fun adopt(result: ApiAuthResult) {
        token = result.token
        currentUser = result.user
        store.save(result.token, apiUserToJson(result.user))
    }

    /** Dart updateProfile local side effect: apply provided fields, derive
     *  initials from the effective display name's first char, persist. */
    fun updateProfileFields(
        displayName: String?,
        about: String?,
        username: String?,
        avatarUrl: String?,
    ): ApiUserProfile {
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
        currentUser = updated
        token?.let { store.save(it, apiUserToJson(updated)) }
        return updated
    }

    fun signOut() {
        token = null
        currentUser = null
        store.clear()
    }
}
