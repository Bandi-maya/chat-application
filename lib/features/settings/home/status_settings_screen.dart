import 'package:flutter/material.dart';

import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart';
import '../../../ui/core/design_system/gb_design_system.dart';

class StatusSettingsScreen extends StatefulWidget {
  final ChatyPreferencesController preferencesController;

  const StatusSettingsScreen({
    super.key,
    required this.preferencesController,
  });

  @override
  State<StatusSettingsScreen> createState() => _StatusSettingsScreenState();
}

class _StatusSettingsScreenState extends State<StatusSettingsScreen> {
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

  void _showStoriesStyleModal() {
    final prefs = widget.preferencesController.status;
    GbRadioSelectionModal.show<String>(
      context: context,
      title: 'Stories Style',
      options: ['Instagram', 'Facebook', 'RC-Notification', 'Stock', 'Card View'],
      selectedOption: prefs.storiesStyle,
      onSelected: (val) {
        widget.preferencesController.updateStatus(
          prefs.copyWith(storiesStyle: val),
        );
      },
    );
  }

  void _showStatusStyleModal() {
    final prefs = widget.preferencesController.status;
    GbRadioSelectionModal.show<String>(
      context: context,
      title: 'Activate the new status style',
      options: [
        'Old Status with Thumbnail',
        'Modern Grid View',
        'Minimalist Card',
        'Full Bleed Carousel',
      ],
      selectedOption: prefs.activateNewStatusStyle,
      onSelected: (val) {
        widget.preferencesController.updateStatus(
          prefs.copyWith(activateNewStatusStyle: val),
        );
      },
    );
  }

