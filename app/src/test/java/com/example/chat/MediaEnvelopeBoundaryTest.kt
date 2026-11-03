package com.example.chat
import com.example.chat.domain.models.ChatMessage
import com.example.chat.data.local.AttachmentCachePolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class MediaEnvelopeBoundaryTest {
 private fun parse(e: JSONObject) = ChatMessage.fromApi(mapOf("id" to "api-id", "room_id" to "api-room", "sender_id" to "api-sender", "sender_name" to "API sender", "content" to e.put("type", "image").put("url", "synthetic-local").put("text", "Caption").toString()))
 @Test fun batchAndProtectionRoundTrip() {
  val m=parse(JSONObject().put("chaty_album_v",1).put("chaty_album_id","synthetic").put("chaty_album_index",0).put("chaty_album_count",2).put("view_once",true).put("forwarded",true))
  assertEquals("synthetic",m.metadata["chaty_album_id"]);assertEquals(2,m.metadata["chaty_album_count"])
  assertTrue(AttachmentCachePolicy.viewOnceRestricted(m.metadata));assertEquals(true,m.metadata["forwarded"]);assertEquals("Caption",m.text)
 }
 @Test fun envelopeCannotReplaceApiIdentityOrSenderLabel() {
  val m=parse(JSONObject().put("id","attachment-id").put("uid","spoof").put("room_id","spoof").put("conversation_id","spoof").put("sender_id","spoof").put("sender_name","spoof").put("metadata",JSONObject().put("view_once",false)).put("view_once",true))
  assertEquals("api-id",m.id);assertEquals("api-room",m.conversationId);assertEquals("api-sender",m.senderId);assertEquals("attachment-id",m.attachment!!.id);assertEquals("API sender",m.metadata["sender_name"])
  assertTrue(AttachmentCachePolicy.viewOnceRestricted(m.metadata));for(k in listOf("id","uid","room_id","conversation_id","sender_id","type","url","metadata"))assertFalse(k,m.metadata.containsKey(k))
 }
 @Test fun plaintextInviteMaterialExcludedEncryptedMaterialRetained() {
  val e=JSONObject();val denied=listOf("secret","token","invite_secret","link_secret","invite_token","transfer_secret","invite_url","transfer_uri","p2p_invite_secret","p2p_invite_url","SECRET")
  denied.forEach{e.put(it,"do-not-retain")};val safe=listOf("p2p_invite_ciphertext","p2p_invite_group","p2p_invite_epoch","p2p_transfer_version");safe.forEach{e.put(it,if(it=="p2p_invite_epoch") 3 else if(it=="p2p_transfer_version") 1 else "encrypted-hint")}
  val m=parse(e);denied.forEach{assertFalse(it,m.metadata.containsKey(it))};safe.forEach{assertEquals(it,if(it=="p2p_invite_epoch") 3 else if(it=="p2p_transfer_version") 1 else "encrypted-hint",m.metadata[it])}
 }
 @Test fun unknownHintsDoNotOverrideProtectionOrGrantActions() {
  val m=parse(JSONObject().put("future_flag",JSONObject().put("admin",true)).put("view_once",JSONObject().put("value",false)).put("can_forward",true))
  assertFalse(m.metadata.containsKey("future_flag"));assertEquals(true,m.metadata["unsupported_envelope_metadata"]);assertTrue(AttachmentCachePolicy.viewOnceRestricted(m.metadata));assertFalse(AttachmentCachePolicy.mayPersist(AttachmentCachePolicy.viewOnceRestricted(m.metadata),false))
 }
 @Test fun unknownHintsAndProtectionCannotEnableAlbumGrouping() {
  fun pair(extra: String?) = (0..1).map { i ->
   var m=parse(JSONObject().put("chaty_album_v",1).put("chaty_album_id","batch").put("chaty_album_index",i).put("chaty_album_count",2))
   m=m.copy(id="m$i",attachment=m.attachment!!.copy(id="a$i"),text=if(i==0) "Caption" else "")
   if(extra==null)m else m.copy(metadata=m.metadata+mapOf(extra to true))
  }
  assertEquals(1,com.example.chat.ui.chats.detail.mediaTimelineRows(pair(null)).size)
  for(k in listOf("view_once","can_forward","future_flag"))assertEquals(k,2,com.example.chat.ui.chats.detail.mediaTimelineRows(pair(k)).size)
 }

 @Test fun arbitrarySecretBearingFieldsAreNotRetained() {
  val denied=listOf("auth_token","access_token","session_token","password","api_key","authorization","cookie","future_flag")
  val e=JSONObject();denied.forEach{e.put(it,JSONObject().put("sensitive","do-not-retain"))}
  val m=parse(e);denied.forEach{assertFalse(it,m.metadata.containsKey(it))};assertEquals(true,m.metadata["unsupported_envelope_metadata"])
  assertFalse(m.metadata.values.any{it is JSONObject || it is org.json.JSONArray})
 }
 @Test fun protectionSpellingConflictsAlwaysFailClosedWithoutValueRetention() {
  for(e in listOf(JSONObject().put("VIEW_ONCE",false),JSONObject().put("view_once",false).put("View_Once",false),JSONObject().put("view_once",JSONObject().put("password","do-not-retain")))) {
   val m=parse(e);assertEquals(true,m.metadata["view_once"]);assertTrue(AttachmentCachePolicy.viewOnceRestricted(m.metadata));assertFalse(m.metadata.containsKey("VIEW_ONCE"));assertFalse(m.metadata.containsKey("View_Once"))
  }
 }

 @Test fun malformedAllowedHintsAreNotInventedOrRetainedAsNestedPayload() {
  val invalid=listOf("forwarded" to "true","chaty_album_v" to 2,"chaty_album_count" to 1,"chaty_album_index" to 0.5,"chaty_album_id" to " ","p2p_transfer_version" to 2,"p2p_invite_epoch" to -1,"p2p_invite_ciphertext" to JSONObject().put("password","do-not-retain"),"FORWARDED" to true)
  for((key,value) in invalid) {val m=parse(JSONObject().put(key,value));assertFalse(key,m.metadata.containsKey(key));assertEquals(key,true,m.metadata["unsupported_envelope_metadata"])}
 }

}
