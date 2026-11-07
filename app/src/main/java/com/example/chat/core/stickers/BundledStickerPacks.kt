package com.example.chat.core.stickers

/** Only the actual supplied sticker is included. Character/creator identity is not inferred. */
object BundledStickerPacks {
    val expectedSha256 = NewsStickerPack.expectedSha256 + mapOf("anime-drink" to "967f4e313bb3d12212fc93851bb4e24b24ea8351b3a59aea4a0ebfd26ec4cfd2")
    val packs = listOf(NewsStickerPack.pack, StickerPack(
        id = "user-supplied-anime", title = "Supplied anime sticker", attribution = "User-supplied artwork. Creator not identified.",
        stickers = listOf(StickerAsset(
            id = "anime-drink", packId = "user-supplied-anime", label = "Anime girl drinking",
            assetPath = "stickers/user-supplied/anime-drink.webp", tags = setOf("anime", "girl", "drink", "drinking", "cup", "chibi"),
            animated = true, sourceRef = "user-provided:wamid.HBgMOTE5OTUxNjcyODQzFQIAEhggQUNBODEyOTFGOTRBNDFEMkU0RTAxMzZDNUUyMzlEOEQA",
        )),
    ))
}
