import 'dart:async';

import '../../domain/models/user_profile.dart';
import 'api_backend_service.dart';
import '../../injection/locator.dart';

/// Authenticates with either the registered email address or the public Chaty
/// username via the new ApiBackendService.
class UsernameLoginService {
  UsernameLoginService({ApiBackendService? apiBackend})
    : _apiBackend = apiBackend ?? locator<ApiBackendService>();

  final ApiBackendService _apiBackend;

  Future<UserProfile> login({
    required String identifier,
    required String password,
    String? tenantId,
    bool isDefaultTenant = false,
  }) async {
    final value = identifier.trim().replaceFirst(RegExp(r'^@+'), '');
    
    if (isDefaultTenant) {
      await ApiBackendConfig.setTenantId(null, useDefault: true);
    } else if (tenantId != null && tenantId.trim().isNotEmpty) {
      await ApiBackendConfig.setTenantId(tenantId.trim(), useDefault: false);
    }

    // Delegate entirely to ApiBackendService
    final profile = await _apiBackend.login(identifier: value, password: password);
    return profile;
  }
}

