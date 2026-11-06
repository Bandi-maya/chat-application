package com.example.chat

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source wiring guard only, not pixel/Compose/device proof. */
class IosSupportedCallSourceContractTest {
    private fun source(name: String): String {
        val p = "app/src/main/kotlin/com/example/chat/ui/calls/$name"
        return listOf(File(p), File("../$p")).first { it.exists() }.readText()
    }
    @Test fun bothStableIncomingRoutesUseTheRealSliderWithoutKeyguardBranch() {
        val s = source("bundles/BundleIncomingCallView.kt")
        val body = s.substringAfter("private fun ChatyDefaultIncomingCall(")
            .substringBefore("private fun IosSecondaryAction")
        assertTrue(body.contains("AppleIosIncomingCall(tokens, session, onAccept, onDecline)"))
        assertTrue(body.contains("IosSlideToAnswerPill(tokens.acceptColor, onAccept)"))
        assertFalse(body.contains("isKeyguardLocked"))
        assertFalse(body.contains("BundleActionButton("))
        for (label in listOf("Message", "Voicemail", "FaceTime", "Keypad"))
            assertFalse(label, body.contains("\"$label\""))
    }
    @Test fun productionAudioSelectsSupportedControlsWithoutFakeVideo() {
        val s = source("OngoingCallScreen.kt")
        assertTrue(s.contains("if (!session.isVideo && iosLayout)"))
        val body = s.substringAfter("private fun IosSupportedAudioControls(")
            .substringBefore("/** Exactly four primary audio controls.")
        assertTrue(body.contains("video?.let"))
        assertTrue(body.contains("more?.let"))
        for (label in listOf("Speaker", "Mute", "End")) assertTrue(body.contains("CallControlAction(\"$label\""))
        for (label in listOf("Voicemail", "FaceTime", "Keypad", "Translation")) assertFalse(body.contains("\"$label\""))
        assertTrue(body.contains("Modifier.size(60.dp)"))
    }
    @Test fun changedSurfacesRemainTokenized() {
        for (name in listOf("OngoingCallScreen.kt", "bundles/BundleIncomingCallView.kt")) {
            val s = source(name)
            assertFalse(name, s.contains("Color(0x"))
            assertFalse(name, s.contains("Color.White"))
            assertFalse(name, s.contains("Color.Black"))
        }
    }
}
