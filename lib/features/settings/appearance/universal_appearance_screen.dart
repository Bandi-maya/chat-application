import 'package:flutter/material.dart';

import '../../../injection/locator.dart';
import '../../../ui/core/controllers/app_icon_controller.dart';
import '../../../ui/core/controllers/appearance_variant_controller.dart';
import 'app_icon_settings_screen.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart';
import '../theme_editor_screen.dart';

class UniversalAppearanceScreen extends StatelessWidget {
  final ChatyPreferencesController preferencesController;

  const UniversalAppearanceScreen({
    super.key,
    required this.preferencesController,
  });

  @override
  Widget build(BuildContext context) {
    final controller = locator<AppearanceVariantController>();
    final themeController = locator<ThemeController>();

    return ListenableBuilder(
      listenable: Listenable.merge(<Listenable>[controller, themeController]),
      builder: (context, _) {
        final theme = themeController.globalTheme;
        return Scaffold(
          backgroundColor: theme.backgroundColor,
          appBar: AppBar(
            automaticallyImplyLeading: false,
            backgroundColor: theme.surfaceColor,
            foregroundColor: theme.primaryTextColor,
            surfaceTintColor: Colors.transparent,
            leading: const Padding(
              padding: EdgeInsets.all(8.0),
              child: ChatyBackButton(),
            ),
            title: const Text('Look & feel'),
          ),
          body: SafeArea(
            top: false,
            child: ListView(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
              children: [
                _AppearanceOverview(controller: controller),
                const SizedBox(height: 18),
                Text(
                  'Customize components',
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  'Preview a component first, then apply it. Your current choice stays active until you confirm a new one.',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                    height: 1.4,
                  ),
                ),
                const SizedBox(height: 12),
                // REAL control: navigation layout (Compact Rail / Gesture
                // Tabs / Bottom Nav) lives in the theme editor and is fully
                // wired. The old write-only 'Navigation style' / 'Bottom bar
                // style' pickers here never changed anything app-wide and
                // were removed; this tile takes you to the real one.
                ListTile(
                  leading: const Icon(Icons.view_sidebar_outlined),
                  title: const Text('Navigation layout architecture'),
                  subtitle: const Text(
                    'Bottom nav, Top WhatsApp bar, 3D Drawer, Side menu & Island rail',
                  ),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) => ThemeEditorScreen(
                        themeController: locator<ThemeController>(),
                      ),
                    ),
                  ),
                ),
                // REAL control: the app icon is managed by the dedicated
                // App Icon system (launcher variants + custom uploaded icon).
                // The old write-only 'icon language' choice lists lived here;
                // they never changed anything app-wide and were removed.
                ListTile(
                  leading: const Icon(Icons.apps_rounded),
                  title: const Text('App icon'),
                  subtitle: const Text(
                    'Launcher style, custom uploaded icon & preview',
                  ),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) => AppIconSettingsScreen(
                        appIconController: locator<AppIconController>(),
                      ),
                    ),
                  ),
                ),
                _VariantSection(
                  kind: _PreviewKind.bottomBar,
                  title: 'Bottom navigation bar design',
                  subtitle: '12 unique animated navigation bar layouts',
                  value: controller.bottomBarStyle,
                  options: AppearanceVariantController.bottomBarStyles,
                  icon: Icons.dock_rounded,
                  onSelected: controller.setBottomBarStyle,
                ),
                _VariantSection(
                  kind: _PreviewKind.typography,
                  title: 'Typography style',
                  subtitle: 'Text density and scale across Chaty',
                  value: controller.typographyStyle,
                  options: AppearanceVariantController.typographyStyles,
                  icon: Icons.text_fields_rounded,
                  onSelected: controller.setTypographyStyle,
                ),
                _VariantSection(
                  kind: _PreviewKind.entryMotion,
                  title: 'Entry animation',
                  subtitle: 'Motion used when a screen opens',
                  value: controller.entryAnimation,
                  options: AppearanceVariantController.entryAnimations,
                  icon: Icons.login_rounded,
                  onSelected: controller.setEntryAnimation,
                ),
                _VariantSection(
                  kind: _PreviewKind.exitMotion,
                  title: 'Exit animation',
                  subtitle: 'Motion used when a screen closes',
                  value: controller.exitAnimation,
                  options: AppearanceVariantController.exitAnimations,
                  icon: Icons.logout_rounded,
                  onSelected: controller.setExitAnimation,
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}

class _AppearanceOverview extends StatelessWidget {
  final AppearanceVariantController controller;

