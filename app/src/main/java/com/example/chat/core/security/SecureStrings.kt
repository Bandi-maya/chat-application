package com.example.chat.core.security

/**
 * Obfuscated store for security-plane string literals (own implementation,
 * patch 26).
 *
 * Plain `const val` strings survive as readable entries in the DEX constant
 * pool even after R8 shrinking, so a `strings` dump of the APK reveals where
 * the encrypted key/value store lives and which AndroidKeyStore alias guards
 * the local backup key. Keeping those literals XOR-obfuscated forces an
 * attacker to reverse the codec instead of reading a dump.
 *
 * This is obfuscation, not encryption: the codec and seeds ship in the same
 * binary, so a determined reverse engineer can still recover the values. It
 * raises the bar against automated scanning and casual inspection; secrets
 * (keys, tokens) never belong here.
 */
object SecureStrings {

    /** Shared-preferences file backing the encrypted secure key/value store. */
    val secureStoreFileName: String by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SecureStringCodec.decode(SECURE_STORE_FILE_OBF, SECURE_STORE_FILE_SEED)
    }

    /** AndroidKeyStore alias of the AES key that encrypts local backups. */
    val backupKeyAlias: String by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SecureStringCodec.decode(BACKUP_KEY_ALIAS_OBF, BACKUP_KEY_ALIAS_SEED)
    }

    private const val SECURE_STORE_FILE_SEED = 0x51A7C0DE
    private val SECURE_STORE_FILE_OBF = byteArrayOf(
        -121, 67, -55, 31, 6, -62,
        1, 0, -101, -49, 20, 73,
        79, 9, -78, 91, 116, -70,
    )

    private const val BACKUP_KEY_ALIAS_SEED = 0x6B3FA917
    private val BACKUP_KEY_ALIAS_OBF = byteArrayOf(
        9, 86, 15, 127, -94, 26,
        -19, 23, -105, -25, 114, 122,
        -98, 122, 55, -20, -56, 59,
        12, -11, -34,
    )
}

/**
 * Deterministic XOR-stream codec behind [SecureStrings]. Kept separate (and
 * internal) so the round trip is unit-testable on the JVM without Android.
 * The stream is an LCG keyed by a per-entry seed; different seeds decorrelate
 * the entries so one recovered stream does not expose the others.
 */
internal object SecureStringCodec {

    fun keystream(seed: Int, length: Int): ByteArray {
        var state = seed
        return ByteArray(length) {
            state = state * 1103515245 + 12345
            ((state ushr 16) and 0xFF).toByte()
        }
    }

    fun encode(plain: String, seed: Int): ByteArray {
        val bytes = plain.toByteArray(Charsets.UTF_8)
        val ks = keystream(seed, bytes.size)
        return ByteArray(bytes.size) { i -> (bytes[i].toInt() xor ks[i].toInt()).toByte() }
    }

    fun decode(data: ByteArray, seed: Int): String {
        val ks = keystream(seed, data.size)
        return String(ByteArray(data.size) { i -> (data[i].toInt() xor ks[i].toInt()).toByte() }, Charsets.UTF_8)
    }
}
