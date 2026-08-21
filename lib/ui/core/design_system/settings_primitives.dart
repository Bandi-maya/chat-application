import 'package:flutter/material.dart';
import 'tokens/app_tokens.dart';
import 'components/app_components.dart';
import 'components/chaty_kit.dart';

/// Comprehensive Chaty Settings UI Primitives & Design System Tokens

class ChatySettingsPage extends StatelessWidget {
  final String title;
  final String? subtitle;
  final Widget? trailingHeaderWidget;
  final List<Widget> children;
  final Widget? bottomNavigationBar;
  final FloatingActionButton? floatingActionButton;

  const ChatySettingsPage({
    super.key,
    required this.title,
    this.subtitle,
    this.trailingHeaderWidget,
    required this.children,
    this.bottomNavigationBar,
    this.floatingActionButton,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final canPop = Navigator.of(context).canPop();
    return ChatyScaffold(
      appBar: ChatyAppBar(
        automaticallyImplyLeading: canPop,
        leading: canPop ? const ChatyBackButton() : const SizedBox.shrink(),
        titleWidget: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              title,
              style: TextStyle(
                fontWeight: FontWeight.w600,
                fontSize: 16.5,
                color: theme.colorScheme.onSurface,
              ),
            ),
            if (subtitle != null)
              Text(
                subtitle!,
                style: ChatyTypography.caption(
                  theme.colorScheme.onSurface.withValues(alpha: 0.6),
                ),
              ),
          ],
        ),
        actions: trailingHeaderWidget != null
            ? [
                trailingHeaderWidget!,
                const SizedBox(width: ChatySpacing.sm),
              ]
            : null,
      ),
      body: SafeArea(
        child: ListView(
          physics: const BouncingScrollPhysics(),
          padding: const EdgeInsets.symmetric(
            horizontal: ChatySpacing.base,
            vertical: ChatySpacing.md,
          ),
          children: children,
        ),
      ),
      bottomNavigationBar: bottomNavigationBar,
      floatingActionButton: floatingActionButton,
    );
  }
}


class ChatySettingsSection extends StatelessWidget {
  final String? title;
  final String? description;
  final List<Widget> children;

  const ChatySettingsSection({
    super.key,
    this.title,
    this.description,
    required this.children,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (title != null) ...[
          Padding(
            padding: const EdgeInsets.only(left: 12, bottom: 6, top: 12),
            child: Text(
              title!.toUpperCase(),
              style: TextStyle(
                fontSize: 11,
                fontWeight: FontWeight.w700,
                letterSpacing: 1.1,
                color: theme.colorScheme.primary,
              ),
            ),
          ),
        ],
        ChatySettingsCard(children: children),
        if (description != null) ...[
          Padding(
            padding: const EdgeInsets.only(
              left: 14,
              right: 14,
              top: 6,
              bottom: 12,
            ),
            child: Text(
              description!,
              style: TextStyle(
                fontSize: 12,
                color: theme.textTheme.bodyMedium?.color?.withValues(
                  alpha: 0.65,
                ),
                height: 1.35,
              ),
            ),
          ),
        ] else ...[
          const SizedBox(height: 12),
        ],
      ],
    );
  }
}

class ChatySettingsCard extends StatelessWidget {
  final List<Widget> children;
  final Color? backgroundColor;
  final EdgeInsetsGeometry? padding;

  const ChatySettingsCard({
    super.key,
    required this.children,
    this.backgroundColor,
    this.padding,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      elevation: 0,
      clipBehavior: Clip.antiAlias,
      color: backgroundColor ?? theme.cardColor,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(
          color: theme.dividerColor.withValues(alpha: 0.12),
          width: 1,
        ),
      ),
      child: Padding(
        padding: padding ?? EdgeInsets.zero,
        child: Column(children: _buildSeparatedChildren(children, theme)),
      ),
    );
  }

  List<Widget> _buildSeparatedChildren(List<Widget> list, ThemeData theme) {
    if (list.isEmpty) return [];
    final List<Widget> result = [];
    for (int i = 0; i < list.length; i++) {
      result.add(list[i]);
      if (i < list.length - 1 &&
          list[i] is! ChatySectionDivider &&
          list[i + 1] is! ChatySectionDivider) {
        result.add(
          Divider(
            height: 1,
            thickness: 0.7,
            indent: 52,
            endIndent: 12,
            color: theme.dividerColor.withValues(alpha: 0.15),
          ),
        );
      }
    }
    return result;
  }
}

