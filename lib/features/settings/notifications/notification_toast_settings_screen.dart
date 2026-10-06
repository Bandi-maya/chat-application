import 'package:flutter/material.dart';

import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart';
import '../../../ui/core/design_system/gb_design_system.dart';

class NotificationToastSettingsScreen extends StatefulWidget {
  final ChatyPreferencesController preferencesController;

  const NotificationToastSettingsScreen({
    super.key,
    required this.preferencesController,
  });

  @override
  State<NotificationToastSettingsScreen> createState() => _NotificationToastSettingsScreenState();
}

class _NotificationToastSettingsScreenState extends State<NotificationToastSettingsScreen> {
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

  void _showPositionModal(String current, ValueChanged<String> onSelected) {
    GbRadioSelectionModal.show<String>(
      context: context,
      title: 'Position of Toast',
      options: ['Top of the screen', 'Center of screen', 'Bottom of screen'],
      selectedOption: current,
      onSelected: onSelected,
    );
  }

  void _showRingtoneModal(String current, ValueChanged<String> onSelected) {
    GbRadioSelectionModal.show<String>(
      context: context,
      title: 'Ringtone',
      options: ['Default Chaty Tone', 'Soft Ping', 'Pop Bubble', 'Chime Bell', 'Silent'],
      selectedOption: current,
      onSelected: onSelected,
    );
  }

