package com.example.chat

import com.example.chat.data.api.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CallScopeGateTest {
 private fun scope(g:Long)=CallSignalScope("me","https://example.invalid","same-token",g)
 @Test fun registerAfterLogoutCancelsBeforeAnyLaunch() {
  val gate=CallScopeGate();val old=scope(1);gate.replace(old);gate.replace(null)
  var canceled=false;assertFalse(gate.register(old){canceled=true});assertTrue(canceled)
 }
 @Test fun sameTokenAbaDoesNotAdoptOldScope() {
  val gate=CallScopeGate();val old=scope(1);gate.replace(old);gate.replace(scope(2))
  assertFalse(gate.isCurrent(old));assertFalse(gate.mutate(old){fail("Old backoff must not mutate")})
 }
 @Test fun scopeSwitchDuringBatchNeverRelabelsRemainingOldSignals() {
  val gate=CallScopeGate();val old=scope(1);gate.replace(old);var count=0
  assertFalse(gate.deliver(old,listOf(mapOf("i" to 1),mapOf("i" to 2))){count++;gate.replace(scope(2))})
  assertEquals(1,count)
 }
 @Test fun concurrentSwitchWaitsUntilFinalDeliveryCompletes() {
  val gate=CallScopeGate();val old=scope(1);gate.replace(old)
  val entered=CountDownLatch(1);val release=CountDownLatch(1);val switched=CountDownLatch(1)
  val delivery=Thread{gate.deliver(old,listOf(mapOf("id" to "x"))){entered.countDown();assertTrue(release.await(2,TimeUnit.SECONDS))}}
  delivery.start();assertTrue(entered.await(2,TimeUnit.SECONDS))
  val switcher=Thread{gate.replace(scope(2));switched.countDown()};switcher.start()
  assertFalse(switched.await(30,TimeUnit.MILLISECONDS));release.countDown();delivery.join(2000);switcher.join(2000)
  assertEquals(0,switched.count);assertFalse(gate.isCurrent(old))
 }
 @Test fun resetCancelsAllRegisteredWorkExactlyOnce() {
  val gate=CallScopeGate();val old=scope(1);gate.replace(old);var canceled=0
  val cancel:()->Unit={canceled++};assertTrue(gate.register(old,cancel));gate.replace(null);gate.replace(null)
  assertEquals(1,canceled)
 }
}
