import 'package:flutter/material.dart';

import 'context_surface_engine.dart';
export 'context_surface_engine.dart';

class ContextMenuItem {
  final Widget? iconWidget;
  final IconData? icon;
  final String label;
  final String? subtitle;
  final VoidCallback onTap;
  final bool isDestructive;
  final Widget? trailing;
  final bool isEnabled;

  const ContextMenuItem({
    this.iconWidget,
    this.icon,
    required this.label,
    this.subtitle,
    required this.onTap,
    this.isDestructive = false,
    this.trailing,
    this.isEnabled = true,
  });
}

class ContextMenuSection {
  final String? title;
  final List<ContextMenuItem> items;

  const ContextMenuSection({
    this.title,
    required this.items,
  });
}

/// Unified, high-performance contextual menu system for Chaty.
/// Supports both anchored floating popover menus (originating near the invoking widget/tap)
/// and adaptive compact bottom sheets when anchor is not specified.
class AppContextMenu {
  AppContextMenu._();

  /// Shows an anchored floating context menu at the specified global [anchorRect] or [anchorPosition].
  /// Smoothly scales (0.94 -> 1.0) and fades in (160-200ms) with intelligent edge avoidance.
  static Future<void> show({
    required BuildContext context,
    String? title,
    String? subtitle,
    required List<ContextMenuSection> sections,
    Rect? anchorRect,
    Offset? anchorPosition,
    Color? backgroundColor,
    Color? primaryTextColor,
    Color? secondaryTextColor,
    Color? destructiveColor,
    ContextAnchorType anchorType = ContextAnchorType.generic,
    double minWidth = 150.0,
    double maxWidth = 240.0,
  }) {
    // If no anchor is passed, attempt to find the render box from context
    Rect? effectiveAnchor = anchorRect;
    if (effectiveAnchor == null && anchorPosition != null) {
      effectiveAnchor = Rect.fromCenter(
        center: anchorPosition,
        width: 0,
        height: 0,
      );
    }
    if (effectiveAnchor == null) {
      final renderBox = context.findRenderObject() as RenderBox?;
      if (renderBox != null && renderBox.hasSize) {
        final origin = renderBox.localToGlobal(Offset.zero);
        effectiveAnchor = origin & renderBox.size;
      }
    }

    if (effectiveAnchor != null) {
      return _showAnchoredMenu(
        context: context,
        anchor: effectiveAnchor,
        title: title,
        subtitle: subtitle,
        sections: sections,
        backgroundColor: backgroundColor,
        primaryTextColor: primaryTextColor,
        secondaryTextColor: secondaryTextColor,
        destructiveColor: destructiveColor,
        anchorType: anchorType,
        minWidth: minWidth,
        maxWidth: maxWidth,
      );
    }

    return _showFallbackModal(
      context: context,
      title: title,
      subtitle: subtitle,
      sections: sections,
      backgroundColor: backgroundColor,
      primaryTextColor: primaryTextColor,
      secondaryTextColor: secondaryTextColor,
      destructiveColor: destructiveColor,
    );
  }

  /// Like [show] but prepends a quick-reaction emoji strip above the menu items.
  static Future<void> showWithReactionRail({
    required BuildContext context,
    required List<ContextMenuSection> sections,
    required List<String> quickReactions,
    required void Function(String emoji) onQuickReaction,
    Rect? anchorRect,
    Color? backgroundColor,
    Color? primaryTextColor,
    Color? secondaryTextColor,
    Color? destructiveColor,
    ContextAnchorType anchorType = ContextAnchorType.generic,
    double minWidth = 150.0,
    double maxWidth = 240.0,
  }) {
    Rect? effectiveAnchor = anchorRect;
    if (effectiveAnchor == null) {
      final renderBox = context.findRenderObject() as RenderBox?;
      if (renderBox != null && renderBox.hasSize) {
        final origin = renderBox.localToGlobal(Offset.zero);
        effectiveAnchor = origin & renderBox.size;
      }
    }

    final theme = Theme.of(context);
    final bg = backgroundColor ?? theme.cardColor;
    final primary = primaryTextColor ?? theme.colorScheme.onSurface;
    final secondary = secondaryTextColor ?? theme.colorScheme.onSurfaceVariant;
    final danger = destructiveColor ?? theme.colorScheme.error;

    final totalItems = sections.fold<int>(0, (sum, s) => sum + s.items.length);
    final estimatedHeight =
        56.0 + 8.0 + (totalItems * 44.0);

    if (effectiveAnchor != null) {
      return ContextSurfaceController.showSurface<void>(
        context: context,
        anchorRect: effectiveAnchor,
        preferredSize: Size(maxWidth, estimatedHeight),
        builder: (dialogContext, resolution) {
          return _AnchoredMenuLayout(
            anchor: effectiveAnchor!,
            resolution: resolution,
            minWidth: minWidth,
            maxWidth: maxWidth,
            backgroundColor: bg,
            primaryColor: primary,
            secondaryColor: secondary,
            dangerColor: danger,
            sections: sections,
            onDismiss: () => Navigator.of(dialogContext).pop(),
            headerWidget: _ReactionRailWidget(
              emojis: quickReactions,
              backgroundColor: bg,
              onReact: (emoji) {
                Navigator.of(dialogContext).pop();
                onQuickReaction(emoji);
              },
            ),
          );
        },
      );
    }

    return _showFallbackModal(
      context: context,
      sections: sections,
      backgroundColor: backgroundColor,
      primaryTextColor: primaryTextColor,
      secondaryTextColor: secondaryTextColor,
      destructiveColor: destructiveColor,
    );
  }

