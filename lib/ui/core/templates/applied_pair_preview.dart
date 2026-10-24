import 'package:flutter/material.dart';

import '../../../injection/locator.dart';
import '../controllers/appearance_variant_controller.dart';
import '../controllers/preferences_controller.dart';
import '../theme/theme_config.dart';
import '../theme/theme_pair_preview.dart';
import 'applied_preview_model.dart';
import 'template_controller.dart';
import 'template_models.dart';

/// Layout of the setup that is active right now: the active template config
/// (with component overrides) plus the user's saved bottom bar style, Home UI
/// preset, stories choice and hidden Tasks/Updates tabs. Read-only.
PreviewLayout currentSetupLayout() {
  final prefs = locator<ChatyPreferencesController>();
  return PreviewLayout.resolve(
    locator<TemplateController>().config,
    bottomBarStyle: locator<AppearanceVariantController>().bottomBarStyle,
    hiddenDestinationIds: _hiddenDestinations(prefs),
    homeStylePreset: prefs.home.homeStyle,
    showStories: prefs.home.enableStoriesStrip,
  );
}

/// Layout that "Apply Complete Template" produces for [id]: the template's own
/// specs with component overrides cleared and destination customisation reset.
/// Hidden Tasks/Updates tabs stay hidden because Apply does not touch them.
PreviewLayout appliedTemplateLayout(ChatyTemplateId id) {
  return PreviewLayout.resolve(
    UserTemplateConfiguration(baseTemplate: id, componentOverrides: const {}),
    hiddenDestinationIds: _hiddenDestinations(
      locator<ChatyPreferencesController>(),
    ),
  );
}

Set<String> _hiddenDestinations(ChatyPreferencesController prefs) => {
  if (prefs.gbBool('nav_hide_tasks')) ChatyNavigationDestinationIds.tasks,
  if (prefs.gbBool('nav_hide_updates')) ChatyNavigationDestinationIds.updates,
};

/// Home and Conversation miniatures for a palette plus a resolved
/// [PreviewLayout]. Pure and read-only: it changes no setting.
class AppliedPairPreview extends StatelessWidget {
  const AppliedPairPreview({
    super.key,
    required this.palette,
    required this.layout,
    required this.applyTemplateConversation,
  });

  final ThemeConfig palette;
  final PreviewLayout layout;
  final bool applyTemplateConversation;

  @override
  Widget build(BuildContext context) {
    final theme = applyTemplateConversation
        ? themeWithTemplateConversation(palette, layout.conversation)
        : palette;
    return Center(child: ThemePairPreview(themeConfig: theme, layout: layout));
  }
}
