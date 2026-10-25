package com.example.chat.ui.tasks

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.ViewKanban
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.chat.domain.models.ChatTask
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.TaskPriority
import com.example.chat.domain.models.TaskStatus
import com.example.chat.domain.models.TaskWorkflow
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.theme.LocalThemeConfig
import java.time.ZoneId
import kotlinx.coroutines.launch

/** Exact port of tasks_screen.dart: 'Action Items' header + create action,
 *  Task List / Kanban Board tabs, All/Urgent/High/Med/Low priority filter,
 *  cards with status+priority badges, overflow menu (Edit Task/View
 *  Details/Delete Task), assignee avatars, due label, kanban columns with
 *  workflow-validated drag & drop AND chevron moves, per-task history dialog.
 *
 *  [tasksRevision] bumps on every TasksController mutation so the screen
 *  re-reads controller.tasks (the same listener -> recompose pattern the
 *  owner's screens use on ApiBackendService). */
@Composable
fun TasksScreen(
    controller: TasksController,
    tasksRevision: Int,
    conversations: List<Conversation>,
    getUser: (String) -> UserProfile?,
    onOpenDetail: (ChatTask) -> Unit,
    onEditTask: (ChatTask) -> Unit,
    onCreateTask: () -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION") tasksRevision
    val theme = LocalThemeConfig.current
    val colors = remember(theme) { TaskColors(theme) }
    val tasks = controller.tasks
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tabIndex by remember { mutableIntStateOf(0) }
    var priorityFilter by remember { mutableStateOf("All") }
    var historyTask by remember { mutableStateOf<ChatTask?>(null) }

    fun moveTask(task: ChatTask, target: TaskStatus) {
        scope.launch {
            when (val result = controller.moveTask(task, target)) {
                is TasksController.MoveResult.Invalid -> {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(result.reason)
                }
                is TasksController.MoveResult.Failed -> {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(result.message)
                }
                else -> Unit
            }
        }
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Header row
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Action Items",
                    color = colors.foreground,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onCreateTask,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceSecondary),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Create task", tint = colors.primary)
                }
            }
            // Tabs
            TabRow(
                selectedTabIndex = tabIndex,
                containerColor = Color.Transparent,
                contentColor = colors.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    selectedContentColor = colors.primary,
                    unselectedContentColor = colors.foregroundSecondary,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 10.dp),
                    ) {
                        Icon(Icons.Rounded.ListAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Task List")
                    }
                }
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    selectedContentColor = colors.primary,
                    unselectedContentColor = colors.foregroundSecondary,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 10.dp),
                    ) {
                        Icon(Icons.Rounded.ViewKanban, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Kanban Board")
                    }
                }
            }
            // Priority filter chips
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 4.dp),
            ) {
                for (p in listOf("All", "Urgent", "High", "Med", "Low")) {
                    val selected = priorityFilter == p
                    FilterChip(
                        selected = selected,
                        onClick = { priorityFilter = p },
                        label = {
                            Text(
                                p,
                                color = if (selected) colors.primary else colors.foregroundSecondary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                            )
                        },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            val filtered = tasks.filter { task ->
                when (priorityFilter) {
                    "Urgent" -> task.priority == TaskPriority.urgent
                    "High" -> task.priority == TaskPriority.high
                    "Med" -> task.priority == TaskPriority.medium
                    "Low" -> task.priority == TaskPriority.low
                    else -> true
                }
            }

            if (tabIndex == 0) {
                TaskListView(
                    filtered = filtered,
                    colors = colors,
                    priorityFilter = priorityFilter,
                    getUser = getUser,
                    onClearFilter = { priorityFilter = "All" },
                    onOpenDetail = onOpenDetail,
                    onEditTask = onEditTask,
                    onCreateTask = onCreateTask,
                    onDeleteTask = { task -> scope.launch { controller.deleteTask(task.id) } },
                )
            } else {
                KanbanBoard(
                    filtered = filtered,
                    colors = colors,
                    onOpenDetail = onOpenDetail,
                    onEditTask = onEditTask,
                    onShowHistory = { historyTask = it },
                    onMove = ::moveTask,
                )
            }
        }
    }

    historyTask?.let { task ->
        TaskHistoryDialog(
            task = task,
            entries = controller.historyFor(task.id),
            colors = colors,
            onClose = { historyTask = null },
        )
    }
}

