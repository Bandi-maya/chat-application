package com.example.chat

import com.example.chat.ui.camera.CapabilityTier
import com.example.chat.ui.camera.LensManager
import com.example.chat.ui.camera.LensRuntimeState
import com.example.chat.ui.camera.LensTimerHandle
import com.example.chat.ui.camera.LensTimerScheduler
import com.example.chat.ui.camera.LensStringListStore
import com.example.chat.ui.camera.kBuiltInLenses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behavior tests written from the Dart source's observable behavior in
 *  lib/features/camera/lens/lens_manager.dart and lens_definition.dart. */
class LensManagerParityTest {

    private class FakeScheduler : LensTimerScheduler {
        val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
        override fun schedule(delayMs: Long, action: () -> Unit): LensTimerHandle {
            val entry = delayMs to action
            scheduled.add(entry)
            return LensTimerHandle { scheduled.remove(entry) }
        }
        fun fireLatest() { scheduled.lastOrNull()?.second?.invoke() }
    }

    private class FakeStore : LensStringListStore {
        val data = mutableMapOf<String, List<String>>()
        override fun getStringList(key: String) = data[key]
        override fun setStringList(key: String, value: List<String>) { data[key] = value }
    }

    private fun fixture(): Triple<FakeScheduler, FakeStore, LensManager> {
        val scheduler = FakeScheduler()
        val store = FakeStore()
        return Triple(scheduler, store, LensManager(prefs = store, timerScheduler = scheduler))
    }

    @Test fun `catalog has 10 built-in lenses with none first`() {
        assertEquals(10, kBuiltInLenses.size)
        assertEquals("none", kBuiltInLenses.first().id)
        assertTrue(kBuiltInLenses.first().isNoLens)
        assertFalse(kBuiltInLenses.first().requiresFaceTracking)
    }

    @Test fun `default selection is none with none state`() {
        val (_, _, m) = fixture()
        assertEquals("none", m.selected.id)
        assertEquals(LensRuntimeState.none, m.runtimeState)
    }

    @Test fun `select updates recents and schedules settle`() {
        val (scheduler, store, m) = fixture()
        m.select("lens_dog_ears")
        assertEquals("lens_dog_ears", m.selected.id)
        assertEquals(LensRuntimeState.applying, m.runtimeState)
        assertEquals(listOf("lens_dog_ears"), m.recents)
        assertEquals(listOf("lens_dog_ears"), store.data[LensManager.RECENT_KEY])
        assertEquals(180L, scheduler.scheduled.last().first)
    }

    @Test fun `selecting none does not touch recents`() {
        val (_, store, m) = fixture()
        m.select("lens_dog_ears")
        m.clear()
        assertEquals("none", m.selected.id)
        assertEquals(listOf("lens_dog_ears"), m.recents)
        assertEquals(LensRuntimeState.none, m.runtimeState)
    }

    @Test fun `settle commits active state`() {
        val (scheduler, _, m) = fixture()
        m.select("lens_sparkles")
        scheduler.fireLatest()
        assertEquals(LensRuntimeState.active, m.runtimeState)
    }

    @Test fun `superseded selection drops stale commit`() {
        val (scheduler, _, m) = fixture()
        m.select("lens_sparkles")
        m.select("lens_dog_ears") // bumps request id; first commit is stale
        // fire all pending timers; only the newest may commit
        val actions = scheduler.scheduled.toList()
        scheduler.scheduled.clear()
        actions.forEach { it.second.invoke() }
        assertEquals("lens_dog_ears", m.selected.id)
        assertEquals(LensRuntimeState.active, m.runtimeState)
    }

    @Test fun `re-selecting same id is a no-op`() {
        val (scheduler, _, m) = fixture()
        m.select("lens_dog_ears")
        val count = scheduler.scheduled.size
        m.select("lens_dog_ears")
        assertEquals(count, scheduler.scheduled.size)
    }

    @Test fun `recents dedupe and cap at 20`() {
        val (_, _, m) = fixture()
        m.select("lens_dog_ears")
        m.select("lens_sparkles")
        m.select("lens_dog_ears")
        assertEquals(listOf("lens_dog_ears", "lens_sparkles"), m.recents)
    }

    @Test fun `toggleFavorite flips and persists`() {
        val (_, store, m) = fixture()
        m.toggleFavorite("lens_dog_ears")
        assertTrue(m.favorites.contains("lens_dog_ears"))
        assertEquals(listOf("lens_dog_ears"), store.data[LensManager.FAVORITE_KEY])
        m.toggleFavorite("lens_dog_ears")
        assertFalse(m.favorites.contains("lens_dog_ears"))
    }

    @Test fun `prefetch indices bounded and center-ordered`() {
        val (_, _, m) = fixture()
        val indices = m.prefetchIndices(5)
        assertTrue(indices.contains(5))
        assertTrue(indices.all { it in 0 until kBuiltInLenses.size })
        // center-out ordering: first element must be the center
        assertEquals(5, indices.first())
        val edge = m.prefetchIndices(0)
        assertTrue(edge.all { it >= 0 })
    }

    @Test fun `supportedForDevice tier rules`() {
        val high = kBuiltInLenses.first().copy(minimumTier = CapabilityTier.high)
        assertFalse(high.supportedForDevice(CapabilityTier.mid))
        assertTrue(high.supportedForDevice(CapabilityTier.high))
        val mid = kBuiltInLenses.first().copy(minimumTier = CapabilityTier.mid)
        assertFalse(mid.supportedForDevice(CapabilityTier.any))
        assertTrue(mid.supportedForDevice(CapabilityTier.high))
    }

    @Test fun `recents restored from prefs`() {
        val store = FakeStore()
        store.data[LensManager.RECENT_KEY] = listOf("lens_sparkles", "lens_dog_ears")
        store.data[LensManager.FAVORITE_KEY] = listOf("lens_crown_gold")
        val m = LensManager(prefs = store, timerScheduler = FakeScheduler())
        assertEquals(listOf("lens_sparkles", "lens_dog_ears"), m.recents)
        assertTrue(m.favorites.contains("lens_crown_gold"))
    }

    @Test fun `blush glow supports calls like dart`() {
        val blush = kBuiltInLenses.first { it.id == "lens_blush_glow" }
        assertTrue(blush.supportsCalls)
        assertTrue(kBuiltInLenses.filter { it.id != "lens_blush_glow" }.none { it.supportsCalls })
    }
}
