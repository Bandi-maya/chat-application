package com.example.chat
import com.example.chat.ui.profile.*
import org.junit.Assert.*
import org.junit.Test
class ProfileLocalOutputOwnerTest {
    @Test fun registerBeforeCreateAndTransferDoesNotClearIndex() {
        var persisted=emptySet<String>();val o=ProfileLocalOutputOwner({persisted=it},{true})
        o.record("crop");assertEquals(setOf("crop"),persisted)
        // In-memory picker -> draft transfer requires no erase, so restart can clean it.
        val restored=ProfileLocalOutputOwner({persisted=it},{true},persisted)
        restored.restoreCleanup();assertTrue(persisted.isEmpty())
    }
    @Test fun persistenceOrDeleteFailureRetainsRecoverableOwnership() {
        var failure=true;val o=ProfileLocalOutputOwner({if(failure)error("disk")},{true})
        assertTrue(runCatching{o.record("a")}.isFailure);assertFalse(o.contains("a"))
        failure=false;o.record("a");failure=true
        assertTrue(runCatching{o.remove("a")}.isFailure);assertTrue(o.contains("a"))
        val blocked=ProfileLocalOutputOwner({},{false},setOf("b"))
        assertTrue(runCatching{blocked.restoreCleanup()}.isFailure);assertTrue(blocked.contains("b"))
    }
    @Test fun cameraToLocalHandoffNeverHasAnUnindexedWindow() {
        var terminal:String?=null;var local=emptySet<String>()
        val camera=ProfileCameraTerminalOwner({p,_->terminal=p},{true})
        val output=ProfileLocalOutputOwner({local=it},{true})
        camera.begin("a");val result=camera.complete(true)!!
        assertEquals("a",terminal);output.record(result);assertEquals(setOf("a"),local)
        camera.acknowledgeLocalHandoff(result);assertNull(terminal);assertEquals(setOf("a"),local)
    }
    @Test fun restorationAttemptsAllKnownOutputsAndRetainsOnlyFailures() {
        val attempted=mutableListOf<String>()
        val o=ProfileLocalOutputOwner({}, { attempted+=it; it!="bad" },linkedSetOf("bad","good"))
        assertTrue(runCatching{o.restoreCleanup()}.isFailure)
        assertEquals(listOf("bad","good"),attempted);assertEquals(setOf("bad"),o.snapshot())
    }
}
