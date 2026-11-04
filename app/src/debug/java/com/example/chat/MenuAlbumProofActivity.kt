package com.example.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.chat.core.theme.ThemePresets
import com.example.chat.data.realtime.ContactActivityState
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.ContactConnectionStatus
import com.example.chat.domain.models.DeliveryState
import com.example.chat.domain.models.MessageAttachment
import com.example.chat.domain.models.MessageType
import com.example.chat.domain.models.PresenceState
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.chats.ChatDetailScreen
import com.example.chat.ui.chats.contact_info.ContactInfoScreen
import com.example.chat.ui.chats.contact_info.ConversationMediaDialog
import com.example.chat.ui.chats.detail.MediaAlbumContract
import com.example.chat.ui.profile.ProfileScreen
import com.example.chat.ui.theme.ChatyTheme
import java.time.Instant

/** Debug-build-only and exported so `am start` from the shell/capture harness can
 *  reach it (ChatFixtureActivity is non-exported and cannot). Renders real screens
 *  with local fixture data for pixel proof of (a) the chat header overflow menu
 *  with icons, (b) a >4 image album capped at 2x2 with the +N scrim, (c) the
 *  contact-info screen, (d) the All-media surface with seeded media/docs/links
 *  including a video duration badge, and (e) the profile screen. All screens use
 *  default params. No backend, no account, no messages leave the device;
 *  test-only, no product behavior change. */
class MenuAlbumProofActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = intent.getStringExtra(EXTRA_SCREEN) ?: SCREEN_MENU
        setContent {
            ChatyTheme(config = ThemePresets.getSystemDefaultTheme(platformIsLight = false)) {
                when (screen) {
                    SCREEN_CONTACT_INFO -> ContactInfoScreen(
                        conversation = ChatFixtureActivity.fixtureConversations().first(),
                        contact = fixtureContact(),
                        messages = sharedFixtureMessages(),
                        connection = ContactConnectionStatus(
                            conversationId = "r1",
                            myAccepted = true,
                            otherAccepted = true,
                        ),
                        blocked = false,
                        loading = false,
                        loadError = null,
                        busy = false,
                        isOnline = { false },
                        lastSeenFor = { Instant.now().minusSeconds(3600) },
                        activityFor = { _, _ -> ContactActivityState(updatedAt = Instant.now()) },
                        hydrateMessage = { it },
                        onAccept = {},
                        onSetBlocked = {},
                        onDeleteConversation = {},
                        onDeleteUser = {},
                        onOpenPrivacy = {},
                        onOpenMedia = {},
                        onSearch = {},
                        onDeletedPopToRoot = {},
                        onBack = {},
                    )
                    SCREEN_ALL_MEDIA -> ConversationMediaDialog(
                        messages = sharedFixtureMessages(),
                        onOpen = {},
                        onClose = {},
                    )
                    SCREEN_PROFILE -> ProfileScreen(
                        user = fixtureContact(),
                        chatsCount = 12,
                        groupsCount = 3,
                        contactsCount = 48,
                        onEditProfile = {},
                        onOpenSettings = {},
                        onOpenPrivacyCenter = {},
                        onOpenNotifications = {},
                        onLogout = {},
                    )
                    else -> ChatDetailScreen(
                        conversation = ChatFixtureActivity.fixtureConversations().first(),
                        messages = if (screen == SCREEN_ALBUM) albumMessages() else ChatFixtureActivity.fixtureMessages(),
                        currentUserId = "me",
                        initialChatMenuOpen = screen == SCREEN_MENU,
                        onBack = {},
                        onSend = {},
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_MENU = "menu"
        const val SCREEN_ALBUM = "album"
        const val SCREEN_CONTACT_INFO = "contactinfo"
        const val SCREEN_ALL_MEDIA = "allmedia"
        const val SCREEN_PROFILE = "profile"

        /** Fixture contact shared by the contact-info and profile proofs. */
        fun fixtureContact(): UserProfile = UserProfile(
            id = "u2",
            displayName = "Alice Rao",
            username = "alicerao",
            avatarInitials = "AR",
            avatarColorHex = "0xFF6366F1",
            about = "Product designer. Trekking on weekends.",
            presence = PresenceState.offline,
            lastSeenAt = Instant.now().minusSeconds(3600),
        )

        /** Shared-media fixture: three images, one video with a duration (the
         *  badge proof), one document and one text carrying a link so all three
         *  All-media tabs have rows. None are deleted/restricted, so all pass
         *  the indexable filter. */
        fun sharedFixtureMessages(): List<ChatMessage> = listOf(
            ChatMessage(
                id = "proof_s_img1",
                conversationId = "r1",
                senderId = "u2",
                type = MessageType.image,
                text = "",
                attachment = MessageAttachment("proof_s_a1", "image", "ridge.jpg", "2 MB"),
                createdAt = Instant.now().minusSeconds(600),
                deliveryState = DeliveryState.delivered,
            ),
            ChatMessage(
                id = "proof_s_img2",
                conversationId = "r1",
                senderId = "me",
                type = MessageType.image,
                text = "",
                attachment = MessageAttachment("proof_s_a2", "image", "camp.jpg", "3 MB"),
                createdAt = Instant.now().minusSeconds(590),
                deliveryState = DeliveryState.read,
            ),
            ChatMessage(
                id = "proof_s_img3",
                conversationId = "r1",
                senderId = "u2",
                type = MessageType.image,
                text = "",
                attachment = MessageAttachment("proof_s_a3", "image", "summit.jpg", "2 MB"),
                createdAt = Instant.now().minusSeconds(580),
                deliveryState = DeliveryState.delivered,
            ),
            ChatMessage(
                id = "proof_s_vid1",
                conversationId = "r1",
                senderId = "u2",
                type = MessageType.video,
                text = "",
                attachment = MessageAttachment("proof_s_a4", "video", "trail.mp4", "18 MB", durationSeconds = 83),
                createdAt = Instant.now().minusSeconds(570),
                deliveryState = DeliveryState.delivered,
            ),
            ChatMessage(
                id = "proof_s_doc1",
                conversationId = "r1",
                senderId = "me",
                type = MessageType.document,
                text = "",
                attachment = MessageAttachment("proof_s_a5", "document", "permits.pdf", "640 KB"),
                createdAt = Instant.now().minusSeconds(560),
                deliveryState = DeliveryState.sent,
            ),
            ChatMessage(
                id = "proof_s_link1",
                conversationId = "r1",
                senderId = "u2",
                type = MessageType.text,
                text = "Route link https://example.com/trek-route",
                createdAt = Instant.now().minusSeconds(550),
                deliveryState = DeliveryState.delivered,
            ),
        )

        /** Six envelope-stamped images: the timeline groups them and the preview
         *  caps at four tiles with a "+2" scrim on the last visible tile. */
        fun albumMessages(): List<ChatMessage> = (0 until 6).map { index ->
            ChatMessage(
                id = "proof_album_$index",
                conversationId = "r1",
                senderId = "u2",
                type = MessageType.image,
                text = "",
                attachment = MessageAttachment(
                    id = "proof_att_$index",
                    type = "image",
                    name = "proof_$index.jpg",
                    size = "1 KB",
                    url = null,
                ),
                metadata = MediaAlbumContract.stamp("proof-album", index, 6),
                createdAt = Instant.now().minusSeconds(600 - index * 10L),
                deliveryState = DeliveryState.delivered,
            )
        }
    }
}
