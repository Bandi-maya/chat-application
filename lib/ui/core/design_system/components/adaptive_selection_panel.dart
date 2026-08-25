import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';
import 'app_components.dart';

/// Reusable adaptive option definition for selection panels and settings pickers.
class SelectionOptionItem<T> {
  final T value;
  final String title;
  final String? subtitle;
  final Widget? preview;
  final IconData? leadingIcon;
  final String? badgeText;

  const SelectionOptionItem({
    required this.value,
    required this.title,
    this.subtitle,
    this.preview,
    this.leadingIcon,
    this.badgeText,
  });
}

/// Premium reusable selection panel for settings and configuration surfaces.
/// Replaces repetitive checkbox/tick rows with proper single-selection semantics,
/// smooth animated indicators, responsive positioning, and preview slots.
class AdaptiveSelectionPanel<T> extends StatefulWidget {
  final String title;
  final String? subtitle;
  final T selectedValue;
  final List<SelectionOptionItem<T>> options;
  final ValueChanged<T> onSelected;
  final Widget? headerPreview;
  final bool showApplyButton;
  final String applyButtonText;
  final bool isScrollable;

  const AdaptiveSelectionPanel({
    super.key,
    required this.title,
    this.subtitle,
    required this.selectedValue,
    required this.options,
    required this.onSelected,
    this.headerPreview,
    this.showApplyButton = false,
    this.applyButtonText = 'Apply',
    this.isScrollable = true,
  });

  /// Presents the selection panel adaptively: centered elevated dialog for tablets / large screens,
  /// or a refined modal sheet with spatial height on mobile.
  static Future<T?> show<T>({
    required BuildContext context,
    required String title,
    String? subtitle,
    required T selectedValue,
    required List<SelectionOptionItem<T>> options,
    Widget? headerPreview,
    bool showApplyButton = false,
  }) {
    final media = MediaQuery.of(context);
    final isTablet = media.size.width >= 600;

    if (isTablet) {
      return showDialog<T>(
        context: context,
        barrierDismissible: true,
        builder: (dialogCtx) => Dialog(
          backgroundColor: Colors.transparent,
          insetPadding: const EdgeInsets.symmetric(
            horizontal: 40,
            vertical: 24,
          ),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 520, maxHeight: 680),
            child: AdaptiveSelectionPanel<T>(
              title: title,
              subtitle: subtitle,
              selectedValue: selectedValue,
              options: options,
              headerPreview: headerPreview,
              showApplyButton: showApplyButton,
              onSelected: (val) => Navigator.of(dialogCtx).pop(val),
            ),
          ),
        ),
      );
    }

    return showModalBottomSheet<T>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (sheetCtx) => SafeArea(
        child: Container(
          constraints: BoxConstraints(maxHeight: media.size.height * 0.85),
          margin: const EdgeInsets.fromLTRB(12, 0, 12, 12),
          child: AdaptiveSelectionPanel<T>(
            title: title,
            subtitle: subtitle,
            selectedValue: selectedValue,
            options: options,
            headerPreview: headerPreview,
            showApplyButton: showApplyButton,
            onSelected: (val) => Navigator.of(sheetCtx).pop(val),
          ),
        ),
      ),
    );
  }

  @override
  State<AdaptiveSelectionPanel<T>> createState() =>
      _AdaptiveSelectionPanelState<T>();
}

class _AdaptiveSelectionPanelState<T> extends State<AdaptiveSelectionPanel<T>> {
  late T _current;

