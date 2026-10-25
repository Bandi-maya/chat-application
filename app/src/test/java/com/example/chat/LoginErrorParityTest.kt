package com.example.chat

import com.example.chat.core.auth.friendlyLoginError
import org.junit.Assert.assertEquals
import org.junit.Test

/** friendlyLoginError is an exact port of _LoginScreenState._friendlyError. */
class LoginErrorParityTest {

    private fun err(msg: String) = friendlyLoginError(Exception(msg))

    @Test
    fun connectionFailuresMapToServerMessage() {
        val expected = "Unable to connect to server. Please ensure the backend is running."
        assertEquals(expected, err("SocketException: Connection refused"))
        assertEquals(expected, err("ClientException: Connection refused"))
        assertEquals(expected, err("failed host lookup: api.example.com"))
    }

    @Test
    fun tenantFailuresMapToWorkspaceMessage() {
        val expected = "Workspace / Organization not found. Please check your Workspace ID or register a new organization."
        assertEquals(expected, err("tenant not found"))
        assertEquals(expected, err("missing X-Tenant-ID header"))
    }

    @Test
    fun credentialFailuresMapToIncorrectMessage() {
        val expected = "Incorrect email/username or password."
        assertEquals(expected, err("Invalid login credentials"))
        assertEquals(expected, err("HTTP 401"))
        assertEquals(expected, err("Incorrect password"))
    }

    @Test
    fun unconfirmedEmailMapsToConfirmMessage() {
        assertEquals("Confirm your email first, then sign in.", err("Email not confirmed"))
    }

    @Test
    fun unknownErrorsPassThroughVerbatim() {
        assertEquals("something entirely unexpected", err("something entirely unexpected"))
    }
}
