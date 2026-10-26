package com.example.chat.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.example.chat.ui.kit.ChatySingleChoiceModal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.RingVolume
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material.icons.rounded.SpeakerPhone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.theme.LocalAppColors

/** Contact row for the exceptions dialog (dataStore.contacts id + displayName). */
data class CallContactOption(val id: String, val displayName: String)

/** Call-template choice: [name] is the stored value (CallScreenTemplate.name /
 *  wireName per the Calls worker contract), [label] its displayName. */
data class CallTemplateOption(val name: String, val label: String)

/** Call presentation state mirrored from CallPresentationPreferencesStore.value;
 *  the caller maps its store into this and wires the setters below. */
data class CallPresentationState(
    val dynamicIslandEnabled: Boolean = true,
    val pictureInPictureEnabled: Boolean = true,
    val lowDataUsageEnabled: Boolean = false,
    val incomingCallTemplate: String,
    val ongoingCallTemplate: String,
)

private val audienceOptions = listOf("Everyone", "My Contacts", "My Contacts Except\u2026", "Nobody")
private const val exceptAudience = "My Contacts Except\u2026"

/**
 * Port of call_settings_screen.dart. Decoupled from the call store and the
 * contacts store via parameters so it does not guess the Calls worker's
 * signatures. Quirks kept: "Excluded Contacts" subtitle is literally
 * " contacts excluded" (the Dart string has no count); the ongoing tile calls
 * the outgoing setter ([onOngoingTemplate] should map to
 * setOutgoingCallTemplate, which persists the ongoing key).
 */
