package com.example.chat

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** Wiring guard only. Does not prove transport cancellation or runtime account switching. */
class RoomCreationScopeSourceContractTest {
    private fun method(name: String): String {
        val path = "app/src/main/kotlin/com/example/chat/data/api/ApiBackendService.kt"
        val source = listOf(File(path), File("../$path")).first { it.exists() }.readText()
        return source.substringAfter("    suspend fun $name(").substringBefore("\n    /**")
    }

    @Test fun bothCreationMethodsCaptureAndPinRequestScope() {
        for (name in listOf("createOrGetDirectConversation", "createGroupRoom")) {
            val body = method(name)
            assertTrue(name, body.contains("val expected = authSnapshot()"))
            assertTrue(name, body.contains("baseUrl = { expected.baseUrl }"))
            assertTrue(name, body.contains("tenantId = { expected.tenantId }"))
            assertTrue(name, body.contains("token = { expected.token }"))
            assertTrue(name, body.contains("ScopedCallHttpTransport(expected, ::withAuthScope, sendRequests)"))
            assertFalse(name, body.contains("{ chat.create"))
        }
    }

    @Test fun bothCreationMethodsGuardPublicationAndSnapshotNamespace() {
        for (name in listOf("createOrGetDirectConversation", "createGroupRoom")) {
            val body = method(name)
            val publication = body.substringAfter("if (!withAuthScope(expected) {")
            assertTrue(name, publication.contains("_conversations.add(0, conversation)"))
            assertTrue(name, publication.contains("schedulePersistConversations(expected)"))
            assertFalse(name, body.contains("schedulePersistConversations()"))
            assertTrue(name, body.contains("if (authSnapshot() !== expected) throw ApiScopeChangedException()"))
        }
    }

    @Test fun subscriptionErrorCannotPublishIntoNewAccount() {
        val branch = method("createOrGetDirectConversation").substringAfter("catch (e: ApiChatClient.SubscriptionRequiredException)")
        assertTrue(branch.contains("if (!withAuthScope(expected) {"))
        assertTrue(branch.contains("}) throw ApiScopeChangedException()"))
    }
}