  void _showReactionEmojiModal() {
    final prefs = widget.preferencesController.status;
    GbRadioSelectionModal.show<String>(
      context: context,
      title: 'Status Reaction Emoji',
      options: ['💚', '❤️', '🔥', '👏', '😂', '😍', '🎉'],
      selectedOption: prefs.statusReactionEmoji,
      onSelected: (val) {
        widget.preferencesController.updateStatus(
          prefs.copyWith(statusReactionEmoji: val),
        );
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
        final prefs = widget.preferencesController.status;

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
              'Status',
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
                // Top Live Profile Status Card matching Image 3 Status screen
                GbCardContainer(
                  children: [
                    GbLivePreviewCard(
                      child: Row(
                        children: [
                          Stack(
                            alignment: Alignment.center,
                            children: [
                              Container(
                                width: 56,
                                height: 56,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  border: Border.all(
                                    color: prefs.statusAroundProfile
                                        ? (prefs.statusSeenColor != null
                                            ? Color(prefs.statusSeenColor!)
                                            : theme.accentColor)
                                        : Colors.transparent,
                                    width: 2.5,
                                  ),
                                ),
                              ),
                              CircleAvatar(
                                radius: 23,
                                backgroundColor: theme.accentColor.withValues(alpha: 0.15),
                                child: Text(
                                  'TN',
                                  style: TextStyle(
                                    fontWeight: FontWeight.bold,
                                    color: theme.primaryTextColor,
                                  ),
                                ),
                              ),
                              Positioned(
                                right: 0,
                                bottom: 0,
                                child: Container(
                                  padding: const EdgeInsets.all(2),
                                  decoration: BoxDecoration(
                                    color: theme.accentColor,
                                    shape: BoxShape.circle,
                                  ),
                                  child: const Icon(
                                    Icons.add,
                                    size: 14,
                                    color: Colors.white,
                                  ),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(width: 14),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  'Testing Name',
                                  style: TextStyle(
                                    color: prefs.contactNameColor != null
                                        ? Color(prefs.contactNameColor!)
                                        : theme.primaryTextColor,
                                    fontSize: 16,
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                                const SizedBox(height: 3),
                                Text(
                                  'Tap to add status update',
                                  style: TextStyle(
                                    color: theme.secondaryTextColor,
                                    fontSize: 13,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          Text(
                            prefs.statusReactionEmoji,
                            style: const TextStyle(fontSize: 22),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),

                // Stories Style Toggles & Style Selection
                GbCardContainer(
                  children: [
                    GbSwitchTile(
                      icon: Icons.auto_stories_rounded,
                      title: 'Enable Instagram-like Stories',
                      subtitle: 'Enable Instagram Stories for Chaty (needs restart)',
                      value: prefs.enableInstagramStories,
                      onChanged: (val) {
                        widget.preferencesController.updateStatus(
                          prefs.copyWith(enableInstagramStories: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.view_carousel_rounded,
                      title: 'Carousel view',
                      subtitle: 'Status displayed in horizontal carousel flow',
                      value: prefs.carouselView,
                      onChanged: (val) {
                        widget.preferencesController.updateStatus(
                          prefs.copyWith(carouselView: val),
                        );
                      },
                    ),
                    GbSettingRow(
                      icon: Icons.style_rounded,
                      title: 'Stories Style',
                      subtitle: prefs.storiesStyle,
                      hasSubScreen: true,
                      onTap: _showStoriesStyleModal,
                    ),
                    GbSettingRow(
                      icon: Icons.dashboard_customize_rounded,
                      title: 'Activate the new status style',
                      subtitle: prefs.activateNewStatusStyle,
                      hasSubScreen: true,
                      onTap: _showStatusStyleModal,
                    ),
                    GbSettingRow(
                      icon: Icons.add_reaction_rounded,
                      title: 'Status Reaction Emoji',
                      subtitle: 'Change status reaction button: ${prefs.statusReactionEmoji}',
                      hasSubScreen: true,
                      onTap: _showReactionEmojiModal,
                    ),
                  ],
                ),
                const SizedBox(height: 14),

                // Colors Section
                GbCardContainer(
                  children: [
                    GbSettingRow(
                      icon: Icons.vertical_align_bottom_rounded,
                      title: 'Recent Updates bar',
                      subtitle: 'Change color of background recent/viewed updates bar in status page',
                      colorHex: prefs.recentUpdatesBarColor,
                      onColorTap: () => _pickColor(
                        title: 'Recent Updates Bar Color',
                        currentColor: prefs.recentUpdatesBarColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateStatus(
                            prefs.copyWith(recentUpdatesBarColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.text_fields_rounded,
                      title: 'Recent Updates text',
                      subtitle: 'Change color of text recent/viewed updates bar in status page',
                      colorHex: prefs.recentUpdatesTextColor,
                      onColorTap: () => _pickColor(
                        title: 'Recent Updates Text Color',
                        currentColor: prefs.recentUpdatesTextColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateStatus(
                            prefs.copyWith(recentUpdatesTextColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.person_pin_rounded,
                      title: 'Contact Name',
                      subtitle: 'Change Color Contact Name in Stories',
                      colorHex: prefs.contactNameColor,
                      onColorTap: () => _pickColor(
                        title: 'Contact Name Color',
                        currentColor: prefs.contactNameColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateStatus(
                            prefs.copyWith(contactNameColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.visibility_rounded,
                      title: 'Status Seen',
                      subtitle: 'Select color for circle seen Status',
                      colorHex: prefs.statusSeenColor,
                      onColorTap: () => _pickColor(
                        title: 'Status Seen Color',
                        currentColor: prefs.statusSeenColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateStatus(
                            prefs.copyWith(statusSeenColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.radio_button_checked_rounded,
                      title: 'Status UnSeen',
                      subtitle: 'Select color for circle unseen Status',
                      colorHex: prefs.statusUnseenColor,
                      onColorTap: () => _pickColor(
                        title: 'Status UnSeen Color',
                        currentColor: prefs.statusUnseenColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateStatus(
                            prefs.copyWith(statusUnseenColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.layers_rounded,
                      title: 'Counter Background',
                      subtitle: 'Change Color background counter Stories',
                      colorHex: prefs.counterBackgroundColor,
                      onColorTap: () => _pickColor(
                        title: 'Counter Background Color',
                        currentColor: prefs.counterBackgroundColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateStatus(
                            prefs.copyWith(counterBackgroundColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.format_color_text_rounded,
                      title: 'Counter Text',
                      subtitle: 'Change Color text of counter',
                      colorHex: prefs.counterTextColor,
                      onColorTap: () => _pickColor(
                        title: 'Counter Text Color',
                        currentColor: prefs.counterTextColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateStatus(
                            prefs.copyWith(counterTextColor: val),
                          );
                        },
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),

                // Status Profile & Video Options
                GbCardContainer(
                  children: [
                    GbSwitchTile(
                      icon: Icons.radio_button_on_rounded,
                      title: 'Status around profile',
                      subtitle: 'Turn on to access status updates by clicking profile picture',
                      value: prefs.statusAroundProfile,
                      onChanged: (val) {
                        widget.preferencesController.updateStatus(
                          prefs.copyWith(statusAroundProfile: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.save_alt_rounded,
                      title: 'Save and Mark Seen options',
                      subtitle: 'Save button and Mark Seen button to be displayed when you open any status',
                      value: prefs.saveAndMarkSeenOptions,
                      onChanged: (val) {
                        widget.preferencesController.updateStatus(
                          prefs.copyWith(saveAndMarkSeenOptions: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.camera_front_rounded,
                      title: 'Change Photo Profile / Status Preview',
                      subtitle: 'Change display in Stories from Profile picture to Status preview',
                      value: prefs.changePhotoProfileStatusPreview,
                      onChanged: (val) {
                        widget.preferencesController.updateStatus(
                          prefs.copyWith(changePhotoProfileStatusPreview: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.headset_mic_rounded,
                      title: 'Start stories directly with sound',
                      subtitle: 'You can play the sound in the status or story when the phone is in silent mode or in vibrate mode',
                      value: prefs.startStoriesWithSound,
                      onChanged: (val) {
                        widget.preferencesController.updateStatus(
                          prefs.copyWith(startStoriesWithSound: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.check_circle_outline_rounded,
                      title: 'Confirm before sending a Status',
                      subtitle: 'Confirm dialog will popup before your status is uploaded',
                      value: prefs.confirmBeforeSendingStatus,
                      onChanged: (val) {
                        widget.preferencesController.updateStatus(
                          prefs.copyWith(confirmBeforeSendingStatus: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.timelapse_rounded,
                      title: '5-minute status',
                      subtitle: 'Allows you to put a 50 minute video instead of 30 seconds as a Status. Note: Only Chaty users can see the full status! Other users will see 30sec only.',
                      value: prefs.fiveMinuteStatus,
                      onChanged: (val) {
                        widget.preferencesController.updateStatus(
                          prefs.copyWith(fiveMinuteStatus: val),
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
