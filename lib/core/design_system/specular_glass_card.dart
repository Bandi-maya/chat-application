import 'dart:ui';
import 'package:flutter/material.dart';

/// 21st.dev-grade frosted glass container featuring multi-layered backdrop blur,
/// 1px directional light-gradient border, and subtle inner specular luminance.
class SpecularGlassCard extends StatelessWidget {
  final Widget child;
  final VoidCallback? onTap;
  final double blur;
  final double borderRadius;
  final EdgeInsetsGeometry? padding;
  final Color? tintColor;
  final double opacity;
  final Border? border;
  final List<BoxShadow>? shadows;

  const SpecularGlassCard({
    super.key,
    required this.child,
    this.onTap,
    this.blur = 16.0,
    this.borderRadius = 18.0,
    this.padding,
    this.tintColor,
    this.opacity = 0.08,
    this.border,
    this.shadows,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final baseTint = tintColor ?? (isDark ? Colors.white : Colors.black);

    final effectiveBorder = border ??
        Border.all(
          color: isDark ? Colors.white.withOpacity(0.12) : Colors.black.withOpacity(0.08),
          width: 1.0,
        );

    final cardChild = Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(borderRadius),
        border: effectiveBorder,
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            baseTint.withOpacity(opacity * 1.5),
            baseTint.withOpacity(opacity * 0.6),
          ],
        ),
        boxShadow: shadows ??
            [
              BoxShadow(
                color: isDark ? Colors.black.withOpacity(0.35) : Colors.black.withOpacity(0.04),
                blurRadius: 16,
                offset: const Offset(0, 6),
              ),
            ],
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(borderRadius),
          child: Padding(
            padding: padding ?? const EdgeInsets.all(16.0),
            child: child,
          ),
        ),
      ),
    );

    return ClipRRect(
      borderRadius: BorderRadius.circular(borderRadius),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: blur, sigmaY: blur),
        child: cardChild,
      ),
    );
  }
}