package com.example.chat.ui.camera

import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Stream
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SolarPower
import androidx.compose.material.icons.rounded.PhotoFilter
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.NaturePeople
import androidx.compose.material.icons.rounded.MovieFilter
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.MonochromePhotos
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.LocalMovies
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LensBlur
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Grid4x4
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.FilterVintage
import androidx.compose.material.icons.rounded.FilterBAndW
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.CropOriginal
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.CircleNotifications
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CameraRoll
import androidx.compose.material.icons.rounded.CameraEnhance
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BlurLinear
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.FaceRetouchingNatural
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Brightness7
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FilterDrama
import androidx.compose.material.icons.rounded.FilterHdr
import androidx.compose.material.icons.rounded.InvertColors
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Nightlife
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Texture
import androidx.compose.material.icons.rounded.Tonality
import androidx.compose.material.icons.rounded.WbIridescent
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.WbSunny

/** Exact port of lib/features/camera/effects/effect_model.dart. */
enum class EffectCategory {
    beauty,
    portrait,
    cinematic,
    retro,
    film,
    glow,
    color,
    faceAR,
    celebration,
    cyber,
    background,
}

/** Exact port of EffectPerformanceTier from effect_model.dart. */
enum class EffectPerformanceTier { lightweight, standard, gpuIntensive }

/** Exact port of ChatyCameraEffect from effect_model.dart.
 *
 *  Model definition for an individual camera or video-call filter/lens.
 *  Dart IconData -> ImageVector; Color stays Color.
 */
data class ChatyCameraEffect(
    val id: String,
    val name: String,
    val category: EffectCategory,
    val icon: ImageVector,
    val previewColor: Color,
    val colorMatrix: List<Double>? = null,
    val shaderKey: String? = null,
    val isFaceTrackingRequired: Boolean = false,
    val isSegmentationRequired: Boolean = false,
    val tier: EffectPerformanceTier = EffectPerformanceTier.lightweight,
    val defaultIntensity: Double = 1.0,
)

/** Exact port of lib/features/camera/effects/effect_registry.dart.
 *
 *  The original 60 effects ported from the Dart source at the api anchor
 *  (every id, name, category, flag and 4x5 color matrix value preserved
 *  verbatim), plus 40 new original Chaty effects expanding Beauty,
 *  Cinematic, Retro, Film, Glow, Cyber, Fun (celebration), Color grading
 *  and Portrait. New artwork is original Chaty content only: no
 *  third-party lens/filter assets bundled or fetched. The render pipeline
 *  consumes colorMatrix, plus the single "pixelate" shaderKey above. New entries are all
 *  full-frame color grades: no new entry claims face tracking or
 *  segmentation, because the live pipeline only bridges the six
 *  original face_* ids to lenses (CameraCaptureScreen.lensIdForEffect)
 *  and ships no segmentation renderer. shaderKey is null for every entry
 *  except face_pixel_retro, whose "pixelate" key selects the original
 *  Chaty pixelate renderer (EffectRender/ImageFxBaker).
 */
