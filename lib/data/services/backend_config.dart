import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

class ApiBackendConfig {
  static const String keyActiveTenantId = 'chaty_active_tenant_id';
  static const String keyUseDefaultTenant = 'chaty_use_default_tenant';
  static const String fallbackDefaultTenant = 'chat-application';

  static String? customBaseUrl;
  static String? customTenantId;
  static String? customWebSocketUrl;
  static bool _useDefault = false;

  static bool get isUsingDefaultTenant => _useDefault || (customTenantId == null || customTenantId!.trim().isEmpty);

  static Future<void> init() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      _useDefault = prefs.getBool(keyUseDefaultTenant) ?? false;
      final savedTenant = prefs.getString(keyActiveTenantId);
      if (savedTenant != null && savedTenant.trim().isNotEmpty) {
        customTenantId = savedTenant.trim();
      }
    } catch (e) {
      debugPrint('[ApiBackendConfig] Failed to load saved tenant: $e');
    }
  }

  static Future<void> setTenantId(String? tenant, {bool useDefault = false}) async {
    _useDefault = useDefault;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(keyUseDefaultTenant, useDefault);

    if (useDefault || tenant == null || tenant.trim().isEmpty) {
      customTenantId = null;
      await prefs.remove(keyActiveTenantId);
    } else {
      final clean = tenant.trim();
      customTenantId = clean;
      await prefs.setString(keyActiveTenantId, clean);
    }
  }

  static Future<String?> getSavedTenantId() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      return prefs.getString(keyActiveTenantId);
    } catch (_) {
      return null;
    }
  }

  static String get restApiBaseUrl {
    if (customBaseUrl != null && customBaseUrl!.isNotEmpty) {
      return customBaseUrl!;
    }
    const envUrl = String.fromEnvironment('REST_API_BASE_URL');
    if (envUrl.isNotEmpty) return envUrl;
    if (!kIsWeb && defaultTargetPlatform == TargetPlatform.android) {
      return 'http://10.0.2.2:8000';
    }
    return 'http://localhost:8000';
  }

  static String get defaultTenantId {
    const envTenant = String.fromEnvironment(
      'TENANT_ID',
      defaultValue: fallbackDefaultTenant,
    );
    return envTenant;
  }

  static String get tenantId {
    if (!_useDefault && customTenantId != null && customTenantId!.trim().isNotEmpty) {
      return customTenantId!.trim();
    }
    return defaultTenantId;
  }

  static String getWebSocketUrl(String userId) {
    final cleanUserId = userId.trim();
    if (customWebSocketUrl != null &&
        customWebSocketUrl!.isNotEmpty &&
        !customWebSocketUrl!.contains('/ws/school')) {
      final ws = customWebSocketUrl!.trim();
      if (ws.contains('{userId}') || ws.contains('\$userId')) {
        return ws
            .replaceAll('{userId}', cleanUserId)
            .replaceAll('\$userId', cleanUserId);
      }
      return '${ws.replaceAll(RegExp(r'/+$'), '')}/chat/ws/$cleanUserId';
    }
    const envWs = String.fromEnvironment('WEBSOCKET_URL');
    if (envWs.isNotEmpty && !envWs.contains('/ws/school')) {
      if (envWs.contains('{userId}') || envWs.contains('\$userId')) {
        return envWs
            .replaceAll('{userId}', cleanUserId)
            .replaceAll('\$userId', cleanUserId);
      }
      return '${envWs.replaceAll(RegExp(r'/+$'), '')}/chat/ws/$cleanUserId';
    }
    final base = restApiBaseUrl
        .replaceFirst('https://', 'wss://')
        .replaceFirst('http://', 'ws://')
        .replaceAll(RegExp(r'/api/v[0-9]+/?$'), '')
        .replaceAll(RegExp(r'/+$'), '');
    return '$base/chat/ws/$cleanUserId';
  }
}

