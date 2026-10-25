package com.example.chat

import com.example.chat.ui.island.DynamicIslandController
import com.example.chat.ui.island.IslandPayloadType
import com.example.chat.ui.island.IslandTimerHandle
import com.example.chat.ui.island.IslandTimerScheduler
import com.example.chat.core.audio.AudioOutputRoute
import com.example.chat.core.audio.AudioPolicyDiagnosticEvent
import com.example.chat.core.audio.AudioPolicyGate
import com.example.chat.core.audio.AudioSessionState
import com.example.chat.core.audio.AudioUseCase
import com.example.chat.ui.media.ChatyRepeatMode
import com.example.chat.ui.media.MediaAudioPlayer
import com.example.chat.ui.media.MediaCategory
import com.example.chat.ui.media.MediaFileDescriptor
import com.example.chat.ui.media.MediaPlayerCoordinator
import com.example.chat.ui.media.MediaPlayerState
import com.example.chat.ui.media.MediaProcessingState
import com.example.chat.ui.media.PlaybackStatus
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behavior tests written from the Dart source's observable behavior in
 *  lib/features/media/services/media_player_coordinator.dart. */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaPlayerCoordinatorParityTest {

    private class FakePlayer : MediaAudioPlayer {
        override val positionStream = MutableSharedFlow<Duration>(extraBufferCapacity = 16)
        override val durationStream = MutableSharedFlow<Duration?>(extraBufferCapacity = 16)
        override val bufferedPositionStream = MutableSharedFlow<Duration>(extraBufferCapacity = 16)
        override val playerStateStream = MutableSharedFlow<MediaPlayerState>(extraBufferCapacity = 16)
        val calls = mutableListOf<String>()
        override suspend fun stop() { calls.add("stop") }
        override suspend fun setFilePath(path: String) { calls.add("setFilePath:$path") }
        override suspend fun setSpeed(speed: Double) { calls.add("setSpeed:$speed") }
        override suspend fun play() { calls.add("play") }
        override suspend fun pause() { calls.add("pause") }
        override suspend fun seek(position: Duration) { calls.add("seek:${position.inWholeMilliseconds}") }
        override fun dispose() { calls.add("dispose") }
    }

    private class FakePolicy(
        var acquireResult: Boolean = true,
        override var state: AudioSessionState = AudioSessionState.idle,
        override var activeOwner: AudioUseCase? = null,
    ) : AudioPolicyGate {
        val listeners = mutableListOf<() -> Unit>()
        val stops = mutableListOf<Unit>()
        override fun addListener(listener: () -> Unit) { listeners.add(listener) }
        override fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
        override suspend fun startMediaSession(): Boolean = acquireResult
        override suspend fun stopMediaSession() { stops.add(Unit) }
        override val activeRoute: AudioOutputRoute = AudioOutputRoute.systemDefault
        override val diagnostics: List<AudioPolicyDiagnosticEvent> = emptyList()
        override suspend fun startCallSession(initialRoute: AudioOutputRoute): Boolean = false
        override suspend fun setCallRoute(route: AudioOutputRoute): Boolean = false
        override suspend fun playNotificationSound(playerAction: suspend () -> Unit) { playerAction() }
        override suspend fun endCallSession() {}
        override fun onInterruption(began: Boolean) {}
        fun emit() { listeners.toList().forEach { it() } }
    }

    private class NoopScheduler : IslandTimerScheduler {
        override fun schedule(delayMs: Long, action: () -> Unit) = IslandTimerHandle {}
    }

    private fun track(id: String, name: String = id, metadata: Map<String, Any?> = emptyMap()) =
        MediaFileDescriptor(
            id = id, name = name, path = "/media/$id", sizeBytes = 1000,
            mimeType = "audio/mpeg", category = MediaCategory.audio,
            modifiedAt = Instant.ofEpochMilli(0), metadata = metadata,
        )

    private class Fixture(
        val scope: TestScope,
        val player: FakePlayer,
        val policy: FakePolicy?,
        val island: DynamicIslandController,
        val coordinator: MediaPlayerCoordinator,
    )

    private fun fixture(withPolicy: FakePolicy? = FakePolicy()): Fixture {
        val scope = TestScope(UnconfinedTestDispatcher())
        val player = FakePlayer()
        val island = DynamicIslandController(
            nativeBridge = null,
            timerScheduler = NoopScheduler(),
            scope = scope,
        )
        return Fixture(
            scope, player, withPolicy, island,
            MediaPlayerCoordinator(
                audioPlayer = player,
                scope = scope,
                policyCoordinator = withPolicy,
                islandController = island,
            ),
        )
    }

    @Test fun `playTrack denied by policy reports error without player calls`() = runTest {
        val policy = FakePolicy(acquireResult = false)
        val f = fixture(policy)
        f.coordinator.playTrack(track("a"))
        assertEquals(PlaybackStatus.error, f.coordinator.state.status)
        assertEquals(
            "Cannot play audio while an active call owns the session.",
            f.coordinator.state.errorMessage,
        )
        assertTrue(f.player.calls.isEmpty())
    }

    @Test fun `playTrack success sets queue and issues player sequence`() = runTest {
        val f = fixture()
        val q = listOf(track("a"), track("b"), track("c"))
        f.coordinator.playTrack(track("b"), queue = q)
        assertEquals(PlaybackStatus.loading, f.coordinator.state.status)
        assertEquals(1, f.coordinator.state.queueIndex)
        assertEquals(3, f.coordinator.state.queue.size)
        assertEquals(
            listOf("stop", "setFilePath:/media/b", "setSpeed:1.0", "play"),
            f.player.calls,
        )
    }

    @Test fun `playTrack without queue uses singleton queue`() = runTest {
        val f = fixture()
        f.coordinator.playTrack(track("solo"))
        assertEquals(1, f.coordinator.state.queue.size)
        assertEquals(0, f.coordinator.state.queueIndex)
    }

    @Test fun `player state mapping matches dart`() = runTest {
        val f = fixture()
        f.player.playerStateStream.emit(MediaPlayerState(MediaProcessingState.loading, false))
        assertEquals(PlaybackStatus.loading, f.coordinator.state.status)
        f.player.playerStateStream.emit(MediaPlayerState(MediaProcessingState.buffering, false))
        assertEquals(PlaybackStatus.loading, f.coordinator.state.status)
        f.player.playerStateStream.emit(MediaPlayerState(MediaProcessingState.ready, true))
        assertEquals(PlaybackStatus.playing, f.coordinator.state.status)
        f.player.playerStateStream.emit(MediaPlayerState(MediaProcessingState.ready, false))
        assertEquals(PlaybackStatus.paused, f.coordinator.state.status)
    }

    @Test fun `completed with repeat off and no next stops`() = runTest {
        val f = fixture()
        f.coordinator.playTrack(track("only"))
        f.player.calls.clear()
        f.player.playerStateStream.emit(MediaPlayerState(MediaProcessingState.completed, false))
        assertEquals(PlaybackStatus.stopped, f.coordinator.state.status)
        assertTrue(f.player.calls.isEmpty()) // no restart
    }

    @Test fun `completed with repeat one replays same track`() = runTest {
        val f = fixture()
        f.coordinator.playTrack(track("a"), queue = listOf(track("a")))
        f.coordinator.cycleRepeatMode() // off -> all
        f.coordinator.cycleRepeatMode() // all -> one
        f.player.calls.clear()
        f.player.playerStateStream.emit(MediaPlayerState(MediaProcessingState.completed, false))
        assertTrue(f.player.calls.contains("setFilePath:/media/a"))
    }

    @Test fun `completed with next skips forward`() = runTest {
        val f = fixture()
        val q = listOf(track("a"), track("b"))
        f.coordinator.playTrack(track("a"), queue = q)
        f.player.calls.clear()
        f.player.playerStateStream.emit(MediaPlayerState(MediaProcessingState.completed, false))
        assertTrue(f.player.calls.contains("setFilePath:/media/b"))
    }

    @Test fun `completed at end with repeat all restarts queue`() = runTest {
        val f = fixture()
        val q = listOf(track("a"), track("b"))
        f.coordinator.playTrack(track("b"), queue = q)
        f.coordinator.cycleRepeatMode() // off -> all
        f.player.calls.clear()
        f.player.playerStateStream.emit(MediaPlayerState(MediaProcessingState.completed, false))
        assertTrue(f.player.calls.contains("setFilePath:/media/a"))
    }

    @Test fun `repeat cycle order off-all-one-off`() = runTest {
        val f = fixture()
        assertEquals(ChatyRepeatMode.off, f.coordinator.state.repeatMode)
        f.coordinator.cycleRepeatMode()
        assertEquals(ChatyRepeatMode.all, f.coordinator.state.repeatMode)
        f.coordinator.cycleRepeatMode()
        assertEquals(ChatyRepeatMode.one, f.coordinator.state.repeatMode)
        f.coordinator.cycleRepeatMode()
        assertEquals(ChatyRepeatMode.off, f.coordinator.state.repeatMode)
    }

    @Test fun `toggle play pause by status`() = runTest {
        val f = fixture()
        f.coordinator.playTrack(track("a"))
        f.player.calls.clear()
        // paused: resume path
        f.coordinator.togglePlayPause()
        assertEquals(listOf("play"), f.player.calls)
        assertEquals(PlaybackStatus.playing, f.coordinator.state.status)
        // playing: pause path
        f.coordinator.togglePlayPause()
        assertEquals(listOf("play", "pause"), f.player.calls)
        assertEquals(PlaybackStatus.paused, f.coordinator.state.status)
    }

    @Test fun `skip next previous bounds`() = runTest {
        val f = fixture()
        val q = listOf(track("a"), track("b"), track("c"))
        f.coordinator.playTrack(track("a"), queue = q)
        f.player.calls.clear()
        f.coordinator.skipPrevious() // no-op at start
        assertTrue(f.player.calls.isEmpty())
        f.coordinator.skipNext()
        assertTrue(f.player.calls.contains("setFilePath:/media/b"))
        f.coordinator.skipNext()
        f.coordinator.skipNext() // no-op at end
        assertEquals(1, f.player.calls.count { it == "setFilePath:/media/c" })
    }

    @Test fun `island payload posted while playing with metadata`() = runTest {
        val f = fixture()
        f.coordinator.playTrack(
            track("a", name = "file.mp3", metadata = mapOf("title" to "Song", "artist" to "Artist")),
        )
        f.player.durationStream.emit(100.seconds)
        f.player.playerStateStream.emit(MediaPlayerState(MediaProcessingState.ready, true))
        val payload = f.island.queue.firstOrNull { it.id == "media_active" }
        assertEquals(IslandPayloadType.media, payload?.type)
        assertEquals("Song", payload?.title)
        assertEquals("Artist", payload?.mediaArtist)
        assertEquals(100, payload?.durationSeconds)
        assertTrue(payload?.isPlaying == true)
    }

    @Test fun `island payload removed on stop`() = runTest {
        val f = fixture()
        f.coordinator.playTrack(track("a"))
        f.player.playerStateStream.emit(MediaPlayerState(MediaProcessingState.ready, true))
        f.coordinator.stop()
        assertTrue(f.island.queue.none { it.id == "media_active" })
    }

    @Test fun `stop releases media session`() = runTest {
        val policy = FakePolicy()
        val f = fixture(policy)
        f.coordinator.playTrack(track("a"))
        f.coordinator.stop()
        assertEquals(1, policy.stops.size)
        assertEquals(PlaybackStatus.stopped, f.coordinator.state.status)
    }

    @Test fun `interruption pauses and recovering resumes`() = runTest {
        val policy = FakePolicy()
        val f = fixture(policy)
        f.coordinator.playTrack(track("a"))
        f.player.playerStateStream.emit(MediaPlayerState(MediaProcessingState.ready, true))
        f.player.calls.clear()
        policy.state = AudioSessionState.interrupted
        policy.emit()
        assertEquals(listOf("pause"), f.player.calls)
        policy.state = AudioSessionState.recovering
        policy.emit()
        assertEquals(listOf("pause", "play"), f.player.calls)
    }

    @Test fun `interruption while not playing does not resume after`() = runTest {
        val policy = FakePolicy()
        val f = fixture(policy)
        f.coordinator.playTrack(track("a"))
        // stay in loading (not playing)
        f.player.calls.clear()
        policy.state = AudioSessionState.interrupted
        policy.emit()
        policy.state = AudioSessionState.recovering
        policy.emit()
        assertTrue(f.player.calls.isEmpty())
    }

    @Test fun `seek delegates and setSpeed updates state`() = runTest {
        val f = fixture()
        f.coordinator.seek(42.seconds)
        f.coordinator.setSpeed(1.5)
        assertTrue(f.player.calls.contains("seek:42000"))
        assertTrue(f.player.calls.contains("setSpeed:1.5"))
        assertEquals(1.5, f.coordinator.state.speed, 0.0)
    }

    @Test fun `resume without active item is a no-op`() = runTest {
        val f = fixture()
        f.coordinator.resume()
        assertTrue(f.player.calls.isEmpty())
    }
}
