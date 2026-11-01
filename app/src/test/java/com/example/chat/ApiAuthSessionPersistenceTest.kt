package com.example.chat

import com.example.chat.data.api.ApiAuthResult
import com.example.chat.data.api.ApiAuthSession
import com.example.chat.data.api.ApiSessionStore
import com.example.chat.data.api.ApiUserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiAuthSessionPersistenceTest {
    private class MemoryStore : ApiSessionStore {
        var token: String? = null
        var user: String? = null
        var refresh: String? = null
        var clearCount = 0
        var failSave = false
        override fun loadToken() = token
        override fun loadUserJson() = user
        override fun save(token: String, userJson: String) {
            check(!failSave)
            this.token = token
            this.user = userJson
        }
        override fun loadRefreshToken() = refresh
        override fun saveRefreshToken(token: String?) { refresh = token }
        override fun clear() {
            token = null
            user = null
            refresh = null
            clearCount++
        }
    }

    private fun user(id: String = "u1") = ApiUserProfile(
        id = id, username = "user", displayName = "User", avatarInitials = "U",
    )

    @Test
    fun adoptionPersistsBeforePublishingIdentity() {
        val store = MemoryStore()
        val session = ApiAuthSession(store)
        session.adopt(ApiAuthResult("token-1", user(), refreshToken = "refresh-1"))
        assertEquals("token-1", store.token)
        assertEquals("u1", com.example.chat.data.api.apiUserFromJson(store.user!!).id)
        assertEquals("token-1", session.token)
        assertEquals("refresh-1", store.refresh)
    }

    @Test
    fun failedPersistenceDoesNotPublishNewIdentity() {
        val store = MemoryStore().apply { failSave = true }
        val session = ApiAuthSession(store)
        var failed = false
        try {
            session.adopt(ApiAuthResult("token-1", user()))
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertEquals(true, failed)
        assertNull(session.token)
        assertNull(session.currentUser)
    }

    @Test
    fun profileEditsPreserveRefreshToken() {
        val store = MemoryStore()
        val session = ApiAuthSession(store)
        session.adopt(ApiAuthResult("token-1", user(), refreshToken = "refresh-1"))
        session.updateProfileFields(
            displayName = "Updated User",
            about = null,
            username = null,
            avatarUrl = null,
        )
        assertEquals("refresh-1", store.refresh)
        assertEquals("Updated User", com.example.chat.data.api.apiUserFromJson(store.user!!).displayName)
    }

    @Test
    fun signedOutSessionDropsActiveIdentityAndPersistsClear() {
        val store = MemoryStore()
        val session = ApiAuthSession(store)
        session.adopt(ApiAuthResult("token-1", user()))
        session.signOut()
        assertNull(session.token)
        assertNull(session.currentUser)
        assertNull(store.token)
        assertEquals(1, store.clearCount)
    }
}
