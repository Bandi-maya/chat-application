package com.example.chat

import com.example.chat.data.api.ApiHttpTransport
import com.example.chat.data.api.ApiTasksClient
import com.example.chat.domain.models.TaskStatus
import com.example.chat.domain.models.TaskTransitionError
import com.example.chat.domain.models.TaskWorkflow
import com.example.chat.ui.tasks.TaskHistoryCodec
import com.example.chat.ui.tasks.TaskHistoryStore
import com.example.chat.ui.tasks.TasksController
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Behavior tests for the tasks slice, written from the Dart source's
 *  observable behavior (api_backend_service.dart tasks methods,
 *  task_workflow.dart, tasks_screen.dart history). */
class TaskWorkflowParityTest {

    @Test fun stageOrderAndLabelsMatchDart() {
        assertEquals(
            listOf(
                TaskStatus.inbox, TaskStatus.inProgress, TaskStatus.assigned,
                TaskStatus.blocked, TaskStatus.completed,
            ),
            TaskWorkflow.stages,
        )
        assertEquals("Todo", TaskWorkflow.label(TaskStatus.inbox))
        assertEquals("In Process", TaskWorkflow.label(TaskStatus.inProgress))
        assertEquals("In Review", TaskWorkflow.label(TaskStatus.assigned))
        assertEquals("Testing", TaskWorkflow.label(TaskStatus.blocked))
        assertEquals("Done", TaskWorkflow.label(TaskStatus.completed))
        assertEquals("Archived", TaskWorkflow.label(TaskStatus.archived))
    }

    @Test fun previousNextBoundaries() {
        assertNull(TaskWorkflow.previous(TaskStatus.inbox))
        assertNull(TaskWorkflow.previous(TaskStatus.archived))
        assertEquals(TaskStatus.inbox, TaskWorkflow.previous(TaskStatus.inProgress))
        assertEquals(TaskStatus.blocked, TaskWorkflow.next(TaskStatus.assigned))
        assertNull(TaskWorkflow.next(TaskStatus.completed))
        assertNull(TaskWorkflow.next(TaskStatus.archived))
    }

    @Test fun canMoveRejectsOffBoardStatuses() {
        assertTrue(TaskWorkflow.canMove(TaskStatus.inbox, TaskStatus.completed))
        assertFalse(TaskWorkflow.canMove(TaskStatus.archived, TaskStatus.inbox))
        assertFalse(TaskWorkflow.canMove(TaskStatus.inbox, TaskStatus.archived))
    }

    @Test fun validateNullOnSameStageThrowsOnIllegal() {
        assertNull(TaskWorkflow.validate(TaskStatus.inbox, TaskStatus.inbox))
        val move = TaskWorkflow.validate(TaskStatus.inbox, TaskStatus.inProgress)!!
        assertEquals(TaskStatus.inbox, move.from)
        assertEquals(TaskStatus.inProgress, move.to)
        try {
            TaskWorkflow.validate(TaskStatus.archived, TaskStatus.inbox)
            fail("expected TaskTransitionError")
        } catch (error: TaskTransitionError) {
            assertEquals("Archived → Todo is not a valid workflow move.", error.reason)
        }
    }
}

private class FakeTransport(
    var status: Int = 200,
    var body: String = "{\"data\":[]}",
) : ApiHttpTransport {
    data class Call(val method: String, val url: String, val headers: Map<String, String>, val body: String?)
    val calls = mutableListOf<Call>()
    override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
        calls.add(Call(method, url, headers, body))
        return status to body.let { this.body }
    }
}

private fun clientWith(transport: FakeTransport, token: String? = "tok") = ApiTasksClient(
    baseUrl = { "http://10.0.2.2:8000/" },
    tenantId = { "chat-application" },
    token = { token },
    transport = transport,
)

class ApiTasksClientParityTest {

