package com.example.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.ui.camera.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CameraFullResolutionEffectTest {
    @Test fun optionalEffectNeverSilentlyShrinksAbove1440AndKeepsOriginal() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=File.createTempFile("synthetic_fullfx_", ".png",context.cacheDir)
        val b=Bitmap.createBitmap(1800,1200,Bitmap.Config.ARGB_8888);b.eraseColor(Color.RED)
        source.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
        val before=source.readBytes();var output:File?=null
        try{
            val baked=ImageFxBaker.bakeEffectIntoFile(context,source.absolutePath,EffectRegistry.byId("none"),1.0);assertTrue(baked.applied);output=File(baked.path)
            val out=output!!;assertNotEquals(source.absolutePath,out.absolutePath);assertArrayEquals(before,source.readBytes())
            val result=BitmapFactory.decodeFile(out.absolutePath)
            try{assertEquals(1800,result.width);assertEquals(1200,result.height);val c=result.getPixel(900,600);assertTrue(Color.red(c)>240&&Color.blue(c)<15)}finally{result.recycle()}
        }finally{output?.delete();source.delete()}
    }

    @Test fun segmentBakeRunsRealMaskPipelineAndFailsOpenHonestly() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=File.createTempFile("synthetic_segment_", ".png",context.cacheDir)
        val b=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888);b.eraseColor(Color.GREEN)
        source.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
        var output:File?=null
        try{
            // Real segmenter inference on a synthetic bitmap. The mask content on
            // a solid-color frame is not meaningful, so orientation is NOT asserted
            // here; the contract is: never crash, always produce an output at the
            // original size, and applied=false means fail-open drew the original.
            val baked=ImageFxBaker.bakeEffectIntoFile(context,source.absolutePath,EffectRegistry.byId("bg_portrait_blur"),0.8)
            output=File(baked.path)
            assertTrue(output.exists());assertTrue(output.length()>0)
            val result=BitmapFactory.decodeFile(output.absolutePath)
            try{
                assertEquals(640,result.width);assertEquals(480,result.height)
                if(!baked.applied){
                    val px=result.getPixel(320,240)
                    assertTrue("fail-open keeps the original pixels, never a blank frame",Color.green(px)>200&&Color.red(px)<15)
                }
            }finally{result.recycle()}
        }finally{output?.delete();source.delete()}
    }
}