  static Future<void> _showAnchoredMenu({
    required BuildContext context,
    required Rect anchor,
    String? title,
    String? subtitle,
    required List<ContextMenuSection> sections,
    Color? backgroundColor,
    Color? primaryTextColor,
    Color? secondaryTextColor,
    Color? destructiveColor,
    ContextAnchorType anchorType = ContextAnchorType.generic,
    required double minWidth,
    required double maxWidth,
  }) {
    final theme = Theme.of(context);
    final bg = backgroundColor ?? theme.cardColor;
    final primary = primaryTextColor ?? theme.colorScheme.onSurface;
    final secondary = secondaryTextColor ?? theme.colorScheme.onSurfaceVariant;
    final danger = destructiveColor ?? theme.colorScheme.error;

    // Calculate approximate items count to estimate preferred size
    final totalItems = sections.fold<int>(0, (sum, s) => sum + s.items.length);
    final estimatedHeight = (title != null ? 50.0 : 16.0) + (totalItems * 44.0);

    return ContextSurfaceController.showSurface<void>(
      context: context,
      anchorRect: anchor,
      preferredSize: Size(maxWidth, estimatedHeight),
      builder: (dialogContext, resolution) {
        return _AnchoredMenuLayout(
          anchor: anchor,
          resolution: resolution,
          minWidth: minWidth,
          maxWidth: maxWidth,
          backgroundColor: bg,
          primaryColor: primary,
          secondaryColor: secondary,
          dangerColor: danger,
          title: title,
          subtitle: subtitle,
          sections: sections,
          onDismiss: () => Navigator.of(dialogContext).pop(),
        );
      },
    );
  }

  static Future<void> _showFallbackModal({
    required BuildContext context,
    String? title,
    String? subtitle,
    required List<ContextMenuSection> sections,
    Color? backgroundColor,
    Color? primaryTextColor,
    Color? secondaryTextColor,
    Color? destructiveColor,
  }) {
    final theme = Theme.of(context);
    final bg = backgroundColor ?? theme.cardColor;
    final primary = primaryTextColor ?? theme.colorScheme.onSurface;
    final secondary = secondaryTextColor ?? theme.colorScheme.onSurfaceVariant;
    final danger = destructiveColor ?? theme.colorScheme.error;

    return showModalBottomSheet<void>(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (sheetContext) => SafeArea(
        child: Container(
          margin: const EdgeInsets.fromLTRB(16, 0, 16, 16),
          decoration: BoxDecoration(
            color: bg,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
              color: secondary.withValues(alpha: 0.15),
            ),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (title != null) ...[
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        title,
                        style: TextStyle(
                          color: primary,
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      if (subtitle != null && subtitle.isNotEmpty) ...[
                        const SizedBox(height: 2),
                        Text(
                          subtitle,
                          style: TextStyle(color: secondary, fontSize: 12),
                        ),
                      ],
                    ],
                  ),
                ),
                Divider(height: 1, color: secondary.withValues(alpha: 0.12)),
              ],
              _buildSectionsList(
                context: sheetContext,
                sections: sections,
                primary: primary,
                secondary: secondary,
                danger: danger,
              ),
              const SizedBox(height: 8),
            ],
          ),
        ),
      ),
    );
  }

  static Widget _buildSectionsList({
    required BuildContext context,
    required List<ContextMenuSection> sections,
    required Color primary,
    required Color secondary,
    required Color danger,
  }) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (int sIdx = 0; sIdx < sections.length; sIdx++) ...[
          if (sIdx > 0)
            Divider(
              height: 1,
              thickness: 0.8,
              color: secondary.withValues(alpha: 0.12),
            ),
          if (sections[sIdx].title != null)
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 6, 12, 4),
              child: Text(
                sections[sIdx].title!.toUpperCase(),
                style: TextStyle(
                  color: secondary,
                  fontSize: 10.5,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 0.6,
                ),
              ),
            ),
          ...sections[sIdx].items.map((item) {
            final itemColor = !item.isEnabled
                ? secondary.withValues(alpha: 0.5)
                : item.isDestructive
                    ? danger
                    : primary;

            return InkWell(
              onTap: item.isEnabled
                  ? () {
                      Navigator.of(context).pop();
                      item.onTap();
                    }
                  : null,
              borderRadius: BorderRadius.circular(10),
              child: Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: 10,
                  vertical: 9,
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: [
                    if (item.iconWidget != null)
                      item.iconWidget!
                    else if (item.icon != null)
                      Icon(item.icon, color: itemColor, size: 21),
                    if (item.iconWidget != null || item.icon != null)
                      const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            item.label,
                            style: TextStyle(
                              color: itemColor,
                              fontSize: 14.5,
                              fontWeight: item.isDestructive
                                  ? FontWeight.w600
                                  : FontWeight.w500,
                            ),
                          ),
                          if (item.subtitle != null) ...[
                            const SizedBox(height: 1),
                            Text(
                              item.subtitle!,
                              style: TextStyle(
                                color: secondary,
                                fontSize: 11.5,
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
                    if (item.trailing != null) item.trailing!,
                  ],
                ),
              ),
            );
          }),
        ],
      ],
    );
  }
}

