package com.example.chat.ui.chats.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.chat.data.media.MediaDownloadFormat
import com.example.chat.data.media.MediaDownloadState as S
import com.example.chat.ui.icons.ChatyIcon
import com.example.chat.ui.icons.ChatyIconId

/**
 * Overlay for a received media tile that is not on the device yet. Pure UI: state in, taps out.
 *  idle:        download icon + size ("Photo 2.4 MB")
 *  downloading: ring with real percent, pause icon in the ring, small cancel chip, "1.5 MB of 2.4 MB  62%", bar, ETA
 *  paused:      play-resume icon, "Paused 38%" (+ "waiting for network" when the network paused it)
 *  failed:      refresh icon, reason text
 * All tap targets are at least 48dp; colors come from MaterialTheme tokens; reduced motion skips the animation.
 */
@Composable
internal fun MediaDownloadTile(
    state: S,
    video: Boolean,
    reduceMotion: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val scrim = scheme.scrim.copy(alpha = 0.55f)
    val fraction = when (state) {
        is S.Downloading -> MediaDownloadFormat.percent(state.doneBytes, state.totalBytes)?.div(100f)
        is S.Paused -> MediaDownloadFormat.percent(state.doneBytes, state.totalBytes)?.div(100f)
        is S.Failed -> MediaDownloadFormat.percent(state.doneBytes, state.totalBytes)?.div(100f)
        S.Done -> 1f
        else -> null
    }
    val animated by animateFloatAsState(fraction ?: 0f, animationSpec = if (reduceMotion) tween(0) else tween(180), label = "dl")
    val accent = when (state) {
        S.Done -> Color(0xFF78DC96) // Approved v3 completion green.
        is S.Paused -> scheme.tertiary
        is S.Failed -> scheme.error
        else -> scheme.primary
    }
    val tick by animateFloatAsState(if (state == S.Done) 1f else 0f, animationSpec = if (reduceMotion) tween(0) else tween(220), label = "tick")
    val kindWord = if (video) "Video" else "Photo"
    val label = when (state) {
        is S.Idle -> state.totalBytes?.let { "$kindWord  ${MediaDownloadFormat.size(it)}" } ?: "Tap to download"
        S.Done -> "Downloaded"
        else -> MediaDownloadFormat.label(state)
    }
    val eta = (state as? S.Downloading)?.let { MediaDownloadFormat.speedAndEta(it.doneBytes, it.totalBytes, it.bytesPerSecond) }
    val action = when (state) {
        is S.Idle, is S.Failed -> onStart
        is S.Paused -> onStart
        is S.Downloading, is S.Queued -> onPause
        S.Done -> ({})
    }
    val actionIcon = when (state) {
        is S.Idle -> ChatyIconId.download
        is S.Queued, is S.Downloading -> ChatyIconId.pause
        is S.Paused -> ChatyIconId.playArrow
        is S.Failed -> if (state.reason == S.FailReason.NOT_ALLOWED || state.reason == S.FailReason.SERVER_GONE) ChatyIconId.mediaError else ChatyIconId.refresh
        S.Done -> ChatyIconId.check
    }
    val actionLabel = when (state) {
        is S.Idle -> "Download"
        is S.Queued, is S.Downloading -> "Pause download"
        is S.Paused -> "Resume download"
        is S.Failed -> "Retry download"
        S.Done -> ""
    }
    val terminalFail = state is S.Failed && (state.reason == S.FailReason.NOT_ALLOWED || state.reason == S.FailReason.SERVER_GONE || state.reason == S.FailReason.TOO_LARGE)
    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f))) {
        val compact = maxHeight < 120.dp                       // album cells: icon + ring only, no text
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(if (compact) 48.dp else 56.dp).clip(CircleShape).background(scrim)
                    .then(if (terminalFail || state == S.Done) Modifier else Modifier.clickable(onClickLabel = actionLabel, onClick = action))
                    .semantics { contentDescription = actionLabel.ifEmpty { "Downloaded" } },
                contentAlignment = Alignment.Center,
            ) {
                when (state) {
                    is S.Downloading, is S.Paused, is S.Failed ->
                        if (fraction != null && state !is S.Failed) CircularProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxSize().padding(3.dp), color = accent, trackColor = Color.White.copy(alpha = 0.18f), strokeWidth = 3.dp)
                        else if (state is S.Downloading) CircularProgressIndicator(modifier = Modifier.fillMaxSize().padding(3.dp), color = accent, strokeWidth = 3.dp)
                    S.Done -> CircularProgressIndicator(progress = { 1f }, modifier = Modifier.fillMaxSize().padding(3.dp), color = accent, trackColor = Color.White.copy(alpha = 0.18f), strokeWidth = 3.dp)
                    is S.Queued -> CircularProgressIndicator(modifier = Modifier.fillMaxSize().padding(3.dp), color = accent, strokeWidth = 3.dp)
                    else -> Unit
                }
                ChatyIcon(actionIcon, size = if (compact) 20.dp else 24.dp, color = if (state == S.Done) accent else Color.White,
                    modifier = if (state == S.Done) Modifier.graphicsLayer { scaleX = 0.6f + 0.4f * tick; scaleY = 0.6f + 0.4f * tick; alpha = tick } else Modifier)
            }
            if (!compact) {
                Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                if (state is S.Downloading || state is S.Paused) {
                    LinearProgressIndicator(
                        progress = { animated },
                        modifier = Modifier.fillMaxWidth(0.8f).padding(top = 6.dp).clip(RoundedCornerShape(2.dp)),
                        color = accent, trackColor = Color.White.copy(alpha = 0.22f),
                    )
                }
                if (eta != null) Text(eta, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                if (state is S.Paused && state.reason == S.PauseReason.NO_NETWORK) Text("Resumes automatically", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
            }
        }
        // Cancel chip: only while there is something to cancel. 48dp touch target around a 24dp chip.
        if (state is S.Queued || state is S.Downloading || state is S.Paused) {
            Box(
                Modifier.align(Alignment.TopEnd).size(48.dp).clickable(onClickLabel = "Cancel download", onClick = onCancel)
                    .semantics { contentDescription = "Cancel download" },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(scrim), contentAlignment = Alignment.Center) {
                    ChatyIcon(ChatyIconId.close, size = 16.dp, color = Color.White)
                }
            }
        }
    }
}
