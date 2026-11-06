package com.example.chat
import com.example.chat.ui.profile.ProfilePickFiles
import java.io.File
import org.junit.Assert.*
import org.junit.Test
class ProfilePickFilesTest {
    private fun file() = File.createTempFile("profile-pick", ".png").apply { writeBytes(byteArrayOf(1)) }
    @Test fun retiredWriterDefersDeleteUntilTerminalRelease() {
        val f = file(); val o = ProfilePickFiles(); o.track(f); o.begin(f)
        o.close(); assertTrue(f.exists()); assertFalse(o.isActive())
        assertFalse(o.transfer(f)); o.end(f); assertFalse(f.exists()); o.close()
    }
    @Test fun retiredReaderDefersDeleteAndDeniesNewReader() {
        val f = file(); val o = ProfilePickFiles(); o.track(f); o.begin(f)
        o.close(); assertFalse(o.tryBegin(f)); assertTrue(runCatching { o.begin(f) }.isFailure)
        assertTrue(f.exists()); o.end(f); assertFalse(f.exists())
    }
    @Test fun transferredCropSurvivesPickerDisposal() {
        val f = file(); val o = ProfilePickFiles(); o.track(f)
        assertTrue(o.transfer(f)); o.close(); assertTrue(f.exists()); f.delete()
    }
    @Test fun unclaimedOutputIsDeletedOnCancellationAndLateOutputDenied() {
        val f = file(); val o = ProfilePickFiles(); o.track(f); o.close(); assertFalse(f.exists())
        val late = file(); assertTrue(runCatching { o.track(late) }.isFailure); late.delete()
    }
}
