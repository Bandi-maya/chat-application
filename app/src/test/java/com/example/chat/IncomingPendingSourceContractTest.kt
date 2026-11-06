package com.example.chat
import java.io.File
import org.junit.Assert.*
import org.junit.Test
class IncomingPendingSourceContractTest {
 @Test fun allNineStylesReceivePendingGuardedUiIntents() {
  val p="app/src/main/kotlin/com/example/chat/ui/calls/bundles/BundleIncomingCallView.kt"
  val s=listOf(File(p),File("../$p")).first{it.exists()}.readText()
  val routes=s.substringAfter("when (bundleId) {").substringBefore("\n    }")
  assertEquals(9,Regex("session, requestAccept, requestDecline").findAll(routes).count())
  assertTrue(s.contains("if (!latestPending) latestAccept()"));assertTrue(s.contains("if (!latestPending) latestDecline()"))
  assertFalse(s.contains("CallSignalingService"))
 }
}
