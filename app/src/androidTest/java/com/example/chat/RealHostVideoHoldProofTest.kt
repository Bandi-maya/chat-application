package com.example.chat

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.*
import com.example.chat.ui.ChatDetailHost
import com.example.chat.ui.ChatVideoHoldNativeProof
import com.example.chat.ui.AttachFlow
import com.example.chat.ui.camera.*
import com.example.chat.ui.theme.ChatyTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Host/composer/CameraX, isolated loopback AVD only. Microphone denied, no send/upload action. */
@RunWith(AndroidJUnit4::class)
class RealHostVideoHoldProofTest {
 private val ins=InstrumentationRegistry.getInstrumentation()
 private val d=UiDevice.getInstance(ins)
 @Suppress("UNCHECKED_CAST") private fun <T> field(name:String):T {val f=ServiceLocator.apiBackend.javaClass.getDeclaredField(name);f.isAccessible=true;return f.get(ServiceLocator.apiBackend) as T}
 private fun resetApi(){val f=ServiceLocator.apiBackend.javaClass.getDeclaredField("initialized");f.isAccessible=true;f.setBoolean(ServiceLocator.apiBackend,false);ServiceLocator.apiBackend.initialize()}
 private fun pointer(action:Int,x:Float,y:Float,down:Long){val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);try{ins.sendPointerSync(e)}finally{e.recycle()}}
 private fun capture(name:String){val out=File(ins.targetContext.getExternalFilesDir(null),"screenshots").apply{mkdirs()};d.dumpWindowHierarchy(File(out,"real-host-hold-$name.xml"));ins.uiAutomation.takeScreenshot()?.let{b->File(out,"real-host-hold-$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 private fun await(label:String,predicate:()->Boolean){val end=SystemClock.uptimeMillis()+20000;while(!predicate()&&SystemClock.uptimeMillis()<end)SystemClock.sleep(50);capture(label);assertTrue(label,predicate())}
 private fun runCase(kind:String){
  val c=ins.targetContext;val api=ServiceLocator.apiBackend
  require(api.config.restApiBaseUrl.startsWith("http://10.0.2.2:")){"Synthetic proof refuses non-loopback backend"}
  val prefs=c.getSharedPreferences("chaty_api_backend",Context.MODE_PRIVATE)
  val prior=prefs.all.toMap()
  require(prior["api_hub_token"]==null || prior["api_hub_token"]=="synthetic-test-token"){"Synthetic proof refuses existing non-fixture session"}
  val priorTenant=api.config.tenantId;val priorUseDefault=api.config.isUsingDefaultTenant
  val oldUsers=field<MutableList<UserProfile>>("_users").toList();val oldRooms=field<MutableList<Conversation>>("_conversations").toList();val oldMessages=field<MutableMap<String,MutableList<ChatMessage>>>("_messages").mapValues{it.value.toMutableList()}
  val hadCamera=c.checkSelfPermission(Manifest.permission.CAMERA)==android.content.pm.PackageManager.PERMISSION_GRANTED
  val hadMic=c.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED
  require(!hadMic){"Silent synthetic proof requires microphone permission already denied"}
  val folder=File(c.cacheDir,"camera-video");val before=folder.listFiles()?.map{it.absolutePath}?.toSet()?:emptySet()
  val states=CopyOnWriteArrayList<VideoSessionState>();val entry=AtomicReference<Pair<AttachFlow,VideoHoldRequest>>()
  val gate=CompletableDeferred<Unit>();val entered=CompletableDeferred<Unit>()
  var scenario:ActivityScenario<MediaFixtureActivity>?=null
  var pointerDown=false;var x=0f;var y=0f;var down=0L
  try {
   prefs.edit().putString("api_hub_token","synthetic-test-token").putString("api_hub_user","""{"id":"synthetic-user-1","username":"testuser","displayName":"Test User","avatarInitials":"T","avatarColorHex":"0xFF6366F1","about":"Synthetic","email":"","phone":"","avatarUrl":null}""").commit()
   resetApi();assertTrue(api.isAuthenticated)
   // Throwaway loopback CI AVD only. Grant CAMERA using the existing camera-proof pattern;
   // never grant MICROPHONE and never claim restoration of a real user's permission state.
   d.executeShellCommand("pm grant ${c.packageName} android.permission.CAMERA")
   val room=Conversation(id="synthetic-video-room",type=ConversationType.direct,title="Synthetic Video Peer",participantIds=listOf("synthetic-user-1","synthetic-user-2"),avatarInitials="SV",avatarColorHex="0xFF6366F1",lastMessageText="Synthetic preview",lastMessageTime=Instant.now(),lastMessageSenderId="synthetic-user-2")
   field<MutableList<Conversation>>("_conversations").add(room)
   field<MutableMap<String,MutableList<ChatMessage>>>("_messages")[room.id]=mutableListOf()
   ChatVideoHoldNativeProof.onEntry={flow,token->entry.set(flow to token)}
   CameraVideoNativeProof.observe={states.add(it)}
   if(kind=="preready"||kind=="auth"||kind=="tenant")CameraVideoNativeProof.beforeBind={entered.complete(Unit);gate.await()}
   scenario=ActivityScenario.launch(MediaFixtureActivity::class.java)
   scenario.onActivity{a->a.setContent{ChatyTheme(config=ThemePresets.whatsappIosLight){ChatDetailHost(room,null,{})}}}
   assertTrue(d.wait(Until.hasObject(By.descStartsWith("Microphone mode.")),20000))
   capture("$kind-toggle-before")
   val toggle=d.findObject(By.descStartsWith("Microphone mode.")) ?: throw AssertionError("Microphone control before single tap")
   val toggleStarted=SystemClock.uptimeMillis()
   val toggleDeadline=toggleStarted+5000L
   toggle.click() // Exactly one tap. Capture time is INCLUDED in the original five-second budget.
   capture("$kind-toggle-after")
   var foundControl:androidx.test.uiautomator.UiObject2?=null
   var sampledAt=0L
   while(SystemClock.uptimeMillis()<toggleDeadline && foundControl==null){
    if(android.os.Build.VERSION.SDK_INT>=33)ins.uiAutomation.clearCache()
    val candidate=d.findObject(By.descStartsWith("Video mode."))
    val sampled=SystemClock.uptimeMillis()
    if(candidate!=null && sampled<=toggleDeadline){foundControl=candidate;sampledAt=sampled}
    val remaining=toggleDeadline-SystemClock.uptimeMillis()
    if(foundControl==null && remaining>0)SystemClock.sleep(minOf(50L,remaining))
   }
   // Evidence only after the fixed deadline decision. No late screenshot/query can turn failure into pass.
   capture("$kind-toggle-decision")
   val diagnostics=File(c.getExternalFilesDir(null),"screenshots").apply{mkdirs()}
   File(diagnostics,"real-host-hold-$kind-toggle-timing.txt").writeText(
    "budgetMs=5000;decisionFound=${foundControl!=null};sampleElapsedMs=${if(sampledAt==0L)-1 else sampledAt-toggleStarted};decisionElapsedMs=${SystemClock.uptimeMillis()-toggleStarted};entryObserved=${entry.get()!=null};nativeStateCount=${states.size}")
   val control=foundControl ?: throw AssertionError("Video control within original 5000ms after one tap")
   val r=control.visibleBounds;x=r.exactCenterX();y=r.exactCenterY();down=SystemClock.uptimeMillis();pointer(MotionEvent.ACTION_DOWN,x,y,down);pointerDown=true
   await("$kind-original-entry"){entry.get()!=null}
   if(kind=="preready"||kind=="auth"||kind=="tenant"){
    await("$kind-bind-barrier"){entered.isCompleted}
    if(kind=="auth")scenario.onActivity{api.signOut()}
    if(kind=="tenant"){
     val original=api.authSnapshot()
     scenario.onActivity{api.config.setTenantId("synthetic-hold-tenant-transition")}
     val changed=api.authSnapshot()
     assertNotSame("Real tenant transition changes account-scope identity",original,changed)
     assertEquals("Same signed-in user",original.userId,changed.userId)
     assertEquals("Same synthetic token",original.token,changed.token)
     assertTrue(api.isAuthenticated)
     assertEquals("Routing remains isolated loopback",original.baseUrl,changed.baseUrl)
     assertTrue(changed.baseUrl.startsWith("http://10.0.2.2:"))
    }
    pointer(MotionEvent.ACTION_UP,x,y,down);pointerDown=false;gate.complete(Unit)
    if(kind=="preready")await("preready-real-front"){states.any{it is VideoSessionState.Ready && it.boundFront}}
    else await("$kind-original-flow-cancelled"){entry.get().first.isCancelled}
    SystemClock.sleep(1000)
    assertFalse("No native recording after early release/auth loss",states.any{it is VideoSessionState.Recording})
    assertTrue("No clip created",folder.listFiles()?.none{it.absolutePath !in before}?:true)
   } else {
    await("$kind-native-recording"){states.any{it is VideoSessionState.Recording}}
    assertTrue("Actual front camera bound",states.any{it is VideoSessionState.Ready&&it.boundFront})
    assertFalse("Synthetic video silent",(states.last{it is VideoSessionState.Recording} as VideoSessionState.Recording).hasAudio)
    SystemClock.sleep(1600)
    if(kind=="locked"){pointer(MotionEvent.ACTION_MOVE,x,y-90*c.resources.displayMetrics.density,down);await("locked-token"){entry.get().second.phase.value==VideoHoldRequest.Phase.Locked}}
    pointer(if(kind=="cancel")MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP,x,y,down);pointerDown=false
    if(kind=="locked"){SystemClock.sleep(400);assertFalse(states.any{it is VideoSessionState.Review});assertTrue(d.hasObject(By.desc("Stop recording")));d.findObject(By.desc("Stop recording")).click()}
    if(kind=="cancel"){
     await("cancel-finalized-clean"){folder.listFiles()?.none{it.absolutePath !in before}?:true}
     assertFalse("Cancel never reviews",states.any{it is VideoSessionState.Review})
    } else {
     await("$kind-decoded-review"){states.any{it is VideoSessionState.Review}}
     val video=(states.last{it is VideoSessionState.Review} as VideoSessionState.Review).video
     val reader=MediaMetadataRetriever();try{reader.setDataSource(video.file.absolutePath);val frame=reader.getFrameAtTime(0);assertNotNull("Native MP4 decodes",frame);frame?.recycle()}finally{reader.release()}
     assertTrue(d.wait(Until.hasObject(By.text("Play recording")),5000))
     assertFalse("No pretend send",d.hasObject(By.text("Use video")))
    }
   }
  } finally {
   if(pointerDown)pointer(MotionEvent.ACTION_CANCEL,x,y,down)
   gate.complete(Unit);capture("$kind-finally");scenario?.close()
   CameraVideoNativeProof.reset();ChatVideoHoldNativeProof.onEntry=null
   val deadline=SystemClock.uptimeMillis()+10000;while(folder.listFiles()?.any{it.absolutePath !in before}==true && SystemClock.uptimeMillis()<deadline)SystemClock.sleep(100)
   folder.listFiles()?.filter{it.absolutePath !in before}?.forEach{it.delete()}
   field<MutableList<UserProfile>>("_users").apply{clear();addAll(oldUsers)};field<MutableList<Conversation>>("_conversations").apply{clear();addAll(oldRooms)};field<MutableMap<String,MutableList<ChatMessage>>>("_messages").apply{clear();putAll(oldMessages)}
   api.config.setTenantId(priorTenant,priorUseDefault)
   assertTrue("Restored routing stays loopback",api.config.restApiBaseUrl.startsWith("http://10.0.2.2:"))
   prefs.edit().clear().apply{prior.forEach{(k,v)->when(v){is String->putString(k,v);is Boolean->putBoolean(k,v);is Int->putInt(k,v);is Long->putLong(k,v);is Float->putFloat(k,v)}}}.commit();resetApi()
  }
 }
 @Test fun earlyReleaseBeforeRealBindNeverStarts()=runCase("preready")
 @Test fun nativeRecordingReleaseCreatesDecodedLocalReview()=runCase("release")
 @Test fun actualPointerCancelAfterOverlayDiscardsNativeClip()=runCase("cancel")
 @Test fun upLockReleaseKeepsRecordingUntilNativeStopAndDecodedReview()=runCase("locked")
 @Test fun originalAccountLossBeforeBindDeniesNativeStart()=runCase("auth")
 @Test fun sameSignedInUserTenantTransitionCancelsOriginalFlowBeforeBind()=runCase("tenant")
}
