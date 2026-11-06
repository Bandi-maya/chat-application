package com.example.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.theme.ChatyTheme

/**
 * Debug-build-only host for whole-app small/floating-window (compact width) verification.
 * Renders the production chat screens with ChatFixtureActivity's synthetic data inside a
 * width-constrained container so emulator instrumentation can assert layout at mini-window
 * widths. No backend, no account, no real device settings; pixel-rendering proof only.
 *
 * Extras: "screen"=home|detail, "widthDp" (default 320), "fontScale", "dark", "landscape".
 */
class CompactWindowFixtureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = if (intent.getBooleanExtra("landscape", false))
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContent {
            ChatyTheme(config = ThemePresets.getSystemDefaultTheme(platformIsLight = !intent.getBooleanExtra("dark", false))) {
                val currentDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(
                    currentDensity.density,
                    intent.getFloatExtra("fontScale", currentDensity.fontScale))) {
                    val widthDp = intent.getIntExtra("widthDp", 320)
                    val screen = intent.getStringExtra("screen") ?: "home"
                    Column(Modifier.width(widthDp.dp)) {
                        // Harness self-check probe only: proves scenario.recreate() state retention
                        // works inside this fixture (rememberSaveable). It is NOT evidence about
                        // the production composer's draft persistence.
                        var probe by rememberSaveable { mutableStateOf("") }
                        androidx.compose.material3.OutlinedTextField(
                            value = probe,
                            onValueChange = { probe = it },
                            label = { androidx.compose.material3.Text("Fixture state probe") },
                        )
                        Box {
                            when (screen) {
                                "detail" -> com.example.chat.ui.chats.ChatDetailScreen(
                                    conversation = ChatFixtureActivity.fixtureConversations().first(),
                                    messages = ChatFixtureActivity.fixtureMessages(),
                                    currentUserId = "me",
                                    onBack = {},
                                    onSend = {},
                                )
                                else -> com.example.chat.ui.chats.ChatsHomeScreen(
                                    conversations = ChatFixtureActivity.fixtureConversations(),
                                    requiresSubscription = false,
                                    subscriptionMessage = null,
                                    onOpenConversation = {},
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
