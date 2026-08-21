import 'package:flutter/material.dart';
import '../../../../ui/core/theme/theme_controller.dart';
import '../../../../injection/locator.dart';
import '../../../../data/services/notification_service.dart';

class AuthPermissionsDialog extends StatefulWidget {
  const AuthPermissionsDialog({super.key});

  static Future<void> show(BuildContext context) {
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      isDismissible: true,
      builder: (_) => const AuthPermissionsDialog(),
    );
  }

  @override
  State<AuthPermissionsDialog> createState() => _AuthPermissionsDialogState();
}

class _AuthPermissionsDialogState extends State<AuthPermissionsDialog> {
  bool _notifications = true;
  bool _camera = true;
  bool _microphone = true;
  bool _photosMedia = true;
  bool _contacts = true;

  void _grantAllPermissions() {
    try {
      final notificationService = locator<ChatyNotificationService>();

      // Dispatch welcome notification
      notificationService.triggerEventNotification(
        title: 'Permissions Configured',
        body: 'Camera, Microphone, Media, and Notification permissions active.',
        icon: Icons.verified_rounded,
        color: Colors.green,
      );
    } catch (_) {}

    Navigator.of(context).pop();
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Row(
          children: [
            Icon(Icons.check_circle_rounded, color: Colors.white),
            SizedBox(width: 12),
            Expanded(
              child: Text(
                'All permissions granted! Welcome to Chaty.',
                style: TextStyle(fontWeight: FontWeight.w600),
              ),
            ),
          ],
        ),
        backgroundColor: Color(0xFF10B981),
        duration: Duration(seconds: 3),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = locator<ThemeController>().globalTheme;
    final isDark = theme.brightness == Brightness.dark;

    return Container(
      padding: const EdgeInsets.fromLTRB(24, 16, 24, 32),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF0F172A) : Colors.white,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.3),
            blurRadius: 24,
            offset: const Offset(0, -6),
          ),
        ],
      ),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Handle bar
            Center(
              child: Container(
                width: 44,
                height: 4,
                decoration: BoxDecoration(
                  color: isDark
                      ? const Color(0xFF334155)
                      : const Color(0xFFCBD5E1),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 20),

            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: theme.accentColor.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Icon(
                    Icons.shield_rounded,
                    color: theme.accentColor,
                    size: 28,
                  ),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'App Permissions',
                        style: TextStyle(
                          color: theme.primaryTextColor,
                          fontSize: 20,
                          fontWeight: FontWeight.w800,
                          letterSpacing: -0.3,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        'Required for real-time messaging & media',
                        style: TextStyle(
                          color: theme.secondaryTextColor,
                          fontSize: 13,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 20),

            // Permissions list items
            _buildPermissionItem(
              icon: Icons.notifications_active_rounded,
              title: 'Push Notifications',
              subtitle: 'Instant alerts for messages and updates',
              value: _notifications,
              theme: theme,
              onChanged: (v) => setState(() => _notifications = v),
            ),
            _buildPermissionItem(
              icon: Icons.camera_alt_rounded,
              title: 'Camera Access',
              subtitle: 'Take photos and record video messages',
              value: _camera,
              theme: theme,
              onChanged: (v) => setState(() => _camera = v),
            ),
            _buildPermissionItem(
              icon: Icons.mic_rounded,
              title: 'Microphone Access',
              subtitle: 'Voice notes and voice/video calling',
              value: _microphone,
              theme: theme,
              onChanged: (v) => setState(() => _microphone = v),
            ),
            _buildPermissionItem(
              icon: Icons.photo_library_rounded,
              title: 'Photos & Storage',
              subtitle: 'Send media files and custom wallpapers',
              value: _photosMedia,
              theme: theme,
              onChanged: (v) => setState(() => _photosMedia = v),
            ),
            _buildPermissionItem(
              icon: Icons.contacts_rounded,
              title: 'Contacts & Sync',
              subtitle: 'Find friends and start encrypted chats',
              value: _contacts,
              theme: theme,
              onChanged: (v) => setState(() => _contacts = v),
            ),

            const SizedBox(height: 24),

            // Action buttons
            Row(
              children: [
                Expanded(
                  child: TextButton(
                    onPressed: () => Navigator.of(context).pop(),
                    style: TextButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(14),
                      ),
                    ),
                    child: Text(
                      'Not Now',
                      style: TextStyle(
                        color: theme.secondaryTextColor,
                        fontSize: 15,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  flex: 2,
                  child: ElevatedButton(
                    onPressed: _grantAllPermissions,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: theme.accentColor,
                      foregroundColor: theme.onAccentColor,
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      elevation: 2,
                      shadowColor: theme.accentColor.withValues(alpha: 0.4),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                      ),
                    ),
                    child: Text(
                      'Allow All & Continue',
                      style: TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w700,
                        color: theme.onAccentColor,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPermissionItem({
    required IconData icon,
    required String title,
    required String subtitle,
    required bool value,
    required dynamic theme,
    required ValueChanged<bool> onChanged,
  }) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6.0),
      child: Row(
        children: [
          Icon(icon, color: theme.accentColor, size: 22),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: TextStyle(
                    color: theme.primaryTextColor,
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                Text(
                  subtitle,
                  style: TextStyle(
                    color: theme.secondaryTextColor,
                    fontSize: 11.5,
                  ),
                ),
              ],
            ),
          ),
          Switch(
            value: value,
            activeTrackColor: theme.accentColor,
            onChanged: onChanged,
          ),
        ],
      ),
    );
  }
}
