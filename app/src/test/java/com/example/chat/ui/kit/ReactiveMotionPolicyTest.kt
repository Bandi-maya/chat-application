package com.example.chat.ui.kit

import org.junit.Assert.assertEquals
import org.junit.Test

/** No OS settings changes: fake subscription/read lifecycle only. */
class ReactiveMotionPolicyTest {
    @Test fun readsInitialChangeAndUnregistersExactlyOnce() {
        var current = false
        var callback: (() -> Unit)? = null
        var registrations = 0
        var removals = 0
        val values = mutableListOf<Boolean>()
        val stop = observeChatyMotionPolicy(
            read = { current },
            subscribe = { cb ->
                registrations++
                callback = cb
                val unregister: () -> Unit = { removals++ }
                unregister
            },
            onValue = { values.add(it) },
        )
        assertEquals(listOf(false), values)
        current = true
        callback!!()
        assertEquals(listOf(false, true), values)
        stop()
        stop()
        current = false
        callback!!() // A queued late event after disposal must have no effect.
        assertEquals(listOf(false, true), values)
        assertEquals(1, registrations)
        assertEquals(1, removals)
    }

    @Test fun subscribeBeforeInitialReadClosesSetupRace() {
        var current = false
        val values = mutableListOf<Boolean>()
        val stop = observeChatyMotionPolicy(
            read = { current },
            subscribe = { _ -> current = true; {} },
            onValue = { values.add(it) },
        )
        assertEquals(listOf(true), values)
        stop()
    }
}