class ChatySettingsTile extends StatelessWidget {
  final Widget? leading;
  final IconData? icon;
  final Color? iconColor;
  final String title;
  final String? subtitle;
  final Widget? trailing;
  final String? badgeText;
  final Color? badgeColor;
  final VoidCallback? onTap;
  final bool enabled;

  const ChatySettingsTile({
    super.key,
    this.leading,
    this.icon,
    this.iconColor,
    required this.title,
    this.subtitle,
    this.trailing,
    this.badgeText,
    this.badgeColor,
    this.onTap,
    this.enabled = true,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final primaryColor = iconColor ?? theme.colorScheme.primary;

    Widget? leadingWidget = leading;
    if (leadingWidget == null && icon != null) {
      leadingWidget = Container(
        width: 34,
        height: 34,
        decoration: BoxDecoration(
          color: primaryColor.withValues(alpha: 0.12),
          borderRadius: BorderRadius.circular(10),
        ),
        child: Icon(icon, color: primaryColor, size: 19),
      );
    }

    return ListTile(
      enabled: enabled,
      onTap: onTap,
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 2),
      leading: leadingWidget,
      title: Row(
        children: [
          Expanded(
            child: Text(
              title,
              style: TextStyle(
                fontSize: 14.5,
                fontWeight: FontWeight.w600,
                color: enabled
                    ? theme.textTheme.bodyLarge?.color
                    : theme.disabledColor,
              ),
            ),
          ),
          if (badgeText != null) ...[
            const SizedBox(width: 6),
            ChatyBadge(text: badgeText!, color: badgeColor),
          ],
        ],
      ),
      subtitle: subtitle != null
          ? Text(
              subtitle!,
              style: TextStyle(
                fontSize: 12.5,
                color: theme.textTheme.bodyMedium?.color?.withValues(
                  alpha: 0.7,
                ),
              ),
            )
          : null,
      trailing:
          trailing ??
          (onTap != null
              ? Icon(
                  Icons.chevron_right_rounded,
                  color: theme.hintColor,
                  size: 20,
                )
              : null),
    );
  }
}

class ChatySwitchTile extends StatelessWidget {
  final IconData? icon;
  final Color? iconColor;
  final String title;
  final String? subtitle;
  final bool value;
  final ValueChanged<bool>? onChanged;
  final bool enabled;

  const ChatySwitchTile({
    super.key,
    this.icon,
    this.iconColor,
    required this.title,
    this.subtitle,
    required this.value,
    required this.onChanged,
    this.enabled = true,
  });

  @override
  Widget build(BuildContext context) {
    return ChatySettingsTile(
      icon: icon,
      iconColor: iconColor,
      title: title,
      subtitle: subtitle,
      enabled: enabled,
      onTap: enabled && onChanged != null ? () => onChanged!(!value) : null,
      trailing: ChatySwitch(
        value: value,
        onChanged: enabled ? onChanged : null,
      ),
    );
  }
}

class ChatyColorTile extends StatelessWidget {
  final IconData? icon;
  final String title;
  final String? subtitle;
  final Color color;
  final VoidCallback onTap;

  const ChatyColorTile({
    super.key,
    this.icon,
    required this.title,
    this.subtitle,
    required this.color,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return ChatySettingsTile(
      icon: icon,
      iconColor: color,
      title: title,
      subtitle: subtitle,
      onTap: onTap,
      trailing: Container(
        width: 32,
        height: 32,
        decoration: BoxDecoration(
          color: color,
          shape: BoxShape.circle,
          border: Border.all(color: Colors.white, width: 2),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.2),
              blurRadius: 4,
              offset: const Offset(0, 2),
            ),
          ],
        ),
      ),
    );
  }
}

class ChatySliderTile extends StatelessWidget {
  final IconData? icon;
  final String title;
  final String? subtitle;
  final double value;
  final double min;
  final double max;
  final int? divisions;
  final String Function(double)? valueFormatter;
  final ValueChanged<double> onChanged;

