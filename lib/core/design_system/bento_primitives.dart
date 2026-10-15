import 'package:flutter/material.dart';

/// Semantic spacing tokens for Kombai-grade layout rhythm.
class DesignSpacingTokens {
  static const double xxs = 4.0;
  static const double xs = 8.0;
  static const double sm = 12.0;
  static const double md = 16.0;
  static const double lg = 20.0;
  static const double xl = 24.0;
  static const double xxl = 32.0;

  static const EdgeInsets paddingCard = EdgeInsets.all(md);
  static const EdgeInsets paddingCompact = EdgeInsets.symmetric(horizontal: sm, vertical: xs);
  static const EdgeInsets paddingScreen = EdgeInsets.symmetric(horizontal: md, vertical: lg);
}

/// Semantic typography scale providing structured contrast.
class DesignTypographyScale {
  static TextStyle hero(BuildContext context, {Color? color}) {
    return Theme.of(context).textTheme.headlineMedium?.copyWith(
      fontWeight: FontWeight.w800,
      letterSpacing: -0.8,
      color: color,
    ) ?? TextStyle(fontSize: 28, fontWeight: FontWeight.w800, color: color);
  }

  static TextStyle title(BuildContext context, {Color? color}) {
    return Theme.of(context).textTheme.titleLarge?.copyWith(
      fontWeight: FontWeight.w700,
      letterSpacing: -0.4,
      color: color,
    ) ?? TextStyle(fontSize: 20, fontWeight: FontWeight.w700, color: color);
  }

  static TextStyle subtitle(BuildContext context, {Color? color}) {
    return Theme.of(context).textTheme.titleMedium?.copyWith(
      fontWeight: FontWeight.w600,
      color: color,
    ) ?? TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: color);
  }

  static TextStyle body(BuildContext context, {Color? color}) {
    return Theme.of(context).textTheme.bodyMedium?.copyWith(
      fontSize: 14,
      height: 1.4,
      color: color,
    ) ?? TextStyle(fontSize: 14, height: 1.4, color: color);
  }

  static TextStyle caption(BuildContext context, {Color? color}) {
    return Theme.of(context).textTheme.bodySmall?.copyWith(
      fontSize: 12,
      fontWeight: FontWeight.w500,
      color: color,
    ) ?? TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: color);
  }
}

/// Kombai-style Bento Card primitive featuring responsive auto-sizing,
/// subtle 1px border gradient, inner surface elevation, and optional glow.
class BentoCard extends StatelessWidget {
  final Widget child;
  final VoidCallback? onTap;
  final EdgeInsetsGeometry? padding;
  final Color? backgroundColor;
  final Gradient? backgroundGradient;
  final Color? borderColor;
  final double borderRadius;
  final bool hasGlow;
  final Color? glowColor;
  final Widget? headerLeading;
  final Widget? headerTrailing;
  final String? title;
  final String? subtitle;

  const BentoCard({
    super.key,
    required this.child,
    this.onTap,
    this.padding,
    this.backgroundColor,
    this.backgroundGradient,
    this.borderColor,
    this.borderRadius = 16.0,
    this.hasGlow = false,
    this.glowColor,
    this.headerLeading,
    this.headerTrailing,
    this.title,
    this.subtitle,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final defaultBg = isDark ? const Color(0xFF161A22) : const Color(0xFFF6F8FA);
    final defaultBorder = isDark ? Colors.white.withOpacity(0.08) : Colors.black.withOpacity(0.06);

    Widget content = child;

    if (title != null || headerLeading != null || headerTrailing != null) {
      content = Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              if (headerLeading != null) ...[
                headerLeading!,
                const SizedBox(width: DesignSpacingTokens.xs),
              ],
              if (title != null)
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        title!,
                        style: DesignTypographyScale.subtitle(context),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      if (subtitle != null)
                        Text(
                          subtitle!,
                          style: DesignTypographyScale.caption(context,
                              color: isDark ? Colors.white60 : Colors.black54),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                    ],
                  ),
                ),
              if (headerTrailing != null) headerTrailing!,
            ],
          ),
          const SizedBox(height: DesignSpacingTokens.sm),
          child,
        ],
      );
    }

    return Container(
      decoration: BoxDecoration(
        color: backgroundGradient == null ? (backgroundColor ?? defaultBg) : null,
        gradient: backgroundGradient,
        borderRadius: BorderRadius.circular(borderRadius),
        border: Border.all(
          color: borderColor ?? defaultBorder,
          width: 1.0,
        ),
        boxShadow: [
          if (hasGlow && glowColor != null)
            BoxShadow(
              color: glowColor!.withOpacity(0.25),
              blurRadius: 18,
              spreadRadius: 1,
              offset: const Offset(0, 4),
            )
          else
            BoxShadow(
              color: Colors.black.withOpacity(isDark ? 0.28 : 0.05),
              blurRadius: 10,
              offset: const Offset(0, 3),
            ),
        ],
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(borderRadius),
          child: Padding(
            padding: padding ?? DesignSpacingTokens.paddingCard,
            child: content,
          ),
        ),
      ),
    );
  }
}

/// Grid layout engine for Bento layouts with responsive columns and auto-spacing.
class BentoGrid extends StatelessWidget {
  final List<Widget> children;
  final int crossAxisCount;
  final double spacing;
  final double runSpacing;

  const BentoGrid({
    super.key,
    required this.children,
    this.crossAxisCount = 2,
    this.spacing = 12.0,
    this.runSpacing = 12.0,
  });

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final effectiveCount = constraints.maxWidth < 600 ? crossAxisCount : (crossAxisCount * 1.5).round();
        final itemWidth = (constraints.maxWidth - (spacing * (effectiveCount - 1))) / effectiveCount;

        return Wrap(
          spacing: spacing,
          runSpacing: runSpacing,
          children: children.map((c) => SizedBox(width: itemWidth, child: c)).toList(),
        );
      },
    );
  }
}

/// Zero-overflow responsive flex container preventing rendering exceptions
/// on tight viewports with automatic scroll fallback or wrap.
class ResponsiveFlexBox extends StatelessWidget {
  final List<Widget> children;
  final Axis direction;
  final MainAxisAlignment mainAxisAlignment;
  final CrossAxisAlignment crossAxisAlignment;
  final double spacing;
  final bool enableScrollFallback;

  const ResponsiveFlexBox({
    super.key,
    required this.children,
    this.direction = Axis.horizontal,
    this.mainAxisAlignment = MainAxisAlignment.start,
    this.crossAxisAlignment = CrossAxisAlignment.center,
    this.spacing = 8.0,
    this.enableScrollFallback = true,
  });

  @override
  Widget build(BuildContext context) {
    final spacedChildren = <Widget>[];
    for (var i = 0; i < children.length; i++) {
      spacedChildren.add(children[i]);
      if (i < children.length - 1) {
        spacedChildren.add(direction == Axis.horizontal
            ? SizedBox(width: spacing)
            : SizedBox(height: spacing));
      }
    }

    if (direction == Axis.horizontal && enableScrollFallback) {
      return SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        physics: const BouncingScrollPhysics(),
        child: Row(
          mainAxisAlignment: mainAxisAlignment,
          crossAxisAlignment: crossAxisAlignment,
          children: spacedChildren,
        ),
      );
    }

    return Flex(
      direction: direction,
      mainAxisAlignment: mainAxisAlignment,
      crossAxisAlignment: crossAxisAlignment,
      children: spacedChildren,
    );
  }
}