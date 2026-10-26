package com.example.chat

import com.example.chat.data.local.AttachmentRef
import com.example.chat.data.media.ExportRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ExportRegistryTest {
    private val ref = AttachmentRef("c1", "m1", "a1")
    private var session: String? = "s1"
    private var allowed = true
    private var clock = 1_000L
    private val reg = ExportRegistry({ session }, { allowed }, { clock })
    private fun file() = File.createTempFile("chaty_reg_", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }

    @Test fun opensOnlyWhileEveryCheckPasses() {
        val f = file(); val id = reg.register(f, "image/jpeg", ref, "s1", 2_000L)
        assertEquals(32, id.length); assertTrue(id.all { it in "0123456789abcdef" })
        assertNotNull(reg.open(id)); assertTrue(reg.isLive(id)); assertTrue(f.exists())
    }

    @Test fun unknownIdsAndPathLikeIdsNeverOpen() {
        file().also { reg.register(it, "image/jpeg", ref, "s1", 2_000L) }
        for (bad in listOf("", "../x", "x", "0".repeat(32), "/etc/passwd")) assertNull(reg.open(bad))
    }

    @Test fun expiryRemovesEntryAndFileAtOpen() {
        val f = file(); val id = reg.register(f, "image/jpeg", ref, "s1", 2_000L)
        clock = 2_000L; assertNull(reg.open(id)); assertFalse(f.exists()); assertFalse(reg.isLive(id))
    }

    @Test fun sessionChangeAndLogoutRevokeAtOpen() {
        val f = file(); val id = reg.register(f, "image/jpeg", ref, "s1", 9_000L)
        session = "s2"; assertNull(reg.open(id)); assertFalse(f.exists())
        val f2 = file(); val id2 = reg.register(f2, "image/jpeg", ref, "s2", 9_000L)
        session = null; assertNull(reg.open(id2)); assertFalse(f2.exists())
    }

    @Test fun sourcePolicyRecheckedOnEveryOpenAndPolicyErrorFailsClosed() {
        val f = file(); val id = reg.register(f, "image/jpeg", ref, "s1", 9_000L)
        assertNotNull(reg.open(id)); allowed = false; assertNull(reg.open(id)); assertFalse(f.exists())
        allowed = true
        val bad = ExportRegistry({ "s1" }, { throw IllegalStateException("lookup failed") }, { clock })
        val f2 = file(); val id2 = bad.register(f2, "image/jpeg", ref, "s1", 9_000L)
        assertNull(bad.open(id2)); assertFalse(f2.exists())
    }

    @Test fun deleteHookRevokesOnlyThatSourcesExports() {
        val other = AttachmentRef("c1", "m2", "a2")
        val f1 = file(); val f2 = file(); val f3 = file()
        val a = reg.register(f1, "image/jpeg", ref, "s1", 9_000L); val b = reg.register(f2, "image/jpeg", ref, "s1", 9_000L)
        val c = reg.register(f3, "image/jpeg", other, "s1", 9_000L)
        reg.revokeFor(ref)
        assertNull(reg.open(a)); assertNull(reg.open(b)); assertFalse(f1.exists()); assertFalse(f2.exists())
        assertNotNull(reg.open(c)); reg.revokeAll(); assertNull(reg.open(c)); assertFalse(f3.exists())
    }

    @Test fun sweepRemovesExpiredOnly() {
        val f1 = file(); val f2 = file()
        val a = reg.register(f1, "image/jpeg", ref, "s1", 1_500L); val b = reg.register(f2, "image/jpeg", ref, "s1", 9_000L)
        clock = 1_600L; reg.sweepExpired(); assertFalse(f1.exists()); assertTrue(f2.exists()); assertNull(reg.open(a)); assertNotNull(reg.open(b))
    }

    @Test fun mimeMapping() { assertEquals("image/jpeg", ExportRegistry.mimeFor("JPG")); assertEquals("application/octet-stream", ExportRegistry.mimeFor("exe")) }

    @Test fun everyRemovalPathFiresTheUriRevokeCallbackOnce() {
        val removed = mutableListOf<String>(); reg.onRemoved = { removed += it }
        val f1 = file(); val a = reg.register(f1, "image/jpeg", ref, "s1", 9_000L)
        val f2 = file(); val b = reg.register(f2, "image/jpeg", ref, "s1", 9_000L)
        val f3 = file(); val c = reg.register(f3, "image/jpeg", ref, "s1", 9_000L)
        reg.remove(a); reg.remove(a)                       // second remove is a no-op
        reg.revokeFor(ref)                                  // takes b and c
        assertEquals(setOf(a, b, c), removed.toSet()); assertEquals(3, removed.size)
        assertFalse(f1.exists() || f2.exists() || f3.exists())
    }

    @Test fun revokeWhereRemovesOnlyMatchingAccountAndKeepsTheOther() {
        val dirA = java.nio.file.Files.createTempDirectory("acctA").toFile(); val dirB = java.nio.file.Files.createTempDirectory("acctB").toFile()
        val fa = File(dirA, "x.jpg").apply { writeBytes(byteArrayOf(1)) }; val fb = File(dirB, "x.jpg").apply { writeBytes(byteArrayOf(1)) }
        val ia = reg.register(fa, "image/jpeg", ref, "s1", 9_000L); val ib = reg.register(fb, "image/jpeg", ref, "s1", 9_000L)
        reg.revokeWhere { it.file.path.startsWith(dirA.path) }
        assertNull(reg.open(ia)); assertFalse(fa.exists()); assertNotNull(reg.open(ib)); assertTrue(fb.exists())
    }
}
