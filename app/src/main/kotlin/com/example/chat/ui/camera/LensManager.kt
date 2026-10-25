package com.example.chat.ui.camera

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FaceRetouchingNatural
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.ui.graphics.Color

/** Exact port of defaultLensCategory + kBuiltInLenses from
 *  lib/features/camera/lens/lens_manager.dart.
 *
 *  Placeholder category until a themed category system ships (requirement 55).
 *  Catalog content is original Chaty artwork only - no third-party lens
 *  assets are bundled or fetched.
 */
val defaultLensCategory: EffectCategory = EffectCategory.faceAR

val kBuiltInLenses: List<LensDefinition> = listOf(
    LensDefinition(
        id = "none",
        name = "No Lens",
        category = defaultLensCategory,
        anchor = LensAnchor.fullHead,
        icon = Icons.Outlined.Circle,
        accentColor = Color(0xFF64748B),
        requiresFaceTracking = false,
    ),
    LensDefinition(
        id = "lens_glasses_classic",
        name = "Classic Frames",
        category = defaultLensCategory,
        anchor = LensAnchor.eyes,
        icon = Icons.Rounded.Visibility,
        accentColor = Color(0xFF38BDF8),
    ),
    LensDefinition(
        id = "lens_crown_gold",
        name = "Gold Crown",
        category = defaultLensCategory,
        anchor = LensAnchor.forehead,
        icon = Icons.Rounded.WorkspacePremium,
        accentColor = Color(0xFFFBBF24),
    ),
    LensDefinition(
        id = "lens_dog_ears",
        name = "Puppy Ears",
        category = defaultLensCategory,
        anchor = LensAnchor.ears,
        icon = Icons.Rounded.Pets,
        accentColor = Color(0xFFB45309),
    ),
    LensDefinition(
        id = "lens_blush_glow",
        name = "Blush Glow",
        category = defaultLensCategory,
        anchor = LensAnchor.mouth,
        icon = Icons.Rounded.FaceRetouchingNatural,
        accentColor = Color(0xFFF472B6),
        supportsCalls = true,
    ),
    LensDefinition(
        id = "lens_sunglasses_aviator",
        name = "Aviator Shades",
        category = defaultLensCategory,
        anchor = LensAnchor.eyes,
        icon = Icons.Rounded.RemoveRedEye,
        accentColor = Color(0xFF38BDF8),
    ),
    LensDefinition(
        id = "lens_sparkles",
        name = "Face Sparkles",
        category = defaultLensCategory,
        anchor = LensAnchor.forehead,
        icon = Icons.Rounded.AutoAwesome,
        accentColor = Color(0xFFFDE68A),
    ),
    LensDefinition(
        id = "lens_hearts_float",
        name = "Heart Pop",
        category = defaultLensCategory,
        anchor = LensAnchor.fullHead,
        icon = Icons.Rounded.Favorite,
        accentColor = Color(0xFFF472B6),
    ),
    LensDefinition(
        id = "lens_angel_halo",
        name = "Angel Halo",
        category = defaultLensCategory,
        anchor = LensAnchor.forehead,
        icon = Icons.Rounded.WbTwilight,
        accentColor = Color(0xFFE0F2FE),
    ),
    LensDefinition(
        id = "lens_neon_crown",
        name = "Neon Crown",
        category = defaultLensCategory,
        anchor = LensAnchor.forehead,
        icon = Icons.Rounded.WorkspacePremium,
        accentColor = Color(0xFF22D3EE),
    ),
)

/** Persistence seam for Dart SharedPreferences string lists. */
interface LensStringListStore {
    fun getStringList(key: String): List<String>?
    fun setStringList(key: String, value: List<String>)
}

/** Timer seam for the Dart settle Timer; coroutine-based default or fake in tests. */
fun interface LensTimerHandle {
    fun cancel()
}

fun interface LensTimerScheduler {
    fun schedule(delayMs: Long, action: () -> Unit): LensTimerHandle
}