@Composable
private fun TaskListView(
    filtered: List<ChatTask>,
    colors: TaskColors,
    priorityFilter: String,
    getUser: (String) -> UserProfile?,
    onClearFilter: () -> Unit,
    onOpenDetail: (ChatTask) -> Unit,
    onEditTask: (ChatTask) -> Unit,
    onCreateTask: () -> Unit,
    onDeleteTask: (ChatTask) -> Unit,
) {
    if (filtered.isEmpty()) {
        // Dart _emptyState.
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (priorityFilter != "All") {
                Icon(Icons.Rounded.TaskAlt, contentDescription = null, tint = colors.primary, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    "No results for $priorityFilter Priority",
                    color = colors.foreground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "No tasks found matching the selected priority filter.",
                    color = colors.foregroundSecondary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Show all tasks",
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onClearFilter),
                )
            } else {
                Icon(Icons.Rounded.TaskAlt, contentDescription = null, tint = colors.primary, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text("No action items yet", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Create an action item from any message or using the create task button.",
                    color = colors.foregroundSecondary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Create task",
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onCreateTask),
                )
            }
        }
        return
    }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(filtered.size) { index ->
            val task = filtered[index]
            TaskListCard(
                task = task,
                colors = colors,
                getUser = getUser,
                onOpenDetail = { onOpenDetail(task) },
                onEditTask = { onEditTask(task) },
                onDeleteTask = { onDeleteTask(task) },
            )
        }
    }
}

