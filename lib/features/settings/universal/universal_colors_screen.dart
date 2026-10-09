import 'package:flutter/material.dart';

import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart';
import '../../../ui/core/design_system/gb_design_system.dart';

class UniversalColorsScreen extends StatefulWidget {
  final ChatyPreferencesController preferencesController;

  const UniversalColorsScreen({
    super.key,
    required this.preferencesController,
  });

  @override
  State<UniversalColorsScreen> createState() => _UniversalColorsScreenState();
}

class _UniversalColorsScreenState extends State<UniversalColorsScreen> {
  void _pickColor({
    required String title,
    required int? currentColor,
    required ValueChanged<int?> onColorSelected,
  }) {
    final theme = locator<ThemeController>().globalTheme;
    final initial = currentColor != null ? Color(currentColor) : theme.accentColor;
    GbColorPickerModal.show(
      context,
      title: title,
      currentColor: initial,
      onColorChanged: (c) {
        onColorSelected(c.value);
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final themeController = locator<ThemeController>();
    return ListenableBuilder(
      listenable: Listenable.merge([widget.preferencesController, themeController]),
      builder: (context, _) {
        final theme = themeController.globalTheme;
        final prefs = widget.preferencesController.universal;

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
              'Colors',
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
                      icon: Icons.tv_rounded,
                      title: 'Universal Color',
                      subtitle: 'Changes Color everywhere, including UI',
                      colorValue: prefs.universalColor,
                      onColorTap: () => _pickColor(
                        title: 'Universal Color',
                        currentColor: prefs.universalColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateUniversal(
                            prefs.copyWith(universalColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.text_fields_rounded,
                      title: 'Universal ActionBar Text Color',
                      subtitle: 'Changes the color of the Actionbar on all screens',
                      colorValue: prefs.universalActionBarTextColor,
                      onColorTap: () => _pickColor(
                        title: 'ActionBar Text Color',
                        currentColor: prefs.universalActionBarTextColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateUniversal(
                            prefs.copyWith(universalActionBarTextColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.format_color_fill_rounded,
                      title: 'Background',
                      subtitle: 'Change Color of Background. Default is White/Theme',
                      colorValue: prefs.backgroundColor,
                      onColorTap: () => _pickColor(
                        title: 'Background Color',
                        currentColor: prefs.backgroundColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateUniversal(
                            prefs.copyWith(backgroundColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.dashboard_customize_rounded,
                      title: 'List Background (One UI ONLY)',
                      subtitle: 'Change Background of list Chats in One UI.',
                      colorValue: prefs.listBackgroundColor,
                      onColorTap: () => _pickColor(
                        title: 'List Background Color',
                        currentColor: prefs.listBackgroundColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateUniversal(
                            prefs.copyWith(listBackgroundColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.phone_android_rounded,
                      title: 'Status Bar',
                      subtitle: 'Change Color of StatusBar on Every Screen',
                      colorValue: prefs.statusBarColor,
                      onColorTap: () => _pickColor(
                        title: 'Status Bar Color',
                        currentColor: prefs.statusBarColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateUniversal(
                            prefs.copyWith(statusBarColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.navigation_rounded,
                      title: 'Navigation Bar',
                      subtitle: 'Change Color of NavigationBar on Every Screen',
                      colorValue: prefs.navigationBarColor,
                      onColorTap: () => _pickColor(
                        title: 'Navigation Bar Color',
                        currentColor: prefs.navigationBarColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateUniversal(
                            prefs.copyWith(navigationBarColor: val),
                          );
                        },
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
