import 'package:flutter/material.dart';

/// Canonical Chaty motion language.
///
/// Motion communicates hierarchy and state; it is never required to understand
/// content. Callers must respect [MediaQuery.disableAnimations] and provide a
/// static state when motion is disabled.
abstract final class ChatyMotion {
  static const Duration instant = Duration(milliseconds: 80);
  static const Duration fast = Duration(milliseconds: 140);
  static const Duration base = Duration(milliseconds: 220);
  static const Duration emphasis = Duration(milliseconds: 320);

  static const Curve standard = Curves.easeInOutCubic;
  static const Curve enter = Curves.easeOutCubic;
  static const Curve exit = Curves.easeInCubic;
  static const Curve springSoft = Curves.easeOutBack;

  /// High-precision Apple/Emil Kowalski inspired spring curves
  static const Curve springSnappy = Cubic(0.16, 1.0, 0.3, 1.0);
  static const Curve springGentle = Cubic(0.2, 1.0, 0.25, 1.0);
  static const Curve microPress = Curves.easeOutQuad;

  static Duration duration(BuildContext context, {Duration preferred = base}) {
    return MediaQuery.maybeOf(context)?.disableAnimations == true
        ? Duration.zero
        : preferred;
  }

  static Curve curve(BuildContext context, {Curve preferred = springSnappy}) {
    return MediaQuery.maybeOf(context)?.disableAnimations == true
        ? Curves.linear
        : preferred;
  }
}
