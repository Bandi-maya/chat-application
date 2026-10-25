package com.example.chat.ui.tasks

import com.example.chat.data.api.ApiTasksClient
import com.example.chat.domain.models.ChatTask
import com.example.chat.domain.models.TaskStatus
import com.example.chat.domain.models.TaskTransitionError
import com.example.chat.domain.models.TaskWorkflow
import com.example.chat.domain.models.Transition
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList
import org.json.JSONArray
import org.json.JSONObject

/** Persistent store seam for the task stage history ("task tree") the
 *  tasks_screen.dart keeps under SharedPreferences key chaty_task_history_v1.
 *  The Android SharedPreferences implementation lives with the UI wiring;
 *  JVM tests use an in-memory map. */
interface TaskHistoryStore {
    fun load(): Map<String, List<String>>
    fun save(history: Map<String, List<String>>)
}

/** Pure JSON codec for chaty_task_history_v1 so storage implementations stay
 *  one-liners. Shape: {taskId: ["From → To • d/M HH:mm", ...]}. */
object TaskHistoryCodec {
    fun decode(raw: String?): Map<String, List<String>> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val out = LinkedHashMap<String, List<String>>()
            for (key in obj.keys()) {
                val arr = obj.getJSONArray(key)
                out[key] = (0 until arr.length()).map { arr.get(it).toString() }
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun encode(history: Map<String, List<String>>): String {
        val obj = JSONObject()
        for ((key, value) in history) obj.put(key, JSONArray(value))
        return obj.toString()
    }
}

/** Tasks-plane state holder. Exact port of the ApiBackendService tasks slice
 *  (loadTasks/createTask/updateTaskStatus/updateTask/deleteTask local-state
 *  semantics) plus the tasks_screen.dart history recording (_recordMove with
 *  _historyCapPerTask = 30). Listener fan-out mirrors ApiBackendService's
 *  addListener/notifyListeners so Compose screens recompose on mutation. */
class TasksController(
    private val client: ApiTasksClient,
    private val historyStore: TaskHistoryStore,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now(ZoneId.systemDefault()) },
) {
    private val lock = Any()
    private val _tasks = mutableListOf<ChatTask>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    val tasks: List<ChatTask> get() = synchronized(lock) { _tasks.toList() }

    fun addListener(listener: () -> Unit) = listeners.add(listener)
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyListeners() = listeners.forEach { it() }

    /** Dart loadTasks: replace on 200, keep prior state on failure. */
    suspend fun loadTasks() {
        val fetched = client.listTasks() ?: return
        synchronized(lock) {
            _tasks.clear()
            _tasks.addAll(fetched)
        }
        notifyListeners()
    }

    /** Dart createTask: inserts the server task at index 0 on success;
     *  propagates the client's exception on failure. */
    suspend fun createTask(
        title: String,
        description: String?,
        assigneeIds: List<String>,
        priority: String,
        dueAtIso: String?,
    ): ChatTask {
        val task = client.createTask(title, description, assigneeIds, priority, dueAtIso)
        synchronized(lock) { _tasks.add(0, task) }
        notifyListeners()
        return task
    }

    /** Dart updateTaskStatus: replace at index on 200, silent otherwise. */
    suspend fun updateTaskStatus(taskId: String, status: TaskStatus) {
        val updated = client.updateTaskStatus(taskId, status) ?: return
        synchronized(lock) {
            val index = _tasks.indexOfFirst { it.id == taskId }
            if (index != -1) _tasks[index] = updated
        }
        notifyListeners()
    }

    /** Dart updateTask (full edit): replace at index on 200. */
    suspend fun updateTask(
        taskId: String,
        title: String,
        description: String,
        assigneeIds: List<String>,
        priority: String,
        dueAtIso: String,
    ) {
        val updated = client.updateTask(taskId, title, description, assigneeIds, priority, dueAtIso)
            ?: return
        synchronized(lock) {
            val index = _tasks.indexOfFirst { it.id == taskId }
            if (index != -1) _tasks[index] = updated
        }
        notifyListeners()
    }

    /** Dart deleteTask: remove locally only on 200. */
    suspend fun deleteTask(taskId: String) {
        if (!client.deleteTask(taskId)) return
        synchronized(lock) { _tasks.removeAll { it.id == taskId } }
        notifyListeners()
    }

    /** The SINGLE transition use-case from tasks_screen.dart _moveTask:
     *  workflow validation -> backend persistence -> history recording.
     *  The UI maps the result to the exact Dart snackbars. */
    suspend fun moveTask(task: ChatTask, target: TaskStatus): MoveResult {
        val move: Transition?
        try {
            move = TaskWorkflow.validate(task.status, target)
        } catch (error: TaskTransitionError) {
            return MoveResult.Invalid(error.reason)
        }
        if (move == null) return MoveResult.NoOp
        return try {
            updateTaskStatus(task.id, target)
            recordMove(task, move.from, move.to)
            MoveResult.Moved
        } catch (error: Exception) {
            MoveResult.Failed(
                "Could not move \"${task.title}\" to ${TaskWorkflow.label(target)}: $error",
            )
        }
    }

    /** Dart _recordMove: prepend 'From → To • d/M HH:mm', cap 30 per task. */
    suspend fun recordMove(task: ChatTask, from: TaskStatus, to: TaskStatus) {
        val history = historyStore.load().toMutableMap()
        val entries = history.getOrPut(task.id) { emptyList() }.toMutableList()
        val stampTime = now()
        val stamp = "${stampTime.dayOfMonth}/${stampTime.monthValue} " +
            "${stampTime.hour.toString().padStart(2, '0')}:" +
            stampTime.minute.toString().padStart(2, '0')
        entries.add(0, "${TaskWorkflow.label(from)} → ${TaskWorkflow.label(to)} • $stamp")
        while (entries.size > HISTORY_CAP_PER_TASK) entries.removeAt(entries.size - 1)
        history[task.id] = entries
        try {
            historyStore.save(history)
        } catch (_: Exception) {
        }
    }

    /** Dart _loadHistory + _showHistory read path. */
    fun historyFor(taskId: String): List<String> = historyStore.load()[taskId] ?: emptyList()

    sealed interface MoveResult {
        data object Moved : MoveResult
        data object NoOp : MoveResult
        data class Invalid(val reason: String) : MoveResult
        data class Failed(val message: String) : MoveResult
    }

    companion object {
        const val HISTORY_PREFS_KEY = "chaty_task_history_v1"
        const val HISTORY_CAP_PER_TASK = 30
    }
}