  @override
  void initState() {
    super.initState();
    _current = widget.selectedValue;
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;

    return Container(
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: BorderRadius.circular(24),
        border: Border.all(
          color: colors.border.withValues(alpha: 0.6),
          width: 1.1,
        ),
        boxShadow: [
          BoxShadow(
            color: colors.shadow,
            blurRadius: 24,
            offset: const Offset(0, 8),
          ),
        ],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Header Drag Handle / Spacing
          Center(
            child: Container(
              margin: const EdgeInsets.only(top: 10, bottom: 6),
              width: 36,
              height: 4,
              decoration: BoxDecoration(
                color: colors.foregroundSecondary.withValues(alpha: 0.3),
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),

          // Title & Subtitle
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 6, 12, 10),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        widget.title,
                        style: TextStyle(
                          color: colors.foreground,
                          fontSize: 17,
                          fontWeight: FontWeight.w800,
                          letterSpacing: -0.2,
                        ),
                      ),
                      if (widget.subtitle != null) ...[
                        const SizedBox(height: 2),
                        Text(
                          widget.subtitle!,
                          style: TextStyle(
                            color: colors.foregroundSecondary,
                            fontSize: 12.5,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
                IconButton(
                  icon: Icon(
                    Icons.close_rounded,
                    color: colors.foregroundSecondary,
                    size: 20,
                  ),
                  onPressed: () => Navigator.of(context).pop(),
                ),
              ],
            ),
          ),

          // Optional Header Preview
          if (widget.headerPreview != null) ...[
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
              child: widget.headerPreview!,
            ),
            const SizedBox(height: 6),
          ],

          Divider(height: 1, color: colors.borderSubtle),

          // Options List
          Flexible(
            child: ListView.separated(
              shrinkWrap: true,
              physics: const BouncingScrollPhysics(),
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              itemCount: widget.options.length,
              separatorBuilder: (_, __) => const SizedBox(height: 8),
              itemBuilder: (context, idx) {
                final option = widget.options[idx];
                final isSelected = option.value == _current;

                return SelectionOptionTile<T>(
                  option: option,
                  isSelected: isSelected,
                  onTap: () {
                    setState(() => _current = option.value);
                    if (!widget.showApplyButton) {
                      widget.onSelected(option.value);
                    }
                  },
                );
              },
            ),
          ),

          // Optional Apply Footer Button
          if (widget.showApplyButton) ...[
            Divider(height: 1, color: colors.borderSubtle),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 10, 16, 12),
              child: ChatyPrimaryButton(
                text: widget.applyButtonText,
                onPressed: () => widget.onSelected(_current),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// Premium selection row with radio semantics and smooth state transition.
class SelectionOptionTile<T> extends StatelessWidget {
  final SelectionOptionItem<T> option;
  final bool isSelected;
  final VoidCallback onTap;

  const SelectionOptionTile({
    super.key,
    required this.option,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;

    return AnimatedContainer(
      duration: const Duration(milliseconds: 180),
      curve: Curves.easeInOut,
      decoration: BoxDecoration(
        color: isSelected
            ? colors.primary.withValues(alpha: 0.10)
            : colors.surfaceSecondary.withValues(alpha: 0.5),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isSelected
              ? colors.primary.withValues(alpha: 0.6)
              : colors.borderSubtle,
          width: isSelected ? 1.4 : 1.0,
        ),
      ),
      child: Material(
        color: Colors.transparent,
        borderRadius: BorderRadius.circular(16),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(16),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
            child: Row(
              children: [
                // Radio Indicator
                AnimatedContainer(
                  duration: const Duration(milliseconds: 180),
                  width: 22,
                  height: 22,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: isSelected ? colors.primary : Colors.transparent,
                    border: Border.all(
                      color: isSelected
                          ? colors.primary
                          : colors.foregroundSecondary.withValues(alpha: 0.5),
                      width: 2,
                    ),
                  ),
                  child: isSelected
                      ? Center(
                          child: Container(
                            width: 8,
                            height: 8,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: colors.onPrimary,
                            ),
                          ),
                        )
                      : null,
                ),
                const SizedBox(width: 14),

                // Leading Icon if provided
                if (option.leadingIcon != null) ...[
                  Icon(
                    option.leadingIcon,
                    size: 20,
                    color: isSelected
                        ? colors.primary
                        : colors.foregroundSecondary,
                  ),
                  const SizedBox(width: 12),
                ],

                // Title & Subtitle
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Expanded(
                            child: Text(
                              option.title,
                              style: TextStyle(
                                color: isSelected
                                    ? colors.primary
                                    : colors.foreground,
                                fontSize: 14.5,
                                fontWeight: isSelected
                                    ? FontWeight.w700
                                    : FontWeight.w500,
                              ),
                            ),
                          ),
                          if (option.badgeText != null) ...[
                            const SizedBox(width: 6),
                            Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 6,
                                vertical: 2,
                              ),
                              decoration: BoxDecoration(
                                color: colors.primary.withValues(alpha: 0.15),
                                borderRadius: BorderRadius.circular(6),
                              ),
                              child: Text(
                                option.badgeText!,
                                style: TextStyle(
                                  color: colors.primary,
                                  fontSize: 10,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ),
                          ],
                        ],
                      ),
                      if (option.subtitle != null &&
                          option.subtitle!.isNotEmpty) ...[
                        const SizedBox(height: 2),
                        Text(
                          option.subtitle!,
                          style: TextStyle(
                            color: colors.foregroundSecondary,
                            fontSize: 12,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),

                // Trailing Preview Widget if present
                if (option.preview != null) ...[
                  const SizedBox(width: 10),
                  option.preview!,
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}
