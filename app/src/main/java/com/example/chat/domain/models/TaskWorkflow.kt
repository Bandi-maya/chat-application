package com.example.chat.domain.models

/** Exact port of lib/domain/models/task_workflow.dart.
 *
 *  Typed kanban workflow - the SINGLE authoritative source for stage order,
 *  labels and legal transitions. Both the card chevron and the drag & drop
 *  target MUST go through [TaskWorkflow.validate] so there is exactly one
 *  transition use-case; the UI never moves a task that the workflow rejects
 *  and persistence is the only thing that makes a move visible
 *  (realtime/refresh confirms it). */
object TaskWorkflow {

    /** User-facing stages in board order. Enum values reuse the existing
     *  server mapping (`inbox, assigned, in_progress, blocked, completed`). */
    val stages: List<TaskStatus> = listOf(
        TaskStatus.inbox,
        TaskStatus.inProgress,
        TaskStatus.assigned,
        TaskStatus.blocked,
        TaskStatus.completed,
    )

    private val labels = mapOf(
        TaskStatus.inbox to "Todo",
        TaskStatus.inProgress to "In Process",
        TaskStatus.assigned to "In Review",
        TaskStatus.blocked to "Testing",
        TaskStatus.completed to "Done",
        TaskStatus.archived to "Archived",
    )

    fun label(status: TaskStatus): String = labels[status] ?: status.name

    private fun indexOf(status: TaskStatus): Int = stages.indexOf(status)

    /** The previous board stage, or null when already at the first stage
     *  (or outside the board, e.g. archived). */
    fun previous(status: TaskStatus): TaskStatus? {
        val index = indexOf(status)
        if (index <= 0) return null
        return stages[index - 1]
    }

    /** The next board stage, or null when no forward transition exists. */
    fun next(status: TaskStatus): TaskStatus? {
        val index = indexOf(status)
        if (index < 0 || index >= stages.size - 1) return null
        return stages[index + 1]
    }

    /** Whether [current] -> [target] is a legal move on the board. Same-stage
     *  drops are no-ops (accepted silently), off-board statuses are rejected. */
    fun canMove(current: TaskStatus, target: TaskStatus): Boolean =
        indexOf(current) >= 0 && indexOf(target) >= 0

    /** Validate + describe a requested move. Returns null when the move is a
     *  no-op (same stage); throws [TaskTransitionError] when illegal so the
     *  UI can reject a drop cleanly instead of silently doing nothing. */
    fun validate(current: TaskStatus, target: TaskStatus): Transition? {
        if (current == target) return null
        if (!canMove(current, target)) {
            throw TaskTransitionError(
                from = current,
                to = target,
                reason = "${label(current)} → ${label(target)} is not a valid workflow move.",
            )
        }
        return Transition(from = current, to = target)
    }
}

data class Transition(val from: TaskStatus, val to: TaskStatus)

class TaskTransitionError(
    val from: TaskStatus,
    val to: TaskStatus,
    val reason: String,
) : Exception(reason)
