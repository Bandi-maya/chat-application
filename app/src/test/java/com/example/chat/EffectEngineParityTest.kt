package com.example.chat

import com.example.chat.ui.camera.EffectCategory
import com.example.chat.ui.camera.EffectEngine
import com.example.chat.ui.camera.EffectPerformanceTier
import com.example.chat.ui.camera.EffectRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behavior tests written from the Dart source's observable behavior in
 *  lib/features/camera/effects/effect_engine.dart and effect_registry.dart. */
class EffectEngineParityTest {

    @Test fun `registry holds all 60 effects with none first`() {
        assertEquals(60, EffectRegistry.allEffects.size)
        assertEquals("none", EffectRegistry.allEffects.first().id)
    }

    @Test fun `getById falls back to none`() {
        assertEquals("face_dog_filter", EffectRegistry.getById("face_dog_filter").id)
        assertEquals("none", EffectRegistry.getById("does_not_exist").id)
    }

    @Test fun `byId trims and degrades gracefully`() {
        assertEquals("face_dog_filter", EffectRegistry.byId(" face_dog_filter ").id)
        assertEquals("none", EffectRegistry.byId(null).id)
        assertEquals("none", EffectRegistry.byId("").id)
    }

    @Test fun `getByCategory filters`() {
        val beauty = EffectRegistry.getByCategory(EffectCategory.beauty)
        assertTrue(beauty.isNotEmpty())
        assertTrue(beauty.all { it.category == EffectCategory.beauty })
    }

    @Test fun `every color matrix has 20 elements`() {
        EffectRegistry.allEffects.filter { it.colorMatrix != null }.forEach {
            assertEquals(it.id, 20, it.colorMatrix!!.size)
        }
    }

    @Test fun `default active effect is none at intensity 1`() {
        val e = EffectEngine()
        assertEquals("none", e.activeEffect.id)
        assertEquals(1.0, e.intensity, 0.0)
        assertFalse(e.isEngineDisabled)
    }

    @Test fun `selectEffect resets intensity and tracks recents`() {
        val e = EffectEngine()
        val dog = EffectRegistry.getById("face_dog_filter")
        e.selectEffect(dog)
        assertEquals("face_dog_filter", e.activeEffect.id)
        assertEquals(dog.defaultIntensity, e.intensity, 0.0)
        assertEquals(listOf("face_dog_filter"), e.recentEffectIds)
        // selecting none does not touch recents
        e.selectEffect(EffectRegistry.getById("none"))
        assertEquals(listOf("face_dog_filter"), e.recentEffectIds)
        // re-selecting moves to front without duplicating
        e.selectEffect(dog)
        assertEquals(listOf("face_dog_filter"), e.recentEffectIds)
    }

    @Test fun `recents capped at 20`() {
        val e = EffectEngine()
        EffectRegistry.allEffects.filter { it.id != "none" }.forEach { e.selectEffect(it) }
        assertEquals(20, e.recentEffectIds.size)
    }

    @Test fun `setIntensity clamps to unit range`() {
        val e = EffectEngine()
        e.setIntensity(2.5)
        assertEquals(1.0, e.intensity, 0.0)
        e.setIntensity(-1.0)
        assertEquals(0.0, e.intensity, 0.0)
        e.setIntensity(0.5)
        assertEquals(0.5, e.intensity, 0.0)
    }

    @Test fun `toggleFavorite flips membership`() {
        val e = EffectEngine()
        e.toggleFavorite("face_dog_filter")
        assertTrue(e.favoriteEffectIds.contains("face_dog_filter"))
        e.toggleFavorite("face_dog_filter")
        assertFalse(e.favoriteEffectIds.contains("face_dog_filter"))
    }

    @Test fun `performance fallback forces none effect until restored`() {
        val e = EffectEngine()
        e.selectEffect(EffectRegistry.getById("face_dog_filter"))
        e.triggerPerformanceFallback()
        assertTrue(e.isEngineDisabled)
        assertEquals("none", e.activeEffect.id)
        assertNull(e.activeColorMatrix())
        e.restorePerformanceMode()
        assertFalse(e.isEngineDisabled)
        assertEquals("face_dog_filter", e.activeEffect.id)
    }

    @Test fun `interpolateMatrix lerps from identity`() {
        val target = MutableList(20) { 2.0 }
        val half = EffectEngine.interpolateMatrix(target, 0.5)
        // identity[i] + (2.0 - identity[i]) * 0.5
        for (i in 0 until 20) {
            val identity = if (i % 6 == 0) 1.0 else 0.0
            assertEquals(identity + (2.0 - identity) * 0.5, half[i], 1e-9)
        }
        val zero = EffectEngine.interpolateMatrix(target, 0.0)
        for (i in 0 until 20) {
            assertEquals(if (i % 6 == 0) 1.0 else 0.0, zero[i], 1e-9)
        }
    }

    @Test fun `activeColorMatrix is null for none and interpolated for active`() {
        val e = EffectEngine()
        assertNull(e.activeColorMatrix())
        e.selectEffect(EffectRegistry.getById("face_dog_filter"))
        val m = e.activeColorMatrix()
        assertNotNull(m)
        assertEquals(20, m!!.size)
    }

    @Test fun `animated overlay condition matches dart`() {
        val e = EffectEngine()
        assertFalse(e.isAnimatedOverlayActive) // none
        e.selectEffect(EffectRegistry.getById("face_dog_filter"))
        assertTrue(e.isAnimatedOverlayActive)
        e.triggerPerformanceFallback()
        assertFalse(e.isAnimatedOverlayActive)
    }

    @Test fun `storedColorMatrix resolves receiver look`() {
        assertNull(EffectRegistry.storedColorMatrix(null))
        assertNull(EffectRegistry.storedColorMatrix(mapOf("effect" to "none")))
        assertNull(EffectRegistry.storedColorMatrix(mapOf("effect" to "")))
        val m = EffectRegistry.storedColorMatrix(
            mapOf("effect" to "face_dog_filter", "effect_intensity" to "0.5"),
        )
        assertNotNull(m)
        assertEquals(20, m!!.size)
    }

    @Test fun `gpu tier effects marked`() {
        val gpu = EffectRegistry.allEffects.filter { it.tier == EffectPerformanceTier.gpuIntensive }
        assertEquals(3, gpu.size)
    }
}
