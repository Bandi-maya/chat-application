package com.example.chat

import com.example.chat.ui.icons.*
import org.junit.Assert.*
import org.junit.Test

class AssetIconMappingTest {
    @Test fun hugeiconsHasEverySemanticMapping() {
        ChatyIconId.entries.forEach { assertNotNull(it.name, ChatyAssetIcons.drawable(ChatyIconThemeId.hugeiconsFree, it)) }
    }
    @Test fun phosphorHasEverySemanticMapping() {
        ChatyIconId.entries.forEach { assertNotNull(it.name, ChatyAssetIcons.drawable(ChatyIconThemeId.phosphorRegular, it)) }
    }
    @Test fun iosSelectedUsesNativeFamilyFillAndDoesNotChangeOtherFamilies() {
        val nav = listOf(ChatyIconId.chats, ChatyIconId.updates, ChatyIconId.calls, ChatyIconId.profile,
            ChatyIconId.tasks, ChatyIconId.groups, ChatyIconId.desktop, ChatyIconId.more, ChatyIconId.settings)
        nav.forEach {
            assertNotEquals(ChatyAssetIcons.drawable(ChatyIconThemeId.phosphorRegular, it), ChatyAssetIcons.drawable(ChatyIconThemeId.phosphorRegular, it, true))
            assertEquals(ChatyAssetIcons.drawable(ChatyIconThemeId.hugeiconsFree, it), ChatyAssetIcons.drawable(ChatyIconThemeId.hugeiconsFree, it, true))
        }
    }
    @Test fun allSixFamilyMapsCoverEverySemanticId() {
        ChatyIconThemeId.entries.filter(ChatyIconPackCatalog::isAssetPack).forEach { theme ->
            ChatyIconId.entries.forEach { assertNotNull("${theme.title}:${it.name}", ChatyAssetIcons.drawable(theme, it)) }
        }
    }
    @Test fun proceduralThemeNeverGetsAssetSubstitute() {
        assertNull(ChatyAssetIcons.drawable(ChatyIconThemeId.chatySigil, ChatyIconId.chats))
    }
}
