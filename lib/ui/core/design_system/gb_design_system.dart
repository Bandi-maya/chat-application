import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../theme/theme_extensions.dart';
import '../theme/theme_controller.dart';
import '../../../injection/locator.dart';
import 'color_picker.dart';

/// Design tokens matching the extracted GB / WhatsApp modern design system.
/// Values adapt dynamically to Dark and Light modes.
class GbColors {
  GbColors._();

  static const Color background = Color(0xFF0C1014);
  static const Color surface = Color(0xFF182025);
  static const Color surfaceCard = Color(0xFF1C242A);
  static const Color modalSurface = Color(0xFF1F2B33);
  static const Color menuSurface = Color(0xFF182229);

  // Emerald / WhatsApp Greens (preserved as reference constants)
  static const Color primaryGreen = Color(0xFF00A884);
  static const Color activeGreen = Color(0xFF22C55E);
  static const Color accentGreen = Color(0xFF25D366);
  static const Color iconBadgeBg = Color(0xFF142E25);
  static const Color iconBadgeBorder = Color(0xFF1E4236);

  // Switch Colors
  static const Color switchTrackOff = Color(0xFF26332E);
  static const Color switchThumbOff = Color(0xFF455A51);
  static const Color switchTrackOn = Color(0xFF22C55E);
  static const Color switchThumbOn = Colors.white;

  // Text Colors
  static const Color textPrimary = Color(0xFFE9EDEF);
  static const Color textSecondary = Color(0xFF8696A0);
  static const Color textMuted = Color(0xFF667781);

  // Border & Dividers
  static const Color borderSubtle = Color(0xFF222D34);
  static const Color divider = Color(0xFF202A30);
}

/// Custom Pill Switch with minus `-` icon when off and checkmark `✓` when on.
/// Uses global theme accent color dynamically instead of hardcoded green.
class GbCustomSwitch extends StatelessWidget {
  final bool value;
  final ValueChanged<bool> onChanged;
  final Color? activeColor;
  final Color? inactiveTrackColor;
  final Color? inactiveThumbColor;

  const GbCustomSwitch({
    super.key,
    required this.value,
    required this.onChanged,
    this.activeColor,
    this.inactiveTrackColor,
    this.inactiveThumbColor,
  });

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final theme = locator<ThemeController>().globalTheme;
    final effectiveActiveColor = activeColor ?? theme.accentColor;
    final effectiveInactiveTrack =
        inactiveTrackColor ?? colors.borderSubtle.withValues(alpha: 0.8);
    final effectiveInactiveThumb =
        inactiveThumbColor ?? colors.foregroundTertiary;

    return GestureDetector(
      onTap: () {
        HapticFeedback.selectionClick();
        onChanged(!value);
      },
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        curve: Curves.easeInOut,
        width: 52,
        height: 30,
        padding: const EdgeInsets.symmetric(horizontal: 3, vertical: 3),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(15),
          color: value ? effectiveActiveColor : effectiveInactiveTrack,
          border: Border.all(
            color: value ? effectiveActiveColor : colors.borderSubtle,
            width: 1.2,
          ),
        ),
        child: AnimatedAlign(
          duration: const Duration(milliseconds: 200),
          curve: Curves.easeInOut,
          alignment: value ? Alignment.centerRight : Alignment.centerLeft,
          child: Container(
            width: 22,
            height: 22,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: value ? colors.onPrimary : effectiveInactiveThumb,
              boxShadow: [
                BoxShadow(
                  color: colors.shadow.withValues(alpha: 0.35),
                  blurRadius: 4,
                  offset: const Offset(0, 1.5),
                ),
              ],
            ),
            child: Center(
              child: value
                  ? Icon(
                      Icons.check_rounded,
                      size: 14,
                      color: effectiveActiveColor,
                    )
                  : Container(
                      width: 8,
                      height: 2,
                      decoration: const BoxDecoration(
                        color: Colors.white70,
                        borderRadius: BorderRadius.all(Radius.circular(1)),
                      ),
                    ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Convenient reusable Switch Tile combining GbSettingRow with GbCustomSwitch.
class GbSwitchTile extends StatelessWidget {
  final dynamic icon;
  final String title;
  final String? subtitle;
  final bool value;
  final ValueChanged<bool> onChanged;
  final Color? activeColor;

  const GbSwitchTile({
    super.key,
    required this.icon,
    required this.title,
    this.subtitle,
    required this.value,
    required this.onChanged,
    this.activeColor,
  });

  @override
  Widget build(BuildContext context) {
    return GbSettingRow(
      icon: icon,
      title: title,
      subtitle: subtitle,
      switchValue: value,
      onSwitchChanged: onChanged,
    );
  }
}

/// Reusable Glass-morphic Modal Sheet matching the 3-dots popup on home screen.
class GbModalSheet extends StatelessWidget {
  final String title;
  final String? subtitle;
  final Widget? trailing;
  final Widget child;

  const GbModalSheet({
    super.key,
    required this.title,
    this.subtitle,
    this.trailing,
    required this.child,
  });

  static Future<T?> show<T>({
    required BuildContext context,
    required String title,
    String? subtitle,
    Widget? trailing,
    WidgetBuilder? builder,
    Widget? child,
    bool isScrollControlled = true,
  }) {
    assert(builder != null || child != null, 'Either builder or child must be provided.');
    final colors = context.colors;
    final theme = locator<ThemeController>().globalTheme;
    return showDialog<T>(
      context: context,
      barrierDismissible: true,
      builder: (ctx) => Dialog(
        backgroundColor: Colors.transparent,
        insetPadding: const EdgeInsets.symmetric(horizontal: 24, vertical: 24),
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxWidth: 480,
            maxHeight: MediaQuery.of(ctx).size.height * 0.85,
          ),
          child: Container(
            decoration: BoxDecoration(
              color: colors.surfaceElevated,
              borderRadius: BorderRadius.circular(24),
              border: Border.all(color: colors.borderSubtle, width: 1.0),
              boxShadow: [
                BoxShadow(
                  color: colors.shadow.withValues(alpha: 0.4),
                  blurRadius: 28,
                  offset: const Offset(0, 10),
                ),
              ],
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Center(
                  child: Container(
                    width: 38,
                    height: 4,
                    margin: const EdgeInsets.only(top: 10, bottom: 6),
                    decoration: BoxDecoration(
                      color: colors.foregroundTertiary.withValues(alpha: 0.35),
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 10, 16, 10),
                  child: Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              title,
                              style: TextStyle(
                                color: theme.primaryTextColor,
                                fontSize: 18,
                                fontWeight: FontWeight.w700,
                                letterSpacing: -0.2,
                              ),
                            ),
                            if (subtitle != null && subtitle.isNotEmpty) ...[
                              const SizedBox(height: 3),
                              Text(
                                subtitle,
                                style: TextStyle(
                                  color: theme.secondaryTextColor,
                                  fontSize: 12.5,
                                ),
                              ),
                            ],
                          ],
                        ),
                      ),
                      if (trailing != null) trailing,
                      IconButton(
                        icon: const Icon(Icons.close_rounded),
                        iconSize: 20,
                        color: colors.foregroundSecondary,
                        onPressed: () => Navigator.of(ctx).pop(),
                      ),
                    ],
                  ),
                ),
                Divider(color: colors.divider, height: 1),
                Flexible(child: builder != null ? builder(ctx) : child!),
              ],
            ),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return child;
  }
}

/// Reusable Radio Selection Modal Component for choosing options.
class GbRadioSelectionModal<T> extends StatelessWidget {
  final String title;
  final String? subtitle;
  final List<T> options;
  final T selectedOption;
  final String Function(T)? labelBuilder;
  final String Function(T)? subtitleBuilder;
  final Widget Function(T)? iconBuilder;
  final ValueChanged<T> onSelected;

  const GbRadioSelectionModal({
    super.key,
    required this.title,
    this.subtitle,
    required this.options,
    required this.selectedOption,
    this.labelBuilder,
    this.subtitleBuilder,
    this.iconBuilder,
    required this.onSelected,
  });

