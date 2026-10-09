import 'package:flutter/material.dart';

import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart';
import '../../../ui/core/design_system/gb_design_system.dart';
import 'universal_colors_screen.dart';
import 'universal_styles_screen.dart';
import 'hide_media_screen.dart';
import 'backup_and_restore_screen.dart';
import 'universal_general_settings_screen.dart';

class UniversalScreen extends StatelessWidget {
  final ChatyPreferencesController preferencesController;

  const UniversalScreen({
    super.key,
    required this.preferencesController,
  });

  void _navigate(BuildContext context, Widget screen) {
    Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => screen),
    );
  }

  @override
  Widget build(BuildContext context) {
    final themeController = locator<ThemeController>();
    return ListenableBuilder(
      listenable: themeController,
      builder: (context, _) {
        final theme = themeController.globalTheme;

        return Scaffold(
          backgroundColor: theme.backgroundColor,
          appBar: AppBar(
            automaticallyImplyLeading: false,
            backgroundColor: theme.surfaceColor,
            elevation: 0,
            leading: const Padding(
              padding: EdgeInsets.all(8.0),
              child: ChatyBackButton(),
            ),
            title: Text(
              'Universal',
              style: TextStyle(
                color: theme.primaryTextColor,
                fontSize: 19 * theme.fontScale,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
          body: SafeArea(
            top: false,
            child: ListView(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
              children: [
                GbCardContainer(
                  children: [
                    GbSettingRow(
                      icon: Icons.palette_rounded,
                      title: 'Colors',
                      subtitle: 'Universal Color, Actionbar, Background, Bars',
                      hasSubScreen: true,
                      onTap: () => _navigate(
                        context,
                        UniversalColorsScreen(preferencesController: preferencesController),
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.style_rounded,
                      title: 'Styles (Look and feel)',
                      subtitle: 'Launcher icons, Emoji variant, Notification icon, Font style',
                      hasSubScreen: true,
                      onTap: () => _navigate(
                        context,
                        UniversalStylesScreen(preferencesController: preferencesController),
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.visibility_off_rounded,
                      title: 'Hide Media from gallery',
                      subtitle: 'Photos, Videos, GIFs',
                      hasSubScreen: true,
                      onTap: () => _navigate(
                        context,
                        HideMediaScreen(preferencesController: preferencesController),
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.settings_backup_restore_rounded,
                      title: 'Backup and restore',
                      subtitle: 'Chaty Data, Media, Clear cache',
                      hasSubScreen: true,
                      onTap: () => _navigate(
                        context,
                        BackupAndRestoreScreen(preferencesController: preferencesController),
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.tune_rounded,
                      title: 'Settings',
                      subtitle: 'Translation, Cards, Audio notifications, Image mods',
                      hasSubScreen: true,
                      onTap: () => _navigate(
                        context,
                        UniversalGeneralSettingsScreen(preferencesController: preferencesController),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}
