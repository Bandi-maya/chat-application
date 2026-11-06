package com.example.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.*
import com.example.chat.ui.chats.group_info.GroupInfoScreen
import com.example.chat.ui.profile.ProfileEditScreen
import com.example.chat.ui.theme.ChatyTheme
import java.time.Instant
import kotlinx.coroutines.delay

/** Synthetic callbacks only. No ServiceLocator, account, contacts, network, or media upload. */
class ContactsProfileProofActivity : ComponentActivity() {
    @Volatile var backCount = 0
    @Volatile var saveCount = 0
    @Volatile var leaveCount = 0
    @Volatile var pickCount = 0
    @Volatile var privacySaveCount = 0
    @Volatile var savedPrivacy: ContactPrivacyOverride? = null
    @Volatile var saved: List<String?> = emptyList()
    private fun user(id: String, name: String) = UserProfile(
        id, name, "fixture_$id", "FM", "0xFF6366F1", "Synthetic about",
        lastSeenAt = Instant.parse("2026-10-25T12:00:00Z"), phone = "Not a real number",
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val me = user("me", "Fixture Maya")
        val peer = user("peer", "Fixture Member")
        val group = Conversation(
            id = "synthetic-group", type = ConversationType.group, title = "Fixture Group",
            participantIds = listOf(me.id, peer.id), adminIds = listOf(me.id),
            lastMessageText = "Synthetic", lastMessageTime = me.lastSeenAt, lastMessageSenderId = me.id,
        )
        setContent {
            ChatyTheme(config = ThemePresets.getSystemDefaultTheme(platformIsLight = true)) {
                if (intent.getBooleanExtra("privacy", false)) {
                    com.example.chat.ui.chats.contact_info.ContactPrivacyScreen(
                        contact = peer,
                        privacyFor = { ContactPrivacyOverride(me.id, peer.id) },
                        savePrivacy = {
                            privacySaveCount++
                            savedPrivacy = it
                            delay(100)
                            error("Synthetic privacy failure")
                        },
                        onBack = { backCount++ },
                    )
                } else if (intent.getBooleanExtra("group", false)) {
                    GroupInfoScreen(group, listOf(me, peer), me.id,
                        onLeave = { leaveCount++ }, onBack = { backCount++ })
                } else {
                    ProfileEditScreen(
                        user = me, onBack = { backCount++ },
                        onPickAvatar = { pickCount++; null },
                        onPickBanner = { pickCount++; null },
                        onSave = { name, username, about, avatar, banner ->
                            saveCount++
                            saved = listOf(name, username, about, avatar, banner)
                            delay(100)
                            if (intent.getBooleanExtra("saveFailure", false)) error("Synthetic save failure")
                        },
                    )
                }
            }
        }
    }
}
