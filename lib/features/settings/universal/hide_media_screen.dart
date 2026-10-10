import 'dart:io';

import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';

import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart';
import '../../../ui/core/design_system/gb_design_system.dart';

class HideMediaScreen extends StatelessWidget {
  final ChatyPreferencesController preferencesController;

  const HideMediaScreen({
    super.key,
    required this.preferencesController,
  });

  static Future<void> _syncNoMediaMarker(bool shouldHide) async {
    try {
      final docDir = await getApplicationDocumentsDirectory();
      final mediaDir = Directory('${docDir.path}/ChatyMedia');
      if (!await mediaDir.exists()) {
        await mediaDir.create(recursive: true);
      }
      final noMediaFile = File('${mediaDir.path}/.nomedia');
      if (shouldHide) {
        if (!await noMediaFile.exists()) {
          await noMediaFile.writeAsString('');
        }
      } else {
        if (await noMediaFile.exists()) {
          await noMediaFile.delete();
        }
      }
    } catch (e) {
      debugPrint('Syncing .nomedia marker failed: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    final themeController = locator<ThemeController>();
    return ListenableBuilder(
      listenable: Listenable.merge([preferencesController, themeController]),
      builder: (context, _) {
        final theme = themeController.globalTheme;
        final prefs = preferencesController.universal;

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
              'Hide Media from gallery',
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
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
                  child: Text(
                    'Turn ON toggles to prevent media received in chats from being scanned by and visible in your device gallery.',
                    style: TextStyle(
                      color: theme.secondaryTextColor,
                      fontSize: 13,
                      height: 1.4,
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                GbCardContainer(
                  children: [
                    GbSwitchTile(
                      icon: Icons.photo_library_rounded,
                      title: 'Photos',
                      subtitle: 'Hide photos downloaded from chats in gallery',
                      value: prefs.hideMediaPhotos,
                      onChanged: (val) {
                        preferencesController.updateUniversal(
                          prefs.copyWith(hideMediaPhotos: val),
                        );
                        _syncNoMediaMarker(val || prefs.hideMediaVideos || prefs.hideMediaGifs);
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.video_collection_rounded,
                      title: 'Videos',
                      subtitle: 'Hide received video clips in gallery',
                      value: prefs.hideMediaVideos,
                      onChanged: (val) {
                        preferencesController.updateUniversal(
                          prefs.copyWith(hideMediaVideos: val),
                        );
                        _syncNoMediaMarker(prefs.hideMediaPhotos || val || prefs.hideMediaGifs);
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.gif_box_rounded,
                      title: 'GIFs',
                      subtitle: 'Hide downloaded GIFs and animated stickers in gallery',
                      value: prefs.hideMediaGifs,
                      onChanged: (val) {
                        preferencesController.updateUniversal(
                          prefs.copyWith(hideMediaGifs: val),
                        );
                        _syncNoMediaMarker(prefs.hideMediaPhotos || prefs.hideMediaVideos || val);
                      },
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                Center(
                  child: TextButton.icon(
                    onPressed: () {
                      preferencesController.updateUniversal(
                        prefs.copyWith(
                          hideMediaPhotos: false,
                          hideMediaVideos: false,
                          hideMediaGifs: false,
                        ),
                      );
                      _syncNoMediaMarker(false);
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Media gallery visibility restored to default')),
                      );
                    },
                    icon: Icon(Icons.restart_alt_rounded, color: theme.accentColor, size: 18),
                    label: Text(
                      'Reset Gallery Settings',
                      style: TextStyle(color: theme.accentColor, fontWeight: FontWeight.w600),
                    ),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}
