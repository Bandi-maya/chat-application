package com.example.chat

import com.example.chat.data.api.RegistrationPhonePolicy
import com.example.chat.data.api.ApiUserProfile
import com.example.chat.data.api.apiUserFromJson
import com.example.chat.data.api.apiUserToJson
import org.junit.Assert.*
import org.junit.Test

class RegistrationPhonePolicyTest {
    @Test fun internationalContactClaimNormalizesButDoesNotVerify() {
        assertEquals("+441234567890", RegistrationPhonePolicy.normalize("+44 (1234) 567-890"))
        listOf("", "1234567890", "+0123456789", "+12345", "+123abc45678", "+１２３４５６７８９").forEach {
            assertNull(RegistrationPhonePolicy.normalize(it))
        }
    }
    @Test fun unverifiedIsDefaultAndStoredStatusRoundTrips() {
        val user = ApiUserProfile("id", "name", "Name", "N", phone = "+441234567890")
        assertFalse(apiUserFromJson(apiUserToJson(user)).phoneVerified)
        assertTrue(apiUserFromJson(apiUserToJson(user.copy(phoneVerified = true))).phoneVerified)
        assertFalse(apiUserFromJson("""{"id":"legacy","phone":"+441234567890"}""").phoneVerified)
    }
}
