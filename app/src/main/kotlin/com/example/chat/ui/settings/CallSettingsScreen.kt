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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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

    SettingsPage("Calls", "Incoming call privacy, background audio & visual island", onBack) {
        SettingsSection("Incoming Call Privacy", "Control who is permitted to initiate voice and video calls with you.") {
            SettingsNavTile("\u260E", "Who Can Call Me", privacy.whoCanCallMe, colors.primary) { dialog = 1 }
            if (privacy.whoCanCallMe == exceptAudience) {
                SettingsDivider()
                SettingsNavTile("\u2298", "Excluded Contacts", " contacts excluded", colors.error) { dialog = 4 }
            }
        }
        SettingsSection(
            "Call Screen Style",
            "Chaty-designed screen layouts. GB WhatsApp customizes call colors (see GB Features) but ships no screen templates.",
        ) {
            SettingsNavTile("\u2199", "Incoming Call Screen", label(call.incomingCallTemplate), colors.primary) { dialog = 2 }
            SettingsDivider()
            SettingsNavTile("\u2197", "Ongoing Call Screen", label(call.ongoingCallTemplate), colors.primary) { dialog = 3 }
        }
        SettingsSection("In-App Call Presentation") {
            SettingsSwitchTile("\u25CB", "Dynamic Call Island", "Display interactive floating capsule when navigating away from active calls.", call.dynamicIslandEnabled, colors.primary, onDynamicIsland)
            SettingsDivider()
            SettingsSwitchTile("\u25A3", "Picture-in-Picture Video", "Automatically minimize video stream into PiP window when switching apps.", call.pictureInPictureEnabled, colors.primary, onPictureInPicture)
        }
        SettingsSection("Audio & Connectivity") {
            SettingsNavTile("\u266B", "Dual-Speaker & Output Routing", "Configure receiver, bottom speaker, and capability routing", colors.accent, onOpenAudioSettings)
            SettingsDivider()
            SettingsSwitchTile("\u266A", "Low Data Usage for Calls", "Optimize WebRTC bandwidth consumption on cellular connections.", call.lowDataUsageEnabled, colors.success, onLowData)
        }
    }

    when (dialog) {
        1 -> SettingsRadioDialog("Who Can Call Me", audienceOptions, privacy.whoCanCallMe, { chosen ->
            preferences.updatePrivacy(privacy.copy(whoCanCallMe = chosen), logTitle = "Who Can Call Me")
            if (chosen == exceptAudience) dialog = 4
        }, { if (dialog == 1) dialog = 0 })
        2 -> SettingsRadioDialog("Incoming Call Screen", templates.map { it.label }, label(call.incomingCallTemplate), { picked ->
            templates.firstOrNull { it.label == picked }?.let { onIncomingTemplate(it.name) }
        }, { dialog = 0 })
        3 -> SettingsRadioDialog("Ongoing Call Screen", templates.map { it.label }, label(call.ongoingCallTemplate), { picked ->
            templates.firstOrNull { it.label == picked }?.let { onOngoingTemplate(it.name) }
        }, { dialog = 0 })
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
