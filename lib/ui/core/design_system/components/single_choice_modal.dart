import 'package:flutter/material.dart';

import '../../theme/app_theme.dart';

/// A confirmation-first single-choice dialog.
///
/// Choosing a radio option updates only the local preview. The persisted value
/// is returned to the caller after the user explicitly activates Apply.
class ChatySingleChoiceModal<T> extends StatefulWidget {
  const ChatySingleChoiceModal({
    super.key,
    required this.title,
    this.description,
    required this.value,
    required this.options,
    required this.labelBuilder,
    this.descriptionBuilder,
    this.previewBuilder,
    this.applyLabel = 'Apply',
  });

  final String title;
  final String? description;
  final T value;
  final List<T> options;
  final String Function(T) labelBuilder;
  final String Function(T)? descriptionBuilder;
  final Widget Function(BuildContext, T)? previewBuilder;
  final String applyLabel;

  static Future<T?> show<T>({
    required BuildContext context,
    required String title,
    String? description,
    required T value,
    required List<T> options,
    required String Function(T) labelBuilder,
    String Function(T)? descriptionBuilder,
    Widget Function(BuildContext, T)? previewBuilder,
    String applyLabel = 'Apply',
  }) {
    return showDialog<T>(
      context: context,
      barrierDismissible: true,
      builder: (_) => ChatySingleChoiceModal<T>(
        title: title,
        description: description,
        value: value,
        options: options,
        labelBuilder: labelBuilder,
        descriptionBuilder: descriptionBuilder,
        previewBuilder: previewBuilder,
        applyLabel: applyLabel,
      ),
    );
  }

  @override
  State<ChatySingleChoiceModal<T>> createState() =>
      _ChatySingleChoiceModalState<T>();
}

class _ChatySingleChoiceModalState<T>
    extends State<ChatySingleChoiceModal<T>> {
  late T _draftValue;

  @override
  void initState() {
    super.initState();
    _draftValue = widget.value;
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final theme = Theme.of(context);
    final changed = _draftValue != widget.value;
    final selectedLabel = widget.labelBuilder(_draftValue);

    return Dialog(
      backgroundColor: colors.surface,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(24),
        side: BorderSide(color: colors.border),
      ),
      insetPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxWidth: 520,
          maxHeight: MediaQuery.sizeOf(context).height * 0.9,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 20, 20, 12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    widget.title,
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w800,
                      color: colors.foreground,
                    ),
                  ),
                  if (widget.description case final description?) ...[
                    const SizedBox(height: 4),
                    Text(
                      description,
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: colors.foregroundSecondary,
                      ),
                    ),
                  ],
                ],
              ),
            ),
            Divider(height: 1, color: colors.border),
            if (widget.previewBuilder != null)
              Semantics(
                liveRegion: true,
                label: 'Previewing $selectedLabel',
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Center(
                    child: widget.previewBuilder!(context, _draftValue),
                  ),
                ),
              ),
            Flexible(
              child: Semantics(
                container: true,
                explicitChildNodes: true,
                label: '${widget.title} options',
                child: RadioGroup<T>(
                  groupValue: _draftValue,
                  onChanged: (value) {
                    if (value != null) setState(() => _draftValue = value);
                  },
                  child: ListView.separated(
                    shrinkWrap: true,
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    itemCount: widget.options.length,
                    separatorBuilder: (_, __) => Divider(
                      height: 1,
                      indent: 16,
                      endIndent: 16,
                      color: colors.borderSubtle,
                    ),
                    itemBuilder: (context, index) {
                      final option = widget.options[index];
                      final selected = option == _draftValue;
                      final description = widget.descriptionBuilder?.call(
                        option,
                      );
                      return RadioListTile<T>(
                        value: option,
                        selected: selected,
                        title: Text(widget.labelBuilder(option)),
                        subtitle: description == null || description.isEmpty
                            ? null
                            : Text(description),
                        secondary: widget.previewBuilder == null
                            ? null
                            : ExcludeSemantics(
                                child: widget.previewBuilder!(context, option),
                              ),
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 12,
                        ),
                        visualDensity: VisualDensity.standard,
                      );
                    },
                  ),
                ),
              ),
            ),
            Divider(height: 1, color: colors.border),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  TextButton(
                    onPressed: () => Navigator.of(context).pop(),
                    child: const Text('Cancel'),
                  ),
                  const SizedBox(width: 8),
                  FilledButton(
                    onPressed: changed
                        ? () => Navigator.of(context).pop(_draftValue)
                        : null,
                    child: Text(widget.applyLabel),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
