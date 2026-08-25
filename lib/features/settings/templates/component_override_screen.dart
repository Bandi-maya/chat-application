import 'package:flutter/material.dart';
import '../../../injection/locator.dart';
import '../../../ui/core/controllers/appearance_variant_controller.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart';
import '../../../ui/core/templates/template_controller.dart';
import '../../../ui/core/templates/template_models.dart';
import '../../../ui/core/templates/template_registry.dart';

/// Screen allowing the user to compare and override a specific template component (e.g. Navigation, Composer).
class ComponentOverrideScreen extends StatelessWidget {
  final TemplateComponentType component;

  const ComponentOverrideScreen({super.key, required this.component});

  @override
  Widget build(BuildContext context) {
    final templateController = locator<TemplateController>();
    final colors = context.colors;
    final theme = Theme.of(context);

    return ListenableBuilder(
      listenable: templateController,
      builder: (context, _) {
        final currentEffective = templateController.resolveTemplateFor(
          component,
        );
        final isOverridden = templateController.config.isOverridden(component);
        final baseTemplate = templateController.baseTemplate;

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
            title: Text(' Templates'),
            actions: [
              if (isOverridden)
                TextButton(
                  onPressed: () {
                    templateController.removeComponentOverride(
                      component,
                      appearanceController:
                          locator<AppearanceVariantController>(),
                      preferencesController:
                          locator<ChatyPreferencesController>(),
                    );
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('Reset  to base template ()')),
                    );
                  },
                  child: const Text('Reset'),
                ),
            ],
          ),
          body: SafeArea(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
              children: [
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: colors.surfaceSecondary,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: colors.border),
                  ),
                  child: Row(
                    children: [
                      Container(
                        width: 40,
                        height: 40,
                        decoration: BoxDecoration(
                          color: colors.primary.withValues(alpha: 0.12),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Icon(
                          component.icon,
                          color: colors.primary,
                          size: 22,
                        ),
                      ),
                      const SizedBox(width: 14),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              component.title,
                              style: theme.textTheme.titleMedium?.copyWith(
                                fontWeight: FontWeight.w700,
                                color: colors.foreground,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              component.description,
                              style: theme.textTheme.bodySmall?.copyWith(
                                color: colors.foregroundSecondary,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 20),
                Text(
                  'Select Variant for ',
                  style: theme.textTheme.labelLarge?.copyWith(
                    fontWeight: FontWeight.w800,
                    color: colors.foreground,
                  ),
                ),
                const SizedBox(height: 12),
                ...ChatyTemplateRegistry.list.map((tmpl) {
                  final isCurrentlyUsed = currentEffective == tmpl.id;
                  final isFromBase = !isOverridden && baseTemplate == tmpl.id;

                  return Container(
                    margin: const EdgeInsets.only(bottom: 14),
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: colors.surfaceSecondary,
                      borderRadius: BorderRadius.circular(18),
                      border: Border.all(
                        color: isCurrentlyUsed ? colors.primary : colors.border,
                        width: isCurrentlyUsed ? 2.0 : 1.0,
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
                                  Row(
                                    children: [
                                      Text(
                                        tmpl.name,
                                        style: theme.textTheme.titleSmall
                                            ?.copyWith(
                                              fontWeight: FontWeight.w800,
                                              color: colors.foreground,
                                            ),
                                      ),
                                      if (isCurrentlyUsed) ...[
                                        const SizedBox(width: 8),
                                        Container(
                                          padding: const EdgeInsets.symmetric(
                                            horizontal: 8,
                                            vertical: 2,
                                          ),
                                          decoration: BoxDecoration(
                                            color: colors.primary.withValues(
                                              alpha: 0.15,
                                            ),
                                            borderRadius: BorderRadius.circular(
                                              6,
                                            ),
                                          ),
                                          child: Text(
                                            isFromBase
                                                ? 'Base Template'
                                                : 'Active Override',
                                            style: TextStyle(
                                              fontSize: 10,
                                              fontWeight: FontWeight.w700,
                                              color: colors.primary,
                                            ),
                                          ),
                                        ),
                                      ],
                                    ],
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    tmpl.subtitle,
                                    style: theme.textTheme.bodySmall?.copyWith(
                                      color: colors.foregroundSecondary,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        _buildComponentSnippet(context, tmpl, colors),
                        const SizedBox(height: 14),
                        SizedBox(
                          width: double.infinity,
                          child: FilledButton(
                            style: FilledButton.styleFrom(
                              backgroundColor: isCurrentlyUsed
                                  ? colors.surfaceElevated
                                  : colors.primary,
                              foregroundColor: isCurrentlyUsed
                                  ? colors.foregroundSecondary
                                  : Colors.white,
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12),
                              ),
                            ),
                            onPressed: isCurrentlyUsed
                                ? null
                                : () {
                                    templateController.applyComponent(
                                      component: component,
                                      templateId: tmpl.id,
                                      appearanceController:
                                          locator<
                                            AppearanceVariantController
                                          >(),
                                      preferencesController:
                                          locator<ChatyPreferencesController>(),
                                    );
                                    ScaffoldMessenger.of(context).showSnackBar(
                                      SnackBar(
                                        content: Text('Applied  for  only'),
                                      ),
                                    );
                                  },
                            child: Text(
                              isCurrentlyUsed
                                  ? 'Currently Active'
                                  : 'Use This ',
                            ),
                          ),
                        ),
                      ],
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

  Widget _buildComponentSnippet(
    BuildContext context,
    ChatyTemplateDefinition tmpl,
    AppColors colors,
  ) {
    switch (component) {
      case TemplateComponentType.navigation:
        return Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          decoration: BoxDecoration(
            color: colors.surface,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: colors.border),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              Icon(Icons.chat_bubble_rounded, size: 20, color: colors.primary),
              Icon(
                Icons.auto_stories_rounded,
                size: 20,
                color: colors.foregroundSecondary,
              ),
              if (tmpl.navigation.hasCenterAction)
                Container(
                  width: 32,
                  height: 32,
                  decoration: BoxDecoration(
                    color: colors.primary,
                    shape: BoxShape.circle,
                  ),
                  child: Icon(
                    tmpl.navigation.centerActionIcon ??
                        Icons.camera_alt_rounded,
                    size: 18,
                    color: Colors.white,
                  ),
                ),
              Icon(
                Icons.call_rounded,
                size: 20,
                color: colors.foregroundSecondary,
              ),
              Icon(
                Icons.person_rounded,
                size: 20,
                color: colors.foregroundSecondary,
              ),
            ],
          ),
        );

      case TemplateComponentType.composer:
        return Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          decoration: BoxDecoration(
            color: colors.surface,
            borderRadius: BorderRadius.circular(tmpl.composer.cornerRadius),
            border: Border.all(color: colors.border),
          ),
          child: Row(
            children: [
              Icon(
                Icons.emoji_emotions_outlined,
                size: 20,
                color: colors.foregroundSecondary,
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  'Message ()...',
                  style: TextStyle(
                    fontSize: 12,
                    color: colors.foregroundSecondary,
                  ),
                ),
              ),
              if (tmpl.composer.showCameraShortcut) ...[
                Icon(
                  Icons.camera_alt_outlined,
                  size: 18,
                  color: colors.foregroundSecondary,
                ),
                const SizedBox(width: 8),
              ],
              Icon(Icons.send_rounded, size: 18, color: colors.primary),
            ],
          ),
        );

      case TemplateComponentType.conversation:
        return Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: colors.surface,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.end,
            children: [
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 8,
                ),
                decoration: BoxDecoration(
                  color: colors.primary,
                  borderRadius: BorderRadius.circular(
                    tmpl.conversation.bubbleCornerRadius,
                  ),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      ' bubble',
                      style: const TextStyle(
                        fontSize: 11,
                        color: Colors.white,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(width: 6),
                    const Icon(
                      Icons.done_all_rounded,
                      size: 12,
                      color: Colors.white70,
                    ),
                  ],
                ),
              ),
            ],
          ),
        );

      case TemplateComponentType.home:
      case TemplateComponentType.chatList:
      case TemplateComponentType.updates:
      case TemplateComponentType.profile:
      case TemplateComponentType.calls:
        return Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          decoration: BoxDecoration(
            color: colors.surface,
            borderRadius: BorderRadius.circular(10),
          ),
          child: Text(
            ' configuration ()',
            style: TextStyle(fontSize: 11, color: colors.foregroundSecondary),
          ),
        );
    }
  }
}