  static Future<T?> show<T>({
    required BuildContext context,
    required String title,
    String? subtitle,
    required List<T> options,
    required T selectedOption,
    String Function(T)? labelBuilder,
    String Function(T)? subtitleBuilder,
    Widget Function(T)? iconBuilder,
    ValueChanged<T>? onSelected,
  }) {
    return GbModalSheet.show<T>(
      context: context,
      title: title,
      subtitle: subtitle,
      builder: (ctx) {
        final colors = ctx.colors;
        final theme = locator<ThemeController>().globalTheme;
        return ListView.separated(
          shrinkWrap: true,
          padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 10),
          itemCount: options.length,
          separatorBuilder: (_, __) => Divider(
            color: colors.divider.withValues(alpha: 0.4),
            height: 1,
            indent: 48,
          ),
          itemBuilder: (ctx, i) {
            final opt = options[i];
            final isSelected = opt == selectedOption;
            final label =
                labelBuilder != null ? labelBuilder(opt) : opt.toString();
            final sub =
                subtitleBuilder != null ? subtitleBuilder(opt) : null;
            final iconWidget =
                iconBuilder != null ? iconBuilder(opt) : null;

            return InkWell(
              onTap: () {
                HapticFeedback.selectionClick();
                Navigator.of(ctx).pop(opt);
                if (onSelected != null) {
                  onSelected(opt);
                }
              },
              borderRadius: BorderRadius.circular(12),
              child: Padding(
                padding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                child: Row(
                  children: [
                    if (iconWidget != null) ...[
                      iconWidget,
                      const SizedBox(width: 12),
                    ],
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            label,
                            style: TextStyle(
                              color: isSelected
                                  ? theme.primaryTextColor
                                  : theme.primaryTextColor.withValues(
                                      alpha: 0.85,
                                    ),
                              fontSize: 14.5,
                              fontWeight: isSelected
                                  ? FontWeight.w700
                                  : FontWeight.w500,
                            ),
                          ),
                          if (sub != null && sub.isNotEmpty) ...[
                            const SizedBox(height: 2),
                            Text(
                              sub,
                              style: TextStyle(
                                color: theme.secondaryTextColor,
                                fontSize: 12,
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
                    const SizedBox(width: 12),
                    Container(
                      width: 22,
                      height: 22,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        border: Border.all(
                          color: isSelected
                              ? theme.accentColor
                              : colors.border,
                          width: 2.0,
                        ),
                      ),
                      padding: const EdgeInsets.all(3.5),
                      child: isSelected
                          ? Container(
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                color: theme.accentColor,
                              ),
                            )
                          : null,
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return const SizedBox.shrink();
  }
}

/// Reusable Color Picker Modal wrapping ChatyColorPickerModal.
class GbColorPickerModal {
  GbColorPickerModal._();

  static Future<Color?> show(
    BuildContext context, {
    required String title,
    required Color currentColor,
    ValueChanged<Color>? onColorChanged,
    Color? backgroundContextColor,
  }) async {
    final res = await ChatyColorPickerModal.show(
      context,
      title: title,
      currentColor: currentColor,
      backgroundContextColor: backgroundContextColor,
    );
    if (res != null && onColorChanged != null) {
      onColorChanged(res);
    }
    return res;
  }
}

/// Backward compatibility alias for dialog.
class GbRadioSelectionDialog extends StatelessWidget {
  final String title;
  final List<String> options;
  final String selectedOption;
  final ValueChanged<String> onSelected;

  const GbRadioSelectionDialog({
    super.key,
    required this.title,
    required this.options,
    required this.selectedOption,
    required this.onSelected,
  });

  static Future<String?> show({
    required BuildContext context,
    required String title,
    required List<String> options,
    required String selectedOption,
  }) {
    return GbRadioSelectionModal.show<String>(
      context: context,
      title: title,
      options: options,
      selectedOption: selectedOption,
    );
  }

  @override
  Widget build(BuildContext context) => const SizedBox.shrink();
}

/// Reusable Settings Row Tile with dynamic colors and multiple trailing controls.
class GbSettingRow extends StatelessWidget {
  final dynamic icon;
  final String title;
  final String? subtitle;
  final bool? switchValue;
  final ValueChanged<bool>? onSwitchChanged;
  final int? colorValue;
  final dynamic colorHex;
  final VoidCallback? onColorTap;
  final VoidCallback? onTap;
  final bool showChevron;
  final bool hasSubScreen;
  final Widget? trailing;
  final Color? iconColor;
  final Color? iconBgColor;

  const GbSettingRow({
    super.key,
    required this.icon,
    required this.title,
    this.subtitle,
    this.switchValue,
    this.onSwitchChanged,
    this.colorValue,
    this.colorHex,
    this.onColorTap,
    this.onTap,
    this.showChevron = false,
    this.hasSubScreen = false,
    this.trailing,
    this.iconColor,
    this.iconBgColor,
  });

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final theme = locator<ThemeController>().globalTheme;
    final effectiveIconColor = iconColor ?? theme.accentColor;
    final effectiveIconBg =
        iconBgColor ?? effectiveIconColor.withValues(alpha: 0.12);

    Widget iconWidget;
    if (icon is IconData) {
      iconWidget = Icon(icon as IconData, color: effectiveIconColor, size: 20);
    } else if (icon is Widget) {
      iconWidget = icon as Widget;
    } else {
      iconWidget = Icon(Icons.circle, color: effectiveIconColor, size: 20);
    }

    int? effectiveColorInt = colorValue;
    if (effectiveColorInt == null && colorHex != null) {
      if (colorHex is int) {
        effectiveColorInt = colorHex as int;
      } else if (colorHex is String) {
        final hexStr = (colorHex as String).replaceAll('#', '');
        if (hexStr.length == 6) {
          effectiveColorInt = int.tryParse('FF$hexStr', radix: 16);
        } else if (hexStr.length == 8) {
          effectiveColorInt = int.tryParse(hexStr, radix: 16);
        }
      }
    }

    return InkWell(
      onTap: () {
        HapticFeedback.selectionClick();
        if (onSwitchChanged != null && switchValue != null) {
          onSwitchChanged!(!switchValue!);
        } else if (onColorTap != null) {
          onColorTap!();
        } else if (onTap != null) {
          onTap!();
        }
      },
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Container(
              width: 38,
              height: 38,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: effectiveIconBg,
                border: Border.all(
                  color: effectiveIconColor.withValues(alpha: 0.25),
                  width: 1.2,
                ),
              ),
              child: Center(child: iconWidget),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    title,
                    style: TextStyle(
                      color: theme.primaryTextColor,
                      fontSize: 14.5,
                      fontWeight: FontWeight.w600,
                      letterSpacing: -0.1,
                    ),
                  ),
                  if (subtitle != null && subtitle!.isNotEmpty) ...[
                    const SizedBox(height: 3),
                    Text(
                      subtitle!,
                      style: TextStyle(
                        color: theme.secondaryTextColor,
                        fontSize: 12.0,
                        height: 1.25,
                      ),
                    ),
                  ],
                ],
              ),
            ),
            if (switchValue != null && onSwitchChanged != null)
              GbCustomSwitch(
                value: switchValue!,
                onChanged: onSwitchChanged!,
              )
            else if (effectiveColorInt != null)
              GestureDetector(
                onTap: onColorTap,
                child: Container(
                  width: 26,
                  height: 26,
                  decoration: BoxDecoration(
                    color: Color(effectiveColorInt),
                    shape: BoxShape.circle,
                    border: Border.all(color: colors.borderSubtle, width: 1.5),
                  ),
                ),
              )
            else if (trailing != null)
              trailing!
            else if (showChevron || hasSubScreen)
              Icon(
                Icons.chevron_right_rounded,
                color: theme.accentColor,
                size: 22,
              ),
          ],
        ),
      ),
    );
  }
}

