package com.example.chat.core.templates

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Persistence seam (Dart: SharedPreferences). setString returns false when the write failed. */
interface AppearanceStore {
    suspend fun getString(key: String): String?
    suspend fun setString(key: String, value: String): Boolean
}

/** Port of lib/ui/core/controllers/appearance_variant_controller.dart.
 *  Writes are serialized and publish only after persistence succeeds (failed persist throws IllegalStateException). */
class AppearanceVariantController(private val store: AppearanceStore) {
    companion object {
        val navigationStyles: List<String> = listOf(
            "Adaptive Rail",
            "Compact Rail",
            "Minimal Rail",
            "Pill Rail",
            "Outlined Rail",
            "Floating Rail",
            "Dense Rail",
            "Icon Rail",
            "Label Rail",
            "Soft Rail",
            "Classic Tabs",
            "Segmented Tabs",
            "Underline Tabs",
            "Pill Tabs",
            "Icon Tabs",
            "Compact Tabs",
            "Floating Tabs",
            "Sidebar Tabs",
            "Workspace Tabs",
            "Focus Tabs"
        )

        val bottomBarStyles: List<String> = listOf(
            "Floating Pill",
            "Active Pill Chip",
            "Top Indicator Line",
            "Bottom Indicator Dot",
            "Circle Accent Pop",
            "Curved Notch Teardrop",
            "Floating Dynamic Island",
            "Raised Center Action",
            "Segmented Glass Dock",
            "Minimal Icon Dock",
            "Classic Label Bar",
            "Soft Square Tile"
        )

        val appIconStyles: List<String> = listOf(
            "Chaty Original",
            "Outline Chat",
            "Solid Chat",
            "Soft Square",
            "Circle Chat",
            "Monochrome",
            "High Contrast",
            "Minimal Mark",
            "Rounded Mark",
            "Sharp Mark",
            "Message Stack",
            "Double Bubble",
            "Wave Bubble",
            "Bolt Chat",
            "Orbit Chat",
            "Pixel Chat",
            "Business Chat",
            "Creator Chat",
            "Privacy Chat",
            "Focus Chat"
        )

        val notificationIconStyles: List<String> = listOf(
            "Chat Bubble",
            "Outline Bubble",
            "Double Tick",
            "Bell Dot",
            "Message Dot",
            "Minimal Dot",
            "Priority Ring",
            "Contact Circle",
            "Group Stack",
            "Task Check",
            "Call Ring",
            "Video Ring",
            "Status Ring",
            "Muted Bell",
            "Secure Lock",
            "Shield Chat",
            "Compact Badge",
            "Workspace Badge",
            "Monochrome Badge",
            "Focus Badge"
        )

        val typographyStyles: List<String> = listOf(
            "System Default",
            "Compact",
            "Comfortable",
            "Editorial",
            "Product Sans",
            "Geometric",
            "Humanist",
            "Rounded",
            "Technical",
            "Monospace Accent",
            "Dense UI",
            "Large UI",
            "Accessibility",
            "Minimal",
            "Business",
            "Creator",
            "Classic",
            "Modern",
            "Soft",
            "Focus"
        )

        val entryAnimations: List<String> = listOf(
            "Fade + Slide",
            "Fade",
            "Slide Right",
            "Slide Left",
            "Slide Up",
            "Slide Down",
            "Scale In",
            "Soft Zoom",
            "Shared Axis X",
            "Shared Axis Y",
            "Fade Through",
            "Cupertino Push",
            "Spring Push",
            "Soft Reveal",
            "Card Lift",
            "Blur-free Reveal",
            "Quick Snap",
            "Gentle Drift",
            "Focus In",
            "None"
        )

        val exitAnimations: List<String> = listOf(
            "Fade",
            "Fade + Slide",
            "Slide Right",
            "Slide Left",
            "Slide Up",
            "Slide Down",
            "Scale Out",
            "Soft Zoom",
            "Shared Axis X",
            "Shared Axis Y",
            "Fade Through",
            "Cupertino Pop",
            "Spring Pop",
            "Soft Conceal",
            "Card Drop",
            "Quick Snap",
            "Gentle Drift",
            "Focus Out",
            "Cross Fade",
            "None"
        )

        private val textScales = listOf(
            1.0, 0.94, 1.04, 1.02, 1.0, 1.0, 1.02, 1.03, 0.96, 0.98,
            0.90, 1.10, 1.18, 0.96, 0.98, 1.02, 1.0, 1.0, 1.04, 0.96,
        )

        fun resolveTextScale(systemScale: Double, appearanceScale: Double): Double {
            val preferred = systemScale * appearanceScale
            return if (systemScale > 1.0) preferred.coerceAtLeast(systemScale) else preferred.coerceIn(0.8, 1.6)
        }
    }

