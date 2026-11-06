package com.example.chat

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.ui.camera.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic rasters only. No real camera, picker, account or network. */
@RunWith(AndroidJUnit4::class)
class InlineDocumentProcessorTest {
    @Test fun perspectiveCropRectifiesSyntheticPaperAndKeepsOriginalBytes() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=File.createTempFile("synthetic_paper_", ".png",context.cacheDir)
        val image=Bitmap.createBitmap(600,800,Bitmap.Config.ARGB_8888)
        val quad=PaperQuad(PaperPoint(.15f,.12f),PaperPoint(.85f,.18f),PaperPoint(.78f,.9f),PaperPoint(.22f,.82f))
        Canvas(image).apply {
            drawColor(Color.BLUE)
            drawPath(Path().apply { quad.points.forEachIndexed { i,p -> if(i==0)moveTo(p.x*599,p.y*799)else lineTo(p.x*599,p.y*799) };close() },Paint().apply{color=Color.WHITE})
            drawCircle(300f,400f,75f,Paint().apply{color=Color.RED})
        }
        source.outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
        val before=source.readBytes();var derived:File?=null
        try {
            derived=File(InlineDocumentProcessor.crop(source.absolutePath,quad,File(context.cacheDir,"paper_test")))
            assertNotEquals(source.absolutePath,derived.absolutePath)
            assertArrayEquals(before,source.readBytes())
            val result=BitmapFactory.decodeFile(derived.absolutePath)
            try {
                assertTrue("retain full-size crop",result.width>400 && result.height>550)
                val center=result.getPixel(result.width/2,result.height/2)
                assertTrue("red landmark preserved",Color.red(center)>200 && Color.blue(center)<50)
                listOf(.15f to .15f,.85f to .15f,.15f to .85f,.85f to .85f).forEach{(x,y)->
                    val c=result.getPixel((x*result.width).toInt(),(y*result.height).toInt())
                    assertTrue("paper interior is white",Color.red(c)>230 && Color.green(c)>230 && Color.blue(c)>230)
                }
            }finally{result.recycle()}
        } finally {derived?.delete();source.delete()}
    }
    @Test fun invalidQuadDoesNotAlterOriginalOrCreateOutput() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=File.createTempFile("synthetic_invalid_", ".png",context.cacheDir).apply{writeText("original sentinel")}
        val folder=File(context.cacheDir,"invalid_paper_${System.nanoTime()}")
        val before=source.readBytes()
        try {
            try {InlineDocumentProcessor.crop(source.absolutePath,PaperQuad(PaperPoint(0f,0f),PaperPoint(1f,1f),PaperPoint(1f,0f),PaperPoint(0f,1f)),folder);fail("Must reject crossed quad")}
            catch(_:IllegalArgumentException){}
            assertArrayEquals(before,source.readBytes());assertFalse(folder.exists())
        }finally{source.delete();folder.deleteRecursively()}
    }
}
