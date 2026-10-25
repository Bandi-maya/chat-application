package com.example.chat.ui.camera

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.sqrt

/** Exact port of the lens domain from
 *  lib/features/camera/lens/lens_definition.dart.
 *
 *  A FILTER recolors the whole frame (color matrix / shader) and needs no
 *  tracking. A LENS reacts to the user or scene: it is anchored to tracked
 *  geometry. Chaty's color-matrix registry remains the FILTER domain;
 *  everything tracking-anchored lives here as a LENS.
 */
enum class EffectKind { filter, lens }

/** Where a lens attaches on tracked facial geometry. Anchors are solved in
 *  one place (FaceAnchorSolver) - never scattered through widgets. */
enum class LensAnchor {
    eyes,
    noseBridge,
    noseTip,
    forehead,
    mouth,
    ears,
    fullHead,
}

/** Interaction events a lens can subscribe to, when the tracking engine
 *  exposes them. */
enum class LensTrigger { mouthOpen, smile, browRaise, headTilt }

/** Device performance class required to run a lens safely. */
enum class CapabilityTier { any, mid, high }

/** Download lifecycle of a remote lens resource. */
enum class LensDownloadState { local, downloading, remoteOnly, failed }

/** Typed runtime state for a lens (requirement 47). */
enum class LensRuntimeState {
    none,
    loading,
    ready,
    applying,
    active,
    failed,
    unsupported,
}

/** Exact port of LensDefinition from lens_definition.dart.
 *  Immutable definition of an interactive AR lens. */
data class LensDefinition(
    val id: String,
    val name: String,
    val category: EffectCategory,
    val anchor: LensAnchor,
    val icon: ImageVector,
    val accentColor: Color,

    /** Whether the lens needs face geometry to render at all. */
    val requiresFaceTracking: Boolean = true,

    /** Whether it also needs person/background segmentation. */
    val requiresSegmentation: Boolean = false,

    /** How many faces this lens can decorate simultaneously. */
    val maxFaces: Int = 1,

    /** Front/rear support flags (some world or rear-only lenses differ). */
    val supportsFrontCamera: Boolean = true,
    val supportsRearCamera: Boolean = true,
    val supportsVideo: Boolean = true,
    val supportsCalls: Boolean = false,

    /** Minimum device class that may select this lens. */
    val minimumTier: CapabilityTier = CapabilityTier.any,

    /** Event triggers the lens responds to (empty = always-on rendering). */
    val triggers: Set<LensTrigger> = emptySet(),
) {
    val isNoLens: Boolean get() = id == "none"

    fun supportedForDevice(deviceTier: CapabilityTier): Boolean {
        return when (minimumTier) {
            CapabilityTier.any -> true
            CapabilityTier.mid -> deviceTier != CapabilityTier.any
            CapabilityTier.high -> deviceTier == CapabilityTier.high
        }
    }
}

/** Device capability classification used to gate lens availability. */
enum class DeviceCapabilityTier { lowEnd, midRange, highEnd }

val DeviceCapabilityTier.asLensRequirement: CapabilityTier
    get() = when (this) {
        DeviceCapabilityTier.lowEnd -> CapabilityTier.any
        DeviceCapabilityTier.midRange -> CapabilityTier.mid
        DeviceCapabilityTier.highEnd -> CapabilityTier.high
    }

/** Minimal Offset port for the lens domain (Dart Offset semantics). */
data class LensOffset(val dx: Float, val dy: Float) {
    operator fun plus(o: LensOffset) = LensOffset(dx + o.dx, dy + o.dy)
    operator fun minus(o: LensOffset) = LensOffset(dx - o.dx, dy - o.dy)
    operator fun times(k: Float) = LensOffset(dx * k, dy * k)

    fun distanceTo(other: LensOffset): Float {
        val ddx = dx - other.dx
        val ddy = dy - other.dy
        return sqrt(ddx * ddx + ddy * ddy)
    }
}

/** Minimal Rect port for the lens domain (Dart Rect semantics). */
data class LensRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val center: LensOffset get() = LensOffset((left + right) / 2f, (top + bottom) / 2f)

    companion object {
        /** Dart Rect.fromPoints (normalizes corners). */
        fun fromPoints(a: LensOffset, b: LensOffset) = LensRect(
            minOf(a.dx, b.dx), minOf(a.dy, b.dy),
            maxOf(a.dx, b.dx), maxOf(a.dy, b.dy),
        )
    }
}

/** Minimal Size port for the lens domain (Dart Size semantics). */
data class LensSize(val width: Float, val height: Float)

/** Exact port of FacePose from lens_definition.dart.
 *
 *  One solved facial pose, already mapped into PREVIEW coordinates.
 *  All fields are in logical pixels of the preview surface unless noted.
 *  [rollRadians]/[yawRadians]/[pitchRadians] describe head orientation.
 */
data class FacePose(
    val trackingId: Int,
    val headBounds: LensRect,
    val leftEye: LensOffset,
    val rightEye: LensOffset,
    val noseBase: LensOffset,
    val mouthCenter: LensOffset,
    val rollRadians: Double,
    val yawRadians: Double,
    val pitchRadians: Double,
    val confidence: Double,
) {
    val eyesMidpoint: LensOffset
        get() = LensOffset(
            (leftEye.dx + rightEye.dx) / 2.0f,
            (leftEye.dy + rightEye.dy) / 2.0f,
        )

    val interocular: Float get() = leftEye.distanceTo(rightEye)
}

/** Tracking health used to decide whether lenses render or gracefully fade
 *  (requirement 25). */
enum class TrackingHealth { tracked, temporarilyLost, lost }

/** Adapts the pre-existing color-matrix registry entries into the FILTER side
 *  of the split, so the working filter system is retained untouched. */
fun filterCatalog(): List<ChatyCameraEffect> = EffectRegistry.allEffects.filter {
    !it.isFaceTrackingRequired && it.shaderKey == null
}
