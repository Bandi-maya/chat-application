package com.example.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.core.theme.ChatyColors
import com.example.chat.ui.navigation.CurvedRadialDrawerShell
import com.example.chat.ui.theme.ChatyTheme

/** Real radial implementation, synthetic page only. Never signs in or writes preferences. */
class RadialPlacementProofActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val config = ThemePresets.getSystemDefaultTheme(platformIsLight = true)
            ChatyTheme(config) {
                var selected by remember { mutableStateOf(0) }
                val ids = listOf("chats", "updates", "calls", "tasks", "settings")
                CurvedRadialDrawerShell(
                    colors = ChatyColors(config), navItemIds = ids, selectedIndex = selected,
                    animationsDisabled = true, onSelect = { selected = it }, fab = {},
                    content = {
                        Column(Modifier.fillMaxSize().background(Color.White)) {
                            Text("Synthetic connection banner", Modifier.fillMaxWidth().height(40.dp)
                                .background(Color(0xFFFFF2D6)).semantics { contentDescription = "radial-proof-banner" })
                            Text("Synthetic identity", Modifier.fillMaxWidth().height(64.dp)
                                .semantics { contentDescription = "radial-proof-identity" })
                            Text("Selected: ${ids[selected]}")
                        }
                    },
                )
            }
        }
    }
}
