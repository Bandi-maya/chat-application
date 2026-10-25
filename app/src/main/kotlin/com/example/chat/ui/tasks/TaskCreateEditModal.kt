package com.example.chat.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.domain.models.ChatTask
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.TaskPriority
import com.example.chat.domain.models.UserProfile
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

/** Exact port of task_create_edit_modal.dart: New/Edit Task sheet with title
 *  + description inputs, 'Assign To' participant chips + Me chip, Low/Med/
 *  High/Urgent segmented priority, Today 6 PM / Tomorrow / Next Week quick
 *  chips + date picker, exact validation and error text, exact success
 *  snackbar strings, and the 23514 error mapping.
 *
 *  On the api (custom REST backend) branch, ChatyDataStore.createTaskAsync /
 *  updateTaskAsync delegate to ApiBackendService.createTask/updateTask, so
 *  this modal persists through [TasksController]; labels are a Supabase-only
 *  concept on this plane and are NOT sent (Dart REST path drops them). */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TaskCreateEditModal(
    controller: TasksController,
    colors: TaskColors,
    conversations: List<Conversation>,
    currentUser: UserProfile,
    getUser: (String) -> UserProfile?,
    sourceConversationId: String,
    existingTask: ChatTask? = null,
    initialTitle: String? = null,
    onDismiss: () -> Unit,
    onSaved: (message: String) -> Unit,
    onError: (message: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(existingTask?.title ?: initialTitle ?: "") }
    var description by remember { mutableStateOf(existingTask?.description ?: "") }
    var selectedAssigneeIds by remember {
        mutableStateOf(
            existingTask?.assigneeIds ?: run {
                val me = currentUser.id
                val conversation = conversations.firstOrNull { it.id == sourceConversationId }
                val other = conversation?.participantIds?.firstOrNull { it != me }
                listOf(if (!other.isNullOrEmpty()) other else me)
            },
        )
    }
    var priority by remember { mutableStateOf(existingTask?.priority ?: TaskPriority.medium) }
    var dueDate by remember {
        mutableStateOf(
            existingTask?.dueAt ?: Instant.now().plus(java.time.Duration.ofDays(3)),
        )
    }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val conversation = conversations.firstOrNull { it.id == sourceConversationId }
    val candidateAssignees = (conversation?.participantIds ?: emptyList())
        .filter { it != currentUser.id }
        .mapNotNull(getUser)

    fun setAssignee(userId: String, selected: Boolean) {
        selectedAssigneeIds = if (selected) {
            if (userId in selectedAssigneeIds) selectedAssigneeIds else selectedAssigneeIds + userId
        } else {
            selectedAssigneeIds - userId
        }
    }

    fun save() {
        if (isSaving) return
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) {
            errorMessage = "Task title is required."
            return
        }
        if (selectedAssigneeIds.isEmpty()) {
            errorMessage = "Select at least one assignee."
            return
        }
        isSaving = true
        errorMessage = null
        scope.launch {
            try {
                if (existingTask != null) {
                    controller.updateTask(
                        taskId = existingTask.id,
                        title = trimmedTitle,
                        description = description.trim(),
                        assigneeIds = selectedAssigneeIds,
                        priority = priority.name,
                        dueAtIso = dueDate.toString(),
                    )
                } else {
                    controller.createTask(
                        title = trimmedTitle,
                        description = description.trim(),
                        assigneeIds = selectedAssigneeIds,
                        priority = priority.name,
                        dueAtIso = dueDate.toString(),
                    )
                }
                onDismiss()
                onSaved(
                    if (existingTask == null) "Task \"$trimmedTitle\" created and shared in chat."
                    else "Task \"$trimmedTitle\" updated.",
                )
            } catch (error: Exception) {
                isSaving = false
                val message = (error.message ?: error.toString()).removePrefix("Exception: ")
                errorMessage = if (message.contains("23514")) {
                    "Task status could not be saved. Please retry after refreshing the chat."
                } else {
                    message
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Column(
            modifier = Modifier
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 38.dp, height = 4.5.dp)
                    .align(Alignment.CenterHorizontally)
                    .background(colors.foregroundTertiary, RoundedCornerShape(999.dp)),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (existingTask != null) "Edit Task" else "New Task",
                    color = colors.foreground,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                IconButton(onClick = { if (!isSaving) onDismiss() }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.foregroundSecondary)
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                enabled = !isSaving,
                placeholder = { Text("Task Title") },
                leadingIcon = { Icon(Icons.Rounded.CheckBox, contentDescription = null, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                enabled = !isSaving,
                placeholder = { Text("Description & instructions (optional)") },
                leadingIcon = { Icon(Icons.Rounded.Notes, contentDescription = null, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Spacer(Modifier.height(16.dp))
            Text("Assign To", color = colors.foreground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Select who is responsible for this task:", color = colors.foregroundSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (candidate in candidateAssignees) {
                    val selected = candidate.id in selectedAssigneeIds
                    FilterChip(
                        selected = selected,
                        enabled = !isSaving,
                        onClick = { setAssignee(candidate.id, !selected) },
                        leadingIcon = {
                            Icon(
                                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.PersonOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        label = { Text(candidate.displayName.split(" ").first()) },
                    )
                }
                FilterChip(
                    selected = currentUser.id in selectedAssigneeIds,
                    enabled = !isSaving,
                    onClick = { setAssignee(currentUser.id, currentUser.id !in selectedAssigneeIds) },
                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Me (${currentUser.displayName.split(" ").first()})") },
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("Priority", color = colors.foreground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val priorities = listOf(
                    TaskPriority.low to "Low",
                    TaskPriority.medium to "Med",
                    TaskPriority.high to "High",
                    TaskPriority.urgent to "Urgent",
                )
                priorities.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = priority == value,
                        enabled = !isSaving,
                        onClick = { priority = value },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = priorities.size),
                    ) { Text(label) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Due Date", color = colors.foreground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    enabled = !isSaving,
                    onClick = {
                        val now = java.time.ZonedDateTime.now()
                        dueDate = now.withHour(18).withMinute(0).withSecond(0).withNano(0).toInstant()
                    },
                ) { Text("Today 6 PM", fontSize = 12.sp) }
                TextButton(
                    enabled = !isSaving,
                    onClick = {
                        val t = java.time.ZonedDateTime.now().plusDays(1)
                        dueDate = t.withHour(18).withMinute(0).withSecond(0).withNano(0).toInstant()
                    },
                ) { Text("Tomorrow", fontSize = 12.sp) }
                TextButton(
                    enabled = !isSaving,
                    onClick = {
                        val t = java.time.ZonedDateTime.now().plusDays(7)
                        dueDate = t.withHour(18).withMinute(0).withSecond(0).withNano(0).toInstant()
                    },
                ) { Text("Next Week", fontSize = 12.sp) }
            }
            Spacer(Modifier.height(4.dp))
            val zone = ZoneId.systemDefault()
            val due = dueDate.atZone(zone)
            OutlinedButton(
                onClick = { if (!isSaving) showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Due ${due.dayOfMonth.toString().padStart(2, '0')}/" +
                        "${due.monthValue.toString().padStart(2, '0')}/${due.year} at " +
                        "${due.hour.toString().padStart(2, '0')}:${due.minute.toString().padStart(2, '0')}",
                )
            }
            errorMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = colors.error, fontSize = 13.sp)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = ::save,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (existingTask == null) "Create & Share in Chat" else "Save Changes")
                }
            }
        }
    }

    if (showDatePicker) {
        // Dart showDatePicker: firstDate now, lastDate now + 3650 days.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dueDate.toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis >= java.time.LocalDate.now(ZoneId.systemDefault())
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() &&
                        utcTimeMillis <= Instant.now().plus(java.time.Duration.ofDays(3650)).toEpochMilli()
            },
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val selected = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
                            val current = dueDate.atZone(ZoneId.systemDefault())
                            dueDate = selected
                                .withHour(current.hour).withMinute(current.minute)
                                .withSecond(0).withNano(0)
                                .toInstant()
                        }
                        showDatePicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
