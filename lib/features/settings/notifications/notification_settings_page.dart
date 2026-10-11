import 'package:flutter/material.dart';
import 'package:chat/injection/locator.dart';
import 'package:chat/ui/core/controllers/preferences_controller.dart';
import 'package:chat/ui/core/design_system/settings_primitives.dart';
import 'package:chat/ui/core/design_system/design_system.dart'
    hide ChatySettingsSection;
import 'package:chat/ui/core/design_system/gb_design_system.dart';
import 'package:chat/data/services/notification_service.dart';

class NotificationSettingsPage extends StatefulWidget {
  final ChatyPreferencesController preferencesController;
  final ChatyNotificationService notificationService;

  const NotificationSettingsPage({
    super.key,
    required this.preferencesController,
    required this.notificationService,
  });

  @override
  State<NotificationSettingsPage> createState() =>
      _NotificationSettingsPageState();
}

class _NotificationSettingsPageState extends State<NotificationSettingsPage> {
  void _pickColor({
    required String title,
    required int? currentColor,
    required ValueChanged<int?> onColorSelected,
  }) {
    final theme = locator<ThemeController>().globalTheme;
    final initial =
        currentColor != null ? Color(currentColor) : theme.accentColor;
    GbColorPickerModal.show(
      context,
      title: title,
      currentColor: initial,
      onColorChanged: (c) {
        onColorSelected(c.toARGB32());
      },
    );
  }

  void _showPositionModal(String current, ValueChanged<String> onSelected) {
    GbRadioSelectionModal.show<String>(
      context: context,
      title: 'Position of Toast',
      options: const ['Top of the screen', 'Center of screen', 'Bottom of screen'],
      selectedOption: current,
      onSelected: onSelected,
    );
  }

