package com.example.chat
import com.example.chat.data.api.CallSignalDeduplicator
import org.junit.Assert.*
import org.junit.Test
class CallSignalDeduplicatorTest {
 @Test fun socketAndQueuedOrderDeduplicateAndScopeSwitchDoesNot() {
  val d=CallSignalDeduplicator()
  assertTrue(d.accept(1,mapOf("call_id" to "c", "sdp" to mapOf("sdp" to "raw")),0))
  assertFalse(d.accept(1,mapOf("sdp" to mapOf("sdp" to "raw"),"call_id" to "c"),1))
  assertTrue(d.accept(2,mapOf("call_id" to "c","sdp" to mapOf("sdp" to "raw")),2))
 }
 @Test fun typeTaggedCanonicalDoesNotCollapseStringsNumbersOrNestedStructures() {
  val d=CallSignalDeduplicator()
  assertTrue(d.accept(1,mapOf("v" to "true"),0));assertTrue(d.accept(1,mapOf("v" to true),1))
  assertTrue(d.accept(1,mapOf("v" to 1),2));assertTrue(d.accept(1,mapOf("v" to "1"),3))
  assertTrue(d.accept(1,mapOf("v" to listOf("a,b")),4));assertTrue(d.accept(1,mapOf("v" to listOf("a","b")),5))
 }
 @Test fun boundedTtlAndExplicitClear() {
  val d=CallSignalDeduplicator();val s=mapOf("call_id" to "c")
  assertTrue(d.accept(1,s,0));assertTrue(d.accept(1,s,120001));d.clear();assertTrue(d.accept(1,s,120002))
 }
}
