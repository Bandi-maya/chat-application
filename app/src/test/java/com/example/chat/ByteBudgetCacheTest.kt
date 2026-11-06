package com.example.chat

import com.example.chat.data.links.LinkPreviewService
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ByteBudgetCacheTest {

    @Test fun evictsLeastRecentlyUsedOverBudget() {
        val cache = LinkPreviewService.ByteBudgetCache(10)
        cache.put("a", ByteArray(4))
        cache.put("b", ByteArray(4))
        cache.get("a") // touch: b is now eldest
        cache.put("c", ByteArray(4)) // 12 > 10, evicts b
        assertArrayEquals(ByteArray(4), cache.get("a"))
        assertNull(cache.get("b"))
        assertArrayEquals(ByteArray(4), cache.get("c"))
        assertTrue(cache.totalBytes() <= 10)
    }

    @Test fun neverCachesOversizeEntry() {
        val cache = LinkPreviewService.ByteBudgetCache(8)
        cache.put("big", ByteArray(9))
        assertNull(cache.get("big"))
        assertEquals(0, cache.totalBytes())
    }

    @Test fun replacingKeyAdjustsBudget() {
        val cache = LinkPreviewService.ByteBudgetCache(6)
        cache.put("a", ByteArray(4))
        cache.put("a", ByteArray(2))
        assertEquals(2, cache.totalBytes())
    }

    @Test fun clearEmpties() {
        val cache = LinkPreviewService.ByteBudgetCache(10)
        cache.put("a", ByteArray(4))
        cache.clear()
        assertNull(cache.get("a"))
        assertEquals(0, cache.totalBytes())
    }
}
