import 'package:flutter/foundation.dart';

class ApiBackendConfig {
  static String? customBaseUrl;
  static String? customTenantId;
  static String? customWebSocketUrl;

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

  static String get tenantId {
    if (customTenantId != null && customTenantId!.isNotEmpty) {
      return customTenantId!;
    }
    const envTenant = String.fromEnvironment(
      'TENANT_ID',
      defaultValue: 'chat-application',
    );
    return envTenant;
  }

  static String getWebSocketUrl(String userId) {
    if (customWebSocketUrl != null && customWebSocketUrl!.isNotEmpty) {
      return customWebSocketUrl!
          .replaceAll('{userId}', userId)
          .replaceAll('\$userId', userId);
    }
    const envWs = String.fromEnvironment('WEBSOCKET_URL');
    if (envWs.isNotEmpty) {
      return envWs
          .replaceAll('{userId}', userId)
          .replaceAll('\$userId', userId);
    }
    final base = restApiBaseUrl
        .replaceFirst('https://', 'wss://')
        .replaceFirst('http://', 'ws://');
    return '$base/chat/ws/$userId';
  }
}