@Composable
private fun TaskListCard(
    task: ChatTask,
    colors: TaskColors,
    getUser: (String) -> UserProfile?,
    onOpenDetail: () -> Unit,
    onEditTask: () -> Unit,
    onDeleteTask: () -> Unit,
) {
    val isOverdue = task.isOverdue
    var menuOpen by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val due = task.dueAt.atZone(zone)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceElevated)
            .border(
                width = 1.dp,
                color = if (isOverdue) colors.error.copy(alpha = 0.4f) else colors.borderSubtle,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onOpenDetail)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TaskStatusBadge(task.status, colors)
            Spacer(Modifier.width(8.dp))
            TaskPriorityBadge(task.priority, colors)
            Spacer(Modifier.weight(1f))
            if (isOverdue) {
                Text(
                    "OVERDUE",
                    color = colors.error,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                )
                Spacer(Modifier.width(4.dp))
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Task options", tint = colors.foregroundSecondary, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit Task") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = { menuOpen = false; onEditTask() },
                    )
                    DropdownMenuItem(
                        text = { Text("View Details") },
                        leadingIcon = { Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = { menuOpen = false; onOpenDetail() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Task", color = Color(0xFFF44336)) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Color(0xFFF44336), modifier = Modifier.size(18.dp)) },
                        onClick = { menuOpen = false; onDeleteTask() },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            task.title,
            color = colors.foreground,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp,
        )
        if (task.description.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                task.description,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = colors.foregroundSecondary,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            task.assigneeIds.take(4).forEach { id ->
                val contact = getUser(id)
                Box(Modifier.padding(end = 5.dp)) {
                    TaskAvatar(contact?.avatarInitials ?: "U", contact?.avatarColorHex, 24, colors)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Due ${due.dayOfMonth}/${due.monthValue}",
                color = if (isOverdue) colors.error else colors.foregroundSecondary,
                fontSize = 12.sp,
                fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

/** Kanban with the Dart responsive column sizing and workflow-validated
 *  drag & drop (LongPressDraggable + DragTarget) plus chevron moves. */
@Composable
private fun KanbanBoard(
    filtered: List<ChatTask>,
    colors: TaskColors,
    onOpenDetail: (ChatTask) -> Unit,
    onEditTask: (ChatTask) -> Unit,
    onShowHistory: (ChatTask) -> Unit,
    onMove: (ChatTask, TaskStatus) -> Unit,
) {
    val columns = TaskWorkflow.stages.map { it to TaskWorkflow.label(it) }
    var draggedTask by remember { mutableStateOf<ChatTask?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var pointerRoot by remember { mutableStateOf(Offset.Zero) }
    val columnBounds = remember { mutableStateOf(mapOf<TaskStatus, Rect>()) }
    val density = LocalDensity.current

    BoxWithConstraintsCompat { maxWidthPx, maxHeightPx ->
        val columnWidthDp = with(density) {
            // Dart: <500dp -> 82% width; else fit-all clamped to [240, 460]
            // (280 when fit-all < 240).
            val maxWidthDp = maxWidthPx.toDp().value
            val outerPadding = 16f * 2
            val innerMargins = 12f * columns.size
            val fitAll = (maxWidthDp - outerPadding - innerMargins) / columns.size
            when {
                maxWidthDp < 500 -> maxWidthDp * 0.82f
                fitAll < 240 -> 280f
                else -> fitAll.coerceIn(240f, 460f)
            }.dp
        }

        Box(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for ((status, title) in columns) {
                    val columnTasks = filtered.filter { it.status == status }
                    val isDropTarget = draggedTask != null &&
                        columnBounds.value[status]?.contains(pointerRoot) == true &&
                        TaskWorkflow.canMove(draggedTask!!.status, status)
                    val targetBg by animateColorAsState(
                        if (isDropTarget) colors.primary.copy(alpha = 0.1f) else colors.surfaceSecondary,
                        label = "kanban-column-bg",
                    )
                    Column(
                        modifier = Modifier
                            .width(columnWidthDp)
                            .heightIn(min = with(density) { (maxHeightPx - 24).toDp() })
                            .clip(RoundedCornerShape(18.dp))
                            .background(targetBg)
                            .border(
                                width = if (isDropTarget) 1.5.dp else 1.dp,
                                color = if (isDropTarget) colors.primary else colors.border,
                                shape = RoundedCornerShape(18.dp),
                            )
                            .onGloballyPositioned { coords ->
                                columnBounds.value = columnBounds.value + (status to coords.boundsInRoot())
                            }
                            .padding(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                title,
                                color = colors.foreground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(colors.surfaceSecondary)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    "${columnTasks.size}",
                                    color = colors.foreground,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (columnTasks.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Drop tasks here", color = colors.foregroundTertiary, fontSize = 12.sp)
                            }
                        }
                        for (task in columnTasks) {
                            KanbanCard(
                                task = task,
                                colors = colors,
                                isDragging = draggedTask?.id == task.id,
                                onOpenDetail = { onOpenDetail(task) },
                                onEditTask = { onEditTask(task) },
                                onShowHistory = { onShowHistory(task) },
                                onMove = onMove,
                                onDragStart = { origin ->
                                    draggedTask = task
                                    dragOffset = Offset.Zero
                                    pointerRoot = origin
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount
                                    pointerRoot += amount
                                },
                                onDragEnd = {
                                    val task0 = draggedTask
                                    if (task0 != null) {
                                        val target = columnBounds.value.entries
                                            .firstOrNull { it.value.contains(pointerRoot) }?.key
                                        if (target != null && TaskWorkflow.canMove(task0.status, target)) {
                                            onMove(task0, target)
                                        }
                                    }
                                    draggedTask = null
                                    dragOffset = Offset.Zero
                                },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            // Drag feedback overlay (Dart: 240dp wide card at 0.92 opacity).
            draggedTask?.let { task ->
                Box(
                    modifier = Modifier
                        .zIndex(10f)
                        .offset { IntOffset(dragOffset.x.toInt(), dragOffset.y.toInt()) }
                        .width(240.dp)
                        .alpha(0.92f),
                ) {
                    KanbanCardBody(
                        task = task,
                        colors = colors,
                        showActions = false,
                        onOpenDetail = {},
                        onEditTask = {},
                        onShowHistory = {},
                        onMove = { _, _ -> },
                    )
                }
            }
        }
    }
}

/** BoxWithConstraints without the experimental-API import churn: exposes
 *  pixel constraints to the lambda. */
@Composable
private fun BoxWithConstraintsCompat(content: @Composable (maxWidthPx: Float, maxHeightPx: Float) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints {
        with(LocalDensity.current) {
            content(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        }
    }
}

@Composable
private fun KanbanCard(
    task: ChatTask,
    colors: TaskColors,
    isDragging: Boolean,
    onOpenDetail: () -> Unit,
    onEditTask: () -> Unit,
    onShowHistory: () -> Unit,
    onMove: (ChatTask, TaskStatus) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (androidx.compose.ui.input.pointer.PointerInputChange, Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    Box(
        modifier = Modifier
            .alpha(if (isDragging) 0.35f else 1f)
            .pointerInput(task.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset -> onDragStart(offset) },
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                )
            },
    ) {
        KanbanCardBody(task, colors, true, onOpenDetail, onEditTask, onShowHistory, onMove)
    }
}

@Composable
private fun KanbanCardBody(
    task: ChatTask,
    colors: TaskColors,
    showActions: Boolean,
    onOpenDetail: () -> Unit,
    onEditTask: () -> Unit,
    onShowHistory: () -> Unit,
    onMove: (ChatTask, TaskStatus) -> Unit,
) {
    val previous = TaskWorkflow.previous(task.status)
    val next = TaskWorkflow.next(task.status)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .clickable(enabled = showActions, onClick = onOpenDetail)
            .padding(12.dp),
    ) {
        Text(
            task.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = colors.foreground,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.5.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TaskPriorityBadge(task.priority, colors)
            Spacer(Modifier.weight(1f))
            if (showActions) {
                IconButton(onClick = onEditTask, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit task", tint = colors.primary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onShowHistory, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.History, contentDescription = "History", tint = colors.primary, modifier = Modifier.size(18.dp))
                }
                if (previous != null) {
                    IconButton(onClick = { onMove(task, previous) }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = "Move back",
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp).mirrored(),
                        )
                    }
                }
                if (next != null) {
                    IconButton(onClick = { onMove(task, next) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "Move forward", tint = colors.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** Mirror the chevron for 'Move back' (Dart reuses the chevron glyph). */
private fun Modifier.mirrored(): Modifier =
    this.then(Modifier.graphicsLayer(scaleX = -1f, scaleY = 1f))

/** Dart _showHistory dialog. */
@Composable
private fun TaskHistoryDialog(
    task: ChatTask,
    entries: List<String>,
    colors: TaskColors,
    onClose: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text("History — ${task.title}") },
        text = {
            if (entries.isEmpty()) {
                Text(
                    "No stage changes recorded yet.",
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            } else {
                LazyColumn {
                    items(entries.size) { i ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(entries[i], fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onClose) { Text("Close") }
        },
        containerColor = colors.surface,
    )
}
