import 'package:flutter/material.dart';

/// Categories of available camera and call effects
enum EffectCategory {
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

/// Adaptive performance tier required by the effect
enum EffectPerformanceTier {
  lightweight,
  standard,
  gpuIntensive,
}

/// Model definition for an individual camera or video-call filter/lens
class ChatyCameraEffect {
  final String id;
  final String name;
  final EffectCategory category;
  final IconData icon;
  final Color previewColor;
  final List<double>? colorMatrix;
  final String? shaderKey;
  final bool isFaceTrackingRequired;
  final bool isSegmentationRequired;
  final EffectPerformanceTier tier;
  final double defaultIntensity;

  const ChatyCameraEffect({
    required this.id,
    required this.name,
    required this.category,
    required this.icon,
    required this.previewColor,
    this.colorMatrix,
    this.shaderKey,
    this.isFaceTrackingRequired = false,
    this.isSegmentationRequired = false,
    this.tier = EffectPerformanceTier.lightweight,
    this.defaultIntensity = 1.0,
  });
}
