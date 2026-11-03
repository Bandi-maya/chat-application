package com.example.chat
import com.example.chat.core.preferences.HomePresentationPolicy
import org.junit.Assert.*
import org.junit.Test

class HomeHeaderApplicationPolicyTest {
    @Test fun separationForcesInstagramWithoutChangingStoredChoice() {
        for (saved in listOf("Circular", "Facebook", "Squircle", "Wide Card", "Instagram")) {
            assertEquals("Instagram", HomePresentationPolicy.effectiveStoriesStyle(true, saved))
            assertEquals(saved, HomePresentationPolicy.effectiveStoriesStyle(false, saved))
        }
    }
    @Test fun separationShowsStripRegardlessOfIndependentSwitchOrPreset() {
        for (enabled in listOf(false, true)) for (preset in listOf(false, true))
            assertTrue(HomePresentationPolicy.showStoriesStrip(true, enabled, preset))
    }
    @Test fun unseparatedModesRetainExistingStripLogic() {
        for (enabled in listOf(false, true)) for (preset in listOf(false, true))
            assertEquals(enabled || preset, HomePresentationPolicy.showStoriesStrip(false, enabled, preset))
    }
    @Test fun savedStyleIdentitiesAndPresentationSwapRemainUntouched() {
        assertEquals("ONE UI", HomePresentationPolicy.storedStyle("ONE UI"))
        assertEquals("Cards", HomePresentationPolicy.listStyle("Chaty Default"))
        assertEquals("Chaty Default", HomePresentationPolicy.listStyle("ONE UI"))
    }
}
