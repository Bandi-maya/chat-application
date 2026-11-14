package com.example.chat.data.api

import com.example.chat.data.e2ee.SecureKeyValueStore
import kotlinx.coroutines.test.runTest
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.util.encoders.Base64
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdentityManagerTest {

    private class InMemoryStore : SecureKeyValueStore {
        val data = mutableMapOf<String, String>()
        override suspend fun read(key: String): String? = data[key]
        override suspend fun write(key: String, value: String) { data[key] = value }
        override suspend fun delete(key: String) { data.remove(key) }
    }

    @Test
    fun identityIsCreatedOnceAndPersisted() = runTest {
        val store = InMemoryStore()
        val manager = DeviceIdentityManager(store)
        val first = manager.getOrCreate()
        val second = DeviceIdentityManager(store).getOrCreate()
        assertEquals(first.deviceId, second.deviceId)
        assertEquals(first.publicKeyBase64, second.publicKeyBase64)
    }

    @Test
    fun identityHeadersCarryDeviceIdAndPublicKey() = runTest {
        val headers = DeviceIdentityManager(InMemoryStore()).identityHeaders()
        assertTrue(headers["x-device-id"]!!.isNotBlank())
        assertTrue(headers["x-device-public-key"]!!.isNotBlank())
    }

    @Test
    fun proofIsAValidEd25519SignatureOverTheToken() = runTest {
        val manager = DeviceIdentityManager(InMemoryStore())
        val headers = manager.proofHeaders("refresh-token-value")
        val signature = Base64.decode(headers["x-device-proof"])
        val publicKey = Ed25519PublicKeyParameters(
            Base64.decode(manager.getOrCreate().publicKeyBase64), 0,
        )
        val verifier = Ed25519Signer()
        verifier.init(false, publicKey)
        val message = "refresh-token-value".toByteArray(Charsets.UTF_8)
        verifier.update(message, 0, message.size)
        assertTrue(verifier.verifySignature(signature))
    }

    @Test
    fun freshChallengeSignsExactUtf8AndCarriesNoTenant() = runTest {
        val manager = DeviceIdentityManager(InMemoryStore())
        val message = "exact challenge \n bytes"
        val installationId = manager.getOrCreate().deviceId
        val transport = ApiHttpTransport { _, _, headers, body ->
            assertTrue(!headers.containsKey("x-tenant-id"))
            val request = org.json.JSONObject(body!!)
            assertEquals(installationId, request.getString("installationId"))
            200 to org.json.JSONObject().put("data", org.json.JSONObject().put("challengeId", "challenge")
                .put("challengeMessage", message).put("expiresAt", java.time.Instant.now().plusSeconds(60).toString()).put("accountLimit", 2)).toString()
        }
        val headers = manager.challengeHeaders("https://example.invalid", "ignored", transport) { true }
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(Base64.decode(manager.getOrCreate().publicKeyBase64), 0))
        val bytes = message.toByteArray(Charsets.UTF_8); verifier.update(bytes, 0, bytes.size)
        assertTrue(verifier.verifySignature(Base64.decode(headers["x-device-proof"])))
    }

    @Test
    fun incompleteStoredIdentityIsNotSilentlyRotated() = runTest {
        val store = InMemoryStore()
        val created = DeviceIdentityManager(store).getOrCreate()
        store.data.remove("chaty.device.ed25519.public.v1") // corrupt the pair
        try { DeviceIdentityManager(store).getOrCreate(); org.junit.Assert.fail("Must not rotate installation identity") }
        catch (_: IllegalStateException) { }
        assertEquals(created.deviceId, store.data["chaty.device.id.v1"])
    }
}
