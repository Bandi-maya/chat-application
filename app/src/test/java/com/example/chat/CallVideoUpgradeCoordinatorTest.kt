package com.example.chat.data.services

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

private class UpgradeMedia : CallMediaController {
 override val hasLocalMedia = true; override val hasRemoteMedia = true
 override val supportsVideoUpgrade = true
 var prepared = 0; var commits = 0; var aborts = 0; var disposed = false
 override var onLocalIceCandidate: ((String,String?,Int?) -> Unit)? = null
 override var onRemoteStream: (() -> Unit)? = null
 override var onConnectionState: ((CallTransportState) -> Unit)? = null
 override var onIceFailed: (() -> Unit)? = null
 override suspend fun prepare(isVideo:Boolean,lowData:Boolean,iceServers:List<Map<String,Any?>>) {}
 override suspend fun createOffer(isVideo:Boolean) = "offer"
 override suspend fun createAnswer(isVideo:Boolean) = "answer"
 override suspend fun setRemoteDescription(sdp:String,type:String) {}
 override suspend fun addRemoteCandidate(candidate:String,sdpMid:String?,sdpMLineIndex:Int?) {}
 override fun setAudioEnabled(enabled:Boolean) = true
 override fun setVideoEnabled(enabled:Boolean) = true
 override suspend fun switchCamera() = false
 override suspend fun setSpeakerphoneOn(on:Boolean) {}
 override suspend fun startScreenShare() = false
 override suspend fun stopScreenShare() {}
 override suspend fun dispose() { disposed = true }
 override suspend fun prepareVideoUpgrade() { prepared++ }
 override suspend fun commitVideoUpgrade() { commits++ }
 override suspend fun abortVideoUpgrade() { aborts++ }
}
class CallVideoUpgradeCoordinatorTest {
 @Test fun requestRequiresConsentBeforeCameraAndCommit() = runBlocking {
  val m=UpgradeMedia();val events=mutableListOf<String>();val job=SupervisorJob();val scope=CoroutineScope(coroutineContext+job)
  val c=CallVideoUpgradeCoordinator("a","b",m,scope,{true},{t,_,_->events.add(t)},{},{},{0})
  assertFalse(c.request("r",false));assertTrue(c.request("r",true));assertEquals(0,m.prepared)
  c.commit("r");assertEquals(0,m.commits)
  c.accepted("r");assertEquals(1,m.prepared);assertEquals(0,m.commits);assertTrue(events.contains("video_upgrade_offer"))
  c.answer("r","answer");assertEquals(1,m.commits);assertEquals(VideoUpgradeState.Phase.VIDEO,c.state.phase)
  c.returnToAudio();yield();assertEquals(VideoUpgradeState.Phase.AUDIO,c.state.phase);assertFalse(m.disposed);assertEquals(1,m.aborts);job.cancel()
 }
 @Test fun receiverRejectsCommitBeforeOfferAndDeclineReplay() = runBlocking {
  val m=UpgradeMedia();val job=SupervisorJob();val scope=CoroutineScope(coroutineContext+job)
  val c=CallVideoUpgradeCoordinator("b","a",m,scope,{true},{_,_,_->},{},{},{0})
  assertTrue(c.incoming("r"));assertEquals(0,m.prepared);assertTrue(c.acceptIncoming());c.commit("r");assertEquals(0,m.commits)
  c.offer("r","offer");assertEquals(1,m.prepared);assertEquals(0,m.commits);c.commit("r");assertEquals(1,m.commits)
  c.returnToAudio(false);yield();assertFalse(c.incoming("r"));assertFalse(m.disposed);job.cancel()
 }
 @Test fun cancelAndForeignLateCallbacksCannotPromote() = runBlocking {
  val m=UpgradeMedia();var valid=true;val job=SupervisorJob();val scope=CoroutineScope(coroutineContext+job)
  val c=CallVideoUpgradeCoordinator("a","b",m,scope,{valid},{_,_,_->},{},{},{0})
  c.request("r",true);c.cancel();c.accepted("r");c.answer("r","late");yield();assertEquals(0,m.commits);assertEquals(0,m.prepared)
  c.request("r2",true);valid=false;c.accepted("r2");assertEquals(0,m.prepared);c.close();job.cancel()
 }
 @Test fun timeoutCancelsWithoutTouchingAudio() = runBlocking {
  val m=UpgradeMedia();val events=mutableListOf<String>();val job=SupervisorJob();val scope=CoroutineScope(coroutineContext+job)
  val c=CallVideoUpgradeCoordinator("a","b",m,scope,{true},{t,_,_->events.add(t)},{},{},{0},1)
  c.request("r",true);delay(20);assertEquals(VideoUpgradeState.Phase.AUDIO,c.state.phase)
  assertEquals("Video request timed out",c.notice);assertTrue(events.contains("video_upgrade_cancel"));assertEquals(0,m.commits);assertFalse(m.disposed);job.cancel()
 }
 @Test fun cancelledAwaitCannotEnableVideo() = runBlocking {
  val gate=CompletableDeferred<Unit>();var committed=0
  val m=object: CallMediaController by UpgradeMedia() {
   override suspend fun prepareVideoUpgrade() { gate.await() }
   override suspend fun commitVideoUpgrade() { committed++ }
  }
  val job=SupervisorJob();val scope=CoroutineScope(coroutineContext+job)
  val c=CallVideoUpgradeCoordinator("a","b",m,scope,{true},{_,_,_->},{},{},{0})
  c.request("r",true);val accepting=launch { c.accepted("r") };yield();c.cancel();gate.complete(Unit);accepting.join();yield()
  c.answer("r","late");assertEquals(0,committed);assertEquals(VideoUpgradeState.Phase.AUDIO,c.state.phase);job.cancel()
 }

}
