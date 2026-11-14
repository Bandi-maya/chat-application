package com.example.chat

import com.example.chat.ui.core.system.SampledBitmapDecoder
import org.junit.Assert.assertEquals
import org.junit.Test

class SampledBitmapDecoderTest {
    @Test fun powerOfTwoSamplingKeepsEnoughPixelsForFinalScale() {
        assertEquals(8, SampledBitmapDecoder.sampleSize(8000, 6000, 1000))
        assertEquals(4, SampledBitmapDecoder.sampleSize(6000, 8000, 1600))
        assertEquals(1, SampledBitmapDecoder.sampleSize(32, 32, 128))
        assertEquals(1, SampledBitmapDecoder.sampleSize(8000, 6000, 0))
    }
    @Test fun shortEdgeSamplingLeavesEnoughPixelsForSquareCrop() {
        assertEquals(4, SampledBitmapDecoder.sampleSize(3000, 3000, 512))
        assertEquals(2, SampledBitmapDecoder.sampleSize(3000, 3000, 1024))
    }
}
