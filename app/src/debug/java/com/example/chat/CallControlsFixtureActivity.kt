package com.example.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.chat.core.theme.ThemePresets
import com.example.chat.data.services.*
import com.example.chat.ui.calls.*
import com.example.chat.ui.theme.ChatyTheme
import java.time.Instant

/** No signaling, backend, camera or live call callbacks. */
class CallControlsFixtureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("geometry", false)) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            requestedOrientation = if (intent.getBooleanExtra("landscape", false))
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        setContent {
            ChatyTheme(config = ThemePresets.getSystemDefaultTheme(platformIsLight = !intent.getBooleanExtra("dark", false))) {
                val currentDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(currentDensity.density,
                    intent.getFloatExtra("fontScale", currentDensity.fontScale))) {
                var callback by remember { mutableStateOf("none") }
                val video = intent.getBooleanExtra("video", false)
                val width = intent.getIntExtra("widthDp", 320)
                val geometry = intent.getBooleanExtra("geometry", false)
                val incoming = intent.getBooleanExtra("incoming", false)
                val template = CallScreenTemplate.entries.firstOrNull { it.wireName == intent.getStringExtra("template") } ?: CallScreenTemplate.edge
                val session = ChatyCallSession("fixture", "fixture-peer", "Fixture Peer", isVideo = video,
                    isOutgoing = !incoming, state = if (incoming) CallSessionState.incoming else CallSessionState.ringing,
                    startedAt = Instant.now(), isDemo = true)
                Box(if (geometry) Modifier.fillMaxSize() else Modifier.width(width.dp)) {
                    if (incoming) IncomingCallScreen(session, template,
                        onAccept = { callback = "accept" }, onDecline = { callback = "decline" })
                    else OngoingCallScreen(
                        session = session,
                        callDurationSeconds = 0, hasLocalMedia = true, videoSurfaces = null, setupError = null,
                        template = template, pictureInPictureEnabled = false,
                        onBack = {}, onSessionGone = {}, onEndCall = { callback = "end" },
                        onToggleMute = { callback = "mute" }, onToggleCamera = { callback = "camera" },
                        onSwitchCamera = { callback = "switch" }, onToggleScreenShare = { callback = "share" },
                        onToggleAudioRoute = { callback = "audio" }, onEnterNativePip = {}, autoHideControls = false,
                    )
                    Text("Fixture callback:$callback")
                }
                }
            }
        }
    }
}
