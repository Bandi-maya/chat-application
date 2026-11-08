package com.example.chat.ui.settings.privacy

import com.example.chat.ui.kit.ChatyHapticsPort
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.NotificationImportant
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ForwardToInbox
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.TvOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.domain.models.PrivacyPreferences
import com.example.chat.ui.kit.ChatyBackButton
import com.example.chat.ui.kit.springInteractiveScale
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.launch

private val AudienceOptions = listOf("Everyone", "My Contacts", "My Contacts Except\u2026", "Nobody")

/** Port of showWhoCanCallMeDialog (privacy_center_screen.dart), reused by the chats home menu. */
@Composable
fun WhoCanCallMeDialog(preferences: ChatyPreferencesController, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    val prefs = preferences.privacy
    AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
        onDismissRequest = onDismiss,
        title = { Text("Who can call me?") },
        text = {
            Column {
                AudienceOptions.forEach { opt ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            preferences.updatePrivacy(prefs.copy(whoCanCallMe = opt), logTitle = "Who can call me")
                            onDismiss()
                        }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = opt == prefs.whoCanCallMe,
                            onClick = {
                                preferences.updatePrivacy(prefs.copy(whoCanCallMe = opt), logTitle = "Who can call me")
                                onDismiss()
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = colors.primary),
                        )
                        Text(opt, Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {},
    )
}

private sealed interface PrivacyModal {
    object WhoCanCall : PrivacyModal
    object LastSeen : PrivacyModal
    object Blocked : PrivacyModal
    class Custom(val title: String) : PrivacyModal
    object Recovery : PrivacyModal
    object Reset : PrivacyModal
}

/**
 * Port of PrivacyCenterScreen. Dart's `_runFlow`, `_showSecretPhraseSetupDialog` and the lock-capability
 * fields are declared (ignore_for_file: unused_*) but never reachable from build(), so they are not ported
 * as UI (the lock flow is in SecurityCenterScreen). UNCOMPILED (Compose).
 */
@Composable
fun PrivacyCenterScreen(
    preferences: ChatyPreferencesController,
    onBack: () -> Unit,
    // Server privacy planes (research 5.2/5.3). Null = wire legacy local
    // behaviour (pre-plane server); the server-backed rows then keep the
    // old local-preference presentation.
    loadPresencePrivacy: (suspend () -> com.example.chat.data.api.ApiPresenceClient.PresencePrivacy?)? = null,
    savePresencePrivacy: (suspend (String?, String?, List<String>?) -> com.example.chat.data.api.ApiPresenceClient.PresencePrivacy?)? = null,
    loadCallPolicy: (suspend () -> com.example.chat.data.api.ApiContactsClient.CallPolicy)? = null,
    saveCallPolicy: (suspend (String, List<String>?) -> com.example.chat.data.api.ApiContactsClient.CallPolicy)? = null,
    loadBlocked: (suspend () -> List<com.example.chat.data.api.ApiContactsClient.BlockedEntry>)? = null,
    unblockUser: (suspend (String) -> Unit)? = null,
) {
    val config = LocalThemeConfig.current
    val appColors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val view = androidx.compose.ui.platform.LocalView.current
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(preferences) {
        val l: () -> Unit = { version += 1 }
        preferences.addListener(l)
        onDispose { preferences.removeListener(l) }
    }
    var modal by remember { mutableStateOf<PrivacyModal?>(null) }

    // Server privacy planes (research 5.2/5.3). null after load = plane
    // unavailable; rows then fail closed to the legacy local behaviour.
    var presencePrivacy by remember { mutableStateOf<com.example.chat.data.api.ApiPresenceClient.PresencePrivacy?>(null) }
    var presenceLoaded by remember { mutableStateOf(false) }
    var serverCallPolicy by remember { mutableStateOf<com.example.chat.data.api.ApiContactsClient.CallPolicy?>(null) }
    var blockedUsers by remember { mutableStateOf<List<com.example.chat.data.api.ApiContactsClient.BlockedEntry>?>(null) }
    var serverBusy by remember { mutableStateOf(false) }
    LaunchedEffect(loadPresencePrivacy, loadCallPolicy, loadBlocked) {
        if (loadPresencePrivacy != null) {
            presencePrivacy = try { loadPresencePrivacy() } catch (_: Exception) { null }
            presenceLoaded = true
        }
        if (loadCallPolicy != null) {
            serverCallPolicy = try { loadCallPolicy() } catch (_: Exception) { null }
        }
        if (loadBlocked != null) {
            blockedUsers = try { loadBlocked() } catch (_: Exception) { null }
        }
    }

    val accent = Color(config.accentColor)
    val bg = Color(config.backgroundColor)
    val primaryText = Color(config.primaryTextColor)
    val secondaryText = Color(config.secondaryTextColor)
    val fs = config.fontScale.toFloat()
    val isDark = config.isDark

    fun snack(msg: String) { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } }

    @Suppress("UNUSED_EXPRESSION") version
    val p = preferences.privacy

    @Composable
    fun Row2(icon: ImageVector, title: String, subtitle: String, vPad: Int, onTap: () -> Unit, trailing: @Composable () -> Unit) {
        Box(Modifier.springInteractiveScale(0.98f)) {
            Row(
                Modifier.fillMaxWidth()
                    .clickable { ChatyHapticsPort.selection(view); onTap() }
                    .padding(horizontal = 16.dp, vertical = vPad.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(38.dp).background(accent.copy(alpha = 0.12f), RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = primaryText, fontSize = (14.5f * fs).sp, fontWeight = FontWeight.W600)
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, color = secondaryText, fontSize = (11.5f * fs).sp)
                }
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
    }

    @Composable
    fun SwitchRow(icon: ImageVector, title: String, subtitle: String, value: Boolean, onChanged: (Boolean) -> Unit) {
        Row2(icon, title, subtitle, 8, onTap = { onChanged(!value) }) {
            // Dart: IgnorePointer(Switch(onChanged: null)) -> disabled-look switch driven by row tap.
            Switch(
                checked = value,
                onCheckedChange = null,
                enabled = false,
                colors = com.example.chat.ui.kit.chatySwitchColors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f)),
            )
        }
    }

    @Composable
    fun ChevronRow(icon: ImageVector, title: String, subtitle: String, onTap: () -> Unit) {
        Row2(icon, title, subtitle, 11, onTap) {
            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight, semanticLabel = null, color = accent, modifier = Modifier.size(22.dp))
        }
    }

    @Composable
    fun SectionDivider(label: String) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 6.dp)) {
            Text(
                label, Modifier.fillMaxWidth(), color = accent, fontWeight = FontWeight.W700, fontSize = 16.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth().height(1.2.dp).background(
                    Brush.horizontalGradient(listOf(accent.copy(alpha = 0f), accent.copy(alpha = 0.7f), accent.copy(alpha = 0f))),
                ),
            )
        }
    }

    @Composable
    fun CardContainer(content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier.fillMaxWidth()
                .shadow(
                    4.dp, shape,
                    ambientColor = appColors.shadow, spotColor = appColors.shadow,
                )
                .background(if (isDark) Color(0xFF1E2124) else appColors.surface, shape)
                .border(0.9.dp, appColors.borderSubtle, shape)
                .clip(shape),
        ) { content() }
    }

    Column(Modifier.fillMaxSize().background(bg)) {
        // AppBar: back button in 8dp padding, title 20*fontScale w700 letterSpacing -0.3.
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(8.dp)) { ChatyBackButton(onPressed = onBack) }
            Text(
                "Privacy and Security", color = primaryText, fontSize = (20f * fs).sp,
                fontWeight = FontWeight.W700, letterSpacing = (-0.3).sp,
            )
        }
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    Modifier.fillMaxWidth()
                        .background(accent.copy(alpha = if (isDark) 0.18f else 0.12f), RoundedCornerShape(16.dp))
                        .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Rounded.WarningAmber, null, tint = accent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Important alert: Dear user, If you encounter a problem with a delay in sending and receiving messages. All you have to do is click on the [Reset Privacy] option at the bottom.",
                        Modifier.weight(1f), color = primaryText, fontSize = (12.5f * fs).sp, lineHeight = (12.5f * fs * 1.35f).sp,
                    )
                }
                Spacer(Modifier.height(16.dp))
                CardContainer {
                    SectionDivider("Privacy")
                    SwitchRow(Icons.Rounded.AcUnit, "Freeze Last Seen", "Please restart Chaty for changes to take effect.", p.freezeLastSeen) {
                        preferences.updatePrivacy(p.copy(freezeLastSeen = it), logTitle = "Freeze Last Seen")
                    }
                    SwitchRow(Icons.Outlined.RemoveRedEye, "Anti-View Once", "Open 'view once' messages unlimited", p.antiViewOnce) {
                        preferences.updatePrivacy(p.copy(antiViewOnce = it), logTitle = "Anti-View Once")
                    }
                    SwitchRow(Icons.Rounded.ForwardToInbox, "Disable Forwarded", "Allows you to resend messages without Forwarded tag", p.disableForwardedLabel) {
                        preferences.updatePrivacy(p.copy(disableForwardedLabel = it), logTitle = "Disable Forwarded")
                    }
                    if (loadPresencePrivacy != null && presenceLoaded) {
                        ChevronRow(
                            Icons.Rounded.Visibility,
                            "Last seen & online",
                            presencePrivacy?.let { PrivacyOptionsLogic.audienceLabel(it.lastSeenPolicy) } ?: "Not available on this server",
                        ) {
                            if (presencePrivacy != null) modal = PrivacyModal.LastSeen else snack("Not available on this server")
                        }
                    }
                    ChevronRow(
                        Icons.Outlined.Call,
                        "Who can call me?",
                        serverCallPolicy?.let { PrivacyOptionsLogic.audienceLabel(it.policy) } ?: p.whoCanCallMe,
                    ) { modal = PrivacyModal.WhoCanCall }
                    if (loadBlocked != null && blockedUsers != null) {
                        ChevronRow(
                            Icons.Rounded.Block,
                            "Blocked contacts",
                            if (blockedUsers!!.isEmpty()) "None" else "${blockedUsers!!.size} blocked",
                        ) { modal = PrivacyModal.Blocked }
                    }
                    ChevronRow(Icons.Outlined.AdminPanelSettings, "Custom Privacy", "View all") { modal = PrivacyModal.Custom("Custom") }

                    SectionDivider("Specific Privacy")
                    SwitchRow(Icons.Outlined.Lock, "Hide the privacy option", "Hide the privacy option from the Home Screen Options menu.", p.hidePrivacyOption) {
                        preferences.updatePrivacy(p.copy(hidePrivacyOption = it), logTitle = "Hide the privacy option")
                    }
                    SwitchRow(Icons.Rounded.History, "Hide the update option", "Hide the Chaty Update option from the Home Screen Options menu.", p.hideUpdateOption) {
                        preferences.updatePrivacy(p.copy(hideUpdateOption = it), logTitle = "Hide the update option")
                    }

                    SectionDivider("Status")
                    SwitchRow(Icons.Rounded.TvOff, "Disable channels", "The channels will not be shown in the status screen.", p.disableChannels) {
                        preferences.updatePrivacy(p.copy(disableChannels = it), logTitle = "Disable channels")
                    }
                    SwitchRow(Icons.Rounded.VisibilityOff, "Hide View Status", "Do not tell contact that you have viewed their status", p.hideViewStatus) {
                        preferences.updatePrivacy(p.copy(hideViewStatus = it), logTitle = "Hide View Status")
                    }
                    SwitchRow(Icons.Outlined.DeleteForever, "Anti-Delete Status", "Deleted status/stories will not be deleted for you", p.antiDeleteStatus) {
                        preferences.updatePrivacy(p.copy(antiDeleteStatus = it), logTitle = "Anti-Delete Status")
                    }
                    SwitchRow(Icons.Outlined.NotificationImportant, "Status Revoke Alert", "Show an immediate notification when one of your contacts deletes their status/Story", p.statusRevocationAlert) {
                        preferences.updatePrivacy(p.copy(statusRevocationAlert = it), logTitle = "Status Revoke Alert")
                    }

                    SectionDivider("Chats")
                    SwitchRow(Icons.Outlined.MarkChatRead, "Hide the first message", "When this feature is enabled, the first message in hidden chats will not be shown, and you can only read it after opening the chat", p.hideFirstMessage) {
                        preferences.updatePrivacy(p.copy(hideFirstMessage = it), logTitle = "Hide the first message")
                    }
                    SwitchRow(Icons.Outlined.Timer, "Anti Disappearing Messages", "If Disappearing Messages is Enabled, Messages will not delete from your side", p.antiDisappearingMessages) {
                        preferences.updatePrivacy(p.copy(antiDisappearingMessages = it), logTitle = "Anti Disappearing Messages")
                    }
                    SwitchRow(Icons.Rounded.EditNote, "Show edited message", "Show people normal with messages for you", p.showEditedMessage) {
                        preferences.updatePrivacy(p.copy(showEditedMessage = it), logTitle = "Show edited message")
                    }
                    SwitchRow(Icons.Outlined.Delete, "Anti-Delete Messages", "Other people cannot delete messages for you", p.antiDeleteMessages) {
                        preferences.updatePrivacy(p.copy(antiDeleteMessages = it), logTitle = "Anti-Delete Messages")
                    }
                    SwitchRow(Icons.Outlined.NotificationsActive, "Message Revoke Alert", "Show a notification in the status bar when someone deletes a message sent to you", p.messageRevokeAlert) {
                        preferences.updatePrivacy(p.copy(messageRevokeAlert = it), logTitle = "Message Revoke Alert")
                    }
                    SwitchRow(Icons.Outlined.DeleteSweep, "Deletion of everyone", "Activates the possibility of deleting messages to everyone at any time.", p.deletionOfEveryone) {
                        preferences.updatePrivacy(p.copy(deletionOfEveryone = it), logTitle = "Deletion of everyone")
                    }
                    SwitchRow(Icons.Outlined.AccessTime, "Deleted media time", "Find out the exact moment a message or status was deleted", p.deletedMediaTime) {
                        preferences.updatePrivacy(p.copy(deletedMediaTime = it), logTitle = "Deleted media time")
                    }
                    SwitchRow(Icons.Outlined.MarkChatUnread, "Unviewed chats", "When enabled the message counter will not disappear when you open the chat", p.unviewedChats) {
                        preferences.updatePrivacy(p.copy(unviewedChats = it), logTitle = "Unviewed chats")
                    }
                    SwitchRow(Icons.Rounded.DoneAll, "Show Blue Ticks after reply", "Contact will only see blue ticks after you reply", p.showBlueTicksAfterReply) {
                        preferences.updatePrivacy(p.copy(showBlueTicksAfterReply = it), logTitle = "Show Blue Ticks after reply")
                    }
                    ChevronRow(Icons.Outlined.Contacts, "Contacts", "Change privacy settings") { modal = PrivacyModal.Custom("Contacts") }
                    ChevronRow(Icons.Outlined.Groups, "Groups", "Change privacy settings") { modal = PrivacyModal.Custom("Groups") }
                }
                Spacer(Modifier.height(16.dp))
                CardContainer {
                    ChevronRow(Icons.Outlined.HelpOutline, "Recovery Question", "Set secret question for password reset") { modal = PrivacyModal.Recovery }
                    ChevronRow(Icons.Rounded.RestartAlt, "Reset Privacy", "Sets back Chaty default Privacy") {
                        ChatyHapticsPort.error(view) // HapticFeedback.heavyImpact (CONTEXT_CLICK)
                        modal = PrivacyModal.Reset
                    }
                }
                Spacer(Modifier.height(36.dp))
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    when (val m = modal) {
        PrivacyModal.WhoCanCall -> if (loadCallPolicy != null && saveCallPolicy != null && serverCallPolicy != null) {
            ServerWhoCanCallMeDialog(
                current = serverCallPolicy!!,
                busy = serverBusy,
                onPick = { label ->
                    val policy = PrivacyOptionsLogic.audiencePolicy(label) ?: return@ServerWhoCanCallMeDialog
                    if (serverBusy) return@ServerWhoCanCallMeDialog
                    serverBusy = true
                    scope.launch {
                        try {
                            // exceptions omitted: the server keeps the existing list.
                            serverCallPolicy = saveCallPolicy(policy, null)
                            PrivacyOptionsLogic.audienceLabel(serverCallPolicy!!.policy)?.let { serverLabel ->
                                preferences.updatePrivacy(preferences.privacy.copy(whoCanCallMe = serverLabel), logTitle = "Who can call me")
                            }
                            modal = null
                        } catch (e: Exception) {
                            snack(e.message ?: "Could not save the call policy")
                        } finally {
                            serverBusy = false
                        }
                    }
                },
                onDismiss = { modal = null },
            )
        } else {
            WhoCanCallMeDialog(preferences) { modal = null }
        }
        PrivacyModal.LastSeen -> presencePrivacy?.let { pp ->
            LastSeenOnlineDialog(
                current = pp,
                busy = serverBusy,
                onSave = { lastSeenLabel, onlineLabel ->
                    val lsPolicy = PrivacyOptionsLogic.audiencePolicy(lastSeenLabel) ?: return@LastSeenOnlineDialog
                    val olPolicy = PrivacyOptionsLogic.onlinePolicy(onlineLabel) ?: return@LastSeenOnlineDialog
                    if (serverBusy || savePresencePrivacy == null) return@LastSeenOnlineDialog
                    serverBusy = true
                    scope.launch {
                        try {
                            // exceptions omitted: the server keeps the existing list.
                            presencePrivacy = savePresencePrivacy(lsPolicy, olPolicy, null)
                            modal = null
                        } catch (e: Exception) {
                            snack(e.message ?: "Could not save presence privacy")
                        } finally {
                            serverBusy = false
                        }
                    }
                },
                onDismiss = { modal = null },
            )
        }
        PrivacyModal.Blocked -> blockedUsers?.let { list ->
            BlockedContactsDialog(
                blocked = list,
                busy = serverBusy,
                onUnblock = { entry ->
                    if (serverBusy || unblockUser == null) return@BlockedContactsDialog
                    serverBusy = true
                    scope.launch {
                        try {
                            unblockUser(entry.userId)
                            blockedUsers = blockedUsers!!.filterNot { it.userId == entry.userId }
                            snack("${entry.displayName} unblocked")
                        } catch (e: Exception) {
                            snack(e.message ?: "Could not unblock")
                        } finally {
                            serverBusy = false
                        }
                    }
                },
                onDismiss = { modal = null },
            )
        }
        is PrivacyModal.Custom -> CustomPrivacyDialog(preferences, m.title) { modal = null }
        PrivacyModal.Recovery -> RecoveryQuestionDialog(
            onCancel = { modal = null },
            onSave = { modal = null; snack("Recovery question saved.") },
        )
        PrivacyModal.Reset -> AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { modal = null },
            title = { Text("Reset Privacy?") },
            text = { Text("This will reset all privacy settings back to WhatsApp default configuration.") },
            dismissButton = { TextButton(onClick = { modal = null }) { Text("Cancel") } },
            confirmButton = {
                Button(onClick = {
                    preferences.updatePrivacy(PrivacyPreferences(), logTitle = "Reset Privacy")
                    modal = null
                    snack("Privacy settings reset to defaults! \uD83D\uDD04")
                }) { Text("Reset") }
            },
        )
        null -> {}
    }
}

