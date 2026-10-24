import 'dart:async';
import 'dart:io' show Platform;
import 'package:flutter/foundation.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';

import '../../injection/locator.dart';
import '../../data/services/api_backend_service.dart';
import '../../data/services/push_token_service.dart';

/// Top-level background message handler invoked by Firebase Messaging
/// when the app is in the background or terminated.
@pragma('vm:entry-point')
Future<void> firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  try {
    await Firebase.initializeApp();
  } catch (_) {}
  debugPrint('[FirebaseMessaging] Handling background message: ${message.messageId} | data: ${message.data}');
}

class FirebaseNotificationService extends ChangeNotifier {
  static final FirebaseNotificationService _instance = FirebaseNotificationService._internal();
  factory FirebaseNotificationService() => _instance;
  FirebaseNotificationService._internal();

  FirebaseMessaging? _messaging;
  String? _fcmToken;
  bool _isInitialized = false;

  String? get fcmToken => _fcmToken;
  bool get isInitialized => _isInitialized;

  /// Initializes Firebase and FCM notification listeners
  Future<void> initialize() async {
    if (_isInitialized) return;

    try {
      await Firebase.initializeApp();
      _messaging = FirebaseMessaging.instance;
      _isInitialized = true;
      debugPrint('[FirebaseNotificationService] Firebase initialized successfully');
    } catch (e) {
      debugPrint('[FirebaseNotificationService] Firebase.initializeApp() skipped or failed: $e');
      return;
    }

    try {
      // Set background handler
      FirebaseMessaging.onBackgroundMessage(firebaseMessagingBackgroundHandler);

      // Request user permissions (iOS / Android 13+)
      final settings = await _messaging!.requestPermission(
        alert: true,
        announcement: false,
        badge: true,
        carPlay: false,
        criticalAlert: false,
        provisional: false,
        sound: true,
      );
      debugPrint('[FirebaseNotificationService] Notification permission status: ${settings.authorizationStatus}');

      // Configure foreground presentation options (for iOS / modern Android)
      await _messaging!.setForegroundNotificationPresentationOptions(
        alert: true,
        badge: true,
        sound: true,
      );

      // Retrieve FCM Token
      await _fetchAndRegisterToken();

      // Listen for token refreshes
      _messaging!.onTokenRefresh.listen((newToken) {
        debugPrint('[FirebaseNotificationService] FCM token refreshed: $newToken');
        _fcmToken = newToken;
        _registerWithBackends(newToken);
        notifyListeners();
      });

      // Foreground message listener
      FirebaseMessaging.onMessage.listen((RemoteMessage message) {
        debugPrint('[FirebaseNotificationService] Foreground message received: ${message.notification?.title} - ${message.notification?.body}');
        _handleForegroundMessage(message);
      });

      // Notification opened while in background
      FirebaseMessaging.onMessageOpenedApp.listen((RemoteMessage message) {
        debugPrint('[FirebaseNotificationService] Notification opened from background: ${message.data}');
        _handleNotificationClick(message);
      });

      // Notification opened when app was terminated
      final initialMessage = await _messaging!.getInitialMessage();
      if (initialMessage != null) {
        debugPrint('[FirebaseNotificationService] App launched via initial notification: ${initialMessage.data}');
        _handleNotificationClick(initialMessage);
      }
    } catch (e) {
      debugPrint('[FirebaseNotificationService] Error setting up FCM listeners: $e');
    }
  }

  Future<void> _fetchAndRegisterToken() async {
    try {
      if (_messaging == null) return;
      final token = await _messaging!.getToken();
      if (token != null && token.isNotEmpty) {
        _fcmToken = token;
        debugPrint('[FirebaseNotificationService] FCM Device Token: $token');
        await _registerWithBackends(token);
        notifyListeners();
      }
    } catch (e) {
      debugPrint('[FirebaseNotificationService] Failed to get FCM token: $e');
    }
  }

  /// Sends the FCM token to both api-hub and PushTokenService
  Future<void> _registerWithBackends(String token) async {
    final platform = kIsWeb
        ? 'web'
        : Platform.isAndroid
            ? 'android'
            : Platform.isIOS
                ? 'ios'
                : 'unknown';

    // 1. Register with api-hub (FastAPI backend)
    try {
      if (locator.isRegistered<ApiBackendService>()) {
        final api = locator<ApiBackendService>();
        if (api.isAuthenticated) {
          await api.registerDeviceToken(token, deviceType: platform);
        }
      }
    } catch (e) {
      debugPrint('[FirebaseNotificationService] Failed to register token with api-hub: $e');
    }

    // 2. Register with PushTokenService (Supabase device registry)
    try {
      if (locator.isRegistered<PushTokenService>()) {
        final pushTokenService = locator<PushTokenService>();
        await pushTokenService.registerPlatformToken(token);
      }
    } catch (e) {
      debugPrint('[FirebaseNotificationService] Failed to register token with PushTokenService: $e');
    }
  }

  /// Syncs current token when user logs into api-hub
  Future<void> syncTokenOnLogin() async {
    if (_fcmToken != null && _fcmToken!.isNotEmpty) {
      await _registerWithBackends(_fcmToken!);
    } else {
      await _fetchAndRegisterToken();
    }
  }

  /// Unregisters token on sign out
  Future<void> unregisterOnLogout() async {
    if (_fcmToken != null && _fcmToken!.isNotEmpty) {
      try {
        if (locator.isRegistered<ApiBackendService>()) {
          final api = locator<ApiBackendService>();
          await api.unregisterDeviceToken(_fcmToken!);
        }
      } catch (_) {}
    }
  }

  void _handleForegroundMessage(RemoteMessage message) {
    // Message received in foreground — real-time webSocket or UI toast handles display
    debugPrint('[FirebaseNotificationService] Foreground notification: ${message.notification?.title}: ${message.notification?.body}');
  }

  void _handleNotificationClick(RemoteMessage message) {
    final roomId = message.data['room_id']?.toString() ?? message.data['conversation_id']?.toString();
    if (roomId != null && roomId.isNotEmpty) {
      debugPrint('[FirebaseNotificationService] Should navigate to room: $roomId');
    }
  }
}
