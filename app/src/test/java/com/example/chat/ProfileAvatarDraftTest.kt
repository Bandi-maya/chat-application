package com.example.chat

import com.example.chat.ui.profile.ProfileAvatarDraft
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ProfileAvatarDraftTest {
    @Test fun readIsLocalAndDiscardDeletesOnce() {
        val f = File.createTempFile("avatar-draft", ".png")
        f.writeBytes(byteArrayOf(1, 2, 3))
        val d = ProfileAvatarDraft(f)
        assertEquals(f.absolutePath, d.previewPath)
        assertArrayEquals(byteArrayOf(1, 2, 3), d.readBytes())
        assertTrue(f.exists())
        d.close(); d.close()
        assertFalse(f.exists())
        assertTrue(runCatching { d.readBytes() }.isFailure)
        assertTrue(runCatching { d.previewPath }.isFailure)
    }
    @Test fun emptyAndOversizedDraftsNeverRead() {
        for (length in listOf(0L, ProfileAvatarDraft.MAX_BYTES + 1)) {
            val f = File.createTempFile("avatar-draft", ".png")
            java.io.RandomAccessFile(f, "rw").use { it.setLength(length) }
            val d = ProfileAvatarDraft(f)
            assertTrue(runCatching { d.readBytes() }.isFailure)
            d.close(); assertFalse(f.exists())
        }
    }
}
