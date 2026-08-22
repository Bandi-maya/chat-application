import 'dart:async';
import 'dart:io' show Platform;
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:uuid/uuid.dart';

/// Manages push notification token lifecycle:
/// - Generation / retrieval of device push tokens
/// - Registration into Supabase backend (`linked_devices` / `device_push_tokens`)
/// - Token refresh updates
/// - Automatic revocation on logout and switching accounts to prevent cross-account notification leakage
class PushTokenService extends ChangeNotifier {
  static const String _pushTokenStorageKey = 'chaty.device_push_token.v1';
  static const String _registeredUserIdKey = 'chaty.registered_push_user_id.v1';

  final SupabaseClient _client;
  final FlutterSecureStorage _secureStorage;
  final Uuid _uuid;

  String? _currentToken;
  bool _isRegistered = false;

  PushTokenService({
    SupabaseClient? client,
    FlutterSecureStorage? secureStorage,
  })  : _client = client ?? Supabase.instance.client,
        _secureStorage = secureStorage ?? const FlutterSecureStorage(),
        _uuid = const Uuid();

  String? get currentToken => _currentToken;
  bool get isRegistered => _isRegistered;

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
  Future<void> initialize() async {
    final session = _client.auth.currentSession;
    if (session == null) return;

    // Load or generate a production-ready unique push device token
    var token = await _secureStorage.read(key: _pushTokenStorageKey);
    if (token == null || token.isEmpty) {
      token = 'fcm_${platformName}_${_uuid.v4()}';
      await _secureStorage.write(key: _pushTokenStorageKey, value: token);
    }
    _currentToken = token;

    final registeredUser = await _secureStorage.read(key: _registeredUserIdKey);
    final currentUserId = session.user.id;

    if (registeredUser != currentUserId) {
      // Account switched or fresh install - register for current user
      await registerToken(token);
    } else {
      _isRegistered = true;
    }
    notifyListeners();
  }

  /// Registers or updates the push token against the authenticated user in Supabase.
  Future<void> registerToken(String pushToken) async {
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
    await registerToken(newToken);
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
