package com.example.chat.ui.settings.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.NotificationImportant
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AlignVerticalTop
import androidx.compose.material.icons.rounded.Attachment
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Forward
import androidx.compose.material.icons.rounded.GTranslate
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MenuOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Portrait
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.kit.ChatyHapticsPort
import com.example.chat.ui.kit.GbChevronRow
import com.example.chat.ui.kit.GbColorRow
import com.example.chat.ui.kit.GbPanelCard
import com.example.chat.ui.kit.GbRowDivider
import com.example.chat.ui.kit.GbScreenScaffold
import com.example.chat.ui.kit.GbSectionLabel
import com.example.chat.ui.kit.GbSubpageRow
import com.example.chat.ui.kit.GbSwitchRow
import com.example.chat.ui.kit.rememberGbPickers

/** Port of ConversationSettingsPage (conversation_settings_page.dart). UNCOMPILED (Compose). */
@Composable
fun ConversationSettingsPage(
    preferences: ChatyPreferencesController,
    onBack: () -> Unit,
    onOpenActionBar: () -> Unit,
    onOpenBubbleAndTicks: () -> Unit,
    onOpenEntryStyle: () -> Unit,
    onOpenMoreOptions: () -> Unit,
) {
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(preferences) {
        val l: () -> Unit = { version += 1 }
        preferences.addListener(l)
        onDispose { preferences.removeListener(l) }
    }
    val pickers = rememberGbPickers()
    val view = androidx.compose.ui.platform.LocalView.current
    // Dart _openSubScreen: HapticFeedback.selectionClick() before pushing (on top of the row's own selectionClick).
    fun openSub(go: () -> Unit) { ChatyHapticsPort.selection(view); go() }
    @Suppress("UNUSED_EXPRESSION") version
    val c = preferences.conversation

    GbScreenScaffold(title = "Conversation Screen", onBack = onBack) {
        GbPanelCard {
            GbSubpageRow(Icons.Outlined.TableChart, "Action Bar") { openSub(onOpenActionBar) }
            GbRowDivider()
            GbSubpageRow(Icons.Rounded.ChatBubbleOutline, "Bubble And Ticks") { openSub(onOpenBubbleAndTicks) }
            GbRowDivider()
            GbSubpageRow(Icons.Rounded.EditNote, "Conversation Entry style") { openSub(onOpenEntryStyle) }
            GbRowDivider()
            GbSubpageRow(Icons.Rounded.MoreHoriz, "More options") { openSub(onOpenMoreOptions) }
        }
        Spacer(Modifier.height(16.dp))
        GbPanelCard {
            GbSectionLabel("Mods")
            GbSwitchRow(Icons.Rounded.Link, "Switch direct contact link.", "Currently direct contact links are creating with the 'https://api.whatsapp.com/' prefix", c.switchDirectContactLink) {
                preferences.updateConversation(c.copy(switchDirectContactLink = it), logTitle = "Switch direct contact link")
            }
            GbSwitchRow(Icons.Outlined.StickyNote2, "Confirm before sending a Sticker", "Ask for confirmation before sending a sticker to chat", c.confirmBeforeSendingSticker) {
                preferences.updateConversation(c.copy(confirmBeforeSendingSticker = it), logTitle = "Confirm sticker")
            }
            GbSwitchRow(Icons.Rounded.Attachment, "New Attachment Picker UI", "Use modern bottom sheet attachment picker", c.newAttachmentPickerUi) {
                preferences.updateConversation(c.copy(newAttachmentPickerUi = it), logTitle = "Attachment picker")
            }
            GbSwitchRow(Icons.Rounded.ContentCopy, "Hide date and name", "Hide the date and the name when copying 2 messages or more", c.hideDateAndName) {
                preferences.updateConversation(c.copy(hideDateAndName = it), logTitle = "Hide date and name on copy")
            }
            GbSectionLabel("Groups")
            GbSwitchRow(Icons.Outlined.AdminPanelSettings, "Hide icon next to admin name", "Hide the icon next to the admin name in groups", c.hideAdminNameIcon) {
                preferences.updateConversation(c.copy(hideAdminNameIcon = it), logTitle = "Hide admin icon")
            }
            GbChevronRow(Icons.Outlined.VerifiedUser, "Group admin icon", c.groupAdminIcon.ifEmpty { "Default" }) {
                pickers.showRadio("Group Admin Icon", listOf("Default", "Star", "Shield", "Crown", "Badge"), c.groupAdminIcon) {
                    preferences.updateConversation(c.copy(groupAdminIcon = it), logTitle = "Group admin icon")
                }
            }
            GbSectionLabel("Quick Replies")
            GbSwitchRow(Icons.Outlined.ViewSidebar, "Quick Contact Sidebar", if (c.enableQuickContactSidebar) "Enabled" else "Disabled", c.enableQuickContactSidebar) {
                preferences.updateConversation(c.copy(enableQuickContactSidebar = it), logTitle = "Quick Contact Sidebar")
            }
            GbChevronRow(Icons.Rounded.AlignVerticalTop, "Quick Contact Sidebar Position", c.quickContactSidebarPosition) {
                val next = if (c.quickContactSidebarPosition == "Top") "Bottom" else "Top"
                preferences.updateConversation(c.copy(quickContactSidebarPosition = next))
            }
            GbColorRow(Icons.Rounded.FormatColorFill, "Quick Contact Background", c.quickContactBgColor) {
                pickers.showColor("Quick Contact Background", c.quickContactBgColor) {
                    preferences.updateConversation(c.copy(quickContactBgColor = it))
                }
            }
            GbColorRow(Icons.Outlined.ColorLens, "Quick Contact Text Color", c.quickContactTextColor) {
                pickers.showColor("Quick Contact Text Color", c.quickContactTextColor) {
                    preferences.updateConversation(c.copy(quickContactTextColor = it))
                }
            }
            GbSwitchRow(Icons.Rounded.AddCircleOutline, "Hide Chat FAB", "Hide the plus button on the top right of the chat screen", c.hideChatFab) {
                preferences.updateConversation(c.copy(hideChatFab = it), logTitle = "Hide Chat FAB")
            }
            GbSectionLabel("Options Click on Hours")
            GbSwitchRow(Icons.Rounded.MenuOpen, "iOS style pop-up menu", "Show the new iOS style popup menu in chats", c.iosStylePopupMenu) {
                preferences.updateConversation(c.copy(iosStylePopupMenu = it), logTitle = "iOS style pop-up menu")
            }
            GbSwitchRow(Icons.Rounded.ChatBubbleOutline, "Disable More Options Dialog from Bubble", "You can disable more options dialog from chat bubble", c.disableMoreOptionsFromBubble) {
                preferences.updateConversation(c.copy(disableMoreOptionsFromBubble = it), logTitle = "Disable bubble options dialog")
            }
            GbSwitchRow(Icons.Outlined.TouchApp, "Disable Double Tap Reaction", "Suppress emoji reaction on double tapping a message", c.disableDoubleTapReaction) {
                preferences.updateConversation(c.copy(disableDoubleTapReaction = it), logTitle = "Disable double tap reaction")
            }
            GbSectionLabel("Translate Option Settings")
            GbChevronRow(Icons.Rounded.Translate, "Translate Option Settings", c.translateOptionSettings) {
                pickers.showRadio("Translate Option Settings", listOf("Server", "In-outside apps", "Server + In-outside apps"), c.translateOptionSettings) {
                    preferences.updateConversation(c.copy(translateOptionSettings = it), logTitle = "Translate option")
                }
            }
            GbSwitchRow(Icons.Rounded.GTranslate, "Hide Message Translation Icon", "Dont show message translation icon in conversation entry", c.hideMessageTranslationIcon) {
                preferences.updateConversation(c.copy(hideMessageTranslationIcon = it), logTitle = "Hide message translation icon")
            }
            GbSectionLabel("Wallpaper")
            GbSwitchRow(Icons.Rounded.Wallpaper, "Custom Wallpaper per contact", "Allows you to set custom wallpaper for each person/conversation", c.customWallpaperPerContact) {
                preferences.updateConversation(c.copy(customWallpaperPerContact = it), logTitle = "Custom wallpaper per contact")
            }
            GbSwitchRow(Icons.Rounded.Portrait, "Profile Pic Wallpaper", "Set profile pic as wallpaper if exists", c.profilePicWallpaper) {
                preferences.updateConversation(c.copy(profilePicWallpaper = it), logTitle = "Profile Pic Wallpaper")
            }
            GbSectionLabel("Voice Notes/Audio Mods")
            GbSwitchRow(Icons.Rounded.Sensors, "Enable Proximity Sensor", "Enabled by default. Disable to turn it off.", c.enableProximitySensor) {
                preferences.updateConversation(c.copy(enableProximitySensor = it), logTitle = "Enable Proximity Sensor")
            }
            GbSwitchRow(Icons.Outlined.VolumeUp, "Disable Output Switching", "Prevents speaker/earpiece switching while playing", c.disableOutputSwitching) {
                preferences.updateConversation(c.copy(disableOutputSwitching = it), logTitle = "Disable Output Switching")
            }
            GbSwitchRow(Icons.Outlined.PlayArrow, "Play Voice Notes", "Disables continuous playback of voice notes", c.playVoiceNotes) {
                preferences.updateConversation(c.copy(playVoiceNotes = it), logTitle = "Play Voice Notes")
            }
            GbSwitchRow(Icons.Rounded.Forward, "Forward as voice note", "If enabled, audio messages will be forwarded as voice notes", c.forwardAsVoiceNote) {
                preferences.updateConversation(c.copy(forwardAsVoiceNote = it), logTitle = "Forward as voice note")
            }
            val ringtones = listOf("Default", "Soft Ping", "Pop Bubble", "Chime Bell", "Silent")
            GbChevronRow(Icons.Outlined.NotificationsNone, "Incoming message ringtone", c.incomingMessageRingtone.ifEmpty { "Default" }) {
                pickers.showRadio("Incoming Message Ringtone", ringtones, c.incomingMessageRingtone) {
                    preferences.updateConversation(c.copy(incomingMessageRingtone = it), logTitle = "Incoming ringtone")
                }
            }
            GbChevronRow(Icons.Outlined.NotificationImportant, "Send message ringtone", c.sendMessageRingtone.ifEmpty { "Default" }) {
                pickers.showRadio("Send Message Ringtone", ringtones, c.sendMessageRingtone) {
                    preferences.updateConversation(c.copy(sendMessageRingtone = it), logTitle = "Send ringtone")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(32.dp))
    }
    pickers.Host()
}
