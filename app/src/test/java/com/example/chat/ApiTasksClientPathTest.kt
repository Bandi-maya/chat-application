package com.example.chat

import com.example.chat.data.api.ApiHttpTransport
import com.example.chat.data.api.ApiTasksClient
import com.example.chat.domain.models.TaskStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiTasksClientPathTest {
    private class FakeTransport : ApiHttpTransport {
        val calls = mutableListOf<String>()
        override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
            calls += "$method $url"
            return 200 to """{"data":{}}"""
        }
    }

    @Test
    fun rejectsUnsafeTaskIdsBeforeAnyRequest() {
        val t = FakeTransport()
        val client = ApiTasksClient({ "https://api.example.test" }, { "tenant" }, { "token" }, t)
        assertNull(client.updateTaskStatus("../admin", TaskStatus.completed))
        assertNull(client.updateTask("task?next=/users", "Title", "", emptyList(), "normal", ""))
        assertFalse(client.deleteTask("task#fragment"))
        assertTrue(t.calls.isEmpty())
    }
}
