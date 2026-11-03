package com.example.chat

import com.example.chat.ui.icons.*
import org.junit.Assert.*
import org.junit.Test

class AssetIconAvailabilityTest {
    @Test fun completeApprovedAssetPacksAreSelectableAndRoundTrip() {
        val packs = ChatyIconThemeId.entries.filter(ChatyIconPackCatalog::isAssetPack)
        assertEquals(6, packs.size)
        packs.forEach {
            assertTrue(ChatyIconPackCatalog.isSelectable(it))
            assertEquals(it, ChatyIconThemeId.fromString(it.name))
            assertEquals(it, ChatyIconThemeId.fromString(it.title))
            assertTrue(ChatyIconPackCatalog.info(it)!!.missingIcons.isEmpty())
            assertTrue(ChatyIconPackCatalog.info(it)!!.reason!!.contains("adapted"))
        }
    }
    @Test fun approvedHugeiconsAdaptationsAreSelectableAndRoundTrip() {
        assertTrue(ChatyIconPackCatalog.isSelectable(ChatyIconThemeId.hugeiconsFree))
        assertEquals(ChatyIconThemeId.hugeiconsFree, ChatyIconThemeId.fromString("Hugeicons Free"))
        assertTrue(ChatyIconPackCatalog.info(ChatyIconThemeId.hugeiconsFree)!!.reason!!.contains("adapted"))
    }
    @Test fun approvedPhosphorAdaptationsAreSelectableAndRoundTrip() {
        assertTrue(ChatyIconPackCatalog.isSelectable(ChatyIconThemeId.phosphorRegular))
        assertEquals(ChatyIconThemeId.phosphorRegular, ChatyIconThemeId.fromString("Phosphor Regular"))
        assertTrue(ChatyIconPackCatalog.info(ChatyIconThemeId.phosphorRegular)!!.reason!!.contains("adapted"))
    }
    @Test fun originalTenRemainSelectableAndRoundTrip() {
        ChatyIconThemeId.entries.take(10).forEach {
            assertTrue(ChatyIconPackCatalog.isSelectable(it))
            assertEquals(it, ChatyIconThemeId.fromString(it.title))
        }
    }
    @Test fun requestedPackNamesRemainVisibleWithoutSubstitution() {
        val titles = ChatyIconPackCatalog.blockedPacks.map { it.title }
        assertTrue(titles.containsAll(listOf("Hugeicons Pro", "Iconly Pro", "Nucleo", "Anron", "Streamline", "Untitled UI", "Icons8", "Lordicon")))
        assertTrue(ChatyIconPackCatalog.blockedPacks.none { it.available })
    }
}
