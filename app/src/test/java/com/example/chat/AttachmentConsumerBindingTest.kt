package com.example.chat

import com.example.chat.data.local.*
import com.example.chat.data.media.*
import com.example.chat.domain.models.*
import com.example.chat.ui.chats.detail.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.time.Instant

class AttachmentConsumerBindingTest {
    private class Store : AttachmentStore {
        var stamp: String? = "account-session-1"
        var bytes: ByteArray? = byteArrayOf(1,2,3)
        var corrupt = false
        var puts = 0
        override fun session() = stamp
        override fun isOpen() = stamp != null
        override fun isEpochCurrent(expected: String) = stamp != null && stamp == expected
        override fun readVerified(ref: AttachmentRef, maxBytes: Int): StoreRead<ByteArray> = when {
            stamp == null -> StoreRead.Closed
            corrupt -> StoreRead.Corrupt
            bytes == null -> StoreRead.Miss
            else -> StoreRead.Hit(bytes!!.copyOf())
        }
        override fun lease(ref: AttachmentRef): StoreRead<LeasedFile> = StoreRead.Miss
        override fun put(ref: AttachmentRef, source: InputStream, viewOnce: Boolean, restricted: Boolean, expectedSession: String): StorePut { if (stamp != expectedSession) return StorePut.Closed; puts++; bytes = source.readBytes(); return StorePut.Stored }
        override fun invalidate(ref: AttachmentRef) { bytes = null }
        override fun exportCopy(ref: AttachmentRef, expectedSession: String, purpose: ExportPurpose, extension: String, viewOnce: Boolean, restricted: Boolean, maxLifetimeMs: Long): ExportResult = ExportResult.Denied("fake")
        override fun beginEdit(ref: AttachmentRef, expectedSession: String, kind: EditKind, viewOnce: Boolean, restricted: Boolean, maxLifetimeMs: Long): EditResult = EditResult.Denied("fake")
        override fun commitEdit(session: EditSession, purpose: ExportPurpose, maxLifetimeMs: Long): ExportResult = ExportResult.Denied("fake")
        override fun abortEdit(session: EditSession) {}
    }
    private fun message() = ChatMessage("message", "room", "peer", text = "", attachment = MessageAttachment("attachment", "image", "photo.jpg", "3B", "private-path"), createdAt = Instant.EPOCH)
    @Test fun offlineLocalHitNeverSignsUrlOrDownloads() = runBlocking {
        val store = Store(); var signs = 0; var downloads = 0
        val resolver = AttachmentSourceResolver(store, { false }, { TrustedAttachment("image", false, false) }, { downloads++; ByteArrayInputStream(byteArrayOf()) })
        val binding = AttachmentConsumerBinding("account-session-1", resolver, { store.session() }) { signs++; "https://invalid.example/media" }
        val source = binding.resolve(message(), false)
        assertTrue(source is ResolvedSource.Bytes)
        assertEquals(0, signs); assertEquals(0, downloads)
        releaseConsumerSource(source)
        assertTrue((source as ResolvedSource.Bytes).bytes.all { it == 0.toByte() })
    }
    @Test fun corruptInlineNeverSilentlyRedownloadsOrFallsBack() = runBlocking {
        val store = Store().apply { corrupt = true }
        var network = 0
        val resolver = AttachmentSourceResolver(store, { true }, { TrustedAttachment("image", false, false) }, { network++; ByteArrayInputStream(byteArrayOf(9)) })
        val source = AttachmentConsumerBinding("account-session-1", resolver, { store.session() }) { network++; "https://invalid.example" }.resolve(message(), false)
        assertEquals(ResolvedSource.Reason.CORRUPT, (source as ResolvedSource.Unavailable).reason)
        assertEquals(0, network)
    }
    @Test fun accountSwitchDuringFreshAuthorizationDoesNotReturnOldRemote() = runBlocking {
        val store = Store().apply { bytes = null }
        val resolver = AttachmentSourceResolver(store, { true }, { TrustedAttachment("image", false, false) }, { error("No download") })
        val source = AttachmentConsumerBinding("account-session-1", resolver, { store.session() }) { store.stamp = "account-session-2"; "https://invalid.example" }.resolve(message(), false)
        assertEquals(ResolvedSource.Reason.SIGNED_OUT, (source as ResolvedSource.Unavailable).reason)
    }
    @Test fun explicitOpenDownloadsVerifiedCopyButInlineMissDoesNot() = runBlocking {
        val store = Store().apply { bytes = null }
        val resolver = AttachmentSourceResolver(store, { true }, { TrustedAttachment("image", false, false) }, { ByteArrayInputStream(byteArrayOf(9)) })
        val binding = AttachmentConsumerBinding("account-session-1", resolver, { store.session() }) { "https://invalid.example" }
        assertTrue(binding.resolve(message(), false) is ResolvedSource.Remote); assertEquals(0, store.puts)
        val downloaded = binding.resolve(message(), true)
        assertTrue(downloaded is ResolvedSource.Bytes); assertEquals(1, store.puts)
        releaseConsumerSource(downloaded)
    }
    @Test fun missingCacheIdentityDeniesOnlyMediaWithoutNetwork() = runBlocking {
        val store = Store().apply { stamp = null }
        var traffic = 0
        val resolver = AttachmentSourceResolver(store, { true }, { TrustedAttachment("image", false, false) }, { traffic++; error("No network") })
        val source = AttachmentConsumerBinding("", resolver, { null }) { traffic++; "https://invalid.example" }.resolve(message(), true)
        assertEquals(ResolvedSource.Reason.SIGNED_OUT, (source as ResolvedSource.Unavailable).reason)
        assertEquals(0, traffic)
    }
    @Test fun resolverEntryCannotAdoptDifferentSessionThanCapturedBinding() = runBlocking {
        val store = Store().apply { stamp = "new-session" }
        val resolver = AttachmentSourceResolver(store, { true }, { TrustedAttachment("image", false, false) }, { error("No network") })
        val source = AttachmentConsumerBinding("account-session-1", resolver, { "account-session-1" }) { error("No signing") }.resolve(message(), false)
        assertEquals(ResolvedSource.Reason.SIGNED_OUT, (source as ResolvedSource.Unavailable).reason)
    }
    @Test fun defaultDeliveryDeniesAndWipesSource() {
        val store = Store()
        val resolver = AttachmentSourceResolver(store, { false }, { TrustedAttachment("image", false, false) }, { error("No network") })
        val binding = AttachmentConsumerBinding("account-session-1", resolver, { store.session() }) { error("No signing") }
        val source = ResolvedSource.Bytes(byteArrayOf(7))
        var assigned = false
        assertFalse(binding.deliver(source) { assigned = true })
        assertFalse(assigned); assertEquals(0.toByte(), source.bytes[0])
    }
    @Test fun explicitShortGuardMayAssignOnlyWhileScopeAndEpochMatch() {
        val store = Store()
        val resolver = AttachmentSourceResolver(store, { false }, { TrustedAttachment("image", false, false) }, { error("No network") })
        var scopeCurrent = true
        val binding = AttachmentConsumerBinding("account-session-1", resolver, { store.session() },
            deliverIfCurrent = { block -> if (scopeCurrent && store.isEpochCurrent("account-session-1")) { block(); true } else false },
        ) { error("No signing") }
        var assigned = 0
        assertTrue(binding.deliver(ResolvedSource.Bytes(byteArrayOf(1))) { assigned++ })
        scopeCurrent = false
        val rejected = ResolvedSource.Bytes(byteArrayOf(2))
        assertFalse(binding.deliver(rejected) { assigned++ })
        assertEquals(1, assigned); assertEquals(0.toByte(), rejected.bytes[0])
    }
    @Test fun leaseReleaseIsIdempotentAndBytesWiped() {
        var releases = 0
        val leased = ResolvedSource.Leased(LeasedFile(File("synthetic")) { releases++ })
        releaseConsumerSource(leased); releaseConsumerSource(leased)
        assertEquals(1, releases)
        val bytes = ResolvedSource.Bytes(byteArrayOf(4,5))
        releaseConsumerSource(bytes); assertArrayEquals(byteArrayOf(0,0), bytes.bytes)
    }
}
