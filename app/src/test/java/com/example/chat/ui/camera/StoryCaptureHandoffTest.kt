package com.example.chat.ui.camera
import java.nio.file.Files
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
class StoryCaptureHandoffTest {
 private fun owned():CameraOwnedResult {val f=Files.createTempFile("story-fixture-",".jpg").toFile().apply{writeText("synthetic")};return CameraOwnedFiles().let{it.own(f);it.transfer(f.path)!!}}
 @Test fun acceptedBytesDetachedAndFinalDeleted()=runBlocking{val r=owned();var bytes:ByteArray?=null;assertTrue(handoffCameraPhotoBytes(r,{r.file.readBytes()},{bytes=it;true}));assertFalse(r.file.exists());assertEquals("synthetic",bytes!!.decodeToString())}
 @Test fun staleConsumerRejectsAndOldFileStillDeleted()=runBlocking{val r=owned();assertFalse(handoffCameraPhotoBytes(r,{r.file.readBytes()},{false}));assertFalse(r.file.exists())}
 @Test fun readFailureDeletesAndDoesNotAccept()=runBlocking{val r=owned();var accepted=false;val e=runCatching{handoffCameraPhotoBytes(r,{throw java.io.IOException("fixture")},{accepted=true;true})}.exceptionOrNull();assertTrue(e is java.io.IOException);assertFalse(accepted);assertFalse(r.file.exists())}
 @Test fun cancellationDeletesWithoutAcceptance()=runBlocking{val r=owned();var accepted=false;val e=runCatching{handoffCameraPhotoBytes(r,{throw CancellationException("fixture")},{accepted=true;true})}.exceptionOrNull();assertTrue(e is CancellationException);assertFalse(accepted);assertFalse(r.file.exists())}
 @Test fun suspendedOldHandoffCannotAcceptIntoNewPending()=runBlocking{val r=owned();val gate=CompletableDeferred<Unit>();val started=CompletableDeferred<Unit>();val old=Any();val next=Any();var current=old;var accepted=false;val job=async{handoffCameraPhotoBytes(r,{started.complete(Unit);gate.await();r.file.readBytes()},{if(current===old){accepted=true;true}else false})};started.await();current=next;gate.complete(Unit);assertFalse(job.await());assertSame(next,current);assertFalse(accepted);assertFalse(r.file.exists())}
}
