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
            title: Text('${component.title} Template'),
            actions: [
              if (isOverridden)
                TextButton(
                  onPressed: () async {
                    try {
                      await templateController.removeComponentOverride(
                        component,
                        appearanceController:
                            locator<AppearanceVariantController>(),
                        preferencesController:
                            locator<ChatyPreferencesController>(),
                        themeController: locator<ThemeController>(),
                      );
                      if (!context.mounted) return;
                      ScaffoldMessenger.of(context)
                        ..hideCurrentSnackBar()
                        ..showSnackBar(
                          SnackBar(
                            content: Text(
                              'Reset ${component.title} to the base template.',
                            ),
                          ),
                        );
                    } catch (error) {
                      if (!context.mounted) return;
                      ScaffoldMessenger.of(context)
                        ..hideCurrentSnackBar()
                        ..showSnackBar(
                          SnackBar(
                            content: Text(
                              error.toString().replaceFirst('Bad state: ', ''),
                            ),
                          ),
                        );
                    }
                  },
                  child: const Text('Reset'),
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
                                : () async {
                                    try {
                                      await templateController.applyComponent(
                                        component: component,
                                        templateId: tmpl.id,
                                        appearanceController:
                                            locator<
                                              AppearanceVariantController
                                            >(),
                                        preferencesController:
                                            locator<ChatyPreferencesController>(),
                                        themeController:
                                            locator<ThemeController>(),
                                      );
                                      if (!context.mounted) return;
                                      ScaffoldMessenger.of(context)
                                        ..hideCurrentSnackBar()
                                        ..showSnackBar(
                                          SnackBar(
                                            content: Text(
                                              'Applied ${tmpl.name} to ${component.title} only.',
                                            ),
                                          ),
                                        );
                                    } catch (error) {
                                      if (!context.mounted) return;
                                      ScaffoldMessenger.of(context)
                                        ..hideCurrentSnackBar()
                                        ..showSnackBar(
                                          SnackBar(
                                            content: Text(
                                              error.toString().replaceFirst(
                                                'Bad state: ',
                                                '',
                                              ),
                                            ),
                                          ),
                                        );
                                    }
                                  },
                            child: Text(
                              isCurrentlyUsed
                                  ? 'Currently Active'
                                  : 'Use This Template',
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
    final muted = colors.foregroundSecondary;
    final surface = colors.surface;
    final border = colors.border;
    final tiny = TextStyle(
      fontSize: 10.5,
      color: muted,
      fontWeight: FontWeight.w600,
    );

    IconData destinationIcon(String id) => switch (id) {
      ChatyNavigationDestinationIds.chats => Icons.chat_bubble_outline_rounded,
      ChatyNavigationDestinationIds.groups => Icons.groups_outlined,
      ChatyNavigationDestinationIds.updates => Icons.auto_stories_outlined,
      ChatyNavigationDestinationIds.tasks => Icons.checklist_rounded,
      ChatyNavigationDestinationIds.calls => Icons.call_outlined,
      ChatyNavigationDestinationIds.settings => Icons.settings_outlined,
      ChatyNavigationDestinationIds.desktop => Icons.devices_outlined,
      _ => Icons.circle_outlined,
    };

    String destinationLabel(String id) => switch (id) {
      ChatyNavigationDestinationIds.chats => 'Chats',
      ChatyNavigationDestinationIds.groups => 'Groups',
      ChatyNavigationDestinationIds.updates => 'Updates',
      ChatyNavigationDestinationIds.tasks => 'Tasks',
      ChatyNavigationDestinationIds.calls => 'Calls',
      ChatyNavigationDestinationIds.settings => 'Settings',
      ChatyNavigationDestinationIds.desktop => 'Devices',
      _ => id,
    };

    Widget previewSurface({required Widget child, double radius = 14}) {
      return Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: surface,
          borderRadius: BorderRadius.circular(radius),
          border: Border.all(color: border),
        ),
        child: child,
      );
    }

    Widget circleAction(IconData icon, {Color? color, double size = 17}) {
      return Container(
        width: 30,
        height: 30,
        decoration: BoxDecoration(
          color: (color ?? colors.primary).withValues(alpha: 0.10),
          shape: BoxShape.circle,
        ),
        alignment: Alignment.center,
        child: Icon(icon, size: size, color: color ?? colors.primary),
      );
    }

    switch (component) {
      case TemplateComponentType.navigation:
        final destinations = tmpl.navigation.primaryDestinationIds;
        return previewSurface(
          child: Column(
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  for (final id in destinations.take(4))
                    Expanded(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            destinationIcon(id),
                            size: 19,
                            color: id == destinations.firstOrNull
                                ? colors.primary
                                : muted,
                          ),
                          const SizedBox(height: 4),
                          Text(
                            destinationLabel(id),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: tiny.copyWith(
                              color: id == destinations.firstOrNull
                                  ? colors.primary
                                  : muted,
                            ),
                          ),
                        ],
                      ),
                    ),
                ],
              ),
              if (tmpl.navigation.hasCenterAction) ...[
                const SizedBox(height: 10),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      tmpl.navigation.centerActionIcon ??
                          Icons.camera_alt_rounded,
                      size: 18,
                      color: colors.primary,
                    ),
                    const SizedBox(width: 6),
                    Text(
                      tmpl.navigation.centerActionId == 'camera'
                          ? 'Center camera action'
                          : 'Center quick action',
                      style: tiny,
                    ),
                  ],
                ),
              ],
              const SizedBox(height: 8),
              Text(
                '${tmpl.navigation.layout.name} · ${tmpl.navigation.height.toInt()} dp',
                style: tiny,
                textAlign: TextAlign.center,
              ),
            ],
          ),
        );

      case TemplateComponentType.composer:
        final input = Expanded(
          child: Container(
            constraints: const BoxConstraints(minHeight: 38),
            padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 8),
            decoration: BoxDecoration(
              color: colors.background,
              borderRadius: BorderRadius.circular(tmpl.composer.cornerRadius),
              border: Border.all(color: border),
            ),
            child: Row(
              children: [
                Icon(Icons.emoji_emotions_outlined, size: 16, color: muted),
                const SizedBox(width: 6),
                Expanded(child: Text('Message…', style: tiny)),
              ],
            ),
          ),
        );
        final attachment = circleAction(Icons.add_rounded, size: 18);
        final camera = circleAction(Icons.camera_alt_outlined, size: 16);
        final voice = circleAction(
          tmpl.composer.showVoiceLock
              ? Icons.mic_none_rounded
              : Icons.graphic_eq_rounded,
          size: 16,
        );
        final send = circleAction(Icons.arrow_upward_rounded, color: colors.primary);
        if (tmpl.composer.actionPlacement == ComposerActionPlacement.powerRow) {
          return previewSurface(
            child: Column(
              children: [
                Row(children: [input, const SizedBox(width: 8), send]),
                const SizedBox(height: 10),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    attachment,
                    if (tmpl.composer.showCameraShortcut) camera,
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        if (tmpl.composer.showVoiceLock)
                          Icon(Icons.lock_outline_rounded, size: 12, color: muted),
                        const SizedBox(width: 5),
                        Text('Power row', style: tiny),
                      ],
                    ),
                    voice,
                  ],
                ),
              ],
            ),
          );
        }
        return previewSurface(
          child: Row(
            children: [
              if (tmpl.composer.actionPlacement == ComposerActionPlacement.split &&
                  tmpl.composer.showCameraShortcut) ...[
                camera,
                const SizedBox(width: 6),
                attachment,
                const SizedBox(width: 6),
              ] else ...[
                attachment,
                if (tmpl.composer.showCameraShortcut) ...[
                  const SizedBox(width: 6),
                  camera,
                ],
                const SizedBox(width: 6),
              ],
              input,
              const SizedBox(width: 7),
              send,
            ],
          ),
        );

      case TemplateComponentType.conversation:
        return previewSurface(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  circleAction(Icons.person_outline_rounded, size: 15),
                  const SizedBox(width: 7),
                  Text('Conversation preview', style: tiny),
                  const Spacer(),
                  Text('12:04', style: tiny),
                ],
              ),
              const SizedBox(height: 10),
              Align(
                alignment: Alignment.centerRight,
                child: Container(
                  constraints: const BoxConstraints(maxWidth: 240),
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
                      Flexible(
                        child: Text(
                          'Message sent',
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: 11,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                      const SizedBox(width: 6),
                      Icon(
                        Icons.done_all_rounded,
                        size: 13,
                        color: Colors.white.withValues(alpha: 0.82),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 8),
              Text(
                'Bubble: ${tmpl.conversation.bubbleStyle} · Ticks: ${tmpl.conversation.tickStyle}',
                style: tiny,
              ),
              if (tmpl.conversation.showAvatarInGroup)
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Text('Group sender avatars shown', style: tiny),
                ),
            ],
          ),
        );

      case TemplateComponentType.home:
        return previewSurface(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(Icons.chat_bubble_outline_rounded, color: colors.primary, size: 18),
                  const SizedBox(width: 7),
                  Expanded(
                    child: Text(
                      tmpl.home.headerStyle.name,
                      style: TextStyle(
                        color: colors.foreground,
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                  Icon(Icons.search_rounded, color: muted, size: 17),
                  const SizedBox(width: 10),
                  Icon(Icons.more_vert_rounded, color: muted, size: 17),
                ],
              ),
              const SizedBox(height: 10),
              if (tmpl.home.showStoriesStrip)
                Row(
                  children: [
                    for (int i = 0; i < 4; i++) ...[
                      Container(
                        width: 23,
                        height: 23,
                        margin: const EdgeInsets.only(right: 7),
                        decoration: BoxDecoration(
                          color: <Color>[
                            colors.primary.withValues(alpha: 0.25),
                            colors.info.withValues(alpha: 0.25),
                            colors.success.withValues(alpha: 0.25),
                            colors.accent.withValues(alpha: 0.25),
                          ][i],
                          shape: tmpl.home.storiesStyle.toLowerCase() == 'circular'
                              ? BoxShape.circle
                              : BoxShape.rectangle,
                          borderRadius: tmpl.home.storiesStyle.toLowerCase() == 'circular'
                              ? null
                              : BorderRadius.circular(7),
                        ),
                      ),
                    ],
                    Text(tmpl.home.storiesStyle, style: tiny),
                  ],
                )
              else
                Text('Stories strip hidden', style: tiny),
              const SizedBox(height: 8),
              for (int i = 0; i < 2; i++) ...[
                Container(
                  height: tmpl.home.homeStylePreset == 'Compact' ? 20 : 27,
                  margin: const EdgeInsets.only(bottom: 5),
                  padding: const EdgeInsets.symmetric(horizontal: 8),
                  decoration: BoxDecoration(
                    color: colors.background,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Row(
                    children: [
                      circleAction(Icons.person_outline_rounded, size: 13),
                      const SizedBox(width: 7),
                      Expanded(child: Container(height: 4, color: border)),
                    ],
                  ),
                ),
              ],
            ],
          ),
        );

      case TemplateComponentType.chatList:
        final avatar = switch (tmpl.chatList.avatarShape) {
          'squircle' => BorderRadius.circular(8),
          'roundedSquare' => BorderRadius.circular(5),
          _ => BorderRadius.circular(99),
        };
        return previewSurface(
          child: Column(
            children: [
              for (int i = 0; i < 2; i++)
                Padding(
                  padding: EdgeInsets.symmetric(
                    vertical: tmpl.chatList.density == ChatListDensity.compact
                        ? 3
                        : tmpl.chatList.density == ChatListDensity.comfortable
                        ? 8
                        : 5,
                  ),
                  child: Row(
                    children: [
                      Container(
                        width: 34,
                        height: 34,
                        decoration: BoxDecoration(
                          color: i == 0
                              ? colors.primary.withValues(alpha: 0.22)
                              : colors.info.withValues(alpha: 0.22),
                          borderRadius: avatar,
                        ),
                      ),
                      const SizedBox(width: 9),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Container(height: 5, width: 105, color: border),
                            const SizedBox(height: 5),
                            Container(height: 3, width: 150, color: border.withValues(alpha: 0.7)),
                          ],
                        ),
                      ),
                      if (tmpl.chatList.showPresenceBadge)
                        Container(
                          width: 7,
                          height: 7,
                          decoration: BoxDecoration(
                            color: colors.success,
                            shape: BoxShape.circle,
                          ),
                        ),
                    ],
                  ),
                ),
              Text(
                '${tmpl.chatList.density.name} · ${tmpl.chatList.itemHeight.toInt()} dp rows',
                style: tiny,
              ),
            ],
          ),
        );

      case TemplateComponentType.updates:
        return previewSurface(
          child: Column(
            children: [
              if (tmpl.updates.layoutMode == UpdatesLayoutMode.circularRail)
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceAround,
                  children: [
                    for (int i = 0; i < 4; i++)
                      Column(
                        children: [
                          Container(
                            width: 28,
                            height: 28,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              border: Border.all(color: colors.primary, width: 2),
                              color: colors.primary.withValues(alpha: 0.12),
                            ),
                          ),
                          const SizedBox(height: 5),
                          Container(width: 26, height: 3, color: border),
                        ],
                      ),
                  ],
                )
              else if (tmpl.updates.layoutMode == UpdatesLayoutMode.minimalList)
                Column(
                  children: [
                    for (int i = 0; i < 2; i++)
                      Padding(
                        padding: const EdgeInsets.symmetric(vertical: 5),
                        child: Row(
                          children: [
                            circleAction(Icons.auto_stories_outlined, size: 15),
                            const SizedBox(width: 8),
                            Expanded(child: Container(height: 4, color: border)),
                          ],
                        ),
                      ),
                  ],
                )
              else
                Row(
                  children: [
                    for (int i = 0; i < (tmpl.updates.layoutMode == UpdatesLayoutMode.gridTiles ? 3 : 2); i++)
                      Expanded(
                        child: Container(
                          height: 48,
                          margin: const EdgeInsets.only(right: 6),
                          decoration: BoxDecoration(
                            color: colors.primary.withValues(alpha: 0.10),
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(color: border),
                          ),
                        ),
                      ),
                  ],
                ),
              const SizedBox(height: 8),
              Text(
                '${tmpl.updates.layoutMode.name} · audio ${tmpl.updates.enableStatusAudio ? "on" : "off"}',
                style: tiny,
              ),
            ],
          ),
        );

      case TemplateComponentType.profile:
        return previewSurface(
          child: Column(
            children: [
              Container(
                height: tmpl.profile.headerStyle == ProfileHeaderStyle.compactHeader ? 26 : 44,
                decoration: BoxDecoration(
                  color: colors.primary.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
              Transform.translate(
                offset: const Offset(0, -14),
                child: Container(
                  width: 30,
                  height: 30,
                  decoration: BoxDecoration(
                    color: colors.primary.withValues(alpha: 0.2),
                    shape: tmpl.profile.avatarShape == 'circle'
                        ? BoxShape.circle
                        : BoxShape.rectangle,
                    borderRadius: tmpl.profile.avatarShape == 'circle'
                        ? null
                        : BorderRadius.circular(8),
                    border: Border.all(color: surface, width: 2),
                  ),
                ),
              ),
              Text(
                tmpl.profile.headerStyle.name,
                style: tiny,
                textAlign: TextAlign.center,
              ),
              if (tmpl.profile.showStatsGrid) ...[
                const SizedBox(height: 8),
                Row(
                  children: [
                    for (int i = 0; i < 3; i++)
                      Expanded(
                        child: Container(
                          height: 18,
                          margin: const EdgeInsets.symmetric(horizontal: 3),
                          decoration: BoxDecoration(
                            color: colors.background,
                            borderRadius: BorderRadius.circular(6),
                          ),
                        ),
                      ),
                  ],
                ),
              ],
            ],
          ),
        );

      case TemplateComponentType.calls:
        return previewSurface(
          child: Column(
            children: [
              Row(
                children: [
                  circleAction(Icons.call_outlined, size: 15),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Container(width: 85, height: 5, color: border),
                        const SizedBox(height: 5),
                        Container(width: 57, height: 3, color: border.withValues(alpha: 0.7)),
                      ],
                    ),
                  ),
                  Text(tmpl.calls.enableFloatingIsland ? 'Island on' : 'Island off', style: tiny),
                ],
              ),
              const SizedBox(height: 10),
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: Colors.black,
                  borderRadius: BorderRadius.circular(tmpl.calls.controlBarCornerRadius),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceAround,
                  children: [
                    Icons.mic_none_rounded,
                    Icons.volume_up_rounded,
                    Icons.call_end_rounded,
                  ].map((icon) => Icon(icon, size: 15, color: Colors.white)).toList(),
                ),
              ),
            ],
          ),
        );
    }
  }
}
