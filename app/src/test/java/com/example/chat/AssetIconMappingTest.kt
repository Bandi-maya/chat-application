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

    @Test fun fiveNewSemanticIdsMapToGenuineResourcesInAllSixPacks() {
        val expected: Map<ChatyIconThemeId, Map<ChatyIconId, Int>> = mapOf(
            ChatyIconThemeId.hugeiconsFree to mapOf(
                ChatyIconId.mail to R.drawable.ic_hgf_mail,
                ChatyIconId.eye to R.drawable.ic_hgf_eye,
                ChatyIconId.eyeOff to R.drawable.ic_hgf_eye_off,
                ChatyIconId.workspace to R.drawable.ic_hgf_workspace,
                ChatyIconId.info to R.drawable.ic_hgf_info,
            ),
            ChatyIconThemeId.majesticonsLine to mapOf(
                ChatyIconId.mail to R.drawable.ic_mjl_mail,
                ChatyIconId.eye to R.drawable.ic_mjl_eye,
                ChatyIconId.eyeOff to R.drawable.ic_mjl_eye_off,
                ChatyIconId.workspace to R.drawable.ic_mjl_workspace,
                ChatyIconId.info to R.drawable.ic_mjl_info,
            ),
            ChatyIconThemeId.majesticonsSolid to mapOf(
                ChatyIconId.mail to R.drawable.ic_mjs_mail,
                ChatyIconId.eye to R.drawable.ic_mjs_eye,
                ChatyIconId.eyeOff to R.drawable.ic_mjs_eye_off,
                ChatyIconId.workspace to R.drawable.ic_mjs_workspace,
                ChatyIconId.info to R.drawable.ic_mjs_info,
            ),
            ChatyIconThemeId.pikaiconsStroke to mapOf(
                ChatyIconId.mail to R.drawable.ic_pks_mail,
                ChatyIconId.eye to R.drawable.ic_pks_eye,
                ChatyIconId.eyeOff to R.drawable.ic_pks_eye_off,
                ChatyIconId.workspace to R.drawable.ic_pks_workspace,
                ChatyIconId.info to R.drawable.ic_pks_info,
            ),
            ChatyIconThemeId.pikaiconsSolid to mapOf(
                ChatyIconId.mail to R.drawable.ic_pkf_mail,
                ChatyIconId.eye to R.drawable.ic_pkf_eye,
                ChatyIconId.eyeOff to R.drawable.ic_pkf_eye_off,
                ChatyIconId.workspace to R.drawable.ic_pkf_workspace,
                ChatyIconId.info to R.drawable.ic_pkf_info,
            ),
            ChatyIconThemeId.phosphorRegular to mapOf(
                ChatyIconId.mail to R.drawable.ic_phr_mail,
                ChatyIconId.eye to R.drawable.ic_phr_eye,
                ChatyIconId.eyeOff to R.drawable.ic_phr_eye_off,
                ChatyIconId.workspace to R.drawable.ic_phr_workspace,
                ChatyIconId.info to R.drawable.ic_phr_info,
            ),
        )
        assertEquals(ChatyIconThemeId.entries.filter(ChatyIconPackCatalog::isAssetPack).toSet(), expected.keys)
        for ((theme, ids) in expected) {
            assertTrue("$theme must stay selectable", ChatyIconPackCatalog.isSelectable(theme))
            assertEquals("$theme new mappings must be distinct (fail-closed, no collapse)", 5, ids.values.toSet().size)
            for ((id, res) in ids) {
                assertEquals("${theme.title}:${id.name}", res, ChatyAssetIcons.drawable(theme, id))
            }
        }
    }
}
