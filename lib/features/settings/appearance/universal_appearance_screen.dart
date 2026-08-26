import 'package:flutter/material.dart';

import '../../../injection/locator.dart';
import '../../../ui/core/controllers/appearance_variant_controller.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart';

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
            title: const Text('Typography & Motion'),
          ),
          body: SafeArea(
            top: false,
            child: ListView(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
              children: [
                _AppearanceOverview(controller: controller),
                const SizedBox(height: 18),
                Text(
                  'Text Scaling & Motion Architecture',
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  'Fine-tune system-wide typography density and animated page transition curves.',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                    height: 1.4,
                  ),
                ),
                const SizedBox(height: 12),
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
                  title: 'Screen entry animation',
                  subtitle:
                      'Motion applied when navigating forward into a screen',
                  value: controller.entryAnimation,
                  options: AppearanceVariantController.entryAnimations,
                  icon: Icons.login_rounded,
                  onSelected: controller.setEntryAnimation,
                ),
                _VariantSection(
                  kind: _PreviewKind.exitMotion,
                  title: 'Screen exit animation',
                  subtitle:
                      'Motion applied when dismissing or popping a screen',
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
        border: Border.all(color: scheme.outlineVariant.withValues(alpha: 0.4)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 36,
                height: 36,
                decoration: BoxDecoration(
                  color: scheme.primary.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Icon(
                  Icons.auto_awesome_rounded,
                  color: scheme.primary,
                  size: 20,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Motion & Typography Profile',
                      style: Theme.of(context).textTheme.titleSmall?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    Text(
                      'Custom configured typography scale and responsive curves',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: scheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              _ChipBadge(label: 'Type: '),
              _ChipBadge(label: 'In: '),
              _ChipBadge(label: 'Out: '),
            ],
          ),
        ],
      ),
    );
  }
}

class _ChipBadge extends StatelessWidget {
  final String label;

  const _ChipBadge({required this.label});

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: scheme.surfaceContainerHighest.withValues(alpha: 0.6),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        label,
        style: Theme.of(context).textTheme.labelSmall?.copyWith(
          color: scheme.onSurfaceVariant,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

enum _PreviewKind { typography, entryMotion, exitMotion }

class _VariantSection extends StatelessWidget {
  final _PreviewKind kind;
  final String title;
  final String subtitle;
  final String value;
  final List<String> options;
  final IconData icon;
  final ValueChanged<String> onSelected;

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
      padding: const EdgeInsets.only(top: 14),
      child: Container(
        decoration: BoxDecoration(
          color: scheme.surface,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: scheme.outlineVariant.withValues(alpha: 0.35),
          ),
        ),
        child: ChatyChoiceTile<String>(
          title: title,
          subtitle: subtitle,
          options: options,
          selectedOption: value,
          optionLabel: (option) => option,
          onSelected: onSelected,
          requireApply: true,
          previewBuilder: (context, option) => _buildPreview(
            context,
            option,
          ),
        ),
      ),
    );
  }

  Widget _buildPreview(BuildContext context, String option) {
    final scheme = Theme.of(context).colorScheme;
    final reduceMotion = MediaQuery.disableAnimationsOf(context);
    return AnimatedContainer(
      duration: reduceMotion
          ? Duration.zero
          : const Duration(milliseconds: 180),
      width: 180,
      constraints: const BoxConstraints(minHeight: 52),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: scheme.primary.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: scheme.primary, size: 20),
          const SizedBox(width: 10),
          Flexible(
            child: Text(
              kind == _PreviewKind.typography
                  ? 'Aa · $option'
                  : option,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                color: scheme.onSurface,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
