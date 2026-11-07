package com.example.chat
import com.example.chat.core.stickers.*
import org.junit.Assert.*
import org.junit.Test
class NewsStickerPackTest {
 @Test fun catalogKeepsAllStableIdentitiesAndHonestLabels() {
  val pack = NewsStickerPack.pack
  assertTrue(StickerCatalog.validate(BundledStickerPacks.packs))
  assertEquals(100, pack.stickers.size)
  assertEquals(100, NewsStickerPack.expectedSha256.size)
  assertEquals(9, pack.stickers.count { it.label.contains("[HELD]") })
  assertEquals((1..100).map { "News-%03d".format(it) }.toSet(), pack.stickers.map { it.id }.toSet())
  assertTrue(pack.stickers.all { it.animated && NewsStickerPack.expectedSha256[it.id]?.length == 64 })
  assertTrue(pack.title.contains("approximate"))
  assertTrue(BundledStickerPacks.packs.any { it.id == "user-supplied-anime" })
 }
}