    var navigationStyle = navigationStyles.first(); private set
    var bottomBarStyle = bottomBarStyles.first(); private set
    var appIconStyle = appIconStyles.first(); private set
    var notificationIconStyle = notificationIconStyles.first(); private set
    var typographyStyle = typographyStyles.first(); private set
    var entryAnimation = entryAnimations.first(); private set
    var exitAnimation = exitAnimations.first(); private set
    var loaded = false; private set

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    private val writeLock = Mutex()
    private val loadLock = Mutex()

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    private fun notifyListeners() { listeners.forEach { it() } }

    val navigationIndex: Int get() = navigationStyles.indexOf(navigationStyle).coerceIn(0, navigationStyles.size - 1)
    val bottomBarIndex: Int get() = bottomBarStyles.indexOf(bottomBarStyle).coerceIn(0, bottomBarStyles.size - 1)
    val typographyIndex: Int get() = typographyStyles.indexOf(typographyStyle).coerceIn(0, typographyStyles.size - 1)
    val textScale: Double get() = textScales[typographyIndex]

    private fun validated(value: String?, options: List<String>) =
        if (value != null && options.contains(value)) value else options.first()

    /** Idempotent; Dart ran this from the constructor. */
    suspend fun load() = loadLock.withLock {
        if (loaded) return
        navigationStyle = validated(store.getString("appearance.navigation"), navigationStyles)
        bottomBarStyle = validated(store.getString("appearance.bottomBar"), bottomBarStyles)
        appIconStyle = validated(store.getString("appearance.appIcon"), appIconStyles)
        notificationIconStyle = validated(store.getString("appearance.notificationIcon"), notificationIconStyles)
        typographyStyle = validated(store.getString("appearance.typography"), typographyStyles)
        entryAnimation = validated(store.getString("appearance.entryAnimation"), entryAnimations)
        exitAnimation = validated(store.getString("appearance.exitAnimation"), exitAnimations)
        loaded = true
        notifyListeners()
    }

    suspend fun setNavigationStyle(v: String) = set(v, navigationStyles, "appearance.navigation") { navigationStyle = it }
    suspend fun setBottomBarStyle(v: String) = set(v, bottomBarStyles, "appearance.bottomBar") { bottomBarStyle = it }
    suspend fun setAppIconStyle(v: String) = set(v, appIconStyles, "appearance.appIcon") { appIconStyle = it }
    suspend fun setNotificationIconStyle(v: String) = set(v, notificationIconStyles, "appearance.notificationIcon") { notificationIconStyle = it }
    suspend fun setTypographyStyle(v: String) = set(v, typographyStyles, "appearance.typography") { typographyStyle = it }
    suspend fun setEntryAnimation(v: String) = set(v, entryAnimations, "appearance.entryAnimation") { entryAnimation = it }
    suspend fun setExitAnimation(v: String) = set(v, exitAnimations, "appearance.exitAnimation") { exitAnimation = it }

    private suspend fun set(value: String, options: List<String>, key: String, apply: (String) -> Unit) {
        if (!options.contains(value)) return
        load()
        writeLock.withLock {
            if (!store.setString(key, value)) throw IllegalStateException("Could not persist appearance preference: $key")
            apply(value)
            notifyListeners()
        }
    }
}
