package com.example.chat

import com.example.chat.data.api.ApiPathSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiPathSegmentsTest {
    @Test
    fun acceptsKnownOpaqueIdCharacters() {
        assertEquals("550e8400-e29b-41d4-a716-446655440000",
            ApiPathSegments.safe("550e8400-e29b-41d4-a716-446655440000"))
        assertEquals("task.1_test~2", ApiPathSegments.safe("task.1_test~2"))
    }

    @Test
    fun rejectsPathTraversalAndUrlSyntax() {
        listOf("", ".", "..", "../admin", "a/b", "a\\b", "a?b", "a#b", "%2fadmin", "a\\nb")
            .forEach { assertNull(ApiPathSegments.safe(it)) }
    }

    @Test
    fun enforcesLengthLimit() {
        assertNull(ApiPathSegments.safe("a".repeat(129)))
        assertEquals("a".repeat(4096), ApiPathSegments.safe("a".repeat(4096), maxLength = 4096))
    }
}
