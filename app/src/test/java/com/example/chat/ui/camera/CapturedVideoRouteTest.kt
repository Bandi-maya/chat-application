package com.example.chat.ui.camera

import com.example.chat.ui.chats.detail.*
import com.example.chat.domain.models.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CapturedVideoRouteTest {
    private class Backend : AttachmentBackendPort {
        var type: String? = null
        var file: PickedFile? = null
        var seconds = 0
        var sent: MessageType? = null
        var fail = false
        var afterUpload: () -> Unit = {}
        override suspend fun upload(type: String,file: PickedFile,durationSeconds: Int): MessageAttachment {
            if (fail) error("synthetic failure")
            this.type=type;this.file=file;seconds=durationSeconds
            afterUpload()
            return MessageAttachment("a",type,file.name,"1KB","fixture",durationSeconds)
        }
        override suspend fun sendMessage(text:String,type:MessageType,attachment:MessageAttachment?,extraMetadata:Map<String,Any?>?) { sent=type }
        override suspend fun sendStructuredMessage(type:String,body:String,metadata:Map<String,Any?>) = Unit
        override suspend fun createPoll(question:String,options:List<String>,allowMultiple:Boolean) = Unit
        override suspend fun getPoll(messageId:String):Map<String,Any?>? = null
        override suspend fun votePoll(messageId:String,optionId:String) = Unit
        override suspend fun reloadConversation() = Unit
    }
    @Test fun finalizedMp4UsesVideoUploadNotImage() = runBlocking {
        val backend=Backend();val controller=ChatAttachmentActionController("fixture",backend,{})
        assertTrue(controller.sendCapturedVideo("/fixture/local.mp4",1501,"video/mp4",isStillCurrent={true}))
        assertEquals("video",backend.type);assertEquals("video/mp4",backend.file?.mimeType)
        assertEquals(2,backend.seconds);assertEquals(MessageType.video,backend.sent)
    }
    @Test fun failureDoesNotRunSuccessCleanupCallback() = runBlocking {
        val backend=Backend().apply { fail=true };val controller=ChatAttachmentActionController("fixture",backend,{})
        var completed=false
        assertFalse(controller.sendCapturedVideo("/fixture/local.mp4",1000,"video/mp4",isStillCurrent={true},onSubmitted={completed=true}))
        assertFalse(completed);assertNull(backend.sent)
    }
    @Test fun accountOrConversationSwitchAfterUploadBlocksSubmission() = runBlocking {
        val backend=Backend();var current=true
        backend.afterUpload={current=false}
        val controller=ChatAttachmentActionController("fixture",backend,{})
        assertFalse(controller.sendCapturedVideo("/fixture/local.mp4",1000,"video/mp4",isStillCurrent={current}))
        assertNull(backend.sent)
    }
    @Test fun unitQueuedSendNeverReportsConfirmedDelivery() = runBlocking {
        val backend=Backend();val notices=mutableListOf<String>()
        val controller=ChatAttachmentActionController("fixture",backend,{notices.add(it)})
        assertTrue(controller.sendCapturedVideo("/fixture/local.mp4",1000,"video/mp4",isStillCurrent={true}))
        assertTrue(notices.single().contains("Delivery is not confirmed"))
        assertTrue(notices.single().contains("local recording kept"))
    }
    @Test fun imageMimeCannotEnterVideoRoute() = runBlocking {
        val backend=Backend();val controller=ChatAttachmentActionController("fixture",backend,{})
        try { controller.sendCapturedVideo("/fixture/local.jpg",1000,"image/jpeg",isStillCurrent={true});fail("must refuse") }
        catch (_:IllegalArgumentException) { }
        assertNull(backend.type)
    }
}
