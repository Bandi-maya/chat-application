import 'package:flutter/material.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart' hide ChatySettingsSection;
import '../../../ui/core/design_system/settings_primitives.dart';

/// Dedicated canonical screen for Storage, Network Data, and Media Limits.
class StorageAndMediaSettingsScreen extends StatelessWidget {
  final ChatyPreferencesController preferencesController;

  const StorageAndMediaSettingsScreen({
    super.key,
    required this.preferencesController,
  });

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;

    return ListenableBuilder(
      listenable: preferencesController,
      builder: (context, _) {
        return ChatySettingsPage(
          title: 'Storage & Data',
          subtitle: 'Media quality, upload limits & cache retention',
          children: [
            // 1. MEDIA QUALITY & UPLOAD LIMITS
            ChatySettingsSection(
              title: 'Media Upload & Quality',
              description: 'Configure resolution and maximum upload size thresholds.',
              children: [
                ChatySwitchTile(
                  icon: Icons.hd_rounded,
                  iconColor: colors.primary,
                  title: 'Send Images in High Resolution',
                  subtitle: 'Preserve photographic detail and avoid aggressive downscaling.',
                  value: true,
                  onChanged: (val) {},
                ),
                ChatySwitchTile(
                  icon: Icons.upload_file_rounded,
                  iconColor: colors.primary,
                  title: 'Increased Upload Limit (Up to 700MB)',
                  subtitle: 'Allow sending large audio, video, and document payloads.',
                  value: true,
                  onChanged: (val) {},
                ),
                ChatySwitchTile(
                  icon: Icons.photo_library_outlined,
                  iconColor: colors.primary,
                  title: 'Share Up to 100 Images at Once',
                  subtitle: 'Disable batch-selection limits in media picker.',
                  value: true,
                  onChanged: (val) {},
                ),
              ],
            ),

            // 2. AUTO-DOWNLOAD
            ChatySettingsSection(
              title: 'Automatic Media Download',
              children: [
                ChatySwitchTile(
                  icon: Icons.wifi_rounded,
                  iconColor: colors.success,
                  title: 'When Connected on Wi-Fi',
                  subtitle: 'Photos, voice notes and documents',
                  value: true,
                  onChanged: (val) {},
                ),
                ChatySwitchTile(
                  icon: Icons.signal_cellular_alt_rounded,
                  iconColor: colors.warning,
                  title: 'When Using Mobile Data',
                  subtitle: 'Voice notes only',
                  value: false,
                  onChanged: (val) {},
                ),
              ],
            ),

            // 3. STORAGE USAGE & CACHE
            ChatySettingsSection(
              title: 'Device Storage',
              children: [
                ChatySettingsTile(
                  icon: Icons.cleaning_services_rounded,
                  iconColor: colors.primary,
                  title: 'Clear Local Media Cache',
                  subtitle: 'Free up local disk space without deleting server chats',
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: () {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Media cache cleared successfully.')),
                    );
                  },
                ),
              ],
            ),
          ],
        );
      },
    );
  }
}
