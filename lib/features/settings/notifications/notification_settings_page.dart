import 'package:flutter/material.dart';
import 'package:chat/data/repositories/chaty_data_store.dart';
import 'package:chat/data/services/notification_service.dart';
import 'package:chat/injection/locator.dart';
import 'package:chat/ui/core/controllers/preferences_controller.dart';
import 'package:chat/ui/core/design_system/settings_primitives.dart';
import 'package:chat/ui/core/design_system/design_system.dart'
    hide ChatySettingsSection;
import 'package:chat/ui/core/design_system/color_picker.dart';

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
  static const List<String> _toastPositions = <String>[
    'Top',
    'Center',
    'Bottom',
  ];
  static const List<int> _toastDurations = <int>[2, 3, 4, 5, 6, 8];

  void _previewToast(BuildContext context) {
    widget.notificationService.triggerEventNotification(
      title: 'Elena is online',
      body:
          'Presence alerts appear here only when the corresponding live event is enabled.',
      icon: Icons.online_prediction_rounded,
      color: context.colors.success,
      avatarInitials: 'ER',
      avatarColorHex: '0xFFEC4899',
    );
  }

  @override
  Widget build(BuildContext context) {
    final notif = widget.preferencesController.notification;
    final colors = context.colors;
    final toastPosition = widget.preferencesController.gbString(
      'event_toast_position',
      fallback: 'Top',
    );
    final duration = widget.preferencesController.gbInt(
      'event_toast_duration_seconds',
      fallback: 3,
    );
    final recordingAlert = widget.preferencesController.gbBool(
      'notify_recording_started',
      fallback: false,
    );

    return ChatySettingsPage(
      title: 'Toast & Event Notifications',
      subtitle:
          'Manage live toasts, contact activity alerts and preview styles',
      children: [
        // Live Toast Preview
        ChatySettingsSection(
          title: 'Live Preview',
          children: [
            Padding(
              padding: const EdgeInsets.all(ChatySpacing.base),
              child: Column(
                children: [
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(ChatySpacing.base),
                    decoration: BoxDecoration(
                      color: colors.surfaceSecondary,
                      borderRadius: BorderRadius.circular(ChatyRadius.lg),
                      border: Border.all(color: colors.borderSubtle),
                    ),
                    child: Row(
                      children: [
                        CircleAvatar(
                          radius: 20,
                          backgroundColor: colors.primary,
                          child: Text(
                            'ER',
                            style: TextStyle(
                              color: colors.onPrimary,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                        const SizedBox(width: ChatySpacing.md),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Elena is online',
                                style: ChatyTypography.title(colors.foreground),
                              ),
                              const SizedBox(height: 2),
                              Text(
                                'Presence alerts appear in realtime',
                                style: ChatyTypography.caption(
                                  colors.foregroundSecondary,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: ChatySpacing.md),
                  ChatyPrimaryButton(
                    text: 'Trigger Live Toast Preview',
                    icon: Icons.play_arrow_rounded,
                    onPressed: () => _previewToast(context),
                  ),
                ],
              ),
            ),
          ],
        ),

        // Master Toggle
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
              subtitle: 'Display user avatar icon in event toast notifications',
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
              subtitle: 'Include the online, typing or recording detail text',
              value: notif.showMessagePreview,
              onChanged: (value) =>
                  widget.preferencesController.updateNotification(
                    notif.copyWith(showMessagePreview: value),
                    logTitle: 'Show Message Preview',
                  ),
            ),
          ],
        ),
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
        ChatySettingsSection(
          title: 'Toast placement',
          description:
              'Choose where realtime activity alerts should appear without blocking navigation.',
          children: [
            ChatyChoiceTile<String>(
              title: 'Toast position',
              options: _toastPositions,
              selectedOption: _toastPositions.contains(toastPosition)
                  ? toastPosition
                  : 'Top',
              optionLabel: (value) => value,
              onSelected: (value) =>
                  widget.preferencesController.updateGbFeature(
                    'event_toast_position',
                    value,
                    logTitle: 'Event Toast Position',
                  ),
            ),
            ChatySliderTile(
              icon: Icons.person_rounded,
              title: 'Toast avatar size',
              subtitle: 'Scales the profile picture and toast height',
              value: widget.preferencesController.gbDouble(
                'toast_avatar_size',
                fallback: 40,
              ),
              min: 24,
              max: 56,
              divisions: 8,
              valueFormatter: (v) => '${v.round()} px',
              onChanged: (v) => setState(
                () => widget.preferencesController.updateGbFeature(
                  'toast_avatar_size',
                  v.round(),
                  logTitle: 'Toast Avatar Size',
                ),
              ),
            ),
            ChatySliderTile(
              icon: Icons.rounded_corner_rounded,
              title: 'Corner radius',
              value: widget.preferencesController.gbDouble(
                'toast_corner_radius',
                fallback: 18,
              ),
              min: 4,
              max: 32,
              divisions: 7,
              valueFormatter: (v) => '${v.round()} px',
              onChanged: (v) => widget.preferencesController.updateGbFeature(
                'toast_corner_radius',
                v.round(),
                logTitle: 'Toast Corner Radius',
              ),
            ),
            ChatySliderTile(
              icon: Icons.layers_rounded,
              title: 'Elevation',
              value: widget.preferencesController.gbDouble(
                'toast_elevation',
                fallback: 10,
              ),
              min: 0,
              max: 24,
              divisions: 8,
              valueFormatter: (v) => v.round().toString(),
              onChanged: (v) => widget.preferencesController.updateGbFeature(
                'toast_elevation',
                v.round(),
                logTitle: 'Toast Elevation',
              ),
            ),
            ChatySettingsTile(
              icon: Icons.format_color_fill_rounded,
              title: 'Background color',
              subtitle: _bgColor == null ? 'Theme surface' : 'Custom color set',
              trailing: Container(
                width: 22,
                height: 22,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: _bgColor ?? context.colors.surface,
                  border: Border.all(color: context.colors.border),
                ),
              ),
              onTap: () async {
                final picked = await ChatyColorPickerModal.show(
                  context,
                  title: 'Toast background',
                  currentColor: _bgColor ?? context.colors.surface,
                  backgroundContextColor: context.colors.background,
                );
                if (!mounted) return;
                widget.preferencesController.updateGbFeature(
                  'event_toast_bg',
                  picked?.toARGB32() ?? 0,
                  logTitle: 'Toast Background',
                );
                setState(() {});
              },
            ),
            ChatyChoiceTile<int>(
              title: 'Toast duration',
              options: _toastDurations,
              selectedOption: _toastDurations.contains(duration) ? duration : 3,
              optionLabel: (value) => '$value seconds',
              onSelected: (value) =>
                  widget.preferencesController.updateGbFeature(
                    'event_toast_duration_seconds',
                    value,
                    logTitle: 'Event Toast Duration',
                  ),
            ),
            ChatySettingsTile(
              icon: Icons.play_circle_rounded,
              iconColor: context.colors.primary,
              title: 'Test toast',
              subtitle:
                  'Render this toast now with your current style settings',
              trailing: Icon(
                Icons.play_arrow_rounded,
                color: context.colors.primary,
              ),
              onTap: _fireTestToast,
            ),
          ],
        ),
      ],
    );
  }

  /// The configured custom background, or null when the theme surface is
  /// used. Drives both the swatch preview and the picker's initial value.
  Color? get _bgColor => widget.preferencesController.gbColor('event_toast_bg');

  /// Renders the REAL configured toast through the REAL overlay using this
  /// account's own profile data — a local preview only; nothing is pushed
  /// to anyone. It intentionally respects every active setting (master
  /// switch, position, duration, avatar size, radius, elevation, colors).
  void _fireTestToast() {
    final me = locator<ChatyDataStore>().currentUser;
    widget.notificationService.triggerEventNotification(
      title: '${me.displayName} is online',
      body: 'Preview — your toast style, rendered locally',
      icon: Icons.wifi_tethering_rounded,
      color: context.colors.primary,
      userId: me.id,
      avatarInitials: me.avatarInitials,
      avatarColorHex: me.avatarColorHex,
    );
  }
}
