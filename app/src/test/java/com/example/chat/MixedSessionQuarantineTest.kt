package com.example.chat

import com.example.chat.data.api.ApiAuthSession
import com.example.chat.data.api.ApiRealtimeClient
import com.example.chat.data.api.ApiSessionStore
import com.example.chat.data.demo.DemoMode
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Restore-time quarantine + ACTUAL listener ordering (main/peer: the
 *  restore transition fires auth listeners; a mixed store must not dispatch
 *  the realtime socket on that FIRST transition). Replicates the production
 *  wiring (ApiBackendService.startCallScopeMonitoring): the session's
 *  onTransition drives `if (!DemoMode.active) realtime.connect()`. */
class MixedSessionQuarantineTest {

    @After fun reset() { DemoMode.active = false }

    private class MemoryStore : ApiSessionStore {
        var token: String? = null
        var userJson: String? = null
        override fun loadToken() = token
        override fun loadUserJson() = userJson
        override fun save(token: String, userJson: String) { this.token = token; this.userJson = userJson }
        override fun clear() { token = null; userJson = null }
    }

    /** Queued fake scheduler: schedule() only queues; drain() runs due tasks
     *  in explicit bounded ticks. The client reschedules its 25s ping
     *  heartbeat (and a reconnect after a failed ping) after every run, so an
     *  INLINE scheduler recurses forever (StackOverflowError, run 40649071713)
     *  and an unbounded drain never returns. Leftover tasks after the cap are
     *  INTENTIONAL: the periodic heartbeat stays pending forever. The cap
     *  cannot turn a missing connect into a pass: connect() dispatches on the
     *  first drain tick. */
    private class FakeScheduler : ApiRealtimeClient.Scheduler {
        private val tasks = ArrayDeque<Pair<Long, () -> Unit>>()
        private var now = 0L
        override fun schedule(delayMillis: Long, task: () -> Unit): ApiRealtimeClient.Scheduler.Cancelable {
            val entry = now + delayMillis to task
            tasks.add(entry)
            return ApiRealtimeClient.Scheduler.Cancelable { tasks.remove(entry) }
        }
        fun drain(maxTicks: Int = 64) {
            var remaining = maxTicks
            while (tasks.isNotEmpty() && remaining-- > 0) {
                val (at, run) = tasks.removeFirst()
                now = maxOf(now, at)
                run()
            }
        }
    }

    private class CountingFactory : ApiRealtimeClient.SocketFactory {
        var calls = 0
        override fun connect(url: String, listener: ApiRealtimeClient.SocketListener): ApiRealtimeClient.Socket {
            calls++
            return object : ApiRealtimeClient.Socket {
                // Healthy socket: sends succeed, so no failed-ping cleanup +
                // reconnect churn; the socket path stays at exactly 1 connect.
                override fun send(text: String) = true
                override fun close() {}
            }
        }
    }

    private fun demoJson() = JSONObject().put("id", DemoMode.USER_ID).put("username", "demo").toString()
    private fun realJson() = JSONObject().put("id", "user-123").put("username", "bandi").toString()

    private class Wiring(val session: ApiAuthSession, val factory: CountingFactory, val scheduler: FakeScheduler)

    /** Builds session+realtime with the PRODUCTION listener wiring. The
     *  listener is registered before restore, exactly like
     *  startCallScopeMonitoring runs before session.restore(). */
    private fun wiring(store: MemoryStore): Wiring {
        val factory = CountingFactory()
        val scheduler = FakeScheduler()
        lateinit var session: ApiAuthSession
        val realtime = ApiRealtimeClient(
            webSocketUrl = { "wss://example.invalid/socket" },
            token = { session.token },
            socketFactory = factory,
            scheduler = scheduler,
        )
        session = ApiAuthSession(store, onTransition = {
            if (!DemoMode.active) realtime.connect()
        })
        return Wiring(session, factory, scheduler)
    }

    @Test fun mixedRealTokenDemoJsonNeverDispatchesOnRestoreTransition() {
        val store = MemoryStore().apply { token = "real-jwt-token"; userJson = demoJson() }
        val w = wiring(store)
        w.session.restore()
        w.scheduler.drain()
        assertEquals("mixed identity must never reach the socket factory", 0, w.factory.calls)
        assertNull("mixed session must not be published", w.session.token)
        assertNull(w.session.currentUser)
        assertEquals("raw real bytes must survive", "real-jwt-token", store.loadToken())
        assertEquals(demoJson(), store.loadUserJson())
    }

    @Test fun mixedDemoTokenRealJsonNeverDispatchesOnRestoreTransition() {
        val store = MemoryStore().apply { token = DemoMode.TOKEN; userJson = realJson() }
        val w = wiring(store)
        w.session.restore()
        w.scheduler.drain()
        assertEquals(0, w.factory.calls)
        assertNull(w.session.token)
        assertEquals("raw bytes must survive", DemoMode.TOKEN, store.loadToken())
        assertEquals(realJson(), store.loadUserJson())
    }

    @Test fun strictDemoRestoreStaysOfflineThroughTransition() {
        val store = MemoryStore().apply { token = DemoMode.TOKEN; userJson = demoJson() }
        DemoMode.active = true // mimics initialize()'s strict pre-arm
        val w = wiring(store)
        w.session.restore()
        w.scheduler.drain()
        assertEquals("demo restore must not dispatch even if the listener ran", 0, w.factory.calls)
        assertEquals(DemoMode.TOKEN, w.session.token) // demo session restores normally
    }

    @Test fun realSessionStillConnectsThroughTheSameWiring() {
        val store = MemoryStore().apply { token = "real-jwt-token"; userJson = realJson() }
        val w = wiring(store)
        w.session.restore()
        w.scheduler.drain()
        assertEquals("real sessions must keep the socket path", 1, w.factory.calls)
        assertEquals("real-jwt-token", w.session.token)
    }
}
