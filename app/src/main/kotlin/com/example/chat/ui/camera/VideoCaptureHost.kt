package com.example.chat.ui.camera

import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Enter only after the photo session has unbound. No implicit recording or send. */
@Composable
fun VideoCaptureHost(
    selector: CameraSelector,
    stabilization: Boolean,
    authPort: VideoAuthPort,
    onUseVideo: ((FinalizedVideo) -> Unit)? = null,
    onAuthInvalidated: () -> Unit,
    onBackToPhoto: () -> Unit,
    holdRequest: VideoHoldRequest? = null,
) {
    val owner = LocalLifecycleOwner.current
    var surface by remember { mutableStateOf<PreviewView?>(null) }
    var session by remember { mutableStateOf<CameraVideoSession?>(null) }
    var state by remember { mutableStateOf<VideoSessionState>(VideoSessionState.Preparing) }
    val holdPhase = holdRequest?.phase?.collectAsState()?.value
    LaunchedEffect(holdRequest, holdPhase, state, session) {
        val video=session ?: return@LaunchedEffect
        val ready=state as? VideoSessionState.Ready
        when(holdRequest?.next(ready!=null && authPort.isSignedIn(authPort.snapshot()),ready?.boundFront==true)) {
            VideoHoldRequest.Command.Start -> video.start(requestAudio=true)
            VideoHoldRequest.Command.Stop -> video.stop()
            VideoHoldRequest.Command.Discard -> video.discard()
            null -> Unit
        }
    }
    DisposableEffect(holdRequest) { onDispose { holdRequest?.close() } }
    val useVideo by rememberUpdatedState(onUseVideo)
    val invalidated by rememberUpdatedState(onAuthInvalidated)
    val signal = remember { kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED) }
    val auth = remember(authPort) { VideoScopeLease(authPort) { signal.trySend(Unit) } }
    var stopPlayback by remember { mutableStateOf<(() -> Unit)?>(null) }
    var exitPrompt by remember { mutableStateOf(false) }
    var retakePrompt by remember { mutableStateOf(false) }
    fun stopReader() { stopPlayback?.invoke(); stopPlayback = null }
    fun requestExit() { if (session?.hasDraft() == true) exitPrompt = true else onBackToPhoto() }
    androidx.activity.compose.BackHandler { requestExit() }
    LaunchedEffect(auth) {
        for (ignored in signal) {
            stopReader(); session?.close(); invalidated()
        }
    }
    DisposableEffect(auth) { onDispose { stopReader(); session?.close(); auth.close(); signal.close() } }
    if (exitPrompt || retakePrompt) androidx.compose.material3.AlertDialog(
        onDismissRequest = { exitPrompt = false; retakePrompt = false },
        title = { Text("Discard this unsaved video?") },
        text = { Text("This is a temporary draft. It is not sent or saved. Closing the app can lose it.") },
        confirmButton = { Button(shape = com.example.chat.ui.kit.ChatyActionShape, onClick = {
            val leave = exitPrompt
            stopReader(); session?.discard(); exitPrompt = false; retakePrompt = false
            if (leave) onBackToPhoto()
        }) { Text("Discard video") } },
        dismissButton = { Button(shape = com.example.chat.ui.kit.ChatyActionShape, onClick = { exitPrompt = false; retakePrompt = false }) { Text("Keep reviewing") } },
    )
    DisposableEffect(surface, owner, selector, stabilization, auth) {
        val view = surface
        val video = view?.let { CameraVideoSession(it.context, owner, it, selector, stabilization, auth) { next -> state = next } }
        session = video
        video?.bind()
        onDispose { stopReader(); video?.close(); if (session === video) session = null }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).windowInsetsPadding(WindowInsets.safeDrawing),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(shape = com.example.chat.ui.kit.ChatyActionShape, onClick={requestExit()},modifier=Modifier.heightIn(min=48.dp)) { Text("Back to photo") }
            Text("Video review",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurface,modifier=Modifier.padding(start=8.dp))
        }
        Text("Temporary local video, not sent or saved. Closing the app can lose it.",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(horizontal=16.dp))
        val review = state as? VideoSessionState.Review
        if (review != null) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
            VideoCaptureReview(review.video, auth,
                registerStop = { stopPlayback = it },
                onConfirm = if (useVideo != null) ({
                    // No asynchronous handoff is authorized by this short guard.
                    // A supplied callback must synchronously accept ownership.
                    if (auth.isCurrent()) session?.takeReviewedVideo(review.video)?.let { useVideo?.invoke(it) }
                }) else null,
                onRetake = { retakePrompt = true },
            )
            }
        } else {
            (state as? VideoSessionState.Ready)?.let { ready ->
                Text(if(ready.boundFront) "Front camera" else "Back camera",color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(horizontal=16.dp))
            }
            Text(when (val current = state) {
                is VideoSessionState.Error -> current.message
                is VideoSessionState.Ready -> if (current.stabilizationEnabled) "Video ready · stabilization on" else "Video ready"
                is VideoSessionState.Recording -> if (current.hasAudio) "Recording with sound" else "Recording silently"
                VideoSessionState.Finalizing -> "Finishing video…"
                else -> "Opening video camera…"
            }, color = MaterialTheme.colorScheme.onSurface,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(horizontal=16.dp))
            AndroidView(factory = { context -> PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                surface = this
            } }, modifier = Modifier.fillMaxWidth().weight(1f))
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.OutlinedButton(shape = com.example.chat.ui.kit.ChatyActionShape, onClick = { requestExit() },modifier=Modifier.heightIn(min=48.dp), enabled = state !is VideoSessionState.Finalizing) { Text("Photo") }
                when (state) {
                    is VideoSessionState.Recording -> Button(shape = com.example.chat.ui.kit.ChatyActionShape, onClick = { session?.stop() },modifier=Modifier.heightIn(min=48.dp)) { Text("Stop") }
                    is VideoSessionState.Ready -> Button(shape = com.example.chat.ui.kit.ChatyActionShape, onClick = { if (auth.isCurrent()) session?.start(requestAudio = true) },modifier=Modifier.heightIn(min=48.dp)) { Text("Record") }
                    else -> Unit
                }
            }
            Text("Microphone audio is used only if already permitted. Otherwise video is silent. Review locally. Sharing is not connected yet.",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(horizontal=16.dp))
        }
    }
}
