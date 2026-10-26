package com.example.chat.ui.camera

import org.junit.Assert.*
import org.junit.Test

class CaptureCropGeometryTest {
    @Test fun squareCentersWithoutStretching() { assertArrayEquals(intArrayOf(0,500,3000,3000), centerCropBounds(3000,4000,1f)) }
    @Test fun standardKeepsWholeFourThreeImage() { assertArrayEquals(intArrayOf(0,0,3000,4000), centerCropBounds(3000,4000,.75f)) }
    @Test fun wideIsBoundedAndCentered() { assertArrayEquals(intArrayOf(375,0,2250,4000), centerCropBounds(3000,4000,9f/16f)) }
    @Test fun tinyAndExtremeRatiosNeverEmpty() {
        for (w in listOf(1,2,3000)) for (h in listOf(1,2,4000)) for (ratio in listOf(.01f,.5f,1f,2f,100f)) {
            val b = centerCropBounds(w,h,ratio)
            assertTrue(b[0]>=0 && b[1]>=0 && b[2]>0 && b[3]>0)
            assertTrue(b[0]+b[2]<=w && b[1]+b[3]<=h)
        }
    }
    @Test fun invalidInputsRefuse() {
        for (ratio in listOf(0f,-1f,Float.NaN,Float.POSITIVE_INFINITY)) {
            try { centerCropBounds(10,10,ratio); fail("Must reject $ratio") } catch (_: IllegalArgumentException) { }
        }
    }
}
