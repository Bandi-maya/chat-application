package com.example.chat
import com.example.chat.ui.chats.contact_info.ContactInfoLogic
import com.example.chat.domain.models.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ConversationMediaIndexPolicyTest {
    private fun message(type: String = "image", text: String = "https://example.invalid/one") = ChatMessage(
        "m", "r", "u", MessageType.image, text, MessageAttachment("a",type,"Photo","1KB", "https://example.invalid/photo"), createdAt = Instant.EPOCH)
    @Test fun visibleMediaListed() { assertEquals(1, ContactInfoLogic.sharedMedia(listOf(message())).size) }
    @Test fun documentsSplitFromMedia() { val m = message("document"); assertTrue(ContactInfoLogic.sharedMedia(listOf(m)).isEmpty()); assertEquals(1,ContactInfoLogic.sharedDocuments(listOf(m)).size) }
    @Test fun deletedMediaNotIndexed() { for (m in listOf(message().copy(isDeletedForMe=true), message().copy(isDeletedForEveryone=true),message().copy(isRevokedBySender=true))) assertTrue(ContactInfoLogic.sharedMedia(listOf(m)).isEmpty()) }
    @Test fun viewOnceAndMalformedValuesFailClosed() { for (v in listOf(true,"true",1,"invalid", emptyMap<String,String>())) assertTrue(ContactInfoLogic.sharedMedia(listOf(message().copy(metadata=mapOf("view_once" to v)))).isEmpty()) }
    @Test fun falseViewOnceStillIndexable() { assertEquals(1, ContactInfoLogic.sharedMedia(listOf(message().copy(metadata=mapOf("view_once" to "false")))).size) }
    @Test fun restrictedLinksNotExposed() { assertTrue(ContactInfoLogic.extractLinks(listOf(message().copy(metadata=mapOf("view_once" to true)))).isEmpty()) }
    @Test fun deletedLinksNotExposed() { assertTrue(ContactInfoLogic.extractLinks(listOf(message().copy(isDeletedForMe=true))).isEmpty()) }
    @Test fun repeatedLinksDeduplicated() { assertEquals(listOf("https://example.invalid/one"), ContactInfoLogic.extractLinks(listOf(message(),message().copy(id="n")))) }
    @Test fun restrictedDocumentNotIndexed() { assertTrue(ContactInfoLogic.sharedDocuments(listOf(message("document").copy(metadata=mapOf("restricted" to true)))).isEmpty()) }
}
