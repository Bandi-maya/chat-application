package com.example.chat.ui.camera

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
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
fun VideoCaptureReview(video: FinalizedVideo, auth: VideoScopeLease, registerStop: ((() -> Unit)?) -> Unit, onConfirm: (() -> Unit)?, onRetake: () -> Unit) {
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
    DisposableEffect(video.file) {
        registerStop { view?.stopPlayback(); view = null }
        onDispose { view?.stopPlayback(); view = null; registerStop(null) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(if (video.hasAudio) "Video with sound" else "Silent video (microphone audio was not recorded)", color = MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
        AndroidView(
            factory = { context -> VideoView(context).also { player ->
                view = player
                // Only the scope-guarded Play button controls playback.
                // Native MediaController buttons bypass that boundary.
                if (auth.isCurrent()) player.setVideoURI(Uri.fromFile(video.file))
                player.setOnPreparedListener { media ->
                    if (auth.isCurrent()) { prepared = true; media.isLooping = false; player.seekTo(1) }
                    else player.stopPlayback()
                }
                player.setOnErrorListener { _, _, _ -> if (auth.isCurrent()) error = true; true }
            } },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        if (error) Text("This video could not be played. Record it again.", color = MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(prepared&&!error) Button(shape = com.example.chat.ui.kit.ChatyActionShape, onClick = { if (auth.isCurrent()) { view?.start(); if (!auth.isCurrent()) view?.stopPlayback() } },
                enabled=prepared&&!error,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Play recording") }
            OutlinedButton(shape = com.example.chat.ui.kit.ChatyActionShape, onClick=onRetake,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Record again") }
            if(onConfirm!=null) Button(shape = com.example.chat.ui.kit.ChatyActionShape, onClick=onConfirm,enabled=prepared&&!error,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Use video") }
        }
    }
}
}
