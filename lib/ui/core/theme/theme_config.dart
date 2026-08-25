import 'package:flutter/material.dart';
import 'semantic_colors.dart';
import 'chat_theme_tokens.dart';
import '../bubbles/bubble_style_id.dart';
import '../ticks/delivery_icon_style.dart';

enum UILayoutMode { classic, compact, expressive, focus, tabletDesktop }

enum AppNavigationMode {
  bottomNav,
  topWhatsAppBar,
  floatingIslandRail,
  perspective3DDrawer,
  modernSideMenu,
  gestureTabs,
  compactRail,
}

class ThemeConfig {
  final String id;
  final String name;
  final Brightness brightness;
  final Color accentColor;
  final Color backgroundColor;
  final Color surfaceColor;
  final Color cardColor;
  final Color primaryTextColor;
  final Color secondaryTextColor;
  final Color outgoingBubbleColor;
  final Color incomingBubbleColor;
  final Color outgoingTextColor;
  final Color incomingTextColor;
  final Color linkColor;
  final Color dangerColor;
  final Color successColor;
  final double cornerRadius;
  final double density; // 0.8 to 1.3
  final double fontScale; // 0.85 to 1.3
  final AppNavigationMode navigationMode;
  final UILayoutMode layoutMode;
  final BubbleStyleId bubbleStyle;
  final DeliveryIconStyle deliveryTickStyle;
  final String tickStyle; // preserved as string alias
  final String
  wallpaperId; // 'none', 'subtle_dots', 'geometric', 'gradient_mesh', 'constellation'
  final double animationLevel; // 0.0 to 1.0
  final bool highContrast;

  const ThemeConfig({
    required this.id,
    required this.name,
    required this.brightness,
    required this.accentColor,
    required this.backgroundColor,
    required this.surfaceColor,
    required this.cardColor,
    required this.primaryTextColor,
    required this.secondaryTextColor,
    required this.outgoingBubbleColor,
    required this.incomingBubbleColor,
    required this.outgoingTextColor,
    required this.incomingTextColor,
    required this.linkColor,
    required this.dangerColor,
    required this.successColor,
    this.cornerRadius = 16.0,
    this.density = 1.0,
    this.fontScale = 1.0,
    this.navigationMode = AppNavigationMode.bottomNav,
    this.layoutMode = UILayoutMode.classic,
    this.bubbleStyle = BubbleStyleId.stock,
    this.deliveryTickStyle = DeliveryIconStyle.rcIos11,
    this.tickStyle = 'RC iOS 11',
    this.wallpaperId = 'subtle_dots',
    this.animationLevel = 1.0,
    this.highContrast = false,
  });

  /// Fraction of the available width a chat bubble may occupy, derived from
  /// the UI layout preset. Consumed by MessageBubble so the Layout Mode
  /// setting has a real, visible effect (distinct bubble widths per preset).
  double get bubbleMaxWidthFactor => switch (layoutMode) {
    UILayoutMode.classic => 0.80,
    UILayoutMode.compact => 0.72,
    UILayoutMode.expressive => 0.86,
    UILayoutMode.focus => 0.66,
    UILayoutMode.tabletDesktop => 0.80,
  };

