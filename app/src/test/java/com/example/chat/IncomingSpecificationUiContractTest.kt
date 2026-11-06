package com.example.chat
import java.io.File
import org.junit.Assert.*
import org.junit.Test
/** Source guard only, not Compose measurement, gesture or pixel proof. */
class IncomingSpecificationUiContractTest {
 private fun source():String {
  val p="app/src/main/kotlin/com/example/chat/ui/calls/bundles/BundleIncomingCallView.kt"
  return listOf(File(p),File("../$p")).first{it.exists()}.readText()
 }
 @Test fun stableNineRoutesAndAppleLockSliderAreKept() {
  val s=source()
  for(id in listOf("CHATY_DEFAULT","APPLE_IOS","SAMSUNG_ONE_UI","GOOGLE_PHONE_LATEST","GOOGLE_PHONE_CLASSIC","VIVO_IQOO","AZURE_RING_VERTICAL","MONO_CHEVRON_DARK","NEXTCLOUD_TALK"))
   assertEquals(id,1,Regex("CallBundleId\\.$id ->").findAll(s).count())
  assertTrue(s.contains("CallBundleId.CHATY_DEFAULT -> ChatyDefaultIncomingCall"))
  assertTrue(s.contains("CallBundleId.APPLE_IOS -> AppleIosIncomingCall"))
  val iosBody=s.substringAfter("private fun ChatyDefaultIncomingCall(")
   .substringBefore("private fun IosSecondaryAction")
  assertTrue(iosBody.contains("AppleIosIncomingCall(tokens, session, onAccept, onDecline)"))
  assertTrue(iosBody.contains("IosSlideToAnswerPill(tokens.acceptColor, onAccept)"))
  assertFalse(iosBody.contains("isKeyguardLocked"))
  assertFalse(iosBody.contains("if (isLocked)"))
 }
 @Test fun tokensBoundedSurfaceAndExplicitDirectionalAccessibility() {
  val s=source()
  assertFalse(s.contains("Color(0x"));assertFalse(s.contains("Color.White"));assertFalse(s.contains("Color.Black"))
  assertTrue(s.contains("widthIn(max = 480.dp)"))
  assertTrue(s.contains("thresholdDp = 112f"));assertTrue(s.contains("thresholdDp = 96f"))
  assertTrue(s.contains("upA11yLabel = \"Answer call\", downA11yLabel = null"))
  assertTrue(s.contains("kotlin.math.abs(start) <= kotlin.math.abs(dx) -> 0"))
  assertFalse(s.contains("HD Voice Call"));assertFalse(s.contains("ORIGIN OS CALL"))
 }
}