  void _showRingtoneModal(String current, ValueChanged<String> onSelected) {
    GbRadioSelectionModal.show<String>(
      context: context,
      title: 'Ringtone',
      options: const [
        'Default Chaty Tone',
        'Soft Ping',
        'Pop Bubble',
        'Chime Bell',
        'Silent',
      ],
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
      listenable: Listenable.merge([
        widget.preferencesController,
        themeController,
      ]),
      builder: (context, _) {
        final notif = widget.preferencesController.notification;
        final prefs = widget.preferencesController.toasts;
        final colors = context.colors;
        final recordingAlert = widget.preferencesController.gbBool(
          'notify_recording_started',
          fallback: false,
        );

        return ChatySettingsPage(
          title: 'Notifications',
          subtitle:
              'In-app alerts, realtime contact events & toast notification customizations',
          children: [
            // 1. In-app event notifications master settings
            ChatySettingsSection(
              title: 'In-app event notifications',
              description:
                  'Display non-intrusive toasts for incoming live state transitions without interrupting chats.',
              children: [
                ChatySwitchTile(
                  icon: Icons.notifications_active_rounded,
                  iconColor: colors.primary,
                  title: 'Enable in-app notifications',
                  subtitle:
                      'Allow Chaty to show realtime event banners while the app is open',
                  value: notif.enableGlobalNotifications,
                  onChanged: (value) =>
                      widget.preferencesController.updateNotification(
                        notif.copyWith(enableGlobalNotifications: value),
                        logTitle: 'In-App Notifications',
                      ),
                ),
                ChatySwitchTile(
                  icon: Icons.account_circle_rounded,
                  title: 'Show sender avatar',
                  subtitle:
                      'Display user avatar icon in event toast notifications',
                  value: notif.showSenderAvatar,
                  onChanged: (value) =>
                      widget.preferencesController.updateNotification(
                        notif.copyWith(showSenderAvatar: value),
                        logTitle: 'Notification Avatar',
                      ),
                ),
                ChatySwitchTile(
                  icon: Icons.badge_rounded,
                  title: 'Show sender name',
                  subtitle: 'Include the contact name in the toast title',
                  value: notif.showSenderName,
                  onChanged: (value) =>
                      widget.preferencesController.updateNotification(
                        notif.copyWith(showSenderName: value),
                        logTitle: 'Notification Name',
                      ),
                ),
                ChatySwitchTile(
                  icon: Icons.subtitles_rounded,
                  title: 'Show event detail',
                  subtitle:
                      'Include the online, typing or recording detail text',
                  value: notif.showMessagePreview,
                  onChanged: (value) =>
                      widget.preferencesController.updateNotification(
                        notif.copyWith(showMessagePreview: value),
                        logTitle: 'Show Message Preview',
                      ),
                ),
              ],
            ),

            // 2. Realtime contact events
            ChatySettingsSection(
              title: 'Realtime contact events',
              description:
                  'These alerts are generated from Supabase Realtime state changes, not simulations.',
              children: [
                ChatySwitchTile(
                  icon: Icons.online_prediction_rounded,
                  iconColor: colors.success,
                  title: 'Contact online alert',
                  subtitle:
                      'Show a toast when a conversation contact changes from offline to online',
                  value: notif.notifyContactOnline,
                  onChanged: (value) =>
                      widget.preferencesController.updateNotification(
                        notif.copyWith(notifyContactOnline: value),
                        logTitle: 'Contact Online Alert',
                      ),
                ),
                ChatySwitchTile(
                  icon: Icons.keyboard_alt_outlined,
                  iconColor: colors.info,
                  title: 'Typing alert',
                  subtitle: 'Show a toast when a contact starts typing',
                  value: notif.notifyTypingStarted,
                  onChanged: (value) =>
                      widget.preferencesController.updateNotification(
                        notif.copyWith(notifyTypingStarted: value),
                        logTitle: 'Typing Alert',
                      ),
                ),
                ChatySwitchTile(
                  icon: Icons.mic_none_rounded,
                  iconColor: colors.error,
                  title: 'Recording alert',
                  subtitle:
                      'Show a toast when a contact starts recording a voice message',
                  value: recordingAlert,
                  onChanged: (value) =>
                      widget.preferencesController.updateGbFeature(
                        'notify_recording_started',
                        value,
                        logTitle: 'Recording Alert',
                      ),
                ),
                ChatySwitchTile(
                  icon: Icons.visibility_rounded,
                  iconColor: colors.accent,
                  title: 'Status / story viewed alert',
                  subtitle:
                      'Notify when the backend records a contact viewing your story',
                  value: notif.notifyStatusViewed,
                  onChanged: (value) =>
                      widget.preferencesController.updateNotification(
                        notif.copyWith(notifyStatusViewed: value),
                        logTitle: 'Status Viewed Alert',
                      ),
                ),
                ChatySwitchTile(
                  icon: Icons.delete_sweep_rounded,
                  iconColor: colors.error,
                  title: 'Message revoked alert',
                  subtitle: 'Notify when a contact deletes a message',
                  value: notif.notifyMessageDeleted,
                  onChanged: (value) =>
                      widget.preferencesController.updateNotification(
                        notif.copyWith(notifyMessageDeleted: value),
                        logTitle: 'Message Revoked Alert',
                      ),
                ),
              ],
            ),

            // 3. TOAST NOTIFICATION CUSTOMIZATION: Contact Online Toast
            GbCardContainer(
              headerText: 'Contact Online Toast Customization',
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
                  subtitle:
                      'Show a notification on the phone\'s status bar',
                  value: prefs.onlineInNotificationBar,
                  onChanged: (val) {
                    widget.preferencesController.updateToasts(
                      prefs.copyWith(onlineInNotificationBar: val),
                      logTitle: 'Online In Notification Bar',
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
                      logTitle: 'Disable Contact Online Toast',
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
                      logTitle: 'Online With Profile Pic',
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
                      logTitle: 'Online Toast Elevation',
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
                      logTitle: 'Online Toast Radius',
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
                      logTitle: 'Online Toast Ringtone',
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
                      logTitle: 'Online Toast Position',
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
                        logTitle: 'Online Toast Bg Color',
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
                        logTitle: 'Online Toast Text Color',
                      );
                    },
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),

            // 4. TOAST NOTIFICATION CUSTOMIZATION: Viewed Story Toast
            GbCardContainer(
              headerText: 'Viewed Story Toast Customization',
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
                      logTitle: 'Story In Notification Bar',
                    );
                  },
                ),
                GbSwitchTile(
                  icon: Icons.history_edu_rounded,
                  title: 'Viewed Story Toast',
                  subtitle:
                      'Know immediately when someone views your status via Toast message',
                  value: prefs.viewedStoryToast,
                  onChanged: (val) {
                    widget.preferencesController.updateToasts(
                      prefs.copyWith(viewedStoryToast: val),
                      logTitle: 'Viewed Story Toast',
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
                      logTitle: 'Story With Profile Pic',
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
                      logTitle: 'Story Toast Radius',
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
                      logTitle: 'Story Toast Position',
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
                        logTitle: 'Story Toast Bg Color',
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
                        logTitle: 'Story Toast Text Color',
                      );
                    },
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),

            // 5. TOAST NOTIFICATION CUSTOMIZATION: Profile Toast
            GbCardContainer(
              headerText: 'Profile Toast Customization',
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
                  subtitle:
                      'Know immediately when anyone views your profile via a Toast message',
                  value: prefs.profileToast,
                  onChanged: (val) {
                    widget.preferencesController.updateToasts(
                      prefs.copyWith(profileToast: val),
                      logTitle: 'Profile Toast',
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
                      logTitle: 'Profile With Profile Pic',
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
                      logTitle: 'Profile Toast Radius',
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
                      logTitle: 'Profile Toast Position',
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
                        logTitle: 'Profile Toast Bg Color',
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
                        logTitle: 'Profile Toast Text Color',
                      );
                    },
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),

            // 6. TOAST NOTIFICATION CUSTOMIZATION: Typing Toast
            GbCardContainer(
              headerText: 'Typing Toast Customization',
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
                  subtitle:
                      'Know immediately when anyone starts typing to you via a floating toast message',
                  value: prefs.typingToast,
                  onChanged: (val) {
                    widget.preferencesController.updateToasts(
                      prefs.copyWith(typingToast: val),
                      logTitle: 'Typing Toast',
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
                      logTitle: 'Typing With Profile Pic',
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
                      logTitle: 'Typing Toast Radius',
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
                      logTitle: 'Typing Toast Position',
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
                        logTitle: 'Typing Toast Bg Color',
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
                        logTitle: 'Typing Toast Text Color',
                      );
                    },
                  ),
                ),
              ],
            ),
          ],
        );
      },
    );
  }
}
