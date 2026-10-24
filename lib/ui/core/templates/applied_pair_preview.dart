import 'package:flutter/material.dart';

import '../theme/theme_config.dart';
import '../theme/theme_pair_preview.dart';
import 'applied_preview_model.dart';
import 'template_models.dart';

/// Home and Conversation miniatures for a palette plus a template
/// configuration. Component specs are resolved with the app's own precedence
/// (override over base template) through [AppliedPreviewModel], so the
/// miniatures follow the same configuration Apply produces. Pure and
/// read-only: it changes no setting.
class AppliedPairPreview extends StatelessWidget {
  const AppliedPairPreview({
    super.key,
    required this.palette,
    required this.config,
    required this.applyTemplateConversation,
  });

  final ThemeConfig palette;
  final UserTemplateConfiguration config;
  final bool applyTemplateConversation;

  @override
  Widget build(BuildContext context) {
    final model = AppliedPreviewModel.resolve(
      palette: palette,
      config: config,
      applyTemplateConversation: applyTemplateConversation,
    );
    return Center(
      child: ThemePairPreview(themeConfig: model.theme, layout: model.layout),
    );
  }
}
