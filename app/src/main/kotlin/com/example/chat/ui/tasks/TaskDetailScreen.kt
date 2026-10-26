package com.example.chat.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Button
import com.example.chat.ui.kit.ChatyDropdownMenu as DropdownMenu
import com.example.chat.ui.kit.ChatyDropdownMenuItem as DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.domain.models.ChatTask
import com.example.chat.domain.models.TaskStatus
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.auth.AuthBackButton
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

/** Exact port of task_detail_screen.dart: header card with the status
 *  dropdown + priority badge + Mark Completed/Reopen + edit, the Task
 *  Properties grouped section, and the Activity Timeline section.
 *
 *  Custom-backend honesty: the Dart screen's Activity Timeline reads the
 *  Supabase `task_activity` table directly; the custom REST backend exposes
 *  no task-activity endpoint, so on this plane the Dart screen falls into
 *  its empty state. This port renders the same section with the same empty
 *  state ('No changes recorded yet.') instead of fabricating activity or
 *  expanding Supabase usage. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    task: ChatTask,
    controller: TasksController,
    colors: TaskColors,
    currentUser: UserProfile,
    getUser: (String) -> UserProfile?,
    onBack: () -> Unit,
    onEditTask: (ChatTask) -> Unit,
) {
    var currentTask by remember(task.id) { mutableStateOf(task) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun userOf(id: String): UserProfile? =
        if (id == currentUser.id) currentUser else getUser(id)

    fun updateStatus(newStatus: TaskStatus) {
        if (currentTask.status == newStatus) return
        // Dart applies the local copy first, then persists.
        currentTask = currentTask.copy(status = newStatus, updatedAt = Instant.now())
        scope.launch {
            runCatching { controller.updateTaskStatus(currentTask.id, newStatus) }
        }
    }

    fun formatDate(value: Instant): String {
        val d = value.atZone(ZoneId.systemDefault())
        return "${d.dayOfMonth.toString().padStart(2, '0')}/" +
            "${d.monthValue.toString().padStart(2, '0')}/${d.year} " +
            "${d.hour.toString().padStart(2, '0')}:${d.minute.toString().padStart(2, '0')}"
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("Task Details") },
                navigationIcon = { AuthBackButton(onClick = onBack) },
                actions = {
                    IconButton(onClick = { onEditTask(currentTask) }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit Task")
                    }
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(shape = com.example.chat.ui.kit.ChatyModalShape, expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Edit Task") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                onClick = { menuOpen = false; onEditTask(currentTask) },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Task", color = Color(0xFFF44336)) },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Color(0xFFF44336), modifier = Modifier.size(20.dp)) },
                                onClick = { menuOpen = false; confirmDelete = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // Header card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surfaceElevated)
                    .padding(20.dp),
            ) {
                Row {
                    var statusMenuOpen by remember { mutableStateOf(false) }
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors.primary.copy(alpha = 0.08f))
                            .border(1.dp, colors.primary.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
                                .clickable { statusMenuOpen = true }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            TaskStatusBadge(currentTask.status, colors)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Change Status", tint = colors.primary, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(shape = com.example.chat.ui.kit.ChatyModalShape, expanded = statusMenuOpen, onDismissRequest = { statusMenuOpen = false }) {
                            for (s in TaskStatus.entries) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TaskStatusBadge(s, colors)
                                            Spacer(Modifier.weight(1f))
                                            if (s == currentTask.status) {
                                                Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    },
                                    onClick = { statusMenuOpen = false; updateStatus(s) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    TaskPriorityBadge(currentTask.priority, colors)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    currentTask.title,
                    color = colors.foreground,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (currentTask.description.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        currentTask.description,
                        color = colors.foreground.copy(alpha = 0.75f),
                        fontSize = 14.5.sp,
                        lineHeight = 21.sp,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row {
                    Button(
                        onClick = {
                            updateStatus(
                                if (currentTask.status == TaskStatus.completed) TaskStatus.inbox
                                else TaskStatus.completed,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            if (currentTask.status == TaskStatus.completed) Icons.Rounded.Replay
                            else Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (currentTask.status == TaskStatus.completed) "Reopen Task" else "Mark Completed")
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { onEditTask(currentTask) }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit Task")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Task Properties
            TaskSectionTitle("Task Properties", colors)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surfaceElevated),
            ) {
                TaskPropertyRow(
                    icon = { Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                    title = "Task ID",
                    colors = colors,
                ) {
                    Text(currentTask.id, color = colors.foreground.copy(alpha = 0.6f), fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
                TaskPropertyRow(
                    icon = { Icon(Icons.Rounded.PersonOutline, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                    title = "Created By",
                    colors = colors,
                ) {
                    Text(userOf(currentTask.creatorId)?.displayName ?: currentTask.creatorId, color = colors.foreground.copy(alpha = 0.6f), fontSize = 12.sp)
                }
                TaskPropertyRow(
                    icon = { Icon(Icons.Rounded.CalendarToday, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                    title = "Created At",
                    colors = colors,
                ) {
                    Text(formatDate(currentTask.createdAt), color = colors.foreground.copy(alpha = 0.6f), fontSize = 12.sp)
                }
                TaskPropertyRow(
                    icon = { Icon(Icons.Rounded.Alarm, contentDescription = null, tint = colors.warning, modifier = Modifier.size(20.dp)) },
                    title = "Due Date",
                    colors = colors,
                ) {
                    Text(formatDate(currentTask.dueAt), color = colors.foreground.copy(alpha = 0.6f), fontSize = 12.sp)
                }
                if (currentTask.assigneeIds.isNotEmpty()) {
                    TaskPropertyRow(
                        icon = { Icon(Icons.Rounded.Groups, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                        title = "Assignees (${currentTask.assigneeIds.size})",
                        colors = colors,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            currentTask.assigneeIds.take(6).forEach { id ->
                                val user = userOf(id)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(colors.surfaceSecondary)
                                        .padding(end = 8.dp),
                                ) {
                                    TaskAvatar(user?.avatarInitials ?: "U", user?.avatarColorHex ?: "0xFF6366F1", 18, colors)
                                    Spacer(Modifier.width(4.dp))
                                    Text(user?.displayName ?: id, fontSize = 11.5.sp, color = colors.foreground)
                                }
                            }
                        }
                    }
                }
                if (currentTask.labels.isNotEmpty()) {
                    TaskPropertyRow(
                        icon = { Icon(Icons.Rounded.Label, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                        title = "Labels",
                        colors = colors,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            currentTask.labels.forEach { label ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(colors.surfaceSecondary)
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text(label, fontSize = 11.5.sp, color = colors.foreground)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Activity Timeline - Supabase-only source on the api branch; the
            // custom REST plane has no activity feed, so the Dart empty state
            // is the truthful render.
            TaskSectionTitle("Activity Timeline", colors)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surfaceElevated)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("No changes recorded yet.", color = colors.foreground.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        DeleteTaskDialog(
            onCancel = { confirmDelete = false },
            onDelete = {
                confirmDelete = false
                scope.launch {
                    controller.deleteTask(currentTask.id)
                    onBack()
                }
            },
        )
    }
}

@Composable
private fun TaskSectionTitle(title: String, colors: TaskColors) {
    Text(
        title,
        color = colors.foregroundSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun TaskPropertyRow(
    icon: @Composable () -> Unit,
    title: String,
    colors: TaskColors,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = colors.foreground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            content()
        }
    }
}
