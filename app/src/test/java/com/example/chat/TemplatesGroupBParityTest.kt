package com.example.chat

import com.example.chat.core.templates.ChatyTemplateId
import com.example.chat.core.templates.TemplateComponentType
import com.example.chat.core.templates.UserTemplateConfiguration
import com.example.chat.ui.settings.templates.TemplateProfileCodec
import com.example.chat.ui.settings.templates.templatePreviewAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TemplatePreviewAccentParityTest {
    @Test fun accentsMatchDartSwitch() {
        assertEquals(0xFFE45D9C, templatePreviewAccent(ChatyTemplateId.visualSocial))
        assertEquals(0xFF12A7B8, templatePreviewAccent(ChatyTemplateId.stream))
        assertEquals(0xFFF0A43A, templatePreviewAccent(ChatyTemplateId.cameraFirst))
        assertEquals(0xFF4388F5, templatePreviewAccent(ChatyTemplateId.messageFirst))
        assertEquals(0xFF20A985, templatePreviewAccent(ChatyTemplateId.powerChat))
        assertEquals(0xFF8067E8, templatePreviewAccent(ChatyTemplateId.community))
    }
}

class TemplateProfileCodecParityTest {

    private fun assertInvalid(payload: String) {
        try {
            TemplateProfileCodec.decode(payload)
            fail("expected ProfileFormatException for: $payload")
        } catch (e: TemplateProfileCodec.ProfileFormatException) {
            // expected
        }
    }

    @Test fun encodeShapeMatchesDart() {
        val encoded = TemplateProfileCodec.encode(UserTemplateConfiguration())
        val obj = org.json.JSONObject(encoded)
        assertEquals("chaty_template_profile", obj.getString("kind"))
        assertEquals(1, obj.getInt("schemaVersion"))
        val cfg = obj.getJSONObject("configuration")
        assertEquals(1, cfg.getInt("version"))
        assertEquals("message_first", cfg.getString("base"))
    }

    @Test fun roundTripDefaultAndFullyCustomized() {
        assertEquals(
            UserTemplateConfiguration(),
            TemplateProfileCodec.decode(TemplateProfileCodec.encode(UserTemplateConfiguration())),
        )
        val custom = UserTemplateConfiguration(
            baseTemplate = ChatyTemplateId.powerChat,
            componentOverrides = mapOf(
                TemplateComponentType.navigation to ChatyTemplateId.stream,
                TemplateComponentType.updates to ChatyTemplateId.cameraFirst,
            ),
            navigationPrimaryDestinationIds = listOf("chats", "tasks"),
            navigationOverflowDestinationIds = listOf("groups", "updates", "calls", "settings", "desktop"),
        )
        assertEquals(custom, TemplateProfileCodec.decode(TemplateProfileCodec.encode(custom)))
    }

    @Test fun importValidationChainMatchesDart() {
        assertInvalid("not json")
        assertInvalid("{}")
        assertInvalid("""{"kind":"other","schemaVersion":1,"configuration":{}}""")
        assertInvalid("""{"kind":"chaty_template_profile","schemaVersion":2,"configuration":{}}""")
        assertInvalid("""{"kind":"chaty_template_profile","schemaVersion":1}""")
        assertInvalid("""{"kind":"chaty_template_profile","schemaVersion":1,"configuration":{"base":"nope"}}""")
        assertInvalid("""{"kind":"chaty_template_profile","schemaVersion":1,"configuration":{"base":"stream","overrides":[]}}""")
        assertInvalid("""{"kind":"chaty_template_profile","schemaVersion":1,"configuration":{"base":"stream","overrides":{"bogus":"stream"}}}""")
        assertInvalid("""{"kind":"chaty_template_profile","schemaVersion":1,"configuration":{"base":"stream","overrides":{"navigation":"nope"}}}""")
        // One-sided navigation lists.
        assertInvalid("""{"kind":"chaty_template_profile","schemaVersion":1,"configuration":{"base":"stream","navPrimary":["chats"]}}""")
        assertInvalid("""{"kind":"chaty_template_profile","schemaVersion":1,"configuration":{"base":"stream","navOverflow":["tasks"]}}""")
        // Non-array navigation lists.
        assertInvalid("""{"kind":"chaty_template_profile","schemaVersion":1,"configuration":{"base":"stream","navPrimary":"chats","navOverflow":["tasks"]}}""")
        // Non-string entries drop out via whereType, then length mismatch fails.
        assertInvalid("""{"kind":"chaty_template_profile","schemaVersion":1,"configuration":{"base":"stream","navPrimary":["chats",7],"navOverflow":["groups","updates","tasks","calls","settings","desktop"]}}""")
        // Empty / oversized primary.
        assertInvalid("""{"kind":"chaty_template_profile","schemaVersion":1,"configuration":{"base":"stream","navPrimary":[],"navOverflow":["chats","groups","updates","tasks","calls","settings","desktop"]}}""")
        assertInvalid("""{"kind":"chaty_template_profile","schemaVersion":1,"configuration":{"base":"stream","navPrimary":["chats","groups","updates","tasks","calls"],"navOverflow":["settings","desktop"]}}""")
        // Duplicates within a list.
        assertInvalid("""{"kind":"chaty_template_profile","schemaVersion":1,"configuration":{"base":"stream","navPrimary":["chats","chats"],"navOverflow":["groups","updates","tasks","calls","settings","desktop"]}}""")
        // Overlap between lists.
        assertInvalid("""{"kind":"chaty_template_profile","schemaVersion":1,"configuration":{"base":"stream","navPrimary":["chats","tasks"],"navOverflow":["tasks","groups","updates","calls","settings","desktop"]}}""")
        // Unknown destination id.
        assertInvalid("""{"kind":"chaty_template_profile","schemaVersion":1,"configuration":{"base":"stream","navPrimary":["chats","bogus"],"navOverflow":["groups","updates","tasks","calls","settings","desktop"]}}""")
        // Not every destination assigned exactly once (6 of 7).
        assertInvalid("""{"kind":"chaty_template_profile","schemaVersion":1,"configuration":{"base":"stream","navPrimary":["chats"],"navOverflow":["groups","updates","tasks","calls","settings"]}}""")
    }

    @Test fun validNavigationListsImport() {
        val decoded = TemplateProfileCodec.decode(
            """{"kind":"chaty_template_profile","schemaVersion":1,"configuration":{"version":1,"base":"community","overrides":{"composer":"power_chat"},"navPrimary":["chats","updates","calls","tasks"],"navOverflow":["groups","settings","desktop"]}}""",
        )
        assertEquals(ChatyTemplateId.community, decoded.baseTemplate)
        assertEquals(ChatyTemplateId.powerChat, decoded.componentOverrides[TemplateComponentType.composer])
        assertEquals(listOf("chats", "updates", "calls", "tasks"), decoded.navigationPrimaryDestinationIds)
        assertEquals(listOf("groups", "settings", "desktop"), decoded.navigationOverflowDestinationIds)
        assertTrue(decoded.hasNavigationDestinationOverride)
    }
}
