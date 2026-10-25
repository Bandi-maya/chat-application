package com.example.chat.settings

import com.example.chat.ui.settings.templates.APPEARANCE_SAVE_ERROR
import com.example.chat.ui.settings.templates.appearanceOverviewChips
import com.example.chat.ui.settings.templates.callsIslandLabel
import com.example.chat.ui.settings.templates.centerActionLabel
import com.example.chat.ui.settings.templates.chatListAvatarRadiusDp
import com.example.chat.ui.settings.templates.chatListRowPaddingDp
import com.example.chat.ui.settings.templates.chatListSummary
import com.example.chat.ui.settings.templates.conversationSummary
import com.example.chat.ui.settings.templates.homeRowHeightDp
import com.example.chat.ui.settings.templates.navigationDestinationLabel
import com.example.chat.ui.settings.templates.navigationSummary
import com.example.chat.ui.settings.templates.overrideApplySuccess
import com.example.chat.ui.settings.templates.overrideBadge
import com.example.chat.ui.settings.templates.overrideErrorText
import com.example.chat.ui.settings.templates.overrideResetSuccess
import com.example.chat.ui.settings.templates.profileBannerHeightDp
import com.example.chat.ui.settings.templates.updatesSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComponentOverrideHelpersTest {

    @Test fun errorTextStripsBadStatePrefix() {
        assertEquals("boom", overrideErrorText(IllegalStateException("boom")))
        assertEquals("bad arg", overrideErrorText(IllegalArgumentException("bad arg")))
        // Parity: a carried-over Dart-style prefix is stripped too.
        assertEquals("x", overrideErrorText(RuntimeException("Bad state: x")))
    }

    @Test fun badgeOnlyWhenUsed() {
        assertNull(overrideBadge(isCurrentlyUsed = false, isFromBase = false))
        assertEquals("Base Template", overrideBadge(isCurrentlyUsed = true, isFromBase = true))
        assertEquals("Active Override", overrideBadge(isCurrentlyUsed = true, isFromBase = false))
    }

    @Test fun snackbarTexts() {
        assertEquals("Applied Stream to Composer only.", overrideApplySuccess("Stream", "Composer"))
        assertEquals("Reset Navigation to the base template.", overrideResetSuccess("Navigation"))
    }

    @Test fun destinationLabels() {
        assertEquals("Chats", navigationDestinationLabel("chats"))
        assertEquals("Groups", navigationDestinationLabel("groups"))
        assertEquals("Updates", navigationDestinationLabel("updates"))
        assertEquals("Tasks", navigationDestinationLabel("tasks"))
        assertEquals("Calls", navigationDestinationLabel("calls"))
        assertEquals("Settings", navigationDestinationLabel("settings"))
        assertEquals("Devices", navigationDestinationLabel("desktop"))
        assertEquals("unknown_id", navigationDestinationLabel("unknown_id"))
    }

    @Test fun summaries() {
        assertEquals("floatingPill · 68 dp", navigationSummary("floatingPill", 68.0))
        assertEquals("Center camera action", centerActionLabel("camera"))
        assertEquals("Center quick action", centerActionLabel("compose"))
        assertEquals("Bubble: Rounded · Ticks: iOS 11", conversationSummary("Rounded", "iOS 11"))
        assertEquals("compact · 56 dp rows", chatListSummary("compact", 56.0))
        assertEquals("circularRail · audio on", updatesSummary("circularRail", true))
        assertEquals("minimalList · audio off", updatesSummary("minimalList", false))
        assertEquals("Island on", callsIslandLabel(true))
        assertEquals("Island off", callsIslandLabel(false))
    }

    @Test fun previewDimensions() {
        assertEquals(3, chatListRowPaddingDp("compact"))
        assertEquals(8, chatListRowPaddingDp("comfortable"))
        assertEquals(5, chatListRowPaddingDp("regular"))
        assertEquals(8, chatListAvatarRadiusDp("squircle"))
        assertEquals(5, chatListAvatarRadiusDp("roundedSquare"))
        assertEquals(99, chatListAvatarRadiusDp("circle"))
        assertEquals(20, homeRowHeightDp("Compact"))
        assertEquals(27, homeRowHeightDp("Stories First"))
        assertEquals(26, profileBannerHeightDp("compactHeader"))
        assertEquals(44, profileBannerHeightDp("bannerWithAvatar"))
    }

    @Test fun appearanceChips() {
        assertEquals(
            listOf("Type: Compact", "In: Fade", "Out: None"),
            appearanceOverviewChips("Compact", "Fade", "None"),
        )
        assertEquals(
            "Could not save this appearance setting. Your previous choice was kept.",
            APPEARANCE_SAVE_ERROR,
        )
    }
}
