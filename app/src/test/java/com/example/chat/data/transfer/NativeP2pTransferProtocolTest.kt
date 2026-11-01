package com.example.chat.data.transfer

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeP2pTransferProtocolTest {
    @Test
    fun rejectsNonCanonicalUppercaseTransferIds() {
        val invite = NativeP2pTransferProtocol.createInvite()
        try {
            val parts = invite.toUri().split("#", limit = 2)
            val id = invite.transferId.uppercase()
            val forged = "chaty://transfer/$id#${parts.getOrNull(1).orEmpty()}"
            var rejected = false
            try {
                NativeP2pTransferProtocol.parseInvite(forged)
            } catch (_: SecurityException) {
                rejected = true
            }
            assertTrue("non-canonical transfer IDs must be rejected", rejected)
        } finally {
            invite.secret.fill(0)
        }
    }

    @Test
    fun normalizesUntrustedMimeTypesAndKeepsValidTypes() {
        val root = Files.createTempDirectory("chaty-p2p-mime").toFile()
        try {
            val source = File(root, "payload.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val invite = NativeP2pTransferProtocol.createInvite()
            val valid = NativeP2pTransferProtocol.createManifest(
                invite, source, "video/mp4", System.currentTimeMillis() / 1000L + 600,
            )
            assertEquals("video/mp4", valid.mimeType)
            val untrusted = NativeP2pTransferProtocol.createManifest(
                invite, source, "text/plain; charset=utf-8", System.currentTimeMillis() / 1000L + 600,
            )
            assertEquals("application/octet-stream", untrusted.mimeType)
            invite.secret.fill(0)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun encryptsAndVerifiesChunkedFileEndToEnd() {
        val root = Files.createTempDirectory("chaty-p2p").toFile()
        try {
            val source = File(root, "source.bin")
            val bytes = ByteArray(170_321) { ((it * 31) % 251).toByte() }
            source.writeBytes(bytes)
            val invite = NativeP2pTransferProtocol.createInvite()
            val manifest = NativeP2pTransferProtocol.createManifest(
                invite, source, "application/octet-stream", System.currentTimeMillis() / 1000L + 600,
            )
            val authentication = NativeP2pTransferProtocol.authenticateManifest(manifest, invite.secret)
            NativeP2pTransferProtocol.verifyManifest(manifest, authentication, invite.secret)
            val received = File(root, "received.part")
            for (index in 0 until manifest.chunkCount) {
                val encrypted = NativeP2pTransferProtocol.encryptChunk(source, manifest, invite.secret, index)
                NativeP2pTransferProtocol.writeChunk(received, manifest, invite.secret, encrypted)
            }
            NativeP2pTransferProtocol.syncFile(received)
            assertTrue(NativeP2pTransferProtocol.verifyCompletedFile(received, manifest))
            assertArrayEquals(bytes, received.readBytes())
            invite.secret.fill(0)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsModifiedManifestAndModifiedChunk() {
        val root = Files.createTempDirectory("chaty-p2p-tamper").toFile()
        try {
            val source = File(root, "source.bin").apply { writeBytes(ByteArray(90_000) { it.toByte() }) }
            val invite = NativeP2pTransferProtocol.createInvite()
            val manifest = NativeP2pTransferProtocol.createManifest(
                invite, source, "application/octet-stream", System.currentTimeMillis() / 1000L + 600,
            )
            val auth = NativeP2pTransferProtocol.authenticateManifest(manifest, invite.secret)
            val altered = manifest.copy(displayName = "attacker.bin")
            var manifestRejected = false
            try {
                NativeP2pTransferProtocol.verifyManifest(altered, auth, invite.secret)
            } catch (_: SecurityException) {
                manifestRejected = true
            }
            assertTrue("modified manifest must fail HMAC verification", manifestRejected)

            val encrypted = NativeP2pTransferProtocol.encryptChunk(source, manifest, invite.secret, 0)
            val corrupt = encrypted.copy(ciphertextAndTag = encrypted.ciphertextAndTag.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() })
            var chunkRejected = false
            try {
                NativeP2pTransferProtocol.writeChunk(File(root, "corrupt.part"), manifest, invite.secret, corrupt)
            } catch (_: SecurityException) {
                chunkRejected = true
            }
            assertTrue("modified chunk must fail AES-GCM authentication", chunkRejected)
            invite.secret.fill(0)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsOutOfRangeFileChunkIndicesBeforeReading() {
        val root = Files.createTempDirectory("chaty-p2p-index").toFile()
        try {
            val source = File(root, "source.bin").apply { writeBytes(ByteArray(70_000) { it.toByte() }) }
            val invite = NativeP2pTransferProtocol.createInvite()
            val manifest = NativeP2pTransferProtocol.createManifest(
                invite, source, "application/octet-stream", System.currentTimeMillis() / 1000L + 600,
            )
            for (invalidIndex in listOf(-1L, manifest.chunkCount)) {
                var rejected = false
                try {
                    NativeP2pTransferProtocol.encryptChunk(source, manifest, invite.secret, invalidIndex)
                } catch (_: IllegalArgumentException) {
                    rejected = true
                }
                assertTrue("out-of-range chunk index $invalidIndex must be rejected", rejected)
            }
            invite.secret.fill(0)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun readsPickerFileChannelByRandomAccessRange() {
        val root = Files.createTempDirectory("chaty-p2p-channel").toFile()
        try {
            val source = File(root, "large-source.bin")
            val bytes = ByteArray(140_000) { ((it * 17) % 239).toByte() }
            source.writeBytes(bytes)
            val invite = NativeP2pTransferProtocol.createInvite()
            RandomAccessFile(source, "r").use { input ->
                val channel: FileChannel = input.channel
                val manifest = NativeP2pTransferProtocol.createManifest(
                    invite, channel, "camera.jpg", bytes.size.toLong(), "image/jpeg",
                    System.currentTimeMillis() / 1000L + 600,
                )
                val received = File(root, "channel.part")
                for (index in 0 until manifest.chunkCount) {
                    val chunk = NativeP2pTransferProtocol.encryptChunk(channel, manifest, invite.secret, index)
                    NativeP2pTransferProtocol.writeChunk(received, manifest, invite.secret, chunk)
                }
                NativeP2pTransferProtocol.syncFile(received)
                assertTrue(NativeP2pTransferProtocol.verifyCompletedFile(received, manifest))
                assertArrayEquals(bytes, received.readBytes())
            }
            invite.secret.fill(0)
        } finally {
            root.deleteRecursively()
        }
    }
}
