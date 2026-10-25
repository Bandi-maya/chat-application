package com.example.chat.data.messaging

import com.example.chat.core.util.parseIsoToInstant
import com.example.chat.data.remote.PostgrestQueryClient
import com.example.chat.domain.models.ChatTask
import com.example.chat.domain.models.TaskPriority
import com.example.chat.domain.models.TaskStatus
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Exact port of the backend_service.dart task paths (_loadTasks,
 * _taskFromRow, createTask non-demo, _updateTaskStatusAsync, updateTask,
 * deleteTask) plus the status/priority database mappings. Demo mode does not
 * exist on api. [onDataChanged] mirrors the surrounding _loadConversations /
 * _loadMessages refreshes the Dart side bundles with task mutations.
 */
class TasksRepository(
    private val rpc: SupabaseRpc,
    private val queryClient: PostgrestQueryClient? = null,
    private val notifyChanged: () -> Unit = {},
    /** Dart createTask/updateTaskStatus/updateTask/deleteTask also refresh
     *  conversations (and messages where loaded); wiring supplies that. */
    private val refreshConversations: suspend () -> Unit = {},
    private val refreshMessagesIfLoaded: suspend (String) -> Unit = {},
) {
    val tasks = mutableListOf<ChatTask>()

    /** Exact port of _loadTasks (get_my_tasks RPC + _taskFromRow). */
    suspend fun loadTasks() {
        val raw = rpc.rpc("get_my_tasks", emptyMap())
        val rows = raw as? List<*> ?: emptyList<Any?>()
        tasks.clear()
        for (item in rows) {
            val row = item as? Map<String, Any?> ?: continue
            tasks.add(taskFromRow(row))
        }
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
        val clientTaskId = UUID.randomUUID().toString()
        val raw = rpc.rpc(
            "create_chat_task",
            mapOf(
                "p_conversation_id" to sourceConversationId,
                "p_client_task_id" to clientTaskId,
                "p_title" to title.trim(),
                "p_assignee_ids" to assigneeIds,
                "p_priority" to taskPriorityToDatabase(priority),
                "p_due_at" to dueAt.toString(),
                "p_description" to description.trim(),
                "p_labels" to labels,
                "p_source_message_id" to sourceMessageId,
            ),
        )
        val id = raw?.toString() ?: ""
        coroutineScope {
            val a = launch { loadTasks() }
            val b = launch { refreshConversations() }
            val c = launch { refreshMessagesIfLoaded(sourceConversationId) }
            a.join(); b.join(); c.join()
        }
        notifyChanged()
        return tasks.first { it.id == id }
    }

    /** Exact port of _updateTaskStatusAsync (optimistic, then RPC). */
    suspend fun updateTaskStatus(taskId: String, status: TaskStatus) {
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index != -1) {
            tasks[index] = tasks[index].copy(status = status, updatedAt = Instant.now())
            notifyChanged()
        }
        rpc.rpc(
            "update_task_status",
            mapOf(
                "p_task_id" to taskId,
                "p_status" to taskStatusToDatabase(status),
            ),
        )
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
        rpc.rpc(
            "update_chat_task",
            mapOf(
                "p_task_id" to taskId,
                "p_title" to title.trim(),
                "p_description" to description.trim(),
                "p_assignee_ids" to assigneeIds,
                "p_priority" to taskPriorityToDatabase(priority),
                "p_due_at" to dueAt.toString(),
                "p_labels" to labels,
            ),
        )
        loadTasks()
        notifyChanged()
    }

    /** Exact port of deleteTask (local removal, best-effort table delete). */
    suspend fun deleteTask(taskId: String) {
        tasks.removeAll { it.id == taskId }
        notifyChanged()
        runCatching {
            queryClient?.from("tasks")?.eq("id", taskId)?.delete()
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
