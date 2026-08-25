import 'package:flutter/material.dart';
import '../../theme/app_theme.dart';

/// Modal dialog with radio options and optional custom preview builders.
/// Replaces ad-hoc configuration tabs and generic picker bottom sheets.
class ChatySingleChoiceModal<T> extends StatelessWidget {
  final String title;
  final String? description;
  final T value;
  final List<T> options;
  final String Function(T) labelBuilder;
  final String Function(T)? descriptionBuilder;
  final Widget Function(BuildContext, T)? previewBuilder;

  const ChatySingleChoiceModal({
    super.key,
    required this.title,
    this.description,
    required this.value,
    required this.options,
    required this.labelBuilder,
    this.descriptionBuilder,
    this.previewBuilder,
  });

  static Future<T?> show<T>({
    required BuildContext context,
    required String title,
    String? description,
    required T value,
    required List<T> options,
    required String Function(T) labelBuilder,
    String Function(T)? descriptionBuilder,
    Widget Function(BuildContext, T)? previewBuilder,
  }) async {
    return showDialog<T>(
      context: context,
      barrierDismissible: true,
      builder: (ctx) => ChatySingleChoiceModal<T>(
        title: title,
        description: description,
        value: value,
        options: options,
        labelBuilder: labelBuilder,
        descriptionBuilder: descriptionBuilder,
        previewBuilder: previewBuilder,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final theme = Theme.of(context);

    return Dialog(
      backgroundColor: colors.surface,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(24),
        side: BorderSide(color: colors.border, width: 1),
      ),
      insetPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 400, maxHeight: 580),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Header
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 20, 20, 12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w800,
                      color: colors.foreground,
                    ),
                  ),
                  if (description != null) ...[
                    const SizedBox(height: 4),
                    Text(
                      description!,
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: colors.foregroundSecondary,
                      ),
                    ),
                  ],
                ],
              ),
            ),
            Divider(height: 1, color: colors.border),

            // Radio options list
            Flexible(
              child: ListView.separated(
                shrinkWrap: true,
                padding: const EdgeInsets.symmetric(vertical: 8),
                itemCount: options.length,
                separatorBuilder: (_, __) => Divider(
                  height: 1,
                  indent: 16,
                  endIndent: 16,
                  color: colors.borderSubtle,
                ),
                itemBuilder: (context, index) {
                  final option = options[index];
                  final isSelected = option == value;
                  final label = labelBuilder(option);
                  final desc = descriptionBuilder?.call(option);

                  return InkWell(
                    onTap: () => Navigator.of(context).pop(option),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 12,
                      ),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.center,
                        children: [
                          Radio<T>(
                            value: option,
                            groupValue: value,
                            onChanged: (val) {
                              if (val != null) Navigator.of(context).pop(val);
                            },
                            activeColor: colors.primary,
                            materialTapTargetSize:
                                MaterialTapTargetSize.shrinkWrap,
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  label,
                                  style: TextStyle(
                                    fontSize: 14.5,
                                    fontWeight: isSelected
                                        ? FontWeight.w700
                                        : FontWeight.w500,
                                    color: isSelected
                                        ? colors.primary
                                        : colors.foreground,
                                  ),
                                ),
                                if (desc != null && desc.isNotEmpty) ...[
                                  const SizedBox(height: 2),
                                  Text(
                                    desc,
                                    style: TextStyle(
                                      fontSize: 12,
                                      color: colors.foregroundSecondary,
                                    ),
                                  ),
                                ],
                                if (previewBuilder != null) ...[
                                  const SizedBox(height: 8),
                                  previewBuilder!(context, option),
                                ],
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  );
                },
              ),
            ),

            Divider(height: 1, color: colors.border),
            // Footer Action
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              child: Align(
                alignment: Alignment.centerRight,
                child: TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: Text(
                    'Cancel',
                    style: TextStyle(color: colors.foregroundSecondary),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
