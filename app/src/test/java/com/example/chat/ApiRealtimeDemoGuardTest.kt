package com.example.chat

import com.example.chat.data.api.ApiRealtimeClient
import com.example.chat.data.demo.DemoMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Offline demo: the realtime socket factory must NEVER be invoked from a demo
 * session - not by connect(), not by reconnect, whatever the caller.
 */
class ApiRealtimeDemoGuardTest {

    /** Immediate scheduler: schedule(0, task) queues; drain() runs all due. */
    private class FakeScheduler : ApiRealtimeClient.Scheduler {
        private val tasks = ArrayDeque<Pair<Long, () -> Unit>>()
        private var now = 0L
        override fun schedule(delayMillis: Long, run: () -> Unit): ApiRealtimeClient.Scheduler.Cancelable {
            val entry = now + delayMillis to run
            tasks.add(entry)
            return ApiRealtimeClient.Scheduler.Cancelable { tasks.remove(entry) }
        }
        /**
         * Advances the fake scheduler through due tasks in explicit bounded ticks.
         * The client reschedules its 25s ping heartbeat (and reconnect on failure)
         * after every run, so an unbounded drain never returns - this hung run
         * 40643675261's whole unit phase for 17 min with zero completed classes.
         * Leftover tasks after the cap are INTENTIONAL: the periodic heartbeat is
         * expected to stay pending forever. The guard assertions count socket-factory
         * calls, which scheduled pings/reconnects-in-demo never make (0), and the
         * positive control still requires exactly 1 real connect - the cap cannot
         * turn a missing connect into a pass because connect() dispatches
         * synchronously on the first drain tick.
         */
        fun drain(maxTicks: Int = 64) {
            var remaining = maxTicks
            while (tasks.isNotEmpty() && remaining-- > 0) {
                val (at, run) = tasks.removeFirst()
                now = maxOf(now, at)
                run()
            }
        }
    }

    private class RecordingFactory : ApiRealtimeClient.SocketFactory {
        var calls = 0
        override fun connect(url: String, listener: ApiRealtimeClient.SocketListener): ApiRealtimeClient.Socket {
            calls++
            throw IllegalStateException("socket factory must not be invoked in demo")
        }
    }

    private fun client(token: String?, factory: ApiRealtimeClient.SocketFactory, scheduler: FakeScheduler) = ApiRealtimeClient(
        webSocketUrl = { "wss://example.invalid/socket" },
        token = { token },
        socketFactory = factory,
        scheduler = scheduler,
    )

    @Test
    fun demoTokenNeverOpensASocket() {
        val factory = RecordingFactory()
        val scheduler = FakeScheduler()
        val c = client(DemoMode.TOKEN, factory, scheduler)
        c.connect()
        scheduler.drain()
        assertEquals(0, factory.calls)
    }

    @Test
    fun activeDemoBlocksSocketEvenWithRealToken() {
        val factory = RecordingFactory()
        val scheduler = FakeScheduler()
        DemoMode.active = true
        try {
            val c = client("real-token", factory, scheduler)
            c.connect()
            scheduler.drain()
            assertEquals(0, factory.calls)
        } finally {
            DemoMode.active = false
        }
    }

    @Test
    fun realSessionStillConnectsWhenNotDemo() {
        val scheduler = FakeScheduler()
        val factory = object : ApiRealtimeClient.SocketFactory {
            var calls = 0
            override fun connect(url: String, listener: ApiRealtimeClient.SocketListener): ApiRealtimeClient.Socket {
                calls++
                return object : ApiRealtimeClient.Socket {
                    override fun send(text: String): Boolean = true
                    override fun close() {}
                }
            }
        }
        val c = client("real-token", factory, scheduler)
        c.connect()
        scheduler.drain()
        assertEquals(1, factory.calls)
    }
}
