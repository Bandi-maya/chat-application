package com.example.chat
import com.example.chat.ui.calls.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

private class ScopeStorage:CallPresentationPrefsStorage {
 val data=mutableMapOf<String,String>();var fail=false;var writes=0
 override suspend fun getBool(key:String):Boolean?=null
 override suspend fun getString(key:String)=data[key]
 override suspend fun putBool(key:String,value:Boolean)=true
 override suspend fun putString(key:String,value:String):Boolean { writes++;if(fail)return false;data[key]=value;return true }
}
class BundleApplyScopeTest {
 @Test fun missingDefaultUsesIosButExplicitLegacyIdIsNeverRewritten()=runBlocking {
  for(saved in listOf(null,"chaty_default","apple_ios")) {
   val storage=ScopeStorage();if(saved!=null)storage.data[CallPresentationPreferencesStore.CALL_EXPERIENCE_BUNDLE_KEY]=saved
   val job=SupervisorJob();val store=CallPresentationPreferencesStore(storage,CoroutineScope(coroutineContext+job))
   store.initialize();yield();assertEquals(saved?:"apple_ios",store.value.callExperienceBundleId);assertEquals(0,storage.writes);job.cancel()
  }
 }

 @Test fun bothUsesOneCommitAndMessagesPreservesCalls()=runBlocking {
  val storage=ScopeStorage();val job=SupervisorJob();val scope=CoroutineScope(coroutineContext+job);val store=CallPresentationPreferencesStore(storage,scope)
  store.applyExperienceBundle("apple_ios",BundleApplyTarget.BOTH);assertEquals(1,storage.writes);assertEquals("apple_ios",store.value.messageExperienceBundleId)
  store.applyExperienceBundle("mono_chevron_dark",BundleApplyTarget.MESSAGES);assertEquals("apple_ios",store.value.callExperienceBundleId)
  assertEquals("mono_chevron_dark",store.value.messageExperienceBundleId)
  store.applyExperienceBundle("samsung_one_ui",BundleApplyTarget.CALLS);assertEquals("mono_chevron_dark",store.value.messageExperienceBundleId);job.cancel()
 }
 @Test fun failedAtomicWritePublishesNothingAndRestoresPair()=runBlocking {
  val storage=ScopeStorage();val job=SupervisorJob();val scope=CoroutineScope(coroutineContext+job);val store=CallPresentationPreferencesStore(storage,scope)
  store.applyExperienceBundle("apple_ios",BundleApplyTarget.BOTH);storage.fail=true
  try {store.applyExperienceBundle("vivo_iqoo",BundleApplyTarget.BOTH);fail("failure expected")} catch(_:IllegalStateException) {}
  assertEquals("apple_ios",store.value.callExperienceBundleId);assertEquals("apple_ios",store.value.messageExperienceBundleId)
  storage.fail=false;val restored=CallPresentationPreferencesStore(storage,scope);restored.initialize();yield();assertEquals(store.value,restored.value);job.cancel()
 }
 @Test fun legacyCallChoiceSurvivesUntilExplicitMessageApply()=runBlocking {
  val storage=ScopeStorage();storage.data[CallPresentationPreferencesStore.CALL_EXPERIENCE_BUNDLE_KEY]="vivo_iqoo"
  val job=SupervisorJob();val scope=CoroutineScope(coroutineContext+job);val store=CallPresentationPreferencesStore(storage,scope);store.initialize();yield()
  assertEquals("vivo_iqoo",store.value.callExperienceBundleId);assertEquals("",store.value.messageExperienceBundleId)
  try{store.applyExperienceBundle("unknown|id",BundleApplyTarget.BOTH);fail("reject expected")}catch(_:IllegalArgumentException){};assertEquals(0,storage.writes);job.cancel()
 }
}
