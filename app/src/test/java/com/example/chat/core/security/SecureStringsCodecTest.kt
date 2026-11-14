package com.example.chat.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SecureStringsCodecTest {

    @Test
    fun encodeDecodeRoundTrips() {
        val samples = listOf(
            "",
            "a",
            "chaty_secure_store",
            "chaty_local_backup_v1",
            "AES/GCM/NoPadding",
            "Unicode: héllo ✓ こんにちは",
        )
        for (plain in samples) {
            for (seed in listOf(0, 1, 0x51A7C0DE, -1, Int.MAX_VALUE)) {
                val encoded = SecureStringCodec.encode(plain, seed)
                assertEquals(plain, SecureStringCodec.decode(encoded, seed))
            }
        }
    }

    @Test
    fun encodedBytesDifferFromPlain() {
        val plain = "chaty_secure_store"
        val encoded = SecureStringCodec.encode(plain, 0x51A7C0DE)
        assertFalse(encoded.contentEquals(plain.toByteArray(Charsets.UTF_8)))
        assertFalse(String(encoded, Charsets.UTF_8).contains("chaty"))
    }

    @Test
    fun differentSeedsDecorrelateEntries() {
        val plain = "same-value"
        val a = SecureStringCodec.encode(plain, 0x51A7C0DE)
        val b = SecureStringCodec.encode(plain, 0x6B3FA917)
        assertFalse(a.contentEquals(b))
        assertNotEquals(SecureStringCodec.decode(a, 0x51A7C0DE), SecureStringCodec.decode(a, 0x6B3FA917))
    }

    @Test
    fun wrongSeedDoesNotDecode() {
        val encoded = SecureStringCodec.encode("secret-literal", 0x51A7C0DE)
        assertNotEquals("secret-literal", SecureStringCodec.decode(encoded, 0x51A7C0DF))
    }

    @Test
    fun keystreamIsDeterministic() {
        val first = SecureStringCodec.keystream(42, 64)
        val second = SecureStringCodec.keystream(42, 64)
        assertEquals(first.toList(), second.toList())
    }

    @Test
    fun embeddedConstantsDecodeToExpectedLiterals() {
        assertEquals("chaty_secure_store", SecureStrings.secureStoreFileName)
        assertEquals("chaty_local_backup_v1", SecureStrings.backupKeyAlias)
    }
}
