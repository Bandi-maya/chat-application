package com.example.chat
import android.content.Intent
import android.graphics.Bitmap
import java.io.File
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class ProfileHostRouteProofTest {
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val device get()=UiDevice.getInstance(inst)
    private fun capture(name:String){
        val dir=File(inst.targetContext.getExternalFilesDir(null),"screenshots").apply{mkdirs()}
        val image=inst.uiAutomation.takeScreenshot()?:error("No screenshot pixels")
        try{File(dir,"profile-host-$name.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{image.recycle()}
        device.dumpWindowHierarchy(File(dir,"profile-host-$name.xml"))
    }
    private fun click(s:String){assertTrue(device.wait(Until.hasObject(By.text(s)),5000));device.findObject(By.text(s)).click();inst.waitForIdleSync()}
    private fun awaitSyntheticBlueAvatar(){
        val deadline=android.os.SystemClock.uptimeMillis()+8000
        var blue=0
        while(android.os.SystemClock.uptimeMillis()<deadline){
            inst.waitForIdleSync()
            val image=inst.uiAutomation.takeScreenshot()?:error("No preview pixels")
            try{
                blue=0
                // Synthetic picker creates only a solid BLUE avatar; no other header fixture
                // content uses this colour. Restrict to central header, above the form.
                for(y in image.height/4 until image.height/2)for(x in image.width/4 until image.width*3/4){
                    val c=image.getPixel(x,y)
                    if(android.graphics.Color.blue(c)>220&&android.graphics.Color.red(c)<35&&android.graphics.Color.green(c)<35)blue++
                }
            }finally{image.recycle()}
            if(blue>=100)return
            android.os.SystemClock.sleep(50)
        }
        capture("local-draft-preview-load-failed")
        fail("Synthetic local avatar must render blue pixels before acceptance; observed $blue")
    }
    private fun pick(){assertTrue(device.wait(Until.hasObject(By.desc("Change avatar")),10000));device.findObject(By.desc("Change avatar")).click();click("Pick / Choose from Gallery");assertTrue(device.wait(Until.hasObject(By.text("Save")),5000));awaitSyntheticBlueAvatar()}
    @Test fun actualHostLocalPickThenDiscardHasZeroPosts(){
        ActivityScenario.launch<ProfileHostFixtureActivity>(Intent(inst.targetContext,ProfileHostFixtureActivity::class.java)).use{s->
            var h:ProfileHostFixtureActivity?=null;s.onActivity{h=it};pick();capture("local-draft-preview");assertEquals(1,h!!.picks);assertEquals(0,h!!.uploads)
            device.findObject(By.desc("Back")).click();click("Discard");assertEquals(0,h!!.uploads);assertEquals(0,h!!.saves);assertEquals(1,h!!.backs)
        }
    }
    @Test fun actualHostPartialFailureRetryUploadsOnce(){
        ActivityScenario.launch<ProfileHostFixtureActivity>(Intent(inst.targetContext,ProfileHostFixtureActivity::class.java).putExtra("failFirst",true)).use{s->
            var h:ProfileHostFixtureActivity?=null;s.onActivity{h=it};pick();click("Save")
            assertTrue(device.wait(Until.hasObject(By.textContains("Discard will not undo")),8000));assertEquals(1,h!!.uploads);assertEquals(1,h!!.saves);capture("partial-save-warning")
            click("Save");assertEquals(1,h!!.uploads);assertEquals(2,h!!.saves)
        }
    }
    @Test fun actualHostAccountReplacementDiscardsDraftWithoutUpload(){
        ActivityScenario.launch<ProfileHostFixtureActivity>(Intent(inst.targetContext,ProfileHostFixtureActivity::class.java)).use{s->
            var h:ProfileHostFixtureActivity?=null;s.onActivity{h=it};pick();s.onActivity{it.changeScope()};inst.waitForIdleSync()
            assertEquals(0,h!!.uploads);assertEquals(0,h!!.saves)
            device.findObject(By.desc("Back")).click();inst.waitForIdleSync();assertFalse(device.hasObject(By.text("Discard changes?")))
        }
    }
    @Test fun actualHostRapidSaveAndBackDuringHeldUploadAreGated(){
        ActivityScenario.launch<ProfileHostFixtureActivity>(Intent(inst.targetContext,ProfileHostFixtureActivity::class.java).putExtra("holdUpload",true)).use{s->
            var h:ProfileHostFixtureActivity?=null;s.onActivity{h=it};pick()
            val save=device.findObject(By.text("Save"));val bounds=save.visibleBounds
            // Two taps without waiting for recomposition. Synchronous isSaving must gate the second.
            device.click(bounds.centerX(),bounds.centerY());device.click(bounds.centerX(),bounds.centerY())
            assertTrue(h!!.uploadEntered.await(5,java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(1,h!!.uploads);assertEquals(0,h!!.saves)
            inst.waitForIdleSync()
            if(android.os.Build.VERSION.SDK_INT>=33)assertTrue(inst.uiAutomation.clearCache())
            capture("save-busy-before-disabled-assert")
            assertTrue(device.wait(Until.hasObject(By.desc("Change avatar")),5000))
            assertFalse("Picker icon disabled while Save owns draft",device.findObject(By.desc("Change avatar")).isEnabled)
            device.findObject(By.desc("Change avatar")).click();inst.waitForIdleSync();assertEquals(1,h!!.picks)
            device.findObject(By.desc("Back")).click();device.pressBack();inst.waitForIdleSync()
            assertEquals(0,h!!.backs);assertFalse(device.hasObject(By.text("Discard changes?")))
            assertEquals(1,h!!.uploads);assertEquals(0,h!!.saves)
            capture("save-busy-gated")
            s.onActivity{it.releaseUpload.complete(Unit)}
            assertTrue(device.wait(Until.hasObject(By.text("Profile updated successfully.")),8000))
            assertEquals(1,h!!.uploads);assertEquals(1,h!!.saves)
        }
    }
}
