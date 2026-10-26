package com.example.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
        setContent {
            ChatyTheme(config = ThemePresets.getSystemDefaultTheme(platformIsLight = true)) {
                var callback by remember { mutableStateOf("none") }
                val video = intent.getBooleanExtra("video", false)
                val width = intent.getIntExtra("widthDp", 320)
                Box(Modifier.width(width.dp)) {
                    OngoingCallScreen(
                        session = ChatyCallSession("fixture", "fixture-peer", "Fixture Peer", isVideo = video,
                            isOutgoing = true, state = CallSessionState.ringing, startedAt = Instant.now(), isDemo = true),
                        callDurationSeconds = 0, hasLocalMedia = true, videoSurfaces = null, setupError = null,
                        template = CallScreenTemplate.edge, pictureInPictureEnabled = false,
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
