package com.example.chat

import com.example.chat.data.api.ApiAuthClient
import com.example.chat.data.api.ApiAuthResult
import com.example.chat.data.api.ApiAuthSession
import com.example.chat.data.api.ApiHttpTransport
import com.example.chat.data.api.ApiSessionStore
import com.example.chat.data.api.ApiUserProfile
import com.example.chat.data.api.apiUserFromJson
import com.example.chat.data.api.apiUserToJson
import com.example.chat.ui.auth.OtpVerificationLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Email OTP flow: verification send/confirm, reset-with-code, session flag,
 *  and the OTP screen's pure logic (research-driven auth flow). */
class EmailOtpFlowTest {

    private data class Call(val method: String, val url: String, val headers: Map<String, String>, val body: String?)

    private class FakeTransport(var handler: (Call) -> Pair<Int, String>) : ApiHttpTransport {
        val calls = mutableListOf<Call>()
        override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
            val call = Call(method, url, headers, body)
            calls.add(call)
            return handler(call)
        }
    }

    private class MemoryStore : ApiSessionStore {
        var token: String? = null
        var user: String? = null
        var refresh: String? = null
        override fun loadToken() = token
        override fun loadUserJson() = user
        override fun save(token: String, userJson: String) { this.token = token; this.user = userJson }
        override fun loadRefreshToken() = refresh
        override fun saveRefreshToken(token: String?) { refresh = token }
        override fun clear() { token = null; user = null; refresh = null }
    }

    private fun client(transport: FakeTransport) =
        ApiAuthClient({ "http://10.0.2.2:8000" }, { "chat-application" }, transport)

    @Test
    fun verificationSendPostsEmailAndPassesGenericMessageThrough() {
        val t = FakeTransport { call ->
            assertEquals("http://10.0.2.2:8000/auth/email/verification/send", call.url)
            assertEquals("chat-application", call.headers["x-tenant-id"])
            assertTrue(call.body!!.contains("\"email\":\"maya@example.com\""))
            200 to """{"data":{"message":"If an account exists with this email, a verification code has been sent."}}"""
        }
        val msg = client(t).sendEmailVerification(" maya@example.com ")
        assertEquals("If an account exists with this email, a verification code has been sent.", msg)
        assertEquals(1, t.calls.size)
    }

    @Test
    fun verificationSendFallsBackToApiV1On404() {
        val t = FakeTransport { call ->
            if (call.url == "http://10.0.2.2:8000/auth/email/verification/send") 404 to ""
            else 200 to """{"message":"ok"}"""
        }
        val msg = client(t).sendEmailVerification("maya@example.com")
        assertEquals("ok", msg)
        assertEquals("http://10.0.2.2:8000/api/v1/auth/email/verification/send", t.calls[1].url)
    }

    @Test
    fun confirmPostsEmailAndCode() {
        val t = FakeTransport { call ->
            assertEquals("http://10.0.2.2:8000/auth/email/verification/confirm", call.url)
            assertTrue(call.body!!.contains("\"email\":\"maya@example.com\""))
            assertTrue(call.body.contains("\"code\":\"123456\""))
            200 to """{"data":{"message":"Email verified."}}"""
        }
        assertEquals("Email verified.", client(t).confirmEmailVerification("maya@example.com", "123456"))
    }

    @Test
    fun confirmSurfacesAttemptsRemainingFromServer() {
        val t = FakeTransport { 400 to """{"detail":"Incorrect code. 3 attempts remaining."}""" }
        try {
            client(t).confirmEmailVerification("maya@example.com", "000000")
            fail("expected failure")
        } catch (e: Exception) {
            assertEquals("Incorrect code. 3 attempts remaining.", e.message)
        }
    }

    @Test
    fun resendCooldownSurfacesServer429Message() {
        val t = FakeTransport { 429 to """{"detail":"Please wait 60 seconds before requesting a new code."}""" }
        try {
            client(t).sendEmailVerification("maya@example.com")
            fail("expected failure")
        } catch (e: Exception) {
            assertEquals("Please wait 60 seconds before requesting a new code.", e.message)
        }
    }

    @Test
    fun resetPasswordPostsAllThreeFields() {
        val t = FakeTransport { call ->
            assertEquals("http://10.0.2.2:8000/auth/reset-password", call.url)
            assertTrue(call.body!!.contains("\"email\":\"maya@example.com\""))
            assertTrue(call.body.contains("\"code\":\"123456\""))
            assertTrue(call.body.contains("\"new_password\":\"s3cret!\""))
            200 to """{"data":{"message":"Password updated."}}"""
        }
        assertEquals("Password updated.", client(t).resetPasswordWithCode("maya@example.com", "123456", "s3cret!"))
    }

    @Test
    fun resetPasswordSurfacesExpiredCodeMessage() {
        val t = FakeTransport { 400 to """{"detail":"Code expired. Request a new one."}""" }
        try {
            client(t).resetPasswordWithCode("maya@example.com", "123456", "pw")
            fail("expected failure")
        } catch (e: Exception) {
            assertEquals("Code expired. Request a new one.", e.message)
        }
    }

    @Test
    fun transportFailureMapsToUnableToReachServer() {
        val t = FakeTransport { throw java.io.IOException("boom") }
        try {
            client(t).sendEmailVerification("maya@example.com")
            fail("expected failure")
        } catch (e: Exception) {
            assertEquals("Unable to reach server. Please check your internet connection.", e.message)
        }
    }

    @Test
    fun authResponseParsesEmailVerifiedFlag() {
        val t = FakeTransport {
            200 to """{"data":{"token":"${jwt()}","user":{"id":"u1","username":"maya","email_verified":false}}}"""
        }
        val result = client(t).parseAuthResponse(200, """{"data":{"token":"${jwt()}","user":{"id":"u1","username":"maya","email_verified":false}}}""")
        assertFalse(result.user.emailVerified)
    }

    @Test
    fun authResponseDefaultsEmailVerifiedTrueWhenAbsent() {
        val result = client(FakeTransport { 200 to "" }).parseAuthResponse(
            200, """{"data":{"token":"${jwt()}","user":{"id":"u1","username":"maya"}}}""",
        )
        assertTrue(result.user.emailVerified)
    }

    @Test
    fun sessionJsonRoundTripsEmailVerified() {
        val user = ApiUserProfile(
            id = "u1", username = "maya", displayName = "Maya", avatarInitials = "M",
            emailVerified = false,
        )
        assertFalse(apiUserFromJson(apiUserToJson(user)).emailVerified)
        // Older stored sessions without the flag read as verified (no false banner).
        val legacy = """{"id":"u1","username":"maya","displayName":"Maya","avatarInitials":"M"}"""
        assertTrue(apiUserFromJson(legacy).emailVerified)
    }

    @Test
    fun markEmailVerifiedFlipsMemoryAndStore() {
        val store = MemoryStore()
        val session = ApiAuthSession(store)
        val user = ApiUserProfile(
            id = "u1", username = "maya", displayName = "Maya", avatarInitials = "M",
            emailVerified = false,
        )
        session.adopt(ApiAuthResult("token-1", user))
        assertEquals(false, session.currentUser!!.emailVerified)
        session.markEmailVerified()
        assertEquals(true, session.currentUser!!.emailVerified)
        assertTrue(apiUserFromJson(store.user!!).emailVerified)
        // Idempotent.
        session.markEmailVerified()
        assertEquals(true, session.currentUser!!.emailVerified)
    }

    @Test
    fun markEmailVerifiedWithoutSessionIsANoOp() {
        ApiAuthSession(MemoryStore()).markEmailVerified()
    }

    @Test
    fun otpSanitizeKeepsDigitsOnlyAndCapsLength() {
        assertEquals("123456", OtpVerificationLogic.sanitizeCode("123 456"))
        assertEquals("123456", OtpVerificationLogic.sanitizeCode("1234567890"))
        assertEquals("12", OtpVerificationLogic.sanitizeCode("a1b2"))
        assertEquals("", OtpVerificationLogic.sanitizeCode(""))
    }

    @Test
    fun otpCompletenessRequiresSixDigits() {
        assertTrue(OtpVerificationLogic.isComplete("123456"))
        assertFalse(OtpVerificationLogic.isComplete("12345"))
        assertFalse(OtpVerificationLogic.isComplete("12345a"))
    }

    @Test
    fun otpMaskEmailKeepsFirstCharAndDomain() {
        assertEquals("b****@gmail.com", OtpVerificationLogic.maskEmail("bandi@gmail.com"))
        assertEquals("a****@x.co", OtpVerificationLogic.maskEmail("a@x.co"))
        assertEquals("no-at-sign", OtpVerificationLogic.maskEmail("no-at-sign"))
        assertEquals("", OtpVerificationLogic.maskEmail(""))
    }

    @Test
    fun otpDisplayErrorStripsExceptionPrefix() {
        assertEquals("Incorrect code.", OtpVerificationLogic.displayError(Exception("Exception: Incorrect code.")))
        assertEquals("Incorrect code.", OtpVerificationLogic.displayError(Exception("Incorrect code.")))
    }

    private fun jwt(): String {
        fun b64(s: String) = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())
        return "${b64("{\"alg\":\"none\"}")}.${b64("{\"uid\":\"u1\"}")}.sig"
    }
}
