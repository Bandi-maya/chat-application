package com.example.chat

import com.example.chat.ui.icons.ChatyIconId
import com.example.chat.ui.icons.ChatyMaterialIconRoles
import org.junit.Assert.*
import org.junit.Test

class MaterialIconRoleBridgeTest {
    @Test fun `known names resolve independently of Material family`() {
        assertEquals(ChatyIconId.reply, ChatyMaterialIconRoles.resolve("AutoMirrored.Rounded.Reply"))
        assertEquals(ChatyIconId.eyeOff, ChatyMaterialIconRoles.resolve("Outlined.VisibilityOff"))
        assertEquals(ChatyIconId.archive, ChatyMaterialIconRoles.resolve("Filled.Archive"))
        assertEquals(ChatyIconId.pin, ChatyMaterialIconRoles.resolve("Rounded.PushPin"))
        assertEquals(ChatyIconId.notifications, ChatyMaterialIconRoles.resolve("Rounded.Notifications"))
        assertEquals(ChatyIconId.groupAdd, ChatyMaterialIconRoles.resolve("Rounded.GroupAdd"))
        assertEquals(ChatyIconId.waveform, ChatyMaterialIconRoles.resolve("Rounded.GraphicEq"))
        assertEquals(ChatyIconId.stories, ChatyMaterialIconRoles.resolve("Outlined.AutoStories"))
        assertEquals(ChatyIconId.pinCode, ChatyMaterialIconRoles.resolve("Rounded.Pin"))
        assertEquals(ChatyIconId.organizationAdd, ChatyMaterialIconRoles.resolve("Rounded.DomainAdd"))
        assertEquals(ChatyIconId.block, ChatyMaterialIconRoles.resolve("Rounded.Block"))
        assertEquals(ChatyIconId.selectAll, ChatyMaterialIconRoles.resolve("Rounded.SelectAll"))
    }
    @Test fun `unknown names never turn into wrong meaning glyphs`() {
        assertEquals(ChatyIconId.fingerprint, ChatyMaterialIconRoles.resolve("Rounded.Fingerprint"))
        assertNull(ChatyMaterialIconRoles.resolve("Rounded.FutureUnmappedRole"))
        assertNull(ChatyMaterialIconRoles.resolve(""))
    }
}
