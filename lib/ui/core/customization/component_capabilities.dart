import 'package:flutter/material.dart';

/// Capabilities that a component variant may require or support.
class ComponentCapabilities {
  final bool compactWidth;
  final bool expandedWidth;
  final bool dark;
  final bool light;
  final bool highContrast;
  final bool largeText;
  final bool reducedMotion;
  final bool pointerInput;
  final bool keyboardInput;
  final bool touchInput;
  final bool rtl;
  final bool dynamicColor;

  const ComponentCapabilities({
    this.compactWidth = true,
    this.expandedWidth = true,
    this.dark = true,
    this.light = true,
    this.highContrast = true,
    this.largeText = true,
    this.reducedMotion = true,
    this.pointerInput = true,
    this.keyboardInput = true,
    this.touchInput = true,
    this.rtl = true,
    this.dynamicColor = true,
  });

  static const ComponentCapabilities universal = ComponentCapabilities();

  bool satisfies(ComponentCapabilities required) {
    if (required.compactWidth && !compactWidth) return false;
    if (required.expandedWidth && !expandedWidth) return false;
    if (required.dark && !dark) return false;
    if (required.light && !light) return false;
    if (required.highContrast && !highContrast) return false;
    if (required.largeText && !largeText) return false;
    if (required.reducedMotion && !reducedMotion) return false;
    if (required.pointerInput && !pointerInput) return false;
    if (required.keyboardInput && !keyboardInput) return false;
    if (required.touchInput && !touchInput) return false;
    if (required.rtl && !rtl) return false;
    if (required.dynamicColor && !dynamicColor) return false;
    return true;
  }
}

/// Runtime context evaluated by the variant resolver to negotiate capability matching.
class VariantContext {
  final double availableWidth;
  final double availableHeight;
  final Brightness brightness;
  final bool isHighContrast;
  final bool isReducedMotion;
  final double textScaleFactor;
  final bool isRtl;

  const VariantContext({
    required this.availableWidth,
    required this.availableHeight,
    required this.brightness,
    this.isHighContrast = false,
    this.isReducedMotion = false,
    this.textScaleFactor = 1.0,
    this.isRtl = false,
  });

  bool get isCompact => availableWidth < 600.0;
  bool get isMedium => availableWidth >= 600.0 && availableWidth < 840.0;
  bool get isExpanded => availableWidth >= 840.0;

  factory VariantContext.fromContext(BuildContext context, {BoxConstraints? localConstraints}) {
    final media = MediaQuery.maybeOf(context);
    final width = localConstraints?.maxWidth ?? media?.size.width ?? 390.0;
    final height = localConstraints?.maxHeight ?? media?.size.height ?? 844.0;
    final theme = Theme.of(context);

    return VariantContext(
      availableWidth: width,
      availableHeight: height,
      brightness: theme.brightness,
      isHighContrast: media?.highContrast ?? false,
      isReducedMotion: media?.disableAnimations ?? false,
      textScaleFactor: media?.textScaler.scale(1.0) ?? 1.0,
      isRtl: Directionality.maybeOf(context) == TextDirection.rtl,
    );
  }
}
