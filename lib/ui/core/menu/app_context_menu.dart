import 'package:flutter/material.dart';

class ContextMenuItem {
  final Widget? iconWidget;
  final IconData? icon;
  final String label;
  final String? subtitle;
  final VoidCallback onTap;
  final bool isDestructive;
  final Widget? trailing;

  const ContextMenuItem({
    this.iconWidget,
    this.icon,
    required this.label,
    this.subtitle,
    required this.onTap,
    this.isDestructive = false,
    this.trailing,
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

/// Unified, high-performance reusable context menu for Home, Chat, Message & Profile.
class AppContextMenu {
  AppContextMenu._();

  static Future<void> show(
    BuildContext context, {
    required String title,
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
          margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          decoration: BoxDecoration(
            color: bg,
            borderRadius: BorderRadius.circular(24),
            border: Border.all(
              color: secondary.withValues(alpha: 0.15),
            ),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Header Drag handle
              Center(
                child: Container(
                  margin: const EdgeInsets.only(top: 10, bottom: 8),
                  width: 36,
                  height: 4,
                  decoration: BoxDecoration(
                    color: secondary.withValues(alpha: 0.3),
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              // Title & Subtitle
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 4, 20, 12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: primary,
                        fontSize: 17,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    if (subtitle != null && subtitle.isNotEmpty) ...[
                      const SizedBox(height: 2),
                      Text(
                        subtitle,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          color: secondary,
                          fontSize: 13,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
              Divider(height: 1, color: secondary.withValues(alpha: 0.12)),
              // Sections
              Flexible(
                child: ListView.separated(
                  shrinkWrap: true,
                  physics: const BouncingScrollPhysics(),
                  padding: const EdgeInsets.symmetric(vertical: 6),
                  itemCount: sections.length,
                  separatorBuilder: (_, __) => Divider(
                    height: 1,
                    color: secondary.withValues(alpha: 0.08),
                  ),
                  itemBuilder: (context, sIdx) {
                    final section = sections[sIdx];
                    return Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        if (section.title != null)
                          Padding(
                            padding: const EdgeInsets.fromLTRB(20, 10, 20, 4),
                            child: Text(
                              section.title!.toUpperCase(),
                              style: TextStyle(
                                color: secondary,
                                fontSize: 11,
                                fontWeight: FontWeight.w700,
                                letterSpacing: 0.5,
                              ),
                            ),
                          ),
                        ...section.items.map((item) {
                          final itemColor = item.isDestructive ? danger : primary;
                          return InkWell(
                            onTap: () {
                              Navigator.of(sheetContext).pop();
                              item.onTap();
                            },
                            child: Padding(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 20,
                                vertical: 12,
                              ),
                              child: Row(
                                children: [
                                  if (item.iconWidget != null)
                                    item.iconWidget!
                                  else if (item.icon != null)
                                    Icon(item.icon, color: itemColor, size: 20),
                                  if (item.iconWidget != null || item.icon != null)
                                    const SizedBox(width: 16),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Text(
                                          item.label,
                                          style: TextStyle(
                                            color: itemColor,
                                            fontSize: 15,
                                            fontWeight: item.isDestructive
                                                ? FontWeight.w600
                                                : FontWeight.w500,
                                          ),
                                        ),
                                        if (item.subtitle != null) ...[
                                          const SizedBox(height: 2),
                                          Text(
                                            item.subtitle!,
                                            style: TextStyle(
                                              color: secondary,
                                              fontSize: 12,
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
                    );
                  },
                ),
              ),
              const SizedBox(height: 8),
            ],
          ),
        ),
      ),
    );
  }
}
