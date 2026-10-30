package com.example.chat.ui.camera

import android.util.Log

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
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.EmojiNature
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Loupe
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.StarRate
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WbIridescent
import androidx.compose.ui.graphics.Color

/** Exact port of defaultLensCategory + kBuiltInLenses from
 *  lib/features/camera/lens/lens_manager.dart.
 *
 *  Placeholder category until a themed category system ships (requirement 55).
 *  Catalog content is original Chaty artwork only - no third-party lens
 *  assets are bundled or fetched. The original 10 built-ins are preserved
 *  (ids unchanged) and the catalog now spans 31 lenses: redesigned classics
 *  plus new ear accessories, eyewear, crowns/hats, horns, mouth/nose art and
 *  full-head scene lenses, all painted procedurally in LensOverlay.
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
    LensDefinition(
        id = "lens_cat_ears",
        name = "Cat Ears",
        category = defaultLensCategory,
        anchor = LensAnchor.ears,
        icon = Icons.Rounded.Pets,
        accentColor = Color(0xFFF59E0B),
        maxFaces = 2,
    ),
    LensDefinition(
        id = "lens_bunny_ears",
        name = "Bunny Ears",
        category = defaultLensCategory,
        anchor = LensAnchor.ears,
        icon = Icons.Rounded.EmojiNature,
        accentColor = Color(0xFFF9A8D4),
    ),
    LensDefinition(
        id = "lens_round_shades",
        name = "Retro Rounds",
        category = defaultLensCategory,
        anchor = LensAnchor.eyes,
        icon = Icons.Rounded.Face,
        accentColor = Color(0xFF111827),
    ),
    LensDefinition(
        id = "lens_heart_shades",
        name = "Heart Shades",
        category = defaultLensCategory,
        anchor = LensAnchor.eyes,
        icon = Icons.Rounded.Favorite,
        accentColor = Color(0xFFEF4444),
        maxFaces = 2,
    ),
    LensDefinition(
        id = "lens_star_eyes",
        name = "Starstruck",
        category = defaultLensCategory,
        anchor = LensAnchor.eyes,
        icon = Icons.Rounded.StarRate,
        accentColor = Color(0xFFFDE047),
    ),
    LensDefinition(
        id = "lens_monocle",
        name = "Monocle",
        category = defaultLensCategory,
        anchor = LensAnchor.eyes,
        icon = Icons.Rounded.Loupe,
        accentColor = Color(0xFFEAB308),
    ),
    LensDefinition(
        id = "lens_flower_crown",
        name = "Flower Crown",
        category = defaultLensCategory,
        anchor = LensAnchor.forehead,
        icon = Icons.Rounded.LocalFlorist,
        accentColor = Color(0xFFF472B6),
    ),
    LensDefinition(
        id = "lens_tiara",
        name = "Tiara",
        category = defaultLensCategory,
        anchor = LensAnchor.forehead,
        icon = Icons.Rounded.Diamond,
        accentColor = Color(0xFF93C5FD),
    ),
    LensDefinition(
        id = "lens_wizard_hat",
        name = "Wizard Hat",
        category = defaultLensCategory,
        anchor = LensAnchor.forehead,
        icon = Icons.Rounded.AutoFixHigh,
        accentColor = Color(0xFF8B5CF6),
    ),
    LensDefinition(
        id = "lens_headphones",
        name = "Studio Phones",
        category = defaultLensCategory,
        anchor = LensAnchor.ears,
        icon = Icons.Rounded.Headset,
        accentColor = Color(0xFF38BDF8),
    ),
    LensDefinition(
        id = "lens_devil_horns",
        name = "Devil Horns",
        category = defaultLensCategory,
        anchor = LensAnchor.forehead,
        icon = Icons.Rounded.LocalFireDepartment,
        accentColor = Color(0xFFDC2626),
    ),
    LensDefinition(
        id = "lens_rainbow_arc",
        name = "Rainbow Arc",
        category = defaultLensCategory,
        anchor = LensAnchor.fullHead,
        icon = Icons.Rounded.WbIridescent,
        accentColor = Color(0xFFF97316),
    ),
    LensDefinition(
        id = "lens_butterflies",
        name = "Butterflies",
        category = defaultLensCategory,
        anchor = LensAnchor.fullHead,
        icon = Icons.Rounded.Eco,
        accentColor = Color(0xFFA78BFA),
    ),
    LensDefinition(
        id = "lens_freckles",
        name = "Sunkissed Freckles",
        category = defaultLensCategory,
        anchor = LensAnchor.mouth,
        icon = Icons.Rounded.Mood,
        accentColor = Color(0xFFFB923C),
    ),
    LensDefinition(
        id = "lens_mustache",
        name = "Handlebar Mustache",
        category = defaultLensCategory,
        anchor = LensAnchor.mouth,
        icon = Icons.Rounded.SentimentVerySatisfied,
        accentColor = Color(0xFF3F3F46),
    ),
    LensDefinition(
        id = "lens_lip_gloss",
        name = "Lip Gloss",
        category = defaultLensCategory,
        anchor = LensAnchor.mouth,
        icon = Icons.Rounded.FavoriteBorder,
        accentColor = Color(0xFFF472B6),
    ),
    LensDefinition(
        id = "lens_clown_nose",
        name = "Clown Nose",
        category = defaultLensCategory,
        anchor = LensAnchor.noseTip,
        icon = Icons.Rounded.EmojiEmotions,
        accentColor = Color(0xFFEF4444),
    ),
    LensDefinition(
        id = "lens_pirate_patch",
        name = "Pirate Patch",
        category = defaultLensCategory,
        anchor = LensAnchor.eyes,
        icon = Icons.Rounded.VisibilityOff,
        accentColor = Color(0xFF1F2937),
    ),
    LensDefinition(
        id = "lens_snow_drift",
        name = "Snow Drift",
        category = defaultLensCategory,
        anchor = LensAnchor.fullHead,
        icon = Icons.Rounded.AcUnit,
        accentColor = Color(0xFFBAE6FD),
    ),
    LensDefinition(
        id = "lens_music_notes",
        name = "Music Notes",
        category = defaultLensCategory,
        anchor = LensAnchor.fullHead,
        icon = Icons.Rounded.MusicNote,
        accentColor = Color(0xFF8B5CF6),
    ),
    LensDefinition(
        id = "lens_star_crown",
        name = "Star Crown",
        category = defaultLensCategory,
        anchor = LensAnchor.forehead,
        icon = Icons.Rounded.Stars,
        accentColor = Color(0xFFFDE047),
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
        if (com.example.chat.BuildConfig.DEBUG) runCatching { Log.i("LENS-DIAG", "select(" + lensId + ") prev=" + _selectedId) }
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
        if (com.example.chat.BuildConfig.DEBUG) runCatching { Log.i("LENS-DIAG", "commit requestId=" + requestId + " pending=" + pendingRequestId + " selected=" + selected.id) }
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
