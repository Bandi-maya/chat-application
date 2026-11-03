package com.example.chat

import com.example.chat.core.preferences.HomePresentationPolicy
import org.junit.Assert.*
import org.junit.Test

class HomePresentationPolicyTest {
    @Test fun legacyGbWritesPreserveExplicitStyleIds() {
        listOf("Chaty Default", "ONE UI", "WhatsApp UI Stock", "IOS STYLE", "Telegram Style", "Modern Side Menu").forEach {
            assertEquals(it, HomePresentationPolicy.storedStyle(it))
        }
    }
    @Test fun caseInsensitiveExplicitStyleDoesNotCollapse() {
        assertEquals("WhatsApp UI Stock", HomePresentationPolicy.storedStyle(" whatsapp UI stock "))
    }
    @Test fun existingCanonicalCardsRemainsCards() { assertEquals("Cards", HomePresentationPolicy.storedStyle("Cards")) }

    @Test fun defaultUsesOldOneUiPresentation() {
        listOf("Chaty Default", "chaty", "default").forEach {
            assertEquals("Cards", HomePresentationPolicy.listStyle(it))
            assertEquals("ONE UI", HomePresentationPolicy.navigationStyle(it))
        }
    }
    @Test fun oneUiUsesOldDefaultPresentation() {
        listOf("ONE UI", "Cards").forEach {
            assertEquals("Chaty Default", HomePresentationPolicy.listStyle(it))
            assertEquals("WhatsApp UI Stock", HomePresentationPolicy.navigationStyle(it))
        }
    }
    @Test fun whatsappStockDoesNotFollowDefaultSwap() {
        assertEquals("Chaty Default", HomePresentationPolicy.listStyle("WhatsApp UI Stock"))
        assertEquals("WhatsApp UI Stock", HomePresentationPolicy.navigationStyle("WhatsApp UI Stock"))
    }
    @Test fun otherNavigationStylesUnchanged() {
        listOf("IOS STYLE", "Telegram Style", "BUBBLES TAB STYLE", "BASIC TAB STYLE", "WhatsApp OLD UI", "Floating Rail", "Modern Side Menu").forEach {
            assertEquals(it, HomePresentationPolicy.navigationStyle(it))
        }
    }
    @Test fun modernSideMenuCardRowsNotSwapped() { assertEquals("Cards", HomePresentationPolicy.listStyle("Modern Side Menu")) }
}
