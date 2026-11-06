package com.example.chat
import com.example.chat.core.media.ImageTextOverlayPolicy as P
import org.junit.Assert.*
import org.junit.Test
class ImageTextOverlayPolicyTest {
 @Test fun textBoundedAndSingleLine() { assertEquals("a b c",P.text("a\nb\rc")); assertEquals(120,P.text("x".repeat(500)).length) }
 @Test fun nonFiniteSettingsSafe() { assertEquals(0.1f,P.x(Float.NaN));assertEquals(0.3f,P.y(Float.POSITIVE_INFINITY));assertEquals(0.07f,P.scale(Float.NaN)) }
 @Test fun longTextScalesToRemainingImageWidth() { assertEquals(19f,P.fittedTextSize(100f,0.6f,50f,100f),0.0001f) }
 @Test fun shortTextDoesNotUpscale() { assertEquals(10f,P.fittedTextSize(100f,0f,10f,20f)) }
 @Test fun zeroImageWidthIsSafe() { assertEquals(0f,P.fittedTextSize(0f,0f,10f,20f)) }
 @Test fun positionsStayInsideImage() { assertEquals(0.9f,P.x(1f));assertEquals(0.1f,P.y(-1f));assertEquals(0.15f,P.scale(9f)) }
}
