import 'dart:async';
import 'dart:io' show Platform;
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

/// Manages push notification token lifecycle:
/// - Registration of a real platform push token into the Supabase backend
///   (`linked_devices` / `device_push_tokens`)
/// - Token refresh updates
/// - Automatic revocation on logout and switching accounts to prevent
///   cross-account notification leakage
///
/// Chaty never fabricates transport tokens. A token only exists once a real
/// push transport (FCM/APNs) delivers one to [registerPlatformToken].
class PushTokenService extends ChangeNotifier {
  static const String _pushTokenStorageKey = 'chaty.device_push_token.v1';
  static const String _registeredUserIdKey = 'chaty.registered_push_user_id.v1';

  final SupabaseClient _client;
  final FlutterSecureStorage _secureStorage;

  String? _currentToken;
  bool _isRegistered = false;

  PushTokenService({
    SupabaseClient? client,
    FlutterSecureStorage? secureStorage,
  }) : _client = client ?? Supabase.instance.client,
       _secureStorage = secureStorage ?? const FlutterSecureStorage();

  String? get currentToken => _currentToken;
  bool get isRegistered => _isRegistered;

  /// Whether a real push transport is integrated for this build.
  ///
  /// Flip to true (and feed tokens into [registerPlatformToken]) when a
  /// transport such as firebase_messaging is added. While false, no device row
  /// is written and no identifier is invented — the backend must never receive
  /// a pseudo-token that cannot actually deliver a notification.
  bool get hasRealPushTransport => false;

  /// Returns the current active platform string.
  String get platformName {
    if (kIsWeb) return 'web';
    if (Platform.isAndroid) return 'android';
    if (Platform.isIOS) return 'ios';
    if (Platform.isMacOS) return 'macos';
    if (Platform.isWindows) return 'windows';
    if (Platform.isLinux) return 'linux';
    return 'unknown';
  }

  /// Initialize token service for the current session.
  ///
  /// Without a real push transport this is an explicit no-op that leaves the
  /// service unregistered instead of impersonating a transport token.
  Future<void> initialize() async {
    final session = _client.auth.currentSession;
    if (!hasRealPushTransport || session == null) {
      _currentToken = null;
      _isRegistered = false;
      return;
    }

    // A real transport exists: reuse its persisted token when present and
    // re-register after an account switch or fresh install.
    final token = await _secureStorage.read(key: _pushTokenStorageKey);
    if (token == null || token.isEmpty) {
      _currentToken = null;
      _isRegistered = false;
      return;
    }
    _currentToken = token;

    final registeredUser = await _secureStorage.read(key: _registeredUserIdKey);
    if (registeredUser != session.user.id) {
      await registerPlatformToken(token);
      return;
    }
    _isRegistered = true;
    notifyListeners();
  }

  /// Registers or updates a real platform push token against the
  /// authenticated user in Supabase. Called by the integrated transport on
  /// first token delivery and on every token refresh.
  Future<void> registerPlatformToken(String pushToken) async {
    final user = _client.auth.currentUser;
    if (user == null) return;

    _currentToken = pushToken;
    await _secureStorage.write(key: _pushTokenStorageKey, value: pushToken);

    try {
      // Update linked_devices with active push token & platform info
      await _client.from('linked_devices').upsert(<String, dynamic>{
        'user_id': user.id,
        'device_id': pushToken,
        'device_name': '$platformName Chaty Device',
        'platform': platformName,
        'location': '',
        'last_active_at': DateTime.now().toUtc().toIso8601String(),
        'revoked_at': null,
      }, onConflict: 'user_id,device_id');

      await _secureStorage.write(key: _registeredUserIdKey, value: user.id);
      _isRegistered = true;
      notifyListeners();
    } catch (error) {
      debugPrint('PushTokenService: failed to register device token: $error');
    }
  }

  /// Refreshes push token and updates server
  Future<void> onTokenRefresh(String newToken) async {
    if (newToken.isEmpty || newToken == _currentToken) return;
    await registerPlatformToken(newToken);
  }

  /// Revokes device push token on logout to prevent cross-account leak
  Future<void> revokeTokenOnLogout() async {
    final user = _client.auth.currentUser;
    final token = _currentToken;

    if (user != null && token != null) {
      try {
        await _client
            .from('linked_devices')
            .update(<String, dynamic>{
              'revoked_at': DateTime.now().toUtc().toIso8601String(),
            })
            .eq('user_id', user.id)
            .eq('device_id', token);
      } catch (e) {
        debugPrint('PushTokenService: error revoking token on logout: $e');
      }
    }

    await _secureStorage.delete(key: _registeredUserIdKey);
    _isRegistered = false;
    notifyListeners();
  }
}
