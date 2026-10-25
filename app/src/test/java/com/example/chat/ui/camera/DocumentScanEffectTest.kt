package com.example.chat.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies the document-scan effect constant and bake lerp against
 *  camera_capture_screen.dart values. */
class DocumentScanEffectTest {

    private val dartMatrix = listOf(
        0.48, 0.94, 0.08, 0.0, -45.0,
        0.48, 0.94, 0.08, 0.0, -45.0,
        0.48, 0.94, 0.08, 0.0, -45.0,
        0.0, 0.0, 0.0, 1.0, 0.0,
    )

    @Test
    fun documentScanConstantMatchesDart() {
        assertEquals("document_scan", DOCUMENT_SCAN_EFFECT.id)
        assertEquals("Document Scan", DOCUMENT_SCAN_EFFECT.name)
        assertEquals(EffectCategory.color, DOCUMENT_SCAN_EFFECT.category)
        assertEquals(20, DOCUMENT_SCAN_EFFECT.colorMatrix!!.size)
        for (i in 0 until 20) {
            assertEquals(dartMatrix[i], DOCUMENT_SCAN_EFFECT.colorMatrix!![i], 1e-9)
        }
    }

    @Test
    fun bakeLerpMatchesDart() {
        // Dart: _identity[i] + (matrix[i] - _identity[i]) * intensity
        val lerped = EffectEngine.interpolateMatrix(DOCUMENT_SCAN_EFFECT.colorMatrix!!, 0.5)
        for (i in 0 until 20) {
            val identity = if (i % 6 == 0) 1.0 else 0.0
            val expected = identity + (dartMatrix[i] - identity) * 0.5
            assertEquals(expected, lerped[i], 1e-9)
        }
        // intensity 1 -> exact matrix; 0 -> identity
        val full = EffectEngine.interpolateMatrix(DOCUMENT_SCAN_EFFECT.colorMatrix!!, 1.0)
        assertEquals(0.48, full[0], 1e-9)
        val zero = EffectEngine.interpolateMatrix(DOCUMENT_SCAN_EFFECT.colorMatrix!!, 0.0)
        for (i in 0 until 20) assertEquals(if (i % 6 == 0) 1.0 else 0.0, zero[i], 1e-9)
    }
}