  const _AppearanceOverview({required this.controller});

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: scheme.surfaceContainerLow,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: scheme.outlineVariant.withValues(alpha: 0.55),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 42,
                height: 42,
                decoration: BoxDecoration(
                  color: scheme.primaryContainer,
                  borderRadius: BorderRadius.circular(13),
                ),
                child: Icon(
                  Icons.visibility_outlined,
                  color: scheme.onPrimaryContainer,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Current appearance',
                      style: TextStyle(
                        fontWeight: FontWeight.w800,
                        fontSize: 16,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      controller.typographyStyle,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: scheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

enum _PreviewKind {
  navigation,
  bottomBar,
  appIcon,
  notification,
  typography,
  entryMotion,
  exitMotion,
}

class _VariantSection extends StatelessWidget {
  final _PreviewKind kind;
  final String title;
  final String subtitle;
  final String value;
  final List<String> options;
  final IconData icon;
  final Future<void> Function(String) onSelected;

  const _VariantSection({
    required this.kind,
    required this.title,
    required this.subtitle,
    required this.value,
    required this.options,
    required this.icon,
    required this.onSelected,
  });

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Material(
        color: scheme.surfaceContainerLow,
        borderRadius: BorderRadius.circular(16),
        child: InkWell(
          borderRadius: BorderRadius.circular(16),
          onTap: () => _showSelector(context),
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 76),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
              child: Row(
                children: [
                  Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      color: scheme.primaryContainer,
                      borderRadius: BorderRadius.circular(13),
                    ),
                    child: Icon(
                      icon,
                      color: scheme.onPrimaryContainer,
                      size: 21,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text(
                          title,
                          style: const TextStyle(fontWeight: FontWeight.w700),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          subtitle,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: Theme.of(context).textTheme.bodySmall
                              ?.copyWith(color: scheme.onSurfaceVariant),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          value,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            color: scheme.primary,
                            fontSize: 12.5,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 8),
                  const Icon(Icons.chevron_right_rounded),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _showSelector(BuildContext context) async {
    final selected = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      builder: (sheetContext) => _VariantPickerSheet(
        kind: kind,
        title: title,
        currentValue: value,
        options: options,
      ),
    );
    if (selected != null && selected != value) {
      await onSelected(selected);
    }
  }
}

class _VariantPickerSheet extends StatefulWidget {
  final _PreviewKind kind;
  final String title;
  final String currentValue;
  final List<String> options;

  const _VariantPickerSheet({
    required this.kind,
    required this.title,
    required this.currentValue,
    required this.options,
  });

  @override
  State<_VariantPickerSheet> createState() => _VariantPickerSheetState();
}

class _VariantPickerSheetState extends State<_VariantPickerSheet> {
  late String _candidate;

  @override
  void initState() {
    super.initState();
    _candidate = widget.currentValue;
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return FractionallySizedBox(
      heightFactor: 0.9,
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(18, 2, 8, 10),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        widget.title,
                        style: Theme.of(context).textTheme.titleLarge?.copyWith(
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        'Preview before applying',
                        style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: scheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  tooltip: 'Close',
                  onPressed: () => Navigator.of(context).pop(),
                  icon: const Icon(Icons.close_rounded),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: AnimatedSwitcher(
              duration: const Duration(milliseconds: 180),
              child: _OptionPreview(
                key: ValueKey('${widget.kind.name}-$_candidate'),
                kind: widget.kind,
                value: _candidate,
                options: widget.options,
              ),
            ),
          ),
          const SizedBox(height: 12),
          Expanded(
            child: ListView.builder(
              padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
              itemCount: widget.options.length,
              itemBuilder: (context, index) {
                final option = widget.options[index];
                final active = option == _candidate;
                return Padding(
                  padding: const EdgeInsets.only(bottom: 4),
                  child: Material(
                    color: active
                        ? scheme.primaryContainer.withValues(alpha: 0.55)
                        : Colors.transparent,
                    borderRadius: BorderRadius.circular(14),
                    child: InkWell(
                      borderRadius: BorderRadius.circular(14),
                      onTap: () => setState(() => _candidate = option),
                      child: ConstrainedBox(
                        constraints: const BoxConstraints(minHeight: 52),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 12,
                            vertical: 8,
                          ),
                          child: Row(
                            children: [
                              SizedBox(
                                width: 34,
                                child: Text(
                                  '${index + 1}'.padLeft(2, '0'),
                                  style: Theme.of(context).textTheme.labelSmall,
                                ),
                              ),
                              Expanded(
                                child: Text(
                                  option,
                                  style: TextStyle(
                                    fontWeight: active
                                        ? FontWeight.w800
                                        : FontWeight.w500,
                                  ),
                                ),
                              ),
                              if (active)
                                Icon(
                                  Icons.check_circle_rounded,
                                  color: scheme.primary,
                                )
                              else
                                const Icon(Icons.chevron_right_rounded),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
          Container(
            padding: EdgeInsets.fromLTRB(
              16,
              10,
              16,
              10 + MediaQuery.viewPaddingOf(context).bottom,
            ),
            decoration: BoxDecoration(
              color: scheme.surface,
              border: Border(
                top: BorderSide(
                  color: scheme.outlineVariant.withValues(alpha: 0.5),
                ),
              ),
            ),
            child: Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => Navigator.of(context).pop(),
                    child: const Text('Cancel'),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  flex: 2,
                  child: FilledButton.icon(
                    onPressed: () => Navigator.of(context).pop(_candidate),
                    icon: const Icon(Icons.check_rounded),
                    label: Text(
                      _candidate == widget.currentValue
                          ? 'Keep current'
                          : 'Apply',
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _OptionPreview extends StatelessWidget {
  final _PreviewKind kind;
  final String value;
  final List<String> options;

  const _OptionPreview({
    super.key,
    required this.kind,
    required this.value,
    required this.options,
  });

  int get _index {
    final index = options.indexOf(value);
    return index < 0 ? 0 : index;
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      width: double.infinity,
      constraints: const BoxConstraints(minHeight: 112),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: scheme.surfaceContainerLow,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: scheme.outlineVariant.withValues(alpha: 0.65),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  value,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontWeight: FontWeight.w800),
                ),
              ),
              Text(
                'Preview',
                style: Theme.of(context).textTheme.labelSmall?.copyWith(
                  color: scheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          _buildPreview(context),
        ],
      ),
    );
  }

  Widget _buildPreview(BuildContext context) {
    switch (kind) {
      case _PreviewKind.bottomBar:
        return _bottomBarPreview(context);
      case _PreviewKind.navigation:
        return _navigationPreview(context);
      case _PreviewKind.appIcon:
        return _iconPreview(context, notification: false);
      case _PreviewKind.notification:
        return _iconPreview(context, notification: true);
      case _PreviewKind.typography:
        return _typographyPreview(context);
      case _PreviewKind.entryMotion:
        return _motionPreview(context, entering: true);
      case _PreviewKind.exitMotion:
        return _motionPreview(context, entering: false);
    }
  }

  Widget _bottomBarPreview(BuildContext context) {
    final colors = context.colors;
    final primary = colors.primary;

    return Container(
      height: 64,
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: switch (value) {
          'Floating Dynamic Island' => colors.surfaceElevated,
          'Segmented Glass Dock' => colors.surface.withValues(alpha: 0.85),
          _ => colors.surface,
        },
        borderRadius: BorderRadius.circular(switch (value) {
          'Floating Pill' ||
          'Floating Dynamic Island' ||
          'Active Pill Chip' => 28,
          'Top Indicator Line' || 'Classic Label Bar' => 12,
          'Circle Accent Pop' => 24,
          'Soft Square Tile' => 16,
          _ => 20,
        }),
        border: Border.all(color: colors.borderSubtle),
        boxShadow: [
          BoxShadow(
            color: colors.shadow,
            blurRadius: 10,
            offset: const Offset(0, 3),
          ),
        ],
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          // Active Tab 1
          _buildSampleActiveTab(context, primary, value),
          // Inactive Tab 2
          Icon(
            Icons.update_rounded,
            size: 20,
            color: colors.foregroundSecondary,
          ),
          // Inactive Tab 3
          Icon(
            Icons.checklist_rounded,
            size: 20,
            color: colors.foregroundSecondary,
          ),
          // Inactive Tab 4
          Icon(Icons.call_rounded, size: 20, color: colors.foregroundSecondary),
        ],
      ),
    );
  }

  Widget _buildSampleActiveTab(
    BuildContext context,
    Color accent,
    String style,
  ) {
    final colors = context.colors;
    switch (style) {
      case 'Active Pill Chip':
        return Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          decoration: BoxDecoration(
            color: accent.withValues(alpha: 0.16),
            borderRadius: BorderRadius.circular(20),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.chat_bubble_rounded, size: 18, color: accent),
              const SizedBox(width: 5),
              Text(
                'Chats',
                style: TextStyle(
                  color: accent,
                  fontWeight: FontWeight.w700,
                  fontSize: 11.5,
                ),
              ),
            ],
          ),
        );
      case 'Circle Accent Pop':
        return Container(
          width: 36,
          height: 36,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: accent,
            boxShadow: [
              BoxShadow(
                color: accent.withValues(alpha: 0.35),
                blurRadius: 8,
                offset: const Offset(0, 2),
              ),
            ],
          ),
          child: Icon(
            Icons.chat_bubble_rounded,
            size: 18,
            color: colors.onPrimary,
          ),
        );
      case 'Top Indicator Line':
        return Column(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Container(
              width: 22,
              height: 3,
              decoration: BoxDecoration(
                color: accent,
                borderRadius: const BorderRadius.vertical(
                  bottom: Radius.circular(2),
                ),
              ),
            ),
            Icon(Icons.chat_bubble_rounded, size: 20, color: accent),
          ],
        );
      case 'Bottom Indicator Dot':
        return Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.chat_bubble_rounded, size: 20, color: accent),
            const SizedBox(height: 3),
            Container(
              width: 4.5,
              height: 4.5,
              decoration: BoxDecoration(color: accent, shape: BoxShape.circle),
            ),
          ],
        );
      case 'Raised Center Action':
        return Transform.translate(
          offset: const Offset(0, -6),
          child: Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: [accent, accent.withValues(alpha: 0.8)],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              shape: BoxShape.circle,
              boxShadow: [
                BoxShadow(
                  color: accent.withValues(alpha: 0.4),
                  blurRadius: 8,
                  offset: const Offset(0, 3),
                ),
              ],
            ),
            child: Icon(
              Icons.chat_bubble_rounded,
              size: 20,
              color: colors.onPrimary,
            ),
          ),
        );
      case 'Soft Square Tile':
        return Container(
          padding: const EdgeInsets.all(7),
          decoration: BoxDecoration(
            color: accent.withValues(alpha: 0.16),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Icon(Icons.chat_bubble_rounded, size: 18, color: accent),
        );
      default:
        return Container(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
          decoration: BoxDecoration(
            color: accent.withValues(alpha: 0.14),
            borderRadius: BorderRadius.circular(16),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.chat_bubble_rounded, size: 18, color: accent),
              const SizedBox(width: 4),
              Text(
                'Chats',
                style: TextStyle(
                  color: accent,
                  fontWeight: FontWeight.w700,
                  fontSize: 11,
                ),
              ),
            ],
          ),
        );
    }
  }

  Widget _navigationPreview(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final rail = _index < 10 || <int>{17, 18}.contains(_index);
    if (rail) {
      return Container(
        height: 92,
        decoration: BoxDecoration(
          color: scheme.surface,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: scheme.outlineVariant),
        ),
        child: Row(
          children: [
            Container(
              width: _index == 8 ? 88 : 48,
              padding: const EdgeInsets.symmetric(vertical: 6),
              decoration: BoxDecoration(
                border: Border(right: BorderSide(color: scheme.outlineVariant)),
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: const [
                  Icon(Icons.chat_bubble_rounded, size: 18),
                  Icon(Icons.update_rounded, size: 18),
                  Icon(Icons.call_rounded, size: 18),
                ],
              ),
            ),
            Expanded(
              child: Center(
                child: Text(
                  'Chat content',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: scheme.onSurfaceVariant,
                  ),
                ),
              ),
            ),
          ],
        ),
      );
    }
    return Container(
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        color: scheme.surface,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: scheme.outlineVariant),
      ),
      child: Column(
        children: [
          Row(
            children: [
              Expanded(child: _tab(context, 'Chats', true)),
              Expanded(child: _tab(context, 'Updates', false)),
              Expanded(child: _tab(context, 'Calls', false)),
            ],
          ),
          const SizedBox(height: 12),
          Align(
            alignment: Alignment.centerLeft,
            child: Container(
              width: 110,
              height: 8,
              decoration: BoxDecoration(
                color: scheme.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(8),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _tab(BuildContext context, String label, bool selected) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      alignment: Alignment.center,
      constraints: const BoxConstraints(minHeight: 40),
      decoration: BoxDecoration(
        color: selected ? scheme.primaryContainer : Colors.transparent,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(
        label,
        style: TextStyle(
          fontSize: 11,
          fontWeight: selected ? FontWeight.w800 : FontWeight.w500,
        ),
      ),
    );
  }

  Widget _iconPreview(BuildContext context, {required bool notification}) {
    final scheme = Theme.of(context).colorScheme;
    final icons = notification ? _notificationIcons : _appIcons;
    final icon = icons[_index.clamp(0, icons.length - 1)];
    return Row(
      children: [
        Container(
          width: 64,
          height: 64,
          decoration: BoxDecoration(
            color: scheme.primaryContainer,
            borderRadius: BorderRadius.circular(notification ? 32 : 17),
          ),
          child: Icon(icon, size: 30, color: scheme.onPrimaryContainer),
        ),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                notification ? 'New message' : 'Chaty',
                style: const TextStyle(fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 4),
              Text(
                notification
                    ? 'This is how the selected notification identity reads at a glance.'
                    : 'This icon identity is used on supported in-app surfaces.',
                style: Theme.of(
                  context,
                ).textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _typographyPreview(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    const scales = <double>[
      1.0,
      0.94,
      1.04,
      1.02,
      1.0,
      1.0,
      1.02,
      1.03,
      0.96,
      0.98,
      0.90,
      1.10,
      1.18,
      0.96,
      0.98,
      1.02,
      1.0,
      1.0,
      1.04,
      0.96,
    ];
    final scale = scales[_index.clamp(0, scales.length - 1)];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'A clear conversation',
          style: TextStyle(fontSize: 18 * scale, fontWeight: FontWeight.w800),
        ),
        const SizedBox(height: 4),
        Text(
          'Messages, labels and actions remain readable without crowding the screen.',
          style: TextStyle(
            fontSize: 13 * scale,
            height: 1.35,
            color: scheme.onSurfaceVariant,
          ),
        ),
      ],
    );
  }

  Widget _motionPreview(BuildContext context, {required bool entering}) {
    final scheme = Theme.of(context).colorScheme;
    final lower = value.toLowerCase();
    IconData icon;
    if (lower.contains('left')) {
      icon = Icons.west_rounded;
    } else if (lower.contains('right')) {
      icon = Icons.east_rounded;
    } else if (lower.contains('up')) {
      icon = Icons.north_rounded;
    } else if (lower.contains('down')) {
      icon = Icons.south_rounded;
    } else if (lower.contains('scale') || lower.contains('zoom')) {
      icon = entering ? Icons.zoom_in_rounded : Icons.zoom_out_rounded;
    } else if (lower.contains('none')) {
      icon = Icons.horizontal_rule_rounded;
    } else {
      icon = entering ? Icons.login_rounded : Icons.logout_rounded;
    }
    return Row(
      children: [
        Container(
          width: 56,
          height: 56,
          decoration: BoxDecoration(
            color: scheme.primaryContainer,
            borderRadius: BorderRadius.circular(16),
          ),
          child: Icon(icon, color: scheme.onPrimaryContainer, size: 28),
        ),
        const SizedBox(width: 14),
        Expanded(
          child: Text(
            entering
                ? 'Screen content enters with this motion profile.'
                : 'Screen content leaves with this motion profile.',
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: scheme.onSurfaceVariant,
              height: 1.4,
            ),
          ),
        ),
      ],
    );
  }

  static const List<IconData> _appIcons = <IconData>[
    Icons.chat_bubble_rounded,
    Icons.chat_bubble_outline_rounded,
    Icons.forum_rounded,
    Icons.message_rounded,
    Icons.mark_chat_unread_rounded,
    Icons.contrast_rounded,
    Icons.visibility_rounded,
    Icons.chat_rounded,
    Icons.sms_rounded,
    Icons.send_rounded,
    Icons.forum_outlined,
    Icons.question_answer_rounded,
    Icons.waves_rounded,
    Icons.bolt_rounded,
    Icons.blur_circular_rounded,
    Icons.grid_view_rounded,
    Icons.workspaces_rounded,
    Icons.auto_awesome_rounded,
    Icons.lock_rounded,
    Icons.center_focus_strong_rounded,
  ];

  static const List<IconData> _notificationIcons = <IconData>[
    Icons.chat_bubble_rounded,
    Icons.chat_bubble_outline_rounded,
    Icons.done_all_rounded,
    Icons.notifications_active_rounded,
    Icons.mark_unread_chat_alt_rounded,
    Icons.circle_notifications_rounded,
    Icons.priority_high_rounded,
    Icons.account_circle_rounded,
    Icons.groups_rounded,
    Icons.task_alt_rounded,
    Icons.call_rounded,
    Icons.videocam_rounded,
    Icons.update_rounded,
    Icons.notifications_off_rounded,
    Icons.lock_rounded,
    Icons.shield_rounded,
    Icons.badge_rounded,
    Icons.workspaces_rounded,
    Icons.contrast_rounded,
    Icons.center_focus_strong_rounded,
  ];
}
