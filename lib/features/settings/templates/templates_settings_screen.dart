import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
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
class TemplatesSettingsScreen extends StatefulWidget {
  const TemplatesSettingsScreen({super.key});

  @override
  State<TemplatesSettingsScreen> createState() => _TemplatesSettingsScreenState();
}

class _TemplatesSettingsScreenState extends State<TemplatesSettingsScreen> {
  final TextEditingController _searchController = TextEditingController();
  String _query = '';

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

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
        final query = _query;
        final matchingTemplates = ChatyTemplateRegistry.list.where((template) {
          if (query.isEmpty) return true;
          return template.name.toLowerCase().contains(query) ||
              template.subtitle.toLowerCase().contains(query) ||
              template.description.toLowerCase().contains(query);
        }).toList(growable: false);
        final matchingComponents = TemplateComponentType.values.where((component) {
          if (query.isEmpty) return true;
          return component.title.toLowerCase().contains(query) ||
              component.description.toLowerCase().contains(query);
        }).toList(growable: false);

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
              IconButton(
                tooltip: 'Export template profile',
                icon: const Icon(Icons.file_download_outlined),
                onPressed: () => _exportConfiguration(context, templateController),
              ),
              IconButton(
                tooltip: 'Import template profile',
                icon: const Icon(Icons.file_upload_outlined),
                onPressed: () => _importConfiguration(context, templateController),
              ),
              if (overridesCount > 0 ||
                  config.baseTemplate != ChatyTemplateId.messageFirst)
                IconButton(
                  tooltip: 'Reset All Templates',
                  icon: const Icon(Icons.restart_alt_rounded),
                  onPressed: () => _confirmReset(context, templateController),
                ),
            ],
          ),
          body: SafeArea(
            child: Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 1040),
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
                            padding: const EdgeInsets.symmetric(
                              horizontal: 10,
                              vertical: 4,
                            ),
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
                              '$overridesCount component override${overridesCount == 1 ? '' : 's'} active',
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

                const SizedBox(height: 16),

                TextField(
                  controller: _searchController,
                  onChanged: (value) {
                    setState(() => _query = value.trim().toLowerCase());
                  },
                  textInputAction: TextInputAction.search,
                  decoration: InputDecoration(
                    hintText: 'Search templates and components',
                    prefixIcon: const Icon(Icons.search_rounded),
                    suffixIcon: _query.isEmpty
                        ? null
                        : IconButton(
                            tooltip: 'Clear search',
                            onPressed: () {
                              _searchController.clear();
                              setState(() => _query = '');
                            },
                            icon: const Icon(Icons.close_rounded),
                          ),
                    filled: true,
                    fillColor: colors.surfaceSecondary,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(14),
                      borderSide: BorderSide(color: colors.border),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(14),
                      borderSide: BorderSide(color: colors.border),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(14),
                      borderSide: BorderSide(color: colors.primary, width: 1.4),
                    ),
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

                ...matchingTemplates.map((tmpl) {
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
                                    style: theme.textTheme.titleMedium
                                        ?.copyWith(
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
                                  backgroundColor: isBase
                                      ? colors.surfaceElevated
                                      : colors.primary,
                                  foregroundColor: isBase
                                      ? colors.foregroundSecondary
                                      : Colors.white,
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(12),
                                  ),
                                ),
                                onPressed: isBase && overridesCount == 0
                                    ? null
                                    : () => _applyFullTemplate(
                                        context,
                                        templateController,
                                        tmpl,
                                      ),
                                child: Text(
                                  isBase && overridesCount == 0
                                      ? 'Currently Used'
                                      : 'Apply Complete Template',
                                ),
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

                ...matchingComponents.map((component) {
                  final effectiveId = templateController.resolveTemplateFor(
                    component,
                  );
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
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 4,
                      ),
                      leading: Container(
                        width: 38,
                        height: 38,
                        decoration: BoxDecoration(
                          color: colors.primary.withValues(alpha: 0.1),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Icon(
                          component.icon,
                          color: colors.primary,
                          size: 20,
                        ),
                      ),
                      title: Text(
                        component.title,
                        style: TextStyle(
                          fontWeight: FontWeight.w700,
                          fontSize: 14,
                          color: colors.foreground,
                        ),
                      ),
                      subtitle: Text(
                        '${effectiveTmpl.name} ${isOverridden ? "• (Customized)" : "• (From base)"}',
                        style: TextStyle(
                          fontSize: 12,
                          color: isOverridden
                              ? colors.primary
                              : colors.foregroundSecondary,
                          fontWeight: isOverridden
                              ? FontWeight.w600
                              : FontWeight.w400,
                        ),
                      ),
                      trailing: Icon(
                        Icons.chevron_right_rounded,
                        color: colors.foregroundSecondary,
                      ),
                      onTap: () {
                        Navigator.of(context).push(
                          MaterialPageRoute<void>(
                            builder: (_) =>
                                ComponentOverrideScreen(component: component),
                          ),
                        );
                      },
                    ),
                  );
                }),
                if (_query.isNotEmpty &&
                    matchingTemplates.isEmpty &&
                    matchingComponents.isEmpty)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 32),
                    child: Column(
                      children: [
                        Icon(
                          Icons.search_off_rounded,
                          size: 34,
                          color: colors.foregroundSecondary,
                        ),
                        const SizedBox(height: 10),
                        Text(
                          'No template or component found',
                          style: TextStyle(
                            color: colors.foreground,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'Try a template name such as Visual Social or search for composer, chat list, profile or calls.',
                          textAlign: TextAlign.center,
                          style: TextStyle(color: colors.foregroundSecondary),
                        ),
                      ],
                    ),
                  ),
                  ],
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  Future<void> _exportConfiguration(
    BuildContext context,
    TemplateController controller,
  ) async {
    final payload = jsonEncode(<String, dynamic>{
      'kind': 'chaty_template_profile',
      'schemaVersion': 1,
      'configuration': controller.config.toMap(),
    });
    await Clipboard.setData(ClipboardData(text: payload));
    if (!context.mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        const SnackBar(
          content: Text('Template profile copied. It contains appearance settings only.'),
        ),
      );
  }

  Future<void> _importConfiguration(
    BuildContext context,
    TemplateController controller,
  ) async {
    final input = TextEditingController();
    final accepted = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Import template profile'),
        content: SizedBox(
          width: 520,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Paste a Chaty template profile JSON. Only template and component style choices are imported; chats, account data, credentials and keys are never included.',
              ),
              const SizedBox(height: 12),
              TextField(
                controller: input,
                minLines: 4,
                maxLines: 8,
                decoration: const InputDecoration(
                  hintText: 'Paste template profile JSON',
                  border: OutlineInputBorder(),
                ),
              ),
              Align(
                alignment: Alignment.centerRight,
                child: TextButton.icon(
                  onPressed: () async {
                    final clipboard = await Clipboard.getData('text/plain');
                    if (clipboard?.text != null) input.text = clipboard!.text!;
                  },
                  icon: const Icon(Icons.content_paste_rounded),
                  label: const Text('Paste clipboard'),
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Validate & Import'),
          ),
        ],
      ),
    );

    if (accepted != true) {
      input.dispose();
      return;
    }

    try {
      final decoded = jsonDecode(input.text);
      if (decoded is! Map ||
          decoded['kind'] != 'chaty_template_profile' ||
          decoded['schemaVersion'] != 1 ||
          decoded['configuration'] is! Map) {
        throw const FormatException('Unsupported template profile format.');
      }
      final raw = Map<String, dynamic>.from(decoded['configuration'] as Map);
      final baseKey = raw['base'];
      if (!ChatyTemplateId.values.any((template) => template.key == baseKey)) {
        throw const FormatException('Unknown base template.');
      }
      final rawOverrides = raw['overrides'];
      if (rawOverrides != null && rawOverrides is! Map) {
        throw const FormatException('Invalid component overrides.');
      }
      if (rawOverrides is Map) {
        for (final entry in rawOverrides.entries) {
          final knownComponent = TemplateComponentType.values.any(
            (component) => component.name == entry.key,
          );
          final knownTemplate = ChatyTemplateId.values.any(
            (template) => template.key == entry.value,
          );
          if (!knownComponent || !knownTemplate) {
            throw const FormatException('Unknown component or template.');
          }
        }
      }

      final rawPrimary = raw['navPrimary'];
      final rawOverflow = raw['navOverflow'];
      if ((rawPrimary == null) != (rawOverflow == null)) {
        throw const FormatException('Both primary and overflow destinations are required.');
      }
      if (rawPrimary != null) {
        if (rawPrimary is! List || rawOverflow is! List) {
          throw const FormatException('Invalid navigation destination lists.');
        }
        final primary = rawPrimary.whereType<String>().toList();
        final overflow = rawOverflow.whereType<String>().toList();
        final primarySet = primary.toSet();
        final overflowSet = overflow.toSet();
        final assigned = <String>{...primary, ...overflow};
        if (primary.length != rawPrimary.length ||
            overflow.length != rawOverflow.length ||
            primary.isEmpty ||
            primary.length > 4 ||
            primarySet.length != primary.length ||
            overflowSet.length != overflow.length ||
            primarySet.intersection(overflowSet).isNotEmpty ||
            primary.any((id) => !ChatyNavigationDestinationIds.known.contains(id)) ||
            overflow.any((id) => !ChatyNavigationDestinationIds.known.contains(id)) ||
            assigned.length != ChatyNavigationDestinationIds.all.length ||
            !assigned.containsAll(ChatyNavigationDestinationIds.all)) {
          throw const FormatException(
            'Navigation profile must assign every supported destination exactly once.',
          );
        }
      }

      final configuration = UserTemplateConfiguration.fromMap(raw);
      await controller.applyConfiguration(
        configuration,
        appearanceController: locator<AppearanceVariantController>(),
        preferencesController: locator<ChatyPreferencesController>(),
        themeController: locator<ThemeController>(),
      );
      if (!context.mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          const SnackBar(content: Text('Template profile imported.')),
        );
    } catch (_) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          const SnackBar(
            content: Text('Import failed. Check the template JSON and try again.'),
          ),
        );
    } finally {
      input.dispose();
    }
  }

  void _applyFullTemplate(
    BuildContext context,
    TemplateController controller,
    ChatyTemplateDefinition template,
  ) {
    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('Apply ${template.name}?'),
        content: Text(
          'This applies the ${template.name} structural layout across all supported components.\n\nYour account data, chats, privacy, security, and color palette are preserved.',
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
                themeController: locator<ThemeController>(),
              );
              ScaffoldMessenger.of(
                context,
              ).showSnackBar(SnackBar(content: Text('Applied ${template.name} template.')));
            },
            child: const Text('Apply'),
          ),
        ],
      ),
    );
  }

  void _confirmReset(BuildContext context, TemplateController controller) {
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
                themeController: locator<ThemeController>(),
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
