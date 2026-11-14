package com.example.chat
import com.example.chat.island.core.NotificationDismissPolicy as P
import org.junit.Assert.*
import org.junit.Test
class NotificationDismissPolicyTest {
    private var now=0L
    private val p=P{now}
    private val ok=P.Eligibility(true,true)
    @Test fun offerAloneNeverCancels() { p.offer("a",ok);assertFalse(p.consumeOwnRemoval("a")) }
    @Test fun exactDrawOnceThenPreserveOwnRemovalOnce() { val r=p.offer("a",ok);assertFalse(p.displayed("b",r,true){fail()});var n=0;assertTrue(p.displayed("a",r,true){n++});assertFalse(p.displayed("a",r,true){fail()});assertEquals(1,n);assertTrue(p.consumeOwnRemoval("a"));assertFalse(p.consumeOwnRemoval("a")) }
    @Test fun exclusionsNeverCancel() { for(e in listOf(ok.copy(enabled=false),ok.copy(ordinaryMessage=false),ok.copy(ongoing=true),ok.copy(service=true),ok.copy(alarmOrCall=true),ok.copy(progress=true),ok.copy(groupSummary=true),ok.copy(ownPackage=true),ok.copy(dismissible=false))) { val r=p.offer("a",e);assertNull(r);assertFalse(p.displayed("a",r,true){fail()}) } }
    @Test fun hiddenOrDisabledHostDoesNotCancel() { val r=p.offer("a",ok);assertFalse(p.displayed("a",r,false){fail()}) }
    @Test fun staleQueuedCardCannotCancelLater() { val r=p.offer("a",ok);now=10_001;assertFalse(p.displayed("a",r,true){fail()}) }
    @Test fun cancellationFailureDoesNotSwallowRealRemoval() { val r=p.offer("a",ok);assertFalse(p.displayed("a",r,true){throw SecurityException()});assertFalse(p.consumeOwnRemoval("a")) }
    @Test fun privacyClearDropsPendingAndOwnMarkers() { val a=p.offer("a",ok);val b=p.offer("b",ok);p.displayed("a",a,true){};p.clear();assertFalse(p.consumeOwnRemoval("a"));assertFalse(p.displayed("b",b,true){fail()}) }
    @Test fun ownMarkerExpires() { val r=p.offer("a",ok);p.displayed("a",r,true){};now=10_001;assertFalse(p.consumeOwnRemoval("a")) }
    @Test fun queueIsBounded() { val r=p.offer("old",ok);var last:Long?=null;repeat(256){last=p.offer("$it",ok)};assertFalse(p.displayed("old",r,true){fail()});assertTrue(p.displayed("255",last,true){}) }
    @Test fun realRemovalBeforeDrawDisarms() {val r=p.offer("a",ok);assertFalse(p.consumeOwnRemoval("a"));assertFalse(p.displayed("a",r,true){fail()})}
    @Test fun oldDrawOfSameKeyCannotDismissNewUnseenUpdate() { val old=p.offer("a",ok);val next=p.offer("a",ok);assertFalse(p.displayed("a",old,true){fail()});assertTrue(p.displayed("a",next,true){}) }
    @Test fun missingDrawReceiptCannotCancel() {p.offer("a",ok);assertFalse(p.displayed("a",null,true){fail()})}
}
