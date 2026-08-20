import 'package:get_it/get_it.dart';
import 'package:chat/ui/core/theme/theme_controller.dart';
import 'package:chat/data/repositories/mock_data_store.dart';
import 'package:chat/data/services/chaty_backend_service.dart';
import 'package:chat/data/services/contact_relationship_service.dart';
import 'package:chat/data/services/local_lock_service.dart';
import 'package:chat/data/services/rich_chat_realtime_service.dart';
import 'package:chat/ui/core/controllers/app_icon_controller.dart';
import 'package:chat/ui/core/controllers/chaty_preferences_controller.dart';
import 'package:chat/ui/core/controllers/appearance_variant_controller.dart';
import 'package:chat/data/services/chaty_notification_service.dart';
import 'package:chat/data/services/message_automation_service.dart';

final GetIt locator = GetIt.instance;

void setupLocator() {
  if (locator.isRegistered<ThemeController>()) return;

  locator.registerLazySingleton<ThemeController>(() => ThemeController());
  locator.registerLazySingleton<ChatyBackendService>(() => ChatyBackendService());
  locator.registerLazySingleton<MockDataStore>(() => MockDataStore());
  locator.registerLazySingleton<ChatyPreferencesController>(() => ChatyPreferencesController());
  locator.registerLazySingleton<AppearanceVariantController>(() => AppearanceVariantController());
  locator.registerLazySingleton<ChatyNotificationService>(() => ChatyNotificationService());
  locator.registerLazySingleton<AppIconController>(() => AppIconController());
  locator.registerLazySingleton<LocalLockService>(() => LocalLockService());
  locator.registerLazySingleton<ContactRelationshipService>(() => ContactRelationshipService());
  locator.registerLazySingleton<RichChatRealtimeService>(
    () => RichChatRealtimeService(
      preferencesController: locator<ChatyPreferencesController>(),
      notificationService: locator<ChatyNotificationService>(),
      backendService: locator<ChatyBackendService>(),
    ),
  );

  locator.registerFactory<MessageAutomationService>(
    () => MessageAutomationService(
      preferencesController: locator<ChatyPreferencesController>(),
      dataStore: locator<MockDataStore>(),
    ),
  );
}