  ThemeConfig copyWith({
    String? id,
    String? name,
    Brightness? brightness,
    Color? accentColor,
    Color? backgroundColor,
    Color? surfaceColor,
    Color? cardColor,
    Color? primaryTextColor,
    Color? secondaryTextColor,
    Color? outgoingBubbleColor,
    Color? incomingBubbleColor,
    Color? outgoingTextColor,
    Color? incomingTextColor,
    Color? linkColor,
    Color? dangerColor,
    Color? successColor,
    double? cornerRadius,
    double? density,
    double? fontScale,
    bool? highContrast,
    AppNavigationMode? navigationMode,
    UILayoutMode? layoutMode,
    BubbleStyleId? bubbleStyle,
    DeliveryIconStyle? deliveryTickStyle,
    String? tickStyle,
    String? wallpaperId,
    double? animationLevel,
  }) {
    return ThemeConfig(
      id: id ?? this.id,
      name: name ?? this.name,
      brightness: brightness ?? this.brightness,
      accentColor: accentColor ?? this.accentColor,
      backgroundColor: backgroundColor ?? this.backgroundColor,
      surfaceColor: surfaceColor ?? this.surfaceColor,
      cardColor: cardColor ?? this.cardColor,
      primaryTextColor: primaryTextColor ?? this.primaryTextColor,
      secondaryTextColor: secondaryTextColor ?? this.secondaryTextColor,
      outgoingBubbleColor: outgoingBubbleColor ?? this.outgoingBubbleColor,
      incomingBubbleColor: incomingBubbleColor ?? this.incomingBubbleColor,
      outgoingTextColor: outgoingTextColor ?? this.outgoingTextColor,
      incomingTextColor: incomingTextColor ?? this.incomingTextColor,
      linkColor: linkColor ?? this.linkColor,
      dangerColor: dangerColor ?? this.dangerColor,
      successColor: successColor ?? this.successColor,
      cornerRadius: cornerRadius ?? this.cornerRadius,
      density: density ?? this.density,
      fontScale: fontScale != null && fontScale > 0.0 && fontScale.isFinite
          ? fontScale.clamp(0.5, 2.0)
          : this.fontScale,
      highContrast: highContrast ?? this.highContrast,
      navigationMode: navigationMode ?? this.navigationMode,
      layoutMode: layoutMode ?? this.layoutMode,
      bubbleStyle: bubbleStyle ?? this.bubbleStyle,
      deliveryTickStyle: deliveryTickStyle ?? this.deliveryTickStyle,
      tickStyle: tickStyle ?? this.tickStyle,
      wallpaperId: wallpaperId ?? this.wallpaperId,
      animationLevel: animationLevel ?? this.animationLevel,
    );
  }

  // Calculate contrast ratio helper (L1 + 0.05) / (L2 + 0.05)
  static double calculateContrastRatio(Color foreground, Color background) {
    double lum1 = foreground.computeLuminance();
    double lum2 = background.computeLuminance();
    double brightest = lum1 > lum2 ? lum1 : lum2;
    double darkest = lum1 > lum2 ? lum2 : lum1;
    return (brightest + 0.05) / (darkest + 0.05);
  }

  bool get hasContrastIssue {
    final double textRatio = calculateContrastRatio(
      primaryTextColor,
      backgroundColor,
    );
    // Secondary text (timestamps, subtitles, hints) on the canvas — this pair
    // was previously unguarded, so custom themes could render invisible
    // metadata text.
    final double secondaryTextRatio = calculateContrastRatio(
      secondaryTextColor,
      backgroundColor,
    );
    // Primary text on elevated surfaces (app bars, cards, sheets).
    final double textOnSurfaceRatio = calculateContrastRatio(
      primaryTextColor,
      surfaceColor,
    );
    final double outgoingBubbleRatio = calculateContrastRatio(
      outgoingTextColor,
      outgoingBubbleColor,
    );
    final double incomingBubbleRatio = calculateContrastRatio(
      incomingTextColor,
      incomingBubbleColor,
    );
    return textRatio < 4.5 ||
        secondaryTextRatio < 3.0 ||
        textOnSurfaceRatio < 3.5 ||
        outgoingBubbleRatio < 3.5 ||
        incomingBubbleRatio < 3.5;
  }

  Color get onAccentColor => accentColor.computeLuminance() > 0.5
      ? const Color(0xFF000000)
      : const Color(0xFFFFFFFF);
  Color get onSurfaceColor => primaryTextColor;
  Color get onBackgroundColor => primaryTextColor;

