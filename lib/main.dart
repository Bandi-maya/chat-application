import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import 'package:chat/data/repositories/chaty_data_store.dart';
import 'package:chat/data/services/backend_service.dart';
import 'package:chat/data/services/call_signaling_service.dart';
import 'package:chat/data/services/notification_service.dart';
import 'package:chat/data/services/contact_relationship_service.dart';
import 'package:chat/data/services/local_lock_service.dart';
import 'package:chat/data/services/protected_resource_gate.dart';
import 'package:chat/data/services/message_automation_service.dart';
import 'package:chat/data/services/rich_chat_realtime_service.dart';
import 'package:chat/data/services/status_service.dart';
import 'package:chat/data/services/push_token_service.dart';
import 'package:chat/data/services/notification_channel_manager.dart';
import 'package:chat/domain/models/call_state.dart';
import 'package:chat/domain/models/user_profile.dart';
import 'package:chat/features/auth/create_new_password_screen.dart';
import 'package:chat/features/auth/welcome_screen.dart';
import 'package:chat/features/chats/main_navigation_shell.dart';
import 'package:chat/features/calls/ongoing_call_screen.dart';
import 'package:chat/features/settings/security/app_lock_overlay.dart';
import 'package:chat/features/settings/security/security_center_screen.dart';
import 'package:chat/injection/locator.dart';
import 'package:chat/ui/core/design_system/design_system.dart';
import 'package:chat/ui/core/controllers/app_icon_controller.dart';
import 'package:chat/ui/core/controllers/appearance_variant_controller.dart';
import 'package:chat/ui/core/controllers/preferences_controller.dart';
import 'package:chat/ui/core/gb/gb_theme_overrides.dart';
import 'package:chat/ui/core/widgets/app_avatar.dart';
import 'package:chat/ui/core/widgets/event_toast_overlay.dart';
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
  // Only work that shapes the very first frame blocks launch. The shell must
  // paint from local state immediately; platform services catch up right
  // after the first frame instead of gating it.
  await locator<ThemeController>().init();
  runApp(const ChatyApp());
  WidgetsBinding.instance.addPostFrameCallback((_) {
    unawaited(_initializeDeferredPlatformServices());
  });
}

