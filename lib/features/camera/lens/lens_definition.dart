import 'package:flutter/material.dart';

import '../effects/effect_model.dart';
import '../effects/effect_registry.dart';

/// The two effect concepts are deliberately separate domains.
///
/// A FILTER recolors the whole frame (color matrix / shader) and needs no
/// tracking. A LENS reacts to the user or scene: it is anchored to tracked
/// geometry and must follow translation, scale and head rotation. Chaty's
/// pre-existing color-matrix registry remains the FILTER domain; everything
/// tracking-anchored lives here as a LENS.
enum EffectKind { filter, lens }

/// Where a lens attaches on tracked facial geometry. Anchors are solved in one
/// place ([FaceAnchorSolver]) — never scattered through widgets.
enum LensAnchor {
  eyes,
  noseBridge,
  noseTip,
  forehead,
  mouth,
  ears,
  fullHead,
}

/// Interaction events a lens can subscribe to, when the tracking engine
/// exposes them.
enum LensTrigger { mouthOpen, smile, browRaise, headTilt }

/// Device performance class required to run a lens safely.
enum CapabilityTier { any, mid, high }

/// Download lifecycle of a remote lens resource.
enum LensDownloadState { local, downloading, remoteOnly, failed }

/// Typed runtime state for a lens (requirement 47).
enum LensRuntimeState {
  none,
  loading,
  ready,
  applying,
  active,
  failed,
  unsupported,
}

/// Immutable definition of an interactive AR lens.
class LensDefinition {
  final String id;
  final String name;
  final EffectCategory category;
  final LensAnchor anchor;
  final IconData icon;
  final Color accentColor;

  /// Whether the lens needs face geometry to render at all.
  final bool requiresFaceTracking;

  /// Whether it also needs person/background segmentation.
  final bool requiresSegmentation;

  /// How many faces this lens can decorate simultaneously.
  final int maxFaces;

  /// Front/rear support flags (some world or rear-only lenses differ).
  final bool supportsFrontCamera;
  final bool supportsRearCamera;
  final bool supportsVideo;
  final bool supportsCalls;

  /// Minimum device class that may select this lens.
  final CapabilityTier minimumTier;

  /// Event triggers the lens responds to (empty = always-on rendering).
  final Set<LensTrigger> triggers;

  const LensDefinition({
    required this.id,
    required this.name,
    required this.category,
    required this.anchor,
    required this.icon,
    required this.accentColor,
    this.requiresFaceTracking = true,
    this.requiresSegmentation = false,
    this.maxFaces = 1,
    this.supportsFrontCamera = true,
    this.supportsRearCamera = true,
    this.supportsVideo = true,
    this.supportsCalls = false,
    this.minimumTier = CapabilityTier.any,
    this.triggers = const <LensTrigger>{},
  });

  bool get isNoLens => id == 'none';

  bool supportedForDevice(CapabilityTier deviceTier) {
    switch (minimumTier) {
      case CapabilityTier.any:
        return true;
      case CapabilityTier.mid:
        return deviceTier != CapabilityTier.any;
      case CapabilityTier.high:
        return deviceTier == CapabilityTier.high;
    }
  }
}

/// Device capability classification used to gate lens availability.
enum DeviceCapabilityTier { lowEnd, midRange, highEnd }

extension DeviceCapabilityTierX on DeviceCapabilityTier {
  CapabilityTier get asLensRequirement {
    switch (this) {
      case DeviceCapabilityTier.lowEnd:
        return CapabilityTier.any;
      case DeviceCapabilityTier.midRange:
        return CapabilityTier.mid;
      case DeviceCapabilityTier.highEnd:
        return CapabilityTier.high;
    }
  }
}

/// One solved facial pose, already mapped into PREVIEW coordinates.
///
/// All fields are in logical pixels of the preview surface unless noted.
/// [rollRadians]/[yawRadians]/[pitchRadians] describe head orientation.
class FacePose {
  final int trackingId;
  final Rect headBounds;
  final Offset leftEye;
  final Offset rightEye;
  final Offset noseBase;
  final Offset mouthCenter;
  final double rollRadians;
  final double yawRadians;
  final double pitchRadians;
  final double confidence;

  const FacePose({
    required this.trackingId,
    required this.headBounds,
    required this.leftEye,
    required this.rightEye,
    required this.noseBase,
    required this.mouthCenter,
    required this.rollRadians,
    required this.yawRadians,
    required this.pitchRadians,
    required this.confidence,
  });

  Offset get eyesMidpoint => Offset(
    (leftEye.dx + rightEye.dx) / 2.0,
    (leftEye.dy + rightEye.dy) / 2.0,
  );

  double get interocular => (leftEye - rightEye).distance;
}

/// Tracking health used to decide whether lenses render or gracefully fade
/// (requirement 25).
enum TrackingHealth { tracked, temporarilyLost, lost }

/// Adapts the pre-existing color-matrix registry entries into the FILTER side
/// of the split, so the working filter system is retained untouched.
Iterable<ChatyCameraEffect> filterCatalog() => EffectRegistry.allEffects.where(
  (effect) => !effect.isFaceTrackingRequired && effect.shaderKey == null,
);