/// Reusable Slider Tile with live numeric readout.
class GbSliderTile extends StatelessWidget {
  final dynamic icon;
  final String title;
  final String? subtitle;
  final double value;
  final double min;
  final double max;
  final int? divisions;
  final String suffix;
  final String? unit;
  final ValueChanged<double> onChanged;

  const GbSliderTile({
    super.key,
    this.icon,
    required this.title,
    this.subtitle,
    required this.value,
    this.min = 10.0,
    this.max = 24.0,
    this.divisions,
    this.suffix = '',
    this.unit,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final theme = locator<ThemeController>().globalTheme;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              if (icon != null) ...[
                if (icon is IconData)
                  Icon(icon as IconData, size: 18, color: theme.accentColor)
                else if (icon is Widget)
                  icon as Widget,
                const SizedBox(width: 10),
              ],
              Expanded(
                child: Text(
                  title,
                  style: TextStyle(
                    color: theme.primaryTextColor,
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: theme.accentColor.withValues(alpha: 0.15),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(
                  '${value.toInt()}${unit ?? suffix}',
                  style: TextStyle(
                    color: theme.accentColor,
                    fontSize: 12.5,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ],
          ),
          if (subtitle != null && subtitle!.isNotEmpty) ...[
            const SizedBox(height: 4),
            Text(
              subtitle!,
              style: TextStyle(
                color: theme.secondaryTextColor,
                fontSize: 12.0,
              ),
            ),
          ],
          SliderTheme(
            data: SliderTheme.of(context).copyWith(
              activeTrackColor: theme.accentColor,
              inactiveTrackColor: colors.borderSubtle,
              thumbColor: theme.accentColor,
              overlayColor: theme.accentColor.withValues(alpha: 0.2),
            ),
            child: Slider(
              value: value.clamp(min, max),
              min: min,
              max: max,
              divisions: divisions ?? (max - min).toInt(),
              onChanged: onChanged,
            ),
          ),
        ],
      ),
    );
  }
}

/// Stylized section divider matching the `—•— Label —•—` style.
class GbSectionDivider extends StatelessWidget {
  final String label;
  final Color? accent;

  const GbSectionDivider({
    super.key,
    required this.label,
    this.accent,
  });

  @override
  Widget build(BuildContext context) {
    final theme = locator<ThemeController>().globalTheme;
    final effAccent = accent ?? theme.accentColor;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 12),
      child: Row(
        children: [
          Expanded(
            child: Container(
              height: 1,
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [
                    effAccent.withValues(alpha: 0.0),
                    effAccent.withValues(alpha: 0.4),
                  ],
                ),
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 10),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 5,
                  height: 5,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: effAccent,
                  ),
                ),
                const SizedBox(width: 8),
                Text(
                  label,
                  style: TextStyle(
                    color: effAccent,
                    fontSize: 12.5,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 0.4,
                  ),
                ),
                const SizedBox(width: 8),
                Container(
                  width: 5,
                  height: 5,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: effAccent,
                  ),
                ),
              ],
            ),
          ),
          Expanded(
            child: Container(
              height: 1,
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [
                    effAccent.withValues(alpha: 0.4),
                    effAccent.withValues(alpha: 0.0),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Sleek card container grouping a list of settings rows without hardcoded green borders.
class GbCardContainer extends StatelessWidget {
  final List<Widget> children;
  final EdgeInsetsGeometry? padding;
  final EdgeInsetsGeometry? margin;
  final String? headerText;

  const GbCardContainer({
    super.key,
    required this.children,
    this.padding,
    this.margin,
    this.headerText,
  });

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final theme = locator<ThemeController>().globalTheme;
    final card = Container(
      margin: margin ?? const EdgeInsets.symmetric(vertical: 6),
      padding: padding ?? const EdgeInsets.symmetric(vertical: 4),
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: BorderRadius.circular(theme.cornerRadius),
        border: Border.all(color: colors.borderSubtle, width: 0.9),
        boxShadow: [
          BoxShadow(
            color: colors.shadow.withValues(alpha: 0.15),
            blurRadius: 16,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: children,
      ),
    );

    if (headerText != null && headerText!.isNotEmpty) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
            child: Text(
              headerText!,
              style: TextStyle(
                color: theme.accentColor,
                fontSize: 13,
                fontWeight: FontWeight.w700,
                letterSpacing: 0.4,
              ),
            ),
          ),
          card,
        ],
      );
    }
    return card;
  }
}

/// Reusable Top/Bottom Live Interactive Preview Card.
class GbLivePreviewCard extends StatelessWidget {
  final String label;
  final Widget child;
  final double? height;
  final EdgeInsetsGeometry? padding;

  const GbLivePreviewCard({
    super.key,
    this.label = 'LIVE INTERACTIVE PREVIEW',
    required this.child,
    this.height,
    this.padding,
  });

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final theme = locator<ThemeController>().globalTheme;

    final content = height != null ? SizedBox(height: height, child: child) : child;

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: colors.surfaceElevated,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: theme.accentColor.withValues(alpha: 0.35),
          width: 1.2,
        ),
        boxShadow: [
          BoxShadow(
            color: colors.shadow.withValues(alpha: 0.25),
            blurRadius: 18,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
            decoration: BoxDecoration(
              color: theme.accentColor.withValues(alpha: 0.12),
              borderRadius:
                  const BorderRadius.vertical(top: Radius.circular(19)),
            ),
            child: Row(
              children: [
                Container(
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: theme.accentColor,
                  ),
                ),
                const SizedBox(width: 8),
                Text(
                  label,
                  style: TextStyle(
                    color: theme.accentColor,
                    fontSize: 11,
                    fontWeight: FontWeight.w800,
                    letterSpacing: 0.6,
                  ),
                ),
              ],
            ),
          ),
          Padding(
            padding: padding ?? const EdgeInsets.all(12),
            child: content,
          ),
        ],
      ),
    );
  }
}

/// Fixed Live Preview of Home Header pinned at the top of the Header Settings screen.
/// Lively reflects current user options (Set My Name, Disable Search Bar, Separate Groups, Tabs, etc.).
/// Fixed Live Preview of Home Header and Home Screen pinned at the top of the Header Settings screen.
/// Lively reflects chosen Home UI Style ('ONE UI', 'WhatsApp UI Stock', 'IOS STYLE', 'BUBBLES TAB STYLE', 'BASIC TAB STYLE', 'WhatsApp OLD UI'),
/// Tab Bubble Styles, 3D Transition effects, Search Bar, Groups separation, and corresponding Bottom Navigation design.
class GbLiveHeaderPreview extends StatelessWidget {
  // Always resolve preview colors from the active application-wide theme.
  dynamic get _liveTheme => locator<ThemeController>().globalTheme;

  final String displayName;
  final String statusText;
  final bool setMyName;
  final bool disableStatusUnderName;
  final bool disableSearchBar;
  final bool showSearchBar;
  final bool showCameraIcon;
  final bool showDesktopIcon;
  final bool showIconAddAccount;
  final bool separateChatsAndGroups;
  final String homeStyle;
  final String tabBubbleStyle;
  final String pagerTransition3d;
  final String searchPlacement;
  final VoidCallback onThreeDotsClick;

  bool get _showHeaderSearch =>
      showSearchBar && !disableSearchBar &&
      searchPlacement.trim().toLowerCase() == 'header action';
  bool get _showInlineSearch =>
      showSearchBar &&
      searchPlacement.trim().toLowerCase() != 'header action';

  const GbLiveHeaderPreview({
    super.key,
    required this.displayName,
    this.statusText = '',
    required this.setMyName,
    required this.disableStatusUnderName,
    required this.disableSearchBar,
    this.showSearchBar = true,
    this.showCameraIcon = false,
    this.showDesktopIcon = true,
    this.showIconAddAccount = false,
    required this.separateChatsAndGroups,
    required this.homeStyle,
    this.tabBubbleStyle = 'Capsule Pill',
    this.pagerTransition3d = 'Cube 3D',
    this.searchPlacement = 'Header action',
    required this.onThreeDotsClick,
  });

