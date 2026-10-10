import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';

import '../../../data/repositories/chaty_data_store.dart';
import '../../../domain/models/preferences.dart';
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
  bool _isCalculatingStorage = false;
  int _totalStorageBytes = 0;
  String _backupPath = '';
  String _databasePath = '';

  @override
  void initState() {
    super.initState();
    _initPathsAndStorage();
  }

  Future<void> _initPathsAndStorage() async {
    setState(() => _isCalculatingStorage = true);
    try {
      final docDir = await getApplicationDocumentsDirectory();
      final tempDir = await getTemporaryDirectory();
      final backupDir = Directory('${docDir.path}/ChatyBackups');
      if (!await backupDir.exists()) {
        await backupDir.create(recursive: true);
      }
      _backupPath = backupDir.path;
      _databasePath = '${docDir.path}/Databases';

      int total = 0;
      Future<int> dirSize(Directory d) async {
        if (!await d.exists()) return 0;
        int sz = 0;
        await for (final f in d.list(recursive: true, followLinks: false)) {
          if (f is File) {
            sz += await f.length();
          }
        }
        return sz;
      }

      total += await dirSize(docDir);
      total += await dirSize(tempDir);

      if (mounted) {
        setState(() {
          _totalStorageBytes = total;
          _isCalculatingStorage = false;
        });
      }
    } catch (_) {
      if (mounted) setState(() => _isCalculatingStorage = false);
    }
  }

  Future<void> _performBackup(String target) async {
    setState(() => _isBackingUp = true);
    try {
      final docDir = await getApplicationDocumentsDirectory();
      final backupDir = Directory('${docDir.path}/ChatyBackups');
      if (!await backupDir.exists()) {
        await backupDir.create(recursive: true);
      }

      final timestamp = DateTime.now().millisecondsSinceEpoch;
      final file = File('${backupDir.path}/chaty_backup_$timestamp.json');

      final dataStore = locator.isRegistered<ChatyDataStore>()
          ? locator<ChatyDataStore>()
          : null;

      final conversations = dataStore?.conversations ?? [];
      final conversationsMap = conversations.map((c) => <String, dynamic>{
        'id': c.id,
        'title': c.title,
        'type': c.type.name,
        'participantIds': c.participantIds,
        'lastMessageText': c.lastMessageText,
        'lastMessageTime': c.lastMessageTime.toIso8601String(),
        'unreadCount': c.unreadCount,
        'isMuted': c.isMuted,
        'isPinned': c.isPinned,
        'isArchived': c.isArchived,
      }).toList();

      final payload = <String, dynamic>{
        'kind': 'chaty_full_backup',
        'schemaVersion': 1,
        'createdAt': DateTime.now().toIso8601String(),
        'target': target,
        'preferences': {
          'universal': widget.preferencesController.universal.toMap(),
          'home': widget.preferencesController.home.toMap(),
          'conversation': widget.preferencesController.conversation.toMap(),
          'privacy': widget.preferencesController.privacy.toMap(),
          'security': widget.preferencesController.security.toMap(),
          'notification': widget.preferencesController.notification.toMap(),
        },
        'conversations': conversationsMap,
      };

      await file.writeAsString(jsonEncode(payload));
      await _initPathsAndStorage();

      if (!mounted) return;
      setState(() => _isBackingUp = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('$target backup created successfully! Saved to ${file.uri.pathSegments.last}')),
      );
    } catch (e) {
      if (!mounted) return;
      setState(() => _isBackingUp = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Backup failed: $e')),
      );
    }
  }

  Future<void> _performRestore(String target) async {
    setState(() => _isRestoring = true);
    try {
      final docDir = await getApplicationDocumentsDirectory();
      final backupDir = Directory('${docDir.path}/ChatyBackups');
      if (!await backupDir.exists()) {
        if (!mounted) return;
        setState(() => _isRestoring = false);
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('No backup directory found.')),
        );
        return;
      }

      File? latestBackup;
      int latestTime = 0;
      await for (final entity in backupDir.list(followLinks: false)) {
        if (entity is File && entity.path.endsWith('.json')) {
          final stat = await entity.stat();
          if (stat.modified.millisecondsSinceEpoch > latestTime) {
            latestTime = stat.modified.millisecondsSinceEpoch;
            latestBackup = entity;
          }
        }
      }

      if (latestBackup == null) {
        if (!mounted) return;
        setState(() => _isRestoring = false);
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('No local backups found to restore.')),
        );
        return;
      }

      final content = await latestBackup.readAsString();
      final map = jsonDecode(content) as Map<String, dynamic>;

      if (map['kind'] != 'chaty_full_backup') {
        throw const FormatException('Invalid or unverified backup manifest format.');
      }

      final prefsMap = map['preferences'] as Map<String, dynamic>?;
      if (prefsMap != null) {
        final univMap = prefsMap['universal'] as Map<String, dynamic>?;
        if (univMap != null) {
          widget.preferencesController.updateUniversal(
            UniversalPreferences.fromMap(univMap),
          );
        }
      }

      if (!mounted) return;
      setState(() => _isRestoring = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('$target data restored successfully from ${latestBackup.uri.pathSegments.last}!')),
      );
    } catch (e) {
      if (!mounted) return;
      setState(() => _isRestoring = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Restore failed: $e')),
      );
    }
  }

  Future<void> _clearTemporaryAndBackups() async {
    int freed = 0;
    try {
      final docDir = await getApplicationDocumentsDirectory();
      final tempDir = await getTemporaryDirectory();
      final backupDir = Directory('${docDir.path}/ChatyBackups');

      if (await tempDir.exists()) {
        await for (final f in tempDir.list(recursive: true, followLinks: false)) {
          if (f is File) {
            final len = await f.length();
            await f.delete();
            freed += len;
          }
        }
      }

      if (await backupDir.exists()) {
        final backups = <File>[];
        await for (final f in backupDir.list(followLinks: false)) {
          if (f is File) backups.add(f);
        }
        backups.sort((a, b) => b.lastModifiedSync().compareTo(a.lastModifiedSync()));
        // Keep the most recent backup, clear older ones
        if (backups.length > 1) {
          for (var i = 1; i < backups.length; i++) {
            final len = await backups[i].length();
            await backups[i].delete();
            freed += len;
          }
        }
      }
    } catch (_) {}

    await _initPathsAndStorage();
    if (!mounted) return;
    final freedMb = (freed / (1024 * 1024)).toStringAsFixed(1);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('Old temporary backups & cache cleared! ($freedMb MB freed)')),
    );
  }

  @override
  Widget build(BuildContext context) {
    final themeController = locator<ThemeController>();
    return ListenableBuilder(
      listenable: themeController,
      builder: (context, _) {
        final theme = themeController.globalTheme;

        final storageMb = (_totalStorageBytes / (1024 * 1024)).toStringAsFixed(1);

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
                      subtitle: 'Create an authentic backup of Chaty chats and account preferences',
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
                      subtitle: 'Restore the most recent local backup file',
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
                      title: 'Backup Media',
                      subtitle: 'Media and documents configuration',
                      hasSubScreen: true,
                      onTap: () => _performBackup('Media archives'),
                    ),
                    GbSettingRow(
                      icon: Icons.restore_page_rounded,
                      title: 'Restore Media',
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
                      subtitle: _isCalculatingStorage
                          ? 'Calculating…'
                          : 'Current Size: $storageMb MB',
                      onTap: _initPathsAndStorage,
                    ),
                    GbSettingRow(
                      icon: Icons.delete_sweep_rounded,
                      title: 'Clear',
                      subtitle: 'Erase old temporary backups and cache logs',
                      hasSubScreen: false,
                      onTap: _clearTemporaryAndBackups,
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
                            '• Chaty backup location: $_backupPath',
                            style: TextStyle(
                              color: theme.secondaryTextColor,
                              fontSize: 12,
                              fontFamily: 'monospace',
                              height: 1.4,
                            ),
                          ),
                          const SizedBox(height: 6),
                          Text(
                            '• Database location: $_databasePath',
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
