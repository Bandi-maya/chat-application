package com.example.chat.ui.camera

import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
    onUseVideo: (FinalizedVideo) -> Unit,
    onBackToPhoto: () -> Unit,
) {
    val owner = LocalLifecycleOwner.current
    var surface by remember { mutableStateOf<PreviewView?>(null) }
    var session by remember { mutableStateOf<CameraVideoSession?>(null) }
    var state by remember { mutableStateOf<VideoSessionState>(VideoSessionState.Preparing) }
    val useVideo by rememberUpdatedState(onUseVideo)
    DisposableEffect(surface, owner, selector, stabilization) {
        val view = surface
        val video = view?.let { CameraVideoSession(it.context, owner, it, selector, stabilization) { next -> state = next } }
        session = video
        video?.bind()
        onDispose { video?.close(); if (session === video) session = null }
    }
    Column(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val review = state as? VideoSessionState.Review
        if (review != null) {
            VideoCaptureReview(review.video,
                onConfirm = { session?.takeReviewedVideo(review.video)?.let(useVideo) },
                onRetake = { session?.discard() },
            )
        } else {
            Text(when (val current = state) {
                is VideoSessionState.Error -> current.message
                is VideoSessionState.Ready -> if (current.stabilizationEnabled) "Video ready · stabilization on" else "Video ready"
                is VideoSessionState.Recording -> if (current.hasAudio) "Recording with sound" else "Recording silently"
                VideoSessionState.Finalizing -> "Finishing video…"
                else -> "Opening video camera…"
            }, color = Color.White)
            AndroidView(factory = { context -> PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                surface = this
            } }, modifier = Modifier.fillMaxWidth().weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = onBackToPhoto, enabled = state !is VideoSessionState.Finalizing) { Text("Photo") }
                when (state) {
                    is VideoSessionState.Recording -> Button(onClick = { session?.stop() }) { Text("Stop") }
                    is VideoSessionState.Ready -> Button(onClick = { session?.start(requestAudio = true) }) { Text("Record") }
                    else -> Unit
                }
            }
            Text("Microphone audio is used only if already permitted. Otherwise video is silent. Review before sending.", color = Color.LightGray)
        }
    }
}
