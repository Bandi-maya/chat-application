import 'package:flutter/material.dart';

/// The single, measured header row owned by a structural template shell.
///
/// Feature screens provide their content below this widget; they must not
/// position global navigation controls over that content. Keeping navigation,
/// title and actions in explicit slots prevents drawer controls and long titles
/// from competing for the same pixels.
class TemplateShellHeader extends StatelessWidget {
  const TemplateShellHeader({
    super.key,
    required this.title,
    required this.navigation,
    this.actions = const <Widget>[],
    this.backgroundColor,
    this.foregroundColor,
    this.dividerColor,
  });

  final String title;
  final Widget navigation;
  final List<Widget> actions;
  final Color? backgroundColor;
  final Color? foregroundColor;
  final Color? dividerColor;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final effectiveForeground = foregroundColor ?? theme.colorScheme.onSurface;

    return Material(
      color: backgroundColor ?? theme.colorScheme.surface,
      child: DecoratedBox(
        decoration: BoxDecoration(
          border: Border(
            bottom: BorderSide(
              color: dividerColor ?? theme.dividerColor,
              width: 0.8,
            ),
          ),
        ),
        child: SafeArea(
          bottom: false,
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 56),
            child: Row(
              children: [
                SizedBox(width: 56, height: 56, child: Center(child: navigation)),
                Expanded(
                  child: Text(
                    title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.titleLarge?.copyWith(
                      color: effectiveForeground,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
                if (actions.isNotEmpty)
                  ConstrainedBox(
                    constraints: const BoxConstraints(minHeight: 56),
                    child: Row(mainAxisSize: MainAxisSize.min, children: actions),
                  ),
                const SizedBox(width: 8),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
