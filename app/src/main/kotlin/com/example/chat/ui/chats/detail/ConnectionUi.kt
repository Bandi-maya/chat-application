package com.example.chat.ui.chats.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import com.example.chat.ui.icons.ChatyIcon
import com.example.chat.ui.icons.ChatyIconId
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.domain.models.ConnectionHealth
import com.example.chat.domain.models.ConnectionHealthState
import kotlinx.coroutines.delay
import kotlin.math.max

internal val connectionExcellent = Color(0xFF10B981)
internal val connectionWeak = Color(0xFFF59E0B)
internal val connectionPoor = Color(0xFFEF4444)
internal val connectionOffline = Color(0xFF71717A)

/** Pack-aware signal glyph, strength tint/mask and offline state decoration. */
@Composable
fun ConnectionHealthIndicator(
    health: ConnectionHealth,
    size: Dp = 18.dp,
    onTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (onTap == null) {
        ConnectionHealthBars(health, size, modifier)
    } else {
        // Explicit 48dp tap box centered on the icon; the drawn icon keeps its size. It is the only
        // tap target (the bars themselves are not clickable), so no hidden area spreads over the name.
        Box(
            modifier
                .size(maxOf(size, 48.dp))
                .semantics(mergeDescendants = true) {
                    onClick(label = "Open connection details") { onTap(); true }
                }
                .pointerInput(onTap) { detectTapGestures(onTap = { onTap() }) },
            contentAlignment = Alignment.Center,
        ) { ConnectionHealthBars(health, size, Modifier) }
    }
}

@Composable
private fun ConnectionHealthBars(
    health: ConnectionHealth,
    size: Dp,
    modifier: Modifier,
) {
    val semanticLabel = when (health) {
        ConnectionHealth.excellent -> "Connection excellent"
        ConnectionHealth.weak -> "Connection weak"
        ConnectionHealth.poor -> "Connection poor"
        ConnectionHealth.offline -> "No internet connection"
    }
    val activeColor = when (health) {
        ConnectionHealth.excellent -> connectionExcellent
        ConnectionHealth.weak -> connectionWeak
        ConnectionHealth.poor -> connectionPoor
        ConnectionHealth.offline -> connectionOffline
    }
    val activeBars = when (health) {
        ConnectionHealth.excellent -> 3
        ConnectionHealth.weak -> 2
        ConnectionHealth.poor -> 1
        ConnectionHealth.offline -> 0
    }
    val isOffline = health == ConnectionHealth.offline
    Box(modifier.size(size).semantics { contentDescription = semanticLabel }) {
        ChatyIcon(ChatyIconId.signal, size = size, color = activeColor.copy(alpha = 0.25f))
        if (!isOffline) {
            ChatyIcon(ChatyIconId.signal, size = size, color = activeColor,
                modifier = Modifier.drawWithContent {
                    clipRect(right = this.size.width * activeBars / 3f) { this@drawWithContent.drawContent() }
                })
        } else {
            // State decoration, not a replacement glyph: selected-pack signal remains visible.
            Box(Modifier.size(size).drawBehind {
                drawLine(activeColor, Offset(this.size.width * 0.1f, this.size.height * 0.9f),
                    Offset(this.size.width * 0.9f, this.size.height * 0.1f),
                    strokeWidth = max(1.4f, this.size.width * 0.08f), cap = StrokeCap.Round)
            })
        }
    }

}

/** Exact port of lib/ui/core/connection/global_connection_banner.dart: a
 *  subtle status strip for degraded/offline states, hidden while healthy,
 *  with a 3s "Connection restored" pill when health returns to excellent. */
@Composable
fun GlobalConnectionBanner(
    state: ConnectionHealthState,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRestoredToast by remember { mutableStateOf(false) }
    var lastHealth by remember { mutableStateOf(state.health) }

    LaunchedEffect(state.health) {
        val wasBad = lastHealth != ConnectionHealth.excellent
        lastHealth = state.health
        if (wasBad && state.health == ConnectionHealth.excellent) {
            showRestoredToast = true
            try {
                delay(3000)
            } finally {
                showRestoredToast = false
            }
        }
    }

    val visible = state.health != ConnectionHealth.excellent || showRestoredToast
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        val (bgColor, fgColor, icon, text) = when (state.health) {
            ConnectionHealth.excellent -> BannerStyle(
                connectionExcellent,
                Color.White,
                BannerIcon.CheckCircle,
                "Connection restored",
            )
            ConnectionHealth.weak -> BannerStyle(
                connectionWeak.copy(alpha = 0.15f),
                connectionWeak,
                BannerIcon.Wifi,
                "Weak connection — messages may take longer",
            )
            ConnectionHealth.poor -> BannerStyle(
                connectionPoor.copy(alpha = 0.15f),
                connectionPoor,
                BannerIcon.CloudOff,
                if (state.backendReachable) {
                    "Connection unstable — retrying automatically"
                } else {
                    "Chat service unreachable — retrying…"
                },
            )
            ConnectionHealth.offline -> BannerStyle(
                connectionOffline.copy(alpha = 0.18f),
                Color.White,
                BannerIcon.SignalWifiOff,
                if (state.queuedMessagesCount > 0) {
                    "No internet — ${state.queuedMessagesCount} messages waiting to send"
                } else {
                    "No internet connection — waiting for network"
                },
            )
        }
        Row(
            modifier
                .fillMaxWidth()
                .background(bgColor)
                .pointerInput("connectionBanner") { detectTapGestures(onTap = { onTap() }) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChatyIcon(
                if (icon == BannerIcon.CheckCircle) ChatyIconId.check else ChatyIconId.signal,
                color = fgColor, size = 16.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                color = fgColor,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.W600,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            ConnectionHealthIndicator(health = state.health, size = 14.dp)
        }
    }
}

private enum class BannerIcon { CheckCircle, Wifi, CloudOff, SignalWifiOff }

private data class BannerStyle(
    val bg: Color,
    val fg: Color,
    val icon: BannerIcon,
    val text: String,
)
