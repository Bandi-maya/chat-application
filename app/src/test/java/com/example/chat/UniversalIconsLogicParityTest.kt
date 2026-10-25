package com.example.chat

import com.example.chat.ui.icons.ChatyIconId
import com.example.chat.ui.settings.universal.UniversalIconsLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behavior parity tests for universal_chaty_icons_screen.dart logic. */
class UniversalIconsLogicParityTest {

    @Test
    fun categoriesMatchDartOrder() {
        assertEquals(
            listOf("All", "Navigation", "Messaging", "Calls", "Camera", "Status", "Settings"),
            UniversalIconsLogic.categories,
        )
    }

    @Test
    fun coreContactIconsAreTheDart14() {
        assertEquals(14, UniversalIconsLogic.coreContactIcons.size)
        assertEquals(ChatyIconId.search, UniversalIconsLogic.coreContactIcons.first())
        assertEquals(ChatyIconId.messageCheckDouble, UniversalIconsLogic.coreContactIcons.last())
        assertEquals(ChatyIconId.favourite, UniversalIconsLogic.coreContactIcons[11])
    }

    @Test
    fun categorizedIconsMatchDart() {
        assertEquals(6, UniversalIconsLogic.categorizedIcons.size)
        assertEquals(
            listOf(
                ChatyIconId.chats, ChatyIconId.updates, ChatyIconId.calls, ChatyIconId.profile,
                ChatyIconId.tasks, ChatyIconId.groups, ChatyIconId.desktop,
            ),
            UniversalIconsLogic.categorizedIcons["Navigation"],
        )
        assertEquals(13, UniversalIconsLogic.categorizedIcons["Messaging"]!!.size)
        assertEquals(6, UniversalIconsLogic.categorizedIcons["Calls"]!!.size)
        assertEquals(5, UniversalIconsLogic.categorizedIcons["Camera"]!!.size)
        assertEquals(
            listOf(
                ChatyIconId.signal, ChatyIconId.messageCheckSingle,
                ChatyIconId.messageCheckDouble, ChatyIconId.messageClock,
            ),
            UniversalIconsLogic.categorizedIcons["Status"],
        )
        assertEquals(7, UniversalIconsLogic.categorizedIcons["Settings"]!!.size)
    }

    @Test
    fun everyCategorizedIdExists() {
        for ((_, ids) in UniversalIconsLogic.categorizedIcons) {
            for (id in ids) assertTrue(ChatyIconId.entries.contains(id))
        }
    }

    @Test
    fun displayNameSplitsCamelCaseAndCapitalizes() {
        assertEquals("Chats", UniversalIconsLogic.iconDisplayName(ChatyIconId.chats))
        assertEquals("Camera Flip", UniversalIconsLogic.iconDisplayName(ChatyIconId.cameraFlip))
        assertEquals(
            "Message Check Double",
            UniversalIconsLogic.iconDisplayName(ChatyIconId.messageCheckDouble),
        )
        assertEquals("Call Audio", UniversalIconsLogic.iconDisplayName(ChatyIconId.callAudio))
    }

    @Test
    fun filterAllReturnsEveryIcon() {
        assertEquals(ChatyIconId.entries, UniversalIconsLogic.filteredIcons("All", ""))
        assertEquals(ChatyIconId.entries, UniversalIconsLogic.filteredIcons("All", "   "))
    }

    @Test
    fun filterUnknownCategoryFallsBackToAll() {
        assertEquals(ChatyIconId.entries, UniversalIconsLogic.filteredIcons("Nope", ""))
    }

    @Test
    fun filterByCategoryWithoutQuery() {
        assertEquals(
            UniversalIconsLogic.categorizedIcons["Camera"],
            UniversalIconsLogic.filteredIcons("Camera", ""),
        )
    }

    @Test
    fun filterMatchesEnumNameCaseInsensitiveTrimmed() {
        val result = UniversalIconsLogic.filteredIcons("All", "  CAMERA ")
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.name.lowercase().contains("camera") })
        assertTrue(result.contains(ChatyIconId.cameraFlip))
    }

    @Test
    fun filterWithinCategoryOnly() {
        val result = UniversalIconsLogic.filteredIcons("Camera", "camera")
        assertEquals(
            listOf(
                ChatyIconId.camera, ChatyIconId.cameraFlip,
                ChatyIconId.cameraShutter, ChatyIconId.cameraFlashOn,
            ),
            result,
        )
    }

    @Test
    fun filterNoMatchReturnsEmpty() {
        assertTrue(UniversalIconsLogic.filteredIcons("All", "zzz-no-such-icon").isEmpty())
    }

    @Test
    fun previewSizeMatchesDart() {
        assertEquals(20f, UniversalIconsLogic.previewSize(true), 0.0f)
        assertEquals(26f, UniversalIconsLogic.previewSize(false), 0.0f)
    }
}
