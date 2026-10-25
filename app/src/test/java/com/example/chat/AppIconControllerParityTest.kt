package com.example.chat

import com.example.chat.ui.core.controllers.BrandIconSource
import com.example.chat.ui.core.controllers.LauncherIconVariant
import com.example.chat.ui.core.controllers.id
import com.example.chat.ui.core.controllers.launcherIconVariantFromId
import com.example.chat.ui.core.controllers.subtitle
import com.example.chat.ui.core.controllers.title
import com.example.chat.ui.settings.appicon.AppIconController
import com.example.chat.ui.settings.appicon.AppIconPreferenceStore
import com.example.chat.ui.settings.appicon.CustomIconInputSource
import com.example.chat.ui.settings.appicon.CustomIconPreset
import com.example.chat.ui.settings.appicon.CustomIconProcessor
import com.example.chat.ui.settings.appicon.LauncherIconBridge
import com.example.chat.ui.settings.appicon.LauncherIconPickException
import com.example.chat.ui.settings.appicon.coverFitDestination
import com.example.chat.ui.settings.appicon.iconEditorGestureStep
import androidx.compose.ui.geometry.Offset
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakePrefs : AppIconPreferenceStore {
    val map = mutableMapOf<String, String>()
    override fun getString(key: String): String? = map[key]
    override fun putString(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
}

private class FakeBridge : LauncherIconBridge {
    var currentAlias: String? = "bird"
    var setResult: String? = null
    var pickResult: String? = null
    var pickError: Exception? = null
    override suspend fun getCurrentLauncherIcon(): String? = currentAlias
    override suspend fun setLauncherIcon(alias: String): String? =
        setResult ?: alias.also { currentAlias = it }
    override suspend fun pickCustomIconImage(source: CustomIconInputSource): String? {
        pickError?.let { throw it }
        return pickResult
    }
}

private class FakeProcessor(private val dir: File) : CustomIconProcessor {
    override suspend fun persistSquarePng(sourcePng: ByteArray, presetId: String?): File {
        require(sourcePng.isNotEmpty())
        val f = File(dir, "${presetId}.launcher.png")
        f.writeBytes(sourcePng)
        return f
    }
}

class AppIconControllerParityTest {

    private fun fixture(
        bridge: FakeBridge? = FakeBridge(),
        prefs: FakePrefs = FakePrefs(),
    ): Triple<AppIconController, FakePrefs, FakeBridge?> {
        val dir = createTempDir("appicon")
        val controller = AppIconController(
            bridge = bridge,
            prefs = prefs,
            processor = FakeProcessor(dir),
            nowMillis = { 1_700_000_000_000L },
            nowMicros = { 1_700_000_000_000_000L },
        )
        return Triple(controller, prefs, bridge)
    }

    // ---- Variant metadata (Dart LauncherIconVariantMetadata) ----

    @Test
    fun `fromId maps aliases and falls back to bird`() {
        assertEquals(LauncherIconVariant.bird, launcherIconVariantFromId("bird"))
        assertEquals(LauncherIconVariant.bird, launcherIconVariantFromId("swift"))
        assertEquals(LauncherIconVariant.warm, launcherIconVariantFromId("original"))
        assertEquals(LauncherIconVariant.outline, launcherIconVariantFromId("minimal"))
        assertEquals(LauncherIconVariant.obsidian, launcherIconVariantFromId("midnight"))
        assertEquals(LauncherIconVariant.glass, launcherIconVariantFromId("bubble"))
        assertEquals(LauncherIconVariant.signal, launcherIconVariantFromId("ocean"))
        assertEquals(LauncherIconVariant.fold, launcherIconVariantFromId("violet"))
        assertEquals(LauncherIconVariant.warm, launcherIconVariantFromId("warm"))
        assertEquals(LauncherIconVariant.bird, launcherIconVariantFromId(null))
        assertEquals(LauncherIconVariant.bird, launcherIconVariantFromId("nonsense"))
    }

    @Test
    fun `titles and ids are verbatim`() {
        assertEquals("Swift Flight", LauncherIconVariant.bird.title)
        assertEquals("Warm Signature", LauncherIconVariant.warm.title)
        assertEquals("Spatial Glass", LauncherIconVariant.glass.title)
        assertEquals("fold", LauncherIconVariant.fold.id)
        assertEquals(
            "Warm presence pulse geometry with core Chaty mark",
            LauncherIconVariant.signal.subtitle,
        )
    }

    // ---- CustomIconPreset json ----

    @Test
    fun `preset json round trip and invalid shapes`() {
        val preset = CustomIconPreset("a", "/tmp/x.png", 42)
        val decoded = CustomIconPreset.fromJson(Json.parseToJsonElement(preset.toJson().toString()))
        assertEquals(preset, decoded)
        assertNull(CustomIconPreset.fromJson(Json.parseToJsonElement("""{"id":"","path":"/x"}""")))
        assertNull(CustomIconPreset.fromJson(Json.parseToJsonElement("""{"id":"a"}""")))
        assertNull(CustomIconPreset.fromJson(Json.parseToJsonElement("""[1,2]""")))
        // createdAt non-int -> 0
        val noCreated = CustomIconPreset.fromJson(Json.parseToJsonElement("""{"id":"a","path":"/x"}"""))
        assertEquals(0L, noCreated!!.createdAt)
    }

    // ---- Controller behavior ----

    @Test
    fun `initialize syncs native alias over stale pref`() = runBlocking {
        val prefs = FakePrefs()
        prefs.putString(AppIconController.LAUNCHER_PREFERENCE_KEY, "warm")
        val bridge = FakeBridge().apply { currentAlias = "glass" }
        val (controller) = fixture(bridge = bridge, prefs = prefs)
        controller.initialize()
        assertTrue(controller.initialized)
        assertEquals(LauncherIconVariant.glass, controller.launcherIcon)
        assertEquals("glass", prefs.map[AppIconController.LAUNCHER_PREFERENCE_KEY])
    }

    @Test
    fun `applyLauncherIcon persists and resets brand source`() = runBlocking {
        val (controller, prefs) = fixture()
        controller.initialize()
        assertTrue(controller.applyLauncherIcon(LauncherIconVariant.obsidian))
        assertEquals(LauncherIconVariant.obsidian, controller.launcherIcon)
        assertEquals(BrandIconSource.bundled, controller.brandIconSource)
        assertEquals("obsidian", prefs.map[AppIconController.LAUNCHER_PREFERENCE_KEY])
        assertEquals("bundled", prefs.map[AppIconController.BRAND_SOURCE_PREFERENCE_KEY])
    }

    @Test
    fun `applyLauncherIcon rolls back and sets error on native mismatch`() = runBlocking {
        val bridge = FakeBridge().apply { setResult = "warm" } // mismatch vs request
        val (controller) = fixture(bridge = bridge)
        controller.initialize()
        assertFalse(controller.applyLauncherIcon(LauncherIconVariant.fold))
        assertEquals(LauncherIconVariant.bird, controller.launcherIcon)
        assertEquals(
            "Could not apply that launcher icon. Your previous icon is still active.",
            controller.lastError,
        )
    }

    @Test
    fun `pick error maps camera permission message`() = runBlocking {
        val bridge = FakeBridge().apply {
            pickError = LauncherIconPickException("camera_permission_denied", "denied")
        }
        val (controller) = fixture(bridge = bridge)
        assertNull(controller.pickCustomIconImage(CustomIconInputSource.CAMERA))
        assertEquals(
            "Camera permission is required only when you choose Camera.",
            controller.lastError,
        )
    }

    @Test
    fun `pick error maps generic platform message`() = runBlocking {
        val bridge = FakeBridge().apply {
            pickError = LauncherIconPickException("other", "boom")
        }
        val (controller) = fixture(bridge = bridge)
        assertNull(controller.pickCustomIconImage(CustomIconInputSource.PHOTOS))
        assertEquals("boom", controller.lastError)
    }

    @Test
    fun `save custom icon persists library and activates preset`() = runBlocking {
        val (controller, prefs) = fixture()
        controller.initialize()
        assertTrue(controller.saveCustomBrandIcon(byteArrayOf(1, 2, 3)))
        assertEquals(BrandIconSource.custom, controller.brandIconSource)
        assertEquals("custom_1700000000000000", controller.activeCustomPresetId)
        assertTrue(prefs.map[AppIconController.CUSTOM_LIBRARY_PREFERENCE_KEY]!!.contains("custom_1700000000000000"))
        assertEquals(
            "custom",
            prefs.map[AppIconController.BRAND_SOURCE_PREFERENCE_KEY],
        )
        assertTrue(controller.hasSavedCustomIcon)
    }

    @Test
    fun `activate missing preset sets exact error`() = runBlocking {
        val (controller) = fixture()
        controller.initialize()
        assertFalse(controller.activateCustomPreset("nope"))
        assertEquals("That custom icon is no longer available.", controller.lastError)
    }

    @Test
    fun `remove active preset falls back to last remaining`() = runBlocking {
        var micros = 1_700_000_000_000_000L
        val dir = createTempDir("appicon2")
        val controller = AppIconController(
            bridge = FakeBridge(),
            prefs = FakePrefs(),
            processor = FakeProcessor(dir),
            nowMillis = { 1_700_000_000_000L },
            nowMicros = { micros },
        )
        controller.initialize()
        controller.saveCustomBrandIcon(byteArrayOf(1))
        val first = controller.activeCustomPresetId!!
        micros = 1_700_000_000_000_001L
        controller.saveCustomBrandIcon(byteArrayOf(2))
        val second = controller.activeCustomPresetId!!
        assertTrue(first != second)
        controller.removeCustomPreset(second)
        // Dart: remaining presets -> activate the LAST existing one.
        assertEquals(first, controller.activeCustomPresetId)
        assertEquals(BrandIconSource.custom, controller.brandIconSource)
    }

    @Test
    fun `remove last preset resets to bundled and clears keys`() = runBlocking {
        val (controller, prefs) = fixture()
        controller.initialize()
        controller.saveCustomBrandIcon(byteArrayOf(1))
        controller.removeCustomPreset(controller.activeCustomPresetId!!)
        assertEquals(BrandIconSource.bundled, controller.brandIconSource)
        assertNull(controller.activeCustomPresetId)
        assertNull(prefs.map[AppIconController.CUSTOM_LIBRARY_PREFERENCE_KEY])
        assertNull(prefs.map[AppIconController.ACTIVE_CUSTOM_PRESET_PREFERENCE_KEY])
        assertEquals("bundled", prefs.map[AppIconController.BRAND_SOURCE_PREFERENCE_KEY])
    }

    @Test
    fun `custom source with missing preset falls back to bundled on init`() = runBlocking {
        val prefs = FakePrefs()
        prefs.putString(AppIconController.BRAND_SOURCE_PREFERENCE_KEY, "custom")
        prefs.putString(AppIconController.ACTIVE_CUSTOM_PRESET_PREFERENCE_KEY, "gone")
        val (controller) = fixture(prefs = prefs)
        controller.initialize()
        assertEquals(BrandIconSource.bundled, controller.brandIconSource)
    }

    // ---- Editor math ----

    @Test
    fun `editor gesture clamps zoom 1 to 6 with margin bounds`() {
        // Zoom out below minScale: scale stays 1, offset clamps into
        // [-margin, margin].
        val (s1, o1) = iconEditorGestureStep(
            offset = Offset.Zero, centroid = Offset(100f, 100f),
            pan = Offset(500f, -500f), scale = 1f, zoom = 0.5f,
            viewportWidth = 360f, viewportHeight = 360f, margin = 80f,
        )
        assertEquals(1f, s1, 1e-6f)
        assertEquals(80f, o1.x, 1e-6f)
        assertEquals(-80f, o1.y, 1e-6f)

        // Zoom above maxScale: clamps at 6.
        val (s2, _) = iconEditorGestureStep(
            offset = Offset.Zero, centroid = Offset(0f, 0f),
            pan = Offset.Zero, scale = 5f, zoom = 2f,
            viewportWidth = 360f, viewportHeight = 360f, margin = 80f,
        )
        assertEquals(6f, s2, 1e-6f)

        // Focal point fixed: centroid maps to itself.
        val (s3, o3) = iconEditorGestureStep(
            offset = Offset.Zero, centroid = Offset(180f, 90f),
            pan = Offset.Zero, scale = 1f, zoom = 3f,
            viewportWidth = 360f, viewportHeight = 360f, margin = 80f,
        )
        assertEquals(3f, s3, 1e-6f)
        assertEquals(180f, o3.x + 180f * s3, 1e-4f)
        assertEquals(90f, o3.y + 90f * s3, 1e-4f)
    }

    @Test
    fun `cover fit fills square and centers overflow`() {
        // Wide image: height fills, width overflows symmetrically.
        val wide = coverFitDestination(200f, 100f, 100f)
        assertEquals(-50f, wide.left, 1e-6f)
        assertEquals(0f, wide.top, 1e-6f)
        assertEquals(200f, wide.width(), 1e-6f)
        assertEquals(100f, wide.height(), 1e-6f)
        // Tall image: width fills, height overflows symmetrically.
        val tall = coverFitDestination(100f, 400f, 100f)
        assertEquals(0f, tall.left, 1e-6f)
        assertEquals(-150f, tall.top, 1e-6f)
        assertEquals(100f, tall.width(), 1e-6f)
        assertEquals(400f, tall.height(), 1e-6f)
        // Square: exact fit.
        val sq = coverFitDestination(50f, 50f, 100f)
        assertEquals(0f, sq.left, 1e-6f)
        assertEquals(0f, sq.top, 1e-6f)
        assertEquals(100f, sq.width(), 1e-6f)
    }
}
