package com.example.chat.core.theme

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Theme blob persistence seam (LocalPreferencesStorage on Android). */
interface ThemeStateStore {
    suspend fun loadThemeState(): Map<String, Any?>
    suspend fun saveThemeState(data: Map<String, Any?>): Boolean
}

/** Exact port of lib/ui/core/theme/theme_controller.dart.
 *
 *  Owns the app's global theme, per-chat theme overrides, and display-level
 *  settings (layout, navigation, font scale, density, reduced motion).
 *
 *  PERSISTENCE: the full [ThemeConfig] (including any user customizations made
 *  in the theme editor) is stored in the device-global theme bucket and
 *  restored by [init] before the first frame so there is no theme flash on
 *  launch and a user's chosen theme survives restarts. This state is
 *  device-global (not account-sensitive) and is intentionally NOT namespaced
 *  per user. All mutators persist their result. */
class ThemeController(
    private val store: ThemeStateStore,
    private val scope: CoroutineScope,
) {
    /** Bump when the persisted theme-state shape changes. */
    private val stateVersion = 2

    private var _globalTheme: ThemeConfig = ThemePresets.getSystemDefaultTheme()
    private val _perChatThemes = mutableMapOf<String, ThemeConfig>()
    private var _layoutMode = UILayoutMode.classic
    private var _navigationMode = AppNavigationMode.bottomNav
    private var _useReducedMotion = false
    private var _fontScale = 1.0
    private var _density = 1.0
    private var _initialized = false

    /** When true (default) the app tracks the phone's light/dark setting using
     *  the default light/dark presets. Picking another theme or manually
     *  toggling brightness turns this off until [resetToDefaults]. */
    var followSystem = true
        private set

    /** ChangeNotifier equivalent: listeners fire synchronously on mutate. */
    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        for (l in listeners.toList()) l()
    }

    val globalTheme: ThemeConfig get() = _globalTheme
    val layoutMode: UILayoutMode get() = _layoutMode
    val navigationMode: AppNavigationMode get() = _navigationMode
    val useReducedMotion: Boolean get() = _useReducedMotion
    val fontScale: Double get() = _fontScale
    val density: Double get() = _density

    /** Re-applies the system default theme when following the system. */
    fun syncWithSystem(platformIsLight: Boolean? = null) {
        if (!followSystem) return
        val target = ThemePresets.getSystemDefaultTheme(platformIsLight)
        if (_globalTheme.id == target.id) return
        setGlobalTheme(target, followSystem = true)
    }

    /** Loads persisted theme state. Call once from app start and await it
     *  before the first frame so the first frame already reflects the user's
     *  theme. Safe to call more than once (subsequent calls are no-ops). */
    suspend fun init() {
        if (_initialized) return
        _initialized = true
        try {
            val state = store.loadThemeState()
            if (state.isNotEmpty()) {
                applyState(state)
            }
        } catch (_: Exception) {
            // Corrupt/absent theme state: keep the system-default theme already set.
        }
    }

    fun getThemeForChat(conversationId: String): ThemeConfig =
        _perChatThemes[conversationId] ?: _globalTheme

    fun setGlobalTheme(newTheme: ThemeConfig, followSystem: Boolean = false) {
        this.followSystem = followSystem && isDefaultId(newTheme.id)
        _globalTheme = newTheme.copyWith(
            layoutMode = _layoutMode,
            navigationMode = _navigationMode,
            fontScale = _fontScale,
            density = _density,
            animationLevel = if (_useReducedMotion) 0.0 else 1.0,
        )
        notifyListeners()
        persist()
    }

    fun updateThemeConfig(customConfig: ThemeConfig) {
        followSystem = false
        _globalTheme = customConfig
        // Keep the standalone display fields consistent with the incoming config so
        // a later setGlobalTheme does not clobber the user's customization.
        _layoutMode = customConfig.layoutMode
        _navigationMode = customConfig.navigationMode
        _fontScale = customConfig.fontScale
        _density = customConfig.density
        _useReducedMotion = customConfig.animationLevel <= 0.0
        notifyListeners()
        persist()
    }

    fun setChatTheme(conversationId: String, customTheme: ThemeConfig?) {
        if (customTheme == null) {
            _perChatThemes.remove(conversationId)
        } else {
            _perChatThemes[conversationId] = customTheme
        }
        notifyListeners()
        persist()
    }

    fun setLayoutMode(mode: UILayoutMode) {
        _layoutMode = mode
        _globalTheme = _globalTheme.copyWith(layoutMode = mode)
        notifyListeners()
        persist()
    }

    fun setNavigationMode(mode: AppNavigationMode) {
        _navigationMode = mode
        _globalTheme = _globalTheme.copyWith(navigationMode = mode)
        notifyListeners()
        persist()
    }

    fun setFontScale(scale: Double) {
        _fontScale = scale
        _globalTheme = _globalTheme.copyWith(fontScale = scale)
        notifyListeners()
        persist()
    }

    fun setDensity(density: Double) {
        _density = density
        _globalTheme = _globalTheme.copyWith(density = density)
        notifyListeners()
        persist()
    }

    fun setReducedMotion(reduced: Boolean) {
        _useReducedMotion = reduced
        _globalTheme = _globalTheme.copyWith(animationLevel = if (reduced) 0.0 else 1.0)
        notifyListeners()
        persist()
    }

    fun toggleBrightness() {
        // Manual choice overrides system-follow (setGlobalTheme below clears it).
        val currentId = _globalTheme.id.lowercase()
        if (currentId == "warm_neutral") {
            setGlobalTheme(ThemePresets.warmNeutralDark)
            return
        } else if (currentId == "warm_neutral_dark") {
            setGlobalTheme(ThemePresets.warmNeutral)
            return
        }

        // Check if there is an exact registered preset counterpart
        if (currentId.contains("dark") || currentId.contains("light")) {
            val targetId = if (currentId.contains("dark"))
                currentId.replace("dark", "light")
            else
                currentId.replace("light", "dark")

            val match = ThemePresets.all.firstOrNull { it.id.lowercase() == targetId }
            if (match != null) {
                setGlobalTheme(match) // persists
                return
            }
        }

        // For any custom or arbitrary theme, dynamically invert & adapt colors
        setGlobalTheme(_globalTheme.toggleBrightness()) // persists
    }

    fun resetToDefaults() {
        _globalTheme = ThemePresets.getSystemDefaultTheme()
        followSystem = true
        _perChatThemes.clear()
        _layoutMode = UILayoutMode.classic
        _navigationMode = AppNavigationMode.bottomNav
        _useReducedMotion = false
        _fontScale = 1.0
        _density = 1.0
        notifyListeners()
        persist()
    }

    // ---------------------------------------------------------------------------
    // Persistence internals
    // ---------------------------------------------------------------------------

    private fun persist() {
        // Do not write during the initial load; only user-driven mutations persist.
        if (!_initialized) return
        val snapshot = toState()
        scope.launch {
            store.saveThemeState(snapshot)
        }
    }

    private fun toState(): Map<String, Any?> {
        val perChat = mutableMapOf<String, Any?>()
        for ((chatId, cfg) in _perChatThemes) {
            perChat[chatId] = cfg.toMap()
        }
        return mapOf(
            "schemaVersion" to stateVersion,
            "globalTheme" to _globalTheme.toMap(),
            "perChatThemes" to perChat,
            "useReducedMotion" to _useReducedMotion,
            "followSystem" to followSystem,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyState(state: Map<String, Any?>) {
        // Global theme is authoritative for layout/nav/font/density (setters always
        // sync those into it), so derive the standalone fields from it after decode.
        val rawGlobal = state["globalTheme"]
        if (rawGlobal is Map<*, *>) {
            try {
                _globalTheme = ThemeConfig.fromMap(rawGlobal as Map<String, Any?>)
            } catch (_: Exception) {
                // failed to decode global theme (keeping default)
            }
        }

        // Migration: installs that saved the old (brown) default keep following the
        // system with the new default light/dark presets unless they opted out.
        val storedFollow = state["followSystem"]
        followSystem = if (storedFollow is Boolean)
            storedFollow
        else
            isDefaultId(_globalTheme.id)
        if (followSystem) {
            _globalTheme = ThemePresets.getSystemDefaultTheme()
        }

        _layoutMode = _globalTheme.layoutMode
        _navigationMode = _globalTheme.navigationMode
        _fontScale = _globalTheme.fontScale
        _density = _globalTheme.density

        val reduced = state["useReducedMotion"]
        _useReducedMotion = if (reduced is Boolean)
            reduced
        else
            _globalTheme.animationLevel <= 0.0
        // Keep animationLevel consistent with the restored reduced-motion flag.
        _globalTheme = _globalTheme.copyWith(
            animationLevel = if (_useReducedMotion) 0.0 else 1.0,
        )

        val rawPerChat = state["perChatThemes"]
        if (rawPerChat is Map<*, *>) {
            _perChatThemes.clear()
            for ((key, value) in rawPerChat) {
                if (value is Map<*, *>) {
                    try {
                        _perChatThemes["$key"] = ThemeConfig.fromMap(value as Map<String, Any?>)
                    } catch (_: Exception) {
                        // Skip a single corrupt per-chat entry; others still load.
                    }
                }
            }
        }
    }

    companion object {
        private fun isDefaultId(id: String): Boolean =
            id == "warm_neutral" || id == "warm_neutral_dark"
    }
}
