import 'dart:async';

import 'package:supabase_flutter/supabase_flutter.dart';

import '../../domain/models/user_profile.dart';
import 'backend_service.dart';

/// Authenticates with either the registered email address or the public Chaty
/// username. Username-to-email resolution happens inside the dedicated Supabase
/// Edge Function; the client never receives another user's email address.
class UsernameLoginService {
  UsernameLoginService({SupabaseClient? client, ChatyBackendService? backend})
    : _client = client ?? Supabase.instance.client,
      _backend = backend ?? ChatyBackendService();

  final SupabaseClient _client;
  final ChatyBackendService _backend;

  Future<UserProfile> login({
    required String identifier,
    required String password,
  }) async {
    final value = identifier.trim();
    if (value.contains('@')) {
      return _backend.login(identifier: value, password: password);
    }

    final response = await _client.functions.invoke(
      'username-login',
      body: <String, dynamic>{
        'identifier': value.replaceFirst(RegExp(r'^@+'), ''),
        'password': password,
      },
    );
    final raw = response.data;
    if (raw is! Map) throw Exception('Invalid login credentials.');
    final payload = Map<String, dynamic>.from(raw);
    final refreshToken = payload['refresh_token']?.toString() ?? '';
    final accessToken = payload['access_token']?.toString() ?? '';
    if (refreshToken.isEmpty || accessToken.isEmpty) {
      throw Exception('Invalid login credentials.');
    }

    await _client.auth.setSession(refreshToken, accessToken: accessToken);
    if (!_backend.isInitialized) await _backend.initialize();

    // The backend owns profile hydration and listens to Supabase auth changes.
    // Wait briefly for that single source of truth rather than duplicating it.
    final deadline = DateTime.now().add(const Duration(seconds: 8));
    while (DateTime.now().isBefore(deadline)) {
      final profile = _backend.currentUser;
      if (_backend.isAuthenticated && profile != null) return profile;
      await Future<void>.delayed(const Duration(milliseconds: 80));
    }
    throw Exception(
      'Your secure session was created, but the profile could not be loaded.',
    );
  }
}
