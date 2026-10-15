import 'package:flutter/material.dart';
import 'specular_glass_card.dart';
import 'spring_interactive_scale.dart';

class IsometricActionItem {
  final String id;
  final String label;
  final String? subtitle;
  final IconData icon;
  final Color? iconColor;
  final bool isDestructive;
  final VoidCallback onTap;

  const IsometricActionItem({
    required this.id,
    required this.label,
    this.subtitle,
    required this.icon,
    this.iconColor,
    this.isDestructive = false,
    required this.onTap,
  });
}

/// 3D-depth modal sheet with subtle 2.5D perspective styling and spring physics.
class IsometricActionSheet extends StatelessWidget {
  final String? title;
  final String? description;
  final List<IsometricActionItem> items;

  const IsometricActionSheet({
    super.key,
    this.title,
    this.description,
    required this.items,
  });

  static Future<T?> show<T>({
    required BuildContext context,
    String? title,
    String? description,
    required List<IsometricActionItem> items,
  }) {
    return showModalBottomSheet<T>(
      context: context,
      backgroundColor: Colors.transparent,
      barrierColor: Colors.black.withOpacity(0.55),
      isScrollControlled: true,
      builder: (context) => IsometricActionSheet(
        title: title,
        description: description,
        items: items,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 14.0, vertical: 12.0),
        child: SpecularGlassCard(
          borderRadius: 24.0,
          blur: 24.0,
          opacity: isDark ? 0.22 : 0.75,
          padding: const EdgeInsets.symmetric(horizontal: 18.0, vertical: 16.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              // Grab handle
              Container(
                width: 36,
                height: 4,
                decoration: BoxDecoration(
                  color: isDark ? Colors.white30 : Colors.black26,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              const SizedBox(height: 14),
              if (title != null) ...[
                Text(
                  title!,
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w700,
                    letterSpacing: -0.3,
                  ),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 4),
              ],
              if (description != null) ...[
                Text(
                  description!,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: isDark ? Colors.white60 : Colors.black54,
                  ),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 14),
              ],
              ...items.map((item) {
                final itemColor = item.isDestructive
                    ? Colors.redAccent
                    : (item.iconColor ?? theme.colorScheme.primary);

                return Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4.0),
                  child: SpringInteractiveScale(
                    onTap: () {
                      Navigator.of(context).pop();
                      item.onTap();
                    },
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 14.0, vertical: 12.0),
                      decoration: BoxDecoration(
                        color: isDark ? Colors.white.withOpacity(0.04) : Colors.black.withOpacity(0.03),
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(
                          color: isDark ? Colors.white.withOpacity(0.06) : Colors.black.withOpacity(0.04),
                        ),
                      ),
                      child: Row(
                        children: [
                          Container(
                            width: 38,
                            height: 38,
                            decoration: BoxDecoration(
                              color: itemColor.withOpacity(0.12),
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Icon(item.icon, color: itemColor, size: 20),
                          ),
                          const SizedBox(width: 14),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  item.label,
                                  style: TextStyle(
                                    fontSize: 15,
                                    fontWeight: FontWeight.w600,
                                    color: item.isDestructive ? Colors.redAccent : null,
                                  ),
                                ),
                                if (item.subtitle != null)
                                  Text(
                                    item.subtitle!,
                                    style: TextStyle(
                                      fontSize: 12,
                                      color: isDark ? Colors.white54 : Colors.black45,
                                    ),
                                  ),
                              ],
                            ),
                          ),
                          Icon(
                            Icons.chevron_right_rounded,
                            size: 18,
                            color: isDark ? Colors.white38 : Colors.black38,
                          ),
                        ],
                      ),
                    ),
                  ),
                );
              }),
              const SizedBox(height: 8),
            ],
          ),
        ),
      ),
    );
  }
}