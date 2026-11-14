package com.example.chat

import com.example.chat.data.media.MediaDownloadEvent as E
import com.example.chat.data.media.MediaDownloadFormat
import com.example.chat.data.media.MediaDownloadReducer
import com.example.chat.data.media.MediaDownloadState as S
import com.example.chat.data.media.ResumePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDownloadStateTest {
    private fun run(start: S, vararg ev: E) = ev.fold(start) { s, e -> MediaDownloadReducer.reduce(s, e) }

    @Test fun `tap download progress finish`() {
        val s = run(S.Idle(2_400_000), E.Start, E.Progress(1_500_000, 2_400_000, 250_000))
        assertEquals(S.Downloading(1_500_000, 2_400_000, 250_000), s)
        assertEquals(S.Done, MediaDownloadReducer.reduce(s, E.Finished))
    }
    @Test fun `network loss pauses and auto resumes but user pause does not`() {
        val net = run(S.Idle(100), E.Start, E.Progress(40, 100, 1), E.NetworkLost)
        assertEquals(S.Paused(40, 100, S.PauseReason.NO_NETWORK), net)
        assertEquals(S.Queued(100), MediaDownloadReducer.reduce(net, E.NetworkBack))
        val user = run(S.Idle(100), E.Start, E.Progress(40, 100, 1), E.UserPause, E.NetworkBack)
        assertEquals(S.Paused(40, 100, S.PauseReason.USER), user)
    }
    @Test fun `progress never goes backwards and is dropped after pause`() {
        val s = run(S.Idle(100), E.Start, E.Progress(50, 100, 1), E.Progress(30, 100, 1))
        assertEquals(50L, (s as S.Downloading).doneBytes)
        assertEquals(S.Paused(50, 100, S.PauseReason.USER), MediaDownloadReducer.reduce(MediaDownloadReducer.reduce(s, E.UserPause), E.Progress(90, 100, 1)))
    }
    @Test fun `cancel returns to idle and done is terminal`() {
        assertEquals(S.Idle(100), run(S.Idle(100), E.Start, E.Progress(10, 100, 1), E.UserCancel))
        assertEquals(S.Done, MediaDownloadReducer.reduce(S.Done, E.UserCancel))
    }
    @Test fun `error keeps partial bytes for retry`() {
        val f = run(S.Idle(100), E.Start, E.Progress(10, 100, 1), E.Error(S.FailReason.NETWORK))
        assertEquals(S.Failed(10, 100, S.FailReason.NETWORK), f)
        assertEquals(S.Queued(100), MediaDownloadReducer.reduce(f, E.Start))
    }
    @Test fun `labels`() {
        assertEquals("1.5 MB of 2.4 MB  62%", MediaDownloadFormat.label(S.Downloading(1_500_000, 2_400_000, 1)))
        assertEquals(62, MediaDownloadFormat.percent(1_500_000, 2_400_000))
        assertNull(MediaDownloadFormat.percent(5, null))
        assertEquals("about 4 s left", MediaDownloadFormat.eta(1_500_000, 2_400_000, 225_000))
        assertEquals("Paused 38%  waiting for network", MediaDownloadFormat.label(S.Paused(38, 100, S.PauseReason.NO_NETWORK)))
    }
    @Test fun `resume plan trusts only proven identical bytes`() {
        val a = "\"a\""; val b = "\"b\""
        assertEquals(ResumePlan.Action.Append(1000), ResumePlan.decide(400, 206, "bytes 400-999/1000", 600, a, a))
        // either validator missing -> never append
        assertEquals(ResumePlan.Action.RestartFresh, ResumePlan.decide(400, 206, "bytes 400-999/1000", 600, null, a))
        assertEquals(ResumePlan.Action.RestartFresh, ResumePlan.decide(400, 206, "bytes 400-999/1000", 600, a, null))
        assertEquals(ResumePlan.Action.RestartFresh, ResumePlan.decide(400, 206, "bytes 400-999/1000", 600, null, null))
        // different validator, weak validator, wrong offset, garbage Content-Range
        assertEquals(ResumePlan.Action.RestartFresh, ResumePlan.decide(400, 206, "bytes 400-999/1000", 600, a, b))
        assertEquals(ResumePlan.Action.RestartFresh, ResumePlan.decide(400, 206, "bytes 400-999/1000", 600, "W/\"a\"", "W/\"a\""))
        assertEquals(ResumePlan.Action.RestartFresh, ResumePlan.decide(400, 206, "bytes 0-999/1000", 1000, a, a))
        assertEquals(ResumePlan.Action.RestartFresh, ResumePlan.decide(400, 206, "nonsense", 600, a, a))
        // 200 body is whole: restart in place
        assertEquals(ResumePlan.Action.Restart(1000), ResumePlan.decide(400, 200, null, 1000, a, a))
        // 416 completes only with proof, else restart
        assertEquals(ResumePlan.Action.AlreadyComplete, ResumePlan.decide(1000, 416, "bytes */1000", null, a, a))
        assertEquals(ResumePlan.Action.RestartFresh, ResumePlan.decide(1000, 416, "bytes */1000", null, null, a))
        assertEquals(ResumePlan.Action.RestartFresh, ResumePlan.decide(500, 416, "bytes */1000", null, a, a))
        assertEquals(ResumePlan.Action.Fail(S.FailReason.SERVER_GONE), ResumePlan.decide(0, 404, null, null, null, null))
        assertEquals(ResumePlan.Action.Fail(S.FailReason.NOT_ALLOWED), ResumePlan.decide(0, 403, null, null, null, null))
        assertTrue(ResumePlan.canTryResume(5, a)); assertTrue(!ResumePlan.canTryResume(5, null)); assertTrue(!ResumePlan.canTryResume(5, "W/\"a\""))
        assertTrue(!ResumePlan.canTryResume(0, a))
    }
    @Test fun `size text parses to bytes or null`() {
        assertEquals(2_400_000L, MediaDownloadFormat.parseBytes("2.4 MB"))
        assertEquals(512_000L, MediaDownloadFormat.parseBytes("512KB"))
        assertEquals(1234L, MediaDownloadFormat.parseBytes("1234"))
        assertNull(MediaDownloadFormat.parseBytes(""))
        assertNull(MediaDownloadFormat.parseBytes("big"))
    }
}
