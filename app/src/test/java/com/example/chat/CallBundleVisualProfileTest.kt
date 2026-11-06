package com.example.chat.ui.calls

import com.example.chat.ui.calls.bundles.CallBundleId
import org.junit.Assert.*
import org.junit.Test

class CallBundleVisualProfileTest {
 @Test fun nineProfilesRemainDistinctWithoutColorForks() {
  val profiles = CallBundleId.entries.map { callBundleVisualProfile(it) }
  assertEquals(9, profiles.distinct().size)
  profiles.forEach { p ->
   assertTrue(p.controlDiameterDp >= 48)
   assertTrue(p.columns in 2..4)
   assertTrue(p.spacingDp >= 8)
  }
 }
 @Test fun monoAndAzureUseOutlineAndLatestUsesReachablePanel() {
  assertTrue(callBundleVisualProfile(CallBundleId.MONO_CHEVRON_DARK).outlined)
  assertTrue(callBundleVisualProfile(CallBundleId.AZURE_RING_VERTICAL).outlined)
  assertTrue(callBundleVisualProfile(CallBundleId.GOOGLE_PHONE_LATEST).panel)
  assertEquals(2, callBundleVisualProfile(CallBundleId.SAMSUNG_ONE_UI).columns)
 }
 @Test fun defaultIsStableAndDoesNotOverwriteExplicitApple() {
  assertEquals(callBundleVisualProfile(CallBundleId.APPLE_IOS),callBundleVisualProfile(null))
  assertNotEquals(callBundleVisualProfile(CallBundleId.CHATY_DEFAULT),callBundleVisualProfile(null))
 }
}
