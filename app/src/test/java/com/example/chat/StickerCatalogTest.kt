package com.example.chat

import com.example.chat.core.stickers.*
import org.junit.Assert.*
import org.junit.Test

class StickerCatalogTest {
    private val happy = StickerAsset("a", "faces", "Happy cat", "stickers/faces/a.webp", setOf("smile", "joy"), true, "https://example.invalid/a")
    private val sad = happy.copy(id = "b", label = "Sad cat", assetPath = "stickers/faces/b.webp", tags = setOf("cry", "tears"))
    private val packs = listOf(StickerPack("faces", "Cat faces", "Actual source credit", listOf(happy, sad)))
    @Test fun emptySearchKeepsGroupedPacks() { assertEquals(packs, StickerCatalog.search(packs, "")) }
    @Test fun tagSearchFindsRelatedArt() { assertEquals(listOf(happy), StickerCatalog.search(packs, "JOY").single().stickers) }
    @Test fun multiwordSearchRequiresAllWords() { assertEquals(listOf(sad), StickerCatalog.search(packs, "cat cry").single().stickers) }
    @Test fun unknownQueryDoesNotInventOnlineMatches() { assertTrue(StickerCatalog.search(packs, "dog").isEmpty()) }
    @Test fun searchKeepsPackIdentity() { assertEquals("faces", StickerCatalog.search(packs, "happy").single().id) }
    @Test fun prefixQueryFindsName() { assertEquals(listOf(happy), StickerCatalog.search(packs, "hap").single().stickers) }
    @Test fun duplicateIdsRejected() { assertFalse(StickerCatalog.validate(listOf(packs.single().copy(stickers = listOf(happy, happy))))) }
    @Test fun mixedPackMembershipRejected() { assertFalse(StickerCatalog.validate(listOf(packs.single().copy(stickers = listOf(happy.copy(packId = "other")))))) }
    @Test fun pathTraversalRejected() { assertFalse(StickerCatalog.validate(listOf(packs.single().copy(stickers = listOf(happy.copy(assetPath = "stickers/../secret")))))) }
    @Test fun unicodeEmojiNotRequiredForStickerIdentity() { assertTrue(StickerCatalog.validate(packs)); assertEquals("a", happy.id) }
}
