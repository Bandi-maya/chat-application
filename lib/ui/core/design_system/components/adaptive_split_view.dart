import 'package:flutter/material.dart';

import '../chaty_adaptive.dart';

/// Adaptive master/detail shell used by conversations, Settings and tablet
/// surfaces. Compact/medium windows keep a single routed pane; expanded/large
/// windows render the list and detail together without duplicating navigation.
class ChatyAdaptiveSplitView extends StatelessWidget {
  const ChatyAdaptiveSplitView({
    super.key,
    required this.primary,
    required this.secondary,
    this.placeholder,
    this.divider,
  });

  final Widget primary;
  final Widget? secondary;
  final Widget? placeholder;
  final Widget? divider;

  @override
  Widget build(BuildContext context) {
    if (!ChatyAdaptive.prefersTwoPane(context)) return primary;

    final colors = Theme.of(context).colorScheme;
    final navigationWidth = ChatyAdaptive.navigationWidth(context);
    final primaryWidth = ChatyAdaptive.conversationListWidth(context);

    return Row(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (navigationWidth > 0) SizedBox(width: navigationWidth),
        SizedBox(width: primaryWidth, child: primary),
        divider ??
            VerticalDivider(
              width: 1,
              thickness: 1,
              color: colors.outlineVariant.withValues(alpha: .55),
            ),
        Expanded(
          child:
              secondary ??
              placeholder ??
              Center(
                child: Text(
                  'Select a conversation',
                  style: TextStyle(color: colors.onSurfaceVariant),
                ),
              ),
        ),
      ],
    );
  }
}
