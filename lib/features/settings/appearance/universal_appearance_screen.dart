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
                  subtitle: 'Motion applied when navigating forward into a screen',
                  value: controller.entryAnimation,
                  options: AppearanceVariantController.entryAnimations,
                  icon: Icons.login_rounded,
                  onSelected: controller.setEntryAnimation,
                ),
                _VariantSection(
                  kind: _PreviewKind.exitMotion,
                  title: 'Screen exit animation',
                  subtitle: 'Motion applied when dismissing or popping a screen',
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
          color: scheme.outlineVariant.withValues(alpha: 0.4),
        ),
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
        child: ExpansionTile(
          shape: const Border(),
          collapsedShape: const Border(),
          leading: Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              color: scheme.primary.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(icon, color: scheme.primary, size: 20),
          ),
          title: Text(
            title,
            style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15),
          ),
          subtitle: Text(
            'Active: ',
            style: TextStyle(
              color: scheme.primary,
              fontWeight: FontWeight.w600,
              fontSize: 13,
            ),
          ),
          childrenPadding: const EdgeInsets.fromLTRB(14, 0, 14, 14),
          children: [
            Align(
              alignment: Alignment.centerLeft,
              child: Text(
                subtitle,
                style: TextStyle(
                  color: scheme.onSurfaceVariant,
                  fontSize: 12.5,
                ),
              ),
            ),
            const SizedBox(height: 12),
            _buildInteractivePicker(context),
          ],
        ),
      ),
    );
  }

  Widget _buildInteractivePicker(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return ListView.separated(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      itemCount: options.length,
      separatorBuilder: (_, _) => const SizedBox(height: 6),
      itemBuilder: (context, index) {
        final opt = options[index];
        final isSelected = opt == value;
        return InkWell(
          onTap: () => onSelected(opt),
          borderRadius: BorderRadius.circular(12),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 180),
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            decoration: BoxDecoration(
              color: isSelected
                  ? scheme.primary.withValues(alpha: 0.12)
                  : scheme.surfaceContainerLow,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: isSelected
                    ? scheme.primary
                    : scheme.outlineVariant.withValues(alpha: 0.2),
                width: isSelected ? 1.5 : 1.0,
              ),
            ),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    opt,
                    style: TextStyle(
                      fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                      color: isSelected ? scheme.primary : scheme.onSurface,
                    ),
                  ),
                ),
                if (isSelected)
                  Icon(Icons.check_circle_rounded, color: scheme.primary, size: 20),
              ],
            ),
          ),
        );
      },
    );
  }
}
