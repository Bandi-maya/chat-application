import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import 'package:chat/data/repositories/mock_data_store.dart';
import 'package:chat/data/services/chaty_backend_service.dart';
import 'package:chat/data/services/chaty_notification_service.dart';
import 'package:chat/data/services/contact_relationship_service.dart';
import 'package:chat/data/services/local_lock_service.dart';
import 'package:chat/data/services/message_automation_service.dart';
import 'package:chat/domain/models/user_profile.dart';
import 'package:chat/features/auth/create_new_password_screen.dart';
import 'package:chat/features/auth/splash_screen.dart';
import 'package:chat/features/auth/welcome_screen.dart';
import 'package:chat/features/settings/security/app_lock_overlay.dart';
import 'package:chat/injection/locator.dart';
import 'package:chat/ui/core/controllers/app_icon_controller.dart';
import 'package:chat/ui/core/controllers/appearance_variant_controller.dart';
import 'package:chat/ui/core/controllers/chaty_preferences_controller.dart';
import 'package:chat/ui/core/gb/gb_theme_overrides.dart';
import 'package:chat/ui/core/theme/theme_controller.dart';
import 'package:chat/ui/core/theme/theme_presets.dart';
import 'package:chat/ui/core/widgets/chaty_event_toast_overlay.dart';
import 'package:chat/ui/core/widgets/click_particle_overlay.dart';
import 'package:chat/ui/core/widgets/falling_particles_overlay.dart';

const String _supabaseUrl = String.fromEnvironment(
  'SUPABASE_URL',
  defaultValue: 'https://dntnxeanubswyswahdnj.supabase.co',
);
const String _supabasePublishableKey = String.fromEnvironment(
  'SUPABASE_PUBLISHABLE_KEY',
  defaultValue: 'sb_publishable_gFpVYJctaDkRRgttvnUl-A_TKu81hFF',
);

final GlobalKey<NavigatorState> _rootNavigatorKey = GlobalKey<NavigatorState>();

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Supabase.initialize(
    url: _supabaseUrl,
    publishableKey: _supabasePublishableKey,
    debug: !kReleaseMode,
  );
  setupLocator();
  await locator<AppIconController>().initialize();
  runApp(const ChatyApp());
}

class ChatyApp extends StatefulWidget {
  const ChatyApp({super.key});

  @override
  State<ChatyApp> createState() => _ChatyAppState();
}

