import 'package:flutter/material.dart';

import '../../../data/services/chaty_notification_service.dart';
import '../../../ui/core/controllers/chaty_preferences_controller.dart';
import '../../../ui/core/design_system/chaty_settings_primitives.dart';

class NotificationSettingsPage extends StatefulWidget {
  final ChatyPreferencesController preferencesController;
  final ChatyNotificationService notificationService;

  const NotificationSettingsPage({
    super.key,
    required this.preferencesController,
    required this.notificationService,
  });

  @override
  State<NotificationSettingsPage> createState() => _NotificationSettingsPageState();
}

class _NotificationSettingsPageState extends State<NotificationSettingsPage> {
  static const List<String> _toastPositions = <String>['Top', 'Center', 'Bottom'];
  static const List<int> _toastDurations = <int>[2, 3, 4, 5, 6, 8];

  void _previewToast() {
    widget.notificationService.triggerEventNotification(
      title: 'Elena is online',
      body: 'Presence alerts appear here only when the corresponding live event is enabled.',
      icon: Icons.online_prediction_rounded,
      color: Colors.greenAccent,
      avatarInitials: 'ER',
      avatarColorHex: '0xFFEC4899',
    );
  }

  @override
  Widget build(BuildContext context) {
    final notif = widget.preferencesController.notification;
    final toastPosition = widget.preferencesController.gbString('event_toast_position', fallback: 'Top');
    final duration = widget.preferencesController.gbInt('event_toast_duration_seconds', fallback: 3).clamp(2, 8);
    final recordingAlert = widget.preferencesController.gbBool('notify_recording_started', fallback: true);

    return ChatySettingsPage(
      title: 'Notification Customization',
      subtitle: 'Live presence, typing and activity alerts',
      children: [
        ChatyPreviewCard(
          title: 'Live in-app toast preview',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Position: $toastPosition • Duration: ${duration}s • Online: ${notif.notifyContactOnline ? 'On' : 'Off'} • Typing: ${notif.notifyTypingStarted ? 'On' : 'Off'}',
                style: TextStyle(fontSize: 12, color: Theme.of(context).textTheme.bodySmall?.color),
              ),
              const SizedBox(height: 10),
              FilledButton.icon(
                onPressed: _previewToast,
                icon: const Icon(Icons.play_arrow_rounded),
                label: const Text('Preview toast'),
              ),
            ],
          ),
        ),
        ChatySettingsSection(
          title: 'Global alerts',
          children: [
            ChatySwitchTile(
              icon: Icons.notifications_active_rounded,
              iconColor: Colors.indigoAccent,
              title: 'Enable in-app notifications',
              subtitle: 'Allow Chaty to show realtime event banners while the app is open',
              value: notif.enableGlobalNotifications,
              onChanged: (value) => widget.preferencesController.updateNotification(
                notif.copyWith(enableGlobalNotifications: value),
                logTitle: 'Enable Notifications',
              ),
            ),
            ChatySwitchTile(
              icon: Icons.account_circle_rounded,
              title: 'Show sender avatar',
              subtitle: 'Show the contact avatar on presence and activity toasts',
              value: notif.showSenderAvatar,
              onChanged: (value) => widget.preferencesController.updateNotification(
                notif.copyWith(showSenderAvatar: value),
                logTitle: 'Show Sender Avatar',
              ),
            ),
            ChatySwitchTile(
              icon: Icons.badge_outlined,
              title: 'Show sender name',
              subtitle: 'Display the contact name in event banners',
              value: notif.showSenderName,
              onChanged: (value) => widget.preferencesController.updateNotification(
                notif.copyWith(showSenderName: value),
                logTitle: 'Show Sender Name',
              ),
            ),
            ChatySwitchTile(
              icon: Icons.subtitles_rounded,
              title: 'Show event detail',
              subtitle: 'Include the online, typing or recording detail text',
              value: notif.showMessagePreview,
              onChanged: (value) => widget.preferencesController.updateNotification(
                notif.copyWith(showMessagePreview: value),
                logTitle: 'Show Message Preview',
              ),
            ),
          ],
        ),
        ChatySettingsSection(
          title: 'Realtime contact events',
          description: 'These alerts are generated from Supabase Realtime state changes, not simulations.',
          children: [
            ChatySwitchTile(
              icon: Icons.online_prediction_rounded,
              iconColor: Colors.greenAccent,
              title: 'Contact online alert',
              subtitle: 'Show a toast when a conversation contact changes from offline to online',
              value: notif.notifyContactOnline,
              onChanged: (value) => widget.preferencesController.updateNotification(
                notif.copyWith(notifyContactOnline: value),
                logTitle: 'Contact Online Alert',
              ),
            ),
            ChatySwitchTile(
              icon: Icons.keyboard_alt_outlined,
              iconColor: Colors.blueAccent,
              title: 'Typing alert',
              subtitle: 'Show a toast when a contact starts typing',
              value: notif.notifyTypingStarted,
              onChanged: (value) => widget.preferencesController.updateNotification(
                notif.copyWith(notifyTypingStarted: value),
                logTitle: 'Typing Alert',
              ),
            ),
            ChatySwitchTile(
              icon: Icons.mic_none_rounded,
              iconColor: Colors.redAccent,
              title: 'Recording alert',
              subtitle: 'Show a toast when a contact starts recording a voice message',
              value: recordingAlert,
              onChanged: (value) => widget.preferencesController.updateGbFeature(
                'notify_recording_started',
                value,
                logTitle: 'Recording Alert',
              ),
            ),
            ChatySwitchTile(
              icon: Icons.visibility_rounded,
              iconColor: Colors.cyanAccent,
              title: 'Status / story viewed alert',
              subtitle: 'Notify when the backend records a contact viewing your story',
              value: notif.notifyStatusViewed,
              onChanged: (value) => widget.preferencesController.updateNotification(
                notif.copyWith(notifyStatusViewed: value),
                logTitle: 'Status Viewed Alert',
              ),
            ),
            ChatySwitchTile(
              icon: Icons.delete_sweep_rounded,
              iconColor: Colors.redAccent,
              title: 'Message revoked alert',
              subtitle: 'Notify when a contact deletes a message',
              value: notif.notifyMessageDeleted,
              onChanged: (value) => widget.preferencesController.updateNotification(
                notif.copyWith(notifyMessageDeleted: value),
                logTitle: 'Message Revoked Alert',
              ),
            ),
          ],
        ),
        ChatySettingsSection(
          title: 'Toast placement',
          description: 'Choose where realtime activity alerts should appear without blocking navigation.',
          children: [
            ChatyChoiceTile<String>(
              title: 'Toast position',
              options: _toastPositions,
              selectedOption: _toastPositions.contains(toastPosition) ? toastPosition : 'Top',
              optionLabel: (value) => value,
              onSelected: (value) => widget.preferencesController.updateGbFeature(
                'event_toast_position',
                value,
                logTitle: 'Event Toast Position',
              ),
            ),
            ChatyChoiceTile<int>(
              title: 'Toast duration',
              options: _toastDurations,
              selectedOption: _toastDurations.contains(duration) ? duration : 3,
              optionLabel: (value) => '$value seconds',
              onSelected: (value) => widget.preferencesController.updateGbFeature(
                'event_toast_duration_seconds',
                value,
                logTitle: 'Event Toast Duration',
              ),
            ),
          ],
        ),
      ],
    );
  }
}
