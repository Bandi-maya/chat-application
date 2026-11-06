package com.example.chat

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chat.ui.chats.detail.EditorStroke
import com.example.chat.ui.chats.detail.renderEditedImage
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Local bitmap output only. No account/session/network/upload. */
@RunWith(AndroidJUnit4::class)
class ImageEditorPixelsTest {
    @Test fun defaultApplyPreservesImageAndLeavesOriginalUntouched() {
        val original = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(60, 80, 100)) }
        val edited = renderEditedImage(original, 0f, 1f, emptyList())
        assertEquals(original.getPixel(40, 40), edited.getPixel(40, 40))
        assertNotSame(original, edited)
        assertEquals(Color.rgb(60, 80, 100), original.getPixel(40, 40))
    }
    @Test fun drawingAndBrightnessAreInExportedPixelsNotJustPreview() {
        val original = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(60, 80, 100)) }
        //4px brush at500px source gives a solid interior sample;100px made a
        //0.8px antialiased line with no fully covered pixel at the asserted center.
        val stroke = EditorStroke(Color.RED.toLong(), listOf(Offset(0.1f, 0.5f), Offset(0.9f, 0.5f)))
        val edited = renderEditedImage(original, 0.1f, 1f, listOf(stroke))
        assertTrue(Color.red(edited.getPixel(250, 250)) > 240)
        assertTrue(Color.green(edited.getPixel(250, 250)) < 15)
        assertTrue(Color.red(edited.getPixel(50, 50)) > Color.red(original.getPixel(50, 50)))
        assertEquals(Color.rgb(60, 80, 100), original.getPixel(250, 250))
    }
}
