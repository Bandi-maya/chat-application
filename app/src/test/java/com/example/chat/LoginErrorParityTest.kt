package com.example.chat

import com.example.chat.core.auth.friendlyLoginError
import com.example.chat.core.auth.friendlyOrgRegisterError
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

    @Test
    fun orgRegisterConnectionRefusedMapsToServerMessage() {
        assertEquals(
            "Unable to connect to the backend server. Please verify your connection.",
            friendlyOrgRegisterError(Exception("Connection refused")),
        )
        assertEquals(
            "Unable to connect to the backend server. Please verify your connection.",
            friendlyOrgRegisterError(Exception("SocketException: failed host lookup")),
        )
    }

    @Test
    fun orgRegisterTenantTakenMapsToWorkspaceMessage() {
        assertEquals(
            "This Workspace ID is already taken. Please choose a different Workspace ID.",
            friendlyOrgRegisterError(Exception("tenant id already exists")),
        )
    }

    @Test
    fun orgRegisterDomainMappedMapsToDomainMessage() {
        assertEquals(
            "This domain or workspace is already mapped to an existing organization.",
            friendlyOrgRegisterError(Exception("domain already mapped")),
        )
    }

    @Test
    fun orgRegisterUnknownErrorPassesThroughStripped() {
        assertEquals("something odd", friendlyOrgRegisterError(Exception("Exception: something odd")))
    }
}