  const ChatySliderTile({
    super.key,
    this.icon,
    required this.title,
    this.subtitle,
    required this.value,
    this.min = 0.0,
    this.max = 1.0,
    this.divisions,
    this.valueFormatter,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final displayVal = valueFormatter != null
        ? valueFormatter!(value)
        : value.toStringAsFixed(1);

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              if (icon != null) ...[
                Container(
                  width: 32,
                  height: 32,
                  decoration: BoxDecoration(
                    color: theme.colorScheme.primary.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Icon(icon, color: theme.colorScheme.primary, size: 18),
                ),
                const SizedBox(width: 12),
              ],
              Expanded(
                child: Text(
                  title,
                  style: const TextStyle(
                    fontSize: 14.5,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                decoration: BoxDecoration(
                  color: theme.colorScheme.primary.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  displayVal,
                  style: TextStyle(
                    fontSize: 12.5,
                    fontWeight: FontWeight.bold,
                    color: theme.colorScheme.primary,
                  ),
                ),
              ),
            ],
          ),
          if (subtitle != null) ...[
            const SizedBox(height: 4),
            Text(
              subtitle!,
              style: TextStyle(
                fontSize: 12,
                color: theme.textTheme.bodyMedium?.color?.withValues(
                  alpha: 0.7,
                ),
              ),
            ),
          ],
          const SizedBox(height: 4),
          ChatySlider(
            value: value,
            min: min,
            max: max,
            divisions: divisions,
            onChanged: onChanged,
          ),
        ],
      ),
    );
  }
}

class ChatyRadioTile<T> extends StatelessWidget {
  final String title;
  final String? subtitle;
  final T value;
  final T groupValue;
  final ValueChanged<T?> onChanged;

  const ChatyRadioTile({
    super.key,
    required this.title,
    this.subtitle,
    required this.value,
    required this.groupValue,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isSelected = value == groupValue;
    return ListTile(
      onTap: () => onChanged(value),
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 0),
      title: Text(
        title,
        style: TextStyle(
          fontSize: 14.5,
          fontWeight: isSelected ? FontWeight.bold : FontWeight.w500,
          color: isSelected
              ? theme.colorScheme.primary
              : theme.textTheme.bodyLarge?.color,
        ),
      ),
      subtitle: subtitle != null
          ? Text(
              subtitle!,
              style: TextStyle(
                fontSize: 12,
                color: theme.textTheme.bodyMedium?.color?.withValues(
                  alpha: 0.65,
                ),
              ),
            )
          : null,
      trailing: Radio<T>(
        value: value,
        // ignore: deprecated_member_use
        groupValue: groupValue,
        // ignore: deprecated_member_use
        onChanged: onChanged,
        activeColor: theme.colorScheme.primary,
      ),
    );
  }
}

class ChatyChoiceTile<T> extends StatelessWidget {
  final String title;
  final String? subtitle;
  final List<T> options;
  final T selectedOption;
  final String Function(T) optionLabel;
  final ValueChanged<T> onSelected;

  const ChatyChoiceTile({
    super.key,
    required this.title,
    this.subtitle,
    required this.options,
    required this.selectedOption,
    required this.optionLabel,
    required this.onSelected,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w600),
          ),
          if (subtitle != null) ...[
            const SizedBox(height: 2),
            Text(
              subtitle!,
              style: TextStyle(
                fontSize: 12,
                color: theme.textTheme.bodyMedium?.color?.withValues(
                  alpha: 0.7,
                ),
              ),
            ),
          ],
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: options.map((opt) {
              final isSel = opt == selectedOption;
              return ChoiceChip(
                label: Text(optionLabel(opt)),
                selected: isSel,
                onSelected: (selected) {
                  if (selected) onSelected(opt);
                },
                selectedColor: theme.colorScheme.primary.withValues(alpha: 0.2),
                backgroundColor: theme.cardColor,
                labelStyle: TextStyle(
                  color: isSel
                      ? theme.colorScheme.primary
                      : theme.textTheme.bodyMedium?.color,
                  fontWeight: isSel ? FontWeight.bold : FontWeight.w500,
                  fontSize: 12.5,
                ),
              );
            }).toList(),
          ),
        ],
      ),
    );
  }
}

class ChatyExpandableTile extends StatefulWidget {
  final IconData? icon;
  final String title;
  final String? subtitle;
  final List<Widget> children;

  const ChatyExpandableTile({
    super.key,
    this.icon,
    required this.title,
    this.subtitle,
    required this.children,
  });

  @override
  State<ChatyExpandableTile> createState() => _ChatyExpandableTileState();
}

class _ChatyExpandableTileState extends State<ChatyExpandableTile> {
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        ChatySettingsTile(
          icon: widget.icon,
          title: widget.title,
          subtitle: widget.subtitle,
          onTap: () => setState(() => _expanded = !_expanded),
          trailing: Icon(
            _expanded ? Icons.expand_less_rounded : Icons.expand_more_rounded,
            color: Theme.of(context).hintColor,
          ),
        ),
        if (_expanded)
          Padding(
            padding: const EdgeInsets.only(left: 16, bottom: 8),
            child: Column(children: widget.children),
          ),
      ],
    );
  }
}

