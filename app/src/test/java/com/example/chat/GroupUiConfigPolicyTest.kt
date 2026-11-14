package com.example.chat

import com.example.chat.core.config.*
import org.junit.Assert.*
import org.junit.Test

class GroupUiConfigPolicyTest {
    private val valid = """{"schema_version":1,"revision":"r1","screens":{"group_creation":{"title":"New group","name_label":"Subject","name_max_length":100,"member_selection":{"order":["search","selected","contacts"],"selected_display":"chips","show_selected_count":true},"labels":{"next":"Next","create":"Create","search":"Search"}}}}"""
    @Test fun strictBoundedSchema() {
        assertNotNull(GroupUiConfigPolicy.parse(valid))
        assertNull(GroupUiConfigPolicy.parse(valid.replace("\"schema_version\":1", "\"schema_version\":2")))
        assertNull(GroupUiConfigPolicy.parse(valid.replace("\"name_max_length\":100", "\"name_max_length\":101")))
        assertNull(GroupUiConfigPolicy.parse(valid.replace("\"search\",\"selected\",\"contacts\"", "\"search\",\"search\",\"contacts\"")))
        assertNull(GroupUiConfigPolicy.parse(valid.replace("New group", "<script>")))
        assertNull(GroupUiConfigPolicy.parse(" ".repeat(8193)))
    }
    @Test fun explicitChoicesSurviveNewServerDefaultsWithoutBeingReset() {
        val chosen = GroupUiUserChoices(listOf("selected", "search", "contacts"), "list", false)
        val a = chosen.applyTo(GroupUiConfig(selectedDisplay = "chips", showSelectedCount = true))
        val b = chosen.applyTo(GroupUiConfig(title = "Team", selectedDisplay = "chips", showSelectedCount = true))
        assertEquals(a.selectedDisplay, b.selectedDisplay); assertEquals(a.order, b.order)
        assertFalse(b.showSelectedCount); assertEquals("Team", b.title)
    }
}