/// Noncritical startup work deferred until after the first frame so cold
/// start renders the cached shell immediately. Each step fails soft: a
/// skipped service must never take the app down during boot.
Future<void> _initializeDeferredPlatformServices() async {
  try {
    await locator<AppIconController>().initialize();
  } catch (error) {
    debugPrint('Chaty app icon bootstrap skipped: $error');
  }
  try {
    await locator<NotificationChannelManager>().initialize();
  } catch (error) {
    debugPrint('Chaty notification channel bootstrap skipped: $error');
  }
  try {
    await locator<PushTokenService>().initialize();
  } catch (error) {
    debugPrint('Chaty push token bootstrap skipped: $error');
  }
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
  late final CallSignalingService _callService;
  late final LocalLockService _lockService;
  late final ChatyNotificationService _notificationService;
  late final ContactRelationshipService _relationshipService;
  late final MessageAutomationService _automationService;
  late final StatusService _statusService;
  late final StreamSubscription<AuthState> _authUiSubscription;

  /// Single merged signal for every global surface the root rebuilds on
  /// (theme, preferences, appearance, backend, realtime, calls and the
  /// presented-call-screen counter).
  late final Listenable _rootSignals;
  bool _recoveryRouteOpen = false;
  bool _appLockRequired = false;
  bool _initialAppLockScheduled = false;
  bool _checkingLockCapability = false;
  bool _freshLoginSession = false;
  bool _postLoginAppLockPromptScheduled = false;
  bool _postLoginAppLockPromptShown = false;
  DateTime? _backgroundedAt;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _themeController = locator<ThemeController>();
    _rootSignals = Listenable.merge(<Listenable>[
      _themeController,
      locator<ChatyPreferencesController>(),
      locator<AppearanceVariantController>(),
      locator<ChatyBackendService>(),
      locator<RichChatRealtimeService>(),
      locator<CallSignalingService>(),
      // Presence of the full call screen: the minimized-call capsule hides
      // itself while OngoingCallScreen is presented.
      OngoingCallScreen.presentedInstances,
    ]);
    _preferencesController = locator<ChatyPreferencesController>();
    _appearanceController = locator<AppearanceVariantController>();
    _backend = locator<ChatyBackendService>();
    unawaited(
      _backend.initialize().catchError((Object error, StackTrace stackTrace) {
        debugPrint('Chaty backend bootstrap failed: $error\n$stackTrace');
      }),
    );
    _callService = locator<CallSignalingService>();
    unawaited(
      _callService.initialize().catchError((
        Object error,
        StackTrace stackTrace,
      ) {
        debugPrint(
          'Chaty call signaling bootstrap failed: $error\n$stackTrace',
        );
      }),
    );
    _lockService = locator<LocalLockService>();
    _notificationService = locator<ChatyNotificationService>();
    _relationshipService = locator<ContactRelationshipService>();
    _preferencesController.addListener(_handleSecurityPreferenceChanged);
    _statusService = StatusService();
    if (Supabase.instance.client.auth.currentSession != null) {
      _statusService.startRevocationWatch();
    }
    _automationService = MessageAutomationService(
      preferencesController: _preferencesController,
      dataStore: locator<ChatyDataStore>(),
    );
    _authUiSubscription = Supabase.instance.client.auth.onAuthStateChange
        .listen(_handleAuthUiEvent);
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
    if (_appLockRequired && mounted) setState(() => _appLockRequired = false);
  }

  Future<bool> _isCurrentLockMethodReady() async {
    final security = _preferencesController.security;
    if (!security.isAppLockEnabled) return false;
    switch (security.lockMethod) {
      case 'PIN':
      case 'Pattern':
      case 'Password':
        return _lockService.hasCredential(security.lockMethod);
      case 'Biometric':
        return _lockService.canUseBiometrics();
      case 'Device Credential':
        return true;
      default:
        return false;
    }
  }

  void _scheduleInitialAppLockIfNeeded() {
    if (!_backend.isAuthenticated ||
        !_preferencesController.security.isAppLockEnabled) {
      return;
    }
    if (_freshLoginSession ||
        _initialAppLockScheduled ||
        _appLockRequired ||
        _checkingLockCapability) {
      return;
    }
    _checkingLockCapability = true;
    unawaited(() async {
      final ready = await _isCurrentLockMethodReady();
      if (!mounted) return;
      _checkingLockCapability = false;
      if (!ready ||
          _freshLoginSession ||
          !_backend.isAuthenticated ||
          !_preferencesController.security.isAppLockEnabled) {
        return;
      }
      _initialAppLockScheduled = true;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted ||
            _freshLoginSession ||
            !_backend.isAuthenticated ||
            !_preferencesController.security.isAppLockEnabled) {
          return;
        }
        setState(() => _appLockRequired = true);
      });
    }());
  }

  void _schedulePostLoginAppLockPrompt() {
    if (_postLoginAppLockPromptScheduled || _postLoginAppLockPromptShown) {
      return;
    }
    _postLoginAppLockPromptScheduled = true;
    Future<void>.delayed(const Duration(milliseconds: 650), () async {
      _postLoginAppLockPromptScheduled = false;
      if (!mounted ||
          !_backend.isAuthenticated ||
          _postLoginAppLockPromptShown) {
        return;
      }
      _postLoginAppLockPromptShown = true;

      if (_preferencesController.security.isAppLockEnabled &&
          await _isCurrentLockMethodReady()) {
        return;
      }
      if (!mounted) return;
      final navigator = _rootNavigatorKey.currentState;
      final context = _rootNavigatorKey.currentContext;
      if (navigator == null || context == null) return;

      final openSettings = await showDialog<bool>(
        context: context,
        barrierDismissible: true,
        builder: (dialogContext) => AlertDialog(
          title: const Row(
            children: [
              Icon(Icons.lock_outline_rounded),
              SizedBox(width: 10),
              Expanded(child: Text('Enable App Lock?')),
            ],
          ),
          content: const Text(
            'You can protect Chaty with biometrics, device lock, PIN, pattern, or password. Setup is optional and can be changed anytime in Settings.',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(false),
              child: const Text('Not now'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(dialogContext).pop(true),
              child: const Text('Open settings'),
            ),
          ],
        ),
      );
      if (openSettings == true && mounted) {
        navigator.push(
          MaterialPageRoute(
            builder: (_) => SecurityCenterScreen(
              preferencesController: _preferencesController,
            ),
          ),
        );
      }
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
      default:
        return Duration.zero;
    }
  }

  void _applyAutoLockOnResume() {
    if (!_backend.isAuthenticated ||
        !_preferencesController.security.isAppLockEnabled) {
      _backgroundedAt = null;
      return;
    }
    final backgroundedAt = _backgroundedAt;
    _backgroundedAt = null;
    if (backgroundedAt == null) return;
    if (DateTime.now().difference(backgroundedAt) <
        _autoLockDelay(_preferencesController.security.autoLockTimeout)) {
      return;
    }
    if (_appLockRequired || _checkingLockCapability) return;
    _checkingLockCapability = true;
    unawaited(() async {
      final ready = await _isCurrentLockMethodReady();
      if (!mounted) return;
      _checkingLockCapability = false;
      if (ready &&
          _backend.isAuthenticated &&
          _preferencesController.security.isAppLockEnabled &&
          !_appLockRequired) {
        setState(() => _appLockRequired = true);
      }
    }());
  }

  void _handleAppUnlocked() {
    if (mounted) setState(() => _appLockRequired = false);
  }

  /// Minimized-call surface for the global activity layer. Visible while a
  /// live outgoing/connected session exists and the full call screen is not
  /// presented; null otherwise.
  Widget? _buildMinimizedCallCapsule(ThemeConfig theme) {
    final session = _callService.currentSession;
    if (session == null || !_backend.isAuthenticated) return null;
    switch (session.state) {
      case CallSessionState.initiating:
      case CallSessionState.ringing:
      case CallSessionState.connecting:
      case CallSessionState.connected:
      case CallSessionState.reconnecting:
        break;
      case CallSessionState.incoming:
      case CallSessionState.idle:
      case CallSessionState.declined:
      case CallSessionState.busy:
      case CallSessionState.missed:
      case CallSessionState.ended:
      case CallSessionState.failed:
        return null;
    }
    if (OngoingCallScreen.presentedInstances.value > 0) return null;
    return ChatyCallActivityCapsule(
      contactName: session.remoteDisplayName,
      status: _minimizedCallStatus(session),
      isVideo: session.isVideo,
      isSpeaker: session.audioRoute == AudioRouteType.speaker,
      onOpen: () => unawaited(_openOngoingCall(theme)),
      onToggleSpeaker: () => unawaited(() async {
        try {
          await _callService.setAudioRoute(
            session.audioRoute == AudioRouteType.speaker
                ? AudioRouteType.earpiece
                : AudioRouteType.speaker,
          );
        } catch (error) {
          debugPrint('Chaty audio route switch failed: $error');
        }
      }()),
      onHangUp: () => unawaited(() async {
        try {
          await _callService.endCall();
        } catch (error) {
          debugPrint('Chaty end call failed: $error');
        }
      }()),
    );
  }

  String _minimizedCallStatus(ChatyCallSession session) {
    switch (session.state) {
      case CallSessionState.initiating:
        return 'Calling…';
      case CallSessionState.ringing:
        return session.isOutgoing ? 'Ringing…' : 'Connecting…';
      case CallSessionState.connecting:
        return 'Connecting…';
      case CallSessionState.reconnecting:
        return 'Reconnecting…';
      case CallSessionState.connected:
        return session.isMuted ? 'Connected · muted' : 'Connected';
      default:
        return 'Call';
    }
  }

  Future<void> _openOngoingCall(ThemeConfig theme) async {
    final navigator = _rootNavigatorKey.currentState;
    if (navigator == null) return;
    if (OngoingCallScreen.presentedInstances.value > 0) return;
    await navigator.push(
      MaterialPageRoute(builder: (_) => OngoingCallScreen(theme: theme)),
    );
  }

  void _handleAuthUiEvent(AuthState state) {
    if (state.session != null) unawaited(_registerCurrentDevice());
    if (state.event == AuthChangeEvent.passwordRecovery &&
        !_recoveryRouteOpen) {
      _recoveryRouteOpen = true;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        final navigator = _rootNavigatorKey.currentState;
        if (navigator == null) {
          _recoveryRouteOpen = false;
          return;
        }
        navigator.pushAndRemoveUntil(
          MaterialPageRoute(
            builder: (_) =>
                CreateNewPasswordScreen(email: state.session?.user.email ?? ''),
          ),
          (route) => false,
        );
      });
      return;
    }
    if (state.event == AuthChangeEvent.signedIn) {
      _statusService.resetRevocationTracking();
      _statusService.startRevocationWatch();
      _freshLoginSession = true;
      _initialAppLockScheduled = false;
      _backgroundedAt = null;
      if (_appLockRequired && mounted) setState(() => _appLockRequired = false);
      _schedulePostLoginAppLockPrompt();
    }
    if (state.event == AuthChangeEvent.signedOut) {
      _statusService.stopRevocationWatch();
      _freshLoginSession = false;
      _initialAppLockScheduled = false;
      _postLoginAppLockPromptScheduled = false;
      _postLoginAppLockPromptShown = false;
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
    final brightness =
        WidgetsBinding.instance.platformDispatcher.platformBrightness;
    if (_themeController.globalTheme.id == 'monochrome_dark' ||
        _themeController.globalTheme.id == 'monochrome_light') {
      _themeController.setGlobalTheme(
        ThemePresets.getSystemDefaultTheme(brightness),
      );
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    super.didChangeAppLifecycleState(state);
    if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.hidden ||
        state == AppLifecycleState.detached) {
      _backgroundedAt ??= DateTime.now();
      ProtectedResourceGate.invalidateAllSessions();
    } else if (state == AppLifecycleState.resumed) {
      _freshLoginSession = false;
      _applyAutoLockOnResume();
      if (_backend.isAuthenticated) unawaited(_registerCurrentDevice());
    }

    if (!_backend.isAuthenticated) return;
    final airplane =
        _preferencesController.home.airplaneModeSimulator ||
        _preferencesController.gbBool('yo_want_airplanemode');
    final ghost =
        _preferencesController.home.ghostMode ||
        _preferencesController.gbBool('yo_want_ghostmode');
    final alwaysOnline = _preferencesController.gbBool('always_online');
    if (airplane || ghost) {
      unawaited(_backend.setPresence(PresenceState.offline));
      return;
    }
    if (state == AppLifecycleState.resumed || alwaysOnline) {
      unawaited(_backend.setPresence(PresenceState.online));
    } else if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.detached ||
        state == AppLifecycleState.hidden) {
      unawaited(_backend.setPresence(PresenceState.offline));
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _preferencesController.removeListener(_handleSecurityPreferenceChanged);
    unawaited(_authUiSubscription.cancel());
    _automationService.dispose();
    _statusService.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: _rootSignals,
      builder: (context, _) {
        _scheduleInitialAppLockIfNeeded();
        final currentTheme = GbThemeOverrides.resolve(
          _themeController.globalTheme,
          _preferencesController,
        );
        return MaterialApp(
          navigatorKey: _rootNavigatorKey,
          title: 'Chaty',
          debugShowCheckedModeBanner: false,
          theme: currentTheme.toThemeData().copyWith(
            pageTransitionsTheme: ChatyTransitions.build(
              entry: _appearanceController.entryAnimation,
              exit: _appearanceController.exitAnimation,
            ),
          ),
          builder: (context, child) {
            final media = MediaQuery.of(context);
            final scaled = media.copyWith(
              textScaler: TextScaler.linear(
                (media.textScaler.scale(1.0) * _appearanceController.textScale)
                    .clamp(0.8, 1.6),
              ),
            );
            final shouldShowLock =
                _backend.isAuthenticated &&
                _preferencesController.security.isAppLockEnabled &&
                _appLockRequired;
            final appContent = MediaQuery(
              data: scaled,
              child: ChatyEventToastOverlay(
                notificationService: _notificationService,
                preferencesController: _preferencesController,
                concealWhileLocked:
                    shouldShowLock &&
                    _preferencesController.security.hideLockNotificationContent,
                // Global activity surfaces (minimized call, later uploads /
                // recording / sync) render above every route through this
                // single host instead of per-screen overlays.
                child: ChatyGlobalActivityHost(
                  primaryActivity: _buildMinimizedCallCapsule(currentTheme),
                  child: ClickParticleOverlay(
                    preferencesController: _preferencesController,
                    child: FallingParticlesOverlay(
                      preferencesController: _preferencesController,
                      currentScope: 'Home',
                      child: child ?? const SizedBox(),
                    ),
                  ),
                ),
              ),
            );

            // The RLS-protected call row is the only ringing source. The server
            // already enforces block/contact/Who-Can-Call-Me rules before this
            // state can exist, so a modified Flutter client cannot bypass them.
            final callSession = _callService.currentSession;
            final incomingCall = callSession?.state == CallSessionState.incoming
                ? callSession
                : null;
            final showIncoming =
                incomingCall != null && _backend.isAuthenticated;
            if (!shouldShowLock && !showIncoming) return appContent;
            return Stack(
              fit: StackFit.expand,
              children: [
                appContent,
                if (showIncoming)
                  _IncomingCallOverlay(
                    call: incomingCall,
                    theme: currentTheme,
                    onAccept: () {
                      unawaited(() async {
                        try {
                          await _callService.acceptCall();
                          if (!mounted) return;
                          _rootNavigatorKey.currentState?.push(
                            MaterialPageRoute(
                              builder: (_) =>
                                  OngoingCallScreen(theme: currentTheme),
                            ),
                          );
                        } catch (error) {
                          final callContext = _rootNavigatorKey.currentContext;
                          if (callContext != null) {
                            ScaffoldMessenger.of(callContext).showSnackBar(
                              SnackBar(
                                content: Text(
                                  'Unable to answer call: ${error.toString().replaceFirst('Exception: ', '')}',
                                ),
                              ),
                            );
                          }
                        }
                      }());
                    },
                    onDecline: () => unawaited(_callService.declineCall()),
                  ),
                if (shouldShowLock)
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
          home: Supabase.instance.client.auth.currentSession != null
              ? const MainNavigationShell()
              : const WelcomeScreen(),
        );
      },
    );
  }
}

class _IncomingCallOverlay extends StatelessWidget {
  final ChatyCallSession call;
  final ThemeConfig theme;
  final VoidCallback onAccept;
  final VoidCallback onDecline;

  const _IncomingCallOverlay({
    required this.call,
    required this.theme,
    required this.onAccept,
    required this.onDecline,
  });

  @override
  Widget build(BuildContext context) {
    final fallbackInitials = call.remoteDisplayName.characters
        .take(2)
        .toString()
        .toUpperCase();
    return Align(
      alignment: Alignment.bottomCenter,
      child: SafeArea(
        child: Container(
          margin: const EdgeInsets.fromLTRB(14, 0, 14, 16),
          padding: const EdgeInsets.fromLTRB(16, 14, 16, 14),
          decoration: BoxDecoration(
            color: theme.surfaceColor,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(color: theme.accentColor.withValues(alpha: 0.4)),
            boxShadow: <BoxShadow>[
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.35),
                blurRadius: 24,
                offset: const Offset(0, 8),
              ),
            ],
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                children: [
                  AppAvatar(
                    initials: call.remoteAvatarInitials ?? fallbackInitials,
                    colorHex: call.remoteAvatarColorHex ?? '0xFF6366F1',
                    size: 44,
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          call.remoteDisplayName,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            color: theme.primaryTextColor,
                            fontWeight: FontWeight.w800,
                            fontSize: 15.5,
                          ),
                        ),
                        Text(
                          call.isVideo
                              ? 'Incoming video call'
                              : 'Incoming voice call',
                          style: TextStyle(
                            color: theme.secondaryTextColor,
                            fontSize: 11.5,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Icon(
                    call.isVideo ? Icons.videocam_rounded : Icons.call_rounded,
                    color: theme.accentColor,
                  ),
                ],
              ),
              const SizedBox(height: 14),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton.icon(
                      style: OutlinedButton.styleFrom(
                        foregroundColor: theme.dangerColor,
                        side: BorderSide(
                          color: theme.dangerColor.withValues(alpha: 0.5),
                        ),
                      ),
                      onPressed: onDecline,
                      icon: const Icon(Icons.call_end_rounded),
                      label: const Text('Decline'),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: FilledButton.icon(
                      style: FilledButton.styleFrom(
                        backgroundColor: theme.successColor,
                        foregroundColor: Colors.white,
                      ),
                      onPressed: onAccept,
                      icon: const Icon(Icons.call_rounded),
                      label: const Text('Accept'),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