  /// Dynamically inverts brightness and adapts all colors without hardcoding presets.
  ThemeConfig toggleBrightness() {
    final bool isDark = brightness == Brightness.dark;
    final targetBrightness = isDark ? Brightness.light : Brightness.dark;

    if (isDark) {
      // Transition from Dark -> Light
      final lightBg = const Color(0xFFFFFFFF);
      final lightSurface = const Color(0xFFF4F4F5);
      final lightCard = const Color(0xFFFFFFFF);
      final lightPrimaryText = const Color(0xFF09090B);
      final lightSecondaryText = const Color(0xFF71717A);

      final adaptedAccent = accentColor.computeLuminance() > 0.85
          ? const Color(0xFF09090B)
          : accentColor;

      return copyWith(
        brightness: targetBrightness,
        backgroundColor: lightBg,
        surfaceColor: lightSurface,
        cardColor: lightCard,
        primaryTextColor: lightPrimaryText,
        secondaryTextColor: lightSecondaryText,
        accentColor: adaptedAccent,
        incomingBubbleColor: const Color(0xFFF4F4F5),
        incomingTextColor: const Color(0xFF09090B),
        outgoingBubbleColor: adaptedAccent,
        outgoingTextColor: adaptedAccent.computeLuminance() > 0.5
            ? Colors.black
            : Colors.white,
      );
    } else {
      // Transition from Light -> Dark
      final darkBg = const Color(0xFF000000);
      final darkSurface = const Color(0xFF121212);
      final darkCard = const Color(0xFF1C1C1E);
      final darkPrimaryText = const Color(0xFFFFFFFF);
      final darkSecondaryText = const Color(0xFFA1A1AA);

      final adaptedAccent = accentColor.computeLuminance() < 0.15
          ? const Color(0xFFFFFFFF)
          : accentColor;

      return copyWith(
        brightness: targetBrightness,
        backgroundColor: darkBg,
        surfaceColor: darkSurface,
        cardColor: darkCard,
        primaryTextColor: darkPrimaryText,
        secondaryTextColor: darkSecondaryText,
        accentColor: adaptedAccent,
        incomingBubbleColor: const Color(0xFF18181B),
        incomingTextColor: const Color(0xFFF4F4F5),
        outgoingBubbleColor: adaptedAccent.computeLuminance() > 0.85
            ? const Color(0xFF27272A)
            : adaptedAccent,
        outgoingTextColor: Colors.white,
      );
    }
  }

