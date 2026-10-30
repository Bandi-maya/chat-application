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
            output=File(ImageFxBaker.bakeEffectIntoFile(context,source.absolutePath,EffectRegistry.byId("none"),1.0).path)
            val out=output!!;assertNotEquals(source.absolutePath,out.absolutePath);assertArrayEquals(before,source.readBytes())
            val result=BitmapFactory.decodeFile(out.absolutePath)
            try{assertEquals(1800,result.width);assertEquals(1200,result.height);val c=result.getPixel(900,600);assertTrue(Color.red(c)>240&&Color.blue(c)<15)}finally{result.recycle()}
        }finally{output?.delete();source.delete()}
    }
}
