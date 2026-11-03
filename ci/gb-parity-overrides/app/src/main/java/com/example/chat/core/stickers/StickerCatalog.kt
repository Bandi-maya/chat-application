package com.example.chat.core.stickers

import java.util.Locale

/** Asset identities are independent of Unicode emoji. Packs preserve source/style families. */
data class StickerAsset(
    val id: String, val packId: String, val label: String, val assetPath: String,
    val tags: Set<String>, val animated: Boolean, val sourceRef: String,
)
data class StickerPack(val id: String, val title: String, val attribution: String, val stickers: List<StickerAsset>)

object StickerCatalog {
    fun validate(packs: List<StickerPack>): Boolean {
        if (packs.map { it.id }.toSet().size != packs.size) return false
        val seen = HashSet<String>()
        return packs.all { pack ->
            pack.id.isNotBlank() && pack.title.isNotBlank() && pack.attribution.isNotBlank() && pack.stickers.all { sticker ->
                sticker.id.isNotBlank() && seen.add(sticker.id) && sticker.packId == pack.id &&
                    sticker.label.isNotBlank() && sticker.assetPath.startsWith("stickers/") &&
                    !sticker.assetPath.split('/').any { it == ".." || it.isBlank() } &&
                    (sticker.sourceRef.startsWith("https://") || sticker.sourceRef.startsWith("user-provided:"))
            }
        }
    }
    /** Word-prefix matching over real labels/tags. No external calls or invented suggestions. */
    fun search(packs: List<StickerPack>, query: String): List<StickerPack> {
        require(validate(packs)) { "Invalid sticker catalog" }
        val words = normalize(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return packs
        return packs.mapNotNull { pack ->
            val matches = pack.stickers.filter { asset ->
                val tokens = normalize(listOf(pack.title, asset.label, asset.tags.joinToString(" ")).joinToString(" "))
                    .split(Regex("[^\\p{L}\\p{N}]+"))
                words.all { word -> tokens.any { it.startsWith(word) } }
            }
            if (matches.isEmpty()) null else pack.copy(stickers = matches)
        }
    }
    private fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT)
}