/** Exact port of LensManager from
 *  lib/features/camera/lens/lens_manager.dart.
 *
 *  Owns the lens catalog, selection pipeline, prefetch tiers, recents and
 *  runtime state. Widgets never implement this logic themselves
 *  (requirement 46). ChangeNotifier is ported as the same listener pattern
 *  used by ChatyPreferencesController.
 */
class LensManager(
    val catalog: List<LensDefinition> = kBuiltInLenses,
    private val prefs: LensStringListStore? = null,
    private val timerScheduler: LensTimerScheduler,
) {
    companion object {
        const val RECENT_KEY = "camera.lens.recents"
        const val FAVORITE_KEY = "camera.lens.favorites"

        /** How long a settled selection must stay before a heavy load begins;
         *  fast flicks across many lenses never enqueue work (requirement 49). */
        const val SELECTION_SETTLE_DEBOUNCE_MS: Long = 180
    }

    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyListeners() { listeners.toList().forEach { it() } }

    private var recentsList: List<String> = prefs?.getStringList(RECENT_KEY) ?: emptyList()
    private var favoritesSet: Set<String> =
        (prefs?.getStringList(FAVORITE_KEY) ?: emptyList()).toSet()

    val selected: LensDefinition
        get() = catalog.firstOrNull { it.id == _selectedId } ?: catalog.first()
    private var _selectedId = "none"

    private var _runtimeState = LensRuntimeState.none
    val runtimeState: LensRuntimeState get() = _runtimeState

    val recents: List<String> get() = recentsList.toList()
    val favorites: Set<String> get() = favoritesSet.toSet()

    private var pendingRequestId = 0
    private var settleTimer: LensTimerHandle? = null

    init {
        _runtimeState = LensRuntimeState.none
    }

    /** Selects a lens. Returns immediately; heavy load work is debounced so a
     *  fast scroll applies only where the carousel actually settles
     *  (requirements 48/49). Stale pending requests are cancelled by token. */
    fun select(lensId: String) {
        if (_selectedId == lensId) return
        _selectedId = lensId
        pendingRequestId++ // cancels any in-flight application
        _runtimeState = if (selected.isNoLens) LensRuntimeState.none else LensRuntimeState.applying

        if (!selected.isNoLens) {
            recentsList = (listOf(lensId) + recentsList.filter { it != lensId }).take(20)
            prefs?.setStringList(RECENT_KEY, recentsList)
        }
        notifyListeners()

        settleTimer?.cancel()
        val requestId = pendingRequestId
        settleTimer = timerScheduler.schedule(SELECTION_SETTLE_DEBOUNCE_MS) {
            commitSelection(requestId)
        }
    }

    private fun commitSelection(requestId: Int) {
        // A newer selection superseded this one - drop silently (requirement 48).
        if (requestId != pendingRequestId || selected.isNoLens) {
            return
        }
        _runtimeState = LensRuntimeState.active
        notifyListeners()
    }

    fun clear() = select("none")

    fun toggleFavorite(lensId: String) {
        // Exact Dart: if (!_favorites.add(lensId)) { _favorites.remove(lensId); }
        favoritesSet = if (favoritesSet.contains(lensId)) {
            favoritesSet - lensId
        } else {
            favoritesSet + lensId
        }
        prefs?.setStringList(FAVORITE_KEY, favoritesSet.toList())
        notifyListeners()
    }

    /** Visible-window prefetch tiering (requirement 15): returns the catalog
     *  indices that should be prepared for [centerIndex], ordered from the
     *  center out. */
    fun prefetchIndices(centerIndex: Int, near: Int = 2, far: Int = 5): Set<Int> {
        val indices = mutableSetOf<Int>()
        for (delta in -far..far) {
            val index = centerIndex + delta
            if (index >= 0 && index < catalog.size) indices.add(index)
        }
        // Near items are prioritized by ordering the set from the center out.
        return indices.toSortedSet(compareBy { kotlin.math.abs(it - centerIndex) })
    }

    fun dispose() {
        settleTimer?.cancel()
    }
}
