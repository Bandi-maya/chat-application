package com.example.chat.ui.camera
import org.junit.Assert.*
import org.junit.Test
class VideoHoldRequestTest {
 @Test fun releaseBeforeReadyNeverStartsLater(){val h=VideoHoldRequest{true};assertNull(h.next(false,false));h.release();assertNull(h.next(true,true));assertNull(h.next(true,true))}
 @Test fun cancelBeforePermissionNeverStartsLater(){val h=VideoHoldRequest{true};h.cancel();assertNull(h.next(true,true))}
 @Test fun readyFrontStartsOnlyOnceReleaseStopsNoSend(){val h=VideoHoldRequest{true};assertNull(h.next(true,false));assertEquals(VideoHoldRequest.Command.Start,h.next(true,true));assertNull(h.next(true,true));h.release();assertEquals(VideoHoldRequest.Command.Stop,h.next(false,false));assertNull(h.next(true,true))}
 @Test fun cancelAfterStartDiscardsNotStopsIntoReview(){val h=VideoHoldRequest{true};assertEquals(VideoHoldRequest.Command.Start,h.next(true,true));h.cancel();assertEquals(VideoHoldRequest.Command.Discard,h.next(false,false));assertNull(h.next(true,true))}
 @Test fun lockedReleaseKeepsRecordingUntilNativeStop(){val h=VideoHoldRequest{true};h.lock();assertEquals(VideoHoldRequest.Command.Start,h.next(true,true));h.release();assertEquals(VideoHoldRequest.Phase.Locked,h.phase.value);assertNull(h.next(false,true));h.cancel();assertEquals(VideoHoldRequest.Command.Discard,h.next(false,true))}
 @Test fun accountEntryChangedBeforeBindDeniesStart(){var current=true;val h=VideoHoldRequest{current};current=false;assertNull(h.next(true,true));current=true;assertNull(h.next(true,true))}
 @Test fun disposalCancelsPendingAndStarted(){val a=VideoHoldRequest{true};a.close();assertNull(a.next(true,true));val b=VideoHoldRequest{true};b.next(true,true);b.close();assertEquals(VideoHoldRequest.Command.Discard,b.next(true,true))}
}
