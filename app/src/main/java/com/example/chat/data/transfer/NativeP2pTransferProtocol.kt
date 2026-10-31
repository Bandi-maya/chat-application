package com.example.chat.data.transfer

import android.util.Base64
import java.io.File
import java.io.RandomAccessFile
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Native Kotlin primitives for Chaty's AlterSend-inspired P2P file protocol.
 *
 * The invitation secret is carried only in the URI fragment. A rendezvous/signalling
 * service must receive the transferId (never the secret), authenticate the recipient,
 * and establish an authenticated encrypted data channel before these chunk methods
 * are used. WebRTC DTLS is the transport encryption layer; AES-GCM here additionally
 * protects stored/in-flight chunk payloads end-to-end.
 *
 * This class deliberately does not pretend to establish a P2P connection. It is the
 * transport-independent, disk-backed protocol/security layer for that connection.
 */
object NativeP2pTransferProtocol {
    const val MAX_FILE_BYTES: Long = 100L * 1024L * 1024L * 1024L
    const val DEFAULT_CHUNK_BYTES: Int = 256 * 1024
    private const val KEY_BYTES = 32
    private const val NONCE_PREFIX_BYTES = 4
    private const val GCM_TAG_BITS = 128

    private val random = SecureRandom()

    data class Invite(val transferId: String, val secret: ByteArray) {
        fun toUri(): String = "chaty://transfer/$transferId#${encode(secret)}"
    }

    data class Manifest(
        val transferId: String,
        val displayName: String,
        val mimeType: String,
        val sizeBytes: Long,
        val chunkBytes: Int,
        val sha256Hex: String,
        val noncePrefix: ByteArray,
        val expiresAtEpochSeconds: Long,
    ) {
        val chunkCount: Long
            get() = if (sizeBytes == 0L) 0L else (sizeBytes + chunkBytes - 1L) / chunkBytes
    }

    data class EncryptedChunk(val index: Long, val ciphertextAndTag: ByteArray)

    /** A channel implementation can be backed by WebRTC DataChannel or a blind relay. */
    interface ChunkChannel {
        suspend fun sendManifest(manifest: Manifest, authentication: ByteArray)
        suspend fun requestChunks(indices: LongArray)
        suspend fun sendChunk(chunk: EncryptedChunk)
        suspend fun sendComplete()
    }

    fun createInvite(): Invite {
        val secret = ByteArray(KEY_BYTES).also(random::nextBytes)
        val id = java.util.UUID.randomUUID().toString()
        return Invite(id, secret)
    }

    fun parseInvite(uriText: String): Invite {
        val uri = runCatching { URI(uriText) }.getOrElse { throw SecurityException("Invalid transfer link") }
        if (uri.scheme != "chaty" || uri.host != "transfer" || uri.query != null) {
            throw SecurityException("Unsupported transfer link")
        }
        val id = uri.path.removePrefix("/")
        if (!id.matches(Regex("[0-9a-fA-F-]{36}"))) throw SecurityException("Invalid transfer id")
        val secret = decode(uri.rawFragment ?: throw SecurityException("Transfer link is incomplete"))
        if (secret.size != KEY_BYTES) throw SecurityException("Invalid transfer secret")
        return Invite(id, secret)
    }

    fun createManifest(
        invite: Invite,
        source: File,
        mimeType: String,
        expiresAtEpochSeconds: Long,
        chunkBytes: Int = DEFAULT_CHUNK_BYTES,
    ): Manifest {
        require(source.isFile && source.canRead()) { "Source file is not readable" }
        val size = source.length()
        require(size in 1..MAX_FILE_BYTES) { "File must be between 1 byte and 100 GB" }
        require(chunkBytes in (64 * 1024)..(1024 * 1024)) { "Unsupported chunk size" }
        require(expiresAtEpochSeconds > System.currentTimeMillis() / 1000L) { "Transfer expiry must be in the future" }
        val safeName = source.name
            .replace(Regex("[\\\\/\\u0000-\\u001f]"), "_")
            .take(180)
            .ifBlank { "video" }
        val prefix = ByteArray(NONCE_PREFIX_BYTES).also(random::nextBytes)
        return Manifest(
            transferId = invite.transferId,
            displayName = safeName,
            mimeType = mimeType.take(120).ifBlank { "application/octet-stream" },
            sizeBytes = size,
            chunkBytes = chunkBytes,
            sha256Hex = sha256(source),
            noncePrefix = prefix,
            expiresAtEpochSeconds = expiresAtEpochSeconds,
        )
    }

