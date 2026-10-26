package com.example.chat.ui.automation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ScheduleSend
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import com.example.chat.ui.kit.ChatyDropdownMenuItem as DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** Exact port of lib/features/automation/scheduled_messages_screen.dart.
 *
 *  Seams:
 *  - ChatyDataStore.conversations -> [conversationsProvider]; wired by the
 *    integration layer to the shared conversation list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledMessagesScreen(
    service: ScheduledMessageService,
    conversationsProvider: () -> List<Conversation>,
    onBack: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // AnimatedBuilder port: recompose whenever the service notifies.
    var listenTick by remember { mutableIntStateOf(0) }
    DisposableEffect(service) {
        val listener: () -> Unit = { listenTick++ }
        service.addListener(listener)
        onDispose { service.removeListener(listener) }
    }
    @Suppress("UNUSED_EXPRESSION") listenTick

    var showSchedule by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { service.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Message Scheduler") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.Close, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { service.refresh() } }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val conversations = conversationsProvider()
                    if (conversations.isEmpty()) {
                        scope.launch {
                            snackbarHostState.showSnackbar("No active conversations found to schedule for.")
                        }
                    } else {
                        showSchedule = true
                    }
                },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Schedule") },
            )
        },
    ) { padding ->
        if (service.isLoading && service.messages.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        } else if (service.messages.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Rounded.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "No scheduled messages",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap + Schedule to queue messages for automatic delivery",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        } else {
            val conversations = conversationsProvider()
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(service.messages, key = { it.id }) { msg ->
                    val conversation = conversations.firstOrNull { it.id == msg.conversationId }
                    val convTitle = if (conversation != null) {
                        conversation.title.ifEmpty { "Conversation" }
                    } else {
                        val short = msg.conversationId.take(8)
                        "Chat ($short)"
                    }
                    val isPending = msg.state == "pending"

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.ChatBubbleOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    convTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                val chipColor = when {
                                    isPending -> Color(0xFFFFC107)
                                    msg.state == "completed" -> Color(0xFF4CAF50)
                                    else -> Color(0xFFF44336)
                                }
                                Surface(
                                    color = chipColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(
                                        msg.state.uppercase(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = chipColor.copy(alpha = 1f).darker(),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(msg.body, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.AccessTime,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Delivery: ${msg.scheduledAt.withZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                                Spacer(Modifier.weight(1f))
                                if (isPending) {
                                    TextButton(
                                        onClick = { scope.launch { service.cancelScheduledMessage(msg.id) } },
                                    ) {
                                        Icon(
                                            Icons.Rounded.Cancel,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Cancel", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSchedule) {
        ScheduleMessageSheet(
            conversations = conversationsProvider(),
            onDismiss = { showSchedule = false },
            onConfirm = { conversationId, body, scheduledAt ->
                showSchedule = false
                scope.launch {
                    try {
                        service.scheduleMessage(
                            conversationId = conversationId,
                            body = body,
                            scheduledAt = scheduledAt,
                        )
                        snackbarHostState.showSnackbar("Message scheduled successfully!")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Failed to schedule: $e")
                    }
                }
            },
        )
    }
}

private fun Color.darker(): Color = Color(
    red = red * 0.6f,
    green = green * 0.6f,
    blue = blue * 0.6f,
    alpha = alpha,
)

/** Port of the schedule-message modal bottom sheet from scheduled_messages_screen.dart. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleMessageSheet(
    conversations: List<Conversation>,
    onDismiss: () -> Unit,
    onConfirm: (conversationId: String, body: String, scheduledAt: ZonedDateTime) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedConversation by remember { mutableStateOf(conversations.first()) }
    var body by remember { mutableStateOf("") }
    var selectedDateTime by remember {
        mutableStateOf(ZonedDateTime.now().plusHours(1))
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(dragHandle = { com.example.chat.ui.kit.ChatySheetGrabber() }, tonalElevation = 0.dp, containerColor = com.example.chat.ui.theme.LocalAppColors.current.surfaceElevated, shape = com.example.chat.ui.kit.ChatyModalShape,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.ScheduleSend,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Schedule Message",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }
            Spacer(Modifier.height(16.dp))

            var convExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = convExpanded,
                onExpandedChange = { convExpanded = it },
            ) {
                OutlinedTextField(
                    value = conversationDisplayTitle(selectedConversation),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Recipient / Chat") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = convExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                )
                ExposedDropdownMenu(containerColor = com.example.chat.ui.theme.LocalAppColors.current.surfaceElevated, tonalElevation = 0.dp, shape = com.example.chat.ui.kit.ChatyModalShape,
                    expanded = convExpanded,
                    onDismissRequest = { convExpanded = false },
                ) {
                    conversations.forEach { conversation ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    conversationDisplayTitle(conversation),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            onClick = {
                                selectedConversation = conversation
                                convExpanded = false
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Message Body") },
                placeholder = { Text("Enter scheduled message...") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.CalendarToday, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Schedule Date & Time")
                    Text(
                        selectedDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd 'at' HH:mm")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Pick date & time")
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (body.trim().isNotEmpty()) {
                        onConfirm(selectedConversation.id, body.trim(), selectedDateTime)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.ScheduleSend, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Confirm Schedule")
            }
        }
    }

    if (showDatePicker) {
        // Dart: firstDate = now, lastDate = now + 365 days.
        val nowMillis = System.currentTimeMillis()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateTime.toInstant().toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis >= nowMillis - 86_400_000L &&
                        utcTimeMillis <= nowMillis + 365L * 86_400_000L
                }
            },
        )
        DatePickerDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
                        selectedDateTime = selectedDateTime
                            .withYear(picked.year)
                            .withMonth(picked.monthValue)
                            .withDayOfMonth(picked.dayOfMonth)
                        showDatePicker = false
                        showTimePicker = true
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedDateTime.hour,
            initialMinute = selectedDateTime.minute,
        )
        com.example.chat.ui.kit.ChatyConfirmationDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedDateTime = selectedDateTime
                        .withHour(timePickerState.hour)
                        .withMinute(timePickerState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = timePickerState) },
        )
    }
}

/** Port of the Dart dropdown label logic. */
private fun conversationDisplayTitle(c: Conversation): String =
    if (c.type == ConversationType.direct) {
        c.title.ifEmpty { "Direct Chat" }
    } else {
        c.title.ifEmpty { "Group Chat" }
    }
