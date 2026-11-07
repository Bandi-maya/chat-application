package com.example.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.chats.detail.ChatyEmojiPickerSheet
import com.example.chat.ui.theme.ChatyTheme

/** Debug-only offline host of the real picker component. No account or send callback. */
class NewsEmojiPanelProofActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val light = !intent.getBooleanExtra("dark", false)
        val theme = ThemePresets.getSystemDefaultTheme(platformIsLight = light)
        setContent {
            ChatyTheme(theme) {
                Column(Modifier.fillMaxSize().background(Color(theme.backgroundColor)).statusBarsPadding().navigationBarsPadding()) {
                    Text("Offline panel fixture · no account or sending", color = Color(theme.secondaryTextColor),
                        modifier = Modifier.padding(16.dp))
                    Spacer(Modifier.weight(1f))
                    ChatyEmojiPickerSheet(theme = theme, reactionMode = false, composerMode = true)
                }
            }
        }
    }
}
