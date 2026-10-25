package com.example.chat.data.api

import com.example.chat.domain.models.ChatTask
import com.example.chat.domain.models.TaskStatus
import org.json.JSONArray
import org.json.JSONObject

/** REST tasks plane of the api branch's ApiBackendService
 *  (lib/data/services/api_backend_service.dart lines 1798-1945): loadTasks,
 *  createTask, updateTaskStatus, updateTask, deleteTask against
 *  `{restApiBaseUrl}/tasks`. Exact URL, header and status handling parity:
 *  unlike the chat plane, the Dart tasks paths have NO /api/v1 404 fallback
 *  and no 402 subscription handling. Context-free so it is JVM-testable;
 *  state ownership lives in the caller (TasksController), exactly like Dart
 *  where ApiBackendService owns _tasks. */
class ApiTasksClient(
    private val baseUrl: () -> String,
    private val tenantId: () -> String,
    private val token: () -> String?,
    private val transport: ApiHttpTransport,
) {

    private fun base() = baseUrl().trimEnd('/')

    private fun authHeaders(json: Boolean = false): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        token()?.let { headers["Authorization"] = "Bearer $it" }
        headers["x-tenant-id"] = tenantId()
        if (json) headers["Content-Type"] = "application/json"
        return headers
    }

    /** loadTasks: GET /tasks -> ChatTask.fromApi list, or null on failure
     *  (Dart catches and keeps prior state; null tells the caller to keep
     *  what it has). Returns null without a token, mirroring Dart's early
     *  return. */
    fun listTasks(): List<ChatTask>? {
        if (token() == null) return null
        return try {
            val response = transport.execute("GET", "${base()}/tasks", authHeaders(), null)
            if (response.first != 200) return null
            val data = JSONObject(response.second).getJSONArray("data")
            val tasks = mutableListOf<ChatTask>()
            for (i in 0 until data.length()) {
                tasks.add(ChatTask.fromApi(ApiChatClient.deepMap(data.getJSONObject(i))))
            }
            tasks
        } catch (_: Exception) {
            null
        }
    }

    /** createTask: POST /tasks {title, description, priority, due_date,
     *  assigned_to_ids} -> created task on 200. Throws on non-200, matching
     *  Dart's `throw Exception('Failed to create task')`; the modal surfaces
     *  the message after stripping an 'Exception: ' prefix. */
    fun createTask(
        title: String,
        description: String?,
        assigneeIds: List<String>,
        priority: String,
        dueAtIso: String?,
    ): ChatTask {
        val payload = JSONObject()
            .put("title", title)
            .put("description", description)
            .put("priority", priority)
            .put("due_date", dueAtIso)
            .put("assigned_to_ids", JSONArray(assigneeIds))
            .toString()
        val response = transport.execute("POST", "${base()}/tasks", authHeaders(json = true), payload)
        if (response.first == 200) {
            val data = JSONObject(response.second).getJSONObject("data")
            return ChatTask.fromApi(ApiChatClient.deepMap(data))
        }
        throw Exception("Failed to create task")
    }

    /** updateTaskStatus: PUT /tasks/<id> {status}. Dart maps the enum to the
     *  server string ('todo' for inbox) and only replaces local state on 200. */
    fun updateTaskStatus(taskId: String, status: TaskStatus): ChatTask? {
        val statusStr = when (status) {
            TaskStatus.inbox -> "todo"
            TaskStatus.assigned -> "assigned"
            TaskStatus.inProgress -> "in_progress"
            TaskStatus.blocked -> "blocked"
            TaskStatus.completed -> "completed"
            TaskStatus.archived -> "archived"
        }
        return try {
            val payload = JSONObject().put("status", statusStr).toString()
            val response = transport.execute(
                "PUT", "${base()}/tasks/$taskId", authHeaders(json = true), payload,
            )
            if (response.first != 200) return null
            ChatTask.fromApi(ApiChatClient.deepMap(JSONObject(response.second).getJSONObject("data")))
        } catch (_: Exception) {
            null
        }
    }

    /** updateTask: PUT /tasks/<id> {title, description, priority, due_date,
     *  assigned_to_ids}; returns the updated task on 200, null otherwise. */
    fun updateTask(
        taskId: String,
        title: String,
        description: String,
        assigneeIds: List<String>,
        priority: String,
        dueAtIso: String,
    ): ChatTask? {
        return try {
            val payload = JSONObject()
                .put("title", title)
                .put("description", description)
                .put("priority", priority)
                .put("due_date", dueAtIso)
                .put("assigned_to_ids", JSONArray(assigneeIds))
                .toString()
            val response = transport.execute(
                "PUT", "${base()}/tasks/$taskId", authHeaders(json = true), payload,
            )
            if (response.first != 200) return null
            ChatTask.fromApi(ApiChatClient.deepMap(JSONObject(response.second).getJSONObject("data")))
        } catch (_: Exception) {
            null
        }
    }

    /** deleteTask: DELETE /tasks/<id>; Dart only removes local state on 200. */
    fun deleteTask(taskId: String): Boolean {
        return try {
            val response = transport.execute("DELETE", "${base()}/tasks/$taskId", authHeaders(), null)
            response.first == 200
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        /** Dart updateTaskStatus's reverse map for callers that need the
         *  wire value (inbox -> 'todo'). Kept beside the client so the UI
         *  never re-derives it. */
        fun taskStatusToWire(status: TaskStatus): String = when (status) {
            TaskStatus.inbox -> "todo"
            TaskStatus.assigned -> "assigned"
            TaskStatus.inProgress -> "in_progress"
            TaskStatus.blocked -> "blocked"
            TaskStatus.completed -> "completed"
            TaskStatus.archived -> "archived"
        }
    }
}
