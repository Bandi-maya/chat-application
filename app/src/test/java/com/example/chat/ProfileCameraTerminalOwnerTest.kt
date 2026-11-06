package com.example.chat
import com.example.chat.ui.profile.ProfileCameraTerminalOwner
import org.junit.Assert.*
import org.junit.Test
class ProfileCameraTerminalOwnerTest {
    @Test fun retireDoesNotDeleteUntilTerminalAndBlocksReplacement() {
        val saved=mutableListOf<String?>();val deleted=mutableListOf<String>()
        val o=ProfileCameraTerminalOwner({p,_->saved+=p},{deleted+=it; true})
        o.begin("a");o.retire();assertTrue(deleted.isEmpty())
        assertTrue(runCatching{o.begin("b")}.isFailure)
        assertNull(o.complete(true));assertEquals(listOf("a"),deleted)
        assertEquals(listOf("a","a",null),saved);assertFalse(o.hasPending())
    }
    @Test fun restoredSuccessIsCleanupOnly() {
        val deleted=mutableListOf<String>();val o=ProfileCameraTerminalOwner({_,_->}, {deleted+=it; true}, "restored")
        assertNull(o.complete(true));assertEquals(listOf("restored"),deleted)
    }
    @Test fun originalSuccessTransfersOnceAndNoDelete() {
        val deleted=mutableListOf<String>();val o=ProfileCameraTerminalOwner({_,_->}, {deleted+=it; true})
        o.begin("a");assertEquals("a",o.complete(true));assertTrue(o.hasPending());o.acknowledgeLocalHandoff("a");assertNull(o.complete(true));assertTrue(deleted.isEmpty())
    }
    @Test fun launchFailureIsTerminalAndAllowsNewRequest() {
        val deleted=mutableListOf<String>();val o=ProfileCameraTerminalOwner({_,_->}, {deleted+=it; true})
        o.begin("a");o.launchFailed();assertEquals(listOf("a"),deleted);o.begin("b")
    }
    @Test fun persistenceFailureNeverStartsWriter() {
        val o=ProfileCameraTerminalOwner({_,_->error("disk")},{fail(); false})
        assertTrue(runCatching{o.begin("a")}.isFailure);assertFalse(o.hasPending())
    }
    @Test fun deleteFailureKeepsOwnershipAndReregistrationRetriesOnlyTerminal() {
        var succeeds=false; var saves=0
        val o=ProfileCameraTerminalOwner({_,_->saves++},{succeeds})
        o.begin("a");o.retire();o.retryTerminalCleanup();assertEquals(1,saves)
        assertTrue(runCatching{o.complete(true)}.isFailure);assertTrue(o.hasPending())
        succeeds=true;o.retryTerminalCleanup();assertFalse(o.hasPending());assertEquals(3,saves)
    }
    @Test fun clearPersistenceFailureKeepsCleanupTombstoneWithoutResult() {
        var clearFails=true;val deleted=mutableListOf<String>()
        val o=ProfileCameraTerminalOwner({p,_->if(p==null&&clearFails)error("disk")},{deleted+=it;true})
        o.begin("a");assertEquals("a",o.complete(true));assertTrue(runCatching{o.acknowledgeLocalHandoff("a")}.isFailure)
        assertTrue(o.hasPending());assertTrue(deleted.isEmpty())
        clearFails=false;o.retryTerminalCleanup();assertEquals(listOf("a"),deleted);assertFalse(o.hasPending())
    }
    @Test fun restoredTerminalRetriesButRestoredWriterNeverDeletes() {
        val deleted=mutableListOf<String>()
        val terminal=ProfileCameraTerminalOwner({_,_->},{deleted+=it;true},"a",true)
        terminal.retryTerminalCleanup();assertEquals(listOf("a"),deleted)
        val writer=ProfileCameraTerminalOwner({_,_->},{deleted+=it;true},"b",false)
        writer.retryTerminalCleanup();assertTrue(writer.hasPending());assertEquals(listOf("a"),deleted)
    }
}
