import 'package:flutter/material.dart';

import '../../../injection/locator.dart';
import '../../../ui/core/controllers/appearance_variant_controller.dart';
import '../../../ui/core/controllers/chaty_preferences_controller.dart';
import '../../../ui/core/theme/theme_controller.dart';

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
            leading: IconButton(
              tooltip: 'Back',
              onPressed: () => Navigator.of(context).pop(),
              icon: const Icon(Icons.chevron_left_rounded),
            ),
            title: const Text('Universal appearance'),
          ),
          body: SafeArea(
            top: false,
            child: ListView(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
              children: [
                _AppearancePreview(controller: controller),
                const SizedBox(height: 18),
                _VariantSection(
                  title: 'Navigation style',
                  subtitle: '20 navigation layouts for regular, split-screen and wide windows.',
                  value: controller.navigationStyle,
                  options: AppearanceVariantController.navigationStyles,
                  icon: Icons.view_sidebar_outlined,
                  onSelected: controller.setNavigationStyle,
                ),
                _VariantSection(
                  title: 'Bottom bar style',
                  subtitle: '20 bottom navigation treatments; the selected style is applied to the root shell.',
                  value: controller.bottomBarStyle,
                  options: AppearanceVariantController.bottomBarStyles,
                  icon: Icons.space_bar_rounded,
                  onSelected: controller.setBottomBarStyle,
                ),
                _VariantSection(
                  title: 'App icon language',
                  subtitle: '20 in-app icon identities used by appearance previews and branded surfaces.',
                  value: controller.appIconStyle,
                  options: AppearanceVariantController.appIconStyles,
                  icon: Icons.apps_rounded,
                  onSelected: controller.setAppIconStyle,
                ),
                _VariantSection(
                  title: 'Notification icon language',
                  subtitle: '20 notification visual identities for preview and notification presentation.',
                  value: controller.notificationIconStyle,
                  options: AppearanceVariantController.notificationIconStyles,
                  icon: Icons.notifications_active_outlined,
                  onSelected: controller.setNotificationIconStyle,
                ),
                _VariantSection(
                  title: 'Typography style',
                  subtitle: '20 persistent typography density/scale presets applied globally.',
                  value: controller.typographyStyle,
                  options: AppearanceVariantController.typographyStyles,
                  icon: Icons.text_fields_rounded,
                  onSelected: controller.setTypographyStyle,
                ),
                _VariantSection(
                  title: 'Entry animation',
                  subtitle: '20 motion profiles for incoming navigation surfaces.',
                  value: controller.entryAnimation,
                  options: AppearanceVariantController.entryAnimations,
                  icon: Icons.login_rounded,
                  onSelected: controller.setEntryAnimation,
                ),
                _VariantSection(
                  title: 'Exit animation',
                  subtitle: '20 motion profiles for outgoing navigation surfaces.',
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

class _AppearancePreview extends StatelessWidget {
  final AppearanceVariantController controller;
  const _AppearancePreview({required this.controller});

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    final bottomIndex = controller.bottomBarIndex;
    final radius = <double>[30, 14, 24, 22, 28, 30, 18, 12, 8, 20, 16, 20, 4, 10, 32, 26, 18, 0, 14, 10][bottomIndex];
    final compact = <int>{2, 6, 8, 13, 15, 19}.contains(bottomIndex);

    return Container(
      decoration: BoxDecoration(
        color: scheme.surfaceContainerLow,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: scheme.outlineVariant.withValues(alpha: 0.7)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 10),
            child: Row(
              children: [
                Icon(Icons.visibility_outlined, color: scheme.primary, size: 19),
                const SizedBox(width: 8),
                Text('Live preview', style: text.titleSmall?.copyWith(fontWeight: FontWeight.w800)),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Container(
              padding: const EdgeInsets.all(13),
              decoration: BoxDecoration(
                color: scheme.surface,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: scheme.outlineVariant.withValues(alpha: 0.55)),
              ),
              child: Row(
                children: [
                  CircleAvatar(
                    radius: 20,
                    backgroundColor: scheme.primaryContainer,
                    child: Icon(_appIcon(controller.appIconStyle), color: scheme.onPrimaryContainer),
                  ),
                  const SizedBox(width: 11),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Chaty preview', style: text.bodyLarge?.copyWith(fontWeight: FontWeight.w800)),
                        const SizedBox(height: 2),
                        Text(
                          '${controller.typographyStyle} • ${controller.notificationIconStyle}',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: text.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
                        ),
                      ],
                    ),
                  ),
                  Icon(_notificationIcon(controller.notificationIconStyle), color: scheme.primary),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 14),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 220),
              height: compact ? 50 : 58,
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
              decoration: BoxDecoration(
                color: scheme.surface,
                borderRadius: BorderRadius.circular(radius),
                border: Border.all(color: scheme.outlineVariant),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  _PreviewNavIcon(icon: Icons.chat_bubble_rounded, selected: true),
                  _PreviewNavIcon(icon: Icons.update_rounded),
                  _PreviewNavIcon(icon: Icons.checklist_rounded),
                  _PreviewNavIcon(icon: Icons.call_rounded),
                  _PreviewNavIcon(icon: Icons.settings_rounded),
                ],
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 14),
            child: Text(
              'Navigation: ${controller.navigationStyle}\nEntry: ${controller.entryAnimation}   Exit: ${controller.exitAnimation}',
              style: text.bodySmall?.copyWith(color: scheme.onSurfaceVariant, height: 1.45),
            ),
          ),
        ],
      ),
    );
  }

  IconData _appIcon(String style) {
    final index = AppearanceVariantController.appIconStyles.indexOf(style);
    const icons = <IconData>[
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
    return icons[index < 0 ? 0 : index];
  }

  IconData _notificationIcon(String style) {
    final index = AppearanceVariantController.notificationIconStyles.indexOf(style);
    const icons = <IconData>[
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
    return icons[index < 0 ? 0 : index];
  }
}

class _PreviewNavIcon extends StatelessWidget {
  final IconData icon;
  final bool selected;
  const _PreviewNavIcon({required this.icon, this.selected = false});

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      width: 38,
      height: 38,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: selected ? scheme.primaryContainer : Colors.transparent,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Icon(icon, size: 19, color: selected ? scheme.primary : scheme.onSurfaceVariant),
    );
  }
}

