package com.example.chat.ui.settings.appicon

import com.example.chat.ui.core.controllers.BrandIconSource
import com.example.chat.ui.core.controllers.LauncherIconVariant
import com.example.chat.ui.core.controllers.androidAlias
import com.example.chat.ui.core.controllers.id
import com.example.chat.ui.core.controllers.launcherIconVariantFromId
import com.example.chat.ui.core.controllers.AppIconControllerContract
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Exact port of lib/ui/core/controllers/app_icon_controller.dart. */

enum class CustomIconInputSource { PHOTOS, CAMERA;

    /** Dart passes source.name ('photos'/'camera') to the MethodChannel. */
    val channelName: String get() = name.lowercase()
}

data class CustomIconPreset(
    val id: String,
    val path: String,
    val createdAt: Long,
) {
    /** Dart: path.isNotEmpty && File(path).existsSync() */
    val exists: Boolean get() = path.isNotEmpty() && File(path).exists()

    fun toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("path", path)
        put("createdAt", createdAt)
    }

    companion object {
        /** Dart: returns null unless id and path are non-empty strings;
         *  createdAt is int-or-0. */
        fun fromJson(value: Any?): CustomIconPreset? {
            if (value !is JsonObject) return null
            val id = value["id"]?.jsonPrimitive?.content
            val path = value["path"]?.jsonPrimitive?.content
            if (id.isNullOrEmpty() || path.isNullOrEmpty()) return null
            return CustomIconPreset(
                id = id,
                path = path,
                createdAt = value["createdAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            )
        }
    }
}

/** Native seam for MethodChannel('chaty/launcher_icon') (integration owner:
 *  activity-alias manifest entries + MainActivity handler). Semantics mirror
 *  the Dart channel contract exactly. */
interface LauncherIconBridge {
    /** -> 'getCurrentLauncherIcon' => active alias id or null. */
    suspend fun getCurrentLauncherIcon(): String?

    /** -> 'setLauncherIcon' {'alias': alias} => confirmed alias; the Dart
     *  controller throws when the returned alias differs from the requested
     *  one, so an implementation MUST return the requested alias on success
     *  and throw (PlatformException equivalent) on failure. */
    suspend fun setLauncherIcon(alias: String): String?

    /** -> 'pickCustomIconImage' {'source': 'photos'|'camera'} => prepared
     *  temp file path or null (cancelled). Implementations throw
     *  [LauncherIconPickException] with code 'camera_permission_denied' for
     *  the camera-permission case so the controller can map the exact Dart
     *  error message. */
    suspend fun pickCustomIconImage(source: CustomIconInputSource): String?
}

class LauncherIconPickException(val code: String?, message: String?) : Exception(message)

/** Persistence seam; production wires SharedPreferences with these exact
 *  keys (values are strings). */
interface AppIconPreferenceStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

/** Image-processing seam for CustomAppIconProcessor.persistSquarePng.
 *  Android impl: [AndroidCustomIconProcessor]. */
interface CustomIconProcessor {
    suspend fun persistSquarePng(sourcePng: ByteArray, presetId: String?): File
}

class AppIconController(
    private val bridge: LauncherIconBridge?,
    private val prefs: AppIconPreferenceStore,
    private val processor: CustomIconProcessor,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    /** Dart DateTime.now().microsecondsSinceEpoch for preset ids. */
    private val nowMicros: () -> Long = { System.currentTimeMillis() * 1000 },
) : AppIconControllerContract {
    companion object {
        const val LAUNCHER_PREFERENCE_KEY = "chaty_launcher_icon_v1"
        const val BRAND_SOURCE_PREFERENCE_KEY = "chaty_brand_source_v1"
        const val CUSTOM_BRAND_PATH_PREFERENCE_KEY = "chaty_custom_brand_icon_path_v1"
        const val CUSTOM_LIBRARY_PREFERENCE_KEY = "chaty_custom_icon_library_v2"
        const val ACTIVE_CUSTOM_PRESET_PREFERENCE_KEY = "chaty_active_custom_icon_v2"
    }

    // ChangeNotifier convention (see ui/camera controllers).
    private val listeners = mutableListOf<() -> Unit>()
    override fun addListener(listener: () -> Unit) { listeners += listener }
    override fun removeListener(listener: () -> Unit) { listeners -= listener }
    private fun notifyListeners() = listeners.toList().forEach { it() }

    private var _launcherIcon = LauncherIconVariant.bird
    private var _brandIconSource = BrandIconSource.bundled
    private val _customIconPresets = mutableListOf<CustomIconPreset>()
    private var _activeCustomPresetId: String? = null
    private var _customBrandIconPath: String? = null
    private var _initialized = false
    private var _isApplyingLauncherIcon = false
    private var _isSavingCustomBrandIcon = false
    private var _isRefreshingNativeState = false
    private var _lastError: String? = null

    override val launcherIcon: LauncherIconVariant get() = _launcherIcon
    override val brandIconSource: BrandIconSource get() = _brandIconSource
    val customBrandIconPath: String? get() = _customBrandIconPath
    val activeCustomPresetId: String? get() = _activeCustomPresetId
    val customIconPresets: List<CustomIconPreset> get() = _customIconPresets.toList()

    /** Dart: any preset with an existing file. */
    val hasSavedCustomIcon: Boolean get() = _customIconPresets.any { it.exists }
    val initialized: Boolean get() = _initialized
    val isApplyingLauncherIcon: Boolean get() = _isApplyingLauncherIcon
    val isSavingCustomBrandIcon: Boolean get() = _isSavingCustomBrandIcon
    override val isBusy: Boolean get() = _isApplyingLauncherIcon || _isSavingCustomBrandIcon
    override val lastError: String? get() = _lastError

    val activeCustomPreset: CustomIconPreset?
        get() {
            val id = _activeCustomPresetId ?: return null
            return _customIconPresets.firstOrNull { it.id == id && it.exists }
        }

    override suspend fun initialize() {
        if (_initialized) return
        _launcherIcon = launcherIconVariantFromId(prefs.getString(LAUNCHER_PREFERENCE_KEY))
        _brandIconSource = if (prefs.getString(BRAND_SOURCE_PREFERENCE_KEY) == BrandIconSource.custom.name) BrandIconSource.custom else BrandIconSource.bundled

        loadCustomLibrary()
        migrateLegacyCustomIcon()
        cleanMissingPresets()

        _activeCustomPresetId = prefs.getString(ACTIVE_CUSTOM_PRESET_PREFERENCE_KEY)
        syncActiveCustomPath()

        if (_brandIconSource == BrandIconSource.custom && activeCustomPreset == null) {
            _brandIconSource = BrandIconSource.bundled
            prefs.putString(BRAND_SOURCE_PREFERENCE_KEY, BrandIconSource.bundled.name)
        }

        refreshNativeLauncherState(notify = false)
        _initialized = true
        notifyListeners()
    }

    private fun loadCustomLibrary() {
        _customIconPresets.clear()
        val raw = prefs.getString(CUSTOM_LIBRARY_PREFERENCE_KEY)
        if (raw.isNullOrEmpty()) return
        try {
            val decoded = Json.parseToJsonElement(raw)
            if (decoded !is kotlinx.serialization.json.JsonArray) return
            for (item in decoded) {
                CustomIconPreset.fromJson(item)?.let { _customIconPresets.add(it) }
            }
        } catch (_: Exception) {
            // Dart: debugPrint('Custom icon library decode failed: ...')
        }
    }

    private fun migrateLegacyCustomIcon() {
        if (_customIconPresets.isNotEmpty()) return
        val legacyPath = prefs.getString(CUSTOM_BRAND_PATH_PREFERENCE_KEY)
        if (legacyPath.isNullOrEmpty() || !File(legacyPath).exists()) return
        val now = nowMillis()
        val preset = CustomIconPreset(id = "legacy_$now", path = legacyPath, createdAt = now)
        _customIconPresets.add(preset)
        _activeCustomPresetId = preset.id
        prefs.putString(ACTIVE_CUSTOM_PRESET_PREFERENCE_KEY, preset.id)
        persistLibrary()
    }

    private fun cleanMissingPresets() {
        val before = _customIconPresets.size
        _customIconPresets.removeAll { !it.exists }
        if (_customIconPresets.size != before) persistLibrary()
    }

    private fun persistLibrary() {
        prefs.putString(
            CUSTOM_LIBRARY_PREFERENCE_KEY,
            buildJsonArray { _customIconPresets.forEach { add(it.toJson()) } }.toString(),
        )
    }

    private fun syncActiveCustomPath() {
        _customBrandIconPath = activeCustomPreset?.path
    }

    /** Dart refreshNativeLauncherState: skipped off-Android; Kotlin targets
     *  Android only, so the bridge being null is the unavailable case. */
    override suspend fun refreshNativeLauncherState(notify: Boolean) {
        if (_isRefreshingNativeState || bridge == null) return
        _isRefreshingNativeState = true
        try {
            val activeAlias = bridge.getCurrentLauncherIcon()
            if (!activeAlias.isNullOrEmpty()) {
                val nativeVariant = launcherIconVariantFromId(activeAlias)
                _launcherIcon = nativeVariant
                prefs.putString(LAUNCHER_PREFERENCE_KEY, nativeVariant.id)
            }
            if (_brandIconSource == BrandIconSource.custom && activeCustomPreset == null) {
                _brandIconSource = BrandIconSource.bundled
                prefs.putString(BRAND_SOURCE_PREFERENCE_KEY, BrandIconSource.bundled.name)
            }
        } catch (_: Exception) {
            _lastError = "Unable to verify the launcher icon on this device."
        } finally {
            _isRefreshingNativeState = false
            if (notify) notifyListeners()
        }
    }

    override suspend fun applyLauncherIcon(variant: LauncherIconVariant): Boolean {
        if (_isApplyingLauncherIcon) return false
        _isApplyingLauncherIcon = true
        _lastError = null
        notifyListeners()

        val previous = _launcherIcon
        val previousSource = _brandIconSource
        try {
            if (bridge != null) {
                val applied = bridge.setLauncherIcon(variant.androidAlias)
                if (applied != variant.androidAlias) {
                    // Dart PlatformException(code: 'launcher_icon_mismatch')
                    throw LauncherIconPickException(
                        "launcher_icon_mismatch",
                        "Android did not confirm the selected launcher icon.",
                    )
                }
            }
            _launcherIcon = variant
            _brandIconSource = BrandIconSource.bundled
            prefs.putString(LAUNCHER_PREFERENCE_KEY, variant.id)
            prefs.putString(BRAND_SOURCE_PREFERENCE_KEY, BrandIconSource.bundled.name)
            return true
        } catch (_: Exception) {
            _launcherIcon = previous
            _brandIconSource = previousSource
            _lastError = "Could not apply that launcher icon. Your previous icon is still active."
            return false
        } finally {
            _isApplyingLauncherIcon = false
            notifyListeners()
        }
    }

    /** Dart: returns the prepared temp path, or null on cancel; on error the
     *  mapped message lands in [lastError]. */
    suspend fun pickCustomIconImage(source: CustomIconInputSource): String? {
        if (bridge == null) return null
        _lastError = null
        return try {
            bridge.pickCustomIconImage(source)
        } catch (e: LauncherIconPickException) {
            _lastError = if (e.code == "camera_permission_denied") {
                "Camera permission is required only when you choose Camera."
            } else {
                e.message ?: "The image source could not be opened."
            }
            notifyListeners()
            null
        } catch (_: Exception) {
            _lastError = "The image source could not be opened."
            notifyListeners()
            null
        }
    }

    suspend fun saveCustomBrandIcon(pngBytes: ByteArray): Boolean {
        if (_isSavingCustomBrandIcon) return false
        _isSavingCustomBrandIcon = true
        _lastError = null
        notifyListeners()

        val id = "custom_${nowMicros()}" // Dart: custom_${DateTime.now().microsecondsSinceEpoch}
        try {
            val target = processor.persistSquarePng(pngBytes, presetId = id)
            val preset = CustomIconPreset(id = id, path = target.path, createdAt = nowMillis())
            _customIconPresets.add(preset)
            _activeCustomPresetId = preset.id
            _customBrandIconPath = preset.path
            _brandIconSource = BrandIconSource.custom

            persistLibrary()
            prefs.putString(ACTIVE_CUSTOM_PRESET_PREFERENCE_KEY, preset.id)
            prefs.putString(CUSTOM_BRAND_PATH_PREFERENCE_KEY, preset.path)
            prefs.putString(BRAND_SOURCE_PREFERENCE_KEY, BrandIconSource.custom.name)
            return true
        } catch (_: Exception) {
            _lastError = "The custom app icon could not be processed or saved."
            return false
        } finally {
            _isSavingCustomBrandIcon = false
            notifyListeners()
        }
    }

    suspend fun activateCustomPreset(presetId: String): Boolean {
        if (_isSavingCustomBrandIcon) return false
        val preset = _customIconPresets.firstOrNull { it.id == presetId && it.exists }
        if (preset == null) {
            _lastError = "That custom icon is no longer available."
            notifyListeners()
            return false
        }

        _isSavingCustomBrandIcon = true
        _lastError = null
        notifyListeners()
        try {
            _activeCustomPresetId = preset.id
            _customBrandIconPath = preset.path
            _brandIconSource = BrandIconSource.custom
            prefs.putString(ACTIVE_CUSTOM_PRESET_PREFERENCE_KEY, preset.id)
            prefs.putString(CUSTOM_BRAND_PATH_PREFERENCE_KEY, preset.path)
            prefs.putString(BRAND_SOURCE_PREFERENCE_KEY, BrandIconSource.custom.name)
            return true
        } catch (_: Exception) {
            _lastError = "Could not activate that custom brand icon."
            return false
        } finally {
            _isSavingCustomBrandIcon = false
            notifyListeners()
        }
    }

    suspend fun activateSavedCustomIcon(): Boolean {
        val preset = activeCustomPreset ?: _customIconPresets.lastOrNull { it.exists }
        return if (preset == null) false else activateCustomPreset(preset.id)
    }

    suspend fun removeCustomPreset(presetId: String) {
        val index = _customIconPresets.indexOfFirst { it.id == presetId }
        if (index < 0) return
        val removed = _customIconPresets.removeAt(index)
        val wasActive = _activeCustomPresetId == presetId

        try {
            val file = File(removed.path)
            if (file.exists()) file.delete()
        } catch (_: Exception) {
            // Dart: debugPrint('Custom preset file cleanup failed: ...')
        }

        persistLibrary()

        if (!wasActive) {
            notifyListeners()
            return
        }

        val remaining = _customIconPresets.filter { it.exists }
        if (remaining.isNotEmpty()) {
            val next = remaining.last()
            _activeCustomPresetId = next.id
            _customBrandIconPath = next.path
            prefs.putString(ACTIVE_CUSTOM_PRESET_PREFERENCE_KEY, next.id)
            prefs.putString(CUSTOM_BRAND_PATH_PREFERENCE_KEY, next.path)
            if (_brandIconSource == BrandIconSource.custom) {
                activateCustomPreset(next.id)
            } else {
                notifyListeners()
            }
        } else {
            removeCustomBrandIcon()
        }
    }

    suspend fun removeCustomBrandIcon() {
        for (preset in _customIconPresets.toList()) {
            try {
                val file = File(preset.path)
                if (file.exists()) file.delete()
            } catch (_: Exception) {
                // Dart: debugPrint('Custom preset cleanup failed: ...')
            }
        }

        _customIconPresets.clear()
        _activeCustomPresetId = null
        _customBrandIconPath = null
        _brandIconSource = BrandIconSource.bundled

        prefs.putString(BRAND_SOURCE_PREFERENCE_KEY, BrandIconSource.bundled.name)
        prefs.remove(CUSTOM_BRAND_PATH_PREFERENCE_KEY)
        prefs.remove(ACTIVE_CUSTOM_PRESET_PREFERENCE_KEY)
        prefs.remove(CUSTOM_LIBRARY_PREFERENCE_KEY)
        notifyListeners()
    }

    suspend fun resetLauncherIcon() {
        applyLauncherIcon(LauncherIconVariant.bird)
    }

    fun clearError() {
        if (_lastError == null) return
        _lastError = null
        notifyListeners()
    }
}
