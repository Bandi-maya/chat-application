package com.example.chat

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-text guard (not runtime proof): a stale createTaskTick must not reopen the editor on Tasks entry. */
class TasksEntryNoAutoOpenRegressionTest {
    @Test
    fun staleTickDoesNotOpenEditor() {
        val f = listOf("src/main/kotlin/com/example/chat/ui/AppRoot.kt", "app/src/main/kotlin/com/example/chat/ui/AppRoot.kt")
            .map { File(it) }.first { it.exists() }
        val t = f.readText()
        assertTrue(t.contains("createTaskTick > handledCreateTaskTick"))
        assertTrue(!t.contains("if (createTaskTick > 0)"))
    }
}
