import 'package:flutter/services.dart';

/// Purposeful haptic grammar used throughout Chaty.
///
/// Do not vibrate for scrolling or every tap. Haptics communicate selection,
/// thresholds and outcome states only.
abstract final class ChatyHaptics {
  static Future<void> selection() => HapticFeedback.selectionClick();
  static Future<void> light() => HapticFeedback.lightImpact();
  static Future<void> threshold() => HapticFeedback.mediumImpact();
  static Future<void> success() => HapticFeedback.lightImpact();
  static Future<void> warning() => HapticFeedback.mediumImpact();
  static Future<void> error() => HapticFeedback.heavyImpact();
}
