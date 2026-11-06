package com.example.chat.data.transfer

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ChatyDirectProtocolTest {
    private fun fixture(run: (File, NativeP2pTransferProtocol.Manifest, ByteArray, File) -> Unit) {
        val root = Files.createTempDirectory("chaty-direct-v2").toFile()
        val source = File(root, "original.webp").apply { writeBytes(ByteArray(140123) { (it * 17).toByte() }) }
        val invite = NativeP2pTransferProtocol.createInvite()
        val manifest = NativeP2pTransferProtocol.createManifest(invite, source, "image/webp", System.currentTimeMillis()/1000 + 600)
        try { run(source, manifest, invite.secret, root) } finally { invite.secret.fill(0); root.deleteRecursively() }
    }
    @Test fun originalBytesRoundTripOutOfOrderAndResume() = fixture { source, manifest, secret, root ->
        val destination = File(root, "received.part")
        val frames = RandomAccessFile(source, "r").use { f -> (0 until manifest.chunkCount).map { ChatyDirectProtocol.encryptChunk(f.channel, manifest, secret, it) } }
        ChatyDirectProtocol.writeChunk(destination, manifest, secret, frames.last())
        NativeP2pTransferProtocol.syncFile(destination)
        // Reopen the on-disk partial and send only missing chunks; final digest is mandatory.
        for (frame in frames.dropLast(1)) ChatyDirectProtocol.writeChunk(destination, manifest, secret, frame)
        assertTrue(NativeP2pTransferProtocol.verifyCompletedFile(destination, manifest))
        assertArrayEquals(source.readBytes(), destination.readBytes())
        ChatyDirectProtocol.writeChunk(destination, manifest, secret, frames.first())
        assertTrue(NativeP2pTransferProtocol.verifyCompletedFile(destination, manifest))
    }
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Must reject forged/incompatible chunk") } catch (_: SecurityException) { } catch (_: javax.crypto.AEADBadTagException) { }
    }
    @Test fun tamperedWrongKeyWrongTransferAndLegacyFramesFail() = fixture { source, m, secret, root ->
        val frame = RandomAccessFile(source,"r").use { ChatyDirectProtocol.encryptChunk(it.channel,m,secret,0) }
        val dest = File(root,"rejected.part")
        rejected { ChatyDirectProtocol.writeChunk(dest,m,secret,frame.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }) }
        rejected { ChatyDirectProtocol.writeChunk(dest,m,ByteArray(32){9},frame) }
        rejected { ChatyDirectProtocol.writeChunk(dest,m.copy(transferId=java.util.UUID.randomUUID().toString()),secret,frame) }
        rejected { ChatyDirectProtocol.writeChunk(dest,m,secret,frame.copyOf().also { it[0] = 0 }) }
        rejected { ChatyDirectProtocol.writeChunk(dest,m,secret,ByteArray(12)) }
        assertFalse(dest.exists())
    }
    @Test fun controlAuthenticationBindsDirectionTransferSequenceAndBody() {
        val key = ByteArray(32){it.toByte()}
        val a = ChatyDirectProtocol.controlMac(key,"transfer","alice",1,"complete")
        assertArrayEquals(a,ChatyDirectProtocol.controlMac(key,"transfer","alice",1,"complete"))
        assertFalse(a.contentEquals(ChatyDirectProtocol.controlMac(key,"transfer","bob",1,"complete")))
        assertFalse(a.contentEquals(ChatyDirectProtocol.controlMac(key,"other","alice",1,"complete")))
        assertFalse(a.contentEquals(ChatyDirectProtocol.controlMac(key,"transfer","alice",2,"complete")))
        assertFalse(a.contentEquals(ChatyDirectProtocol.controlMac(key,"transfer","alice",1,"request")))
    }
    @Test fun rejectsTurnAndRelayWithoutBlockingMobileHostOrReflexiveCandidates() {
        assertFalse(ChatyDirectProtocol.allowedIceUrl("turn:example.org"))
        assertFalse(ChatyDirectProtocol.allowedIceUrl("turns:example.org"))
        assertTrue(ChatyDirectProtocol.allowedIceUrl("stun:example.org"))
        assertTrue(ChatyDirectProtocol.allowedIceUrl("stuns:example.org"))
        assertTrue(ChatyDirectProtocol.isRelayCandidate("candidate:1 1 udp 10 1.2.3.4 99 typ relay raddr 0.0.0.0"))
        assertFalse(ChatyDirectProtocol.isRelayCandidate("candidate:1 1 udp 10 1.2.3.4 99 typ srflx"))
        assertFalse(ChatyDirectProtocol.isRelayCandidate("candidate:1 1 udp 10 10.1.2.3 99 typ host"))
    }
    @Test fun sdpEmbeddedRelayCandidatesAreRemoved() {
        val sdp = "v=0\r\na=candidate:1 1 udp 1 1.2.3.4 99 typ relay\r\na=candidate:2 1 udp 1 10.1.1.1 99 typ host\r\n"
        val safe = ChatyDirectProtocol.directSdp(sdp)
        assertFalse(safe.contains("typ relay"))
        assertTrue(safe.contains("typ host"))
    }
    @Test fun explicitSha256MismatchFailsEvenWithValidGcmTag() = fixture { source,m,secret,root ->
        val frame=RandomAccessFile(source,"r").use{ChatyDirectProtocol.encryptChunk(it.channel,m,secret,0)}
        val b=java.nio.ByteBuffer.wrap(frame)
        b.int;val index=b.long;val nonce=ByteArray(12).also(b::get);val encrypted=ByteArray(b.remaining()).also(b::get)
        val mac=javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(secret,"HmacSHA256"))
        val k=mac.doFinal("chaty-direct-v2-chunk".toByteArray())
        val aad="CDY2|${m.transferId}|${m.sha256Hex}|${m.sizeBytes}|${m.chunkBytes}|$index".toByteArray()
        val c=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        c.init(javax.crypto.Cipher.DECRYPT_MODE,javax.crypto.spec.SecretKeySpec(k,"AES"),javax.crypto.spec.GCMParameterSpec(128,nonce));c.updateAAD(aad)
        val payload=c.doFinal(encrypted)
        payload[0]=(payload[0].toInt() xor 1).toByte() // wrong embedded digest, valid new AEAD tag
        c.init(javax.crypto.Cipher.ENCRYPT_MODE,javax.crypto.spec.SecretKeySpec(k,"AES"),javax.crypto.spec.GCMParameterSpec(128,ByteArray(12){7}));c.updateAAD(aad)
        val forged=java.nio.ByteBuffer.allocate(frame.size).putInt(0x43445932).putLong(index).put(ByteArray(12){7}).put(c.doFinal(payload)).array()
        rejected { ChatyDirectProtocol.writeChunk(File(root,"bad-digest.part"),m,secret,forged) }
        k.fill(0);payload.fill(0)
    }
    @Test fun sourceChangeFailsFinalHashEvenWhenEachNewChunkIsAuthenticated() = fixture { source,m,secret,root ->
        source.writeBytes(ByteArray(source.length().toInt()){42})
        val dest=File(root,"changed.part")
        RandomAccessFile(source,"r").use { f -> for (i in 0 until m.chunkCount) ChatyDirectProtocol.writeChunk(dest,m,secret,ChatyDirectProtocol.encryptChunk(f.channel,m,secret,i)) }
        assertFalse(NativeP2pTransferProtocol.verifyCompletedFile(dest,m))
    }
}