class ChatyInfoTile extends StatelessWidget {
  final String message;
  final IconData icon;
  final Color? color;

  const ChatyInfoTile({
    super.key,
    required this.message,
    this.icon = Icons.info_outline_rounded,
    this.color,
  });

  @override
  Widget build(BuildContext context) {
    final effectiveColor = color ?? Theme.of(context).colorScheme.primary;
    return Container(
      margin: const EdgeInsets.all(12),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: effectiveColor.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: effectiveColor.withValues(alpha: 0.2)),
      ),
      child: Row(
        children: [
          Icon(icon, color: effectiveColor, size: 20),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              message,
              style: TextStyle(
                fontSize: 12.5,
                color: effectiveColor,
                height: 1.35,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class ChatyDangerTile extends StatelessWidget {
  final String title;
  final String? subtitle;
  final VoidCallback onTap;

  const ChatyDangerTile({
    super.key,
    required this.title,
    this.subtitle,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return ChatySettingsTile(
      icon: Icons.delete_outline_rounded,
      iconColor: Colors.redAccent,
      title: title,
      subtitle: subtitle,
      onTap: onTap,
      trailing: const Icon(
        Icons.arrow_forward_ios_rounded,
        color: Colors.redAccent,
        size: 16,
      ),
    );
  }
}

class ChatyPreviewCard extends StatelessWidget {
  final String title;
  final Widget child;

  const ChatyPreviewCard({super.key, required this.title, required this.child});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(left: 4, bottom: 6),
          child: Text(
            title.toUpperCase(),
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.bold,
              letterSpacing: 1.1,
              color: theme.colorScheme.primary,
            ),
          ),
        ),
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: theme.cardColor,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: theme.dividerColor.withValues(alpha: 0.15),
            ),
          ),
          child: child,
        ),
        const SizedBox(height: 14),
      ],
    );
  }
}

class ChatySectionDivider extends StatelessWidget {
  const ChatySectionDivider({super.key});

  @override
  Widget build(BuildContext context) {
    return Divider(
      height: 1,
      thickness: 1,
      color: Theme.of(context).dividerColor.withValues(alpha: 0.15),
    );
  }
}

class ChatyBadge extends StatelessWidget {
  final String text;
  final Color? color;

  const ChatyBadge({super.key, required this.text, this.color});

  @override
  Widget build(BuildContext context) {
    final bg = color ?? Theme.of(context).colorScheme.primary;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
      decoration: BoxDecoration(
        color: bg.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        text,
        style: TextStyle(
          color: bg,
          fontSize: 10.5,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }
}

class ChatySwitch extends StatelessWidget {
  final bool value;
  final ValueChanged<bool>? onChanged;

  const ChatySwitch({super.key, required this.value, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Switch(
      value: value,
      onChanged: onChanged,
      activeThumbColor: theme.colorScheme.primary,
      activeTrackColor: theme.colorScheme.primary.withValues(alpha: 0.3),
    );
  }
}

class ChatySlider extends StatelessWidget {
  final double value;
  final double min;
  final double max;
  final int? divisions;
  final ValueChanged<double> onChanged;

  const ChatySlider({
    super.key,
    required this.value,
    this.min = 0.0,
    this.max = 1.0,
    this.divisions,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SliderTheme(
      data: SliderThemeData(
        activeTrackColor: theme.colorScheme.primary,
        inactiveTrackColor: theme.colorScheme.primary.withValues(alpha: 0.18),
        thumbColor: theme.colorScheme.primary,
        overlayColor: theme.colorScheme.primary.withValues(alpha: 0.12),
        trackHeight: 4,
      ),
      child: Slider(
        value: value.clamp(min, max),
        min: min,
        max: max,
        divisions: divisions,
        onChanged: onChanged,
      ),
    );
  }
}

class ChatyAvatar extends StatelessWidget {
  final String initials;
  final Color color;
  final double size;
  final String shape; // 'circle', 'squircle', 'roundedSquare'

  const ChatyAvatar({
    super.key,
    required this.initials,
    required this.color,
    this.size = 42,
    this.shape = 'circle',
  });

  @override
  Widget build(BuildContext context) {
    return ChatyAvatarCore(
      initials: initials,
      color: color,
      size: size,
      shape: shape,
    );
  }
}
