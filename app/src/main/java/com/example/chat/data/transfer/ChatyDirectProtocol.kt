package com.example.chat.data.transfer

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Phase1 client-only wire format. Never fall back to the legacy channel/framing. */
object ChatyDirectProtocol {
    const val CHANNEL = "chaty-direct-original-v2"
    const val OVERHEAD = 4 + 8 + 12 + 32 + 16
    private const val MAGIC = 0x43445932 // CDY2
    private val random = SecureRandom()

    fun allowedIceUrl(url: String): Boolean = url.trim().lowercase().let {
        it.startsWith("stun:") || it.startsWith("stuns:")
    }
    fun isRelayCandidate(sdp: String): Boolean = Regex("(?:^|\\s)typ\\s+relay(?:\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(sdp)

    fun directSdp(sdp: String): String = sdp.lineSequence()
        .filterNot { it.trimStart().startsWith("a=candidate:") && isRelayCandidate(it) }
        .joinToString("\r\n")

    private fun key(secret: ByteArray, purpose: String): ByteArray {
        require(secret.size == 32)
        return Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(secret, "HmacSHA256")); doFinal(purpose.toByteArray(Charsets.UTF_8))
        }
    }
    private fun aad(m: NativeP2pTransferProtocol.Manifest, index: Long): ByteArray =
        "CDY2|${m.transferId}|${m.sha256Hex}|${m.sizeBytes}|${m.chunkBytes}|$index".toByteArray(Charsets.UTF_8)

    fun encryptChunk(source: FileChannel, m: NativeP2pTransferProtocol.Manifest, secret: ByteArray, index: Long): ByteArray {
        require(index in 0 until m.chunkCount && source.isOpen && source.size() == m.sizeBytes)
        val offset = Math.multiplyExact(index, m.chunkBytes.toLong())
        val length = minOf(m.chunkBytes.toLong(), m.sizeBytes - offset).toInt()
        val plain = ByteArray(length)
        val b = ByteBuffer.wrap(plain)
        var at = offset
        while (b.hasRemaining()) {
            val n = source.read(b, at)
            if (n <= 0) throw java.io.EOFException("Original source became unavailable")
            at += n
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(plain)
        val payload = digest + plain
        val nonce = ByteArray(12).also(random::nextBytes)
        val k = key(secret, "chaty-direct-v2-chunk")
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(aad(m, index))
            val encrypted = cipher.doFinal(payload)
            ByteBuffer.allocate(4 + 8 + 12 + encrypted.size).order(ByteOrder.BIG_ENDIAN)
                .putInt(MAGIC).putLong(index).put(nonce).put(encrypted).array()
        } finally { plain.fill(0); digest.fill(0); payload.fill(0); k.fill(0) }
    }
    fun writeChunk(destination: File, m: NativeP2pTransferProtocol.Manifest, secret: ByteArray, frame: ByteArray): Long {
        if (frame.size !in (OVERHEAD + 1)..(m.chunkBytes + OVERHEAD)) throw SecurityException("Invalid Direct chunk length")
        val b = ByteBuffer.wrap(frame).order(ByteOrder.BIG_ENDIAN)
        if (b.int != MAGIC) throw SecurityException("Update both phones: incompatible Chaty Direct version")
        val index = b.long
        if (index !in 0 until m.chunkCount) throw SecurityException("Invalid Direct chunk index")
        val nonce = ByteArray(12).also(b::get)
        val encrypted = ByteArray(b.remaining()).also(b::get)
        val k = key(secret, "chaty-direct-v2-chunk")
        val payload = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(aad(m, index)); cipher.doFinal(encrypted)
        } finally { k.fill(0) }
        try {
            val expectedLength = minOf(m.chunkBytes.toLong(), m.sizeBytes - index * m.chunkBytes).toInt()
            if (payload.size != 32 + expectedLength) throw SecurityException("Direct chunk geometry mismatch")
            val plain = payload.copyOfRange(32, payload.size)
            val expected = payload.copyOfRange(0, 32)
            try {
                if (!MessageDigest.isEqual(expected, MessageDigest.getInstance("SHA-256").digest(plain))) {
                    throw SecurityException("Direct chunk SHA-256 mismatch")
                }
                RandomAccessFile(destination, "rw").use { out -> out.seek(index * m.chunkBytes); out.write(plain) }
            } finally { plain.fill(0); expected.fill(0) }
            return index
        } finally { payload.fill(0) }
    }
    /** Authenticated, transfer-bound, directional controls. Sequence is checked by the session. */
    fun controlMac(secret: ByteArray, transferId: String, senderId: String, sequence: Long, body: String): ByteArray {
        require(sequence > 0 && body.length <= 65536)
        val k = key(secret, "chaty-direct-v2-control")
        return try {
            Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(k, "HmacSHA256"))
                doFinal("CDY2|$transferId|$senderId|$sequence|$body".toByteArray(Charsets.UTF_8))
            }
        } finally { k.fill(0) }
    }
}