@Composable
fun CallSettingsScreen(
    preferences: ChatyPreferencesController,
    call: CallPresentationState,
    templates: List<CallTemplateOption>,
    contacts: List<CallContactOption>,
    onBack: () -> Unit,
    onIncomingTemplate: (String) -> Unit,
    onOngoingTemplate: (String) -> Unit,
    onDynamicIsland: (Boolean) -> Unit,
    onPictureInPicture: (Boolean) -> Unit,
    onLowData: (Boolean) -> Unit,
    onOpenAudioSettings: () -> Unit,
) {
    rememberPreferencesVersion(preferences)
    val colors = LocalAppColors.current
    val privacy = preferences.privacy
    // 0 none, 1 who-can-call, 2 incoming template, 3 ongoing template, 4 exceptions
    var dialog by remember { mutableStateOf(0) }

    fun label(name: String) = templates.firstOrNull { it.name == name }?.label ?: templates.firstOrNull()?.label ?: name

    ChatySettingsPage("Calls", "Incoming call privacy, background audio & visual island", onBack = onBack) {
        ChatySettingsSection("Incoming Call Privacy", "Control who is permitted to initiate voice and video calls with you.") {
            ChatySettingsTile(title = "Who Can Call Me", icon = Icons.Rounded.RingVolume, iconColor = colors.primary, subtitle = privacy.whoCanCallMe, trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = colors.foregroundSecondary) }, onTap = { dialog = 1 })
            if (privacy.whoCanCallMe == exceptAudience) {
                ChatySettingsRowDivider()
                ChatySettingsTile(title = "Excluded Contacts", icon = Icons.Outlined.PersonOff, iconColor = colors.error, subtitle = " contacts excluded", trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = colors.foregroundSecondary) }, onTap = { dialog = 4 })
            }
        }
        ChatySettingsSection(
            "Call Screen Style",
            "Chaty-designed screen layouts. GB WhatsApp customizes call colors (see GB Features) but ships no screen templates.",
        ) {
            ChatySettingsTile(title = "Incoming Call Screen", icon = Icons.Rounded.CallReceived, iconColor = colors.primary, subtitle = label(call.incomingCallTemplate), trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = colors.foregroundSecondary) }, onTap = { dialog = 2 })
            ChatySettingsRowDivider()
            ChatySettingsTile(title = "Ongoing Call Screen", icon = Icons.Rounded.CallMade, iconColor = colors.primary, subtitle = label(call.ongoingCallTemplate), trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = colors.foregroundSecondary) }, onTap = { dialog = 3 })
        }
        ChatySettingsSection("In-App Call Presentation") {
            ChatySwitchTile(title = "Dynamic Call Island", icon = Icons.Rounded.BubbleChart, iconColor = colors.primary, subtitle = "Display interactive floating capsule when navigating away from active calls.", value = call.dynamicIslandEnabled, onChanged = onDynamicIsland)
            ChatySettingsRowDivider()
            ChatySwitchTile(title = "Picture-in-Picture Video", icon = Icons.Rounded.PictureInPicture, iconColor = colors.primary, subtitle = "Automatically minimize video stream into PiP window when switching apps.", value = call.pictureInPictureEnabled, onChanged = onPictureInPicture)
        }
        ChatySettingsSection("Audio & Connectivity") {
            ChatySettingsTile(title = "Dual-Speaker & Output Routing", icon = Icons.Rounded.SpeakerGroup, iconColor = colors.accent, subtitle = "Configure receiver, bottom speaker, and capability routing", trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = colors.foregroundSecondary) }, onTap = onOpenAudioSettings)
            ChatySettingsRowDivider()
            ChatySwitchTile(title = "Low Data Usage for Calls", icon = Icons.Rounded.SpeakerPhone, iconColor = colors.success, subtitle = "Optimize WebRTC bandwidth consumption on cellular connections.", value = call.lowDataUsageEnabled, onChanged = onLowData)
        }
    }

    when (dialog) {
        1 -> ChatySingleChoiceModal(
            title = "Who Can Call Me",
            description = "Choose who can reach you via audio and video calls.",
            value = privacy.whoCanCallMe,
            options = audienceOptions,
            labelBuilder = { it },
            onApply = { chosen ->
                preferences.updatePrivacy(privacy.copy(whoCanCallMe = chosen), logTitle = "Who Can Call Me")
                dialog = if (chosen == exceptAudience) 4 else 0
            },
            onDismiss = { if (dialog == 1) dialog = 0 },
        )
        2 -> ChatySingleChoiceModal(
            title = "Incoming Call Screen",
            description = "Chaty design applied to the incoming call UI.",
            value = call.incomingCallTemplate,
            options = templates.map { it.name },
            labelBuilder = { label(it) },
            onApply = { onIncomingTemplate(it); dialog = 0 },
            onDismiss = { dialog = 0 },
        )
        3 -> ChatySingleChoiceModal(
            title = "Ongoing Call Screen",
            description = "Chaty design applied while a call is active.",
            value = call.ongoingCallTemplate,
            options = templates.map { it.name },
            labelBuilder = { label(it) },
            onApply = { onOngoingTemplate(it); dialog = 0 },
            onDismiss = { dialog = 0 },
        )
        4 -> {
            val selected = remember { privacy.whoCanCallMeExceptions.toMutableStateList() }
            AlertDialog(
                onDismissRequest = { dialog = 0 },
                containerColor = colors.surfaceElevated,
                title = { Text("Contacts who may not call you", color = colors.foreground) },
                text = {
                    if (contacts.isEmpty()) {
                        Text("No contacts found.", color = colors.foreground, modifier = Modifier.padding(vertical = 18.dp))
                    } else {
                        LazyColumn {
                            items(contacts) { c ->
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        if (selected.contains(c.id)) selected.remove(c.id) else selected.add(c.id)
                                    },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(checked = selected.contains(c.id), onCheckedChange = {
                                        if (it) { if (!selected.contains(c.id)) selected.add(c.id) } else selected.remove(c.id)
                                    })
                                    Spacer(Modifier.width(4.dp))
                                    Text(c.displayName, color = colors.foreground)
                                }
                            }
                        }
                    }
                },
                dismissButton = { TextButton(onClick = { dialog = 0 }) { Text("Cancel") } },
                confirmButton = {
                    TextButton(onClick = {
                        preferences.updatePrivacy(
                            preferences.privacy.copy(whoCanCallMeExceptions = selected.toList()),
                            logTitle = "Who Can Call Me Exceptions",
                        )
                        dialog = 0
                    }) { Text("Save") }
                },
            )
        }
    }
}
