package com.example.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.ConnectionHealth
import com.example.chat.domain.models.ConnectionHealthState
import com.example.chat.domain.models.HomePreferences
import com.example.chat.ui.chats.ChatsHomeScreen
import com.example.chat.ui.chats.home.ChatsHomeNav
import com.example.chat.ui.theme.ChatyTheme
import java.time.Instant

/** Debug-only synthetic host: real ChatsHomeScreen with fixture conversations, a weak-connection state
 *  and counting callbacks. No ServiceLocator, session, network or account. Counters are shown as text
 *  ("lock=N health=N settings=N vault=N") so UiAutomator can read them. */
class HomeHeaderProofActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val nameTapOn = intent.getBooleanExtra(EXTRA_NAME_TAP_ON, false)
        setContent {
            var lockTaps by remember { mutableIntStateOf(0) }
            var healthTaps by remember { mutableIntStateOf(0) }
            var settingsTaps by remember { mutableIntStateOf(0) }
            var vaultOpens by remember { mutableIntStateOf(0) }
            ChatyTheme(config = ThemePresets.getSystemDefaultTheme(platformIsLight = true)) {
                Column(Modifier.fillMaxSize()) {
                    Text("lock=$lockTaps health=$healthTaps settings=$settingsTaps vault=$vaultOpens")
                    Box(Modifier.weight(1f)) {
                        ChatsHomeScreen(
                            conversations = ChatFixtureActivity.fixtureConversations(),
                            requiresSubscription = false,
                            subscriptionMessage = null,
                            onOpenConversation = {},
                            nav = ChatsHomeNav(
                                onOpenSettings = { settingsTaps++ },
                                onOpenLockedVault = { vaultOpens++ },
                            ),
                            connectionHealthState = ConnectionHealthState(
                                health = ConnectionHealth.weak, lastCheckedAt = Instant.now(),
                            ),
                            homePreferences = HomePreferences().copy(nameTapOpensLocked = nameTapOn),
                            onOpenConnectionDetail = { healthTaps++ },
                            lockedChatsConfigured = true,
                            onRequestVaultAuth = { lockTaps++ },
                        )
                    }
                }
            }
        }
    }

    companion object { const val EXTRA_NAME_TAP_ON = "nameTapOn" }
}
