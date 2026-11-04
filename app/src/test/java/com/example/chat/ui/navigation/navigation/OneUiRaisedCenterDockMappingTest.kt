package com.example.chat.ui.navigation

import com.example.chat.core.theme.AppNavigationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression contract for the raised-centre One UI dock: it applies ONLY to the
 *  explicit One UI / Cards pick - never to the Default presentation or stock
 *  WhatsApp UI - and it changes ONLY the dock inside BottomShell. The shell
 *  renderer (and with it header ownership, collapse and title treatment on
 *  non-chat destinations) must stay exactly what the presentation mapping says. */
class OneUiRaisedCenterDockMappingTest {

    @Test fun defaultKeepsCurrentRendererWithoutRaisedDock() {
        // Chaty Default presents through the oneUi shell mode (HomePresentationPolicy
        // maps it there) but must NOT get the raised-centre dock: the flag stays off
        // and referenceDefaultPresentation gates the button/split row inside the shell.
        assertEquals(AppNavigationMode.oneUi, HomePresetNormalizer.presentationNavigationMode("Chaty Default"))
        assertFalse(HomePresetNormalizer.oneUiRaisedCenterDockEnabled("Chaty Default"))
        assertFalse(HomePresetNormalizer.oneUiRaisedCenterDockEnabled(null))
        assertEquals("Floating Pill",
            HomePresetNormalizer.resolvedBottomDockVariant("Floating Pill", oneUiRaisedCenterDock = false))
    }

    @Test fun explicitOneUiKeepsBottomNavRendererAndOnlySwitchesDock() {
        // Renderer/header preservation: explicit picks keep their existing
        // presentation mapping (bottomNav via the stock label), so BottomShell and
        // its header behavior are untouched; ONLY the dock variant inside
        // BottomShell switches to the raised-centre scrolling dock.
        assertEquals(AppNavigationMode.bottomNav, HomePresetNormalizer.presentationNavigationMode("Cards"))
        assertEquals(AppNavigationMode.bottomNav, HomePresetNormalizer.presentationNavigationMode("ONE UI"))
        assertTrue(HomePresetNormalizer.oneUiRaisedCenterDockEnabled("Cards"))
        assertTrue(HomePresetNormalizer.oneUiRaisedCenterDockEnabled("ONE UI"))
        assertTrue(HomePresetNormalizer.oneUiRaisedCenterDockEnabled("one ui"))
        assertEquals(HomePresetNormalizer.RAISED_CENTER_SCROLL_DOCK,
            HomePresetNormalizer.resolvedBottomDockVariant("Floating Pill", oneUiRaisedCenterDock = true))
    }

    @Test fun stockWhatsAppUiUnchanged() {
        // Stock maps to bottomNav (BottomShell), never arms the flag, and keeps its
        // configured named dock style.
        assertEquals(AppNavigationMode.bottomNav, HomePresetNormalizer.presentationNavigationMode("WhatsApp UI Stock"))
        assertFalse(HomePresetNormalizer.oneUiRaisedCenterDockEnabled("WhatsApp UI Stock"))
        assertEquals("Floating Pill",
            HomePresetNormalizer.resolvedBottomDockVariant("Floating Pill",
                HomePresetNormalizer.oneUiRaisedCenterDockEnabled("WhatsApp UI Stock")))
    }
}
