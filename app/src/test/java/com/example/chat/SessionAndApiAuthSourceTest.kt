package com.example.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Publisher review of delta 3: the REST/GoTrue preference auth adapter needs
 *  direct tests. Planes are fakes; the composite is pure Kotlin. */
class SessionAndApiAuthSourceTest {
    private class FakePlane : AuthPlaneSource {
        var id: String? = null
        val planeListeners = mutableListOf<() -> Unit>()
        var addCount = 0
        var removeCount = 0
        override fun currentUserId(): String? = id
        override fun addPlaneListener(listener: () -> Unit) { addCount++; planeListeners.add(listener) }
        override fun removePlaneListener(listener: () -> Unit) { removeCount++; planeListeners.remove(listener) }
        fun fire() = planeListeners.toList().forEach { it() }
    }

    private fun harness(): Triple<SessionAndApiAuthSource, FakePlane, FakePlane> {
        val goTrue = FakePlane()
        val rest = FakePlane()
        return Triple(SessionAndApiAuthSource(goTrue, rest), goTrue, rest)
    }

    @Test
    fun restOnlyAccountGetsItsOwnScopeAndEvents() {
        val (source, _, rest) = harness()
        val seen = mutableListOf<String?>()
        source.addListener { seen.add(it) }
        rest.id = "rest-a"
        rest.fire()
        assertEquals(listOf("rest-a"), seen)
        assertEquals("rest-a", source.currentUserId())
    }

    @Test
    fun restAccountSwitchAtoBtoAForwardsEachChange() {
        val (source, _, rest) = harness()
        val seen = mutableListOf<String?>()
        source.addListener { seen.add(it) }
        rest.id = "rest-a"; rest.fire()
        rest.id = "rest-b"; rest.fire()
        rest.id = "rest-a"; rest.fire()
        assertEquals(listOf("rest-a", "rest-b", "rest-a"), seen)
    }

    @Test
    fun signOutFallsBackToNullScope() {
        val (source, _, rest) = harness()
        val seen = mutableListOf<String?>()
        source.addListener { seen.add(it) }
        rest.id = "rest-a"; rest.fire()
        rest.id = null; rest.fire()
        assertEquals(listOf("rest-a", null), seen)
        assertNull(source.currentUserId())
    }

    @Test
    fun goTrueWinsWhenPresentAndRestReappearsOnGoTrueSignOut() {
        val (source, goTrue, rest) = harness()
        val seen = mutableListOf<String?>()
        source.addListener { seen.add(it) }
        rest.id = "rest-a"; rest.fire()
        goTrue.id = "supa-1"; goTrue.fire()
        assertEquals("supa-1", source.currentUserId())
        goTrue.id = null; goTrue.fire()
        assertEquals("rest-a", source.currentUserId())
        assertEquals(listOf("rest-a", "supa-1", "rest-a"), seen)
    }

    @Test
    fun goTrueSameIdEventsStillForwardForReconcileParity() {
        val (source, goTrue, _) = harness()
        val seen = mutableListOf<String?>()
        source.addListener { seen.add(it) }
        goTrue.id = "supa-1"; goTrue.fire()
        goTrue.fire() // token refresh: same id, still forwarded
        assertEquals(listOf("supa-1", "supa-1"), seen)
    }

    @Test
    fun restSameIdRoutingOnlyEventsAreDeduped() {
        val (source, _, rest) = harness()
        val seen = mutableListOf<String?>()
        source.addListener { seen.add(it) }
        rest.id = "rest-a"; rest.fire()
        rest.fire() // tenant/routing change, same user
        rest.fire()
        assertEquals(listOf("rest-a"), seen)
    }

    @Test
    fun lastListenerRemovalDetachesBothPlanes() {
        val (source, goTrue, rest) = harness()
        val seen = mutableListOf<String?>()
        val listener: (String?) -> Unit = { seen.add(it) }
        source.addListener(listener)
        assertEquals(1, goTrue.addCount)
        assertEquals(1, rest.addCount)
        source.removeListener(listener)
        assertEquals(1, goTrue.removeCount)
        assertEquals(1, rest.removeCount)
        rest.id = "rest-a"; rest.fire()
        goTrue.id = "supa-1"; goTrue.fire()
        assertTrue(seen.isEmpty())
    }

    @Test
    fun reattachmentAfterTeardownResumesEvents() {
        val (source, _, rest) = harness()
        val seen = mutableListOf<String?>()
        val listener: (String?) -> Unit = { seen.add(it) }
        source.addListener(listener)
        source.removeListener(listener)
        source.addListener(listener)
        rest.id = "rest-a"; rest.fire()
        assertEquals(listOf("rest-a"), seen)
    }

    @Test
    fun attachmentImmediateFireIsSuppressed() {
        // ApiBackendService.addAuthScopeListener fires immediately on attach;
        // the pre-seeded dedupe must make that a no-op.
        val (source, goTrue, rest) = harness()
        rest.id = "rest-a"
        goTrue.id = null
        val seen = mutableListOf<String?>()
        val listener: (String?) -> Unit = { seen.add(it) }
        source.addListener(listener)
        rest.fire() // simulates the immediate fire at attach time
        assertTrue(seen.isEmpty())
    }
}
