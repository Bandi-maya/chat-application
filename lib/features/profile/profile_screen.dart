import 'package:flutter/material.dart';

import '../../data/repositories/chaty_data_store.dart';
import '../../data/services/notification_service.dart';
import '../../ui/core/controllers/preferences_controller.dart';
import '../../ui/core/theme/theme_controller.dart';
import '../settings/settings_root_screen.dart';

/// Root "Profile" destination (bottom navigation).
/// Renders the enhanced GB WhatsApp profile & settings dashboard.
class ProfileScreen extends StatelessWidget {
  final ChatyPreferencesController preferencesController;
  final ThemeController themeController;
  final ChatyDataStore dataStore;
  final ChatyNotificationService notificationService;

  const ProfileScreen({
    super.key,
    required this.preferencesController,
    required this.themeController,
    required this.dataStore,
    required this.notificationService,
  });

  @override
  Widget build(BuildContext context) {
    return SettingsRootScreen(
      preferencesController: preferencesController,
      themeController: themeController,
      dataStore: dataStore,
      notificationService: notificationService,
    );
  }
}
