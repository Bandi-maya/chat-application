package com.example.chat.core.stickers

/** A display hint in the EXISTING image-content envelope, never permissions or message authority. */
object StickerMessagePolicy {
    const val VERSION = "chaty_sticker_version"
    const val PACK = "chaty_sticker_pack"
    const val ID = "chaty_sticker_id"
    private val safeId = Regex("[A-Za-z0-9_-]{1,80}")
    fun metadata(sticker: StickerAsset): Map<String, Any?> {
        require(safeId.matches(sticker.id) && safeId.matches(sticker.packId))
        return mapOf(VERSION to 1, PACK to sticker.packId, ID to sticker.id)
    }
    fun parse(type: String, metadata: Map<String, Any?>): Map<String, Any?> {
        if (type != "image" || metadata[VERSION]?.toString() != "1") return emptyMap()
        val pack = metadata[PACK] as? String ?: return emptyMap()
        val id = metadata[ID] as? String ?: return emptyMap()
        if (!safeId.matches(pack) || !safeId.matches(id)) return emptyMap()
        return mapOf(VERSION to 1, PACK to pack, ID to id)
    }
    fun isSticker(type: String, metadata: Map<String, Any?>): Boolean = parse(type, metadata).isNotEmpty()
}
