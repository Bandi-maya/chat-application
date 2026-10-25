package com.example.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chat.core.crypto.MlsCiphersuite
import com.example.chat.core.crypto.MlsEngine
import com.example.chat.core.crypto.MlsGroupConfig
import com.example.chat.core.crypto.MlsNativeStatics
import com.example.chat.core.crypto.FlexibleCommitOptions
import com.example.chat.core.crypto.ProcessedMessageType
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runtime verification tier (distinct from compile + JVM unit verification):
 * boots the real application process on a device/emulator and executes the
 * real OpenMLS native engine over JNI.
 *
 * Scope honesty: the two-party roundtrip below proves the app's installed MLS
 * engine performs the full protocol path on-device (key packages, group
 * create, add-member commit + welcome, join, encrypt, decrypt both
 * directions). It is NOT app E2E messaging: no server, realtime channel,
 * auth session, or UI is involved.
 */
@RunWith(AndroidJUnit4::class)
class RuntimeBootstrapSmokeTest {

    private fun randomBytes(count: Int): ByteArray =
        ByteArray(count).also { SecureRandom().nextBytes(it) }

    @Test
    fun applicationBootstrapInstallsMlsFactoryAndStatics() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        assertTrue("expected ChatyApplication bootstrap", app is ChatyApplication)
        // ChatyApplication.onCreate -> UniffiMlsEngine.install().
        assertNotNull("MLS engine factory installed at app start", MlsEngine.factory)
        assertNotNull("MLS native statics installed at app start", MlsNativeStatics.instance)
    }

    @Test
    fun twoPartyMlsRoundtripOnRealNativeEngine() = runBlocking {
        val ciphersuite = MlsCiphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519
        val statics = MlsNativeStatics.require()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.filesDir, "smoke-mls").apply { mkdirs() }

        // Two independent engine instances = two devices (separate encrypted
        // stores + signer material), mirroring MlsE2eeService.initializeLocked.
        val engineA = MlsEngine.create(File(dir, "device-a.db").absolutePath, randomBytes(32))
        val engineB = MlsEngine.create(File(dir, "device-b.db").absolutePath, randomBytes(32))
        try {
            val keyPairA = statics.signatureKeyPairGenerate(ciphersuite)
            val signerA = statics.serializeSigner(ciphersuite, keyPairA.first, keyPairA.second)
            val identityA = "user-a:device-1".toByteArray()

            val keyPairB = statics.signatureKeyPairGenerate(ciphersuite)
            val signerB = statics.serializeSigner(ciphersuite, keyPairB.first, keyPairB.second)
            val identityB = "user-b:device-1".toByteArray()

            // B publishes a one-time key package; A creates the group and adds B.
            val keyPackageB = engineB.createKeyPackage(ciphersuite, signerB, identityB, keyPairB.second)
            val group = engineA.createGroup(
                MlsGroupConfig.defaultConfig(ciphersuite), signerA, identityA, keyPairA.second,
            )
            val commit = engineA.flexibleCommit(
                group.groupId,
                signerA,
                FlexibleCommitOptions(
                    addKeyPackages = listOf(keyPackageB.keyPackageBytes),
                    removeIndices = intArrayOf(),
                    forceSelfUpdate = false,
                    consumePendingProposals = true,
                    createGroupInfo = false,
                    useRatchetTreeExtension = true,
                ),
            )
            assertNotNull("welcome expected when adding a member", commit.welcome)
            engineA.mergePendingCommit(group.groupId)

            // B joins from the welcome; both sides converge on the same epoch.
            val joined = engineB.joinGroupFromWelcome(
                MlsGroupConfig.defaultConfig(ciphersuite), commit.welcome!!, signerB,
            )
            assertEquals(
                engineA.groupEpoch(group.groupId),
                engineB.groupEpoch(joined.groupId),
            )

            // A -> B application message over the real MLS channel.
            val plaintext = "smoke: hello over MLS".toByteArray()
            val sealed = engineA.createMessage(group.groupId, signerA, plaintext, byteArrayOf())
            val processed = engineB.processMessage(group.groupId, sealed.ciphertext)
            assertEquals(ProcessedMessageType.application, processed.messageType)
            assertArrayEquals(plaintext, processed.applicationMessage)

            // B -> A proves both sender ratchets.
            val reply = "smoke: reply over MLS".toByteArray()
            val sealedReply = engineB.createMessage(group.groupId, signerB, reply, byteArrayOf())
            val processedReply = engineA.processMessage(group.groupId, sealedReply.ciphertext)
            assertEquals(ProcessedMessageType.application, processedReply.messageType)
            assertArrayEquals(reply, processedReply.applicationMessage)
        } finally {
            engineA.close()
            engineB.close()
        }
        Unit
    }
}
