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
        // Two-tone frame (left red, right blue, hard vertical edge at x=320): a real
        // mask+blur bake softens the edge, so applied=true is verifiable instead of
        // letting an unfiltered passthrough pass.
        val b=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888)
        val px=IntArray(640*480){i->if(i%640<320)Color.rgb(255,0,0)else Color.rgb(0,0,255)}
        b.setPixels(px,0,640,0,0,640,480)
        source.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
        var output:File?=null
        try{
            // Real segmenter inference on a synthetic bitmap. The mask content on a
            // person-less frame is not meaningful, so orientation is NOT asserted here;
            // the contract is: never crash, output at the original size, and the branch
            // that ran is recorded and verified against honest behavior.
            val baked=ImageFxBaker.bakeEffectIntoFile(context,source.absolutePath,EffectRegistry.byId("bg_portrait_blur"),0.8)
            output=File(baked.path)
            assertTrue(output.exists());assertTrue(output.length()>0)
            val result=BitmapFactory.decodeFile(output.absolutePath)
            try{
                assertEquals(640,result.width);assertEquals(480,result.height)
                // Record which branch actually ran so a pass is attributable in the shard log.
                android.util.Log.i("SegmentBakeProof","branch="+(if(baked.applied)"applied"else"fail-open"))
                var mixedEdge=false
                for(x in 316..323){val c=result.getPixel(x,240)
                    val red=Color.red(c);val blue=Color.blue(c)
                    if(red>10&&blue>10)mixedEdge=true}
                if(baked.applied){
                    assertTrue("applied=true must show real pipeline output: the two-tone edge must be softened by the mask blur",mixedEdge)
                }else{
                    assertFalse("fail-open returns the original pixels, never a processed or blank frame",mixedEdge)
                    val l=result.getPixel(100,240);val r=result.getPixel(540,240)
                    assertTrue(Color.red(l)>240&&Color.blue(l)<15)
                    assertTrue(Color.blue(r)>240&&Color.red(r)<15)
                }
            }finally{result.recycle()}
        }finally{output?.delete();source.delete()}
    }

}
