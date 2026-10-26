package com.example.chat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.chat.ServiceLocator
import com.example.chat.data.services.AudioRouteType
import com.example.chat.data.services.CallSessionState
import com.example.chat.ui.core.effects.ClickParticleOverlay
import com.example.chat.ui.core.effects.FallingParticlesOverlay
import com.example.chat.ui.core.theme.ChatyTextScaleProvider
import com.example.chat.ui.core.theme.ChatyTypography
import com.example.chat.ui.kit.ChatyActivity
import com.example.chat.ui.kit.ChatyCallActivityCapsule
import com.example.chat.ui.kit.ChatyGlobalActivityHost
import kotlinx.coroutines.launch

/** main.dart MaterialApp.builder, outer part: typography style (_applyTypographyStyle) and the
 *  effective text scale (AppearanceVariantController.resolveTextScale). Recomposes on appearance and
 *  preference changes. */
@Composable
fun GlobalTypographyScale(content: @Composable () -> Unit) {
    val appearance = ServiceLocator.appearanceVariantController
    val prefs = ServiceLocator.preferencesController
    var rev by remember { mutableIntStateOf(0) }
    DisposableEffect(appearance, prefs) {
        val l: () -> Unit = { rev++ }
        appearance.addListener(l)
        prefs.addListener(l)
        onDispose { appearance.removeListener(l); prefs.removeListener(l) }
    }
    @Suppress("UNUSED_EXPRESSION") rev
    val typography = ChatyTypography.apply(MaterialTheme.typography, appearance.typographyStyle, prefs.universal.fontStyle)
    MaterialTheme(colorScheme = MaterialTheme.colorScheme, typography = typography) {
        ChatyTextScaleProvider(appearance.textScale, content)
    }
}

/** main.dart builder inner part: ChatyGlobalActivityHost(primaryActivity: minimized call capsule) >
 *  ClickParticleOverlay > FallingParticlesOverlay(currentScope 'Home') > routed child. */
@Composable
fun GlobalRouteLayers(content: @Composable () -> Unit) {
    val service = remember { ServiceLocator.callSignaling }
    val presentation = remember { ServiceLocator.callPresentationController }
    val prefs = ServiceLocator.preferencesController
    val scope = rememberCoroutineScope()
    var rev by remember { mutableIntStateOf(0) }
    DisposableEffect(service, presentation) {
        val l: () -> Unit = { rev++ }
        service.addStateListener(l)
        presentation.addListener(l)
        onDispose { service.removeStateListener(l); presentation.removeListener(l) }
    }
    @Suppress("UNUSED_EXPRESSION") rev
    // main.dart _buildMinimizedCallCapsule.
    val session = service.currentSession
    val live = session != null && ServiceLocator.apiBackend.isAuthenticated && when (session.state) {
        CallSessionState.initiating, CallSessionState.ringing, CallSessionState.connecting,
        CallSessionState.connected, CallSessionState.reconnecting -> true
        else -> false
    }
    val islandEnabled = ServiceLocator.templateController.calls.enableFloatingIsland
    val capsule = if (live && session != null && !presentation.isFullScreen &&
        !(presentation.isInAppVideoPip || (presentation.isInAppIsland && islandEnabled))
    ) {
        ChatyActivity("call_capsule") {
            ChatyCallActivityCapsule(
                contactName = session.remoteDisplayName,
                status = when (session.state) {
                    CallSessionState.initiating -> "Calling…"
                    CallSessionState.ringing -> if (session.isOutgoing) "Ringing…" else "Connecting…"
                    CallSessionState.connecting -> "Connecting…"
                    CallSessionState.reconnecting -> "Reconnecting…"
                    CallSessionState.connected -> if (session.isMuted) "Connected · muted" else "Connected"
                    else -> "Call"
                },
                isVideo = session.isVideo,
                isSpeaker = session.audioRoute == AudioRouteType.speaker,
                onOpen = { presentation.showFullScreen() },
                onToggleSpeaker = {
                    scope.launch {
                        try {
                            service.setAudioRoute(
                                if (session.audioRoute == AudioRouteType.speaker) AudioRouteType.earpiece else AudioRouteType.speaker,
                            )
                        } catch (_: Exception) {}
                    }
                },
                onHangUp = { scope.launch { try { service.endCall() } catch (_: Exception) {} } },
            )
        }
    } else null
    ChatyGlobalActivityHost(primaryActivity = capsule) {
        ClickParticleOverlay(preferences = prefs) {
            FallingParticlesOverlay(preferences = prefs, currentScope = "Home", content = content)
        }
    }
}