    @Test fun listTasksGetNoFallbackAndParses() {
        val transport = FakeTransport(
            body = "{\"data\":[{\"uid\":\"t1\",\"chat_room_id\":\"r1\",\"title\":\"Ship\"," +
                "\"description\":\"d\",\"created_by_id\":\"u1\",\"assigned_to_ids\":[\"u2\"]," +
                "\"status\":\"in_progress\",\"priority\":\"urgent\",\"due_date\":\"2026-10-26T18:00:00Z\"," +
                "\"created_at\":\"2026-10-25T10:00:00Z\",\"updated_at\":\"2026-10-25T10:00:00Z\"}]}",
        )
        val tasks = clientWith(transport).listTasks()!!
        val call = transport.calls.single()
        assertEquals("GET", call.method)
        assertEquals("http://10.0.2.2:8000/tasks", call.url)
        assertEquals("Bearer tok", call.headers["Authorization"])
        assertEquals("chat-application", call.headers["x-tenant-id"])
        assertEquals(1, tasks.size)
        assertEquals("t1", tasks[0].id)
        assertEquals(TaskStatus.inProgress, tasks[0].status)
        assertEquals(com.example.chat.domain.models.TaskPriority.urgent, tasks[0].priority)
        assertEquals(listOf("u2"), tasks[0].assigneeIds)
    }

    @Test fun listTasks404DoesNotFallbackAndKeepsNull() {
        // Dart tasks paths have NO /api/v1 fallback (unlike the chat plane).
        val transport = FakeTransport(status = 404, body = "{}")
        assertNull(clientWith(transport).listTasks())
        assertEquals(1, transport.calls.size)
    }

    @Test fun listTasksNoTokenReturnsNullWithoutCall() {
        val transport = FakeTransport()
        assertNull(clientWith(transport, token = null).listTasks())
        assertTrue(transport.calls.isEmpty())
    }

    @Test fun createTaskPostsExactBodyAndParses() {
        val transport = FakeTransport(
            body = "{\"data\":{\"uid\":\"t9\",\"title\":\"New\",\"status\":\"todo\",\"priority\":\"medium\"}}",
        )
        val task = clientWith(transport).createTask(
            title = "New", description = "desc", assigneeIds = listOf("u1"),
            priority = "medium", dueAtIso = "2026-10-28T18:00:00Z",
        )
        val call = transport.calls.single()
        assertEquals("POST", call.method)
        assertEquals("http://10.0.2.2:8000/tasks", call.url)
        val json = org.json.JSONObject(call.body)
        assertEquals("New", json.getString("title"))
        assertEquals("desc", json.getString("description"))
        assertEquals("medium", json.getString("priority"))
        assertEquals("2026-10-28T18:00:00Z", json.getString("due_date"))
        assertEquals("u1", json.getJSONArray("assigned_to_ids").getString(0))
        assertEquals("t9", task.id)
        assertEquals(TaskStatus.inbox, task.status)
    }

    @Test fun createTaskNon200Throws() {
        val transport = FakeTransport(status = 500, body = "{}")
        try {
            clientWith(transport).createTask("t", null, emptyList(), "medium", null)
            fail("expected exception")
        } catch (error: Exception) {
            assertEquals("Failed to create task", error.message)
        }
    }

    @Test fun updateTaskStatusWireMapping() {
        assertEquals("todo", ApiTasksClient.taskStatusToWire(TaskStatus.inbox))
        assertEquals("assigned", ApiTasksClient.taskStatusToWire(TaskStatus.assigned))
        assertEquals("in_progress", ApiTasksClient.taskStatusToWire(TaskStatus.inProgress))
        assertEquals("blocked", ApiTasksClient.taskStatusToWire(TaskStatus.blocked))
        assertEquals("completed", ApiTasksClient.taskStatusToWire(TaskStatus.completed))
        assertEquals("archived", ApiTasksClient.taskStatusToWire(TaskStatus.archived))
    }

    @Test fun updateTaskStatusPutsStatusOnly() {
        val transport = FakeTransport(
            body = "{\"data\":{\"uid\":\"t1\",\"status\":\"completed\",\"title\":\"x\"}}",
        )
        val updated = clientWith(transport).updateTaskStatus("t1", TaskStatus.completed)!!
        val call = transport.calls.single()
        assertEquals("PUT", call.method)
        assertEquals("http://10.0.2.2:8000/tasks/t1", call.url)
        assertEquals("completed", org.json.JSONObject(call.body).getString("status"))
        assertEquals(TaskStatus.completed, updated.status)
    }

    @Test fun deleteOnlyTrueOn200() {
        val ok = FakeTransport(status = 200, body = "{}")
        assertTrue(clientWith(ok).deleteTask("t1"))
        assertEquals("DELETE", ok.calls.single().method)
        val bad = FakeTransport(status = 500, body = "{}")
        assertFalse(clientWith(bad).deleteTask("t1"))
    }
}

