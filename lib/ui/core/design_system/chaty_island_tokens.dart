import 'dart:ui';

/// Design tokens for Flutter Chaty Island matching `docs/island/specs/island_tokens.json`.
abstract final class ChatyIslandTokens {
  static const double compactHeightDp = 36.0;
  static const double compactMinWidthDp = 112.0;
  static const double compactDefaultWidthDp = 126.0;
  static const double compactStackedWidthDp = 142.0;
  static const double compactCornerRadiusDp = 18.0;

  static const double expandedMaxWidthDp = 384.0;
  static const double expandedHorizontalMarginDp = 16.0;
  static const double expandedCornerRadiusDp = 32.0;

  static const double expandedNotificationHeightDp = 88.0;
  static const double expandedMediaHeightDp = 168.0;
  static const double expandedCallHeightDp = 148.0;
  static const double expandedChargingHeightDp = 136.0;
  static const double expandedReplyHeightDp = 156.0;
  static const double minimalDotSizeDp = 16.0;

  static const Color compactBackground = Color(0xFF000000);
  static const Color expandedBackground = Color(0xFF0B0B0E);
  static const Color expandedStroke = Color(0x1AFFFFFF);
  static const Color textPrimary = Color(0xFFFFFFFF);
  static const Color textSecondary = Color(0xFF9E9EA7);
  static const Color textTertiary = Color(0xFF63636E);
  static const Color accentCallGreen = Color(0xFF34D399);
  static const Color accentChargingGreen = Color(0xFF10B981);
  static const Color accentActionBlue = Color(0xFF3B82F6);
  static const Color accentDestructiveRed = Color(0xFFEF4444);

  // Springs
  static const double popInStiffness = 420.0;
  static const double popInDamping = 0.72;
  static const double expandStiffness = 380.0;
  static const double expandDamping = 0.78;
  static const double collapseStiffness = 520.0;
  static const double collapseDamping = 0.90;
}