  @override
  Widget build(BuildContext context) {
    final title = setMyName && displayName.isNotEmpty ? displayName : 'Chaty';
    final isOneUi = homeStyle == 'ONE UI';
    final isIos = homeStyle == 'IOS STYLE';
    final isStock = homeStyle == 'WhatsApp UI Stock';
    final isBubbles = homeStyle == 'BUBBLES TAB STYLE';
    final isBasicTabs = homeStyle == 'BASIC TAB STYLE';
    final isInstagram = homeStyle == 'Instagram Style';
    final isTelegram = homeStyle == 'Telegram Style';
    final isFloatingRail = homeStyle == 'Floating Rail';
    final isPerspectiveDrawer = homeStyle == '3D Perspective Drawer';
    final isModernDrawer = homeStyle == 'Modern Side Menu';
    final isCurvedRadial = homeStyle == 'Curved Radial Menu';
    final isOldUi = homeStyle == 'WhatsApp OLD UI';

    return Container(
      width: double.infinity,
      margin: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
      decoration: BoxDecoration(
        color: _liveTheme.backgroundColor,
        borderRadius: BorderRadius.circular(
          _liveTheme.cornerRadius.clamp(0.0, 32.0).toDouble(),
        ),
        border: Border.all(color: _liveTheme.cardColor, width: 1.2),
        boxShadow: [
          BoxShadow(
            color: _liveTheme.backgroundColor.withValues(alpha: 0.4),
            blurRadius: 16,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Mode Indicator Header Bar
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
            color: _liveTheme.surfaceColor,
            child: Row(
              children: [
                Container(
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: _liveTheme.accentColor,
                    shape: BoxShape.circle,
                  ),
                ),
                SizedBox(width: 8),
                Text(
                  'LIVE PREVIEW • $homeStyle',
                  style: TextStyle(
                    color: _liveTheme.accentColor,
                    fontSize: 11,
                    fontWeight: FontWeight.w800,
                    letterSpacing: 0.6,
                  ),
                ),
                Spacer(),
                Text(
                  '3D: $pagerTransition3d',
                  style: TextStyle(
                    color: _liveTheme.secondaryTextColor,
                    fontSize: 10.5,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),

          // 1. TOP HEADER BASED ON STYLE
          if (isOneUi)
            _buildOneUiHeader(title)
          else if (isIos)
            _buildIosHeader(title)
          else if (isStock)
            _buildStockHeader(title)
          else if (isBubbles)
            _buildBubblesHeader(title)
          else if (isOldUi)
            _buildOldUiHeader(title)
          else if (isInstagram)
            _buildInstagramHeader(title)
          else if (isTelegram)
            _buildTelegramHeader(title)
          else if (isCurvedRadial)
            _buildCurvedRadialHeader(title)
          else if (isFloatingRail)
            _buildFloatingRailHeader(title)
          else if (isPerspectiveDrawer || isModernDrawer)
            _buildDrawerHeader(title)
          else
            _buildBasicHeader(title),

          if ((showCameraIcon && !isStock && !isOldUi) ||
              showDesktopIcon ||
              showIconAddAccount)
            Padding(
              padding: const EdgeInsets.fromLTRB(14, 0, 14, 4),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  if (showCameraIcon && !isStock && !isOldUi)
                    _buildHeaderActionPreviewIcon(Icons.photo_camera_outlined, 'Camera'),
                  if (showDesktopIcon)
                    _buildHeaderActionPreviewIcon(Icons.qr_code_scanner_rounded, 'Scan'),
                  if (showIconAddAccount)
                    _buildHeaderActionPreviewIcon(Icons.person_add_alt_1_rounded, 'Account'),
                ],
              ),
            ),
          // Search placement is previewed in the same position as the live Chats screen.
          if (_showInlineSearch &&
              searchPlacement.trim().toLowerCase() == 'below header')
            _buildSearchPlacementPreview(),
          if (_showInlineSearch &&
              searchPlacement.trim().toLowerCase() == 'above chat filters')
            _buildSearchAboveFiltersPreview(),

          // 2. LIVE SAMPLE CHAT ROW (Tyler Durden)
          _buildSampleChatRow(),

          // 3. LIVE BOTTOM BAR PREVIEW BASED ON STYLE
          if (isOneUi)
            _buildOneUiBottomBar()
          else if (isIos)
            _buildIosBottomBar()
          else if (isStock)
            _buildStockBottomBar()
          else if (isBubbles)
            _buildBubblesBottomBar()
          else if (isOldUi)
            _buildOldUiBottomBar()
          else if (isInstagram)
            _buildInstagramBottomBar()
          else if (isTelegram)
            _buildTelegramBottomBar()
          else if (isBasicTabs)
            _buildBasicBottomBar()
          else if (isFloatingRail)
            _buildFloatingRailBottomBar()
          else if (isCurvedRadial)
            _buildCurvedRadialBottomBar()
          else if (isPerspectiveDrawer || isModernDrawer)
            _buildDrawerBottomBar()
          else
            _buildBasicBottomBar(),
        ],
      ),
    );
  }

  Widget _buildHeaderActionPreviewIcon(IconData icon, String label) => Padding(
    padding: const EdgeInsets.only(left: 11),
    child: Tooltip(
      message: label,
      child: Icon(
        icon,
        color: _liveTheme.primaryTextColor,
        size: 18,
      ),
    ),
  );

  Widget _buildSearchPlacementPreview() => Padding(
    padding: const EdgeInsets.fromLTRB(14, 0, 14, 8),
    child: Container(
      height: 30,
      padding: const EdgeInsets.symmetric(horizontal: 10),
      decoration: BoxDecoration(
        color: _liveTheme.cardColor,
        borderRadius: BorderRadius.circular(
          _liveTheme.cornerRadius.clamp(0.0, 28.0).toDouble(),
        ),
        border: Border.all(
          color: _liveTheme.secondaryTextColor.withValues(alpha: 0.24),
        ),
      ),
      child: Row(
        children: [
          Icon(Icons.search_rounded, color: _liveTheme.secondaryTextColor, size: 15),
          const SizedBox(width: 7),
          Text(
            'Search chats',
            style: TextStyle(color: _liveTheme.secondaryTextColor, fontSize: 11),
          ),
          const Spacer(),
          Icon(Icons.tune_rounded, color: _liveTheme.secondaryTextColor, size: 14),
        ],
      ),
    ),
  );

  Widget _buildSearchAboveFiltersPreview() => Padding(
    padding: const EdgeInsets.fromLTRB(14, 0, 14, 8),
    child: Column(
      children: [
        _buildSearchPlacementPreview(),
        Row(
          children: [
            for (final tag in const <String>['All', 'Unread', 'Groups'])
              Padding(
                padding: const EdgeInsets.only(right: 6),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: tag == 'All'
                        ? _liveTheme.accentColor.withValues(alpha: 0.14)
                        : _liveTheme.cardColor,
                    border: Border.all(
                      color: tag == 'All'
                          ? _liveTheme.accentColor.withValues(alpha: 0.45)
                          : _liveTheme.secondaryTextColor.withValues(alpha: 0.18),
                    ),
                  ),
                  child: Text(
                    tag,
                    style: TextStyle(
                      color: tag == 'All'
                          ? _liveTheme.accentColor
                          : _liveTheme.secondaryTextColor,
                      fontSize: 9,
                      fontWeight: tag == 'All' ? FontWeight.w700 : FontWeight.w500,
                    ),
                  ),
                ),
              ),
          ],
        ),
      ],
    ),
  );

  // Each preview below mirrors the selected layout's hierarchy while using
  // the same live theme tokens as the real application shell.
  Widget _buildInstagramHeader(String title) {
    return Container(
      padding: const EdgeInsets.fromLTRB(14, 10, 14, 9),
      color: _liveTheme.backgroundColor,
      child: Row(
        children: [
          Expanded(
            child: Text(
              title,
              style: TextStyle(
                color: _liveTheme.primaryTextColor,
                fontSize: 20,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
          if (_showHeaderSearch)
            Icon(Icons.search_rounded, color: _liveTheme.primaryTextColor, size: 21),
          SizedBox(width: 12),
          Icon(Icons.favorite_border_rounded, color: _liveTheme.primaryTextColor, size: 21),
          SizedBox(width: 10),
          GestureDetector(
            onTap: onThreeDotsClick,
            child: Icon(Icons.more_horiz_rounded, color: _liveTheme.primaryTextColor, size: 22),
          ),
        ],
      ),
    );
  }

  Widget _buildTelegramHeader(String title) {
    return Container(
      padding: const EdgeInsets.fromLTRB(14, 9, 14, 9),
      color: _liveTheme.surfaceColor,
      child: Column(
        children: [
          Row(
            children: [
              Icon(Icons.menu_rounded, color: _liveTheme.accentColor, size: 21),
              SizedBox(width: 10),
              Expanded(
                child: Text(
                  title,
                  style: TextStyle(
                    color: _liveTheme.primaryTextColor,
                    fontSize: 19,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              if (_showHeaderSearch)
                Icon(Icons.search_rounded, color: _liveTheme.secondaryTextColor, size: 21),
              SizedBox(width: 12),
              GestureDetector(
                onTap: onThreeDotsClick,
                child: Icon(Icons.more_vert_rounded, color: _liveTheme.secondaryTextColor, size: 21),
              ),
            ],
          ),
          SizedBox(height: 8),
          if (_showHeaderSearch)
            Container(
              height: 30,
              padding: const EdgeInsets.symmetric(horizontal: 8),
              decoration: BoxDecoration(
                color: _liveTheme.cardColor,
                border: Border.all(color: _liveTheme.secondaryTextColor.withValues(alpha: 0.28)),
              ),
              child: Row(
                children: [
                  Icon(Icons.search_rounded, color: _liveTheme.secondaryTextColor, size: 15),
                  SizedBox(width: 6),
                  Text(
                    'Search chats',
                    style: TextStyle(color: _liveTheme.secondaryTextColor, fontSize: 12),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildCurvedRadialHeader(String title) {
    return Container(
      color: _liveTheme.backgroundColor,
      padding: const EdgeInsets.fromLTRB(14, 10, 14, 10),
      child: Row(
        children: [
          Container(
            width: 30,
            height: 30,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: _liveTheme.accentColor.withValues(alpha: 0.16),
              border: Border.all(color: _liveTheme.accentColor),
            ),
            child: Icon(Icons.menu_rounded, color: _liveTheme.accentColor, size: 18),
          ),
          SizedBox(width: 10),
          Expanded(
            child: Text(
              title,
              style: TextStyle(
                color: _liveTheme.primaryTextColor,
                fontSize: 18,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
          GestureDetector(
            onTap: onThreeDotsClick,
            child: Icon(Icons.more_vert_rounded, color: _liveTheme.primaryTextColor, size: 22),
          ),
        ],
      ),
    );
  }

  Widget _buildFloatingRailHeader(String title) {
    return Container(
      color: _liveTheme.backgroundColor,
      padding: const EdgeInsets.all(12),
      child: Row(
        children: [
          Container(
            width: 30,
            height: 54,
            decoration: BoxDecoration(
              color: _liveTheme.surfaceColor,
              border: Border.all(color: _liveTheme.cardColor),
            ),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                Icon(Icons.chat_bubble_rounded, color: _liveTheme.accentColor, size: 16),
                Icon(Icons.auto_stories_rounded, color: _liveTheme.secondaryTextColor, size: 16),
              ],
            ),
          ),
          SizedBox(width: 10),
          Expanded(
            child: Text(
              title,
              style: TextStyle(
                color: _liveTheme.primaryTextColor,
                fontSize: 19,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
          Icon(Icons.search_rounded, color: _liveTheme.secondaryTextColor),
        ],
      ),
    );
  }

  Widget _buildDrawerHeader(String title) {
    return Container(
      color: _liveTheme.backgroundColor,
      padding: const EdgeInsets.fromLTRB(14, 10, 14, 10),
      child: Row(
        children: [
          Container(
            width: 82,
            height: 50,
            padding: const EdgeInsets.all(5),
            decoration: BoxDecoration(
              color: _liveTheme.surfaceColor,
              border: Border.all(color: _liveTheme.cardColor),
            ),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                Container(height: 3, color: _liveTheme.accentColor),
                Container(height: 2, color: _liveTheme.secondaryTextColor),
                Container(height: 2, color: _liveTheme.secondaryTextColor.withValues(alpha: 0.55)),
              ],
            ),
          ),
          SizedBox(width: 10),
          Expanded(
            child: Text(
              title,
              style: TextStyle(
                color: _liveTheme.primaryTextColor,
                fontSize: 18,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
          GestureDetector(
            onTap: onThreeDotsClick,
            child: Icon(Icons.more_vert_rounded, color: _liveTheme.primaryTextColor),
          ),
        ],
      ),
    );
  }

  Widget _buildInstagramBottomBar() {
    return Container(
      height: 44,
      color: _liveTheme.backgroundColor,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          Icon(Icons.home_rounded, color: _liveTheme.primaryTextColor, size: 23),
          Icon(Icons.search_rounded, color: _liveTheme.primaryTextColor, size: 23),
          Icon(Icons.add_box_outlined, color: _liveTheme.primaryTextColor, size: 23),
          Icon(Icons.play_circle_outline_rounded, color: _liveTheme.primaryTextColor, size: 23),
          Icon(Icons.person_outline_rounded, color: _liveTheme.primaryTextColor, size: 23),
        ],
      ),
    );
  }

  Widget _buildPreviewTab(
    IconData icon,
    String label, {
    bool selected = false,
  }) {
    return Expanded(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            icon,
            size: 15,
            color: selected ? _liveTheme.accentColor : _liveTheme.secondaryTextColor,
          ),
          const SizedBox(height: 2),
          Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              color: selected ? _liveTheme.primaryTextColor : _liveTheme.secondaryTextColor,
              fontSize: 8.5,
              fontWeight: selected ? FontWeight.w800 : FontWeight.w500,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTelegramBottomBar() {
    return Container(
      height: 50,
      decoration: BoxDecoration(
        color: _liveTheme.surfaceColor,
        border: Border(top: BorderSide(color: _liveTheme.cardColor)),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          _buildPreviewTab(Icons.chat_bubble_rounded, 'Chats', selected: true),
          _buildPreviewTab(Icons.contacts_rounded, 'Contacts'),
          _buildPreviewTab(Icons.call_rounded, 'Calls'),
          _buildPreviewTab(Icons.settings_rounded, 'Settings'),
        ],
      ),
    );
  }

  Widget _buildFloatingRailBottomBar() {
    return Container(
      height: 44,
      color: _liveTheme.backgroundColor,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          _buildPreviewTab(Icons.chat_bubble_rounded, 'Chats', selected: true),
          _buildPreviewTab(Icons.auto_stories_rounded, 'Updates'),
          _buildPreviewTab(Icons.call_rounded, 'Calls'),
        ],
      ),
    );
  }

  Widget _buildCurvedRadialBottomBar() {
    return Container(
      height: 44,
      color: _liveTheme.surfaceColor,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          for (final icon in <IconData>[
            Icons.chat_bubble_rounded,
            Icons.auto_stories_rounded,
            Icons.call_rounded,
            Icons.settings_rounded,
          ])
            Container(
              width: 28,
              height: 28,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: _liveTheme.accentColor.withValues(alpha: 0.14),
                border: Border.all(color: _liveTheme.accentColor.withValues(alpha: 0.5)),
              ),
              child: Icon(icon, size: 14, color: _liveTheme.accentColor),
            ),
        ],
      ),
    );
  }

  Widget _buildDrawerBottomBar() {
    return Container(
      height: 44,
      color: _liveTheme.surfaceColor,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          _buildPreviewTab(Icons.chat_bubble_rounded, 'Chats', selected: true),
          _buildPreviewTab(Icons.auto_stories_rounded, 'Updates'),
          _buildPreviewTab(Icons.call_rounded, 'Calls'),
          _buildPreviewTab(Icons.settings_rounded, 'Settings'),
        ],
      ),
    );
  }

  // --- One UI Header ---
  Widget _buildOneUiHeader(String title) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 10),
      color: _liveTheme.surfaceColor,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.end,
            children: [
              if (_showHeaderSearch)
                IconButton(
                  icon: Icon(Icons.search_rounded, color: _liveTheme.primaryTextColor, size: 21),
                  onPressed: () {},
                  visualDensity: VisualDensity.compact,
                ),
              IconButton(
                icon: Icon(Icons.more_vert_rounded, color: _liveTheme.primaryTextColor, size: 21),
                onPressed: onThreeDotsClick,
                visualDensity: VisualDensity.compact,
              ),
            ],
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: TextStyle(
                    color: _liveTheme.primaryTextColor,
                    fontSize: 23,
                    fontWeight: FontWeight.w800,
                    letterSpacing: -0.4,
                  ),
                ),
                if (!disableStatusUnderName && statusText.isNotEmpty) ...[
                  SizedBox(height: 2),
                  Text(
                    statusText,
                    style: TextStyle(
                      color: _liveTheme.secondaryTextColor,
                      fontSize: 12,
                    ),
                  ),
                ],
              ],
            ),
          ),
          SizedBox(height: 10),
          Row(
            children: [
              _buildPillTab('Chats', isSelected: true, count: '7'),
              SizedBox(width: 8),
              if (separateChatsAndGroups) ...[
                _buildPillTab('Groups'),
                SizedBox(width: 8),
              ],
              _buildPillTab('Status'),
              SizedBox(width: 8),
              _buildPillTab('Calls'),
            ],
          ),
        ],
      ),
    );
  }

  // --- iOS Style Header ---
  Widget _buildIosHeader(String title) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
      color: _liveTheme.surfaceColor,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text(
                'Edit',
                style: TextStyle(
                  color: _liveTheme.accentColor,
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                ),
              ),
              Spacer(),
              IconButton(
                icon: Icon(Icons.more_vert_rounded, color: _liveTheme.accentColor, size: 20),
                onPressed: onThreeDotsClick,
                visualDensity: VisualDensity.compact,
              ),
            ],
          ),
          Text(
            title,
            style: TextStyle(
              color: _liveTheme.primaryTextColor,
              fontSize: 22,
              fontWeight: FontWeight.w800,
              letterSpacing: -0.5,
            ),
          ),
          if (_showHeaderSearch) ...[
            SizedBox(height: 8),
            Container(
              height: 32,
              padding: const EdgeInsets.symmetric(horizontal: 10),
              decoration: BoxDecoration(
                color: _liveTheme.cardColor,
                borderRadius: BorderRadius.circular(10),
              ),
              child: Row(
                children: [
                  Icon(Icons.search_rounded, color: _liveTheme.secondaryTextColor, size: 16),
                  SizedBox(width: 6),
                  Text(
                    'Search',
                    style: TextStyle(color: _liveTheme.secondaryTextColor, fontSize: 13),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  // --- WhatsApp UI Stock Header ---
  Widget _buildStockHeader(String title) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 8),
      color: _liveTheme.backgroundColor,
      child: Row(
        children: [
          Text(
            title,
            style: TextStyle(
              color: _liveTheme.accentColor,
              fontSize: 19,
              fontWeight: FontWeight.w800,
            ),
          ),
          Spacer(),
          if (showCameraIcon) ...[
            Icon(Icons.camera_alt_outlined, color: _liveTheme.primaryTextColor, size: 21),
            SizedBox(width: 14),
          ],
          if (_showHeaderSearch) ...[
            Icon(Icons.search_rounded, color: _liveTheme.primaryTextColor, size: 21),
            SizedBox(width: 14),
          ],
          GestureDetector(
            onTap: onThreeDotsClick,
            child: Icon(Icons.more_vert_rounded, color: _liveTheme.primaryTextColor, size: 21),
          ),
        ],
      ),
    );
  }

  // --- Bubbles Tab Style Header ---
  Widget _buildBubblesHeader(String title) {
    return Container(
      padding: const EdgeInsets.fromLTRB(14, 10, 14, 10),
      color: _liveTheme.backgroundColor,
      child: Column(
        children: [
          Row(
            children: [
              Text(
                title,
                style: TextStyle(
                  color: _liveTheme.primaryTextColor,
                  fontSize: 18,
                  fontWeight: FontWeight.w700,
                ),
              ),
              Spacer(),
              if (_showHeaderSearch)
                Icon(Icons.search_rounded, color: _liveTheme.primaryTextColor, size: 20),
              SizedBox(width: 12),
              GestureDetector(
                onTap: onThreeDotsClick,
                child: Icon(Icons.more_vert_rounded, color: _liveTheme.primaryTextColor, size: 20),
              ),
            ],
          ),
          SizedBox(height: 10),
          Row(
            children: [
              _buildBubbleTabItem('Chats', isSelected: true, count: '7'),
              SizedBox(width: 6),
              if (separateChatsAndGroups) ...[
                _buildBubbleTabItem('Groups'),
                SizedBox(width: 6),
              ],
              _buildBubbleTabItem('Status'),
              SizedBox(width: 6),
              _buildBubbleTabItem('Calls'),
            ],
          ),
        ],
      ),
    );
  }

  // --- WhatsApp Old UI Header ---
  Widget _buildOldUiHeader(String title) {
    return Container(
      color: _liveTheme.surfaceColor,
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Row(
              children: [
                Text(
                  title,
                  style: TextStyle(
                    color: _liveTheme.primaryTextColor,
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                Spacer(),
                if (_showHeaderSearch) ...[
                  Icon(Icons.search, color: _liveTheme.primaryTextColor, size: 21),
                  SizedBox(width: 14),
                ],
                GestureDetector(
                  onTap: onThreeDotsClick,
                  child: Icon(Icons.more_vert, color: _liveTheme.primaryTextColor, size: 21),
                ),
              ],
            ),
          ),
          Row(
            children: [
              if (showCameraIcon)
                SizedBox(width: 36, child: Center(child: Icon(Icons.camera_alt, color: _liveTheme.secondaryTextColor, size: 18))),
              Expanded(
                child: Column(
                  children: [
                    Padding(
                      padding: EdgeInsets.symmetric(vertical: 6),
                      child: Text('CHATS', style: TextStyle(color: _liveTheme.accentColor, fontSize: 13, fontWeight: FontWeight.bold)),
                    ),
                    Container(height: 2.5, color: _liveTheme.accentColor),
                  ],
                ),
              ),
              if (separateChatsAndGroups)
                Expanded(child: Center(child: Text('GROUPS', style: TextStyle(color: _liveTheme.secondaryTextColor, fontSize: 13)))),
              Expanded(child: Center(child: Text('STATUS', style: TextStyle(color: _liveTheme.secondaryTextColor, fontSize: 13)))),
              Expanded(child: Center(child: Text('CALLS', style: TextStyle(color: _liveTheme.secondaryTextColor, fontSize: 13)))),
            ],
          ),
        ],
      ),
    );
  }

  // --- Basic Tab Header ---
  Widget _buildBasicHeader(String title) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 8),
      color: _liveTheme.backgroundColor,
      child: Column(
        children: [
          Row(
            children: [
              Text(
                title,
                style: TextStyle(color: _liveTheme.primaryTextColor, fontSize: 18, fontWeight: FontWeight.bold),
              ),
              Spacer(),
              if (_showHeaderSearch) ...[
                Icon(Icons.search, color: _liveTheme.primaryTextColor, size: 20),
                SizedBox(width: 12),
              ],
              GestureDetector(
                onTap: onThreeDotsClick,
                child: Icon(Icons.more_vert, color: _liveTheme.primaryTextColor, size: 20),
              ),
            ],
          ),
          SizedBox(height: 8),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              Text('Chats (7)', style: TextStyle(color: _liveTheme.primaryTextColor, fontWeight: FontWeight.bold, fontSize: 13)),
              if (separateChatsAndGroups)
                Text('Groups', style: TextStyle(color: _liveTheme.secondaryTextColor, fontSize: 13)),
              Text('Status', style: TextStyle(color: _liveTheme.secondaryTextColor, fontSize: 13)),
              Text('Calls', style: TextStyle(color: _liveTheme.secondaryTextColor, fontSize: 13)),
            ],
          ),
        ],
      ),
    );
  }

  // --- Sample Tyler Durden Chat Row ---
  Widget _buildSampleChatRow() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      color: _liveTheme.backgroundColor,
      child: Row(
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: _liveTheme.cardColor,
              border: Border.all(color: _liveTheme.accentColor.withOpacity(0.4), width: 1.5),
            ),
            child: Center(
              child: Text(
                'TD',
                style: TextStyle(color: _liveTheme.primaryTextColor, fontWeight: FontWeight.bold, fontSize: 15),
              ),
            ),
          ),
          SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      'Tyler Durden',
                      style: TextStyle(color: _liveTheme.primaryTextColor, fontWeight: FontWeight.w600, fontSize: 14.5),
                    ),
                    Text(
                      '10:42 PM',
                      style: TextStyle(color: _liveTheme.accentColor, fontSize: 11, fontWeight: FontWeight.w500),
                    ),
                  ],
                ),
                SizedBox(height: 3),
                Row(
                  children: [
                    Icon(Icons.done_all_rounded, color: _liveTheme.accentColor, size: 15),
                    SizedBox(width: 4),
                    Expanded(
                      child: Text(
                        'The things you own end up owning you.',
                        style: TextStyle(color: _liveTheme.secondaryTextColor, fontSize: 12.5),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    SizedBox(width: 6),
                    CircleAvatar(
                      radius: 9,
                      backgroundColor: _liveTheme.accentColor,
                      child: Text('7', style: TextStyle(color: _liveTheme.onAccentColor, fontSize: 10, fontWeight: FontWeight.bold)),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // --- Bottom Bar: One UI Style ---
  Widget _buildOneUiBottomBar() {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
      color: _liveTheme.surfaceColor,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 5),
            decoration: BoxDecoration(
              color: _liveTheme.accentColor.withOpacity(0.18),
              borderRadius: BorderRadius.circular(16),
            ),
            child: Row(
              children: [
                Icon(Icons.chat_bubble_rounded, color: _liveTheme.accentColor, size: 18),
                SizedBox(width: 6),
                Text('Chats', style: TextStyle(color: _liveTheme.accentColor, fontSize: 12, fontWeight: FontWeight.bold)),
              ],
            ),
          ),
          Icon(Icons.update_rounded, color: _liveTheme.secondaryTextColor, size: 20),
          Icon(Icons.checklist_rounded, color: _liveTheme.secondaryTextColor, size: 20),
          Icon(Icons.call_outlined, color: _liveTheme.secondaryTextColor, size: 20),
        ],
      ),
    );
  }

  // --- Bottom Bar: iOS Style ---
  Widget _buildIosBottomBar() {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 20),
      decoration: BoxDecoration(
        color: _liveTheme.surfaceColor,
        border: Border(top: BorderSide(color: _liveTheme.cardColor, width: 0.8)),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          _buildIosTabItem(Icons.chat_bubble_rounded, 'Chats', isSelected: true, badge: '7'),
          _buildIosTabItem(Icons.circle_outlined, 'Updates'),
          _buildIosTabItem(Icons.checklist_rounded, 'Tasks'),
          _buildIosTabItem(Icons.phone_rounded, 'Calls'),
          _buildIosTabItem(Icons.settings_rounded, 'Settings'),
        ],
      ),
    );
  }

  // --- Bottom Bar: WhatsApp UI Stock ---
  Widget _buildStockBottomBar() {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
      decoration: BoxDecoration(
        color: _liveTheme.backgroundColor,
        border: Border(top: BorderSide(color: _liveTheme.cardColor, width: 0.8)),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          _buildStockTabItem(Icons.chat_bubble_rounded, 'Chats', isSelected: true, badge: '7'),
          _buildStockTabItem(Icons.update_rounded, 'Updates'),
          _buildStockTabItem(Icons.groups_rounded, 'Communities'),
          _buildStockTabItem(Icons.call_rounded, 'Calls'),
        ],
      ),
    );
  }

  // --- Bottom Bar: Bubbles Style ---
  Widget _buildBubblesBottomBar() {
    return Container(
      margin: const EdgeInsets.fromLTRB(12, 4, 12, 8),
      padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 12),
      decoration: BoxDecoration(
        color: _liveTheme.surfaceColor,
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: _liveTheme.cardColor, width: 1),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
            decoration: BoxDecoration(
              color: _liveTheme.accentColor,
              borderRadius: BorderRadius.circular(16),
            ),
            child: Row(
              children: [
                Icon(Icons.chat_bubble_rounded, color: _liveTheme.onAccentColor, size: 16),
                SizedBox(width: 4),
                Text('Chats', style: TextStyle(color: _liveTheme.onAccentColor, fontSize: 11, fontWeight: FontWeight.bold)),
              ],
            ),
          ),
          Icon(Icons.update_rounded, color: _liveTheme.secondaryTextColor, size: 18),
          Icon(Icons.checklist_rounded, color: _liveTheme.secondaryTextColor, size: 18),
          Icon(Icons.call_rounded, color: _liveTheme.secondaryTextColor, size: 18),
        ],
      ),
    );
  }

  // --- Bottom Bar: Old UI Style ---
  Widget _buildOldUiBottomBar() {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 6),
      color: _liveTheme.surfaceColor,
      child: Center(
        child: Text(
          'Classic Full-Screen Tab View (Old UI)',
          style: TextStyle(color: _liveTheme.secondaryTextColor, fontSize: 10.5),
        ),
      ),
    );
  }

  // --- Bottom Bar: Basic Style ---
  Widget _buildBasicBottomBar() {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 8),
      color: _liveTheme.surfaceColor,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          Icon(Icons.chat_bubble_rounded, color: _liveTheme.accentColor, size: 20),
          Icon(Icons.update_rounded, color: _liveTheme.secondaryTextColor, size: 20),
          Icon(Icons.checklist_rounded, color: _liveTheme.secondaryTextColor, size: 20),
          Icon(Icons.call_rounded, color: _liveTheme.secondaryTextColor, size: 20),
        ],
      ),
    );
  }

  // --- Helpers ---
  Widget _buildPillTab(String label, {bool isSelected = false, String? count}) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: isSelected ? _liveTheme.accentColor : _liveTheme.cardColor,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            label,
            style: TextStyle(
              color: isSelected ? _liveTheme.onAccentColor : _liveTheme.secondaryTextColor,
              fontSize: 11,
              fontWeight: FontWeight.bold,
            ),
          ),
          if (count != null) ...[
            SizedBox(width: 4),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
              decoration: BoxDecoration(
                color: isSelected ? _liveTheme.onAccentColor : _liveTheme.accentColor,
                shape: BoxShape.circle,
              ),
              child: Text(
                count,
                style: TextStyle(
                  color: isSelected ? _liveTheme.primaryTextColor : _liveTheme.onAccentColor,
                  fontSize: 9,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildBubbleTabItem(String label, {bool isSelected = false, String? count}) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 5),
        decoration: BoxDecoration(
          color: isSelected ? _liveTheme.accentColor.withOpacity(0.2) : Colors.transparent,
          borderRadius: BorderRadius.circular(14),
          border: isSelected ? Border.all(color: _liveTheme.accentColor, width: 1.2) : null,
        ),
        child: Center(
          child: Text(
            count != null ? '$label ($count)' : label,
            style: TextStyle(
              color: isSelected ? _liveTheme.accentColor : _liveTheme.secondaryTextColor,
              fontSize: 11,
              fontWeight: isSelected ? FontWeight.bold : FontWeight.w500,
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildIosTabItem(IconData icon, String label, {bool isSelected = false, String? badge}) {
    final activeColor = _liveTheme.accentColor;
    final color = isSelected ? activeColor : _liveTheme.secondaryTextColor;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Stack(
          clipBehavior: Clip.none,
          children: [
            Icon(icon, color: color, size: 18),
            if (badge != null)
              Positioned(
                top: -3,
                right: -6,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
                  decoration: BoxDecoration(
                    color: _liveTheme.dangerColor,
                    shape: BoxShape.circle,
                  ),
                  child: Text(badge, style: TextStyle(color: _liveTheme.primaryTextColor, fontSize: 8, fontWeight: FontWeight.bold)),
                ),
              ),
          ],
        ),
        SizedBox(height: 2),
        Text(label, style: TextStyle(color: color, fontSize: 9.5, fontWeight: FontWeight.w600)),
      ],
    );
  }

  Widget _buildStockTabItem(IconData icon, String label, {bool isSelected = false, String? badge}) {
    final color = isSelected ? _liveTheme.accentColor : _liveTheme.secondaryTextColor;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Stack(
          clipBehavior: Clip.none,
          children: [
            Icon(icon, color: color, size: 19),
            if (badge != null)
              Positioned(
                top: -3,
                right: -7,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
                  decoration: BoxDecoration(
                    color: _liveTheme.accentColor,
                    shape: BoxShape.circle,
                  ),
                  child: Text(badge, style: TextStyle(color: _liveTheme.onAccentColor, fontSize: 8, fontWeight: FontWeight.bold)),
                ),
              ),
          ],
        ),
        SizedBox(height: 3),
        Text(label, style: TextStyle(color: color, fontSize: 10, fontWeight: FontWeight.w600)),
      ],
    );
  }
}