private class InMemoryHistoryStore : TaskHistoryStore {
    var data = mapOf<String, List<String>>()
    override fun load(): Map<String, List<String>> = data
    override fun save(history: Map<String, List<String>>) { data = history }
}

class TasksControllerParityTest {

    private val fixedNow = ZonedDateTime.of(2026, 10, 25, 18, 30, 0, 0, ZoneId.systemDefault())

    private fun controller(transport: FakeTransport, store: InMemoryHistoryStore = InMemoryHistoryStore()) =
        TasksController(clientWith(transport), store, now = { fixedNow })

    @Test fun loadKeepsPriorStateOnFailure() = runBlocking {
        val transport = FakeTransport(
            body = "{\"data\":[{\"uid\":\"t1\",\"title\":\"a\"}]}",
        )
        val c = controller(transport)
        c.loadTasks()
        assertEquals(1, c.tasks.size)
        transport.status = 500
        c.loadTasks()
        assertEquals(1, c.tasks.size)
    }

    @Test fun createInsertsAtZero() = runBlocking {
        val transport = FakeTransport(body = "{\"data\":[{\"uid\":\"t0\"}]}")
        val c = controller(transport)
        c.loadTasks()
        transport.body = "{\"data\":{\"uid\":\"t-new\",\"title\":\"n\"}}"
        c.createTask("n", null, emptyList(), "medium", null)
        assertEquals("t-new", c.tasks.first().id)
    }

    @Test fun deleteKeepsTaskOnFailure() = runBlocking {
        val transport = FakeTransport(body = "{\"data\":[{\"uid\":\"t1\"}]}")
        val c = controller(transport)
        c.loadTasks()
        transport.status = 500
        c.deleteTask("t1")
        assertEquals(1, c.tasks.size)
        transport.status = 200
        c.deleteTask("t1")
        assertTrue(c.tasks.isEmpty())
    }

    @Test fun moveTaskValidationAndHistory() = runBlocking {
        val transport = FakeTransport(body = "{\"data\":[{\"uid\":\"t1\",\"status\":\"todo\",\"title\":\"T\"}]}")
        val store = InMemoryHistoryStore()
        val c = controller(transport, store)
        c.loadTasks()
        val task = c.tasks.single()

        // Same-stage is a silent no-op.
        assertTrue(c.moveTask(task, TaskStatus.inbox) is TasksController.MoveResult.NoOp)

        // Illegal move surfaces the workflow reason, no persistence.
        val invalid = c.moveTask(task.copy(status = TaskStatus.archived), TaskStatus.inbox)
        assertTrue(invalid is TasksController.MoveResult.Invalid)
        assertEquals(
            "Archived → Todo is not a valid workflow move.",
            (invalid as TasksController.MoveResult.Invalid).reason,
        )

        // Legal move persists and records history with the Dart stamp format.
        transport.body = "{\"data\":{\"uid\":\"t1\",\"status\":\"in_progress\",\"title\":\"T\"}}"
        assertTrue(c.moveTask(task, TaskStatus.inProgress) is TasksController.MoveResult.Moved)
        assertEquals(TaskStatus.inProgress, c.tasks.single().status)
        assertEquals(listOf("Todo → In Process • 25/10 18:30"), c.historyFor("t1"))
    }

    @Test fun historyCodecRoundTripAndCap() = runBlocking {
        val transport = FakeTransport(body = "{\"data\":[{\"uid\":\"t1\",\"status\":\"todo\",\"title\":\"T\"}]}")
        val store = InMemoryHistoryStore()
        val c = controller(transport, store)
        c.loadTasks()
        val task = c.tasks.single()
        transport.body = "{\"data\":{\"uid\":\"t1\",\"status\":\"in_progress\",\"title\":\"T\"}}"
        repeat(35) { c.recordMove(task, TaskStatus.inbox, TaskStatus.inProgress) }
        assertEquals(30, c.historyFor("t1").size)
        val encoded = TaskHistoryCodec.encode(store.data)
        assertEquals(store.data, TaskHistoryCodec.decode(encoded))
        assertEquals(emptyMap<String, List<String>>(), TaskHistoryCodec.decode("not json"))
        assertEquals(emptyMap<String, List<String>>(), TaskHistoryCodec.decode(null))
    }
}
