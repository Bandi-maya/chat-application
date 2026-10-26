package com.example.chat
import com.example.chat.data.api.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
class ApiAccountScopeTest {
 private class Store: ApiSessionStore {
  var tokenValue:String?=null;var userValue:String?=null
  override fun loadToken()=tokenValue
  override fun loadUserJson()=userValue
  override fun save(token:String,userJson:String){tokenValue=token;userValue=userJson}
  override fun clear(){tokenValue=null;userValue=null}
 }
 private fun user(id:String="a")=ApiUserProfile(id,id,id,id)
 @Test fun sameTokenReloginRestoreAndSignoutAdvanceGenerationProfileDoesNot(){
  val s=ApiAuthSession(Store());val g=s.snapshot().generation
  s.adopt(ApiAuthResult("fake",user()));val a=s.snapshot();assertEquals(g+1,a.generation)
  s.updateProfileFields("new",null,null,null);assertEquals(a.generation,s.snapshot().generation)
  s.adopt(ApiAuthResult("fake",user()));assertEquals(a.generation+1,s.snapshot().generation)
  s.restore();val restored=s.snapshot();assertEquals(a.generation+2,restored.generation)
  s.signOut();assertEquals(restored.generation+1,s.snapshot().generation);assertNull(s.snapshot().token);assertNull(s.snapshot().user)
 }
 @Test fun scopeStableAcrossProfileButNotAuthOrRoutingAba(){
  val lock=Any();val session=ApiAuthSession(Store(),lock);session.adopt(ApiAuthResult("fake",user()))
  var r=ApiRoutingSnapshot("t","https://invalid.example","wss://invalid.example",0)
  val owner=ApiAccountScopeOwner(lock,session::snapshot,{r});val a=owner.snapshot();assertSame(a,owner.snapshot())
  session.updateProfileFields("new",null,null,null);assertSame(a,owner.snapshot())
  synchronized(lock){r=r.copy(generation=1)};val b=owner.snapshot();assertNotSame(a,b)
  synchronized(lock){r=r.copy(generation=2)};assertNotSame(b,owner.snapshot())
  session.signOut();session.adopt(ApiAuthResult("fake",user()));assertFalse(owner.withScope(a){fail("stale callback")})
 }
 @Test fun finalSynchronousDeliverySerializesConcurrentLogout(){
  val lock=Any();val session=ApiAuthSession(Store(),lock);session.adopt(ApiAuthResult("fake",user()))
  val owner=ApiAccountScopeOwner(lock,session::snapshot,{ApiRoutingSnapshot("t","https://invalid.example","",0)})
  val a=owner.snapshot();val entered=CountDownLatch(1);val release=CountDownLatch(1);val changed=CountDownLatch(1)
  val t=Thread{assertTrue(owner.withScope(a){entered.countDown();check(release.await(2,TimeUnit.SECONDS))})};t.start();assertTrue(entered.await(2,TimeUnit.SECONDS))
  val q=Thread{session.signOut();changed.countDown()};q.start();assertFalse(changed.await(30,TimeUnit.MILLISECONDS));release.countDown();t.join(2000);q.join(2000);assertEquals(0L,changed.count);assertFalse(owner.withScope(a){fail()})
 }
 @Test fun parallelAdoptsNeverExposeTornUserToken(){
  val session=ApiAuthSession(Store());val writers=(1..4).map{n->Thread{repeat(100){session.adopt(ApiAuthResult("token$n",user("$n")))}}}
  writers.forEach{it.start()};repeat(10000){val s=session.snapshot();s.user?.let{assertEquals("token${it.id}",s.token)}};writers.forEach{it.join()};assertEquals(400,session.snapshot().generation.toInt())
 }
 @Test fun transitionHookSeesInstalledSnapshotBeforeMutationReturns(){
  val seen=mutableListOf<ApiAuthSnapshot>();lateinit var session:ApiAuthSession
  session=ApiAuthSession(Store(),Any()){seen.add(session.snapshot())}
  session.adopt(ApiAuthResult("fake",user()));session.restore();session.signOut()
  assertEquals(listOf(1L,2L,3L),seen.map{it.generation});assertNull(seen.last().token)
 }

}
