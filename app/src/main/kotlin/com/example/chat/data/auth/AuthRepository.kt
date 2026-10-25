package com.example.chat.data.auth

import com.example.chat.core.validators.ChatyValidators
import com.example.chat.data.messaging.SupabaseRpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Port of the auth methods in lib/data/services/backend_service.dart.
 * Error strings and validation order mirror the Flutter source exactly.
 */
class AuthRepository(
    private val auth: SupabaseAuthClient = SupabaseAuthClient(),
    private val sessions: SessionStore,
    private val rpc: SupabaseRpc? = null,
    /** When supplied by the service locator, session adoption and sign-out
     *  route through it so auth state events fire and auto-refresh is
     *  scheduled (mirrors supabase_flutter's client-side session owner). */
    private val sessionManager: AuthSessionManager? = null,
) {
    data class UserProfile(
        val id: String,
        val displayName: String,
        val username: String,
        val avatarInitials: String,
        val avatarColorHex: String,
        val about: String,
        val email: String,
        val phone: String,
    )

    private fun initials(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return "CU"
        if (words.size == 1) {
            return words.first().substring(0, if (words.first().length >= 2) 2 else 1).uppercase()
        }
        return "${words.first()[0]}${words.last()[0]}".uppercase()
    }

    suspend fun registerUser(
        displayName: String,
        username: String,
        password: String,
        email: String = "",
        phone: String = "",
        about: String = "Hey there! I am using Chaty.",
    ): UserProfile = withContext(Dispatchers.IO) {
        ChatyValidators.validateUsername(username)?.let { throw Exception(it) }
        ChatyValidators.validatePassword(password)?.let { throw Exception(it) }
        if (displayName.trim().length < 2) throw Exception("Display name is required.")
        if (email.trim().isEmpty() || !email.contains("@")) {
            throw Exception("A valid email is required for account verification.")
        }
        if (!isUsernameAvailable(username)) {
            throw Exception("That username is already taken.")
        }

        val initials = initials(displayName)
        val color = "0xFF6366F1"
        val metadata = buildJsonObject {
            put("display_name", displayName.trim())
            put("username", ChatyValidators.normalizeUsername(username))
            put("about", about.trim())
            put("phone", phone.trim())
            put("avatar_initials", initials)
            put("avatar_color_hex", color)
        }
        val response = auth.signUp(email.trim().lowercase(), password, metadata)
        val authUser = response.user ?: throw Exception("Unable to create the account.")

        if (response.accessToken != null && response.refreshToken != null) {
            val session = SupabaseAuthClient.AuthSession(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                expiresIn = response.expiresIn ?: 3600,
                user = authUser,
            )
            if (sessionManager != null) {
                sessionManager.adopt(session, authUser)
            } else {
                sessions.save(session, authUser.id)
            }
        }
        UserProfile(
            id = authUser.id,
            displayName = displayName.trim(),
            username = ChatyValidators.normalizeUsername(username),
            avatarInitials = initials,
            avatarColorHex = color,
            about = about.trim(),
            email = authUser.email ?: email.trim(),
            phone = authUser.phone ?: phone.trim(),
        )
    }

    suspend fun login(identifier: String, password: String): UserProfile =
        withContext(Dispatchers.IO) {
            val value = identifier.trim()
            if (!value.contains("@")) {
                throw Exception(
                    "Sign in with your registered email address. Username discovery remains available after login.",
                )
            }
            val session = auth.signInWithPassword(value.lowercase(), password)
            val user = session.user ?: throw Exception("Unable to establish a secure session.")
            if (sessionManager != null) {
                sessionManager.adopt(session, user)
            } else {
                sessions.save(session, user.id)
            }
            val metadata = user.userMetadata
            UserProfile(
                id = user.id,
                displayName = metadata?.get("display_name")?.toString()?.trim('"')
                    ?: user.email ?: "",
                username = metadata?.get("username")?.toString()?.trim('"') ?: "",
                avatarInitials = metadata?.get("avatar_initials")?.toString()?.trim('"')
                    ?: initials(user.email ?: "CU"),
                avatarColorHex = metadata?.get("avatar_color_hex")?.toString()?.trim('"')
                    ?: "0xFF6366F1",
                about = metadata?.get("about")?.toString()?.trim('"') ?: "",
                email = user.email ?: "",
                phone = user.phone ?: "",
            )
        }

    suspend fun loginWithSocial(provider: String): UserProfile {
        throw Exception(
            "$provider sign-in is not configured on the Chaty Supabase project yet. Use email/password sign-in.",
        )
    }

    suspend fun resetPassword(email: String) {
        auth.resetPasswordForEmail(email.trim().lowercase())
    }

    suspend fun isUsernameAvailable(username: String): Boolean {
        if (ChatyValidators.validateUsername(username) != null) return false
        // Mirrors backend_service.dart isUsernameAvailable: RPC
        // is_username_available(p_username) with the normalized username.
        val client = rpc
            ?: throw IllegalStateException("Username availability check is unavailable.")
        val raw = client.rpc(
            "is_username_available",
            mapOf("p_username" to ChatyValidators.normalizeUsername(username)),
        )
        return raw == true
    }

    suspend fun signOut() {
        if (sessionManager != null) {
            sessionManager.signOut()
        } else {
            sessions.clear()
        }
    }
}
