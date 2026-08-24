import 'package:flutter/material.dart';
import '../../../injection/locator.dart';
import '../../../ui/core/controllers/appearance_variant_controller.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart';
import '../../../ui/core/templates/template_controller.dart';
import '../../../ui/core/templates/template_models.dart';
import '../../../ui/core/templates/template_registry.dart';
import 'component_override_screen.dart';
import 'template_preview_widget.dart';

/// Top-level Settings screen for managing structural UI Templates & component-level overrides.
class TemplatesSettingsScreen extends StatelessWidget {
  const TemplatesSettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final templateController = locator<TemplateController>();
    final colors = context.colors;
    final theme = Theme.of(context);

    return ListenableBuilder(
      listenable: templateController,
      builder: (context, _) {
        final config = templateController.config;
        final baseTmpl = ChatyTemplateRegistry.get(config.baseTemplate);
        final overridesCount = config.componentOverrides.length;

        return Scaffold(
          backgroundColor: colors.background,
          appBar: AppBar(
            automaticallyImplyLeading: false,
            backgroundColor: colors.surface,
            foregroundColor: colors.foreground,
            elevation: 0,
            leading: const Padding(
              padding: EdgeInsets.all(8.0),
              child: ChatyBackButton(),
            ),
            title: const Text('Templates'),
            actions: [
              if (overridesCount > 0 || config.baseTemplate != ChatyTemplateId.messageFirst)
                IconButton(
                  tooltip: 'Reset All Templates',
                  icon: const Icon(Icons.restart_alt_rounded),
                  onPressed: () => _confirmReset(context, templateController),
                ),
            ],
          ),
          body: SafeArea(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
              children: [
                // 1. Current Template Overview Card
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: colors.surfaceSecondary,
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: colors.border),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                            decoration: BoxDecoration(
                              color: colors.primary.withValues(alpha: 0.15),
                              borderRadius: BorderRadius.circular(8),
                            ),
                            child: Text(
                              'CURRENT SETUP',
                              style: TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.w800,
                                color: colors.primary,
                                letterSpacing: 0.5,
                              ),
                            ),
                          ),
                          const Spacer(),
                          if (overridesCount > 0)
                            Text(
                              ' override active',
                              style: TextStyle(
                                fontSize: 12,
                                fontWeight: FontWeight.w700,
                                color: colors.primary,
                              ),
                            ),
                        ],
                      ),
                      const SizedBox(height: 10),
                      Text(
                        baseTmpl.name,
                        style: theme.textTheme.titleLarge?.copyWith(
                          fontWeight: FontWeight.w800,
                          color: colors.foreground,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        baseTmpl.subtitle,
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: colors.foregroundSecondary,
                        ),
                      ),
                      const SizedBox(height: 14),
                      TemplateCompositePreview(
                        template: baseTmpl,
                        isSelected: true,
                        height: 190,
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 24),