    /** HMAC binds the file metadata, size, chunk geometry, expiry and whole-file digest. */
    fun authenticateManifest(manifest: Manifest, secret: ByteArray): ByteArray {
        require(secret.size == KEY_BYTES)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(deriveKey(secret, "chaty-manifest-v1"), "HmacSHA256"))
        mac.update(manifest.transferId.toByteArray(Charsets.UTF_8))
        mac.update(0.toByte())
        mac.update(manifest.displayName.toByteArray(Charsets.UTF_8))
        mac.update(0)
        mac.update(manifest.mimeType.toByteArray(Charsets.UTF_8))
        mac.update(ByteBuffer.allocate(8 + 4 + 8).order(ByteOrder.BIG_ENDIAN)
            .putLong(manifest.sizeBytes).putInt(manifest.chunkBytes).putLong(manifest.expiresAtEpochSeconds).array())
        mac.update(manifest.sha256Hex.toByteArray(Charsets.US_ASCII))
        mac.update(manifest.noncePrefix)
        return mac.doFinal()
    }

    fun verifyManifest(manifest: Manifest, authentication: ByteArray, secret: ByteArray, nowEpochSeconds: Long = System.currentTimeMillis() / 1000L) {
        if (manifest.expiresAtEpochSeconds <= nowEpochSeconds) throw SecurityException("This transfer link has expired")
        if (manifest.sizeBytes !in 1..MAX_FILE_BYTES) throw SecurityException("Invalid file size")
        if (manifest.chunkBytes !in (64 * 1024)..(1024 * 1024)) throw SecurityException("Invalid chunk size")
        if (manifest.noncePrefix.size != NONCE_PREFIX_BYTES) throw SecurityException("Invalid transfer nonce")
        if (!manifest.sha256Hex.matches(Regex("[0-9a-f]{64}"))) throw SecurityException("Invalid file digest")
        val expected = authenticateManifest(manifest, secret)
        if (!MessageDigest.isEqual(expected, authentication)) throw SecurityException("Transfer link authentication failed")
    }

    /** Read only the requested byte range; never loads the whole source file into memory. */
    fun encryptChunk(source: File, manifest: Manifest, secret: ByteArray, index: Long): EncryptedChunk {
        require(secret.size == KEY_BYTES)
        require(index in 0 until manifest.chunkCount)
        if (!source.isFile || source.length() != manifest.sizeBytes) throw SecurityException("Source file changed during transfer")
        val offset = Math.multiplyExact(index, manifest.chunkBytes.toLong())
        val length = minOf(manifest.chunkBytes.toLong(), manifest.sizeBytes - offset).toInt()
        val plain = ByteArray(length)
        RandomAccessFile(source, "r").use { file ->
            file.seek(offset)
            file.readFully(plain)
        }
        return EncryptedChunk(index, crypt(Cipher.ENCRYPT_MODE, manifest, secret, index, plain))
    }

    /** Decrypt/authenticate one chunk and write it at its exact file offset. */
    fun writeChunk(destination: File, manifest: Manifest, secret: ByteArray, chunk: EncryptedChunk) {
        require(secret.size == KEY_BYTES)
        require(chunk.index in 0 until manifest.chunkCount)
        val offset = Math.multiplyExact(chunk.index, manifest.chunkBytes.toLong())
        val expectedLength = minOf(manifest.chunkBytes.toLong(), manifest.sizeBytes - offset).toInt()
        val plain = crypt(Cipher.DECRYPT_MODE, manifest, secret, chunk.index, chunk.ciphertextAndTag)
        if (plain.size != expectedLength) throw SecurityException("Chunk length does not match manifest")
        destination.parentFile?.mkdirs()
        RandomAccessFile(destination, "rw").use { file ->
            file.seek(offset)
            file.write(plain)
            file.fd.sync()
        }
    }

    /** Call only after every chunk has been received; rejects missing/truncated/corrupt files. */
    fun verifyCompletedFile(destination: File, manifest: Manifest): Boolean {
        if (!destination.isFile || destination.length() != manifest.sizeBytes) return false
        return MessageDigest.isEqual(
            manifest.sha256Hex.toByteArray(Charsets.US_ASCII),
            sha256(destination).toByteArray(Charsets.US_ASCII),
        )
    }

    fun chunkIndices(sizeBytes: Long, chunkBytes: Int): LongArray {
        require(sizeBytes in 1..MAX_FILE_BYTES && chunkBytes > 0)
        val count = (sizeBytes + chunkBytes - 1L) / chunkBytes
        require(count <= Int.MAX_VALUE) { "Too many chunks" }
        return LongArray(count.toInt()) { it.toLong() }
    }

    private fun crypt(mode: Int, manifest: Manifest, secret: ByteArray, index: Long, input: ByteArray): ByteArray {
        val nonce = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
            .put(manifest.noncePrefix).putLong(index).array()
        val aad = ByteBuffer.allocate(16 + 8 + 4).order(ByteOrder.BIG_ENDIAN)
            .put(java.util.UUID.fromString(manifest.transferId).let {
                ByteBuffer.allocate(16).putLong(it.mostSignificantBits).putLong(it.leastSignificantBits).array()
            })
            .putLong(index).putInt(input.size.takeIf { mode == Cipher.ENCRYPT_MODE } ?: (input.size - 16))
            .array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, SecretKeySpec(deriveKey(secret, "chaty-chunk-aes-v1"), "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return try { cipher.doFinal(input) } catch (_: Exception) {
            throw SecurityException("Chunk authentication failed")
        }
    }

    /** HKDF-SHA256 key separation: manifest authentication and chunk encryption never reuse a raw key. */
    private fun deriveKey(secret: ByteArray, label: String): ByteArray {
        val salt = "chaty-transfer-v1".toByteArray(Charsets.UTF_8)
        val extract = Mac.getInstance("HmacSHA256")
        extract.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = extract.doFinal(secret)
        val expand = Mac.getInstance("HmacSHA256")
        expand.init(SecretKeySpec(prk, "HmacSHA256"))
        expand.update(label.toByteArray(Charsets.UTF_8))
        expand.update(1.toByte())
        return expand.doFinal().copyOf(KEY_BYTES)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(256 * 1024).use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun decode(value: String): ByteArray =
        runCatching { Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }
            .getOrElse { throw SecurityException("Invalid transfer secret") }
}
