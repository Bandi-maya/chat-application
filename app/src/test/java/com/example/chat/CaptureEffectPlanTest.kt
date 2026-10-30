package com.example.chat

import com.example.chat.ui.camera.ImageFxBaker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proofs for the capture-time effect contract (ImageFxBaker.resolveCaptureEffectPlan
 * + tagAfterBake): which captures bake, what the metadata tag says afterwards,
 * and the fail-open rule that never claims an unrendered filter.
 */
class CaptureEffectPlanTest {

    private fun plan(
        story: Boolean,
        effectId: String = "face_dog_filter",
        renderPath: Boolean = true,
        shader: Boolean = false,
        intensity: Double = 0.7,
    ) = ImageFxBaker.resolveCaptureEffectPlan(story, effectId, renderPath, shader, intensity)

    @Test fun `story matrix capture bakes and keeps the tag`() {
        val p = plan(story = true)
        assertTrue(p.bake)
        assertEquals("face_dog_filter", p.tagOnSuccess)
        assertEquals(0.7, p.intensityOnSuccess, 0.0)
        val (tag, i) = ImageFxBaker.tagAfterBake(p, applied = true, currentId = "face_dog_filter", currentIntensity = 0.7)
        assertEquals("face_dog_filter", tag)
        assertEquals(0.7, i, 0.0)
    }

    @Test fun `chat matrix capture skips the bake and keeps metadata for receivers`() {
        val p = plan(story = false)
        assertFalse(p.bake)
        val (tag, i) = ImageFxBaker.tagAfterBake(p, applied = true, currentId = "face_dog_filter", currentIntensity = 0.7)
        assertEquals("face_dog_filter", tag)
        assertEquals(0.7, i, 0.0)
    }

    @Test fun `shader effects bake in both story and chat and clear the tag`() {
        listOf(true, false).forEach { story ->
            val p = plan(story = story, effectId = "bg_portrait_blur", renderPath = true, shader = true)
            assertTrue("shader must bake in story=$story", p.bake)
            assertEquals("none", p.tagOnSuccess)
            assertEquals(1.0, p.intensityOnSuccess, 0.0)
            val (tag, i) = ImageFxBaker.tagAfterBake(p, applied = true, currentId = "bg_portrait_blur", currentIntensity = 0.7)
            assertEquals("none", tag)
            assertEquals(1.0, i, 0.0)
        }
    }

    @Test fun `failed bake never claims the filter`() {
        val p = plan(story = true, effectId = "bg_virtual_office", shader = true)
        val (tag, i) = ImageFxBaker.tagAfterBake(p, applied = false, currentId = "bg_virtual_office", currentIntensity = 0.7)
        assertEquals("none", tag)
        assertEquals(1.0, i, 0.0)
        // same rule for matrix effects
        val pm = plan(story = true)
        val (tagM, iM) = ImageFxBaker.tagAfterBake(pm, applied = false, currentId = "face_dog_filter", currentIntensity = 0.7)
        assertEquals("none", tagM)
        assertEquals(1.0, iM, 0.0)
    }

    @Test fun `none effect and renderer-less selections never bake`() {
        assertFalse(plan(story = true, effectId = "none").bake)
        assertFalse(plan(story = true, effectId = "bg_portrait_blur", renderPath = false).bake)
        assertFalse(plan(story = false, effectId = "none", shader = true).bake)
    }

    @Test fun `bake result carries the applied flag`() {
        val ok = ImageFxBaker.BakedEffect("/tmp/a.jpg", applied = true)
        val failedOpen = ImageFxBaker.BakedEffect("/tmp/b.jpg", applied = false)
        assertTrue(ok.applied)
        assertFalse(failedOpen.applied)
    }
}
