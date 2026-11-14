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
import androidx.compose.material.icons.rounded.ChevronLeft
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
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleMessageSheet(
    conversations: List<Conversation>,
    initialConversationId: String? = null,
    initialBody: String = "",
    confirmEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (conversationId: String, body: String, scheduledAt: ZonedDateTime) -> Unit,
) {
    if (conversations.isEmpty()) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedConversation by remember { mutableStateOf(conversations.firstOrNull { it.id == initialConversationId } ?: conversations.first()) }
    var body by remember { mutableStateOf(initialBody) }
    var selectedDateTime by remember {
        mutableStateOf(ZonedDateTime.now().plusHours(1))
    }
    var attemptedRequest by remember { mutableStateOf<Pair<String, java.time.Instant>?>(null) }
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
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.scheduleSend,
                    semanticLabel = null,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Schedule Message",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.close, semanticLabel = "Close")
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
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight, semanticLabel = "Pick date & time")
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                enabled = confirmEnabled && body.isNotBlank() && (selectedDateTime.isAfter(ZonedDateTime.now()) ||
                    attemptedRequest == (body.trim() to selectedDateTime.toInstant())),
                onClick = {
                    if (body.trim().isNotEmpty()) {
                        attemptedRequest = body.trim() to selectedDateTime.toInstant()
                        onConfirm(selectedConversation.id, body.trim(), selectedDateTime)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.scheduleSend, semanticLabel = null)
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
