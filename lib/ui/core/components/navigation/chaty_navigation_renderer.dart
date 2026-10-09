import 'dart:ui';
import 'package:flutter/material.dart';
import '../../design_system/tokens/app_tokens.dart';
import '../../theme/app_theme.dart';

/// Semantic navigation item model. Independent of visual variants.
class ChatyNavItemData {
  final String id;
  final String label;
  final IconData icon;
  final IconData? activeIcon;
  final int badgeCount;
  final bool isSelected;
  final VoidCallback onTap;

  const ChatyNavItemData({
    required this.id,
    required this.label,
    required this.icon,
    this.activeIcon,
    this.badgeCount = 0,
    required this.isSelected,
    required this.onTap,
  });
}

/// Unified navigation bar renderer that delegates visual presentation to the
/// chosen variant while preserving 100% identical interaction and semantic contracts.
class ChatyNavigationRenderer extends StatelessWidget {
  final String variantId;
  final List<ChatyNavItemData> items;
  final Color? backgroundColor;
  final Color? activeColor;
  final Color? inactiveColor;

  const ChatyNavigationRenderer({
    super.key,
    required this.variantId,
    required this.items,
    this.backgroundColor,
    this.activeColor,
    this.inactiveColor,
  });

  @override
  Widget build(BuildContext context) {
    switch (variantId) {
      case 'floating_dock':
        return _buildFloatingDock(context);
      case 'capsule_rail':
        return _buildCapsuleDock(context);
      case 'split_segments':
        return _buildSplitSegments(context);
      case 'glass_dock':
        return _buildGlassDock(context);
      case 'compact_icon_dock':
        return _buildCompactIconDock(context);
      case 'orbit':
        return _buildOrbitDock(context);
      case 'elevated_pill':
        return _buildElevatedPill(context);
      case 'line_marker':
        return _buildLineMarker(context);
      case 'adaptive_rail':
        return _buildAdaptiveDock(context);
      case 'classic_label_bar':
      default:
        return _buildClassicLabelBar(context);
    }
  }

