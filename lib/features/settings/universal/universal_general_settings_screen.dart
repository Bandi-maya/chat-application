import 'package:flutter/material.dart';

import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart';
import '../../../ui/core/design_system/gb_design_system.dart';

class UniversalGeneralSettingsScreen extends StatelessWidget {
  final ChatyPreferencesController preferencesController;

  const UniversalGeneralSettingsScreen({
    super.key,
    required this.preferencesController,
  });

  void _showTranslateSettingsModal(BuildContext context, ThemeData gbTheme) {
    final prefs = preferencesController.universal;
    GbRadioSelectionModal.show<String>(
      context: context,
      title: 'Translate Option Settings',
      options: ['Server Translation (Google/Cloud)', 'In-app Translation (Offline/Direct)'],
      selectedOption: prefs.translateOption == 'In-app'
          ? 'In-app Translation (Offline/Direct)'
          : 'Server Translation (Google/Cloud)',
      onSelected: (val) {
        preferencesController.updateUniversal(
          prefs.copyWith(
            translateOption: val.startsWith('In-app') ? 'In-app' : 'Server',
          ),
        );
      },
    );
  }

  void _showLanguageModal(BuildContext context, ThemeData gbTheme) {
    final prefs = preferencesController.universal;
    final languages = [
      'Show All',
      'English (US)',
      'Spanish (Español)',
      'Arabic (العربية)',
      'Hindi (हिन्दी)',
      'Portuguese (Português)',
      'French (Français)',
      'German (Deutsch)',
      'Russian (Русский)',
      'Japanese (日本語)',
    ];
    GbRadioSelectionModal.show<String>(
      context: context,
      title: 'Default Translation Language',
      options: languages,
      selectedOption: prefs.translationLanguage,
      onSelected: (val) {
        preferencesController.updateUniversal(
          prefs.copyWith(translationLanguage: val),
        );
      },
    );
  }

  void _showGifProviderModal(BuildContext context, ThemeData gbTheme) {
    final prefs = preferencesController.universal;
    GbRadioSelectionModal.show<String>(
      context: context,
      title: 'GIF Provider',
      options: ['Tenor', 'Giphy'],
      selectedOption: prefs.gifProvider,
      onSelected: (val) {
        preferencesController.updateUniversal(
          prefs.copyWith(gifProvider: val),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final themeController = locator<ThemeController>();
    return ListenableBuilder(
      listenable: Listenable.merge([preferencesController, themeController]),
      builder: (context, _) {
        final theme = themeController.globalTheme;
        final prefs = preferencesController.universal;
        final gbTheme = Theme.of(context);

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
              'Settings',
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
                // SECTION: Translation
                GbCardContainer(
                  children: [
                    GbSettingRow(
                      icon: Icons.translate_rounded,
                      title: 'Translate Option Settings',
                      subtitle: 'Choose how the translation icon works: ${prefs.translateOption}',
                      hasSubScreen: true,
                      onTap: () => _showTranslateSettingsModal(context, gbTheme),
                    ),
                    GbSettingRow(
                      icon: Icons.language_rounded,
                      title: 'Default translation language',
                      subtitle: prefs.translationLanguage,
                      hasSubScreen: true,
                      onTap: () => _showLanguageModal(context, gbTheme),
                    ),
                  ],
                ),
                const SizedBox(height: 14),

                // SECTION: General Mods
                GbCardContainer(
                  children: [
                    GbSwitchTile(
                      icon: Icons.view_carousel_rounded,
                      title: 'Conversation Cards (multi Chats)',
                      subtitle: 'Every chat you open will become a card, can switch from Recents easily',
                      value: prefs.conversationCards,
                      onChanged: (val) {
                        preferencesController.updateUniversal(
                          prefs.copyWith(conversationCards: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.notifications_off_rounded,
                      title: 'Disable Heads-Up Notification',
                      subtitle: 'Disable Heads-Up Popup in notifications',
                      value: prefs.disableHeadsUpNotification,
                      onChanged: (val) {
                        preferencesController.updateUniversal(
                          prefs.copyWith(disableHeadsUpNotification: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.mark_chat_unread_rounded,
                      title: 'Disable Badge counter',
                      subtitle: 'Disables the messages counter on the icon in Home Screen/Launcher',
                      value: prefs.disableBadgeCounter,
                      onChanged: (val) {
                        preferencesController.updateUniversal(
                          prefs.copyWith(disableBadgeCounter: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.volume_off_rounded,
                      title: 'Disable Audio playing Notification',
                      subtitle: 'Remove the notification when playing voice notes/audio',
                      value: prefs.disableAudioPlayingNotification,
                      onChanged: (val) {
                        preferencesController.updateUniversal(
                          prefs.copyWith(disableAudioPlayingNotification: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.forward_rounded,
                      title: 'Increase Forward limit',
                      subtitle: 'Forward messages up to 250 chats!',
                      value: prefs.increaseForwardLimit,
                      onChanged: (val) {
                        preferencesController.updateUniversal(
                          prefs.copyWith(increaseForwardLimit: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.swipe_rounded,
                      title: 'Disable Swipe to exit conversation',
                      subtitle: 'Prevents closing the conversation by swiping from left to right',
                      value: prefs.disableSwipeToExit,
                      onChanged: (val) {
                        preferencesController.updateUniversal(
                          prefs.copyWith(disableSwipeToExit: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.access_time_filled_rounded,
                      title: 'Enable Always Online',
                      subtitle: 'Stay Always online, but don\'t close from Recents!',
                      value: prefs.enableAlwaysOnline,
                      onChanged: (val) {
                        preferencesController.updateUniversal(
                          prefs.copyWith(enableAlwaysOnline: val),
                        );
                      },
                    ),
                    GbSettingRow(
                      icon: Icons.gif_box_rounded,
                      title: 'Tenor/Giphy GIF provider',
                      subtitle: prefs.gifProvider,
                      hasSubScreen: true,
                      onTap: () => _showGifProviderModal(context, gbTheme),
                    ),
                    GbSettingRow(
                      icon: Icons.cleaning_services_rounded,
                      title: 'Clear Chaty Logs',
                      subtitle: '55 MB',
                      hasSubScreen: false,
                      onTap: () {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('Chaty debug logs cleared (55 MB freed)')),
                        );
                      },
                    ),
                  ],
                ),
                const SizedBox(height: 14),

                // SECTION: Image/Video Mods
                GbCardContainer(
                  headerText: 'Image/Video Mods',
                  children: [
                    GbSliderTile(
                      title: 'Send Images in Full Resolution',
                      subtitle: 'Images will be sent in Highest quality/resolution up to ${prefs.sendImagesFullResolutionMb}MB',
                      value: prefs.sendImagesFullResolutionMb.toDouble(),
                      min: 1,
                      max: 6,
                      divisions: 5,
                      unit: 'MB',
                      onChanged: (val) {
                        preferencesController.updateUniversal(
                          prefs.copyWith(sendImagesFullResolutionMb: val.round()),
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
