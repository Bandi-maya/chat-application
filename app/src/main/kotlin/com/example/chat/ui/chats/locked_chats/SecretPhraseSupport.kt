package com.example.chat.ui.chats.locked_chats

/** Secret-phrase pieces ported from local_lock_service.dart (api branch
 *  anchor 3bee780). Storage itself stays on LocalLockService (integration
 *  owner): the Dart service stores
 *  `chaty.local_lock.v2.secret_phrase.hash` / `.salt` in platform secure
 *  storage, PBKDF2-HMAC-SHA256, 120,000 rounds, 16-byte random salt,
 *  256-bit key, over the normalized phrase. */
object SecretPhraseSupport {

    /** LocalLockService.normalizeSecretPhrase: trim, collapse contiguous
     *  whitespace to single spaces, lowercase. */
    fun normalizeSecretPhrase(phrase: String): String =
        phrase.trim().replace(Regex("\\s+"), " ").lowercase()

    /** Dart setSecretPhrase throws ArgumentError('Secret phrase cannot be
     *  empty.') when the normalized phrase is empty. */
    fun requireValidSecretPhrase(rawPhrase: String): String {
        val normalized = normalizeSecretPhrase(rawPhrase)
        if (normalized.isEmpty()) throw IllegalArgumentException("Secret phrase cannot be empty.")
        return normalized
    }
}