  Widget _buildToastPreview({
    required String text,
    required bool hasAvatar,
    required bool hasElevation,
    required double radius,
    required int? bgColor,
    required int? textColor,
    required Color defaultBg,
  }) {
    final bg = bgColor != null ? Color(bgColor) : defaultBg;
    final fg = textColor != null ? Color(textColor) : Colors.white;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(radius),
        boxShadow: hasElevation
            ? [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.25),
                  blurRadius: 8,
                  offset: const Offset(0, 3),
                ),
              ]
            : null,
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (hasAvatar) ...[
            CircleAvatar(
              radius: 12,
              backgroundColor: Colors.white.withValues(alpha: 0.2),
              child: const Icon(Icons.person, size: 14, color: Colors.white),
            ),
            const SizedBox(width: 8),
          ],
          Text(
            text,
            style: TextStyle(
              color: fg,
              fontWeight: FontWeight.w600,
              fontSize: 13,
            ),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final themeController = locator<ThemeController>();
    return ListenableBuilder(
      listenable: Listenable.merge([widget.preferencesController, themeController]),
      builder: (context, _) {
        final theme = themeController.globalTheme;
        final prefs = widget.preferencesController.toasts;

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
              'Notifications',
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
                // 1. CONTACT ONLINE TOAST
                GbCardContainer(
                  headerText: 'Contact Online Toast',
                  children: [
                    GbLivePreviewCard(
                      height: 70,
                      child: Center(
                        child: _buildToastPreview(
                          text: 'Bandi Maya Online',
                          hasAvatar: prefs.onlineWithProfilePic,
                          hasElevation: prefs.onlineElevation,
                          radius: prefs.onlineRadius,
                          bgColor: prefs.onlineBgColor,
                          textColor: prefs.onlineTextColor,
                          defaultBg: const Color(0xFF1E2630),
                        ),
                      ),
                    ),
                    GbSwitchTile(
                      icon: Icons.notifications_none_rounded,
                      title: 'Notification in the Notification Bar',
                      subtitle: 'Show a notification on the phone\'s status bar',
                      value: prefs.onlineInNotificationBar,
                      onChanged: (val) {
                        widget.preferencesController.updateToasts(
                          prefs.copyWith(onlineInNotificationBar: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.toggle_on_rounded,
                      title: 'Disable Contact Online Toast',
                      subtitle: 'Turn on to disable toast notifications',
                      value: prefs.disableContactOnlineToast,
                      onChanged: (val) {
                        widget.preferencesController.updateToasts(
                          prefs.copyWith(disableContactOnlineToast: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.account_circle_outlined,
                      title: 'Notifications with profile picture',
                      subtitle: 'Show avatar next to toast message',
                      value: prefs.onlineWithProfilePic,
                      onChanged: (val) {
                        widget.preferencesController.updateToasts(
                          prefs.copyWith(onlineWithProfilePic: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.filter_none_rounded,
                      title: 'Elevation',
                      subtitle: prefs.onlineElevation ? 'Enabled' : 'Disabled',
                      value: prefs.onlineElevation,
                      onChanged: (val) {
                        widget.preferencesController.updateToasts(
                          prefs.copyWith(onlineElevation: val),
                        );
                      },
                    ),
                    GbSliderTile(
                      title: 'Radius of toast notifications',
                      subtitle: 'Rounded corners radius',
                      value: prefs.onlineRadius,
                      min: 0,
                      max: 30,
                      divisions: 15,
                      onChanged: (val) {
                        widget.preferencesController.updateToasts(
                          prefs.copyWith(onlineRadius: val),
                        );
                      },
                    ),
                    GbSettingRow(
                      icon: Icons.music_note_rounded,
                      title: 'Ringtone',
                      subtitle: prefs.onlineRingtone,
                      hasSubScreen: true,
                      onTap: () => _showRingtoneModal(
                        prefs.onlineRingtone,
                        (val) => widget.preferencesController.updateToasts(
                          prefs.copyWith(onlineRingtone: val),
                        ),
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.vertical_align_top_rounded,
                      title: 'Position of Toast',
                      subtitle: prefs.onlinePosition,
                      hasSubScreen: true,
                      onTap: () => _showPositionModal(
                        prefs.onlinePosition,
                        (val) => widget.preferencesController.updateToasts(
                          prefs.copyWith(onlinePosition: val),
                        ),
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.format_paint_rounded,
                      title: 'Toast Background Color',
                      subtitle: 'Custom background tint',
                      colorHex: prefs.onlineBgColor,
                      onColorTap: () => _pickColor(
                        title: 'Toast Background Color',
                        currentColor: prefs.onlineBgColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateToasts(
                            prefs.copyWith(onlineBgColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.format_color_text_rounded,
                      title: 'Toast Text Color',
                      subtitle: 'Custom text tint',
                      colorHex: prefs.onlineTextColor,
                      onColorTap: () => _pickColor(
                        title: 'Toast Text Color',
                        currentColor: prefs.onlineTextColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateToasts(
                            prefs.copyWith(onlineTextColor: val),
                          );
                        },
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),

                // 2. VIEWED STORY TOAST
                GbCardContainer(
                  headerText: 'Viewed Story Toast',
                  children: [
                    GbLivePreviewCard(
                      height: 70,
                      child: Center(
                        child: _buildToastPreview(
                          text: 'Bandi Maya Status',
                          hasAvatar: prefs.storyWithProfilePic,
                          hasElevation: prefs.storyElevation,
                          radius: prefs.storyRadius,
                          bgColor: prefs.storyBgColor,
                          textColor: prefs.storyTextColor,
                          defaultBg: const Color(0xFF1E2630),
                        ),
                      ),
                    ),
                    GbSwitchTile(
                      icon: Icons.notifications_none_rounded,
                      title: 'Notification in the Notification Bar',
                      subtitle: 'Show in status bar',
                      value: prefs.storyInNotificationBar,
                      onChanged: (val) {
                        widget.preferencesController.updateToasts(
                          prefs.copyWith(storyInNotificationBar: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.history_edu_rounded,
                      title: 'Viewed Story Toast',
                      subtitle: 'Know immediately when someone views your status via Toast message',
                      value: prefs.viewedStoryToast,
                      onChanged: (val) {
                        widget.preferencesController.updateToasts(
                          prefs.copyWith(viewedStoryToast: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.account_circle_outlined,
                      title: 'Notifications with profile picture',
                      subtitle: 'Show avatar next to status toast',
                      value: prefs.storyWithProfilePic,
                      onChanged: (val) {
                        widget.preferencesController.updateToasts(
                          prefs.copyWith(storyWithProfilePic: val),
                        );
                      },
                    ),
                    GbSliderTile(
                      title: 'Radius of toast notifications',
                      subtitle: 'Corner curvature',
                      value: prefs.storyRadius,
                      min: 0,
                      max: 30,
                      divisions: 15,
                      onChanged: (val) {
                        widget.preferencesController.updateToasts(
                          prefs.copyWith(storyRadius: val),
                        );
                      },
                    ),
                    GbSettingRow(
                      icon: Icons.vertical_align_top_rounded,
                      title: 'Position of Toast',
                      subtitle: prefs.storyPosition,
                      hasSubScreen: true,
                      onTap: () => _showPositionModal(
                        prefs.storyPosition,
                        (val) => widget.preferencesController.updateToasts(
                          prefs.copyWith(storyPosition: val),
                        ),
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.format_paint_rounded,
                      title: 'Toast Background Color',
                      subtitle: 'Custom background tint',
                      colorHex: prefs.storyBgColor,
                      onColorTap: () => _pickColor(
                        title: 'Story Toast Background Color',
                        currentColor: prefs.storyBgColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateToasts(
                            prefs.copyWith(storyBgColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.format_color_text_rounded,
                      title: 'Toast Text Color',
                      subtitle: 'Custom text tint',
                      colorHex: prefs.storyTextColor,
                      onColorTap: () => _pickColor(
                        title: 'Story Toast Text Color',
                        currentColor: prefs.storyTextColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateToasts(
                            prefs.copyWith(storyTextColor: val),
                          );
                        },
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),

                // 3. PROFILE TOAST
                GbCardContainer(
                  headerText: 'Profile Toast',
                  children: [
                    GbLivePreviewCard(
                      height: 70,
                      child: Center(
                        child: _buildToastPreview(
                          text: 'Bandi Maya Profile',
                          hasAvatar: prefs.profileWithProfilePic,
                          hasElevation: prefs.profileElevation,
                          radius: prefs.profileRadius,
                          bgColor: prefs.profileBgColor,
                          textColor: prefs.profileTextColor,
                          defaultBg: const Color(0xFF1E2630),
                        ),
                      ),
                    ),
                    GbSwitchTile(
                      icon: Icons.account_box_rounded,
                      title: 'Profile Toast',
                      subtitle: 'Know immediately when anyone views your profile via a Toast message',
                      value: prefs.profileToast,
                      onChanged: (val) {
                        widget.preferencesController.updateToasts(
                          prefs.copyWith(profileToast: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.account_circle_outlined,
                      title: 'Notifications with profile picture',
                      subtitle: 'Show avatar',
                      value: prefs.profileWithProfilePic,
                      onChanged: (val) {
                        widget.preferencesController.updateToasts(
                          prefs.copyWith(profileWithProfilePic: val),
                        );
                      },
                    ),
                    GbSliderTile(
                      title: 'Radius of toast notifications',
                      subtitle: 'Corner curvature',
                      value: prefs.profileRadius,
                      min: 0,
                      max: 30,
                      divisions: 15,
                      onChanged: (val) {
                        widget.preferencesController.updateToasts(
                          prefs.copyWith(profileRadius: val),
                        );
                      },
                    ),
                    GbSettingRow(
                      icon: Icons.vertical_align_top_rounded,
                      title: 'Position of Toast',
                      subtitle: prefs.profilePosition,
                      hasSubScreen: true,
                      onTap: () => _showPositionModal(
                        prefs.profilePosition,
                        (val) => widget.preferencesController.updateToasts(
                          prefs.copyWith(profilePosition: val),
                        ),
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.format_paint_rounded,
                      title: 'Toast Background Color',
                      subtitle: 'Custom background tint',
                      colorHex: prefs.profileBgColor,
                      onColorTap: () => _pickColor(
                        title: 'Profile Toast Background Color',
                        currentColor: prefs.profileBgColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateToasts(
                            prefs.copyWith(profileBgColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.format_color_text_rounded,
                      title: 'Toast Text Color',
                      subtitle: 'Custom text tint',
                      colorHex: prefs.profileTextColor,
                      onColorTap: () => _pickColor(
                        title: 'Profile Toast Text Color',
                        currentColor: prefs.profileTextColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateToasts(
                            prefs.copyWith(profileTextColor: val),
                          );
                        },
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),

                // 4. TYPING TOAST
                GbCardContainer(
                  headerText: 'Typing Toast',
                  children: [
                    GbLivePreviewCard(
                      height: 70,
                      child: Center(
                        child: _buildToastPreview(
                          text: 'Bandi Maya Typing...',
                          hasAvatar: prefs.typingWithProfilePic,
                          hasElevation: prefs.typingElevation,
                          radius: prefs.typingRadius,
                          bgColor: prefs.typingBgColor,
                          textColor: prefs.typingTextColor,
                          defaultBg: const Color(0xFF1E2630),
                        ),
                      ),
                    ),
                    GbSwitchTile(
                      icon: Icons.keyboard_rounded,
                      title: 'Typing Toast',
                      subtitle: 'Know immediately when anyone starts typing to you via a floating toast message',
                      value: prefs.typingToast,
                      onChanged: (val) {
                        widget.preferencesController.updateToasts(
                          prefs.copyWith(typingToast: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.account_circle_outlined,
                      title: 'Notifications with profile picture',
                      subtitle: 'Show avatar next to typing alert',
                      value: prefs.typingWithProfilePic,
                      onChanged: (val) {
                        widget.preferencesController.updateToasts(
                          prefs.copyWith(typingWithProfilePic: val),
                        );
                      },
                    ),
                    GbSliderTile(
                      title: 'Radius of toast notifications',
                      subtitle: 'Corner curvature',
                      value: prefs.typingRadius,
                      min: 0,
                      max: 30,
                      divisions: 15,
                      onChanged: (val) {
                        widget.preferencesController.updateToasts(
                          prefs.copyWith(typingRadius: val),
                        );
                      },
                    ),
                    GbSettingRow(
                      icon: Icons.vertical_align_top_rounded,
                      title: 'Position of Toast',
                      subtitle: prefs.typingPosition,
                      hasSubScreen: true,
                      onTap: () => _showPositionModal(
                        prefs.typingPosition,
                        (val) => widget.preferencesController.updateToasts(
                          prefs.copyWith(typingPosition: val),
                        ),
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.format_paint_rounded,
                      title: 'Toast Background Color',
                      subtitle: 'Custom background tint',
                      colorHex: prefs.typingBgColor,
                      onColorTap: () => _pickColor(
                        title: 'Typing Toast Background Color',
                        currentColor: prefs.typingBgColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateToasts(
                            prefs.copyWith(typingBgColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.format_color_text_rounded,
                      title: 'Toast Text Color',
                      subtitle: 'Custom text tint',
                      colorHex: prefs.typingTextColor,
                      onColorTap: () => _pickColor(
                        title: 'Typing Toast Text Color',
                        currentColor: prefs.typingTextColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateToasts(
                            prefs.copyWith(typingTextColor: val),
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
