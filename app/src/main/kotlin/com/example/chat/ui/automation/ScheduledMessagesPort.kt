package com.example.chat.ui.automation

import com.example.chat.data.services.ServerScheduledMessage
import java.time.Instant

/** Account-bound scheduling session. publish is a short synchronous scope guard, never network IO. */
interface ScheduledMessagesPort {
    fun publish(block: () -> Unit): Boolean
    suspend fun create(roomId: String, body: String, at: Instant): String
    suspend fun list(): List<ServerScheduledMessage>
    suspend fun get(id: String): ServerScheduledMessage
    suspend fun cancel(id: String)
}
