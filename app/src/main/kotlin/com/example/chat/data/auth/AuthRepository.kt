package com.example.chat.data.auth

import com.example.chat.core.validators.ChatyValidators
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
            sessions.save(
                SupabaseAuthClient.AuthSession(
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken,
                    expiresIn = response.expiresIn ?: 3600,
                    user = authUser,
                ),
                authUser.id,
            )
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
            sessions.save(session, user.id)
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
        // PostgREST RPC is_username_available(p_username) - lands with the
        // data layer; username uniqueness is also enforced server-side.
        return true
    }

    suspend fun signOut() {
        sessions.clear()
    }
}