class _VariantSection extends StatelessWidget {
  final String title;
  final String subtitle;
  final String value;
  final List<String> options;
  final IconData icon;
  final Future<void> Function(String) onSelected;

  const _VariantSection({
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
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: scheme.surfaceContainerLow,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: scheme.outlineVariant.withValues(alpha: 0.6)),
      ),
      child: ListTile(
        leading: Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            color: scheme.primaryContainer,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Icon(icon, color: scheme.onPrimaryContainer, size: 20),
        ),
        title: Text(title, style: const TextStyle(fontWeight: FontWeight.w700)),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SizedBox(height: 3),
            Text(subtitle),
            const SizedBox(height: 5),
            Text(value, style: TextStyle(color: scheme.primary, fontWeight: FontWeight.w700)),
          ],
        ),
        trailing: const Icon(Icons.chevron_right_rounded),
        onTap: () => _showSelector(context),
      ),
    );
  }

  Future<void> _showSelector(BuildContext context) async {
    final selected = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (context) {
        return DraggableScrollableSheet(
          expand: false,
          initialChildSize: 0.72,
          minChildSize: 0.45,
          maxChildSize: 0.92,
          builder: (context, scrollController) {
            return Column(
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 14, 8, 10),
                  child: Row(
                    children: [
                      Expanded(child: Text(title, style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800))),
                      IconButton(onPressed: () => Navigator.of(context).pop(), icon: const Icon(Icons.close_rounded)),
                    ],
                  ),
                ),
                Expanded(
                  child: ListView.separated(
                    controller: scrollController,
                    padding: const EdgeInsets.fromLTRB(12, 0, 12, 20),
                    itemCount: options.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 4),
                    itemBuilder: (context, index) {
                      final option = options[index];
                      final active = option == value;
                      return ListTile(
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(13)),
                        selected: active,
                        selectedTileColor: Theme.of(context).colorScheme.primaryContainer.withValues(alpha: 0.55),
                        leading: SizedBox(
                          width: 30,
                          child: Text('${index + 1}'.padLeft(2, '0'), style: Theme.of(context).textTheme.labelSmall),
                        ),
                        title: Text(option, style: TextStyle(fontWeight: active ? FontWeight.w800 : FontWeight.w500)),
                        trailing: active ? const Icon(Icons.check_rounded) : const Icon(Icons.chevron_right_rounded),
                        onTap: () => Navigator.of(context).pop(option),
                      );
                    },
                  ),
                ),
              ],
            );
          },
        );
      },
    );
    if (selected != null) await onSelected(selected);
  }
}
