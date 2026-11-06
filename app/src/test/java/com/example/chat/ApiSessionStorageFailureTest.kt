package com.example.chat

import com.example.chat.data.api.ApiAuthResult
import com.example.chat.data.api.ApiAuthSession
import com.example.chat.data.api.ApiSessionStorageException
import com.example.chat.data.api.ApiSessionStorageFailure
import com.example.chat.data.api.ApiSessionStore
import com.example.chat.data.api.ApiUserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bandi 2026-11-04: a secure-storage failure must block login (fail closed,
 *  no temporary session) and surface WHY it failed for customer care.
 *  Publisher review: user-visible/exported copy must come from allowlisted
 *  reason codes only; raw store exceptions stay private. */
class ApiSessionStorageFailureTest {
    private class FailingStore(private val boom: () -> Unit) : ApiSessionStore {
        override fun loadToken(): String? = null
        override fun loadUserJson(): String? = null
        override fun save(token: String, userJson: String) = boom()
        override fun clear() {}
    }

    private fun user(id: String = "u1") = ApiUserProfile(
        id = id, username = "user", displayName = "User", avatarInitials = "U",
    )

    private fun adoptFailing(boom: () -> Unit): ApiSessionStorageException {
        val session = ApiAuthSession(FailingStore(boom))
        try {
            session.adopt(ApiAuthResult("token-1", user(), refreshToken = "r1"))
            error("expected ApiSessionStorageException")
        } catch (e: ApiSessionStorageException) {
            return e
        }
    }

    @Test
    fun commitFailureMapsToWriteRejected() {
        val e = adoptFailing { throw IllegalStateException("Attempted read or write of encrypted storage failed") }
        assertEquals(ApiSessionStorageFailure.WRITE_REJECTED, e.failure)
        // Existing catch sites typed on IllegalStateException keep working.
        assertTrue(e is IllegalStateException)
    }

    @Test
    fun keystoreFailureMapsToSecureStorageUnavailable() {
        val e = adoptFailing { throw java.security.GeneralSecurityException("keystore operation failed") }
        assertEquals(ApiSessionStorageFailure.SECURE_STORAGE_UNAVAILABLE, e.failure)
    }

    @Test
    fun unknownFailureMapsToUnknown() {
        val e = adoptFailing { throw RuntimeException("something odd") }
        assertEquals(ApiSessionStorageFailure.UNKNOWN, e.failure)
    }

    @Test
    fun userFacingCopyIsFixedAndNeverContainsRawSecretBearingText() {
        val secret = "master-key-alias chaty_masterkey /data/user/0/com.logybyte.chat/shared_prefs"
        val e = adoptFailing { throw IllegalStateException("Attempted read or write of $secret failed") }
        assertEquals(
            "Chaty could not save your sign-in securely on this device, so sign-in was " +
                "stopped to protect your account. Reason: " +
                ApiSessionStorageFailure.WRITE_REJECTED.userSafeReason + ".",
            e.message,
        )
        assertTrue(!e.message!!.contains(secret))
        assertTrue(!e.message!!.contains("chaty_masterkey"))
        assertTrue(!e.message!!.contains("token-1"))
        // The raw cause survives for private diagnostics only.
        assertTrue(e.cause?.message?.contains(secret) == true)
    }

    @Test
    fun failurePublishesNoIdentityAndFiresNoTransition() {
        var transitions = 0
        val session = ApiAuthSession(FailingStore { throw IllegalStateException("Attempted read or write failed") }) { transitions++ }
        try {
            session.adopt(ApiAuthResult("token-1", user()))
            error("expected ApiSessionStorageException")
        } catch (_: ApiSessionStorageException) {
        }
        assertNull(session.token)
        assertNull(session.currentUser)
        assertTrue(!session.isAuthenticated)
        assertEquals(0, transitions)
    }
}