                // 2. Explore All 6 Full Templates
                Text(
                  'Explore Templates',
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w800,
                    color: colors.foreground,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  'Apply an entire visual layout or explore individual components.',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: colors.foregroundSecondary,
                  ),
                ),
                const SizedBox(height: 14),

                ...ChatyTemplateRegistry.list.map((tmpl) {
                  final isBase = config.baseTemplate == tmpl.id;

                  return Container(
                    margin: const EdgeInsets.only(bottom: 16),
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: colors.surfaceSecondary,
                      borderRadius: BorderRadius.circular(20),
                      border: Border.all(
                        color: isBase ? colors.primary : colors.border,
                        width: isBase ? 2.0 : 1.0,
                      ),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    tmpl.name,
                                    style: theme.textTheme.titleMedium?.copyWith(
                                      fontWeight: FontWeight.w800,
                                      color: colors.foreground,
                                    ),
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    tmpl.description,
                                    style: theme.textTheme.bodySmall?.copyWith(
                                      color: colors.foregroundSecondary,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 14),
                        TemplateCompositePreview(
                          template: tmpl,
                          isSelected: isBase,
                          height: 180,
                        ),
                        const SizedBox(height: 14),
                        Row(
                          children: [
                            Expanded(
                              child: FilledButton(
                                style: FilledButton.styleFrom(
                                  backgroundColor: isBase ? colors.surfaceElevated : colors.primary,
                                  foregroundColor: isBase ? colors.foregroundSecondary : Colors.white,
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(12),
                                  ),
                                ),
                                onPressed: isBase && overridesCount == 0
                                    ? null
                                    : () => _applyFullTemplate(context, templateController, tmpl),
                                child: Text(isBase && overridesCount == 0
                                    ? 'Currently Used'
                                    : 'Apply Complete Template'),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  );
                }),

                const SizedBox(height: 16),

                // 3. Customize By Component (The Granular Override System)
                Text(
                  'Customize by Component',
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w800,
                    color: colors.foreground,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  'Mix and match components from different templates independently.',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: colors.foregroundSecondary,
                  ),
                ),
                const SizedBox(height: 12),

                ...TemplateComponentType.values.map((component) {
                  final effectiveId = templateController.resolveTemplateFor(component);
                  final isOverridden = config.isOverridden(component);
                  final effectiveTmpl = ChatyTemplateRegistry.get(effectiveId);

                  return Container(
                    margin: const EdgeInsets.only(bottom: 8),
                    decoration: BoxDecoration(
                      color: colors.surfaceSecondary,
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(color: colors.border),
                    ),
                    child: ListTile(
                      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                      leading: Container(
                        width: 38,
                        height: 38,
                        decoration: BoxDecoration(
                          color: colors.primary.withValues(alpha: 0.1),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Icon(component.icon, color: colors.primary, size: 20),
                      ),
                      title: Text(
                        component.title,
                        style: TextStyle(fontWeight: FontWeight.w700, fontSize: 14, color: colors.foreground),
                      ),
                      subtitle: Text(
                        '${effectiveTmpl.name} ${isOverridden ? "• (Customized)" : "• (From base)"}',
                        style: TextStyle(
                          fontSize: 12,
                          color: isOverridden ? colors.primary : colors.foregroundSecondary,
                          fontWeight: isOverridden ? FontWeight.w600 : FontWeight.w400,
                        ),
                      ),
                      trailing: Icon(Icons.chevron_right_rounded, color: colors.foregroundSecondary),
                      onTap: () {
                        Navigator.of(context).push(
                          MaterialPageRoute<void>(
                            builder: (_) => ComponentOverrideScreen(component: component),
                          ),
                        );
                      },
                    ),
                  );
                }),
              ],
            ),
          ),
        );
      },
    );
  }

  void _applyFullTemplate(
    BuildContext context,
    TemplateController controller,
    ChatyTemplateDefinition template,
  ) {
    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('Apply  Layout?'),
        content: Text(
          'This will apply the  structural layout across all components.\n\nYour account data, chats, privacy, security, and colors are not affected.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () {
              Navigator.of(ctx).pop();
              controller.applyFullTemplate(
                template.id,
                appearanceController: locator<AppearanceVariantController>(),
                preferencesController: locator<ChatyPreferencesController>(),
              );
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(content: Text('Applied full  template')),
              );
            },
            child: const Text('Apply'),
          ),
        ],
      ),
    );
  }

  void _confirmReset(
    BuildContext context,
    TemplateController controller,
  ) {
    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Reset Template Configuration?'),
        content: const Text(
          'This will reset your structural template and all component overrides back to the default Message First layout.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () {
              Navigator.of(ctx).pop();
              controller.resetToDefaults(
                appearanceController: locator<AppearanceVariantController>(),
                preferencesController: locator<ChatyPreferencesController>(),
              );
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Templates reset to default')),
              );
            },
            child: const Text('Reset'),
          ),
        ],
      ),
    );
  }
}
