package com.example.chat.data.media

/** Media tiers orchestration (media matrix): AUTO/HD/FILE image send over the
 *  D2=C encrypted attachment queue.
 *
 *  Pipeline: tier policy -> processor (decode/scale/EXIF-strip/blur
 *  placeholder, Android) -> AES-256-GCM encrypt with a fresh random key ->
 *  upload ciphertext -> MLS message carrying the key + metadata (blur
 *  placeholder, dimensions, tier) inside the encrypted envelope.
 *
 *  The crypto and IO seams are injected so the whole pipeline is
 *  JVM-testable; only the processor needs Android bitmaps.
 */
class SecureImageSendService(
    private val encrypt: (plain: ByteArray, key: ByteArray) -> ByteArray,
    private val upload: suspend (conversationId: String, ciphertext: ByteArray) -> String,
    private val sendMessage: suspend (conversationId: String, payload: Map<String, Any?>) -> Unit,
    private val randomKey: () -> ByteArray = {
        val key = ByteArray(32)
        java.security.SecureRandom().nextBytes(key)
        key
    },
) {
    /** What the sender needs to build the MLS envelope. The key NEVER leaves
     *  this map: it travels only inside the MLS-encrypted message payload. */
    data class Outcome(
        val attachmentId: String,
        val sizeBytes: Long,
        val envelope: Map<String, Any?>,
    )

    /** [prepared] comes from ImageSendProcessor.prepare (or original bytes for
     *  FILE mode). [tier] from ImageSendPolicy.tier(mode, uplinkKbps). */
    suspend fun send(
        conversationId: String,
        prepared: ImageSendProcessor.Prepared,
        tier: ImageSendPolicy.Tier,
        caption: String?,
    ): Outcome {
        val key = randomKey()
        val ciphertext = encrypt(prepared.bytes, key)
        val attachmentId = upload(conversationId, ciphertext)
        val metadata = mutableMapOf<String, Any?>(
            "kind" to "image",
            "send_mode" to tier.mode.name.lowercase(),
            "width" to prepared.width,
            "height" to prepared.height,
            "exif_stripped" to tier.stripExif,
        )
        prepared.blurPlaceholder?.let { metadata["blur_placeholder"] = it }
        caption?.takeIf { it.isNotBlank() }?.let { metadata["caption"] = it }
        val envelope = mapOf<String, Any?>(
            "type" to "attachment",
            "attachment_id" to attachmentId,
            "key_b64" to java.util.Base64.getEncoder().encodeToString(key),
            "size_bytes" to ciphertext.size.toLong(),
            "metadata" to metadata,
        )
        sendMessage(conversationId, envelope)
        return Outcome(attachmentId, ciphertext.size.toLong(), envelope)
    }
}
