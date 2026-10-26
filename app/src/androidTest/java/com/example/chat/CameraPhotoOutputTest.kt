package com.example.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.ui.camera.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic raster only, no camera/user file/network. */
@RunWith(AndroidJUnit4::class)
class CameraPhotoOutputTest {
    @Test fun derivedSquareHasCorrectPixelsAndNeverChangesOriginal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = File.createTempFile("synthetic_original", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        for (y in 100 until 500) for (x in 0 until 400) bitmap.setPixel(x,y,Color.RED)
        original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
        val before = original.readBytes()
        var result: File? = null
        try {
            result = File(CameraPhotoOutput.process(context,original.absolutePath, CameraCaptureOptions(aspect = CaptureAspect.square), 2f/3f))
            assertNotEquals(original.absolutePath, result.absolutePath)
            assertArrayEquals(before, original.readBytes())
            val output = BitmapFactory.decodeFile(result.absolutePath)
            assertEquals(400,output.width); assertEquals(400,output.height)
            val center = output.getPixel(200,200)
            assertTrue(Color.red(center)>240 && Color.blue(center)<15)
            output.recycle()
        } finally { result?.delete(); original.delete() }
    }

    @Test fun fullAspectDoesNotSilentlyDownsample() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = File.createTempFile("synthetic_size", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(900,1200,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,100,it) }; bitmap.recycle()
        val before = original.readBytes()
        var result: File? = null
        try {
            result = File(CameraPhotoOutput.process(context,original.absolutePath,CameraCaptureOptions(),.75f))
            val output = BitmapFactory.decodeFile(result.absolutePath)
            assertEquals(900,output.width); assertEquals(1200,output.height)
            assertArrayEquals(before,original.readBytes()); output.recycle()
        } finally { result?.delete(); original.delete() }
    }
    @Test fun unreadableOriginalIsNeverModifiedOrReplaced() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File.createTempFile("synthetic_invalid", ".jpg", context.cacheDir)
        source.writeText("not an image")
        val before = source.readBytes()
        try {
            try {
                CameraPhotoOutput.process(context,source.absolutePath,CameraCaptureOptions(watermark=true),.75f)
                fail("Invalid raster must refuse processing")
            } catch (_: IllegalStateException) { }
            assertArrayEquals(before,source.readBytes())
        } finally { source.delete() }
    }

}
