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
        val original = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(60, 80, 100)) }
        val stroke = EditorStroke(Color.RED.toLong(), listOf(Offset(0.1f, 0.5f), Offset(0.9f, 0.5f)))
        val edited = renderEditedImage(original, 0.1f, 1f, listOf(stroke))
        assertTrue(Color.red(edited.getPixel(50, 50)) > 240)
        assertTrue(Color.green(edited.getPixel(50, 50)) < 15)
        assertTrue(Color.red(edited.getPixel(10, 10)) > Color.red(original.getPixel(10, 10)))
        assertEquals(Color.rgb(60, 80, 100), original.getPixel(50, 50))
    }
}
