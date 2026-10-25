package com.example.chat.domain.models

import com.example.chat.core.util.parseIsoToInstant
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Exact port of lib/domain/models/chat_task.dart. */

enum class TaskStatus { inbox, assigned, inProgress, blocked, completed, archived }

enum class TaskPriority { low, medium, high, urgent }

data class TaskChecklistItem(
    val id: String,
    val title: String,
    val isCompleted: Boolean = false,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "title" to title,
        "is_completed" to isCompleted,
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): TaskChecklistItem = TaskChecklistItem(
            id = map["id"]?.toString() ?: "",
            title = map["title"]?.toString() ?: "",
            isCompleted = map["is_completed"] == true,
        )
    }
}

data class TaskActivity(
    val id: String,
    val userId: String,
    val text: String,
    val timestamp: Instant,
)

data class ChatTask(
    val id: String,
    val sourceConversationId: String,
    val sourceMessageId: String? = null,
    val title: String,
    val description: String,
    val creatorId: String,
    val assigneeIds: List<String>,
    val status: TaskStatus = TaskStatus.inbox,
    val priority: TaskPriority = TaskPriority.medium,
    val dueAt: Instant,
    val reminderAt: Instant? = null,
    val labels: List<String> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val checklistItems: List<TaskChecklistItem> = emptyList(),
    val activities: List<TaskActivity> = emptyList(),
) {
    val isOverdue: Boolean
        get() = status != TaskStatus.completed &&
            status != TaskStatus.archived &&
            dueAt.isBefore(Instant.now())

    val completedChecklistCount: Int
        get() = checklistItems.count { it.isCompleted }

    val dueRelativeText: String
        get() {
            val zone = ZoneId.systemDefault()
            val now = ZonedDateTime.now(zone)
            val due = dueAt.atZone(zone)
            val today = now.toLocalDate()
            val dueDay = due.toLocalDate()
            val diffDays = java.time.temporal.ChronoUnit.DAYS.between(today, dueDay).toInt()
            val timeStr = "%02d:%02d".format(due.hour, due.minute)

            if (isOverdue) {
                if (diffDays < 0) return "${-diffDays}d overdue"
                return "Overdue ($timeStr)"
            }
            return when {
                diffDays == 0 -> "Today, $timeStr"
                diffDays == 1 -> "Tomorrow, $timeStr"
                diffDays in 2..7 -> {
                    val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    "${weekdays[due.dayOfWeek.value - 1]}, $timeStr"
                }
                else -> "${due.dayOfMonth}/${due.monthValue} $timeStr"
            }
        }

    companion object {
        /** Exact port of ChatTask.fromApi (legacy REST shape). */
        fun fromApi(json: Map<String, Any?>): ChatTask {
            fun parseStatus(status: String?): TaskStatus = when (status) {
                "assigned" -> TaskStatus.assigned
                "in_progress" -> TaskStatus.inProgress
                "blocked" -> TaskStatus.blocked
                "completed" -> TaskStatus.completed
                "archived" -> TaskStatus.archived
                else -> TaskStatus.inbox
            }

            fun parsePriority(priority: String?): TaskPriority = when (priority) {
                "low" -> TaskPriority.low
                "high" -> TaskPriority.high
                "urgent" -> TaskPriority.urgent
                else -> TaskPriority.medium
            }

            return ChatTask(
                id = json["uid"]?.toString() ?: "",
                sourceConversationId = json["chat_room_id"]?.toString() ?: "",
                sourceMessageId = null,
                title = json["title"]?.toString() ?: "",
                description = json["description"]?.toString() ?: "",
                creatorId = json["created_by_id"]?.toString() ?: "",
                assigneeIds = (json["assigned_to_ids"] as? List<*>)?.mapNotNull { it?.toString() }
                    ?: emptyList(),
                status = parseStatus(json["status"]?.toString()),
                priority = parsePriority(json["priority"]?.toString()),
                dueAt = parseIsoToInstant(json["due_date"]?.toString()) ?: Instant.now(),
                createdAt = parseIsoToInstant(json["created_at"]?.toString()) ?: Instant.now(),
                updatedAt = parseIsoToInstant(json["updated_at"]?.toString()) ?: Instant.now(),
                checklistItems = emptyList(),
                activities = emptyList(),
            )
        }
    }
}
