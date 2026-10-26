package com.example.chat.ui.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Minimize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.data.services.CallVideoSurfaceProvider
import com.example.chat.data.services.ChatyCallSession
import kotlin.math.abs
import kotlin.math.max

private val PIP_WIDTH = 120f
private val PIP_HEIGHT = 175f

/**
 * Port of features/calls/widgets/in_app_call_pip.dart: floating in-app
 * Picture-in-Picture window for active video/voice calls (~120x175dp).
 * Supports dragging with safe snap boundaries and drag-to-top-center to
 * collapse into the Call Island.
 */
@Composable
fun InAppCallPip(
    session: ChatyCallSession,
    videoSurfaces: CallVideoSurfaceProvider?,
    durationSeconds: Int,
    onTap: () -> Unit,
    onCollapseToIsland: () -> Unit,
    onEndCall: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    // Parent owns cutout/status safety. Measure its available bounds, not the
    // physical display, and clamp during every drag rather than only on release.
    val screenW = maxWidth.value
    val screenH = maxHeight.value
    val maxX = (screenW - PIP_WIDTH).coerceAtLeast(0f)
    val maxY = (screenH - PIP_HEIGHT - 16f).coerceAtLeast(0f)

    var positionX by remember(screenW) { mutableStateOf((maxX - 16f).coerceAtLeast(0f)) }
    var positionY by remember(screenH) { mutableStateOf(80f.coerceAtMost(maxY)) }
    var isDragging by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .offset(positionX.dp, positionY.dp)
            .size(PIP_WIDTH.dp, PIP_HEIGHT.dp)
            .background(
                Color(0xFF141416),
                RoundedCornerShape(20.dp),
            )
            .pointerInput(screenW, screenH) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        positionX = (positionX + dragAmount.x / density).coerceIn(0f, maxX)
                        positionY = (positionY + dragAmount.y / density).coerceIn(0f, maxY)
                    },
                    onDragEnd = {
                        isDragging = false
                        val centerX = positionX + (PIP_WIDTH / 2)
                        val screenCenterX = screenW / 2
                        // Drag to top-center collapses into the Call Island.
                        if (positionY < 60f + 50f && abs(centerX - screenCenterX) < 90f) {
                            onCollapseToIsland()
                            return@detectDragGestures
                        }
                        // Otherwise snap to the closest safe horizontal edge.
                        val targetX = if (centerX < screenCenterX) 14f else screenW - PIP_WIDTH - 14f
                        positionX = targetX.coerceIn(0f, maxX)
                        positionY = positionY.coerceIn(0f, maxY)
                    },
                    onDragCancel = { isDragging = false },
                )
            }
            .clickable { showControls = !showControls },
    ) {
        if (session.isVideo && !session.isCameraOff && videoSurfaces != null && videoSurfaces.hasRemoteVideo) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { context ->
                    org.webrtc.SurfaceViewRenderer(context).apply {
                        videoSurfaces.attachRemoteRenderer(this)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { renderer -> videoSurfaces.detachRenderer(renderer) },
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E24)),
            ) {
                CallAvatar(
                    initials = session.remoteAvatarInitials
                        ?: (if (session.remoteDisplayName.isNotEmpty()) {
                            session.remoteDisplayName.substring(0, 1).uppercase()
                        } else {
                            "U"
                        }),
                    colorHex = session.remoteAvatarColorHex,
                    size = 46.dp,
                )
            }
        }

        // Duration badge.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Color(0xA6000000), RoundedCornerShape(10.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        ) {
            Box(Modifier.size(6.dp).background(Color(0xFF22C55E), CircleShape))
            Spacer4()
            Text(
                formatCallDuration(durationSeconds),
                color = Color.White,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        if (showControls) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().background(Color(0x8C000000)),
            ) {
                Row {
                    IconButton(onClick = onTap) {
                        Icon(
                            Icons.Rounded.Fullscreen,
                            contentDescription = "Expand call",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    IconButton(onClick = onCollapseToIsland) {
                        Icon(
                            Icons.Rounded.Minimize,
                            contentDescription = "Minimize to Island",
                            tint = Color(0xB3FFFFFF),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = onEndCall) {
                        Icon(
                            Icons.Rounded.CallEnd,
                            contentDescription = "End call",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
    } // Measured safe-area bounds.
}

@Composable
private fun Spacer4() {
    androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
}
