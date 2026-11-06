package com.example.chat
import com.example.chat.domain.models.*
import com.example.chat.ui.chats.detail.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
class CapturedPhotoSubmissionTest {
 private class Backend:AttachmentBackendPort {
  var fail:Exception?=null;var uploads=0;var sends=0
  override suspend fun upload(type:String,file:PickedFile,durationSeconds:Int):MessageAttachment{uploads++;fail?.let{throw it};return MessageAttachment(id="synthetic",type="image",name="fixture.jpg",size="1 KB",url="https://fixture.invalid/photo")}
  override suspend fun sendMessage(text:String,type:MessageType,attachment:MessageAttachment?,extraMetadata:Map<String,Any?>?){sends++}
  override suspend fun sendStructuredMessage(type:String,body:String,metadata:Map<String,Any?>){}
  override suspend fun createPoll(question:String,options:List<String>,allowMultiple:Boolean){}
  override suspend fun getPoll(messageId:String):Map<String,Any?>?=null
  override suspend fun votePoll(messageId:String,optionId:String){}
  override suspend fun reloadConversation(){}
 }
 @Test fun queuedBackendIsSubmittedNotDeliveredAndDoesNotDeleteFile()=runBlocking{val b=Backend();val file=java.io.File.createTempFile("photo-fixture-",".jpg").apply{writeText("synthetic")};var callback=0;val c=ChatAttachmentActionController("fixture",b,{});assertEquals(CapturedPhotoSubmission.Submitted,c.sendCapturedPhoto(file.path,"","none",0.0,{callback++}));assertEquals(1,b.sends);assertEquals(1,callback);assertTrue(file.exists());file.delete();Unit}
 @Test fun uploadFailureReturnsFailedWithoutSuccessCallback()=runBlocking{val b=Backend().apply{fail=java.io.IOException("fixture")};var callback=0;val c=ChatAttachmentActionController("fixture",b,{});assertEquals(CapturedPhotoSubmission.Failed,c.sendCapturedPhoto("fixture.jpg","","none",0.0,{callback++}));assertEquals(0,b.sends);assertEquals(0,callback)}
 @Test fun cancellationPropagatesAndDoesNotToastOrReportSuccess()=runBlocking{val b=Backend().apply{fail=CancellationException("fixture")};var toast=0;var callback=0;val c=ChatAttachmentActionController("fixture",b,{toast++});val e=runCatching{c.sendCapturedPhoto("fixture.jpg","","none",0.0,{callback++})}.exceptionOrNull();assertTrue(e is CancellationException);assertEquals(0,callback);assertEquals(0,toast)}
 @Test fun observerFailureDoesNotBecomeFalseUploadFailure()=runBlocking{val b=Backend();var toast=0;val c=ChatAttachmentActionController("fixture",b,{toast++});assertEquals(CapturedPhotoSubmission.Submitted,c.sendCapturedPhoto("fixture.jpg","","none",0.0,{throw IllegalArgumentException("observer")}));assertEquals(1,b.sends);assertEquals(0,toast)}
 @Test fun noncooperativeBackendCancelThenReturnCannotNotifyObserver()=runBlocking{val child=Job();var callback=0;val b=object:AttachmentBackendPort by Backend(){override suspend fun sendMessage(text:String,type:MessageType,attachment:MessageAttachment?,extraMetadata:Map<String,Any?>?){child.cancel()}};val c=ChatAttachmentActionController("fixture",b,{});val e=runCatching{withContext(child){c.sendCapturedPhoto("fixture.jpg","","none",0.0,{callback++})}}.exceptionOrNull();assertTrue(e is CancellationException);assertEquals(0,callback)}
 @Test fun staleCapturedFlowDoesNotNotifyAfterBackendReturns()=runBlocking{var callback=0;val c=ChatAttachmentActionController("fixture",Backend(),{});assertEquals(CapturedPhotoSubmission.Failed,c.sendCapturedPhoto("fixture.jpg","","none",0.0,{callback++},isStillCurrent={false}));assertEquals(0,callback)}
 @Test fun backendSwitchAfterSendReturnCannotNotifyObserver()=runBlocking{var current=true;var callback=0;val b=object:AttachmentBackendPort by Backend(){override suspend fun sendMessage(text:String,type:MessageType,attachment:MessageAttachment?,extraMetadata:Map<String,Any?>?){current=false}};val c=ChatAttachmentActionController("fixture",b,{});assertEquals(CapturedPhotoSubmission.Submitted,c.sendCapturedPhoto("fixture.jpg","","none",0.0,{callback++},isStillCurrent={current}));assertEquals(0,callback)}
}
