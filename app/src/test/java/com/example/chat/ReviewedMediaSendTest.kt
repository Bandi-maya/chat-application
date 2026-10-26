package com.example.chat

import com.example.chat.domain.models.MessageAttachment
import com.example.chat.domain.models.MessageType
import com.example.chat.ui.chats.detail.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReviewedMediaSendTest {
    private class Backend : AttachmentBackendPort {
        val uploads = mutableListOf<String>()
        val sent = mutableListOf<Triple<String, MessageType, Map<String, Any?>?>>()
        override suspend fun upload(type: String, file: PickedFile, durationSeconds: Int): MessageAttachment {
            uploads += file.name
            return MessageAttachment(id = file.name, type = type, name = file.name, size = "1 KB", url = "/media/${file.name}")
        }
        override suspend fun sendMessage(text: String, type: MessageType, attachment: MessageAttachment?, extraMetadata: Map<String, Any?>?) { sent += Triple(text, type, extraMetadata) }
        override suspend fun sendStructuredMessage(type: String, body: String, metadata: Map<String, Any?>) {}
        override suspend fun createPoll(question: String, options: List<String>, allowMultiple: Boolean) {}
        override suspend fun getPoll(messageId: String): Map<String, Any?>? = null
        override suspend fun votePoll(messageId: String, optionId: String) {}
        override suspend fun reloadConversation() {}
    }
    @Test fun reviewedMultipleImagesKeepViewOnceMetadataAndOnlyFirstCaption() = runBlocking {
        val backend = Backend()
        ChatAttachmentActionController("c", backend, {}).sendReviewedMedia(listOf(PickedFile("a.jpg"), PickedFile("b.jpg")), "image", "caption", true)
        assertEquals(listOf("a.jpg", "b.jpg"), backend.uploads)
        assertEquals(listOf("caption", ""), backend.sent.map { it.first })
        assertTrue(backend.sent.all { it.second == MessageType.image && it.third?.get("view_once") == true })
    }
    @Test fun ordinaryVideoDoesNotInventViewOnceMetadata() = runBlocking {
        val backend = Backend()
        ChatAttachmentActionController("c", backend, {}).sendReviewedMedia(listOf(PickedFile("a.mp4")), "video", "", false)
        assertEquals(MessageType.video, backend.sent.single().second)
        assertNull(backend.sent.single().third)
    }
    @Test fun overLimitFailsBeforeAnyUpload() = runBlocking {
        val backend = Backend()
        val controller = ChatAttachmentActionController("c", backend, {}, imageShareLimit = { 1 })
        assertTrue(runCatching { controller.sendReviewedMedia(listOf(PickedFile("a"), PickedFile("b")), "image", "", false) }.isFailure)
        assertTrue(backend.uploads.isEmpty())
        assertTrue(backend.sent.isEmpty())
    }
    @Test fun nonVisualTypeCannotEnterImageVideoReviewSender() = runBlocking {
        val backend = Backend()
        assertTrue(runCatching { ChatAttachmentActionController("c", backend, {}).sendReviewedMedia(listOf(PickedFile("doc")), "document", "", false) }.isFailure)
        assertTrue(backend.uploads.isEmpty())
    }
}
