package com.example.chat

import com.example.chat.ui.camera.EffectEngine
import com.example.chat.ui.camera.EffectRegistry
import com.example.chat.ui.camera.pixelateBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioral proofs for the batch availability contract: which effects actually
 * render through EffectEngine, and that renderer-less entries are provably inert
 * (they back the disabled "Not available yet" state in both pickers).
 */
class CameraEffectAvailabilityTest {

    private val unavailableIds = listOf(
        "bg_portrait_blur", "bg_virtual_office", "bg_gradient_studio",
    )

    @Test fun `none renders no matrix and no overlay`() {
        val e = EffectEngine()
        assertNull(e.activeColorMatrix())
        assertFalse(e.isAnimatedOverlayActive)
    }

    @Test fun `unavailable effects are inert when selected`() {
        val e = EffectEngine()
        unavailableIds.forEach { id ->
            e.selectEffect(EffectRegistry.getById(id))
            assertEquals(id, e.activeEffect.id)
            assertNull("$id must render no color matrix", e.activeColorMatrix())
            assertFalse("$id must not stack an overlay", e.isAnimatedOverlayActive)
        }
    }

    @Test fun `every render-backed effect produces engine output`() {
        val e = EffectEngine()
        val working = EffectRegistry.allEffects.filter { it.id != "none" && it.id !in unavailableIds }
        assertEquals(96, working.size)
        working.forEach { effect ->
            e.selectEffect(effect)
            val renders = e.activeColorMatrix() != null || e.isAnimatedOverlayActive ||
                effect.isFaceTrackingRequired || effect.shaderKey != null
            assertTrue("${effect.id} has no engine output path", renders)
            if (effect.colorMatrix != null) {
                assertEquals("${effect.id} matrix size", 20, e.activeColorMatrix()!!.size)
            }
        }
    }

    @Test fun `face-flagged effects are exactly the six lens-mapped originals`() {
        // CameraCaptureScreen.lensIdForEffect maps these six ids to lenses; a
        // face-flagged effect outside this set would dead-tap at runtime.
        val flagged = EffectRegistry.allEffects.filter { it.isFaceTrackingRequired }.map { it.id }.sorted()
        assertEquals(
            listOf(
                "face_angel_halo", "face_dog_filter", "face_hearts_float",
                "face_neon_crown", "face_sparkles", "face_sunglasses_aviator",
            ),
            flagged,
        )
    }

    @Test fun `pixel retro carries the original pixelate shader path`() {
        val pixel = EffectRegistry.getById("face_pixel_retro")
        assertEquals("pixelate", pixel.shaderKey)
        assertNull(pixel.colorMatrix)
        assertFalse(pixel.isFaceTrackingRequired)
        // Not a matrix effect: the engine emits no color matrix for it; the
        // pixelate path renders via EffectRender/ImageFxBaker instead.
        val e = EffectEngine()
        e.selectEffect(pixel)
        assertNull(e.activeColorMatrix())
        // Bake-side matrix fallback is identity, never a wrong color shift.
        val identity = com.example.chat.ui.camera.ImageFxBaker.lerpedMatrix(pixel, 0.7)
        assertEquals(1f, identity[0], 1e-6f)
        assertEquals(1f, identity[6], 1e-6f)
        assertEquals(0f, identity[1], 1e-6f)
        // Intensity maps to the block grid: higher intensity = chunkier.
        assertTrue(pixelateBlocks(1.0) < pixelateBlocks(0.0))
    }

    @Test fun `effect favorites persist through the store seam`() {
        val store = mutableMapOf<String, List<String>>()
        val prefs = object : com.example.chat.ui.camera.LensStringListStore {
            override fun getStringList(key: String) = store[key]
            override fun setStringList(key: String, value: List<String>) { store[key] = value }
        }
        val e = EffectEngine(prefs = prefs)
        e.toggleFavorite("face_dog_filter")
        e.toggleFavorite("glow_rainbow_sheen")
        assertTrue(store[EffectEngine.FAVORITES_KEY]!!.containsAll(listOf("face_dog_filter", "glow_rainbow_sheen")))
        e.toggleFavorite("face_dog_filter")
        val restored = EffectEngine(prefs = prefs)
        assertEquals(setOf("glow_rainbow_sheen"), restored.favoriteEffectIds)
    }

    @Test fun `v2 full-frame grades carry real matrices without face flags`() {
        listOf("glow_rainbow_sheen", "beauty_golden_veil", "beauty_sunkissed", "glow_sunset_haze", "glow_forest_mist").forEach { id ->
            val effect = EffectRegistry.getById(id)
            assertEquals(id, effect.id)
            assertFalse("$id must not require face tracking", effect.isFaceTrackingRequired)
            assertNotNull("$id needs a real matrix", effect.colorMatrix)
            assertEquals(20, effect.colorMatrix!!.size)
        }
    }

    @Test fun `performance fallback blanks output and restore brings it back`() {
        val e = EffectEngine()
        e.selectEffect(EffectRegistry.getById("face_dog_filter"))
        assertNotNull(e.activeColorMatrix())
        e.triggerPerformanceFallback()
        assertNull(e.activeColorMatrix())
        assertFalse(e.isAnimatedOverlayActive)
        e.restorePerformanceMode()
        assertNotNull(e.activeColorMatrix())
    }

    @Test fun `intensity clamps to unit range and interpolates`() {
        val e = EffectEngine()
        e.selectEffect(EffectRegistry.getById("face_dog_filter"))
        e.setIntensity(2.5)
        assertEquals(1.0, e.intensity, 0.0)
        e.setIntensity(-1.0)
        assertEquals(0.0, e.intensity, 0.0)
        // intensity 0 is the identity matrix, not a null render
        val identity = e.activeColorMatrix()
        assertNotNull(identity)
        assertEquals(1.0, identity!![0], 1e-9)
        assertEquals(1.0, identity[5], 1e-9)
        assertEquals(0.0, identity[1], 1e-9)
    }
}
