package com.example.chat.ui.settings.messagemanagement

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ReplyAll
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.data.services.GbFeatureBackendService
import com.example.chat.data.services.ServerScheduledMessage
import com.example.chat.domain.models.AutoReplyRule
import com.example.chat.domain.models.QuickReplyTemplate
import com.example.chat.domain.models.ScheduledMessageEntry
import com.example.chat.ui.settings.ChatySettingsPage
import com.example.chat.ui.settings.ChatySettingsSection
import com.example.chat.ui.settings.ChatySettingsTile
import com.example.chat.ui.settings.ChatySwitchTile
import com.example.chat.ui.theme.LocalAppColors
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.launch

/** Exact port of lib/features/settings/message_management/message_management_page.dart.
 *
 *  Seams:
 *  - [conversationSource]: Dart widget.dataStore.conversations (ChatyDataStore
 *    is not yet ported - data owner item). Supplies id+title pairs.
 *  - [showToast]: Dart ScaffoldMessenger snackbars.
 *  - [pickDate]/[pickTime]: Dart showDatePicker/showTimePicker; hosts wire
 *    the Material3 pickers (defaults provided via state dialogs below).
 *  - The Dart page's GbFeatureBackendService is constructed directly
 *    (`GbFeatureBackendService()`); here it is a parameter so the owner can
 *    inject the configured instance (the service itself is already ported
 *    verbatim on the branch). */

data class MessageManagementConversation(val id: String, val title: String)

interface MessageManagementConversationSource {
    val conversations: List<MessageManagementConversation>
}

/** Dart _formatDateTime: d/M/yyyy H:mm local. */
fun formatScheduledDateTime(value: ZonedDateTime): String {
    val local = value.withZoneSameInstant(ZoneId.systemDefault())
    return "${local.dayOfMonth}/${local.monthValue}/${local.year} ${local.hour}:${local.minute.toString().padStart(2, '0')}"
}

/** Dart: `if (rule.id.length == 36)` routes delete by id, otherwise by
 *  keyword+response signature. */
fun shouldDeleteRuleById(ruleId: String): Boolean = ruleId.length == 36

/** Dart quick-reply title fallback: `title.isEmpty ? shortcut : title`. */
fun quickReplyTitle(shortcut: String, title: String): String =
    if (title.isEmpty()) shortcut else title

