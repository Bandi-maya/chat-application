package com.example.chat.island.engine

import org.junit.Assert.*
import org.junit.Test

class NoticeQueueTest {
    private fun message(sender: String, text: String = "body", key: String? = null) = Notice(
        IslandMode.Notification("Chaty test", sender, text), 1000, IslandLevel.Banner,
        notificationKey = key
    )

    @Test fun capacityDropsOldestUnseenEntry() {
        val queue = NoticeQueue(2)
        queue.offer(message("first")); queue.offer(message("second")); queue.offer(message("third"))
        assertEquals(2, queue.size)
        assertEquals("second", (queue.take()!!.content as IslandMode.Notification).title)
        assertEquals("third", (queue.take()!!.content as IslandMode.Notification).title)
        assertNull(queue.take())
    }

    @Test fun repeatedSenderHasOnlyNewestContent() {
        val queue = NoticeQueue()
        queue.offer(message("sender", "old")); queue.offer(message("sender", "updated"))
        assertEquals(1, queue.size)
        assertEquals("updated", (queue.take()!!.content as IslandMode.Notification).text)
    }

    @Test fun parkedNoticeDoesNotBlockUnseenSelection() {
        val queue = NoticeQueue()
        queue.offer(message("held"), park = true)
        queue.offer(message("new"))
        assertEquals("new", (queue.takeUnseen()!!.content as IslandMode.Notification).title)
        val held = queue.take()!!
        assertTrue(held.seen)
        assertEquals(IslandLevel.Compact, held.presentation)
    }

    @Test fun callsKeepOnlyParkedNotices() {
        val queue = NoticeQueue()
        queue.offer(message("held"), park = true); queue.offer(message("transient"))
        queue.discardTransients()
        assertEquals(1, queue.size)
        assertTrue(queue.take()!!.seen)
    }

    @Test fun shadeRemovalOnlyRemovesMatchingSeenKey() {
        val queue = NoticeQueue()
        queue.offer(message("held", key = "old"), park = true)
        queue.offer(message("next", key = "new"))
        queue.removeSeen("new")
        assertEquals(2, queue.size)
        queue.removeSeen("old")
        assertEquals(1, queue.size)
    }

    @Test fun transientStatusesCoalesceByModeType() {
        val queue = NoticeQueue()
        queue.offer(Notice(IslandMode.Charging(20), 1000, IslandLevel.Banner))
        queue.offer(Notice(IslandMode.Charging(80), 1000, IslandLevel.Banner))
        assertEquals(1, queue.size)
        assertEquals(IslandMode.Charging(80), queue.take()!!.content)
    }

    @Test fun parkingAtCapacityProtectsHeldNotice() {
        val queue = NoticeQueue(2)
        queue.offer(message("one")); queue.offer(message("two"))
        queue.offer(message("held"), park = true)
        assertEquals(2, queue.size)
        assertEquals("held", (queue.take()!!.content as IslandMode.Notification).title)
    }
}
