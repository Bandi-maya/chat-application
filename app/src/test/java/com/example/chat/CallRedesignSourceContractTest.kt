package com.example.chat

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Narrow source regression. Does not replace Compose/device/pixel or signaling tests. */
class CallRedesignSourceContractTest {
 private fun source(path:String): String = listOf(File(path),File("../$path")).first { it.exists() }.readText()
 @Test fun newDefaultUsesAppleWhileExplicitChoicesRemain() {
  val s=source("app/src/main/kotlin/com/example/chat/ui/calls/bundles/BundleIncomingCallView.kt")
  assertTrue(s.contains("CallBundleId.CHATY_DEFAULT -> ChatyDefaultIncomingCall"))
  assertTrue(s.contains("CallBundleId.APPLE_IOS -> AppleIosIncomingCall"))
  assertFalse(s.contains("CallBundleId.CHATY_DEFAULT -> AppleIosIncomingCall"))
  for(id in listOf("APPLE_IOS","SAMSUNG_ONE_UI","GOOGLE_PHONE_LATEST","GOOGLE_PHONE_CLASSIC","VIVO_IQOO","AZURE_RING_VERTICAL","MONO_CHEVRON_DARK","NEXTCLOUD_TALK")) assertTrue(id,s.contains("CallBundleId.$id ->"))
  assertFalse(s.contains("isLockedScreenSimulation = true"))
  val registry=source("app/src/main/kotlin/com/example/chat/ui/calls/bundles/CallExperienceBundle.kt")
  val old=registry.substringAfter("    CHATY_DEFAULT(").substringBefore("    APPLE_IOS(")
  val fresh=registry.substringAfter("    APPLE_IOS(").substringBefore("    SAMSUNG_ONE_UI(")
  assertTrue(old.contains("id = \"chaty_default\""));assertTrue(old.contains("displayName = \"Apple iOS Call Experience\""))
  assertTrue(fresh.contains("id = \"apple_ios\""));assertTrue(fresh.contains("displayName = \"Chaty Default\""));assertTrue(fresh.contains("badge = \"Default\""))
  assertTrue(registry.contains("?: APPLE_IOS"))
  val prefs=source("app/src/main/kotlin/com/example/chat/ui/calls/CallPresentation.kt")
  assertTrue(prefs.contains("val callExperienceBundleId: String = \"apple_ios\""))
 }
 @Test fun nonIosRetainsFourAudioControlsAndIosUsesSupportedOnlyLayout() {
  val s=source("app/src/main/kotlin/com/example/chat/ui/calls/OngoingCallScreen.kt");val controls=s.substringAfter("private fun AudioFourControls")
  assertEquals(4,Regex("CallControlAction\\(").findAll(controls).count())
  for(label in listOf("Mute","Speaker","Video","Hang up")) assertTrue(controls.contains("CallControlAction(\"$label\""))
  // New iOS-reference request supersedes the older four-primary layout only
  // for the two stable iOS-labelled IDs. Non-iOS production still uses it.
  assertTrue(s.contains("bundleId == com.example.chat.ui.calls.bundles.CallBundleId.APPLE_IOS ||"))
  assertTrue(s.contains("bundleId == com.example.chat.ui.calls.bundles.CallBundleId.CHATY_DEFAULT"))
  assertTrue(s.contains("if (!session.isVideo && iosLayout)"))
  assertTrue(s.contains("} else if (!session.isVideo) {\n                    AudioFourControls"))
  val ios=s.substringAfter("private fun IosSupportedAudioControls(").substringBefore("/** Exactly four primary audio controls.")
  for(label in listOf("Speaker","Mute","End")) assertTrue(ios.contains("CallControlAction(\"$label\""))
  assertTrue(ios.contains("video?.let"));assertTrue(ios.contains("more?.let"))
  for(label in listOf("FaceTime","Voicemail","Keypad","Translation")) assertFalse(ios.contains("\"$label\""))
  for(f in listOf("OngoingCallScreen.kt","InAppCallPip.kt","bundles/BundleIncomingCallView.kt")) {
   val body=source("app/src/main/kotlin/com/example/chat/ui/calls/$f")
   assertFalse(f,body.contains("Color(0x"));assertFalse(f,body.contains("Color.White"));assertFalse(f,body.contains("Color.Black"))
  }
 }
 @Test fun audioControlsImportTheirExtensionSymbols() {
  val s=source("app/src/main/kotlin/com/example/chat/ui/calls/OngoingCallScreen.kt")
  assertTrue(s.contains("import androidx.compose.material.icons.rounded.Call\n"))
  assertTrue(s.contains("import androidx.compose.foundation.layout.widthIn\n"))
 }
 @Test fun previewIsSeparateAndProductionCapabilityRemainsDenied() {
  val p=source("app/src/main/kotlin/com/example/chat/ui/calls/IncomingLocalCameraPreview.kt")
  assertTrue(p.contains("Preview.Builder()"));assertFalse(p.contains("org.webrtc"));assertFalse(p.contains("VideoCapture"));assertFalse(p.contains("unbindAll"))
  val rtc=source("app/src/main/java/com/example/chat/data/services/WebRtcCallMediaController.kt")
  assertTrue(rtc.contains("override val supportsVideoUpgrade: Boolean get() = false"))
  val service=source("app/src/main/java/com/example/chat/data/services/CallSignalingService.kt")
  assertTrue(service.contains("videoUpgradeCapability: (String, String) -> Boolean = { _, _ -> false }"))
 }
}
