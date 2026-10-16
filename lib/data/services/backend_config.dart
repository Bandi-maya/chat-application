import 'package:flutter_dotenv/flutter_dotenv.dart';

class ApiBackendConfig {
  static String get restApiBaseUrl =>
      dotenv.env['REST_API_BASE_URL'] ?? 'https://api.test.saas.logybyte.in';
  static String get tenantId => 'chat-app-tenant';

  static String getWebSocketUrl(String userId) {
    return dotenv.env['WEBSOCKET_URL'] ??
        'wss://api.test.saas.logybyte.in/api/chat/ws/$userId';
  }
}
