package com.example.chat

import com.example.chat.core.stickers.*
import com.example.chat.domain.models.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StickerMessagePolicyTest {
    private val sticker = BundledStickerPacks.packs.single { it.id == "user-supplied-anime" }
        .stickers.single { it.id == "anime-drink" }
    private fun envelope(): JSONObject = JSONObject().put("type", "image").put("id", "attachment")
        .put("url", "https://example.invalid/sticker.webp").put("name", "sticker.webp").put("size", "101 KB")
        .put("text", "").also { o -> StickerMessagePolicy.metadata(sticker).forEach { (k,v) -> o.put(k,v) } }
    private fun decode(content: String): ChatMessage = ChatMessage.fromApi(mapOf("id" to "m", "room_id" to "r", "sender_id" to "u", "content" to content))
    @Test fun actualMessageDecoderKeepsStickerHint() {
        val message = decode(envelope().toString())
        assertEquals(MessageType.image, message.type)
        assertTrue(StickerMessagePolicy.isSticker(message.type.name, message.metadata))
        assertEquals("https://example.invalid/sticker.webp", message.attachment?.url)
    }
    @Test fun serializedPersistedContentReopensAsSticker() {
        val persisted = JSONObject().put("content", envelope().toString()).toString()
        val restored = decode(JSONObject(persisted).getString("content"))
        assertEquals(sticker.id, restored.metadata[StickerMessagePolicy.ID])
        assertEquals(sticker.packId, restored.metadata[StickerMessagePolicy.PACK])
    }
    @Test fun ordinaryImageDoesNotBecomeSticker() { assertFalse(StickerMessagePolicy.isSticker("image", decode(envelope().also { it.remove(StickerMessagePolicy.VERSION) }.toString()).metadata)) }
    @Test fun nonImageCannotUseStickerHint() { assertTrue(StickerMessagePolicy.parse("video", StickerMessagePolicy.metadata(sticker)).isEmpty()) }
    @Test fun invalidPackIdentityRejected() { assertTrue(StickerMessagePolicy.parse("image", StickerMessagePolicy.metadata(sticker) + (StickerMessagePolicy.PACK to "../../other")).isEmpty()) }
    @Test fun hintsCannotSetProtectionAuthority() { val result = decode(envelope().put("is_protected", true).put("sender_id", "fake").toString()); assertEquals("u", result.senderId); assertFalse(result.metadata.containsKey("is_protected")) }
    @Test fun unknownStickerVersionIgnored() { assertFalse(StickerMessagePolicy.isSticker("image", decode(envelope().put(StickerMessagePolicy.VERSION, 99).toString()).metadata)) }
}
