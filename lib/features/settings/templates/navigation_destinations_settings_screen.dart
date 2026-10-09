import 'package:flutter/material.dart';

import '../../../injection/locator.dart';
import '../../../ui/core/controllers/appearance_variant_controller.dart';
import '../../../ui/core/design_system/design_system.dart';
import '../../../ui/core/theme/app_theme.dart';
import '../../../ui/core/theme/theme_controller.dart';
import '../../../ui/core/templates/template_controller.dart';
import '../../../ui/core/templates/template_models.dart';

/// Lets users choose which supported destinations live in primary navigation
/// and which remain reachable through More. No route is deleted.
class NavigationDestinationsSettingsScreen extends StatefulWidget {
  const NavigationDestinationsSettingsScreen({super.key});

  @override
  State<NavigationDestinationsSettingsScreen> createState() =>
      _NavigationDestinationsSettingsScreenState();
}

class _NavigationDestinationsSettingsScreenState
    extends State<NavigationDestinationsSettingsScreen> {
  final TemplateController _controller = locator<TemplateController>();
  late List<String> _primary;
  late List<String> _overflow;
  bool _saving = false;

  static const Map<String, ({String title, IconData icon})> _labels = {
    ChatyNavigationDestinationIds.chats: (
      title: 'Chats',
      icon: Icons.chat_bubble_outline_rounded,
    ),
    ChatyNavigationDestinationIds.groups: (
      title: 'Groups',
      icon: Icons.groups_outlined,
    ),
    ChatyNavigationDestinationIds.updates: (
      title: 'Updates',
      icon: Icons.auto_awesome_outlined,
    ),
    ChatyNavigationDestinationIds.tasks: (
      title: 'Tasks',
      icon: Icons.checklist_rounded,
    ),
    ChatyNavigationDestinationIds.calls: (
      title: 'Calls',
      icon: Icons.call_outlined,
    ),
    ChatyNavigationDestinationIds.settings: (
      title: 'Settings',
      icon: Icons.settings_outlined,
    ),
    ChatyNavigationDestinationIds.desktop: (
      title: 'Linked devices',
      icon: Icons.devices_outlined,
    ),
  };

  @override
  void initState() {
    super.initState();
    _loadCurrentConfiguration();
  }

  void _loadCurrentConfiguration() {
    final current = _controller.navigation;
    _primary = List<String>.of(current.primaryDestinationIds);
    _overflow = List<String>.of(current.overflowDestinationIds);
    final assigned = <String>{..._primary, ..._overflow};
    for (final destination in ChatyNavigationDestinationIds.all) {
      if (!assigned.contains(destination)) _overflow.add(destination);
    }

    _primary.removeWhere((id) => !_labels.containsKey(id));
    _overflow.removeWhere((id) => !_labels.containsKey(id));
    for (final destination in ChatyNavigationDestinationIds.all) {
      if (!_primary.contains(destination) && !_overflow.contains(destination)) {
        _overflow.add(destination);
      }
    }
  }

  void _moveToOverflow(String id) {
    if (_primary.length <= 1 || !_primary.contains(id)) return;
    setState(() {
      _primary.remove(id);
      _overflow.add(id);
    });
  }

  void _moveToPrimary(String id) {
    if (_primary.length >= 4 || !_overflow.contains(id)) return;
    setState(() {
      _overflow.remove(id);
      _primary.add(id);
    });
  }

  void _reorder(List<String> list, int index, int delta) {
    final next = index + delta;
    if (next < 0 || next >= list.length) return;
    setState(() {
      final item = list.removeAt(index);
      list.insert(next, item);
    });
  }

  Future<void> _save() async {
    if (_saving) return;
    setState(() => _saving = true);
    try {
      await _controller.setNavigationDestinations(
        primaryDestinationIds: _primary,
        overflowDestinationIds: _overflow,
        appearanceController: locator<AppearanceVariantController>(),
        themeController: locator<ThemeController>(),
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          const SnackBar(content: Text('Navigation destinations saved.')),
        );
      Navigator.of(context).pop();
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          SnackBar(content: Text(error.toString().replaceFirst('Bad state: ', ''))),
        );
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _reset() async {
    await _controller.resetNavigationDestinations(
      appearanceController: locator<AppearanceVariantController>(),
      themeController: locator<ThemeController>(),
    );
    if (!mounted) return;
    setState(_loadCurrentConfiguration);
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        const SnackBar(
          content: Text('Destination order reset to the active template.'),
        ),
      );
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final theme = Theme.of(context);

    return Scaffold(
      backgroundColor: colors.background,
      appBar: AppBar(
        automaticallyImplyLeading: false,
        backgroundColor: colors.surface,
        foregroundColor: colors.foreground,
        elevation: 0,
        leading: const Padding(
          padding: EdgeInsets.all(8),
          child: ChatyBackButton(),
        ),
        title: const Text('Navigation destinations'),
        actions: [
          TextButton(
            onPressed: _saving ? null : _reset,
            child: const Text('Reset'),
          ),
          const SizedBox(width: 4),
          IconButton(
            tooltip: 'Save navigation destinations',
            onPressed: _saving ? null : _save,
            icon: _saving
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.check_rounded),
          ),
        ],
      ),
      body: SafeArea(
        child: LayoutBuilder(
          builder: (context, constraints) {
            final primarySection = _buildDestinationSection(
              context,
              title: 'Primary navigation',
              subtitle: 'Up to 4 frequently used destinations',
              ids: _primary,
              isPrimary: true,
              colors: colors,
              theme: theme,
            );
            final overflowSection = _buildDestinationSection(
              context,
              title: 'More menu',
              subtitle: 'All remaining screens stay available here',
              ids: _overflow,
              isPrimary: false,
              colors: colors,
              theme: theme,
            );

            if (constraints.maxWidth >= 760) {
              return Row(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Expanded(
                    child: SingleChildScrollView(
                      padding: const EdgeInsets.fromLTRB(20, 18, 10, 28),
                      child: primarySection,
                    ),
                  ),
                  VerticalDivider(width: 1, color: colors.border),
                  Expanded(
                    child: SingleChildScrollView(
                      padding: const EdgeInsets.fromLTRB(10, 18, 20, 28),
                      child: overflowSection,
                    ),
                  ),
                ],
              );
            }

            return ListView(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
              children: [
                primarySection,
                const SizedBox(height: 18),
                overflowSection,
                const SizedBox(height: 12),
                Text(
                  'Tip: keep your most-used screens in Primary. Every destination remains available either here or under More.',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: colors.foregroundSecondary,
                    height: 1.35,
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _buildDestinationSection(
    BuildContext context, {
    required String title,
    required String subtitle,
    required List<String> ids,
    required bool isPrimary,
    required AppColors colors,
    required ThemeData theme,
  }) {
    return Container(
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: colors.borderSubtle),
      ),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(4, 2, 4, 10),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          title,
                          style: theme.textTheme.titleMedium?.copyWith(
                            color: colors.foreground,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                        const SizedBox(height: 3),
                        Text(
                          subtitle,
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: colors.foregroundSecondary,
                          ),
                        ),
                      ],
                    ),
                  ),
                  if (isPrimary)
                    Text(
                      ids.length.toString() + '/4',
                      style: theme.textTheme.labelLarge?.copyWith(
                        color: colors.primary,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                ],
              ),
            ),
            if (ids.isEmpty)
              Padding(
                padding: const EdgeInsets.all(16),
                child: Text(
                  'No destinations in this group.',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: colors.foregroundSecondary,
                  ),
                ),
              ),
            for (var index = 0; index < ids.length; index++) ...[
              if (index > 0) Divider(height: 1, color: colors.borderSubtle),
              _buildDestinationRow(
                context,
                id: ids[index],
                index: index,
                ids: ids,
                isPrimary: isPrimary,
                colors: colors,
                theme: theme,
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildDestinationRow(
    BuildContext context, {
    required String id,
    required int index,
    required List<String> ids,
    required bool isPrimary,
    required AppColors colors,
    required ThemeData theme,
  }) {
    final option = _labels[id];
    if (option == null) return const SizedBox.shrink();

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 2),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Container(
                width: 38,
                height: 38,
                decoration: BoxDecoration(
                  color: colors.primary.withValues(alpha: 0.10),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(option.icon, color: colors.primary, size: 20),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  option.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: colors.foreground,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Wrap(
            alignment: WrapAlignment.end,
            spacing: 2,
            runSpacing: 0,
            children: [
              IconButton(
                tooltip: 'Move up',
                visualDensity: VisualDensity.compact,
                onPressed: index == 0 ? null : () => _reorder(ids, index, -1),
                icon: const Icon(Icons.keyboard_arrow_up_rounded),
              ),
              IconButton(
                tooltip: 'Move down',
                visualDensity: VisualDensity.compact,
                onPressed: index == ids.length - 1
                    ? null
                    : () => _reorder(ids, index, 1),
                icon: const Icon(Icons.keyboard_arrow_down_rounded),
              ),
              TextButton.icon(
                onPressed: isPrimary
                    ? (_primary.length <= 1 ? null : () => _moveToOverflow(id))
                    : (_primary.length >= 4 ? null : () => _moveToPrimary(id)),
                icon: Icon(
                  isPrimary
                      ? Icons.keyboard_arrow_down_rounded
                      : Icons.keyboard_arrow_up_rounded,
                  size: 18,
                ),
                label: Text(isPrimary ? 'Move to More' : 'Make primary'),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