/// Side Dropdown Popup Menu anchored at the top-right, matching Image 5.
class GbHeaderOverflowMenu extends StatelessWidget {
  dynamic get _liveTheme => locator<ThemeController>().globalTheme;

  final VoidCallback? onGbSettings;
  final VoidCallback? onMessageScheduler;
  final VoidCallback? onAutoReply;
  final VoidCallback? onRestart;
  final VoidCallback? onMessageNumber;
  final VoidCallback? onMassMessage;
  final VoidCallback? onNewGroup;
  final VoidCallback? onNewCommunity;
  final VoidCallback? onBroadcastLists;
  final VoidCallback? onLinkedDevices;
  final VoidCallback? onStarred;
  final VoidCallback? onPayments;
  final VoidCallback? onReadAll;
  final VoidCallback? onSettings;

  const GbHeaderOverflowMenu({
    super.key,
    this.onGbSettings,
    this.onMessageScheduler,
    this.onAutoReply,
    this.onRestart,
    this.onMessageNumber,
    this.onMassMessage,
    this.onNewGroup,
    this.onNewCommunity,
    this.onBroadcastLists,
    this.onLinkedDevices,
    this.onStarred,
    this.onPayments,
    this.onReadAll,
    this.onSettings,
  });

  static void show(
    BuildContext context, {
    VoidCallback? onGbSettings,
    VoidCallback? onMessageScheduler,
    VoidCallback? onAutoReply,
    VoidCallback? onRestart,
    VoidCallback? onMessageNumber,
    VoidCallback? onMassMessage,
    VoidCallback? onNewGroup,
    VoidCallback? onNewCommunity,
    VoidCallback? onBroadcastLists,
    VoidCallback? onLinkedDevices,
    VoidCallback? onStarred,
    VoidCallback? onPayments,
    VoidCallback? onReadAll,
    VoidCallback? onSettings,
  }) {
    showGeneralDialog(
      context: context,
      barrierDismissible: true,
      barrierLabel: 'Dismiss',
      barrierColor: context.colors.shadow.withValues(alpha: 0.38),
      transitionDuration: const Duration(milliseconds: 180),
      pageBuilder: (ctx, anim1, anim2) {
        return Stack(
          children: [
            Positioned(
              top: MediaQuery.of(ctx).padding.top + 42,
              right: 12,
              child: Material(
                color: Colors.transparent,
                child: GbHeaderOverflowMenu(
                  onGbSettings: () {
                    Navigator.of(ctx).pop();
                    onGbSettings?.call();
                  },
                  onMessageScheduler: () {
                    Navigator.of(ctx).pop();
                    onMessageScheduler?.call();
                  },
                  onAutoReply: () {
                    Navigator.of(ctx).pop();
                    onAutoReply?.call();
                  },
                  onRestart: () {
                    Navigator.of(ctx).pop();
                    onRestart?.call();
                  },
                  onMessageNumber: () {
                    Navigator.of(ctx).pop();
                    onMessageNumber?.call();
                  },
                  onMassMessage: () {
                    Navigator.of(ctx).pop();
                    onMassMessage?.call();
                  },
                  onNewGroup: () {
                    Navigator.of(ctx).pop();
                    onNewGroup?.call();
                  },
                  onNewCommunity: () {
                    Navigator.of(ctx).pop();
                    onNewCommunity?.call();
                  },
                  onBroadcastLists: () {
                    Navigator.of(ctx).pop();
                    onBroadcastLists?.call();
                  },
                  onLinkedDevices: () {
                    Navigator.of(ctx).pop();
                    onLinkedDevices?.call();
                  },
                  onStarred: () {
                    Navigator.of(ctx).pop();
                    onStarred?.call();
                  },
                  onPayments: () {
                    Navigator.of(ctx).pop();
                    onPayments?.call();
                  },
                  onReadAll: () {
                    Navigator.of(ctx).pop();
                    onReadAll?.call();
                  },
                  onSettings: () {
                    Navigator.of(ctx).pop();
                    onSettings?.call();
                  },
                ),
              ),
            ),
          ],
        );
      },
      transitionBuilder: (ctx, anim, secondaryAnim, child) {
        return FadeTransition(
          opacity: anim,
          child: ScaleTransition(
            scale: Tween<double>(begin: 0.92, end: 1.0).animate(
              CurvedAnimation(parent: anim, curve: Curves.easeOutCubic),
            ),
            alignment: Alignment.topRight,
            child: child,
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 250,
      constraints: BoxConstraints(
        maxHeight: MediaQuery.of(context).size.height * 0.8,
      ),
      decoration: BoxDecoration(
        color: _liveTheme.surfaceColor,
        borderRadius: BorderRadius.zero,
        border: Border.all(color: _liveTheme.cardColor, width: 1),
        boxShadow: [
          BoxShadow(
            color: _liveTheme.backgroundColor.withValues(alpha: 0.55),
            blurRadius: 24,
            offset: const Offset(-2, 10),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.zero,
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(vertical: 8),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              _item(Icons.tune_rounded, 'Header & Navigation', onGbSettings),
              _item(Icons.schedule_send_outlined, 'Message Scheduler', onMessageScheduler),
              _item(Icons.reply_all_outlined, 'Auto Reply', onAutoReply),
              _item(Icons.restart_alt_rounded, 'Restart Chaty', onRestart),
              _item(Icons.edit_note_rounded, 'Message a number', onMessageNumber),
              _item(Icons.forward_to_inbox_rounded, 'Mass Message Sender', onMassMessage),
              _item(Icons.group_add_outlined, 'New group', onNewGroup),
              _item(Icons.groups_outlined, 'New community', onNewCommunity),
              _item(Icons.devices_rounded, 'Linked devices', onLinkedDevices),
              _item(Icons.star_outline_rounded, 'Starred', onStarred),
              _item(Icons.done_all_rounded, 'Read all', onReadAll),
              _item(Icons.settings_outlined, 'Settings', onSettings),
            ],
          ),
        ),
      ),
    );
  }

  Widget _item(IconData icon, String label, VoidCallback? onTap) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 11),
        child: Row(
          children: [
            Icon(icon, color: _liveTheme.secondaryTextColor, size: 21),
            const SizedBox(width: 16),
            Expanded(
              child: Text(
                label,
                style: TextStyle(
                  color: _liveTheme.primaryTextColor,
                  fontSize: 14.5,
                  fontWeight: FontWeight.w500,
                  letterSpacing: -0.1,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
