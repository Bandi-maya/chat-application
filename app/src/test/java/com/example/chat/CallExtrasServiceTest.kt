package com.example.chat.data.services
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
private class ExtrasMedia : CallMediaController {
 override val hasLocalMedia = true; override val hasRemoteMedia = true
 override val supportsVideoUpgrade = true
 override val supportsLocalHold = true
 var microphone = true; var playout = true
 override fun setRemoteAudioEnabled(enabled:Boolean):Boolean { playout=enabled;return true }
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
 override fun setAudioEnabled(enabled:Boolean):Boolean { microphone=enabled;return true }
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
class CallExtrasServiceTest {
 private suspend fun connected(scope:CoroutineScope,media:ExtrasMedia,capable:Boolean,clock:()->Long,sent:MutableList<Map<String,Any?>>):CallSignalingService {
  val service=CallSignalingService(scope=scope,currentUserId={"me"},signalSender=CallSignalSender { _,data -> sent.add(data) },mediaFactory={media},callExtrasCapability={_,_,_->capable},monotonicMillis=clock)
  service.initiateCall(remoteUserId="peer",remoteDisplayName="Peer",isVideo=false)
  media.onConnectionState?.invoke(CallTransportState.connected)
  return service
 }
 @Test fun localHoldPreservesMuteIntentAndDoesNotNotifyUnsupportedPeer()=runBlocking {
  val job=SupervisorJob();val sc=CoroutineScope(coroutineContext+job);val m=ExtrasMedia();val sent=mutableListOf<Map<String,Any?>>()
  val s=connected(sc,m,false,{0},sent);s.toggleMute();assertFalse(m.microphone)
  assertTrue(s.toggleLocalHold());assertFalse(m.microphone);assertFalse(m.playout);assertTrue(s.currentSession!!.isLocallyHeld)
  s.toggleMute();assertFalse(m.microphone);assertFalse(s.currentSession!!.isMuted)
  assertTrue(s.toggleLocalHold());assertTrue(m.microphone);assertTrue(m.playout);assertFalse(sent.any {it["signal_type"]=="call_hold"});assertFalse(m.disposed);job.cancel()
 }
 @Test fun reactionsAreLocalOnlyRateLimitedAndBounded()=runBlocking {
  val job=SupervisorJob();val sc=CoroutineScope(coroutineContext+job);val m=ExtrasMedia();val sent=mutableListOf<Map<String,Any?>>();var tick=0L
  val s=connected(sc,m,false,{tick},sent)
  assertFalse(s.sendLocalCallReaction("<script>"));assertTrue(s.sendLocalCallReaction("👍"));assertFalse(s.callReaction!!.deliveredToPeer)
  assertFalse(s.sendLocalCallReaction("👏"));tick=1500;assertTrue(s.sendLocalCallReaction("👏"));assertFalse(sent.any {it["signal_type"]=="call_reaction"})
  s.detachForAccountTransition(Any());assertNull(s.callReaction);job.cancel()
 }
 @Test fun peerEventsRequireIdentityAndFreshRevision()=runBlocking {
  val job=SupervisorJob();val sc=CoroutineScope(coroutineContext+job);val m=ExtrasMedia();val sent=mutableListOf<Map<String,Any?>>();var tick=0L
  val s=connected(sc,m,true,{tick},sent);val id=s.currentSession!!.callId
  fun hold(sender:String,revision:Long,held:Boolean)=mapOf("signal_type" to "call_hold","call_id" to id,"sender_id" to sender,"protocol_version" to 1,"revision" to revision,"held" to held)
  s.handleRemoteSignal(hold("foreign",1,true));assertFalse(s.currentSession!!.isRemotelyHeld)
  s.handleRemoteSignal(hold("peer",2,true));assertTrue(s.currentSession!!.isRemotelyHeld)
  s.handleRemoteSignal(hold("peer",1,false));assertTrue(s.currentSession!!.isRemotelyHeld)
  s.handleRemoteSignal(hold("peer",3,false));assertFalse(s.currentSession!!.isRemotelyHeld)
  val reaction=mapOf("signal_type" to "call_reaction","call_id" to id,"sender_id" to "peer","protocol_version" to 1,"reaction_id" to "r1","emoji" to "🎉","ttl_ms" to 4000)
  s.handleRemoteSignal(reaction);assertTrue(s.callReaction!!.fromPeer);tick=2000;s.handleRemoteSignal(reaction);assertEquals("r1",s.callReaction!!.id)
  assertTrue(s.toggleLocalHold());assertTrue(sent.any {it["signal_type"]=="call_hold"});job.cancel()
 }
}