private data class AutoReplyDraft(val keyword: String, val response: String, val scope: String)
private data class ScheduleDraft(val conversationId: String, val body: String)
private data class QuickReplyDraft(val shortcut: String, val title: String, val content: String)
private data class MassSendDraft(val ids: List<String>, val body: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageManagementPage(
    preferencesController: ChatyPreferencesController,
    server: GbFeatureBackendService,
    conversationSource: MessageManagementConversationSource,
    onBack: () -> Unit,
    showToast: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val colors = LocalAppColors.current

    // ListenableBuilder equivalent for the preferences controller.
    var prefsRevision by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.DisposableEffect(preferencesController) {
        val listener: () -> Unit = { prefsRevision++ }
        preferencesController.addListener(listener)
        onDispose { preferencesController.removeListener(listener) }
    }
    @Suppress("UNUSED_EXPRESSION") prefsRevision

    // Dart: _scheduledFuture refreshed via setState; _sync() on init.
    var refreshCounter by remember { mutableIntStateOf(0) }
    var scheduledState by remember {
        mutableStateOf<ScheduledLoadState>(ScheduledLoadState.Loading)
    }
    fun refreshScheduled() {
        refreshCounter++
        scope.launch {
            scheduledState = ScheduledLoadState.Loading
            scheduledState = try {
                ScheduledLoadState.Loaded(server.getScheduledMessages())
            } catch (e: Exception) {
                ScheduledLoadState.Failed(e)
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(server) {
        refreshScheduled()
        // Dart _sync(): best-effort, errors swallowed.
        try {
            server.synchronizeAutomation(preferencesController.automation)
            refreshScheduled()
        } catch (_: Exception) {
        }
    }

    var autoReplyDialogOpen by remember { mutableStateOf(false) }
    var scheduleDialogOpen by remember { mutableStateOf(false) }
    var quickReplyDialogOpen by remember { mutableStateOf(false) }
    var massSendDialogOpen by remember { mutableStateOf(false) }
    var datePickerOpen by remember { mutableStateOf(false) }
    var timePickerOpen by remember { mutableStateOf(false) }
    var pendingScheduleDraft by remember { mutableStateOf<ScheduleDraft?>(null) }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }

    fun addAutoReplyRule(payload: AutoReplyDraft) {
        scope.launch {
            try {
                val id = server.createAutoReplyRule(
                    keyword = payload.keyword,
                    response = payload.response,
                    scope = payload.scope,
                )
                val auto = preferencesController.automation
                val next = auto.autoReplyRules + AutoReplyRule(
                    id = id,
                    keyword = payload.keyword,
                    responseMessage = payload.response,
                    recipientFilter = payload.scope,
                )
                preferencesController.updateAutomation(
                    auto.copy(autoReplyRules = next),
                    logTitle = "Add server auto-reply rule",
                )
                showToast("Auto-reply rule is active on the server.")
            } catch (error: Exception) {
                showToast("Unable to add rule: $error")
            }
        }
    }

    fun deleteRule(rule: AutoReplyRule) {
        scope.launch {
            try {
                if (shouldDeleteRuleById(rule.id)) {
                    server.deleteAutoReplyRule(rule.id)
                } else {
                    server.deleteAutoReplyRuleBySignature(rule.keyword, rule.responseMessage)
                }
                val auto = preferencesController.automation
                preferencesController.updateAutomation(
                    auto.copy(autoReplyRules = auto.autoReplyRules.filter { it.id != rule.id }),
                    logTitle = "Delete server auto-reply rule",
                )
            } catch (error: Exception) {
                showToast("Unable to delete rule: $error")
            }
        }
    }

    fun scheduleMessage(payload: ScheduleDraft, scheduledAt: ZonedDateTime) {
        scope.launch {
            try {
                val id = server.scheduleMessage(
                    conversationId = payload.conversationId,
                    body = payload.body,
                    scheduledAt = scheduledAt.toInstant(),
                )
                val conversation = conversationSource.conversations
                    .first { it.id == payload.conversationId }
                val auto = preferencesController.automation
                val next = auto.scheduledMessages + ScheduledMessageEntry(
                    id = id,
                    recipientId = conversation.id,
                    recipientName = conversation.title,
                    text = payload.body,
                    scheduledAtMillis = scheduledAt.toInstant().toEpochMilli(),
                )
                preferencesController.updateAutomation(
                    auto.copy(scheduledMessages = next),
                    logTitle = "Schedule server message",
                )
                refreshScheduled()
                showToast("Scheduled on the server for ${formatScheduledDateTime(scheduledAt)}.")
            } catch (error: Exception) {
                showToast("Unable to schedule message: $error")
            }
        }
    }

    fun cancelScheduled(item: ServerScheduledMessage) {
        scope.launch {
            try {
                server.cancelScheduledMessage(item.id)
                refreshScheduled()
            } catch (error: Exception) {
                showToast("Unable to cancel: $error")
            }
        }
    }

    fun addQuickReply(template: QuickReplyTemplate) {
        scope.launch {
            try {
                server.upsertQuickReply(
                    shortcut = template.shortcut,
                    title = template.title,
                    content = template.content,
                )
                val auto = preferencesController.automation
                val next = auto.quickReplies
                    .filter { it.shortcut != template.shortcut } + template
                preferencesController.updateAutomation(
                    auto.copy(quickReplies = next),
                    logTitle = "Save server quick reply",
                )
            } catch (error: Exception) {
                showToast("Unable to save quick reply: $error")
            }
        }
    }

    fun massSend(result: MassSendDraft) {
        scope.launch {
            try {
                val sent = server.massSend(
                    conversationIds = result.ids,
                    body = result.body,
                )
                showToast("Message sent to $sent conversation${if (sent == 1) "" else "s"}.")
            } catch (error: Exception) {
                showToast("Mass send failed: $error")
            }
        }
    }

    val auto = preferencesController.automation
    ChatySettingsPage(
        title = "Message Management",
        subtitle = "Server Auto-Reply, Scheduler, Mass Sender & Quick Replies",
        onBack = onBack,
    ) {
        ChatySettingsSection(
            title = "Auto-reply automation",
            description = "Rules execute in Supabase after matching incoming messages, even when the app is backgrounded.",
        ) {
            ChatySwitchTile(
                icon = Icons.Outlined.ReplyAll,
                iconColor = colors.primary,
                title = "Enable auto-reply engine",
                subtitle = "Account-level master switch",
                value = auto.enableAutoReply,
                onChanged = { value ->
                    preferencesController.updateAutomation(
                        auto.copy(enableAutoReply = value),
                        logTitle = "Enable server auto reply",
                    )
                    scope.launch {
                        try {
                            server.synchronizeAutomation(preferencesController.automation)
                            refreshScheduled()
                        } catch (_: Exception) {
                        }
                    }
                },
            )
            auto.autoReplyRules.forEach { rule ->
                ChatySettingsTile(
                    icon = Icons.Outlined.Subtitles,
                    title = "Trigger: \"${rule.keyword}\"",
                    subtitle = "Reply: ${rule.responseMessage}",
                    badgeText = if (rule.enabled) "ACTIVE" else "OFF",
                    badgeColor = if (rule.enabled) colors.success else colors.foregroundTertiary,
                    trailing = {
                        IconButton(onClick = { deleteRule(rule) }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = colors.error)
                        }
                    },
                )
            }
            ChatySettingsTile(
                icon = Icons.Outlined.AddCircleOutline,
                title = "Add server auto-reply rule",
                onTap = { autoReplyDialogOpen = true },
            )
        }

        ChatySettingsSection(
            title = "Message scheduler",
            description = "Scheduled messages are persisted and processed every minute on the server.",
        ) {
            @Suppress("UNUSED_EXPRESSION") refreshCounter
            when (val state = scheduledState) {
                is ScheduledLoadState.Loading -> {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text("Loading scheduled messages…", modifier = Modifier.weight(1f), color = colors.foreground)
                        CircularProgressIndicator(color = colors.primary)
                    }
                }
                is ScheduledLoadState.Failed -> {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("Unable to load scheduler", color = colors.foreground)
                        Text("${state.error}", color = colors.foregroundSecondary, fontSize = 12.5.sp)
                    }
                }
                is ScheduledLoadState.Loaded -> {
                    if (state.items.isEmpty()) {
                        Text(
                            "No scheduled messages",
                            modifier = Modifier.padding(14.dp),
                            color = colors.foreground,
                        )
                    } else {
                        state.items.forEach { entry ->
                            val conv = conversationSource.conversations
                                .firstOrNull { it.id == entry.conversationId }
                            ChatySettingsTile(
                                icon = Icons.Rounded.Schedule,
                                title = conv?.title ?: "Conversation",
                                subtitle = "${entry.body}\n${formatScheduledDateTime(entry.scheduledAt)}${if (entry.lastError == null) "" else " • ${entry.lastError}"}",
                                badgeText = entry.state.uppercase(),
                                badgeColor = when (entry.state) {
                                    "sent" -> colors.success
                                    "failed" -> colors.error
                                    else -> colors.warning
                                },
                                trailing = if (entry.state == "pending") {
                                    {
                                        IconButton(onClick = { cancelScheduled(entry) }) {
                                            Icon(Icons.Rounded.Close, contentDescription = null)
                                        }
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
            ChatySettingsTile(
                icon = Icons.Rounded.AlarmAdd,
                title = "Schedule new message",
                subtitle = "Choose conversation, date and time",
                onTap = {
                    if (conversationSource.conversations.isEmpty()) {
                        showToast("Start a conversation before scheduling a message.")
                    } else {
                        scheduleDialogOpen = true
                    }
                },
            )
        }

        ChatySettingsSection(
            title = "Mass sender",
            description = "Send one message to selected conversations. Server-side membership and configured forwarding limits are enforced.",
        ) {
            ChatySettingsTile(
                icon = Icons.Rounded.Send,
                title = "Open mass sender",
                subtitle = "Select multiple conversations",
                onTap = {
                    if (conversationSource.conversations.isNotEmpty()) {
                        massSendDialogOpen = true
                    }
                },
            )
        }

        ChatySettingsSection(
            title = "Quick replies",
            description = "Templates synchronize with your Chaty account and remain available across sessions.",
        ) {
            auto.quickReplies.forEach { template ->
                ChatySettingsTile(
                    icon = Icons.Rounded.Bolt,
                    title = "${template.shortcut} (${template.title})",
                    subtitle = template.content,
                )
            }
            ChatySettingsTile(
                icon = Icons.Rounded.Add,
                title = "Add quick reply template",
                onTap = { quickReplyDialogOpen = true },
            )
        }
    }

    // ---- Dart AlertDialogs ----

    if (autoReplyDialogOpen) {
        var keyword by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }
        var scopeChoice by remember { mutableStateOf("all") }
        AlertDialog(
            onDismissRequest = { autoReplyDialogOpen = false },
            title = { Text("Add auto-reply rule") },
            text = {
                Column {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = { Text("Keyword trigger") },
                        placeholder = { Text("busy, help, pricing") },
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Automatic response") },
                        maxLines = 3,
                    )
                    Spacer(Modifier.height(12.dp))
                    ScopeDropdown(
                        value = scopeChoice,
                        onChange = { scopeChoice = it },
                        options = listOf(
                            "all" to "All conversations",
                            "direct" to "Direct chats only",
                            "group" to "Groups only",
                        ),
                        label = "Scope",
                    )
                }
            },
            confirmButton = {
                FilledButton(onClick = {
                    val k = keyword.trim()
                    val r = message.trim()
                    if (k.isNotEmpty() && r.isNotEmpty()) {
                        autoReplyDialogOpen = false
                        addAutoReplyRule(AutoReplyDraft(k, r, scopeChoice))
                    }
                }) { Text("Add rule") }
            },
            dismissButton = {
                TextButton(onClick = { autoReplyDialogOpen = false }) { Text("Cancel") }
            },
        )
    }

    if (scheduleDialogOpen) {
        var conversationId by remember {
            mutableStateOf(conversationSource.conversations.first().id)
        }
        var body by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { scheduleDialogOpen = false },
            title = { Text("Schedule message") },
            text = {
                Column {
                    ScopeDropdown(
                        value = conversationId,
                        onChange = { conversationId = it },
                        options = conversationSource.conversations.map { it.id to it.title },
                        label = "Conversation",
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Message") },
                        maxLines = 4,
                    )
                }
            },
            confirmButton = {
                FilledButton(onClick = {
                    val b = body.trim()
                    if (b.isNotEmpty()) {
                        scheduleDialogOpen = false
                        pendingScheduleDraft = ScheduleDraft(conversationId, b)
                        datePickerOpen = true
                    }
                }) { Text("Choose time") }
            },
            dismissButton = {
                TextButton(onClick = { scheduleDialogOpen = false }) { Text("Cancel") }
            },
        )
    }

    // Dart: showDatePicker(firstDate: now, lastDate: now+365d,
    // initialDate: now+5min) then showTimePicker(initialTime: initial).
    if (datePickerOpen) {
        val initialMillis = System.currentTimeMillis() + 5 * 60 * 1000
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = futureDatesOnly,
        )
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = dateState.selectedDateMillis
                    if (millis != null) {
                        datePickerOpen = false
                        pendingDateMillis = millis
                        timePickerOpen = true
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerOpen = false }) { Text("Cancel") }
            },
        ) {
            androidx.compose.material3.DatePicker(state = dateState)
        }
    }

    if (timePickerOpen) {
        val draft = pendingScheduleDraft
        val dateMillis = pendingDateMillis
        if (draft != null && dateMillis != null) {
            TimePickerDialogCompat(
                onCancel = { timePickerOpen = false },
                onConfirm = { hour, minute ->
                    timePickerOpen = false
                    val zone = ZoneId.systemDefault()
                    val date = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                    val scheduledAt = ZonedDateTime.of(date.year, date.monthValue, date.dayOfMonth, hour, minute, 0, 0, zone)
                    // Dart: if (!scheduledAt.isAfter(DateTime.now())) toast.
                    if (!scheduledAt.toInstant().isAfter(Instant.now())) {
                        showToast("Choose a future date and time.")
                    } else {
                        scheduleMessage(draft, scheduledAt)
                    }
                },
            )
        } else {
            timePickerOpen = false
        }
    }

    if (quickReplyDialogOpen) {
        var shortcut by remember { mutableStateOf("#") }
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { quickReplyDialogOpen = false },
            title = { Text("Add quick reply") },
            text = {
                Column {
                    OutlinedTextField(
                        value = shortcut,
                        onValueChange = { shortcut = it },
                        label = { Text("Shortcut") },
                        placeholder = { Text("#thanks") },
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Template content") },
                        maxLines = 3,
                    )
                }
            },
            confirmButton = {
                FilledButton(onClick = {
                    val sc = shortcut.trim()
                    val ct = content.trim()
                    if (sc.isNotEmpty() && ct.isNotEmpty()) {
                        quickReplyDialogOpen = false
                        addQuickReply(
                            QuickReplyTemplate(
                                shortcut = sc,
                                title = quickReplyTitle(sc, title.trim()),
                                content = ct,
                            ),
                        )
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { quickReplyDialogOpen = false }) { Text("Cancel") }
            },
        )
    }

    if (massSendDialogOpen) {
        var body by remember { mutableStateOf("") }
        val selected = remember { mutableStateOf(setOf<String>()) }
        AlertDialog(
            onDismissRequest = { massSendDialogOpen = false },
            title = { Text("Mass sender") },
            text = {
                Column {
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Message") },
                        maxLines = 3,
                    )
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        conversationSource.conversations.forEach { conversation ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = selected.value.contains(conversation.id),
                                    onCheckedChange = { checked ->
                                        selected.value = if (checked == true) {
                                            selected.value + conversation.id
                                        } else {
                                            selected.value - conversation.id
                                        }
                                    },
                                )
                                Text(conversation.title, color = colors.foreground)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                FilledButton(onClick = {
                    val b = body.trim()
                    if (selected.value.isNotEmpty() && b.isNotEmpty()) {
                        massSendDialogOpen = false
                        massSend(MassSendDraft(selected.value.toList(), b))
                    }
                }) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = { massSendDialogOpen = false }) { Text("Cancel") }
            },
        )
    }
}

private sealed interface ScheduledLoadState {
    data object Loading : ScheduledLoadState
    data class Failed(val error: Throwable) : ScheduledLoadState
    data class Loaded(val items: List<ServerScheduledMessage>) : ScheduledLoadState
}

/** Dart firstDate: DateTime.now() for the date picker. */
@OptIn(ExperimentalMaterial3Api::class)
private val futureDatesOnly = object : androidx.compose.material3.SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val todayStart = ZonedDateTime.now().toLocalDate()
            .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        return utcTimeMillis >= todayStart
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopeDropdown(
    value: String,
    onChange: (String) -> Unit,
    options: List<Pair<String, String>>,
    label: String,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == value }?.second ?: value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        expanded = false
                        onChange(id)
                    },
                )
            }
        }
    }
}

@Composable
private fun TimePickerDialogCompat(
    onCancel: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    // Dart showTimePicker(initialTime: now+5min).
    val nowPlus5 = ZonedDateTime.now().plusMinutes(5)
    val state = androidx.compose.material3.rememberTimePickerState(
        initialHour = nowPlus5.hour,
        initialMinute = nowPlus5.minute,
    )
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Select time") },
        text = { androidx.compose.material3.TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
