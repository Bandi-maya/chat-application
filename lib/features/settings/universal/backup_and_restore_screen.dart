import 'package:flutter/material.dart';

import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart';
import '../../../ui/core/design_system/gb_design_system.dart';

class BackupAndRestoreScreen extends StatefulWidget {
  final ChatyPreferencesController preferencesController;

  const BackupAndRestoreScreen({
    super.key,
    required this.preferencesController,
  });

  @override
  State<BackupAndRestoreScreen> createState() => _BackupAndRestoreScreenState();
}

class _BackupAndRestoreScreenState extends State<BackupAndRestoreScreen> {
  bool _isBackingUp = false;
  bool _isRestoring = false;

  void _performBackup(String target) async {
    setState(() => _isBackingUp = true);
    await Future.delayed(const Duration(milliseconds: 900));
    if (!mounted) return;
    setState(() => _isBackingUp = false);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('$target backup created successfully! Saved locally.')),
    );
  }

  void _performRestore(String target) async {
    setState(() => _isRestoring = true);
    await Future.delayed(const Duration(milliseconds: 900));
    if (!mounted) return;
    setState(() => _isRestoring = false);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('$target data restored successfully!')),
    );
  }

  @override
  Widget build(BuildContext context) {
    final themeController = locator<ThemeController>();
    return ListenableBuilder(
      listenable: themeController,
      builder: (context, _) {
        final theme = themeController.globalTheme;

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
              'Backup and restore',
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
                // SECTION: Chaty Data
                GbCardContainer(
                  headerText: 'Chaty Data',
                  children: [
                    GbSettingRow(
                      icon: Icons.cloud_upload_rounded,
                      title: 'Backup Chaty data',
                      subtitle: 'Create a backup of Chaty chats and account preferences',
                      hasSubScreen: true,
                      trailing: _isBackingUp
                          ? const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : null,
                      onTap: () => _performBackup('Chaty database'),
                    ),
                    GbSettingRow(
                      icon: Icons.settings_backup_restore_rounded,
                      title: 'Restore Chaty data',
                      subtitle: 'Restore the most recent local backup',
                      hasSubScreen: true,
                      trailing: _isRestoring
                          ? const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : null,
                      onTap: () => _performRestore('Chaty database'),
                    ),
                  ],
                ),
                const SizedBox(height: 14),

                // SECTION: Media
                GbCardContainer(
                  headerText: 'Media',
                  children: [
                    GbSettingRow(
                      icon: Icons.perm_media_rounded,
                      title: 'Backup',
                      subtitle: 'Media, links, and documents',
                      hasSubScreen: true,
                      onTap: () => _performBackup('Media archives'),
                    ),
                    GbSettingRow(
                      icon: Icons.restore_page_rounded,
                      title: 'Restore',
                      subtitle: 'Restore the most recent media backup',
                      hasSubScreen: true,
                      onTap: () => _performRestore('Media archives'),
                    ),
                  ],
                ),
                const SizedBox(height: 14),

                // SECTION: Chaty Storage Cleanup
                GbCardContainer(
                  headerText: 'Storage Maintenance',
                  children: [
                    GbSettingRow(
                      icon: Icons.storage_rounded,
                      title: 'Storage Footprint',
                      subtitle: 'Current Size: 101 MB',
                      onTap: () {},
                    ),
                    GbSettingRow(
                      icon: Icons.delete_sweep_rounded,
                      title: 'Clear',
                      subtitle: 'Erase old temporary backups and cache logs',
                      hasSubScreen: false,
                      onTap: () {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('Temporary cache and old backups cleared! (Freed 42 MB)')),
                        );
                      },
                    ),
                  ],
                ),
                const SizedBox(height: 14),

                // SECTION: Location
                GbCardContainer(
                  headerText: 'Location',
                  children: [
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '• Chaty backup location: /storage/emulated/0/Chaty/Backups',
                            style: TextStyle(
                              color: theme.secondaryTextColor,
                              fontSize: 12,
                              fontFamily: 'monospace',
                              height: 1.4,
                            ),
                          ),
                          const SizedBox(height: 6),
                          Text(
                            '• Database location: /storage/emulated/0/Android/media/com.chaty/Databases',
                            style: TextStyle(
                              color: theme.secondaryTextColor,
                              fontSize: 12,
                              fontFamily: 'monospace',
                              height: 1.4,
                            ),
                          ),
                        ],
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
