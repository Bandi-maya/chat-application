package com.example.chat

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic document only. No camera/permissions/account/send. */
@RunWith(AndroidJUnit4::class)
class InlineDocumentReviewTest {
    @Test fun cornersCropAndRetakeRemainLocalAndKeepOriginal() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val device=UiDevice.getInstance(instrumentation)
        val source=File.createTempFile("synthetic_review_", ".png",context.cacheDir)
        val raster=Bitmap.createBitmap(400,600,Bitmap.Config.ARGB_8888)
        raster.eraseColor(Color.DKGRAY)
        for(y in 60 until 540)for(x in 40 until 360)raster.setPixel(x,y,Color.WHITE)
        source.outputStream().use{raster.compress(Bitmap.CompressFormat.PNG,100,it)};raster.recycle()
        val before=source.readBytes()
        val folder=File(context.cacheDir,"paper_crops")
        val existing=folder.listFiles()?.map{it.absolutePath}?.toSet()?:emptySet()
        val output=File(context.getExternalFilesDir(null),"screenshots").apply{mkdirs()}
        fun capture(name:String){instrumentation.uiAutomation.takeScreenshot()?.let{b->File(output,"inline-document-$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()};device.dumpWindowHierarchy(File(output,"inline-document-$name.xml"))}
        try {
            for(crop in listOf(false,true)) {
                ActivityScenario.launch<CameraOpeningProofActivity>(Intent(context,CameraOpeningProofActivity::class.java).putExtra("syntheticDocumentPath",source.absolutePath)).use { scenario ->
                    assertTrue(device.wait(Until.hasObject(By.desc("Adjust paper corners")),10000))
                    Thread.sleep(250);capture(if(crop)"before-crop"else"before-retake")
                    val frame=device.findObject(By.desc("Adjust paper corners")).visibleBounds
                    device.swipe(frame.centerX()-frame.width()/3,frame.top+frame.height()/5,frame.centerX()-frame.width()/4,frame.top+frame.height()/4,20)
                    capture("adjusted-$crop")
                    val resultVisible = if(crop){device.findObject(By.text("Crop paper")).click();device.wait(Until.hasObject(By.textStartsWith("Crop ready:")),10000)}
                    else {device.findObject(By.text("Retake")).click();device.wait(Until.hasObject(By.text("Retake kept original")),3000)}
                    capture("result-$crop")
                    scenario.onActivity { activity ->
                        assertEquals("Retake callback count",if(crop)0 else 1,activity.retakeCallbacks)
                        assertEquals("Crop callback count",if(crop)1 else 0,activity.cropCallbacks)
                        if(crop)assertTrue("callback points to derived crop",activity.croppedPath?.let{File(it).isFile}==true)
                    }
                    assertTrue("Result text visible after actual local callback; see result-$crop screenshot/XML",resultVisible)
                    assertArrayEquals(before,source.readBytes())
                    assertEquals(context.packageName,device.currentPackageName)
                }
            }
            assertTrue("new derived crop exists",folder.listFiles()?.any{it.absolutePath !in existing && it.length()>0}==true)
        } finally {source.delete();folder.listFiles()?.filter{it.absolutePath !in existing}?.forEach{it.delete()}}
    }
}