class _AnchoredMenuLayout extends StatelessWidget {
  final Rect anchor;
  final ContextSurfaceResolution resolution;
  final double minWidth;
  final double maxWidth;
  final Color backgroundColor;
  final Color primaryColor;
  final Color secondaryColor;
  final Color dangerColor;
  final String? title;
  final String? subtitle;
  final List<ContextMenuSection> sections;
  final VoidCallback onDismiss;
  final Widget? headerWidget;

  const _AnchoredMenuLayout({
    required this.anchor,
    required this.resolution,
    required this.minWidth,
    required this.maxWidth,
    required this.backgroundColor,
    required this.primaryColor,
    required this.secondaryColor,
    required this.dangerColor,
    this.title,
    this.subtitle,
    required this.sections,
    required this.onDismiss,
    this.headerWidget,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: BoxConstraints(
        minWidth: minWidth,
        maxWidth: maxWidth,
        maxHeight: resolution.maxHeight,
      ),
      decoration: BoxDecoration(
        color: backgroundColor,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: secondaryColor.withValues(alpha: 0.18),
          width: 1.1,
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.16),
            blurRadius: 20,
            offset: const Offset(0, 8),
          ),
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.08),
            blurRadius: 6,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(16),
        child: SingleChildScrollView(
          physics: const ClampingScrollPhysics(),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (title != null && title!.isNotEmpty) ...[
                Padding(
                  padding: const EdgeInsets.fromLTRB(14, 12, 14, 8),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        title!,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          color: primaryColor,
                          fontSize: 14.5,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      if (subtitle != null && subtitle!.isNotEmpty) ...[
                        const SizedBox(height: 1),
                        Text(
                          subtitle!,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            color: secondaryColor,
                            fontSize: 11.5,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
                Divider(
                  height: 1,
                  thickness: 0.8,
                  color: secondaryColor.withValues(alpha: 0.12),
                ),
              ],
              if (headerWidget != null) ...[
                headerWidget!,
                Divider(
                  height: 1,
                  thickness: 0.8,
                  color: secondaryColor.withValues(alpha: 0.12),
                ),
              ],
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 4),
                child: AppContextMenu._buildSectionsList(
                  context: context,
                  sections: sections,
                  primary: primaryColor,
                  secondary: secondaryColor,
                  danger: dangerColor,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Horizontal quick-reaction emoji rail rendered inside the context menu.
class _ReactionRailWidget extends StatelessWidget {
  final List<String> emojis;
  final Color backgroundColor;
  final void Function(String emoji) onReact;

  const _ReactionRailWidget({
    required this.emojis,
    required this.backgroundColor,
    required this.onReact,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 6),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: emojis.map((emoji) {
          return InkWell(
            onTap: () => onReact(emoji),
            borderRadius: BorderRadius.circular(20),
            child: Padding(
              padding: const EdgeInsets.all(6),
              child: Text(
                emoji,
                style: const TextStyle(fontSize: 24),
              ),
            ),
          );
        }).toList(growable: false),
      ),
    );
  }
}
