package com.example.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.ui.media.ImageSendCompressor
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Orientation fixtures. Expected display pixels come from the EXIF orientation table
 * (tag-1 bits: 001 flip H, 010 rotate 180, 100 flip along the main diagonal, combined
 * as in https://magnushoff.com/articles/jpeg-orientation/), written as plain coordinate
 * maps here, independent of android.graphics.Matrix. Stored image S is 8x4 blocks of
 * 4px (32x16, 8 distinct colours); display D(x,y) is read from S via the maps below.
 * Needs a device/emulator; not run yet.
 */
@RunWith(AndroidJUnit4::class)
class ImageSendCompressorExifTest {
    private val bw = 4
    private val W = 32
    private val H = 16
    private val colors = intArrayOf(
        Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.BLACK, Color.WHITE,
    )

    private fun stored(): Bitmap {
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        for (y in 0 until H) for (x in 0 until W) b.setPixel(x, y, colors[(x / bw) % 4 + 4 * ((y / bw) % 2)])
        return b
    }

    /** Source pixel (sx,sy) shown at display (x,y); display dims in the second pair. */
    private fun mapping(tag: Int, x: Int, y: Int): Pair<Int, Int> = when (tag) {
        1 -> x to y
        2 -> (W - 1 - x) to y
        3 -> (W - 1 - x) to (H - 1 - y)
        4 -> x to (H - 1 - y)
        5 -> y to x                              // transpose, display is HxW
        6 -> y to (H - 1 - x)                    // rotate 90 CW
        7 -> (W - 1 - y) to (H - 1 - x)          // transverse
        8 -> (W - 1 - y) to x                    // rotate 270 CW
        else -> error("tag")
    }

    private fun close(a: Int, b: Int) =
        abs(Color.red(a) - Color.red(b)) < 48 && abs(Color.green(a) - Color.green(b)) < 48 && abs(Color.blue(a) - Color.blue(b)) < 48

    private fun jpegWithTag(tag: Int): ByteArray {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val f = File.createTempFile("chaty_exif_", ".jpg", ctx.cacheDir)
        try {
            val bos = ByteArrayOutputStream()
            stored().compress(Bitmap.CompressFormat.JPEG, 100, bos)
            f.writeBytes(bos.toByteArray())
            ExifInterface(f.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, tag.toString()); saveAttributes()
            }
            return f.readBytes()
        } finally { f.delete() }
    }

    @Test fun allEightOrientationsAreBakedIntoPixels() {
        for (tag in 1..8) {
            val src = jpegWithTag(tag)
            val out = ImageSendCompressor.doCompress(src, 90, requireSmaller = false)
            assertNotNull("tag $tag", out)
            val d = BitmapFactory.decodeByteArray(out!!, 0, out.size)
            val swapped = tag in 5..8
            assertEquals("tag $tag width", if (swapped) H else W, d.width)
            assertEquals("tag $tag height", if (swapped) W else H, d.height)
            val s = stored()
            for (y in 2 until d.height step 4) for (x in 2 until d.width step 4) {
                val (sx, sy) = mapping(tag, x, y)
                assertTrue("tag $tag at $x,$y", close(d.getPixel(x, y), s.getPixel(sx, sy)))
            }
            // output carries no orientation tag that would rotate it a second time
            val exif = ExifInterface(java.io.ByteArrayInputStream(out))
            assertEquals("tag $tag exif cleared", ExifInterface.ORIENTATION_NORMAL,
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))
        }
    }

    @Test fun originalKeptForGifAlphaQuality100AndGarbage() {
        assertNull(ImageSendCompressor.compress("GIF89a....".toByteArray(), 85))
        assertNull(ImageSendCompressor.compress(ByteArray(100) { 1 }, 85))
        val png = ByteArrayOutputStream().also {
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        assertNull(ImageSendCompressor.compress(png, 85))
        assertNull(ImageSendCompressor.compress(jpegWithTag(1), 100))
    }
}
