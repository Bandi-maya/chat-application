import 'package:flutter/material.dart';

import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart';
import '../../../ui/core/design_system/gb_design_system.dart';

class HomeFabSettingsScreen extends StatefulWidget {
  final ChatyPreferencesController preferencesController;

  const HomeFabSettingsScreen({
    super.key,
    required this.preferencesController,
  });

  @override
  State<HomeFabSettingsScreen> createState() => _HomeFabSettingsScreenState();
}

class _HomeFabSettingsScreenState extends State<HomeFabSettingsScreen> {
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
        final prefs = widget.preferencesController.home;

        final normalColor = prefs.fabNormalColor != null
            ? Color(prefs.fabNormalColor!)
            : theme.accentColor;
        final pressedColor = prefs.fabPressedColor != null
            ? Color(prefs.fabPressedColor!)
            : theme.accentColor.withValues(alpha: 0.8);
        final iconColor = prefs.fabIconsColor != null
            ? Color(prefs.fabIconsColor!)
            : Colors.white;

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
              'Floating Action Button',
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
                // Top Live Interactive FAB Preview matching Image 3 FAB screen
                GbCardContainer(
                  children: [
                    GbLivePreviewCard(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(
                                'FAB Interactive Preview',
                                style: TextStyle(
                                  color: theme.primaryTextColor,
                                  fontWeight: FontWeight.w700,
                                  fontSize: 14,
                                ),
                              ),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                                decoration: BoxDecoration(
                                  color: prefs.hideFab
                                      ? Colors.redAccent.withValues(alpha: 0.15)
                                      : normalColor.withValues(alpha: 0.15),
                                  borderRadius: BorderRadius.circular(12),
                                ),
                                child: Text(
                                  prefs.hideFab ? 'Hidden' : 'Visible',
                                  style: TextStyle(
                                    fontSize: 11,
                                    fontWeight: FontWeight.bold,
                                    color: prefs.hideFab ? Colors.redAccent : normalColor,
                                  ),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 16),
                          Container(
                            height: 130,
                            width: double.infinity,
                            decoration: BoxDecoration(
                              color: theme.backgroundColor,
                              borderRadius: BorderRadius.circular(12),
                              border: Border.all(
                                color: Theme.of(context).dividerColor.withValues(alpha: 0.3),
                              ),
                            ),
                            child: Stack(
                              children: [
                                Center(
                                  child: Text(
                                    prefs.hideFab
                                        ? 'FAB is currently hidden'
                                        : 'Speed Dial Actions',
                                    style: TextStyle(
                                      color: theme.secondaryTextColor,
                                      fontSize: 12,
                                    ),
                                  ),
                                ),
                                if (!prefs.hideFab)
                                  Positioned(
                                    right: 16,
                                    bottom: 16,
                                    child: Column(
                                      mainAxisSize: MainAxisSize.min,
                                      crossAxisAlignment: CrossAxisAlignment.end,
                                      children: [
                                        if (!prefs.hideGbwaSettingsFab)
                                          Padding(
                                            padding: const EdgeInsets.only(bottom: 6),
                                            child: CircleAvatar(
                                              radius: 14,
                                              backgroundColor: pressedColor,
                                              child: Icon(Icons.settings, size: 14, color: iconColor),
                                            ),
                                          ),
                                        if (!prefs.hideCutEditFab)
                                          Padding(
                                            padding: const EdgeInsets.only(bottom: 6),
                                            child: CircleAvatar(
                                              radius: 14,
                                              backgroundColor: pressedColor,
                                              child: Icon(Icons.content_cut, size: 14, color: iconColor),
                                            ),
                                          ),
                                        if (!prefs.hideLastSeenFab)
                                          Padding(
                                            padding: const EdgeInsets.only(bottom: 6),
                                            child: CircleAvatar(
                                              radius: 14,
                                              backgroundColor: pressedColor,
                                              child: Icon(Icons.history_toggle_off, size: 14, color: iconColor),
                                            ),
                                          ),
                                        CircleAvatar(
                                          radius: 24,
                                          backgroundColor: normalColor,
                                          child: Icon(
                                            prefs.showMetaAiIcon
                                                ? Icons.psychology_rounded
                                                : Icons.chat_bubble_outline_rounded,
                                            color: iconColor,
                                            size: 24,
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),

                // FAB Main Configuration
                GbCardContainer(
                  children: [
                    GbSwitchTile(
                      icon: Icons.visibility_off_rounded,
                      title: 'Hide FAB',
                      subtitle: 'Remove the floating action button from the home screen',
                      value: prefs.hideFab,
                      onChanged: (val) {
                        widget.preferencesController.updateHome(
                          prefs.copyWith(hideFab: val),
                        );
                      },
                    ),
                    GbSettingRow(
                      icon: Icons.lens_rounded,
                      title: 'Fab normal',
                      subtitle: 'Change Color of Fab Normal',
                      colorHex: prefs.fabNormalColor,
                      onColorTap: () => _pickColor(
                        title: 'Fab Normal Color',
                        currentColor: prefs.fabNormalColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateHome(
                            prefs.copyWith(fabNormalColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.touch_app_rounded,
                      title: 'Fab Pressed',
                      subtitle: 'Change Color of Fab pressed',
                      colorHex: prefs.fabPressedColor,
                      onColorTap: () => _pickColor(
                        title: 'Fab Pressed Color',
                        currentColor: prefs.fabPressedColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateHome(
                            prefs.copyWith(fabPressedColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.palette_outlined,
                      title: 'Fab Icons Color',
                      subtitle: 'Change Color of Fab icons',
                      colorHex: prefs.fabIconsColor,
                      onColorTap: () => _pickColor(
                        title: 'Fab Icons Color',
                        currentColor: prefs.fabIconsColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateHome(
                            prefs.copyWith(fabIconsColor: val),
                          );
                        },
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),

                // Sub FAB Actions
                GbCardContainer(
                  children: [
                    GbSwitchTile(
                      icon: Icons.psychology_rounded,
                      title: 'Show icon Meta AI',
                      subtitle: 'You can hide the Meta AI icon from your home screen',
                      value: prefs.showMetaAiIcon,
                      onChanged: (val) {
                        widget.preferencesController.updateHome(
                          prefs.copyWith(showMetaAiIcon: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.mark_email_unread_rounded,
                      title: 'Hide New Message FAB',
                      subtitle: 'Hide new chat quick button from FAB speed dial',
                      value: prefs.hideNewMessageFab,
                      onChanged: (val) {
                        widget.preferencesController.updateHome(
                          prefs.copyWith(hideNewMessageFab: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.history_toggle_off_rounded,
                      title: 'Hide LastSeen FAB',
                      subtitle: 'Hide quick freeze last seen toggle button',
                      value: prefs.hideLastSeenFab,
                      onChanged: (val) {
                        widget.preferencesController.updateHome(
                          prefs.copyWith(hideLastSeenFab: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.content_cut_rounded,
                      title: 'Hide ✂️ FAB',
                      subtitle: 'Hide quick restart/cutter tool button',
                      value: prefs.hideCutterFab,
                      onChanged: (val) {
                        widget.preferencesController.updateHome(
                          prefs.copyWith(hideCutterFab: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.extension_rounded,
                      title: 'Hide plugins list',
                      subtitle: 'Hide plugins drawer trigger from FAB',
                      value: prefs.hidePluginsList,
                      onChanged: (val) {
                        widget.preferencesController.updateHome(
                          prefs.copyWith(hidePluginsList: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.settings_rounded,
                      title: 'Hide Chaty Settings FAB',
                      subtitle: 'Hide quick Chaty settings gear button from FAB speed dial',
                      value: prefs.hideGbwaSettingsFab,
                      onChanged: (val) {
                        widget.preferencesController.updateHome(
                          prefs.copyWith(hideGbwaSettingsFab: val),
                        );
                      },
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
