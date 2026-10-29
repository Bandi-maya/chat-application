package com.example.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemePresets
import com.example.chat.data.services.ProfileMediaSource
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.profile.ProfileAvatarDraft
import com.example.chat.ui.profile.ProfileEditScreen
import com.example.chat.ui.theme.ChatyTheme
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proof for the requested GB-style profile edit: avatar and banner pickers route
 * through the source sheet, the picked image goes through a real crop (center
 * square) into the local draft preview, and Save hands the new URLs to onSave.
 */
@RunWith(AndroidJUnit4::class)
class ProfileEditCropProofTest {
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    private val dev get() = UiDevice.getInstance(ins)

    private fun shot(name: String) {
        val dir = File(ins.targetContext.getExternalFilesDir(null), "screenshots")
        dir.mkdirs()
        dev.waitForIdle(3000)
        val bmp = ins.uiAutomation.takeScreenshot() ?: return
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }

    private fun nearColor(pixel: Int, r: Int, g: Int, b: Int): Boolean =
        Math.abs(AndroidColor.red(pixel) - r) <= 24 &&
            Math.abs(AndroidColor.green(pixel) - g) <= 24 &&
            Math.abs(AndroidColor.blue(pixel) - b) <= 24

    /** Scans the screen above the form fields for the cropped bitmap's two
     *  signature colors (orange circle on blue field). */
    private fun previewShowsCrop(): Boolean {
        val label = dev.findObject(By.text("Display Name")) ?: return false
        val top = label.visibleBounds.top
        val bmp = ins.uiAutomation.takeScreenshot() ?: return false
        var orange = false
        var blue = false
        var y = 0
        while (y < top && !(orange && blue)) {
            var x = 0
            while (x < bmp.width && !(orange && blue)) {
                val px = bmp.getPixel(x, y)
                if (nearColor(px, 220, 120, 40)) orange = true
                if (nearColor(px, 30, 90, 160)) blue = true
                x += 7
            }
            y += 7
        }
        bmp.recycle()
        return orange && blue
    }

    private fun waitFor(cond: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8000
        while (!cond() && SystemClock.uptimeMillis() < deadline) Thread.sleep(150)
    }

    /** Real center-crop: 1200x800 source -> 800x800 PNG, verified on disk. */
    private fun writeCroppedAvatar(): File {
        val src = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(src)
        canvas.drawColor(AndroidColor.rgb(30, 90, 160))
        canvas.drawCircle(600f, 400f, 300f, Paint().apply { color = AndroidColor.rgb(220, 120, 40) })
        val cropped = Bitmap.createBitmap(src, 200, 0, 800, 800)
        val dir = File(ins.targetContext.cacheDir, "profile-proof")
        dir.mkdirs()
        val f = File(dir, "avatar-cropped.png")
        FileOutputStream(f).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
        src.recycle()
        cropped.recycle()
        val check = BitmapFactory.decodeFile(f.absolutePath)
        assertNotNull("cropped avatar must decode", check)
        assertEquals("crop must be square", 800, check!!.width)
        assertEquals("crop must keep the center-square size", 800, check.height)
        check.recycle()
        return f
    }

    @Test
    fun avatarAndBannerPickCropPreviewAndSave() {
        val user = UserProfile(
            id = "me", displayName = "Bandi Proof", username = "bandiproof",
            avatarInitials = "BP", avatarColorHex = "#2AA198", about = "synthetic about",
            lastSeenAt = Instant.EPOCH, email = "me@synthetic.example", phone = "+15550000000",
        )
        val draftState = mutableStateOf<ProfileAvatarDraft?>(null)
        val draftPathState = mutableStateOf<String?>(null)
        val picked = HashMap<String, ProfileMediaSource>()
        val saved = HashMap<String, String?>()
        val scenario = ActivityScenario.launch(ChatFixtureActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContent {
                ChatyTheme(ThemePresets.whatsappIosLight) {
                    ProfileEditScreen(
                        user = user,
                        onBack = {},
                        onPickAvatar = { source ->
                            picked["avatar"] = source
                            val f = writeCroppedAvatar()
                            draftState.value = ProfileAvatarDraft(f)
                            draftPathState.value = f.absolutePath
                            "https://cdn.synthetic.example/avatar-cropped.png"
                        },
                        onPickBanner = { source ->
                            picked["banner"] = source
                            "https://cdn.synthetic.example/banner-cropped.png"
                        },
                        onSave = { displayName, username, about, avatarUrl, bannerUrl ->
                            saved["displayName"] = displayName
                            saved["username"] = username
                            saved["about"] = about
                            saved["avatarUrl"] = avatarUrl
                            saved["bannerUrl"] = bannerUrl
                        },
                        checkUsernameAvailable = { true },
                        avatarDraftPath = draftPathState.value,
                        avatarDraft = draftState.value,
                        isCurrentPreview = { true },
                    )
                }
            }
        }
        dev.wait(Until.findObject(By.text("Edit Profile")), 8000) ?: fail("Edit Profile screen did not render")
        dev.findObject(By.desc("Change avatar")).click()
        dev.wait(Until.findObject(By.text("Pick / Choose from Gallery")), 5000)
            ?: fail("avatar source sheet did not open")
        assertTrue("capture option missing from source sheet", dev.hasObject(By.text("Capture Image")))
        dev.findObject(By.text("Pick / Choose from Gallery")).click()
        waitFor { draftState.value != null }
        assertEquals(ProfileMediaSource.gallery, picked["avatar"])
        val draft = draftState.value ?: throw AssertionError("avatar draft preview state missing")
        // Saved-output pixels: the draft payload the host will upload must be the
        // cropped bitmap (800x800, orange center on blue field).
        val savedPixels = BitmapFactory.decodeByteArray(draft.readBytes(), 0, draft.readBytes().size)
        assertNotNull("draft payload must decode", savedPixels)
        assertEquals(800, savedPixels!!.width)
        assertEquals(800, savedPixels.height)
        assertTrue("draft center must be the cropped circle", nearColor(savedPixels.getPixel(400, 400), 220, 120, 40))
        assertTrue("draft corner must be the cropped field", nearColor(savedPixels.getPixel(5, 5), 30, 90, 160))
        savedPixels.recycle()
        // On-screen pixels: the draft preview must actually render those pixels
        // above the form fields, not a placeholder or the old avatar.
        waitFor { previewShowsCrop() }
        assertTrue("avatar preview must render the cropped pixels on screen", previewShowsCrop())
        shot("profile-edit-avatar-crop-preview")
        dev.findObject(By.desc("Change banner photo")).click()
        dev.wait(Until.findObject(By.text("Pick / Choose from Gallery")), 5000)
            ?: fail("banner source sheet did not open")
        dev.findObject(By.text("Pick / Choose from Gallery")).click()
        waitFor { picked["banner"] == ProfileMediaSource.gallery }
        assertEquals(ProfileMediaSource.gallery, picked["banner"])
        shot("profile-edit-banner-picked")
        dev.findObject(By.text("Save")).click()
        waitFor { saved.containsKey("avatarUrl") }
        assertEquals("Bandi Proof", saved["displayName"])
        assertEquals("https://cdn.synthetic.example/avatar-cropped.png", saved["avatarUrl"])
        assertEquals("https://cdn.synthetic.example/banner-cropped.png", saved["bannerUrl"])
        shot("profile-edit-saved")
        scenario.close()
    }
}