object EffectRegistry {
    val allEffects: List<ChatyCameraEffect> = listOf(
        ChatyCameraEffect(
            id = "none",
            name = "Normal",
            category = EffectCategory.portrait,
            icon = Icons.Rounded.FilterNone,
            previewColor = Color(0xFF64748B),
        ),
        ChatyCameraEffect(
            id = "face_dog_filter",
            name = "Puppy Face",
            category = EffectCategory.faceAR,
            icon = Icons.Rounded.Pets,
            previewColor = Color(0xFFB45309),
            colorMatrix = listOf(
                1.08, 0.0, 0.0, 0.0, 8.0,
                0.0, 1.04, 0.0, 0.0, 6.0,
                0.0, 0.0, 1.02, 0.0, 4.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
            isFaceTrackingRequired = true,
        ),
        ChatyCameraEffect(
            id = "beauty_soft_smooth",
            name = "Soft Glow",
            category = EffectCategory.beauty,
            icon = Icons.Rounded.AutoAwesome,
            previewColor = Color(0xFFF472B6),
            colorMatrix = listOf(
                1.05, 0.0, 0.0, 0.0, 10.0,
                0.0, 1.05, 0.0, 0.0, 10.0,
                0.0, 0.0, 1.05, 0.0, 10.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "beauty_porcelain",
            name = "Porcelain",
            category = EffectCategory.beauty,
            icon = Icons.Rounded.FaceRetouchingNatural,
            previewColor = Color(0xFFFDA4AF),
            colorMatrix = listOf(
                1.1, 0.0, 0.0, 0.0, 18.0,
                0.0, 1.08, 0.0, 0.0, 16.0,
                0.0, 0.0, 1.06, 0.0, 14.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "beauty_warm_blush",
            name = "Warm Blush",
            category = EffectCategory.beauty,
            icon = Icons.Rounded.FavoriteBorder,
            previewColor = Color(0xFFFB7185),
            colorMatrix = listOf(
                1.15, 0.0, 0.0, 0.0, 12.0,
                0.0, 1.02, 0.0, 0.0, 4.0,
                0.0, 0.0, 1.02, 0.0, 4.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "beauty_radiant",
            name = "Radiant",
            category = EffectCategory.beauty,
            icon = Icons.Rounded.WbSunny,
            previewColor = Color(0xFFFDE047),
            colorMatrix = listOf(
                1.12, 0.0, 0.0, 0.0, 15.0,
                0.0, 1.1, 0.0, 0.0, 12.0,
                0.0, 0.0, 0.98, 0.0, 0.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "beauty_golden_hour",
            name = "Golden Hour",
            category = EffectCategory.beauty,
            icon = Icons.Rounded.WbTwilight,
            previewColor = Color(0xFFF59E0B),
            colorMatrix = listOf(
                1.2, 0.0, 0.0, 0.0, 16.0,
                0.0, 1.05, 0.0, 0.0, 8.0,
                0.0, 0.0, 0.85, 0.0, -5.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "beauty_ivory",
            name = "Ivory Tone",
            category = EffectCategory.beauty,
            icon = Icons.Rounded.Spa,
            previewColor = Color(0xFFFEF3C7),
            colorMatrix = listOf(
                1.08, 0.0, 0.0, 0.0, 14.0,
                0.0, 1.08, 0.0, 0.0, 14.0,
                0.0, 0.0, 1.02, 0.0, 6.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "beauty_dewy",
            name = "Dewy Fresh",
            category = EffectCategory.beauty,
            icon = Icons.Rounded.WaterDrop,
            previewColor = Color(0xFF67E8F9),
            colorMatrix = listOf(
                1.04, 0.0, 0.0, 0.0, 8.0,
                0.0, 1.08, 0.0, 0.0, 12.0,
                0.0, 0.0, 1.12, 0.0, 16.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "beauty_sunset_glow",
            name = "Sunset Glow",
            category = EffectCategory.beauty,
            icon = Icons.Rounded.Flare,
            previewColor = Color(0xFFEA580C),
            colorMatrix = listOf(
                1.22, 0.0, 0.0, 0.0, 20.0,
                0.0, 0.95, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.9, 0.0, -10.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "beauty_clean_look",
            name = "Clean Look",
            category = EffectCategory.beauty,
            icon = Icons.Rounded.LensBlur,
            previewColor = Color(0xFFE2E8F0),
            colorMatrix = listOf(
                1.06, 0.0, 0.0, 0.0, 6.0,
                0.0, 1.06, 0.0, 0.0, 6.0,
                0.0, 0.0, 1.06, 0.0, 6.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "beauty_peachy",
            name = "Peach Tone",
            category = EffectCategory.beauty,
            icon = Icons.Rounded.NaturePeople,
            previewColor = Color(0xFFFF8A80),
            colorMatrix = listOf(
                1.18, 0.0, 0.0, 0.0, 16.0,
                0.0, 1.04, 0.0, 0.0, 8.0,
                0.0, 0.0, 0.92, 0.0, 0.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cine_teal_orange",
            name = "Teal & Orange",
            category = EffectCategory.cinematic,
            icon = Icons.Rounded.MovieFilter,
            previewColor = Color(0xFF0D9488),
            colorMatrix = listOf(
                1.25, 0.0, 0.0, 0.0, 10.0,
                0.0, 0.95, 0.0, 0.0, -5.0,
                0.0, 0.0, 1.3, 0.0, 20.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cine_noir",
            name = "Film Noir",
            category = EffectCategory.cinematic,
            icon = Icons.Rounded.Contrast,
            previewColor = Color(0xFF1E293B),
            colorMatrix = listOf(
                0.33, 0.33, 0.33, 0.0, -10.0,
                0.33, 0.33, 0.33, 0.0, -10.0,
                0.33, 0.33, 0.33, 0.0, -10.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cine_hollywood",
            name = "Hollywood 35mm",
            category = EffectCategory.cinematic,
            icon = Icons.Rounded.Theaters,
            previewColor = Color(0xFFCA8A04),
            colorMatrix = listOf(
                1.15, 0.0, 0.0, 0.0, 12.0,
                0.0, 1.1, 0.0, 0.0, 6.0,
                0.0, 0.0, 0.9, 0.0, -8.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cine_dystopia",
            name = "Cyber Dystopia",
            category = EffectCategory.cinematic,
            icon = Icons.Rounded.BlurLinear,
            previewColor = Color(0xFF065F46),
            colorMatrix = listOf(
                0.85, 0.0, 0.0, 0.0, -10.0,
                0.0, 1.25, 0.0, 0.0, 15.0,
                0.0, 0.0, 1.15, 0.0, 10.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cine_moody_blue",
            name = "Moody Blue",
            category = EffectCategory.cinematic,
            icon = Icons.Rounded.Nightlight,
            previewColor = Color(0xFF1E3A8A),
            colorMatrix = listOf(
                0.8, 0.0, 0.0, 0.0, -15.0,
                0.0, 0.9, 0.0, 0.0, -5.0,
                0.0, 0.0, 1.35, 0.0, 25.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cine_matrix_green",
            name = "Matrix Code",
            category = EffectCategory.cinematic,
            icon = Icons.Rounded.Terminal,
            previewColor = Color(0xFF10B981),
            colorMatrix = listOf(
                0.7, 0.0, 0.0, 0.0, -20.0,
                0.0, 1.45, 0.0, 0.0, 25.0,
                0.0, 0.0, 0.7, 0.0, -20.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cine_warm_vintage",
            name = "Warm Cinema",
            category = EffectCategory.cinematic,
            icon = Icons.Rounded.CameraRoll,
            previewColor = Color(0xFFD97706),
            colorMatrix = listOf(
                1.2, 0.0, 0.0, 0.0, 18.0,
                0.0, 1.05, 0.0, 0.0, 8.0,
                0.0, 0.0, 0.8, 0.0, -12.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cine_cold_thriller",
            name = "Nordic Thriller",
            category = EffectCategory.cinematic,
            icon = Icons.Rounded.AcUnit,
            previewColor = Color(0xFF38BDF8),
            colorMatrix = listOf(
                0.88, 0.0, 0.0, 0.0, -8.0,
                0.0, 1.05, 0.0, 0.0, 4.0,
                0.0, 0.0, 1.28, 0.0, 22.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cine_sepia_drama",
            name = "Dramatic Sepia",
            category = EffectCategory.cinematic,
            icon = Icons.Rounded.FilterVintage,
            previewColor = Color(0xFFB45309),
            colorMatrix = listOf(
                0.393, 0.769, 0.189, 0.0, 0.0,
                0.349, 0.686, 0.168, 0.0, 0.0,
                0.272, 0.534, 0.131, 0.0, 0.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cine_blockbuster",
            name = "Blockbuster",
            category = EffectCategory.cinematic,
            icon = Icons.Rounded.LocalMovies,
            previewColor = Color(0xFFEF4444),
            colorMatrix = listOf(
                1.28, 0.0, 0.0, 0.0, 14.0,
                0.0, 1.02, 0.0, 0.0, 0.0,
                0.0, 0.0, 1.18, 0.0, 12.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cine_indie_pastel",
            name = "Indie Pastel",
            category = EffectCategory.cinematic,
            icon = Icons.Rounded.Palette,
            previewColor = Color(0xFFA78BFA),
            colorMatrix = listOf(
                1.08, 0.0, 0.0, 0.0, 16.0,
                0.0, 1.04, 0.0, 0.0, 12.0,
                0.0, 0.0, 1.15, 0.0, 20.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cine_desert_dune",
            name = "Desert Dune",
            category = EffectCategory.cinematic,
            icon = Icons.Rounded.Landscape,
            previewColor = Color(0xFFF97316),
            colorMatrix = listOf(
                1.3, 0.0, 0.0, 0.0, 22.0,
                0.0, 1.08, 0.0, 0.0, 10.0,
                0.0, 0.0, 0.72, 0.0, -18.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "retro_vhs_tape",
            name = "VHS 1984",
            category = EffectCategory.retro,
            icon = Icons.Outlined.Videocam,
            previewColor = Color(0xFFEC4899),
            colorMatrix = listOf(
                1.25, 0.0, 0.0, 0.0, 15.0,
                0.0, 1.1, 0.0, 0.0, 5.0,
                0.0, 0.0, 0.9, 0.0, -10.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "retro_polaroid_600",
            name = "Instant Polaroid",
            category = EffectCategory.retro,
            icon = Icons.Rounded.CropOriginal,
            previewColor = Color(0xFFFBBF24),
            colorMatrix = listOf(
                1.15, 0.0, 0.0, 0.0, 20.0,
                0.0, 1.05, 0.0, 0.0, 15.0,
                0.0, 0.0, 0.95, 0.0, 10.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "retro_kodak_portra",
            name = "Portra 400",
            category = EffectCategory.film,
            icon = Icons.Rounded.CameraAlt,
            previewColor = Color(0xFFD97706),
            colorMatrix = listOf(
                1.12, 0.0, 0.0, 0.0, 10.0,
                0.0, 1.08, 0.0, 0.0, 8.0,
                0.0, 0.0, 0.96, 0.0, -2.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "retro_fuji_velvia",
            name = "Fuji Velvia",
            category = EffectCategory.film,
            icon = Icons.Rounded.PhotoFilter,
            previewColor = Color(0xFF10B981),
            colorMatrix = listOf(
                1.18, 0.0, 0.0, 0.0, 12.0,
                0.0, 1.22, 0.0, 0.0, 14.0,
                0.0, 0.0, 1.05, 0.0, 5.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "retro_super8",
            name = "Super 8 Film",
            category = EffectCategory.film,
            icon = Icons.Rounded.MovieCreation,
            previewColor = Color(0xFFB45309),
            colorMatrix = listOf(
                1.25, 0.0, 0.0, 0.0, 24.0,
                0.0, 1.02, 0.0, 0.0, 8.0,
                0.0, 0.0, 0.82, 0.0, -14.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "retro_monochrome_high_contrast",
            name = "B&W Contrast",
            category = EffectCategory.film,
            icon = Icons.Rounded.MonochromePhotos,
            previewColor = Color(0xFF475569),
            colorMatrix = listOf(
                0.4, 0.4, 0.4, 0.0, -15.0,
                0.4, 0.4, 0.4, 0.0, -15.0,
                0.4, 0.4, 0.4, 0.0, -15.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "retro_cyanotype",
            name = "Cyanotype Blue",
            category = EffectCategory.retro,
            icon = Icons.Rounded.Water,
            previewColor = Color(0xFF0284C7),
            colorMatrix = listOf(
                0.15, 0.15, 0.15, 0.0, -10.0,
                0.3, 0.3, 0.3, 0.0, 0.0,
                0.85, 0.85, 0.85, 0.0, 35.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "retro_vaporwave_80s",
            name = "Vaporwave 80s",
            category = EffectCategory.retro,
            icon = Icons.Rounded.Waves,
            previewColor = Color(0xFFC084FC),
            colorMatrix = listOf(
                1.35, 0.0, 0.0, 0.0, 25.0,
                0.0, 0.85, 0.0, 0.0, -10.0,
                0.0, 0.0, 1.45, 0.0, 30.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "retro_duotone_magenta",
            name = "Duotone Purple",
            category = EffectCategory.retro,
            icon = Icons.Rounded.Style,
            previewColor = Color(0xFF9333EA),
            colorMatrix = listOf(
                0.7, 0.2, 0.2, 0.0, 20.0,
                0.1, 0.3, 0.1, 0.0, -5.0,
                0.3, 0.2, 0.9, 0.0, 30.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "retro_kodachrome",
            name = "Kodachrome 64",
            category = EffectCategory.film,
            icon = Icons.Rounded.Camera,
            previewColor = Color(0xFFDC2626),
            colorMatrix = listOf(
                1.25, 0.0, 0.0, 0.0, 15.0,
                0.0, 1.15, 0.0, 0.0, 10.0,
                0.0, 0.0, 0.95, 0.0, 0.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "retro_lomo_vignette",
            name = "Lomo Toy Camera",
            category = EffectCategory.retro,
            icon = Icons.Rounded.CameraEnhance,
            previewColor = Color(0xFF16A34A),
            colorMatrix = listOf(
                1.3, 0.0, 0.0, 0.0, 15.0,
                0.0, 1.25, 0.0, 0.0, 15.0,
                0.0, 0.0, 0.75, 0.0, -15.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "retro_bleach_bypass",
            name = "Bleach Bypass",
            category = EffectCategory.film,
            icon = Icons.Rounded.Grain,
            previewColor = Color(0xFF6B7280),
            colorMatrix = listOf(
                1.3, 0.0, 0.0, 0.0, -5.0,
                0.0, 1.3, 0.0, 0.0, -5.0,
                0.0, 0.0, 1.3, 0.0, -5.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "retro_cross_process",
            name = "Cross Process",
            category = EffectCategory.film,
            icon = Icons.Rounded.Tune,
            previewColor = Color(0xFF059669),
            colorMatrix = listOf(
                1.2, 0.0, 0.0, 0.0, 10.0,
                0.0, 1.35, 0.0, 0.0, 15.0,
                0.0, 0.0, 0.7, 0.0, -20.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "retro_silver_gelatin",
            name = "Silver Gelatin",
            category = EffectCategory.film,
            icon = Icons.Rounded.FilterBAndW,
            previewColor = Color(0xFFCBD5E1),
            colorMatrix = listOf(
                0.299, 0.587, 0.114, 0.0, 15.0,
                0.299, 0.587, 0.114, 0.0, 15.0,
                0.299, 0.587, 0.114, 0.0, 15.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "retro_warm_amber",
            name = "Amber Glow",
            category = EffectCategory.retro,
            icon = Icons.Rounded.LocalFireDepartment,
            previewColor = Color(0xFFEA580C),
            colorMatrix = listOf(
                1.35, 0.0, 0.0, 0.0, 25.0,
                0.0, 1.05, 0.0, 0.0, 10.0,
                0.0, 0.0, 0.65, 0.0, -25.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cyber_neon_glow",
            name = "Neon Violet",
            category = EffectCategory.glow,
            icon = Icons.Outlined.Lightbulb,
            previewColor = Color(0xFFA855F7),
            colorMatrix = listOf(
                1.3, 0.0, 0.0, 0.0, 20.0,
                0.0, 0.8, 0.0, 0.0, -10.0,
                0.0, 0.0, 1.5, 0.0, 35.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cyber_laser_cyan",
            name = "Laser Cyan",
            category = EffectCategory.cyber,
            icon = Icons.Rounded.ElectricBolt,
            previewColor = Color(0xFF06B6D4),
            colorMatrix = listOf(
                0.7, 0.0, 0.0, 0.0, -15.0,
                0.0, 1.35, 0.0, 0.0, 25.0,
                0.0, 0.0, 1.5, 0.0, 35.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cyber_glitch_flux",
            name = "Glitch Flux",
            category = EffectCategory.cyber,
            icon = Icons.Rounded.BrokenImage,
            previewColor = Color(0xFFEC4899),
            colorMatrix = listOf(
                1.4, 0.0, 0.0, 0.0, 20.0,
                0.0, 1.1, 0.0, 0.0, -5.0,
                0.0, 0.0, 1.3, 0.0, 25.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cyber_holo_grid",
            name = "Hologram",
            category = EffectCategory.cyber,
            icon = Icons.Rounded.Grid4x4,
            previewColor = Color(0xFF38BDF8),
            colorMatrix = listOf(
                0.75, 0.0, 0.0, 0.0, -10.0,
                0.0, 1.2, 0.0, 0.0, 15.0,
                0.0, 0.0, 1.45, 0.0, 30.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cyber_synthwave",
            name = "Synthwave Sunset",
            category = EffectCategory.glow,
            icon = Icons.Outlined.WbSunny,
            previewColor = Color(0xFFF43F5E),
            colorMatrix = listOf(
                1.45, 0.0, 0.0, 0.0, 30.0,
                0.0, 0.85, 0.0, 0.0, -10.0,
                0.0, 0.0, 1.4, 0.0, 25.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cyber_emerald_core",
            name = "Emerald Core",
            category = EffectCategory.glow,
            icon = Icons.Rounded.Diamond,
            previewColor = Color(0xFF34D399),
            colorMatrix = listOf(
                0.8, 0.0, 0.0, 0.0, -10.0,
                0.0, 1.4, 0.0, 0.0, 28.0,
                0.0, 0.0, 1.1, 0.0, 10.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cyber_solar_flare",
            name = "Solar Flare",
            category = EffectCategory.glow,
            icon = Icons.Rounded.SolarPower,
            previewColor = Color(0xFFFBBF24),
            colorMatrix = listOf(
                1.4, 0.0, 0.0, 0.0, 25.0,
                0.0, 1.25, 0.0, 0.0, 15.0,
                0.0, 0.0, 0.65, 0.0, -25.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cyber_aurora_borealis",
            name = "Aurora",
            category = EffectCategory.glow,
            icon = Icons.Rounded.Stream,
            previewColor = Color(0xFF2DD4BF),
            colorMatrix = listOf(
                0.8, 0.0, 0.0, 0.0, -5.0,
                0.0, 1.35, 0.0, 0.0, 25.0,
                0.0, 0.0, 1.35, 0.0, 25.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cyber_plasma_pink",
            name = "Plasma Pink",
            category = EffectCategory.glow,
            icon = Icons.Rounded.CircleNotifications,
            previewColor = Color(0xFFFF4081),
            colorMatrix = listOf(
                1.4, 0.0, 0.0, 0.0, 28.0,
                0.0, 0.9, 0.0, 0.0, 0.0,
                0.0, 0.0, 1.2, 0.0, 15.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cyber_deep_void",
            name = "Deep Space",
            category = EffectCategory.cyber,
            icon = Icons.Rounded.StarBorder,
            previewColor = Color(0xFF312E81),
            colorMatrix = listOf(
                0.75, 0.0, 0.0, 0.0, -18.0,
                0.0, 0.8, 0.0, 0.0, -12.0,
                0.0, 0.0, 1.45, 0.0, 30.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "face_sparkles",
            name = "Golden Sparkles",
            category = EffectCategory.faceAR,
            icon = Icons.Rounded.Stars,
            previewColor = Color(0xFFFDE047),
            colorMatrix = listOf(
                1.15, 0.0, 0.0, 0.0, 15.0,
                0.0, 1.1, 0.0, 0.0, 10.0,
                0.0, 0.0, 0.95, 0.0, 0.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
            isFaceTrackingRequired = true,
        ),
        ChatyCameraEffect(
            id = "face_hearts_float",
            name = "Love Hearts",
            category = EffectCategory.faceAR,
            icon = Icons.Rounded.Favorite,
            previewColor = Color(0xFFF43F5E),
            colorMatrix = listOf(
                1.2, 0.0, 0.0, 0.0, 18.0,
                0.0, 1.0, 0.0, 0.0, 4.0,
                0.0, 0.0, 1.05, 0.0, 8.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
            isFaceTrackingRequired = true,
        ),
        ChatyCameraEffect(
            id = "face_party_confetti",
            name = "Party Confetti",
            category = EffectCategory.celebration,
            icon = Icons.Rounded.Celebration,
            previewColor = Color(0xFF8B5CF6),
            colorMatrix = listOf(
                1.15, 0.0, 0.0, 0.0, 10.0,
                0.0, 1.15, 0.0, 0.0, 10.0,
                0.0, 0.0, 1.15, 0.0, 10.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "face_neon_crown",
            name = "Cyber Crown",
            category = EffectCategory.faceAR,
            icon = Icons.Rounded.MilitaryTech,
            previewColor = Color(0xFFEAB308),
            isFaceTrackingRequired = true,
        ),
        ChatyCameraEffect(
            id = "face_comic_sketch",
            name = "Comic Book",
            category = EffectCategory.cyber,
            icon = Icons.Rounded.AutoStories,
            previewColor = Color(0xFFEF4444),
            colorMatrix = listOf(
                1.4, 0.0, 0.0, 0.0, -10.0,
                0.0, 1.4, 0.0, 0.0, -10.0,
                0.0, 0.0, 1.4, 0.0, -10.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "face_pixel_retro",
            name = "Pixel Arcade",
            category = EffectCategory.cyber,
            icon = Icons.Rounded.VideogameAsset,
            previewColor = Color(0xFF10B981),
            // Original Chaty pixelate renderer (AGSL preview API 33+, nearest-neighbor
            // bake on every API); no third-party shader or asset.
            shaderKey = "pixelate",
        ),
        ChatyCameraEffect(
            id = "face_angel_halo",
            name = "Angel Halo",
            category = EffectCategory.faceAR,
            icon = Icons.Outlined.Circle,
            previewColor = Color(0xFFFEF08A),
            isFaceTrackingRequired = true,
        ),
        ChatyCameraEffect(
            id = "face_sunglasses_aviator",
            name = "Cool Shades",
            category = EffectCategory.faceAR,
            icon = Icons.Rounded.Visibility,
            previewColor = Color(0xFF0F172A),
            isFaceTrackingRequired = true,
        ),
        ChatyCameraEffect(
            id = "bg_portrait_blur",
            name = "Studio Blur",
            category = EffectCategory.background,
            icon = Icons.Rounded.BlurOn,
            previewColor = Color(0xFF6366F1),
            isSegmentationRequired = true,
            tier = EffectPerformanceTier.gpuIntensive,
        ),
        ChatyCameraEffect(
            id = "bg_virtual_office",
            name = "Modern Office",
            category = EffectCategory.background,
            icon = Icons.Rounded.Business,
            previewColor = Color(0xFF475569),
            isSegmentationRequired = true,
            tier = EffectPerformanceTier.gpuIntensive,
        ),
        ChatyCameraEffect(
            id = "bg_gradient_studio",
            name = "Chaty Gradient",
            category = EffectCategory.background,
            icon = Icons.Rounded.Gradient,
            previewColor = Color(0xFF8B5CF6),
            isSegmentationRequired = true,
            tier = EffectPerformanceTier.gpuIntensive,
        ),
        ChatyCameraEffect(
            id = "beauty_rose_glow",
            name = "Rose Glow",
            category = EffectCategory.beauty,
            icon = Icons.Rounded.LocalFlorist,
            previewColor = Color(0xFFFB7185),
            colorMatrix = listOf(
                1.15, 0.0, 0.0, 0.0, 14.0,
                0.0, 0.98, 0.0, 0.0, 2.0,
                0.0, 0.0, 1.05, 0.0, 8.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "beauty_silk",
            name = "Silk Skin",
            category = EffectCategory.beauty,
            icon = Icons.Rounded.AutoFixHigh,
            previewColor = Color(0xFFFBCFE8),
            colorMatrix = listOf(
                1.07, 0.0, 0.0, 0.0, 9.0,
                0.0, 1.07, 0.0, 0.0, 9.0,
                0.0, 0.0, 1.07, 0.0, 0.0,
                9.0, 0.0, 0.0, 0.0, 1.0,
            ),
        ),
        ChatyCameraEffect(
            id = "beauty_bronze",
            name = "Bronze Tan",
            category = EffectCategory.beauty,
            icon = Icons.Rounded.Brightness7,
            previewColor = Color(0xFFD97706),
            colorMatrix = listOf(
                1.22, 0.0, 0.0, 0.0, 18.0,
                0.0, 1.0, 0.0, 0.0, 4.0,
                0.0, 0.0, 0.8, 0.0, -12.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "beauty_pearl",
            name = "Pearl Sheen",
            category = EffectCategory.beauty,
            icon = Icons.Rounded.Diamond,
            previewColor = Color(0xFFE0F2FE),
            colorMatrix = listOf(
                1.06, 0.0, 0.0, 0.0, 10.0,
                0.0, 1.08, 0.0, 0.0, 12.0,
                0.0, 0.0, 1.14, 0.0, 18.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "beauty_velvet",
            name = "Velvet Matte",
            category = EffectCategory.beauty,
            icon = Icons.Rounded.Texture,
            previewColor = Color(0xFFA8A29E),
            colorMatrix = listOf(
                0.92, 0.06, 0.02, 0.0, 4.0,
                0.02, 0.92, 0.06, 0.0, 4.0,
                0.06, 0.02, 0.92, 0.0, 6.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "beauty_honey",
            name = "Honey Light",
            category = EffectCategory.beauty,
            icon = Icons.Rounded.LightMode,
            previewColor = Color(0xFFFCD34D),
            colorMatrix = listOf(
                1.18, 0.0, 0.0, 0.0, 14.0,
                0.0, 1.06, 0.0, 0.0, 8.0,
                0.0, 0.0, 0.88, 0.0, -6.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cine_emerald_city",
            name = "Emerald City",
            category = EffectCategory.cinematic,
            icon = Icons.Rounded.Park,
            previewColor = Color(0xFF059669),
            colorMatrix = listOf(
                0.82, 0.0, 0.0, 0.0, -8.0,
                0.0, 1.3, 0.0, 0.0, 18.0,
                0.0, 0.0, 1.0, 0.0, 4.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cine_royal_purple",
            name = "Royal Purple",
            category = EffectCategory.cinematic,
            icon = Icons.Rounded.AutoAwesome,
            previewColor = Color(0xFF7C3AED),
            colorMatrix = listOf(
                1.05, 0.0, 0.0, 0.0, 8.0,
                0.0, 0.85, 0.0, 0.0, -6.0,
                0.0, 0.0, 1.35, 0.0, 24.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cine_steel_blue",
            name = "Steel Blue",
            category = EffectCategory.cinematic,
            icon = Icons.Rounded.Tonality,
            previewColor = Color(0xFF60A5FA),
            colorMatrix = listOf(
                0.9, 0.0, 0.0, 0.0, -6.0,
                0.0, 1.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 1.25, 0.0, 14.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cine_midnight_sun",
            name = "Midnight Sun",
            category = EffectCategory.cinematic,
            icon = Icons.Rounded.DarkMode,
            previewColor = Color(0xFFF97316),
            colorMatrix = listOf(
                1.3, 0.0, 0.0, 0.0, 16.0,
                0.0, 0.95, 0.0, 0.0, 0.0,
                0.0, 0.0, 1.1, 0.0, 8.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "retro_seventies",
            name = "Retro 70s",
            category = EffectCategory.retro,
            icon = Icons.Rounded.Nightlife,
            previewColor = Color(0xFFEA580C),
            colorMatrix = listOf(
                1.1, 0.05, 0.0, 0.0, 18.0,
                0.0, 1.0, 0.02, 0.0, 10.0,
                0.05, 0.0, 0.75, 0.0, -12.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "retro_newspaper",
            name = "Old Newspaper",
            category = EffectCategory.retro,
            icon = Icons.Rounded.AutoStories,
            previewColor = Color(0xFF9CA3AF),
            colorMatrix = listOf(
                0.4, 0.4, 0.4, 0.0, 10.0,
                0.4, 0.4, 0.4, 0.0, 10.0,
                0.35, 0.35, 0.35, 0.0, 14.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "retro_seaside",
            name = "Seaside Fade",
            category = EffectCategory.retro,
            icon = Icons.Rounded.BeachAccess,
            previewColor = Color(0xFF67E8F9),
            colorMatrix = listOf(
                1.05, 0.0, 0.0, 0.0, 12.0,
                0.0, 1.1, 0.0, 0.0, 14.0,
                0.0, 0.0, 1.2, 0.0, 18.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "retro_faded_denim",
            name = "Faded Denim",
            category = EffectCategory.retro,
            icon = Icons.Rounded.Brush,
            previewColor = Color(0xFF60A5FA),
            colorMatrix = listOf(
                0.95, 0.0, 0.0, 0.0, 4.0,
                0.0, 1.0, 0.0, 0.0, 8.0,
                0.0, 0.0, 1.15, 0.0, 16.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "film_ektar",
            name = "Ektar 100",
            category = EffectCategory.film,
            icon = Icons.Rounded.PhotoFilter,
            previewColor = Color(0xFFEF4444),
            colorMatrix = listOf(
                1.28, 0.0, 0.0, 0.0, 10.0,
                0.0, 1.2, 0.0, 0.0, 8.0,
                0.0, 0.0, 1.12, 0.0, 6.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "film_cinestill",
            name = "CineStill 800",
            category = EffectCategory.film,
            icon = Icons.Rounded.Nightlight,
            previewColor = Color(0xFFF43F5E),
            colorMatrix = listOf(
                1.25, 0.0, 0.0, 0.0, 20.0,
                0.0, 1.02, 0.0, 0.0, 2.0,
                0.0, 0.0, 1.1, 0.0, 12.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "film_delta_mono",
            name = "Delta Mono",
            category = EffectCategory.film,
            icon = Icons.Rounded.FilterBAndW,
            previewColor = Color(0xFF6B7280),
            colorMatrix = listOf(
                0.35, 0.35, 0.35, 0.0, 5.0,
                0.35, 0.35, 0.35, 0.0, 5.0,
                0.35, 0.35, 0.35, 0.0, 5.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "film_slide_chrome",
            name = "Slide Chrome",
            category = EffectCategory.film,
            icon = Icons.Rounded.FilterHdr,
            previewColor = Color(0xFF38BDF8),
            colorMatrix = listOf(
                1.1, 0.0, 0.0, 0.0, 6.0,
                0.0, 1.15, 0.0, 0.0, 8.0,
                0.0, 0.0, 1.3, 0.0, 16.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "glow_cherry",
            name = "Cherry Neon",
            category = EffectCategory.glow,
            icon = Icons.Rounded.Favorite,
            previewColor = Color(0xFFF43F5E),
            colorMatrix = listOf(
                1.5, 0.0, 0.0, 0.0, 32.0,
                0.0, 0.85, 0.0, 0.0, -8.0,
                0.0, 0.0, 1.1, 0.0, 12.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "glow_lime",
            name = "Lime Pulse",
            category = EffectCategory.glow,
            icon = Icons.Rounded.Eco,
            previewColor = Color(0xFF84CC16),
            colorMatrix = listOf(
                0.85, 0.0, 0.0, 0.0, -8.0,
                0.0, 1.5, 0.0, 0.0, 30.0,
                0.0, 0.0, 0.9, 0.0, -6.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "glow_ice",
            name = "Ice Crystal",
            category = EffectCategory.glow,
            icon = Icons.Rounded.AcUnit,
            previewColor = Color(0xFFBAE6FD),
            colorMatrix = listOf(
                1.0, 0.0, 0.0, 0.0, 6.0,
                0.0, 1.15, 0.0, 0.0, 16.0,
                0.0, 0.0, 1.4, 0.0, 28.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "glow_ember",
            name = "Ember Glow",
            category = EffectCategory.glow,
            icon = Icons.Rounded.LocalFireDepartment,
            previewColor = Color(0xFFF97316),
            colorMatrix = listOf(
                1.35, 0.0, 0.0, 0.0, 24.0,
                0.0, 0.95, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.7, 0.0, -18.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cyber_purple_rain",
            name = "Purple Rain",
            category = EffectCategory.cyber,
            icon = Icons.Rounded.FilterDrama,
            previewColor = Color(0xFF9333EA),
            colorMatrix = listOf(
                1.2, 0.0, 0.0, 0.0, 16.0,
                0.0, 0.75, 0.0, 0.0, -12.0,
                0.0, 0.0, 1.5, 0.0, 32.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cyber_infrared",
            name = "Infrared",
            category = EffectCategory.cyber,
            icon = Icons.Rounded.WbIridescent,
            previewColor = Color(0xFFDC2626),
            colorMatrix = listOf(
                1.4, 0.3, 0.0, 0.0, 10.0,
                0.0, 0.6, 0.0, 0.0, -10.0,
                0.0, 0.0, 0.8, 0.0, -6.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "cyber_arcade",
            name = "Arcade CRT",
            category = EffectCategory.cyber,
            icon = Icons.Rounded.VideogameAsset,
            previewColor = Color(0xFF22D3EE),
            colorMatrix = listOf(
                1.35, 0.0, 0.0, 0.0, 22.0,
                0.0, 1.0, 0.0, 0.0, 4.0,
                0.0, 0.0, 1.45, 0.0, 28.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "glow_rainbow_sheen",
            name = "Rainbow Sheen",
            category = EffectCategory.glow,
            icon = Icons.Rounded.WbIridescent,
            previewColor = Color(0xFFF0ABFC),
            colorMatrix = listOf(
                1.12, 0.0, 0.0, 0.0, 14.0,
                0.0, 1.08, 0.0, 0.0, 12.0,
                0.0, 0.0, 1.16, 0.0, 16.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "beauty_golden_veil",
            name = "Golden Veil",
            category = EffectCategory.beauty,
            icon = Icons.Rounded.Diamond,
            previewColor = Color(0xFFEAB308),
            colorMatrix = listOf(
                1.25, 0.0, 0.0, 0.0, 18.0,
                0.0, 1.1, 0.0, 0.0, 12.0,
                0.0, 0.0, 0.8, 0.0, -8.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "beauty_sunkissed",
            name = "Sunkissed",
            category = EffectCategory.beauty,
            icon = Icons.Rounded.WbSunny,
            previewColor = Color(0xFFFB923C),
            colorMatrix = listOf(
                1.12, 0.0, 0.0, 0.0, 10.0,
                0.0, 1.02, 0.0, 0.0, 4.0,
                0.0, 0.0, 0.95, 0.0, 0.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "celebration_fireworks",
            name = "Fireworks",
            category = EffectCategory.celebration,
            icon = Icons.Rounded.EmojiEvents,
            previewColor = Color(0xFFFDE047),
            colorMatrix = listOf(
                1.2, 0.0, 0.0, 0.0, 16.0,
                0.0, 1.1, 0.0, 0.0, 12.0,
                0.0, 0.0, 1.3, 0.0, 20.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "celebration_balloons",
            name = "Balloon Pop",
            category = EffectCategory.celebration,
            icon = Icons.Rounded.Cake,
            previewColor = Color(0xFFF472B6),
            colorMatrix = listOf(
                1.15, 0.0, 0.0, 0.0, 14.0,
                0.0, 1.12, 0.0, 0.0, 12.0,
                0.0, 0.0, 1.18, 0.0, 16.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "celebration_gold_confetti",
            name = "Gold Confetti",
            category = EffectCategory.celebration,
            icon = Icons.Rounded.Stars,
            previewColor = Color(0xFFFBBF24),
            colorMatrix = listOf(
                1.3, 0.0, 0.0, 0.0, 22.0,
                0.0, 1.15, 0.0, 0.0, 14.0,
                0.0, 0.0, 0.85, 0.0, -6.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "color_boost",
            name = "Color Boost",
            category = EffectCategory.color,
            icon = Icons.Rounded.ColorLens,
            previewColor = Color(0xFF22D3EE),
            colorMatrix = listOf(
                1.3, 0.0, 0.0, 0.0, 8.0,
                0.0, 1.3, 0.0, 0.0, 8.0,
                0.0, 0.0, 1.3, 0.0, 8.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "color_muted",
            name = "Muted Earth",
            category = EffectCategory.color,
            icon = Icons.Rounded.InvertColors,
            previewColor = Color(0xFFA8A29E),
            colorMatrix = listOf(
                0.85, 0.08, 0.05, 0.0, 6.0,
                0.05, 0.85, 0.08, 0.0, 6.0,
                0.08, 0.05, 0.85, 0.0, 8.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "color_vivid_pop",
            name = "Vivid Pop",
            category = EffectCategory.color,
            icon = Icons.Rounded.Star,
            previewColor = Color(0xFFF43F5E),
            colorMatrix = listOf(
                1.45, 0.0, 0.0, 0.0, 12.0,
                0.0, 1.35, 0.0, 0.0, 10.0,
                0.0, 0.0, 1.4, 0.0, 14.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "color_mono_cool",
            name = "Cool Mono",
            category = EffectCategory.color,
            icon = Icons.Rounded.Contrast,
            previewColor = Color(0xFF94A3B8),
            colorMatrix = listOf(
                0.28, 0.28, 0.28, 0.0, 8.0,
                0.3, 0.3, 0.3, 0.0, 12.0,
                0.38, 0.38, 0.38, 0.0, 20.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "color_warm_soft",
            name = "Soft Warm",
            category = EffectCategory.color,
            icon = Icons.Rounded.WbSunny,
            previewColor = Color(0xFFFDBA74),
            colorMatrix = listOf(
                1.1, 0.0, 0.0, 0.0, 12.0,
                0.0, 1.03, 0.0, 0.0, 8.0,
                0.0, 0.0, 0.95, 0.0, 2.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "portrait_soft_focus",
            name = "Soft Focus",
            category = EffectCategory.portrait,
            icon = Icons.Rounded.EmojiEmotions,
            previewColor = Color(0xFFFDA4AF),
            colorMatrix = listOf(
                1.06, 0.0, 0.0, 0.0, 10.0,
                0.0, 1.05, 0.0, 0.0, 10.0,
                0.0, 0.0, 1.04, 0.0, 10.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "portrait_contour",
            name = "Studio Contour",
            category = EffectCategory.portrait,
            icon = Icons.Rounded.SentimentVerySatisfied,
            previewColor = Color(0xFFCBD5E1),
            colorMatrix = listOf(
                1.18, 0.0, 0.0, 0.0, -6.0,
                0.0, 1.15, 0.0, 0.0, -6.0,
                0.0, 0.0, 1.12, 0.0, -4.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "glow_sunset_haze",
            name = "Sunset Haze",
            category = EffectCategory.glow,
            icon = Icons.Rounded.Flare,
            previewColor = Color(0xFFFB923C),
            colorMatrix = listOf(
                1.28, 0.0, 0.0, 0.0, 22.0,
                0.0, 0.95, 0.0, 0.0, 4.0,
                0.0, 0.0, 1.05, 0.0, 10.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
        ChatyCameraEffect(
            id = "glow_forest_mist",
            name = "Forest Mist",
            category = EffectCategory.glow,
            icon = Icons.Rounded.Eco,
            previewColor = Color(0xFF34D399),
            colorMatrix = listOf(
                0.9, 0.0, 0.0, 0.0, -4.0,
                0.0, 1.2, 0.0, 0.0, 16.0,
                0.0, 0.0, 0.95, 0.0, 2.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            ),
        ),
    )

    /** Look up an effect by ID (falls back to Normal 'none') */
    fun getById(id: String): ChatyCameraEffect {
        return allEffects.firstOrNull { it.id == id } ?: allEffects.first()
    }

    /** Filter effects by category */
    fun getByCategory(category: EffectCategory): List<ChatyCameraEffect> {
        return allEffects.filter { it.category == category }
    }

    private val byIdMap: Map<String, ChatyCameraEffect> = allEffects.associateBy { it.id }

    /** Resolves a persisted effect id (message/story metadata). Unknown or
     *  missing ids degrade gracefully to the neutral effect. */
    fun byId(id: String?): ChatyCameraEffect =
        byIdMap[id?.trim() ?: ""] ?: allEffects.first()

    /** Pure port of the applyStored logic: resolves the interpolated 4x5
     *  color matrix a receiver should apply for a stored effect, or null when
     *  the content renders unchanged. Metadata keys: 'effect', optional
     *  'effect_intensity' (0..1). */
    fun storedColorMatrix(metadata: Map<String, Any?>?): List<Double>? {
        val id = metadata?.get("effect")?.toString()
        if (id.isNullOrEmpty() || id == "none") return null
        val effect = byId(id)
        val target = effect.colorMatrix ?: return null
        val intensity = ((metadata?.get("effect_intensity")?.toString()?.toDoubleOrNull())
            ?: effect.defaultIntensity).coerceIn(0.0, 1.0)
        return EffectEngine.interpolateMatrix(target, intensity)
    }
}
