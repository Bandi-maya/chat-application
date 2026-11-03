package com.example.chat.home
import org.junit.Assert.*
import org.junit.Test
import com.example.chat.core.templates.*
import com.example.chat.ui.chats.home.resolveHomeTemplates

class ReferenceRowPolicyTest {
    @Test fun untouchedDefaultUsesReferenceMetrics() {
        val t = resolveHomeTemplates(UserTemplateConfiguration(), true, ChatyTemplateRegistry.messageFirst.chatList).chatList
        assertTrue(t.referenceDefault)
        assertEquals(72.0, t.itemHeight, 0.0)
        assertEquals(com.example.chat.ui.chats.home.ChatListDensity.regular, t.density)
        assertTrue(t.showDivider)
    }
    @Test fun otherPresentationKeepsLegacyBaseline() {
        val t = resolveHomeTemplates(UserTemplateConfiguration(), false, ChatyTemplateRegistry.messageFirst.chatList).chatList
        assertFalse(t.referenceDefault)
        assertEquals(76.0, t.itemHeight, 0.0)
        assertFalse(t.showDivider)
    }
    @Test fun everyExplicitRowComponentWins() {
        for (id in ChatyTemplateId.values()) {
            if (id == ChatyTemplateId.messageFirst) continue
            val config = UserTemplateConfiguration(componentOverrides = mapOf(TemplateComponentType.chatList to id))
            val row = ChatyTemplateRegistry.get(id).chatList
            val actual = resolveHomeTemplates(config, true, row).chatList
            assertFalse(actual.referenceDefault)
            assertEquals(row.itemHeight, actual.itemHeight, 0.0)
            assertEquals(row.showDivider, actual.showDivider)
            assertEquals(row.avatarShape, actual.avatarShape)
            assertEquals(row.density.name, actual.density.name)
        }
    }
    @Test fun explicitBaseTemplateWinsIncludingMessageFirstRowOverride() {
        for (id in ChatyTemplateId.values()) {
            if (id == ChatyTemplateId.messageFirst) continue
            val row = ChatyTemplateRegistry.get(id).chatList
            assertFalse(resolveHomeTemplates(UserTemplateConfiguration(baseTemplate=id), true, row).chatList.referenceDefault)
            val config = UserTemplateConfiguration(baseTemplate=id, componentOverrides=mapOf(TemplateComponentType.chatList to ChatyTemplateId.messageFirst))
            val actual = resolveHomeTemplates(config,true,ChatyTemplateRegistry.messageFirst.chatList).chatList
            assertEquals(68.0,actual.itemHeight,0.0)
            assertFalse(actual.referenceDefault)
        }
    }
}
