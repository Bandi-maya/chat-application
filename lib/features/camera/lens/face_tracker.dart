import 'dart:ui' show Offset, Rect, Size;

import 'package:flutter/foundation.dart' show immutable, visibleForTesting;

import 'lens_definition.dart';

/// Pure-math pipeline that turns raw tracker output into stable, correctly
/// mirrored preview-space poses and solves lens anchor transforms.
///
/// Everything here is deterministic and unit-tested; no widget, camera or
/// platform dependency. The platform tracker (ML Kit on Android/iOS) only
/// supplies raw face data in IMAGE coordinates.
class FaceAnchorSolver {
  FaceAnchorSolver({
    this.smoothingAlpha = 0.45,
    this.gracePeriod = const Duration(milliseconds: 350),
  }) : assert(
         smoothingAlpha > 0 && smoothingAlpha <= 1,
         'smoothingAlpha must be in (0, 1]',
       );

  /// Exponential low-pass factor: higher = more responsive, lower = smoother.
  final double smoothingAlpha;

  /// How long a vanished face is held by prediction before lenses fade.
  final Duration gracePeriod;

  _SmoothedFace? _smoothed;

  TrackingHealth get health => _health;
  TrackingHealth _health = TrackingHealth.lost;

  /// Feeds one raw detection (or null when the frame had no face).
  ///
  /// [mirrored] must be true for front-camera previews where the image is
  /// displayed flipped; solving happens after mirroring so anchors can never
  /// move opposite to the user (requirement 42).
  /// [cropToPreview] maps image space onto the cover-cropped preview space
  /// (requirement 44): scale then offset, never stretch.
  List<FacePose> update({
    required int nowMs,
    @visibleForTesting Map<int, RawFace>? rawFaces,
    bool mirrored = true,
    required Size imageExtent,
    required Rect previewCrop,
    required double confidenceScale,
  }) {
    if (rawFaces == null || rawFaces.isEmpty) {
      final held = _health == TrackingHealth.tracked &&
          nowMs - (_lastSeenMs ?? nowMs) <= gracePeriod.inMilliseconds;
      _health = held ? TrackingHealth.temporarilyLost : TrackingHealth.lost;
      if (!held) {
        _smoothed = null;
        return const <FacePose>[];
      }
      // Hold last pose during the grace window so brief occlusions do not
      // make lenses blink out of existence.
      return <FacePose>[if (_smoothed != null) _smoothed!.pose];
    }

    _lastSeenMs = nowMs;
    _health = TrackingHealth.tracked;

    final poses = <FacePose>[];
    for (final entry in rawFaces.entries) {
      final existing = _smoothed?.trackingId == entry.key ? _smoothed : null;
      final pose = _solveAndSmooth(
        raw: entry.value,
        trackingId: entry.key,
        previous: existing,
        mirrored: mirrored,
        imageExtent: imageExtent,
        previewCrop: previewCrop,
        confidenceScale: confidenceScale,
      );
      poses.add(pose);
      if (_smoothed == null || _smoothed!.trackingId == entry.key) {
        _smoothed = _SmoothedFace(entry.key, pose);
      }
    }
    if (_smoothed != null && !poses.any((p) => p.trackingId == _smoothed!.trackingId)) {
      final replacement = poses.isNotEmpty ? poses.first : null;
      _smoothed = replacement == null
          ? null
          : _SmoothedFace(replacement.trackingId, replacement);
    }
    return poses;
  }

  int? _lastSeenMs;

  FacePose _solveAndSmooth({
    required RawFace raw,
    required int trackingId,
    required _SmoothedFace? previous,
    required bool mirrored,
    required Size imageExtent,
    required Rect previewCrop,
    required double confidenceScale,
  }) {
    Offset map(Offset p) {
      final x = mirrored ? imageExtent.width - p.dx : p.dx;
      final sx = previewCrop.width / imageExtent.width;
      final sy = previewCrop.height / imageExtent.height;
      return Offset(
        previewCrop.left + x * sx,
        previewCrop.top + p.dy * sy,
      );
    }

    Rect mapRect(Rect r) {
      final topLeft = map(r.topLeft);
      final bottomRight = map(r.bottomRight);
      return Rect.fromPoints(topLeft, bottomRight);
    }

    Offset smooth(Offset current, Offset? previousValue) {
      if (previousValue == null) return current;
      // Lerp toward the measurement — exponential low-pass (requirement 23).
      return Offset.lerp(previousValue, current, smoothingAlpha)!;
    }

    final headBounds = mapRect(raw.headBounds);
    final prev = previous?.pose;
    final leftEye = smooth(map(raw.leftEye), prev?.leftEye);
    final rightEye = smooth(map(raw.rightEye), prev?.rightEye);
    final noseBase = smooth(map(raw.noseBase), prev?.noseBase);
    final mouthCenter = smooth(map(raw.mouthCenter), prev?.mouthCenter);

    return FacePose(
      trackingId: trackingId,
      headBounds: headBounds,
      leftEye: leftEye,
      rightEye: rightEye,
      noseBase: noseBase,
      mouthCenter: mouthCenter,
      rollRadians: _lerpAngle(prev?.rollRadians ?? raw.rollRadians, raw.rollRadians, smoothingAlpha),
      yawRadians: _lerpAngle(prev?.yawRadians ?? raw.yawRadians, raw.yawRadians, smoothingAlpha),
      pitchRadians: _lerpAngle(prev?.pitchRadians ?? raw.pitchRadians, raw.pitchRadians, smoothingAlpha),
      confidence: raw.confidence * confidenceScale.clamp(0.0, 1.0),
    );
  }

  static double _lerpAngle(double a, double b, double t) => a + (b - a) * t;
}

class _SmoothedFace {
  const _SmoothedFace(this.trackingId, this.pose);

  final int trackingId;
  final FacePose pose;
}

/// Raw, unprocessed detection supplied by the platform tracker in IMAGE space.
@immutable
class RawFace {
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

  const RawFace({
    required this.trackingId,
    required this.headBounds,
    required this.leftEye,
    required this.rightEye,
    required this.noseBase,
    required this.mouthCenter,
    this.rollRadians = 0,
    this.yawRadians = 0,
    this.pitchRadians = 0,
    this.confidence = 1,
  });
}

extension FacePoseX on FacePose {
  /// Eye midpoint — anchor root for glasses-style lenses.
  Offset get eyesMidpoint => Offset(
    (leftEye.dx + rightEye.dx) / 2,
    (leftEye.dy + rightEye.dy) / 2,
  );

  /// Interocular distance — the natural scale unit for eye-anchored lenses.
  double get interocular =>
      (leftEye - rightEye).distance.abs().clamp(8.0, double.infinity);
}
