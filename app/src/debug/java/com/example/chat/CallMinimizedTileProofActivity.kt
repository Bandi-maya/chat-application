package com.example.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.chat.core.theme.ThemePresets
import com.example.chat.data.services.CallSessionState
import com.example.chat.data.services.ChatyCallSession
import com.example.chat.ui.calls.InAppCallPip
import com.example.chat.ui.theme.ChatyTheme
import java.time.Instant

/** Production tile, synthetic session only: no media/account/permission access. */
class CallMinimizedTileProofActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val connected = intent.getBooleanExtra("connected", false)
        setContent {
            ChatyTheme(config = ThemePresets.getSystemDefaultTheme(platformIsLight = false)) {
                Box(Modifier.fillMaxSize().background(Color.Magenta)) {
                    InAppCallPip(
                        session = ChatyCallSession("tile-fixture", "fixture-peer", "Synthetic tile", isVideo = true,
                            isOutgoing = true, state = if (connected) CallSessionState.connected else CallSessionState.ringing,
                            startedAt = Instant.now(), isDemo = true),
                        videoSurfaces = null, durationSeconds = 80,
                        onTap = {}, onCollapseToIsland = {}, onEndCall = {},
                    )
                }
            }
        }
    }
}
