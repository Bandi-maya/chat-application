package com.example.chat.ui.camera
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
class CameraOwnedFilesTest {
 private fun file()=Files.createTempFile("owned-fixture-",".jpg").toFile().apply{writeText("synthetic bytes")}
 @Test fun importedOriginalNeverRegisteredSurvivesDiscard(){val original=file();val copy=file();val ledger=CameraOwnedFiles();ledger.own(copy);ledger.discard();assertTrue(original.exists());assertFalse(copy.exists());original.delete()}
 @Test fun transferKeepsFinalDeletesSiblingsAndDisposeCannotDeleteTransferred(){val raw=file();val derived=file();val ledger=CameraOwnedFiles();ledger.own(raw);ledger.own(derived);val result=ledger.transfer(derived.path)!!;assertFalse(raw.exists());assertTrue(derived.exists());ledger.close();assertTrue(derived.exists());result.close();assertFalse(derived.exists())}
 @Test fun closeThenLateNativeCompletionRegistrationDeletesLateFile(){val ledger=CameraOwnedFiles();val raw=file();ledger.own(raw);ledger.close();assertFalse(raw.exists());raw.writeText("late native callback");assertFalse(ledger.own(raw));assertFalse(raw.exists())}
 @Test fun unknownPathsCannotTransferOrDelete(){val original=file();val ledger=CameraOwnedFiles();assertNull(ledger.transfer(original.path));ledger.close();assertTrue(original.exists());original.delete()}
 @Test fun discardedLedgerAcceptsNewRetakeButClosedDoesNot(){val ledger=CameraOwnedFiles();val first=file();ledger.own(first);ledger.discard();val second=file();assertTrue(ledger.own(second));ledger.close();assertFalse(second.exists());val late=file();assertFalse(ledger.own(late));assertFalse(late.exists())}
}
