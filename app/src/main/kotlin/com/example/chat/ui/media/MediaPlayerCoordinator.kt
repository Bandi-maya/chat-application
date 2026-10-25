package com.example.chat.ui.media

import com.example.chat.core.audio.AudioPolicyGate
import com.example.chat.core.audio.AudioSessionState
import com.example.chat.core.audio.AudioUseCase
import com.example.chat.ui.island.DynamicIslandController
import com.example.chat.ui.island.DynamicIslandPayload
import com.example.chat.ui.island.IslandPayloadType
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/** just_audio ProcessingState port for the player seam. */
enum class MediaProcessingState {
    idle,
    loading,
    buffering,
    ready,
    completed,
}

/** just_audio PlayerState port for the player seam. */
data class MediaPlayerState(
    val processingState: MediaProcessingState,
    val playing: Boolean,
)

/** Seam over the just_audio AudioPlayer surface used by the coordinator.
 *  Android implementation: Media3 ExoPlayer adapter (integration owner;
 *  Media3 is a free androidx dependency to add). */
interface MediaAudioPlayer {
    val positionStream: SharedFlow<Duration>
    val durationStream: SharedFlow<Duration?>
    val bufferedPositionStream: SharedFlow<Duration>
    val playerStateStream: SharedFlow<MediaPlayerState>
    suspend fun stop()
    suspend fun setFilePath(path: String)
    suspend fun setSpeed(speed: Double)
    suspend fun play()
    suspend fun pause()
    suspend fun seek(position: Duration)
    fun dispose()
}

/** Exact port of lib/features/media/services/media_player_coordinator.dart.
 *
 *  Coordinates playback state, queue navigation, repeat/shuffle, audio
 *  policy interruption handling, and Dynamic Island media payload sync.
 *  The Dart locator fallback for the island controller is expressed as the
 *  [islandControllerProvider] seam. ChangeNotifier is ported as the same
 *  listener pattern used by ChatyPreferencesController.
 */
