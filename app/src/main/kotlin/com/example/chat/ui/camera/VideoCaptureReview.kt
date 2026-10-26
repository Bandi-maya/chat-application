package com.example.chat.ui.camera

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Local-only playback. Confirm returns typed video, not image bytes. */
@Composable
fun VideoCaptureReview(video: FinalizedVideo, onConfirm: () -> Unit, onRetake: () -> Unit) {
    key(video.file.absolutePath) {
    var prepared by remember(video.file) { mutableStateOf(false) }
    var error by remember(video.file) { mutableStateOf(false) }
    var view by remember { mutableStateOf<VideoView?>(null) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) view?.pause() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(video.file) { onDispose { view?.stopPlayback(); view = null } }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Text(if (video.hasAudio) "Video with sound" else "Silent video (microphone audio was not recorded)")
        AndroidView(
            factory = { context -> VideoView(context).also { player ->
                view = player
                player.setMediaController(MediaController(context))
                player.setVideoURI(Uri.fromFile(video.file))
                player.setOnPreparedListener { prepared = true; it.isLooping = false; player.seekTo(1) }
                player.setOnErrorListener { _, _, _ -> error = true; true }
            } },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        if (error) Text("This video could not be played. Record it again.")
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onRetake) { Text("Record again") }
            Button(onClick = onConfirm, enabled = prepared && !error) { Text("Use video") }
        }
    }
}
}