  ThemeData toThemeData() {
    final bool isDark = brightness == Brightness.dark;

    final appColors = AppColors(
      brightness: brightness,
      primary: accentColor,
      onPrimary: onAccentColor,
      secondary: accentColor.withValues(alpha: 0.8),
      onSecondary: onAccentColor,
      accent: accentColor,
      onAccent: onAccentColor,
      background: backgroundColor,
      onBackground: primaryTextColor,
      surface: surfaceColor,
      onSurface: primaryTextColor,
      surfaceSecondary: cardColor,
      onSurfaceSecondary: secondaryTextColor,
      surfaceElevated: cardColor,
      onSurfaceElevated: primaryTextColor,
      foreground: primaryTextColor,
      foregroundSecondary: secondaryTextColor,
      foregroundTertiary: secondaryTextColor.withValues(alpha: 0.65),
      border: isDark ? const Color(0xFF27272A) : const Color(0xFFE4E4E7),
      borderSubtle: isDark ? const Color(0xFF18181B) : const Color(0xFFF4F4F5),
      divider: isDark ? const Color(0xFF27272A) : const Color(0xFFE5E7EB),
      input: surfaceColor,
      inputBorder: isDark ? const Color(0xFF27272A) : const Color(0xFFE4E4E7),
      inputFill: isDark ? const Color(0xFF18181B) : const Color(0xFFF4F4F5),
      disabled: isDark ? const Color(0xFF27272A) : const Color(0xFFE4E4E7),
      disabledForeground: isDark
          ? const Color(0xFF52525B)
          : const Color(0xFFA1A1AA),
      selected: accentColor,
      onSelected: onAccentColor,
      hover: accentColor.withValues(alpha: 0.08),
      pressed: accentColor.withValues(alpha: 0.16),
      success: successColor,
      onSuccess: Colors.white,
      warning: const Color(0xFFF59E0B),
      onWarning: Colors.black,
      error: dangerColor,
      onError: Colors.white,
      info: linkColor,
      onInfo: Colors.white,
      link: linkColor,
      icon: primaryTextColor,
      iconSecondary: secondaryTextColor,
      shadow: isDark ? const Color(0x66000000) : const Color(0x0F000000),
    );

    final chatColors = ChatColors(
      incomingBubble: incomingBubbleColor,
      incomingText: incomingTextColor,
      outgoingBubble: outgoingBubbleColor,
      outgoingText: outgoingTextColor,
      replySurface: isDark ? const Color(0xFF0F172A) : const Color(0xFFF1F5F9),
      replyBorder: accentColor,
      composerSurface: surfaceColor,
      composerBorder: isDark
          ? const Color(0xFF27272A)
          : const Color(0xFFE4E4E7),
      messageMetadata: secondaryTextColor.withValues(alpha: 0.7),
      deliveryTick: secondaryTextColor.withValues(alpha: 0.8),
      readTick: tickStyle == 'Neon'
          ? const Color(0xFF39FF14)
          : (tickStyle == 'iOS Style'
                ? const Color(0xFF34C759)
                : (tickStyle == 'Minimal'
                      ? primaryTextColor
                      : const Color(0xFF38BDF8))),
      reactionSurface: cardColor,
      reactionBorder: accentColor.withValues(alpha: 0.25),
      reactionCount: primaryTextColor,
      mentionBackground: accentColor.withValues(alpha: 0.18),
      selectionBackground: accentColor.withValues(alpha: 0.22),
      chatWallpaperBackground: backgroundColor,
      voiceNoteWaveform: accentColor.withValues(alpha: 0.6),
      voiceNoteProgress: accentColor,
      voiceNoteButton: accentColor,
      systemMessageBackground: surfaceColor.withValues(alpha: 0.8),
      systemMessageText: secondaryTextColor,
      taskCardSurface: cardColor,
      taskCardBorder: accentColor.withValues(alpha: 0.3),
    );

    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      scaffoldBackgroundColor: backgroundColor,
      extensions: <ThemeExtension<dynamic>>[appColors, chatColors],
      colorScheme: ColorScheme(
        brightness: brightness,
        primary: accentColor,
        onPrimary: onAccentColor,
        secondary: accentColor.withValues(alpha: 0.8),
        onSecondary: onAccentColor,
        error: dangerColor,
        onError: Colors.white,
        surface: surfaceColor,
        onSurface: primaryTextColor,
      ),
      cardTheme: CardThemeData(
        color: cardColor,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(cornerRadius),
        ),
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: surfaceColor,
        elevation: 0,
        scrolledUnderElevation: 1.0,
        iconTheme: IconThemeData(color: primaryTextColor),
        titleTextStyle: TextStyle(
          color: primaryTextColor,
          fontSize: 19 * fontScale,
          fontWeight: FontWeight.w600,
          letterSpacing: -0.3,
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: accentColor,
          foregroundColor: onAccentColor,
          elevation: 1,
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(
              cornerRadius > 14 ? cornerRadius : 16,
            ),
          ),
          textStyle: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: primaryTextColor,
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
          side: BorderSide(
            color: brightness == Brightness.dark
                ? const Color(0xFF334155)
                : const Color(0xFFE2E8F0),
            width: 1.4,
          ),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(
              cornerRadius > 14 ? cornerRadius : 16,
            ),
          ),
          textStyle: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor:
              (brightness == Brightness.dark &&
                  accentColor.computeLuminance() > 0.8)
              ? primaryTextColor
              : accentColor,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
          textStyle: const TextStyle(
            fontWeight: FontWeight.w700,
            fontSize: 14.5,
          ),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: accentColor,
          foregroundColor: onAccentColor,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(
              cornerRadius > 14 ? cornerRadius : 16,
            ),
          ),
          textStyle: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15),
        ),
      ),
      floatingActionButtonTheme: FloatingActionButtonThemeData(
        backgroundColor: accentColor,
        foregroundColor: onAccentColor,
        elevation: 3,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      ),
      chipTheme: ChipThemeData(
        backgroundColor: surfaceColor,
        selectedColor: accentColor.withValues(alpha: 0.2),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        labelStyle: TextStyle(
          color: primaryTextColor,
          fontWeight: FontWeight.w600,
        ),
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: surfaceColor,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
      ),
      bottomSheetTheme: BottomSheetThemeData(
        backgroundColor: surfaceColor,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: surfaceColor,
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 14,
        ),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: BorderSide(
            color: brightness == Brightness.dark
                ? const Color(0xFF27272A)
                : const Color(0xFFE4E4E7),
          ),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: BorderSide(
            color: brightness == Brightness.dark
                ? const Color(0xFF27272A)
                : const Color(0xFFE4E4E7),
          ),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: BorderSide(color: accentColor, width: 1.5),
        ),
      ),
      textTheme: TextTheme(
        headlineLarge: TextStyle(
          color: primaryTextColor,
          fontSize: 28 * fontScale,
          fontWeight: FontWeight.bold,
          letterSpacing: -0.5,
        ),
        headlineMedium: TextStyle(
          color: primaryTextColor,
          fontSize: 22 * fontScale,
          fontWeight: FontWeight.bold,
          letterSpacing: -0.3,
        ),
        titleLarge: TextStyle(
          color: primaryTextColor,
          fontSize: 18 * fontScale,
          fontWeight: FontWeight.w600,
        ),
        titleMedium: TextStyle(
          color: primaryTextColor,
          fontSize: 15 * fontScale,
          fontWeight: FontWeight.w600,
        ),
        bodyLarge: TextStyle(
          color: primaryTextColor,
          fontSize: 15 * fontScale,
          height: 1.35,
        ),
        bodyMedium: TextStyle(
          color: secondaryTextColor,
          fontSize: 13.5 * fontScale,
          height: 1.35,
        ),
        labelLarge: TextStyle(
          color: primaryTextColor,
          fontSize: 13.5 * fontScale,
          fontWeight: FontWeight.w600,
        ),
        labelSmall: TextStyle(
          color: secondaryTextColor,
          fontSize: 11 * fontScale,
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // Serialization
  //
  // Self-contained (does NOT depend on ThemePresets, to avoid an import cycle):
  // colors are stored as 32-bit ARGB ints via [Color.toARGB32] and rebuilt with
  // the `Color(int)` constructor; enums are stored by `.name` and rebuilt by a
  // name lookup that falls back to the same default the constructor uses. Every
  // field read is corruption-tolerant so a partially-written or hand-edited blob
  // can never throw — missing/invalid fields fall back to sensible dark
  // (monochrome-dark family) defaults, matching the constructor defaults.
  // ---------------------------------------------------------------------------

  Map<String, dynamic> toMap() => {
    'id': id,
    'name': name,
    'brightness': brightness == Brightness.light ? 'light' : 'dark',
    'accentColor': accentColor.toARGB32(),
    'backgroundColor': backgroundColor.toARGB32(),
    'surfaceColor': surfaceColor.toARGB32(),
    'cardColor': cardColor.toARGB32(),
    'primaryTextColor': primaryTextColor.toARGB32(),
    'secondaryTextColor': secondaryTextColor.toARGB32(),
    'outgoingBubbleColor': outgoingBubbleColor.toARGB32(),
    'incomingBubbleColor': incomingBubbleColor.toARGB32(),
    'outgoingTextColor': outgoingTextColor.toARGB32(),
    'incomingTextColor': incomingTextColor.toARGB32(),
    'linkColor': linkColor.toARGB32(),
    'dangerColor': dangerColor.toARGB32(),
    'successColor': successColor.toARGB32(),
    'cornerRadius': cornerRadius,
    'density': density,
    'fontScale': fontScale,
    'highContrast': highContrast,
    'navigationMode': navigationMode.name,
    'layoutMode': layoutMode.name,
    'bubbleStyle': bubbleStyle.name,
    'deliveryTickStyle': deliveryTickStyle.name,
    'tickStyle': tickStyle,
    'wallpaperId': wallpaperId,
    'animationLevel': animationLevel,
  };

  factory ThemeConfig.fromMap(Map<String, dynamic> map) {
    final bubbleStyleRaw = map['bubbleStyle'] as String?;
    final tickStyleRaw =
        map['deliveryTickStyle'] as String? ?? map['tickStyle'] as String?;

    return ThemeConfig(
      id: map['id'] is String && (map['id'] as String).isNotEmpty
          ? map['id'] as String
          : 'monochromeDark',
      name: map['name'] is String && (map['name'] as String).isNotEmpty
          ? map['name'] as String
          : 'Monochrome Dark',
      brightness: (map['brightness'] == 'light')
          ? Brightness.light
          : Brightness.dark,
      accentColor: _color(map['accentColor'], const Color(0xFFFFFFFF)),
      backgroundColor: _color(map['backgroundColor'], const Color(0xFF000000)),
      surfaceColor: _color(map['surfaceColor'], const Color(0xFF121212)),
      cardColor: _color(map['cardColor'], const Color(0xFF1C1C1E)),
      primaryTextColor: _color(
        map['primaryTextColor'],
        const Color(0xFFFFFFFF),
      ),
      secondaryTextColor: _color(
        map['secondaryTextColor'],
        const Color(0xFFA1A1AA),
      ),
      outgoingBubbleColor: _color(
        map['outgoingBubbleColor'],
        const Color(0xFF27272A),
      ),
      incomingBubbleColor: _color(
        map['incomingBubbleColor'],
        const Color(0xFF18181B),
      ),
      outgoingTextColor: _color(
        map['outgoingTextColor'],
        const Color(0xFFFFFFFF),
      ),
      incomingTextColor: _color(
        map['incomingTextColor'],
        const Color(0xFFF4F4F5),
      ),
      linkColor: _color(map['linkColor'], const Color(0xFF60A5FA)),
      dangerColor: _color(map['dangerColor'], const Color(0xFFEF4444)),
      successColor: _color(map['successColor'], const Color(0xFF22C55E)),
      cornerRadius: _double(map['cornerRadius'], 16.0),
      density: _double(map['density'], 1.0),
      fontScale: _double(map['fontScale'], 1.0).clamp(0.5, 2.0),
      highContrast: map['highContrast'] == true,
      navigationMode: _enumByName(
        AppNavigationMode.values,
        map['navigationMode'],
        AppNavigationMode.bottomNav,
      ),
      layoutMode: _enumByName(
        UILayoutMode.values,
        map['layoutMode'],
        UILayoutMode.classic,
      ),
      bubbleStyle: BubbleStyleIdExtension.fromString(bubbleStyleRaw),
      deliveryTickStyle: DeliveryIconStyleExtension.fromString(tickStyleRaw),
      tickStyle: tickStyleRaw ?? 'RC iOS 11',
      wallpaperId: map['wallpaperId'] is String
          ? map['wallpaperId'] as String
          : 'subtle_dots',
      animationLevel: _double(map['animationLevel'], 1.0).clamp(0.0, 1.0),
    );
  }

  static Color _color(dynamic value, Color fallback) {
    if (value is int) return Color(value);
    if (value is num) return Color(value.toInt());
    if (value is String) {
      final parsed = int.tryParse(value);
      if (parsed != null) return Color(parsed);
    }
    return fallback;
  }

  static double _double(dynamic value, double fallback) {
    if (value is num) return value.toDouble();
    if (value is String) {
      final parsed = double.tryParse(value);
      if (parsed != null) return parsed;
    }
    return fallback;
  }

  static T _enumByName<T extends Enum>(
    List<T> values,
    dynamic name,
    T fallback,
  ) {
    if (name is String) {
      for (final v in values) {
        if (v.name == name) return v;
      }
    }
    return fallback;
  }
}