class MediaPlayerCoordinator(
    private val audioPlayer: MediaAudioPlayer,
    private val scope: CoroutineScope,
    private val policyCoordinator: AudioPolicyGate? = null,
    private val islandController: DynamicIslandController? = null,
    private val islandControllerProvider: () -> DynamicIslandController? = { null },
) {
    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyListeners() { listeners.toList().forEach { it() } }

    private var _state = MediaPlaybackState()
    val state: MediaPlaybackState get() = _state

    private var posJob: Job? = null
    private var durJob: Job? = null
    private var stateJob: Job? = null
    private var bufJob: Job? = null
    private var shouldResumeAfterInterruption = false
    private var lastSyncedSec = -1

    private val onPolicyCoordinatorChanged: () -> Unit = {
        handlePolicyChanged()
    }

    init {
        initListeners()
    }

    private fun initListeners() {
        policyCoordinator?.addListener(onPolicyCoordinatorChanged)

        posJob = scope.launch {
            audioPlayer.positionStream.collect { pos ->
                _state = _state.copy(position = pos)
                if (pos.inWholeSeconds != lastSyncedSec.toLong()) {
                    lastSyncedSec = pos.inWholeSeconds.toInt()
                    syncIslandPayload()
                }
                notifyListeners()
            }
        }

        durJob = scope.launch {
            audioPlayer.durationStream.collect { dur ->
                if (dur != null) {
                    _state = _state.copy(duration = dur)
                    syncIslandPayload()
                    notifyListeners()
                }
            }
        }

        bufJob = scope.launch {
            audioPlayer.bufferedPositionStream.collect { buf ->
                _state = _state.copy(bufferedPosition = buf)
                notifyListeners()
            }
        }

        stateJob = scope.launch {
            audioPlayer.playerStateStream.collect { ps ->
                var status = PlaybackStatus.idle
                if (ps.processingState == MediaProcessingState.loading ||
                    ps.processingState == MediaProcessingState.buffering
                ) {
                    status = PlaybackStatus.loading
                } else if (ps.playing) {
                    status = PlaybackStatus.playing
                } else if (ps.processingState == MediaProcessingState.completed) {
                    status = PlaybackStatus.stopped
                    onTrackCompleted()
                } else {
                    status = PlaybackStatus.paused
                }
                _state = _state.copy(status = status)
                syncIslandPayload()
                notifyListeners()
            }
        }
    }

    private fun handlePolicyChanged() {
        val coordinator = policyCoordinator ?: return

        if (coordinator.state == AudioSessionState.interrupted ||
            coordinator.activeOwner == AudioUseCase.communication
        ) {
            if (_state.status == PlaybackStatus.playing) {
                shouldResumeAfterInterruption = true
                scope.launch { pause() }
            }
        } else if (coordinator.state == AudioSessionState.recovering ||
            coordinator.activeOwner == null
        ) {
            if (shouldResumeAfterInterruption) {
                shouldResumeAfterInterruption = false
                scope.launch { resume() }
            }
        }
    }

    private fun resolveIslandController(): DynamicIslandController? =
        islandController ?: islandControllerProvider()

    private fun syncIslandPayload() {
        val islandCtrl = resolveIslandController() ?: return

        if (_state.status == PlaybackStatus.playing || _state.status == PlaybackStatus.paused) {
            val active = _state.activeItem
            if (active != null) {
                val durSec = _state.duration.inWholeSeconds
                val durMs = _state.duration.inWholeMilliseconds
                val posMs = _state.position.inWholeMilliseconds
                val progress = if (durMs > 0) {
                    (posMs.toDouble() / durMs).coerceIn(0.0, 1.0)
                } else {
                    0.0
                }

                islandCtrl.postPayload(
                    DynamicIslandPayload(
                        id = "media_active",
                        type = IslandPayloadType.media,
                        title = active.metadata["title"] as? String ?: active.name,
                        mediaArtist = active.metadata["artist"] as? String ?: "Audio Track",
                        durationSeconds = durSec.toInt(),
                        progress = progress,
                        isPlaying = _state.status == PlaybackStatus.playing,
                        timestamp = Instant.now(),
                        onTogglePlay = { scope.launch { togglePlayPause() } },
                        onSeek = { p ->
                            val targetMs = (p * durMs).toLong() +
                                if ((p * durMs) % 1 >= 0.5) 1 else 0 // Dart .round()
                            scope.launch { seek(targetMs.milliseconds) }
                        },
                    ),
                    autoExpand = false,
                )
            }
        } else if (_state.status == PlaybackStatus.stopped || _state.status == PlaybackStatus.idle) {
            islandCtrl.removePayload("media_active")
        }
    }

    suspend fun playTrack(item: MediaFileDescriptor, queue: List<MediaFileDescriptor>? = null) {
        try {
            // 1. Check with policy coordinator before playing
            val coordinator = policyCoordinator
            if (coordinator != null) {
                val acquired = coordinator.startMediaSession()
                if (!acquired) {
                    _state = _state.copy(
                        status = PlaybackStatus.error,
                        errorMessage = "Cannot play audio while an active call owns the session.",
                    )
                    notifyListeners()
                    return
                }
            }

            val newQueue = queue ?: listOf(item)
            val idx = newQueue.indexOfFirst { it.path == item.path }

            _state = _state.copy(
                activeItem = item,
                queue = newQueue,
                queueIndex = if (idx != -1) idx else 0,
                status = PlaybackStatus.loading,
                errorMessage = null,
            )
            notifyListeners()

            audioPlayer.stop()
            audioPlayer.setFilePath(item.path)
            audioPlayer.setSpeed(_state.speed)
            audioPlayer.play()
        } catch (e: Exception) {
            _state = _state.copy(
                status = PlaybackStatus.error,
                errorMessage = "Playback error: $e",
            )
            notifyListeners()
        }
    }

    suspend fun togglePlayPause() {
        if (_state.status == PlaybackStatus.playing) {
            pause()
        } else {
            resume()
        }
    }

    suspend fun pause() {
        audioPlayer.pause()
        _state = _state.copy(status = PlaybackStatus.paused)
        notifyListeners()
    }

    suspend fun resume() {
        if (_state.activeItem != null) {
            audioPlayer.play()
            _state = _state.copy(status = PlaybackStatus.playing)
            notifyListeners()
        }
    }

    suspend fun seek(position: Duration) {
        audioPlayer.seek(position)
    }

    suspend fun setSpeed(speed: Double) {
        _state = _state.copy(speed = speed)
        audioPlayer.setSpeed(speed)
        notifyListeners()
    }

    fun toggleShuffle() {
        _state = _state.copy(isShuffle = !_state.isShuffle)
        notifyListeners()
    }

    fun cycleRepeatMode() {
        val next = when (_state.repeatMode) {
            ChatyRepeatMode.off -> ChatyRepeatMode.all
            ChatyRepeatMode.all -> ChatyRepeatMode.one
            ChatyRepeatMode.one -> ChatyRepeatMode.off
        }
        _state = _state.copy(repeatMode = next)
        notifyListeners()
    }

    suspend fun skipNext() {
        if (_state.hasNext) {
            val nextItem = _state.queue[_state.queueIndex + 1]
            playTrack(nextItem, queue = _state.queue)
        }
    }

    suspend fun skipPrevious() {
        if (_state.hasPrevious) {
            val prevItem = _state.queue[_state.queueIndex - 1]
            playTrack(prevItem, queue = _state.queue)
        }
    }

    /** Dart has both cycleRepeatMode and cycleChatyRepeatMode with identical
     *  bodies; kept for call-site parity. */
    fun cycleChatyRepeatMode() {
        val nextMode = when (_state.repeatMode) {
            ChatyRepeatMode.off -> ChatyRepeatMode.all
            ChatyRepeatMode.all -> ChatyRepeatMode.one
            ChatyRepeatMode.one -> ChatyRepeatMode.off
        }
        _state = _state.copy(repeatMode = nextMode)
        notifyListeners()
    }

    private fun onTrackCompleted() {
        if (_state.repeatMode == ChatyRepeatMode.one && _state.activeItem != null) {
            scope.launch { playTrack(_state.activeItem!!, queue = _state.queue) }
        } else if (_state.hasNext) {
            scope.launch { skipNext() }
        } else if (_state.repeatMode == ChatyRepeatMode.all && _state.queue.isNotEmpty()) {
            scope.launch { playTrack(_state.queue.first(), queue = _state.queue) }
        }
    }

    suspend fun stop() {
        audioPlayer.stop()
        _state = _state.copy(
            status = PlaybackStatus.stopped,
            position = Duration.ZERO,
        )
        syncIslandPayload()
        policyCoordinator?.stopMediaSession()
        notifyListeners()
    }

    fun dispose() {
        policyCoordinator?.removeListener(onPolicyCoordinatorChanged)
        resolveIslandController()?.removePayload("media_active")
        posJob?.cancel()
        durJob?.cancel()
        stateJob?.cancel()
        bufJob?.cancel()
        audioPlayer.dispose()
    }
}

