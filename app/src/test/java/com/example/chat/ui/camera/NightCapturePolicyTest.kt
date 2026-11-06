package com.example.chat.ui.camera
import org.junit.Assert.*
import org.junit.Test
class NightCapturePolicyTest {
 @Test fun onlyRequestedAndAvailableUsesOemNight(){assertFalse(shouldUseNight(false,false));assertFalse(shouldUseNight(false,true));assertFalse(shouldUseNight(true,false));assertTrue(shouldUseNight(true,true))}
}
