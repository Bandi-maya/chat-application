package com.example.chat
import com.example.chat.core.messages.LocalPinnedMessagePolicy
import com.example.chat.domain.models.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
class LocalPinnedMessagePolicyTest {
 private fun m(id:String="a") = ChatMessage(id,"r","u",MessageType.text,"pinned",createdAt=Instant.EPOCH,isPinned=true)
 @Test fun unpinnedNotShown() { assertNull(LocalPinnedMessagePolicy.latest(listOf(m().copy(isPinned=false)))) }
 @Test fun deletedRevokedHidden() { assertNull(LocalPinnedMessagePolicy.latest(listOf(m().copy(isDeletedForMe=true),m().copy(isRevokedBySender=true),m().copy(isDeletedForEveryone=true)))) }
 @Test fun systemHidden() { assertFalse(LocalPinnedMessagePolicy.eligible(m().copy(type=MessageType.system))) }
 @Test fun newestChosenIndependentOfInputOrder() { val new=m("new").copy(createdAt=Instant.EPOCH.plusSeconds(2));assertEquals(new,LocalPinnedMessagePolicy.latest(listOf(new,m()))) }
 @Test fun malformedViewOnceFailsClosed() { assertFalse(LocalPinnedMessagePolicy.eligible(m().copy(metadata=mapOf("view_once" to 1)))) }
 @Test fun actualMessageIdentityReturned() { assertEquals("real-id",LocalPinnedMessagePolicy.latest(listOf(m("real-id")))?.id) }
}
