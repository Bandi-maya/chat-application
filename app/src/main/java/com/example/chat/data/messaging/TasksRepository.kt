package com.example.chat.data.messaging

import com.example.chat.core.util.parseIsoToInstant
import com.example.chat.data.api.ApiTasksClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.chat.domain.models.ChatTask
import com.example.chat.domain.models.TaskPriority
import com.example.chat.domain.models.TaskStatus
import java.time.Instant
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Task cache backed by the Chaty backend tasks plane (/tasks) via
 * [ApiTasksClient]; the retired Supabase task RPCs are gone. The Tasks tab
 * itself rides TasksController -> ApiTasksClient directly; this repository
 * remains the hydration cache plus the status/priority mappings the parity
 * tests pin. [notifyChanged] mirrors the surrounding _loadConversations /
 * _loadMessages refreshes the Dart side bundles with task mutations.
 */
class TasksRepository(
    private val tasksPlane: () -> ApiTasksClient,
    private val notifyChanged: () -> Unit = {},
    /** Dart createTask/updateTaskStatus/updateTask/deleteTask also refresh
     *  conversations (and messages where loaded); wiring supplies that. */
    private val refreshConversations: suspend () -> Unit = {},
    private val refreshMessagesIfLoaded: suspend (String) -> Unit = {},
) {
    val tasks = mutableListOf<ChatTask>()

    fun clearLocalForSwitch() {
        tasks.clear()
        notifyChanged()
    }

    /** GET /tasks. A fetch failure keeps the previous cache (nothing
     *  user-facing reads it mid-hydration; the Tasks tab owns its own
     *  plane-backed state). */
    suspend fun loadTasks() {
        val fresh = withContext(Dispatchers.IO) { tasksPlane().listTasks() } ?: return
        tasks.clear()
        tasks.addAll(fresh)
    }

    /** Exact port of createTask's non-demo path. */
    suspend fun createTask(
        sourceConversationId: String,
        title: String,
        description: String,
        assigneeIds: List<String>,
        priority: TaskPriority,
        dueAt: Instant,
        labels: List<String> = emptyList(),
        sourceMessageId: String? = null,
    ): ChatTask {
        // The backend task model has no conversation link / labels /
        // multi-assignee yet (see ApiTasksClient); those stay local-only
        // until the plane grows them.
        val created = withContext(Dispatchers.IO) {
            tasksPlane().createTask(
                title = title.trim(),
                description = description.trim(),
                assigneeIds = assigneeIds,
                priority = taskPriorityToDatabase(priority),
                dueAtIso = dueAt.toString(),
            )
        }
        coroutineScope {
            val a = launch { loadTasks() }
            val b = launch { refreshConversations() }
            val c = launch { refreshMessagesIfLoaded(sourceConversationId) }
            a.join(); b.join(); c.join()
        }
        notifyChanged()
        return tasks.firstOrNull { it.id == created.id } ?: created
    }

    /** Exact port of _updateTaskStatusAsync (optimistic, then RPC). */
    suspend fun updateTaskStatus(taskId: String, status: TaskStatus) {
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index != -1) {
            tasks[index] = tasks[index].copy(status = status, updatedAt = Instant.now())
            notifyChanged()
        }
        withContext(Dispatchers.IO) { tasksPlane().updateTaskStatus(taskId, status) }
        loadTasks()
        notifyChanged()
    }

    /** Exact port of updateTask (optimistic, then RPC). */
    suspend fun updateTask(
        taskId: String,
        title: String,
        description: String,
        assigneeIds: List<String>,
        priority: TaskPriority,
        dueAt: Instant,
        labels: List<String> = emptyList(),
    ) {
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index != -1) {
            tasks[index] = tasks[index].copy(
                title = title.trim(),
                description = description.trim(),
                assigneeIds = assigneeIds,
                priority = priority,
                dueAt = dueAt,
                labels = labels,
                updatedAt = Instant.now(),
            )
            notifyChanged()
        }
        withContext(Dispatchers.IO) {
            tasksPlane().updateTask(
                taskId = taskId,
                title = title.trim(),
                description = description.trim(),
                assigneeIds = assigneeIds,
                priority = taskPriorityToDatabase(priority),
                dueAtIso = dueAt.toString(),
            )
        }
        loadTasks()
        notifyChanged()
    }

    /** Exact port of deleteTask (local removal, best-effort table delete). */
    suspend fun deleteTask(taskId: String) {
        tasks.removeAll { it.id == taskId }
        notifyChanged()
        runCatching {
            withContext(Dispatchers.IO) { tasksPlane().deleteTask(taskId) }
        }
        loadTasks()
        notifyChanged()
    }

    companion object {
        /** Exact port of _taskFromRow. */
        fun taskFromRow(row: Map<String, Any?>): ChatTask {
            val createdAt = parseIsoToInstant(row["created_at"]?.toString()) ?: Instant.now()
            return ChatTask(
                id = row["task_id"]?.toString() ?: "",
                sourceConversationId = row["conversation_id"]?.toString() ?: "",
                sourceMessageId = row["source_message_id"]?.toString(),
                title = row["title"]?.toString() ?: "",
                description = row["description"]?.toString() ?: "",
                creatorId = row["creator_id"]?.toString() ?: "",
                assigneeIds = stringList(row["assignee_ids"]),
                status = taskStatusFromDatabase(row["status"]?.toString()),
                priority = taskPriorityFromDatabase(row["priority"]?.toString()),
                dueAt = parseIsoToInstant(row["due_at"]?.toString())
                    ?: createdAt.plus(java.time.Duration.ofDays(3)),
                labels = stringList(row["labels"]),
                createdAt = createdAt,
                updatedAt = parseIsoToInstant(row["updated_at"]?.toString()) ?: createdAt,
            )
        }

        /** Exact port of _taskPriorityFromDatabase. */
        fun taskPriorityFromDatabase(value: String?): TaskPriority = when (value) {
            "low" -> TaskPriority.low
            "high" -> TaskPriority.high
            "urgent" -> TaskPriority.urgent
            else -> TaskPriority.medium
        }

        /** Exact port of _taskPriorityToDatabase (medium -> "normal"). */
        fun taskPriorityToDatabase(value: TaskPriority): String = when (value) {
            TaskPriority.low -> "low"
            TaskPriority.medium -> "normal"
            TaskPriority.high -> "high"
            TaskPriority.urgent -> "urgent"
        }

        /** Exact port of taskStatusFromDatabase (cancelled/archived -> archived). */
        fun taskStatusFromDatabase(value: String?): TaskStatus = when (value) {
            "assigned" -> TaskStatus.assigned
            "in_progress" -> TaskStatus.inProgress
            "blocked" -> TaskStatus.blocked
            "completed" -> TaskStatus.completed
            "cancelled", "archived" -> TaskStatus.archived
            else -> TaskStatus.inbox
        }

        /** Exact port of taskStatusToDatabase (archived -> "cancelled"). */
        fun taskStatusToDatabase(value: TaskStatus): String = when (value) {
            TaskStatus.inProgress -> "in_progress"
            TaskStatus.completed -> "completed"
            TaskStatus.archived -> "cancelled"
            TaskStatus.blocked -> "blocked"
            TaskStatus.assigned -> "assigned"
            TaskStatus.inbox -> "inbox"
        }

        private fun stringList(value: Any?): List<String> =
            (value as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
    }
}
