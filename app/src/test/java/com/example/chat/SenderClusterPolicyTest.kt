package com.example.chat
import com.example.chat.ui.chats.detail.SenderClusterPolicy
import com.example.chat.ui.chats.detail.SenderClusterPosition
import org.junit.Assert.assertEquals
import org.junit.Test
class SenderClusterPolicyTest {
 @Test fun clustersChronologicalEligibleSenderOnly() {
  fun i(t: Long, sender: String = "a", eligible: Boolean = true) = SenderClusterPolicy.Item(sender, "c", t, eligible)
  assertEquals(listOf(SenderClusterPosition.FIRST, SenderClusterPosition.MIDDLE, SenderClusterPosition.LAST),
   SenderClusterPolicy.positions(listOf(i(1), i(1000), i(2000))))
  assertEquals(listOf(SenderClusterPosition.SINGLE, SenderClusterPosition.SINGLE),
   SenderClusterPolicy.positions(listOf(i(1), i(120002))))
  assertEquals(listOf(SenderClusterPosition.SINGLE, SenderClusterPosition.SINGLE),
   SenderClusterPolicy.positions(listOf(i(1), i(2, "b"))))
 }
}