@Composable
private fun CustomPrivacyDialog(preferences: ChatyPreferencesController, title: String, onDone: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_EXPRESSION") tick
    @Composable
    fun Item(label: String, key: String) {
        val full = "${title}_$key"
        Row(
            Modifier.fillMaxWidth().clickable { preferences.updateGbFeature(full, !preferences.gbBool(full, false)); tick++ },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f).padding(start = 8.dp))
            Checkbox(checked = preferences.gbBool(full, false), onCheckedChange = { preferences.updateGbFeature(full, it); tick++ })
        }
    }
    AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
        onDismissRequest = onDone,
        title = { Text("$title Privacy Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Item("Hide blue ticks", "hide_blue_ticks")
                Item("Hide second tick", "hide_second_tick")
                Item("Hide blue microphone", "hide_blue_mic")
                if (title != "Broadcast") {
                    Item("Hide typing\u2026", "hide_typing")
                    Item("Hide recording\u2026", "hide_recording")
                }
            }
        },
        confirmButton = { Button(onClick = onDone) { Text("Done") } },
    )
}

@Composable
private fun RecoveryQuestionDialog(onCancel: () -> Unit, onSave: () -> Unit) {
    var q by remember { mutableStateOf("What is your favorite color?") }
    var a by remember { mutableStateOf("") }
    AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
        onDismissRequest = onCancel,
        title = { Text("Recovery Question") },
        text = {
            Column {
                Text("Set a security question to help recover access if you forget your credential.", fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(q, { q = it }, label = { Text("Security Question") })
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(a, { a = it }, label = { Text("Your Answer") })
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
        confirmButton = { Button(onClick = onSave) { Text("Save") } },
    )
}

/** Server-backed Who Can Call Me (research 5.3): same four WhatsApp options
 *  as the legacy dialog, saved to the contacts plane so calls are actually
 *  enforced server-side. Choosing "My Contacts Except…" keeps the existing
 *  exception list (exception editing comes with the contacts UI). */
@Composable
private fun ServerWhoCanCallMeDialog(
    current: com.example.chat.data.api.ApiContactsClient.CallPolicy,
    busy: Boolean,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val selected = PrivacyOptionsLogic.audienceLabel(current.policy)
    AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
        onDismissRequest = onDismiss,
        title = { Text("Who can call me?") },
        text = {
            Column {
                PrivacyOptionsLogic.audienceLabels.forEach { opt ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !busy) { onPick(opt) }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = opt == selected,
                            onClick = { if (!busy) onPick(opt) },
                            colors = RadioButtonDefaults.colors(selectedColor = colors.primary),
                        )
                        Text(opt, Modifier.padding(start = 8.dp))
                    }
                }
                if (selected == "My Contacts Except…") {
                    Text(
                        "Your existing exceptions stay in place.",
                        Modifier.padding(start = 16.dp, top = 6.dp),
                        color = colors.foregroundSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = {},
    )
}

/** Server-backed Last seen & online (research 5.2): WhatsApp's exact shape -
 *  four audiences for last seen, two for online. */
@Composable
private fun LastSeenOnlineDialog(
    current: com.example.chat.data.api.ApiPresenceClient.PresencePrivacy,
    busy: Boolean,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    var lastSeen by remember { mutableStateOf(PrivacyOptionsLogic.audienceLabel(current.lastSeenPolicy) ?: "My Contacts") }
    var online by remember { mutableStateOf(PrivacyOptionsLogic.onlineLabel(current.onlinePolicy) ?: "Same as last seen") }
    AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
        onDismissRequest = onDismiss,
        title = { Text("Last seen & online") },
        text = {
            Column {
                Text("Who can see my last seen", color = colors.foregroundSecondary, fontSize = 12.sp, fontWeight = FontWeight.W600)
                PrivacyOptionsLogic.audienceLabels.forEach { opt ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !busy) { lastSeen = opt }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = opt == lastSeen,
                            onClick = { if (!busy) lastSeen = opt },
                            colors = RadioButtonDefaults.colors(selectedColor = colors.primary),
                        )
                        Text(opt, Modifier.padding(start = 8.dp))
                    }
                }
                if (lastSeen == "My Contacts Except…") {
                    Text(
                        "Your existing exceptions stay in place.",
                        Modifier.padding(start = 16.dp, top = 2.dp),
                        color = colors.foregroundSecondary,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text("Who can see when I'm online", color = colors.foregroundSecondary, fontSize = 12.sp, fontWeight = FontWeight.W600)
                PrivacyOptionsLogic.onlineLabels.forEach { opt ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !busy) { online = opt }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = opt == online,
                            onClick = { if (!busy) online = opt },
                            colors = RadioButtonDefaults.colors(selectedColor = colors.primary),
                        )
                        Text(opt, Modifier.padding(start = 8.dp))
                    }
                }
                if (lastSeen == "Nobody") {
                    Text(
                        "You won't see other people's last seen either.",
                        Modifier.padding(start = 16.dp, top = 6.dp),
                        color = colors.foregroundSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = { TextButton(onClick = { if (!busy) onSave(lastSeen, online) }) { Text("Save") } },
    )
}

/** Server-backed blocked list (research 5.3): silent unblock, no leaks. */
@Composable
private fun BlockedContactsDialog(
    blocked: List<com.example.chat.data.api.ApiContactsClient.BlockedEntry>,
    busy: Boolean,
    onUnblock: (com.example.chat.data.api.ApiContactsClient.BlockedEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
        onDismissRequest = onDismiss,
        title = { Text("Blocked contacts") },
        text = {
            if (blocked.isEmpty()) {
                Text("No blocked contacts.", color = colors.foregroundSecondary)
            } else {
                Column {
                    blocked.forEach { entry ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.displayName, fontWeight = FontWeight.W600)
                                Text("@${entry.username}", color = colors.foregroundSecondary, fontSize = 12.sp)
                            }
                            TextButton(onClick = { if (!busy) onUnblock(entry) }) { Text("Unblock") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