  // ---------------------------------------------------------------------------
  // NAV-01: Classic Label Bar (Default)
  // ---------------------------------------------------------------------------
  Widget _buildClassicLabelBar(BuildContext context) {
    final colors = context.colors;
    final bg = backgroundColor ?? colors.surface;
    final active = activeColor ?? colors.primary;
    final inactive = inactiveColor ?? colors.foregroundSecondary;

    return Container(
      decoration: BoxDecoration(
        color: bg,
        border: Border(top: BorderSide(color: colors.borderSubtle, width: 0.8)),
      ),
      padding: const EdgeInsets.only(top: 6, bottom: 6),
      child: SafeArea(
        top: false,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: items.map((item) {
            final icon = item.isSelected && item.activeIcon != null
                ? item.activeIcon!
                : item.icon;
            return Expanded(
              child: _NavItemWrapper(
                onTap: item.onTap,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    _BadgeAnchor(
                      count: item.badgeCount,
                      child: Icon(
                        icon,
                        size: 24,
                        color: item.isSelected ? active : inactive,
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      item.label,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 11.5,
                        fontWeight:
                            item.isSelected ? FontWeight.w700 : FontWeight.w500,
                        color: item.isSelected ? active : inactive,
                        letterSpacing: -0.1,
                      ),
                    ),
                  ],
                ),
              ),
            );
          }).toList(),
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // NAV-02: Floating Dock
  // ---------------------------------------------------------------------------
  Widget _buildFloatingDock(BuildContext context) {
    final colors = context.colors;
    final bg = backgroundColor ?? colors.surface;
    final active = activeColor ?? colors.primary;
    final inactive = inactiveColor ?? colors.foregroundSecondary;

    return SafeArea(
      top: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
          decoration: BoxDecoration(
            color: bg,
            borderRadius: BorderRadius.circular(32),
            border: Border.all(color: colors.border, width: 1.0),
            boxShadow: [
              BoxShadow(
                color: colors.shadow.withValues(alpha: 0.12),
                blurRadius: 16,
                offset: const Offset(0, 6),
              ),
            ],
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: items.map((item) {
              return _NavItemWrapper(
                onTap: item.onTap,
                child: AnimatedContainer(
                  duration: ChatyMotion.fast,
                  curve: ChatyMotion.enter,
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                  decoration: BoxDecoration(
                    color: item.isSelected
                        ? active.withValues(alpha: 0.14)
                        : Colors.transparent,
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: _BadgeAnchor(
                    count: item.badgeCount,
                    child: Icon(
                      item.isSelected && item.activeIcon != null
                          ? item.activeIcon!
                          : item.icon,
                      size: 22,
                      color: item.isSelected ? active : inactive,
                    ),
                  ),
                ),
              );
            }).toList(),
          ),
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // NAV-03: Capsule Rail / Dock
  // ---------------------------------------------------------------------------
  Widget _buildCapsuleDock(BuildContext context) {
    final colors = context.colors;
    final bg = backgroundColor ?? colors.surface;
    final active = activeColor ?? colors.primary;
    final inactive = inactiveColor ?? colors.foregroundSecondary;

    return Container(
      color: bg,
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: SafeArea(
        top: false,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          children: items.map((item) {
            return _NavItemWrapper(
              onTap: item.onTap,
              child: AnimatedContainer(
                duration: ChatyMotion.fast,
                curve: ChatyMotion.enter,
                padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 7),
                decoration: BoxDecoration(
                  color: item.isSelected ? active : Colors.transparent,
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    _BadgeAnchor(
                      count: item.badgeCount,
                      child: Icon(
                        item.isSelected && item.activeIcon != null
                            ? item.activeIcon!
                            : item.icon,
                        size: 20,
                        color: item.isSelected ? Colors.white : inactive,
                      ),
                    ),
                    if (item.isSelected) ...[
                      const SizedBox(width: 6),
                      Text(
                        item.label,
                        style: const TextStyle(
                          fontSize: 12.5,
                          fontWeight: FontWeight.w700,
                          color: Colors.white,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            );
          }).toList(),
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // NAV-04: Split Segments
  // ---------------------------------------------------------------------------
  Widget _buildSplitSegments(BuildContext context) {
    final colors = context.colors;
    final bg = backgroundColor ?? colors.surface;
    final active = activeColor ?? colors.primary;
    final inactive = inactiveColor ?? colors.foregroundSecondary;

    return Container(
      color: bg,
      padding: const EdgeInsets.fromLTRB(12, 6, 12, 6),
      child: SafeArea(
        top: false,
        child: Row(
          children: items.map((item) {
            return Expanded(
              child: _NavItemWrapper(
                onTap: item.onTap,
                child: AnimatedContainer(
                  duration: ChatyMotion.fast,
                  curve: ChatyMotion.enter,
                  margin: const EdgeInsets.symmetric(horizontal: 4),
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  decoration: BoxDecoration(
                    color: item.isSelected
                        ? active.withValues(alpha: 0.16)
                        : colors.surfaceSecondary,
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(
                      color: item.isSelected ? active : colors.border,
                      width: 1.0,
                    ),
                  ),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      _BadgeAnchor(
                        count: item.badgeCount,
                        child: Icon(
                          item.isSelected && item.activeIcon != null
                              ? item.activeIcon!
                              : item.icon,
                          size: 20,
                          color: item.isSelected ? active : inactive,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        item.label,
                        maxLines: 1,
                        style: TextStyle(
                          fontSize: 10.5,
                          fontWeight:
                              item.isSelected ? FontWeight.w700 : FontWeight.w500,
                          color: item.isSelected ? active : inactive,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            );
          }).toList(),
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // NAV-05: Glass Dock
  // ---------------------------------------------------------------------------
  Widget _buildGlassDock(BuildContext context) {
    final colors = context.colors;
    final active = activeColor ?? colors.primary;
    final inactive = inactiveColor ?? colors.foregroundSecondary;

    return SafeArea(
      top: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(28),
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 16, sigmaY: 16),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: (backgroundColor ?? colors.surface).withValues(alpha: 0.72),
                borderRadius: BorderRadius.circular(28),
                border: Border.all(
                  color: Colors.white.withValues(alpha: 0.15),
                  width: 1.2,
                ),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: items.map((item) {
                  return _NavItemWrapper(
                    onTap: item.onTap,
                    child: AnimatedScale(
                      scale: item.isSelected ? 1.08 : 1.0,
                      duration: ChatyMotion.fast,
                      curve: ChatyMotion.enter,
                      child: _BadgeAnchor(
                        count: item.badgeCount,
                        child: Icon(
                          item.isSelected && item.activeIcon != null
                              ? item.activeIcon!
                              : item.icon,
                          size: 24,
                          color: item.isSelected ? active : inactive,
                        ),
                      ),
                    ),
                  );
                }).toList(),
              ),
            ),
          ),
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // NAV-06: Compact Icon Dock
  // ---------------------------------------------------------------------------
  Widget _buildCompactIconDock(BuildContext context) {
    final colors = context.colors;
    final bg = backgroundColor ?? colors.surface;
    final active = activeColor ?? colors.primary;
    final inactive = inactiveColor ?? colors.foregroundSecondary;

    return Container(
      color: bg,
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: SafeArea(
        top: false,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: items.map((item) {
            return Tooltip(
              message: item.label,
              child: _NavItemWrapper(
                onTap: item.onTap,
                child: Padding(
                  padding: const EdgeInsets.all(8),
                  child: _BadgeAnchor(
                    count: item.badgeCount,
                    child: Icon(
                      item.isSelected && item.activeIcon != null
                          ? item.activeIcon!
                          : item.icon,
                      size: 24,
                      color: item.isSelected ? active : inactive,
                    ),
                  ),
                ),
              ),
            );
          }).toList(),
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // NAV-07: Orbit
  // ---------------------------------------------------------------------------
  Widget _buildOrbitDock(BuildContext context) {
    final colors = context.colors;
    final bg = backgroundColor ?? colors.surface;
    final active = activeColor ?? colors.primary;
    final inactive = inactiveColor ?? colors.foregroundSecondary;

    return Container(
      color: bg,
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: SafeArea(
        top: false,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: items.map((item) {
            return _NavItemWrapper(
              onTap: item.onTap,
              child: Container(
                padding: const EdgeInsets.all(6),
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  border: Border.all(
                    color: item.isSelected ? active : Colors.transparent,
                    width: 2.0,
                  ),
                ),
                child: _BadgeAnchor(
                  count: item.badgeCount,
                  child: Icon(
                    item.isSelected && item.activeIcon != null
                        ? item.activeIcon!
                        : item.icon,
                    size: 22,
                    color: item.isSelected ? active : inactive,
                  ),
                ),
              ),
            );
          }).toList(),
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // NAV-08: Elevated Pill
  // ---------------------------------------------------------------------------
  Widget _buildElevatedPill(BuildContext context) {
    final colors = context.colors;
    final bg = backgroundColor ?? colors.surface;
    final active = activeColor ?? colors.primary;
    final inactive = inactiveColor ?? colors.foregroundSecondary;

    return SafeArea(
      top: false,
      child: Container(
        margin: const EdgeInsets.fromLTRB(16, 4, 16, 8),
        padding: const EdgeInsets.all(4),
        decoration: BoxDecoration(
          color: colors.surfaceSecondary,
          borderRadius: BorderRadius.circular(24),
          border: Border.all(color: colors.border, width: 0.8),
        ),
        child: Row(
          children: items.map((item) {
            return Expanded(
              child: _NavItemWrapper(
                onTap: item.onTap,
                child: AnimatedContainer(
                  duration: ChatyMotion.fast,
                  curve: ChatyMotion.enter,
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  decoration: BoxDecoration(
                    color: item.isSelected ? bg : Colors.transparent,
                    borderRadius: BorderRadius.circular(20),
                    boxShadow: item.isSelected
                        ? [
                            BoxShadow(
                              color: colors.shadow.withValues(alpha: 0.08),
                              blurRadius: 6,
                              offset: const Offset(0, 2),
                            ),
                          ]
                        : null,
                  ),
                  child: Center(
                    child: _BadgeAnchor(
                      count: item.badgeCount,
                      child: Icon(
                        item.isSelected && item.activeIcon != null
                            ? item.activeIcon!
                            : item.icon,
                        size: 22,
                        color: item.isSelected ? active : inactive,
                      ),
                    ),
                  ),
                ),
              ),
            );
          }).toList(),
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // NAV-09: Line Marker
  // ---------------------------------------------------------------------------
  Widget _buildLineMarker(BuildContext context) {
    final colors = context.colors;
    final bg = backgroundColor ?? colors.surface;
    final active = activeColor ?? colors.primary;
    final inactive = inactiveColor ?? colors.foregroundSecondary;

    return Container(
      color: bg,
      child: SafeArea(
        top: false,
        child: Row(
          children: items.map((item) {
            return Expanded(
              child: _NavItemWrapper(
                onTap: item.onTap,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    AnimatedContainer(
                      duration: ChatyMotion.fast,
                      curve: ChatyMotion.enter,
                      height: 3,
                      width: item.isSelected ? 28 : 0,
                      decoration: BoxDecoration(
                        color: active,
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                    const SizedBox(height: 6),
                    _BadgeAnchor(
                      count: item.badgeCount,
                      child: Icon(
                        item.isSelected && item.activeIcon != null
                            ? item.activeIcon!
                            : item.icon,
                        size: 22,
                        color: item.isSelected ? active : inactive,
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      item.label,
                      maxLines: 1,
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight:
                            item.isSelected ? FontWeight.w700 : FontWeight.w500,
                        color: item.isSelected ? active : inactive,
                      ),
                    ),
                    const SizedBox(height: 4),
                  ],
                ),
              ),
            );
          }).toList(),
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // NAV-10: Adaptive Rail
  // ---------------------------------------------------------------------------
  Widget _buildAdaptiveDock(BuildContext context) {
    // Falls back to Classic Label Bar when used horizontally in bottom nav
    return _buildClassicLabelBar(context);
  }
}

class _NavItemWrapper extends StatefulWidget {
  final Widget child;
  final VoidCallback onTap;

  const _NavItemWrapper({required this.child, required this.onTap});

  @override
  State<_NavItemWrapper> createState() => _NavItemWrapperState();
}

class _NavItemWrapperState extends State<_NavItemWrapper> {
  bool _isPressed = false;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: () {
        ChatyMotion.selection();
        widget.onTap();
      },
      onTapDown: (_) => setState(() => _isPressed = true),
      onTapUp: (_) => setState(() => _isPressed = false),
      onTapCancel: () => setState(() => _isPressed = false),
      child: AnimatedScale(
        scale: _isPressed ? ChatyMotion.activeScale : 1.0,
        duration: ChatyMotion.instant,
        curve: ChatyMotion.enter,
        child: widget.child,
      ),
    );
  }
}

class _BadgeAnchor extends StatelessWidget {
  final Widget child;
  final int count;

  const _BadgeAnchor({required this.child, required this.count});

  @override
  Widget build(BuildContext context) {
    if (count <= 0) return child;
    final display = count > 99 ? '99+' : count.toString();

    return Stack(
      clipBehavior: Clip.none,
      children: [
        child,
        Positioned(
          right: -8,
          top: -4,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 4.5, vertical: 1.5),
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.error,
              borderRadius: BorderRadius.circular(10),
            ),
            constraints: const BoxConstraints(minWidth: 16, minHeight: 16),
            alignment: Alignment.center,
            child: Text(
              display,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 9.5,
                fontWeight: FontWeight.w800,
                height: 1.0,
              ),
            ),
          ),
        ),
      ],
    );
  }
}
