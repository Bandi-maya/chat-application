package com.example.chat

import com.example.chat.data.media.ImageSendPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageSendPolicyTest {

    @Test fun `auto tier adapts quality to uplink within Signal bounds`() {
        assertEquals(50, ImageSendPolicy.autoQuality(50))      // 2G
        assertEquals(87, ImageSendPolicy.autoQuality(20_000))  // fast Wi-Fi
        assertEquals(70, ImageSendPolicy.autoQuality(null))    // unknown = middle
        for (kbps in listOf(1, 99, 100, 499, 500, 1999, 2000, 7999, 8000)) {
            val q = ImageSendPolicy.autoQuality(kbps)
            assertTrue(q in 50..87)
        }
    }

    @Test fun `tiers match the matrix`() {
        val auto = ImageSendPolicy.tier(ImageSendPolicy.SendMode.AUTO, 1000)
        assertEquals(1600, auto.maxDimensionPx)
        assertTrue(auto.stripExif && auto.reencode)
        val hd = ImageSendPolicy.tier(ImageSendPolicy.SendMode.HD)
        assertEquals(4096, hd.maxDimensionPx)
        assertEquals(92, hd.jpegQuality)
        val file = ImageSendPolicy.tier(ImageSendPolicy.SendMode.FILE)
        assertFalse(file.stripExif)
        assertFalse(file.reencode)
    }

    @Test fun `sample size keeps both dimensions above target`() {
        assertEquals(1, ImageSendPolicy.sampleSize(1200, 900, 1600))
        assertEquals(2, ImageSendPolicy.sampleSize(4000, 3000, 1600))
        assertEquals(4, ImageSendPolicy.sampleSize(8000, 6000, 1600))
    }

    @Test fun `target size preserves aspect and never upscales`() {
        assertEquals(1600 to 1200, ImageSendPolicy.targetSize(4000, 3000, 1600))
        assertEquals(1200 to 1600, ImageSendPolicy.targetSize(3000, 4000, 1600))
        assertEquals(800 to 600, ImageSendPolicy.targetSize(800, 600, 1600))
    }
}
