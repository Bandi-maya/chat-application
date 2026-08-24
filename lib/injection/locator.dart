import 'package:get_it/get_it.dart';
import 'package:chat/ui/core/theme/theme_controller.dart';
import 'package:chat/data/repositories/chaty_data_store.dart';
import 'package:chat/data/services/backend_service.dart';
import 'package:chat/data/services/contact_relationship_service.dart';
import 'package:chat/data/services/local_lock_service.dart';
import 'package:chat/data/services/mls_e2ee_service.dart';
import 'package:chat/data/services/rich_chat_realtime_service.dart';
import 'package:chat/ui/core/controllers/app_icon_controller.dart';
import 'package:chat/ui/core/controllers/preferences_controller.dart';
import 'package:chat/ui/core/controllers/appearance_variant_controller.dart';
import 'package:chat/ui/core/templates/template_controller.dart';
import 'package:chat/data/services/notification_service.dart';
import 'package:chat/data/services/message_automation_service.dart';
import 'package:chat/data/services/push_token_service.dart';
import 'package:chat/data/services/notification_channel_manager.dart';
import 'package:chat/data/services/call_signaling_service.dart';
import 'package:chat/data/services/call_foreground_service.dart';
import 'package:chat/data/services/call_lifecycle_coordinator.dart';
import 'package:chat/features/calls/call_presentation_controller.dart';
import 'package:chat/features/camera/effects/effect_engine.dart';

final GetIt locator = GetIt.instance;

void setupLocator() {
  if (locator.isRegistered<ThemeController>()) return;

  locator.registerLazySingleton<ThemeController>(() => ThemeController());
  locator.registerLazySingleton<MlsE2eeService>(() => MlsE2eeService());
  locator.registerLazySingleton<ChatyBackendService>(
    () => ChatyBackendService(),
  );
  locator.registerLazySingleton<ChatyDataStore>(() => ChatyDataStore());
  locator.registerLazySingleton<ChatyPreferencesController>(
    () => ChatyPreferencesController(),
  );
  locator.registerLazySingleton<AppearanceVariantController>(
    () => AppearanceVariantController(),
  );
  locator.registerLazySingleton<TemplateController>(
    () => TemplateController(),
  );
  locator.registerLazySingleton<ChatyNotificationService>(
    () => ChatyNotificationService(),
  );
  locator.registerLazySingleton<PushTokenService>(
    () => PushTokenService(),
  );
  locator.registerLazySingleton<NotificationChannelManager>(
    () => NotificationChannelManager(
      preferences: locator<ChatyPreferencesController>(),
      dataStore: locator<ChatyDataStore>(),
    ),
  );
  locator.registerLazySingleton<CallSignalingService>(
    () => CallSignalingService(
      dataStore: locator<ChatyDataStore>(),
      backend: locator<ChatyBackendService>(),
    ),
  );
  locator.registerLazySingleton<ChatyCallForegroundService>(
    () => ChatyCallForegroundService(),
  );
  locator.registerLazySingleton<CallLifecycleCoordinator>(
    () => CallLifecycleCoordinator(
      callService: locator<CallSignalingService>(),
      foregroundService: locator<ChatyCallForegroundService>(),
    )..start(),
  );
  locator.registerLazySingleton<CallPresentationController>(
    () => CallPresentationController(
      callService: locator<CallSignalingService>(),
    ),
  );
  // Eagerly instantiate to begin tracking presentations immediately
  locator<CallPresentationController>();
  locator.registerLazySingleton<EffectEngine>(
    () => EffectEngine(),
  );
  locator.registerLazySingleton<AppIconController>(() => AppIconController());
  locator.registerLazySingleton<LocalLockService>(() => LocalLockService());
  locator.registerLazySingleton<ContactRelationshipService>(
    () => ContactRelationshipService(),
  );
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
      dataStore: locator<ChatyDataStore>(),
    ),
  );
}