class _ChatyAppState extends State<ChatyApp> with WidgetsBindingObserver {
  late final ThemeController _themeController;
  late final ChatyPreferencesController _preferencesController;
  late final AppearanceVariantController _appearanceController;
  late final ChatyBackendService _backend;
  late final LocalLockService _lockService;
  late final ChatyNotificationService _notificationService;
  late final ContactRelationshipService _relationshipService;
  late final MessageAutomationService _automationService;
  late final StreamSubscription<AuthState> _authUiSubscription;
  bool _recoveryRouteOpen = false;
  bool _appLockRequired = false;
  bool _initialAppLockScheduled = false;
  DateTime? _backgroundedAt;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _themeController = locator<ThemeController>();
    _preferencesController = locator<ChatyPreferencesController>();
    _appearanceController = locator<AppearanceVariantController>();
    _backend = locator<ChatyBackendService>();
    _lockService = locator<LocalLockService>();
    _notificationService = locator<ChatyNotificationService>();
    _relationshipService = locator<ContactRelationshipService>();
    _preferencesController.addListener(_handleSecurityPreferenceChanged);
    _automationService = MessageAutomationService(
      preferencesController: _preferencesController,
      dataStore: locator<MockDataStore>(),
    );
    _authUiSubscription = Supabase.instance.client.auth.onAuthStateChange.listen(_handleAuthUiEvent);
    if (Supabase.instance.client.auth.currentSession != null) {
      unawaited(_registerCurrentDevice());
    }
  }

  Future<void> _registerCurrentDevice() async {
    try {
      await _relationshipService.registerCurrentDevice();
    } catch (error) {
      debugPrint('Chaty device registration skipped: $error');
    }
  }

  void _handleSecurityPreferenceChanged() {
    if (_preferencesController.security.isAppLockEnabled) return;
    _initialAppLockScheduled = false;
    _backgroundedAt = null;
    if (_appLockRequired && mounted) {
      setState(() => _appLockRequired = false);
    }
  }

  void _scheduleInitialAppLockIfNeeded() {
    if (!_backend.isAuthenticated || !_preferencesController.security.isAppLockEnabled) return;
    if (_initialAppLockScheduled || _appLockRequired) return;
    _initialAppLockScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_backend.isAuthenticated || !_preferencesController.security.isAppLockEnabled) return;
      setState(() => _appLockRequired = true);
    });
  }

  Duration _autoLockDelay(String value) {
    switch (value) {
      case '15s':
        return const Duration(seconds: 15);
      case '30s':
        return const Duration(seconds: 30);
      case '1m':
        return const Duration(minutes: 1);
      case '5m':
        return const Duration(minutes: 5);
      case '15m':
        return const Duration(minutes: 15);
      case 'Immediately':
      default:
        return Duration.zero;
    }
  }

  void _applyAutoLockOnResume() {
    if (!_backend.isAuthenticated || !_preferencesController.security.isAppLockEnabled) {
      _backgroundedAt = null;
      return;
    }
    final backgroundedAt = _backgroundedAt;
    _backgroundedAt = null;
    if (backgroundedAt == null) return;
    final elapsed = DateTime.now().difference(backgroundedAt);
    final delay = _autoLockDelay(_preferencesController.security.autoLockTimeout);
    if (elapsed >= delay && mounted && !_appLockRequired) {
      setState(() => _appLockRequired = true);
    }
  }

  void _handleAppUnlocked() {
    if (!mounted) return;
    setState(() => _appLockRequired = false);
  }

  void _handleAuthUiEvent(AuthState state) {
    if (state.session != null) unawaited(_registerCurrentDevice());
    if (state.event == AuthChangeEvent.passwordRecovery && !_recoveryRouteOpen) {
      _recoveryRouteOpen = true;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        final navigator = _rootNavigatorKey.currentState;
        if (navigator == null) {
          _recoveryRouteOpen = false;
          return;
        }
        navigator.pushAndRemoveUntil(
          MaterialPageRoute(builder: (_) => CreateNewPasswordScreen(email: state.session?.user.email ?? '')),
          (route) => false,
        );
      });
      return;
    }
    if (state.event == AuthChangeEvent.signedOut) {
      _initialAppLockScheduled = false;
      _backgroundedAt = null;
      if (mounted && _appLockRequired) setState(() => _appLockRequired = false);
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _rootNavigatorKey.currentState?.pushAndRemoveUntil(
          MaterialPageRoute(builder: (_) => const WelcomeScreen()),
          (route) => false,
        );
      });
    }
  }

  @override
  void didChangePlatformBrightness() {
    super.didChangePlatformBrightness();
    final brightness = WidgetsBinding.instance.platformDispatcher.platformBrightness;
    if (_themeController.globalTheme.id == 'monochrome_dark' || _themeController.globalTheme.id == 'monochrome_light') {
      _themeController.setGlobalTheme(ThemePresets.getSystemDefaultTheme(brightness));
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    super.didChangeAppLifecycleState(state);

    if (state == AppLifecycleState.paused || state == AppLifecycleState.hidden || state == AppLifecycleState.detached) {
      _backgroundedAt ??= DateTime.now();
    } else if (state == AppLifecycleState.resumed) {
      _applyAutoLockOnResume();
      if (_backend.isAuthenticated) unawaited(_registerCurrentDevice());
    }

    if (!_backend.isAuthenticated) return;
    final airplane = _preferencesController.home.airplaneModeSimulator || _preferencesController.gbBool('yo_want_airplanemode');
    final ghost = _preferencesController.home.ghostMode || _preferencesController.gbBool('yo_want_ghostmode');
    final alwaysOnline = _preferencesController.gbBool('always_online');
    if (airplane || ghost) {
      unawaited(_backend.setPresence(PresenceState.offline));
      return;
    }
    if (state == AppLifecycleState.resumed || alwaysOnline) {
      unawaited(_backend.setPresence(PresenceState.online));
    } else if (state == AppLifecycleState.paused || state == AppLifecycleState.detached || state == AppLifecycleState.hidden) {
      unawaited(_backend.setPresence(PresenceState.offline));
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _preferencesController.removeListener(_handleSecurityPreferenceChanged);
    unawaited(_authUiSubscription.cancel());
    _automationService.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge(<Listenable>[_themeController, _preferencesController, _appearanceController, _backend]),
      builder: (context, _) {
        _scheduleInitialAppLockIfNeeded();
        final currentTheme = GbThemeOverrides.resolve(_themeController.globalTheme, _preferencesController);
        return MaterialApp(
          navigatorKey: _rootNavigatorKey,
          title: 'Chaty',
          debugShowCheckedModeBanner: false,
          theme: currentTheme.toThemeData(),
          builder: (context, child) {
            final media = MediaQuery.of(context);
            final scaled = media.copyWith(
              textScaler: TextScaler.linear((media.textScaler.scale(1.0) * _appearanceController.textScale).clamp(0.8, 1.6)),
            );
            final appContent = MediaQuery(
              data: scaled,
              child: ChatyEventToastOverlay(
                notificationService: _notificationService,
                preferencesController: _preferencesController,
                child: ClickParticleOverlay(
                  preferencesController: _preferencesController,
                  child: FallingParticlesOverlay(
                    preferencesController: _preferencesController,
                    currentScope: 'Home',
                    child: child ?? const SizedBox(),
                  ),
                ),
              ),
            );
            final shouldShowLock = _backend.isAuthenticated &&
                _preferencesController.security.isAppLockEnabled &&
                _appLockRequired;
            if (!shouldShowLock) return appContent;
            return Stack(
              fit: StackFit.expand,
              children: [
                appContent,
                AppLockOverlayModal(
                  preferencesController: _preferencesController,
                  lockService: _lockService,
                  title: 'Chaty Locked',
                  reason: 'Authenticate to unlock Chaty',
                  onUnlocked: _handleAppUnlocked,
                ),
              ],
            );
          },
          home: const SplashScreen(),
        );
      },
    );
  }
}
