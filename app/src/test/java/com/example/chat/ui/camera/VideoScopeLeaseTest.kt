package com.example.chat.ui.camera

import org.junit.Assert.*
import org.junit.Test

class VideoScopeLeaseTest {
    private class Port:VideoAuthPort {
        var current:Any=Any()
        val listeners=mutableListOf<(Any)->Unit>()
        override fun snapshot()=current
        override fun isSignedIn(snapshot:Any)=true
        override fun withScope(expected:Any,block:()->Unit):Boolean { if(current!==expected)return false;block();return true }
        override fun addListener(listener:(Any)->Unit){listeners.add(listener);listener(current)}
        override fun removeListener(listener:(Any)->Unit){listeners.remove(listener)}
        fun transition(next:Any){current=next;listeners.toList().forEach{it(next)}}
    }
    @Test fun aToBToAInvalidatesPermanentlyBeforePostedCleanup() {
        val port=Port();val original=port.current;var posted=0;var published=0
        val lease=VideoScopeLease(port){posted++}
        assertTrue(lease.runIfCurrent{published++})
        port.transition(Any());port.transition(original)
        assertEquals(1,posted)
        assertFalse(lease.runIfCurrent{published++})
        assertEquals(1,published)
        lease.close();assertEquals(0,port.listeners.size)
    }
    @Test fun delayedFinalizeAndPlaybackAreDeniedWhenMainCleanupHasNotRun() {
        val port=Port();val pending=mutableListOf<()->Unit>();var reviewed=0;var played=0
        val lease=VideoScopeLease(port){pending.add({})}
        val finalize={lease.runIfCurrent{reviewed++}}
        val prepared={lease.runIfCurrent{played++}}
        port.transition(Any())
        assertEquals(1,pending.size)
        assertFalse(finalize());assertFalse(prepared())
        assertEquals(0,reviewed);assertEquals(0,played)
        lease.close()
    }
    @Test fun immediateRegistrationCallbackClosesEntryRace() {
        val port=object:VideoAuthPort {
            val a=Any();val b=Any()
            override fun snapshot()=a
            override fun isSignedIn(snapshot:Any)=true
            override fun withScope(expected:Any,block:()->Unit)=false
            override fun addListener(listener:(Any)->Unit){listener(b)}
            override fun removeListener(listener:(Any)->Unit){}
        }
        var invalid=0;val lease=VideoScopeLease(port){invalid++}
        assertEquals(1,invalid);assertFalse(lease.isCurrent());lease.close()
    }
}
