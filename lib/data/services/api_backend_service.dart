import 'dart:async';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:web_socket_channel/web_socket_channel.dart';
import 'dart:convert';
import 'backend_config.dart';
import '../../domain/models/user_profile.dart';
import '../../domain/models/conversation.dart';
import '../../domain/models/chat_message.dart';
import '../../domain/models/chat_task.dart';

class OrganizationRegistrationResult {
  final UserProfile user;
  final String organizationId;
  final String organizationName;
  final String tenantId;

  const OrganizationRegistrationResult({
    required this.user,
    required this.organizationId,
    required this.organizationName,
    required this.tenantId,
  });
}

class ApiBackendService extends ChangeNotifier {
  static final ApiBackendService _instance = ApiBackendService._internal();
  factory ApiBackendService() => _instance;
  ApiBackendService._internal();

  bool _isInitialized = false;
  UserProfile? _currentUser;
  Map<String, dynamic>? _currentOrg;
  String? _token;
  WebSocketChannel? _channel;
  Timer? _reconnectTimer;
  Timer? _pingTimer;
  Timer? _syncTimer;
  String? _activeConversationId;
  int _syncTicks = 0;
  bool _isConnecting = false;

  List<Conversation> _conversations = [];
  List<UserProfile> _users = [];
  final Map<String, List<ChatMessage>> _messages = {};

  bool _requiresSubscription = false;
  String? _subscriptionErrorMessage;

  bool get isInitialized => _isInitialized;
  bool get isAuthenticated => _token != null && _currentUser != null;
  bool get isConnected => _channel != null;
  String? get activeConversationId => _activeConversationId;
  bool get requiresSubscription => _requiresSubscription;
  String? get subscriptionErrorMessage => _subscriptionErrorMessage;
  UserProfile? get currentUser => _currentUser;
  Map<String, dynamic>? get currentOrg => _currentOrg;
  Map<String, dynamic>? get currentOrganization => _currentOrg;
  List<Conversation> get conversations => _conversations;
  List<UserProfile> get users => _users;

  void setActiveConversation(String? conversationId) {
    _activeConversationId = conversationId;
    if (conversationId != null && conversationId.isNotEmpty) {
      _startBackgroundSync();
      loadMessages(conversationId);
      if (_channel == null && !_isConnecting) {
        _connectWebSocket();
      }
    }
  }

  UserProfile? getUser(String userId) {
    if (_currentUser?.id == userId) return _currentUser;
    return _users.where((u) => u.id == userId).firstOrNull;
  }

  List<ChatMessage> getMessages(String chatId) => _messages[chatId] ?? [];

  Future<void> initialize() async {
    if (_isInitialized) return;
    _isInitialized = true;
    try {
      final prefs = await SharedPreferences.getInstance();
      final savedToken = prefs.getString('api_hub_token');
      final savedUserJson = prefs.getString('api_hub_user');
      final savedOrgJson = prefs.getString('api_hub_org');
      if (savedToken != null && savedUserJson != null) {
        _token = savedToken;
        _currentUser = UserProfile.fromJson(jsonDecode(savedUserJson));
        if (savedOrgJson != null) {
          try {
            _currentOrg = Map<String, dynamic>.from(jsonDecode(savedOrgJson));
          } catch (_) {}
        }
        debugPrint('[ApiBackendService] Restored authenticated session for ${_currentUser?.displayName}');
        if (_currentUser != null && _currentUser!.id.trim().isEmpty) {
          try {
            final jwt = _decodeJwt(_token!);
            final uid = (jwt['uid'] ?? jwt['sub'] ?? '').toString();
            if (uid.isNotEmpty) {
              _currentUser = _currentUser!.copyWith(id: uid);
            }
          } catch (_) {}
        }
        _connectWebSocket();
        _startBackgroundSync();
        unawaited(loadConversations());
        unawaited(loadUsers());
        unawaited(loadTasks());
      }
    } catch (e) {
      debugPrint('[ApiBackendService] Error restoring session: $e');
    }
    notifyListeners();
  }

  Future<UserProfile> login({
    required String identifier,
    required String password,
  }) async {
    final primaryUri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/auth/token');
    http.Response response;
    try {
      response = await http.post(
        primaryUri,
        headers: {
          'Content-Type': 'application/json',
          'x-tenant-id': ApiBackendConfig.tenantId,
        },
        body: jsonEncode({'username': identifier, 'password': password}),
      );
      if (response.statusCode == 404) {
        final fallbackUri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/api/v1/auth/token');
        response = await http.post(
          fallbackUri,
          headers: {
            'Content-Type': 'application/json',
            'x-tenant-id': ApiBackendConfig.tenantId,
          },
          body: jsonEncode({'username': identifier, 'password': password}),
        );
      }
    } catch (e) {
      throw Exception('Unable to reach server. Please check your internet connection.');
    }
    return _parseAuthResponse(response);
  }

  Future<OrganizationRegistrationResult> registerOrganization({
    required String tenantId,
    required String organizationName,
    required String adminUsername,
    required String adminEmail,
    required String adminPassword,
    required String adminFullName,
    String tenantType = 'chat',
    bool allowUserRegistration = true,
  }) async {
    final payload = {
      'tenant_id': tenantId.trim(),
      'name': organizationName.trim(),
      'admin_username': adminUsername.trim(),
      'admin_email': adminEmail.trim(),
      'admin_password': adminPassword,
      'admin_full_name': adminFullName.trim(),
      'tenant_type': tenantType,
      'allow_user_registration': allowUserRegistration,
    };

    final uri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/organization/admin/create');
    http.Response response = await http.post(
      uri,
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode(payload),
    );

    if (response.statusCode == 404) {
      final fallbackUri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/api/v1/organization/admin/create');
      response = await http.post(
        fallbackUri,
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode(payload),
      );
    }

    String orgId = tenantId.trim();
    String resolvedOrgName = organizationName.trim();

    if (response.statusCode == 200) {
      await ApiBackendConfig.setTenantId(tenantId.trim());
      try {
        final body = jsonDecode(response.body);
        final data = body['data'] ?? body;
        if (data['org'] is Map) {
          _currentOrg = Map<String, dynamic>.from(data['org']);
          orgId = (_currentOrg!['uid'] ?? _currentOrg!['tenantId'] ?? orgId).toString();
          resolvedOrgName = (_currentOrg!['name'] ?? resolvedOrgName).toString();
          final prefs = await SharedPreferences.getInstance();
          await prefs.setString('api_hub_org', jsonEncode(_currentOrg!));
        }
      } catch (_) {}
    }

    final user = _parseAuthResponse(response);
    return OrganizationRegistrationResult(
      user: user,
      organizationId: orgId,
      organizationName: resolvedOrgName,
      tenantId: tenantId.trim(),
    );
  }

  /// Check organization existence and configuration flags (including allow_user_registration).
  Future<Map<String, dynamic>> checkOrganization(String tenantId) async {
    final clean = tenantId.trim();
    if (clean.isEmpty) {
      throw Exception('Organization ID cannot be empty');
    }

    final primaryUri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/organization/check/$clean');
    http.Response? response;
    try {
      response = await http.get(primaryUri);
      if (response.statusCode == 404) {
        final fallbackUri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/tenants/config?tenant_id=$clean');
        response = await http.get(fallbackUri, headers: {'x-tenant-id': clean});
      }
    } catch (e) {
      try {
        final fallbackUri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/tenants/config?tenant_id=$clean');
        response = await http.get(fallbackUri, headers: {'x-tenant-id': clean});
      } catch (_) {
        throw Exception('Unable to reach server to check organization: $e');
      }
    }

    if (response.statusCode == 200) {
      final body = jsonDecode(response.body);
      final data = body['data'] ?? body;
      return Map<String, dynamic>.from(data);
    } else if (response.statusCode == 404) {
      throw Exception('Organization "$clean" not found. Please verify the ID or register a new organization.');
    } else {
      String detail = 'Organization check failed (${response.statusCode})';
      try {
        final body = jsonDecode(response.body);
        detail = body['detail'] ?? body['message'] ?? detail;
      } catch (_) {}
      throw Exception(detail);
    }
  }

  /// Checks if regular user registration is supported for the specified tenant.
  /// Returns true if self-registration is enabled, false if disabled.
  Future<bool> checkUserRegistrationSupported(String tenantId) async {
    try {
      final data = await checkOrganization(tenantId);
      final dynamic flag = data['allow_user_registration'] ?? data['user_registration_enabled'];
      if (flag is bool) return flag;
      if (flag is String) return flag.toLowerCase() == 'true';
      return true; // Default to true if not explicitly disabled
    } catch (_) {
      rethrow;
    }
  }

  /// Update organization user self-registration support flag.
  Future<bool> updateOrganizationRegistrationSupport({
    required String organizationIdOrTenantId,
    required bool allowUserRegistration,
  }) async {
    final clean = organizationIdOrTenantId.trim();
    final uri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/organization/$clean/registration');
    final response = await http.patch(
      uri,
      headers: {
        'Content-Type': 'application/json',
        'x-tenant-id': ApiBackendConfig.tenantId,
        if (_token != null) 'Authorization': 'Bearer $_token',
      },
      body: jsonEncode({
        'allow_user_registration': allowUserRegistration,
      }),
    );
    return response.statusCode == 200;
  }

  Future<List<Map<String, dynamic>>> getAvailablePlans({String? organizationId}) async {
    final headers = {
      'Content-Type': 'application/json',
      'x-tenant-id': ApiBackendConfig.tenantId,
      if (_token != null) 'Authorization': 'Bearer $_token',
    };

    http.Response response;
    final primaryUri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/organization/subscription/products');
    try {
      response = await http.get(primaryUri, headers: headers);
      if (response.statusCode == 404) {
        final fallbackUri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/api/v1/organization/subscription/products');
        response = await http.get(fallbackUri, headers: headers);
      }
    } catch (e) {
      debugPrint('[ApiBackendService] Error fetching subscription plans from API: $e');
      throw Exception('Unable to reach server to fetch subscription plans: $e');
    }

    if (response.statusCode == 200) {
      final body = jsonDecode(response.body);
      final data = body['data'] ?? body;
      final List<Map<String, dynamic>> extracted = [];

      if (data is List) {
        for (final item in data) {
          if (item is Map) {
            final plans = item['plans'];
            final productName = (item['name'] ?? item['title'] ?? '').toString();
            final productCategory = (item['category'] ?? '').toString();
            final defaultTenantType = (item['default_tenant_type'] ?? '').toString();

            if (plans is List && plans.isNotEmpty) {
              for (final p in plans) {
                if (p is Map) {
                  final planMap = Map<String, dynamic>.from(p);
                  if (productName.isNotEmpty) planMap.putIfAbsent('product_name', () => productName);
                  if (productCategory.isNotEmpty) planMap.putIfAbsent('product_category', () => productCategory);
                  if (defaultTenantType.isNotEmpty) planMap.putIfAbsent('default_tenant_type', () => defaultTenantType);
                  extracted.add(planMap);
                }
              }
            } else {
              extracted.add(Map<String, dynamic>.from(item));
            }
          }
        }
      } else if (data is Map) {
        final plans = data['plans'];
        if (plans is List) {
          for (final p in plans) {
            if (p is Map) extracted.add(Map<String, dynamic>.from(p));
          }
        } else {
          extracted.add(Map<String, dynamic>.from(data));
        }
      }

      return extracted;
    } else {
      String errorMsg = 'Failed to load subscription plans (${response.statusCode})';
      try {
        final body = jsonDecode(response.body);
        errorMsg = (body['detail'] ?? body['message'] ?? errorMsg).toString();
      } catch (_) {}
      throw Exception(errorMsg);
    }
  }

  Future<bool> subscribeToPlan({
    required String organizationId,
    required String planId,
    int durationMonths = 1,
    bool isTrial = false,
  }) async {
    final payload = {
      'organization_id': organizationId.trim(),
      'plan_type': planId.trim(),
      'duration_months': durationMonths,
      'is_trial': isTrial,
    };

    final uri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/organization/subscription/create-order');
    final response = await http.post(
      uri,
      headers: {
        'Content-Type': 'application/json',
        'x-tenant-id': ApiBackendConfig.tenantId,
        if (_token != null) 'Authorization': 'Bearer $_token',
      },
      body: jsonEncode(payload),
    );

    if (response.statusCode == 200) {
      _requiresSubscription = false;
      _subscriptionErrorMessage = null;
      debugPrint('[ApiBackendService] Subscription order completed for $organizationId');
      if (_currentOrg != null) {
        _currentOrg!['plan_type'] = planId.trim();
      }
      notifyListeners();
      unawaited(loadConversations());
      unawaited(loadUsers());
      return true;
    } else {
      try {
        final body = jsonDecode(response.body);
        final detail = body['detail'] ?? body['message'] ?? 'Subscription failed (${response.statusCode})';
        throw Exception(detail);
      } catch (e) {
        if (e is Exception && !e.toString().contains('FormatException')) rethrow;
        throw Exception('Failed to activate subscription (${response.statusCode})');
      }
    }
  }

  Future<UserProfile> register({
    required String email,
    required String username,
    required String password,
  }) async {
    final primaryUri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/auth/register');
    http.Response response;
    try {
      response = await http.post(
        primaryUri,
        headers: {
          'Content-Type': 'application/json',
          'x-tenant-id': ApiBackendConfig.tenantId,
        },
        body: jsonEncode({
          'email': email,
          'username': username,
          'password': password,
        }),
      );
      if (response.statusCode == 404) {
        final fallbackUri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/api/v1/auth/register');
        response = await http.post(
          fallbackUri,
          headers: {
            'Content-Type': 'application/json',
            'x-tenant-id': ApiBackendConfig.tenantId,
          },
          body: jsonEncode({
            'email': email,
            'username': username,
            'password': password,
          }),
        );
      }
    } catch (e) {
      throw Exception('Unable to reach server. Please check your internet connection.');
    }
    return _parseAuthResponse(response);
  }

  Future<UserProfile> oauthLogin({
    required String provider,
    required String token,
  }) async {
    final response = await http.post(
      Uri.parse('${ApiBackendConfig.restApiBaseUrl}/auth/oauth'),
      headers: {
        'Content-Type': 'application/json',
        'x-tenant-id': ApiBackendConfig.tenantId,
      },
      body: jsonEncode({'provider': provider, 'token': token}),
    );
    return _parseAuthResponse(response);
  }

  Map<String, dynamic> _decodeJwt(String token) {
    final parts = token.split('.');
    if (parts.length != 3) {
      throw Exception('Invalid token');
    }
    String payload = parts[1];
    payload = payload.replaceAll('-', '+').replaceAll('_', '/');
    switch (payload.length % 4) {
      case 0:
        break;
      case 2:
        payload += '==';
        break;
      case 3:
        payload += '=';
        break;
      default:
        throw Exception('Illegal base64url string!');
    }
    return jsonDecode(utf8.decode(base64Decode(payload)));
  }

  UserProfile _parseAuthResponse(http.Response response) {
    if (response.statusCode == 200) {
      final body = jsonDecode(response.body);
      final data = body['data'] ?? body;
      _token = data['access_token'] ?? data['token'];

      if (_token == null) {
        throw Exception('No token found in response');
      }

      final payload = _decodeJwt(_token!);
      final userObj = (data['user'] is Map ? data['user'] : {}) as Map;

      final userId = (userObj['id'] ?? userObj['uid'] ?? payload['uid'] ?? '').toString();
      final username = (userObj['username'] ?? payload['sub'] ?? '').toString();

      String extractedName = (userObj['full_name'] ?? userObj['display_name'] ?? userObj['name'] ?? '').toString().trim();
      if (extractedName.isEmpty) {
        final fn = (userObj['firstName'] ?? userObj['first_name'] ?? '').toString().trim();
        final ln = (userObj['lastName'] ?? userObj['last_name'] ?? '').toString().trim();
        extractedName = '$fn $ln'.trim();
      }
      if (extractedName.isEmpty) {
        extractedName = username.isNotEmpty ? username : (payload['sub'] ?? 'User').toString();
      }

      final initials = extractedName.isNotEmpty
          ? extractedName.split(RegExp(r'\s+')).map((e) => e.isNotEmpty ? e[0] : '').take(2).join().toUpperCase()
          : (username.isNotEmpty ? username.substring(0, 1).toUpperCase() : 'U');

      final photo = (userObj['photo'] ??
              userObj['avatar_url'] ??
              userObj['avatarUrl'] ??
              (userObj['public_profile'] is Map ? userObj['public_profile']['avatarUrl'] : null))
          ?.toString();

      _currentUser = UserProfile(
        id: userId,
        displayName: extractedName,
        username: username,
        avatarInitials: initials,
        avatarColorHex: '0xFF6366F1',
        about: (userObj['about'] ?? userObj['role'] ?? payload['role'] ?? '').toString(),
        presence: PresenceState.online,
        lastSeenAt: DateTime.now(),
        isVerified: false,
        email: (userObj['email'] ?? '').toString(),
        phone: (userObj['mobile'] ?? userObj['phone'] ?? '').toString(),
        safetyNumber: '',
        avatarUrl: photo,
      );

      // Persist session to SharedPreferences
      unawaited(() async {
        try {
          final prefs = await SharedPreferences.getInstance();
          await prefs.setString('api_hub_token', _token!);
          await prefs.setString('api_hub_user', jsonEncode(_currentUser!.toJson()));
        } catch (e) {
          debugPrint('[ApiBackendService] Error persisting session: $e');
        }
      }());

      _connectWebSocket();
      loadConversations();
      loadUsers();
      loadTasks();
      notifyListeners();
      return _currentUser!;
    } else {
      try {
        final body = jsonDecode(response.body);
        final detail = body['detail'];
        String? detailMsg;
        if (detail is String) {
          detailMsg = detail;
        } else if (detail is List && detail.isNotEmpty) {
          detailMsg = detail.map((e) => e['msg'] ?? e.toString()).join(', ');
        }
        final msg = detailMsg ?? body['error']?['message'] ?? body['message'] ?? 'Authentication failed (${response.statusCode})';
        throw Exception(msg);
      } catch (e) {
        if (e is Exception && !e.toString().contains('FormatException')) rethrow;
        throw Exception('Authentication failed (${response.statusCode})');
      }
    }
  }

  void _cleanupWebSocket() {
    _pingTimer?.cancel();
    _pingTimer = null;
    try {
      _channel?.sink.close();
    } catch (_) {}
    _channel = null;
    _isConnecting = false;
  }

  void _scheduleReconnect() {
    if (_token == null) return;
    _reconnectTimer?.cancel();
    _reconnectTimer = Timer(const Duration(seconds: 4), () {
      if (_token != null && _channel == null) {
        _connectWebSocket();
      }
    });
  }

  void _connectWebSocket() {
    if (_token == null || _currentUser == null || _isConnecting) return;
    if (_currentUser!.id.trim().isEmpty) {
      try {
        final jwt = _decodeJwt(_token!);
        final uid = (jwt['uid'] ?? jwt['sub'] ?? '').toString();
        if (uid.isNotEmpty) {
          _currentUser = _currentUser!.copyWith(id: uid);
        }
      } catch (_) {}
    }
    if (_currentUser!.id.trim().isEmpty) return;
    _reconnectTimer?.cancel();
    _cleanupWebSocket();
    _isConnecting = true;

    try {
      final wsUrl = Uri.parse(
        ApiBackendConfig.getWebSocketUrl(_currentUser!.id),
      );
      debugPrint('[ApiBackendService] Connecting WebSocket to: $wsUrl');
      _channel = WebSocketChannel.connect(wsUrl);
      _isConnecting = false;

      // Start ping heartbeat timer every 25 seconds to keep connection alive
      _pingTimer?.cancel();
      _pingTimer = Timer.periodic(const Duration(seconds: 25), (timer) {
        if (_channel != null && _token != null) {
          try {
            _channel!.sink.add(jsonEncode({'type': 'ping'}));
          } catch (e) {
            debugPrint('[ApiBackendService] Ping failed: $e');
            _cleanupWebSocket();
            _scheduleReconnect();
          }
        } else {
          timer.cancel();
        }
      });

      _channel?.stream.listen((message) {
        try {
          final payload = jsonDecode(message);
          final eventType = payload['type'];

          if (eventType == 'pong') {
            // Heartbeat response from backend
            return;
          }

          if (eventType == 'new_message') {
            final rawData = payload['data'] ?? payload['message'];
            if (rawData != null) {
              final chatMsg = ChatMessage.fromApi(rawData);
              final roomId = chatMsg.conversationId;
              final list = _messages.putIfAbsent(roomId, () => []);

              // Replace optimistic sending message if matching content or temp ID
              final optIdx = list.indexWhere(
                (m) =>
                    (m.deliveryState == DeliveryState.sending || m.id.startsWith('temp_')) &&
                    m.text == chatMsg.text &&
                    (m.senderId == chatMsg.senderId || m.senderId == _currentUser?.id),
              );
              if (optIdx != -1) {
                list[optIdx] = chatMsg;
              } else if (!list.any((m) => m.id == chatMsg.id)) {
                list.add(chatMsg);
              }
              list.sort((a, b) => a.createdAt.compareTo(b.createdAt));

              // Update last message in conversation list & reorder
              final convIdx = _conversations.indexWhere((c) => c.id == roomId);
              if (convIdx != -1) {
                final oldConv = _conversations[convIdx];
                _conversations.removeAt(convIdx);
                _conversations.insert(
                  0,
                  oldConv.copyWith(
                    lastMessageText: chatMsg.text.isNotEmpty
                        ? chatMsg.text
                        : (chatMsg.type == MessageType.image ? '📷 Photo' : '📎 Attachment'),
                    lastMessageTime: chatMsg.createdAt,
                    lastMessageSenderId: chatMsg.senderId,
                  ),
                );
              } else {
                loadConversations();
              }

              notifyListeners();
            }
          }
        } catch (e) {
          debugPrint('[ApiBackendService] Error parsing websocket message: $e');
        }
      }, onError: (error) {
        debugPrint('[ApiBackendService] WebSocket Error: $error');
        _cleanupWebSocket();
        _scheduleReconnect();
      }, onDone: () {
        debugPrint('[ApiBackendService] WebSocket Disconnected. Reconnecting in 4s...');
        _cleanupWebSocket();
        _scheduleReconnect();
      });

      _startBackgroundSync();
    } catch (e) {
      debugPrint('[ApiBackendService] Error connecting to WebSocket: $e');
      _cleanupWebSocket();
      _scheduleReconnect();
    }
  }

  void _startBackgroundSync() {
    _syncTimer?.cancel();
    _syncTimer = Timer.periodic(const Duration(seconds: 4), (timer) {
      if (_token == null) {
        timer.cancel();
        return;
      }
      _syncTicks++;
      // Active conversation polling sync (runs every 4 seconds when in a room)
      if (_activeConversationId != null && _activeConversationId!.isNotEmpty) {
        _syncActiveRoomMessages(_activeConversationId!);
      }
      // Periodically refresh conversation list every ~12 seconds
      if (_syncTicks % 3 == 0) {
        loadConversations();
      }
      // Re-establish WebSocket connection if dropped without reconnect
      if (_channel == null && !_isConnecting) {
        _connectWebSocket();
      }
    });
  }

  Future<void> _syncActiveRoomMessages(String chatId) async {
    if (_token == null) return;
    try {
      final uri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/chat/rooms/$chatId/messages');
      http.Response response = await http.get(
        uri,
        headers: {
          'Authorization': 'Bearer $_token',
          'x-tenant-id': ApiBackendConfig.tenantId,
        },
      );
      if (response.statusCode == 404) {
        final fallbackUri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/api/v1/chat/rooms/$chatId/messages');
        response = await http.get(
          fallbackUri,
          headers: {
            'Authorization': 'Bearer $_token',
            'x-tenant-id': ApiBackendConfig.tenantId,
          },
        );
      }
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body)['data'] as List;
        final remote = data.map((json) => ChatMessage.fromApi(json)).toList();
        final currentList = _messages[chatId] ?? [];
        final pending = currentList
            .where((m) => m.deliveryState == DeliveryState.sending || m.id.startsWith('temp_'))
            .toList();

        // Check if remote has any messages not in currentList or different count
        bool hasChanges = remote.length != currentList.length;
        if (!hasChanges && remote.isNotEmpty && currentList.isNotEmpty) {
          hasChanges = remote.last.id != currentList.last.id ||
              remote.first.id != currentList.first.id;
        }

        if (hasChanges) {
          final merged = <ChatMessage>[...remote];
          for (final p in pending) {
            if (!remote.any((r) => r.text == p.text)) {
              merged.add(p);
            }
          }
          merged.sort((a, b) => a.createdAt.compareTo(b.createdAt));
          _messages[chatId] = merged;

          // Update conversation snippet in list
          if (remote.isNotEmpty) {
            final lastMsg = remote.last;
            final convIdx = _conversations.indexWhere((c) => c.id == chatId);
            if (convIdx != -1) {
              final oldConv = _conversations[convIdx];
              _conversations[convIdx] = oldConv.copyWith(
                lastMessageText: lastMsg.text.isNotEmpty
                    ? lastMsg.text
                    : (lastMsg.type == MessageType.image ? '📷 Photo' : '📎 Attachment'),
                lastMessageTime: lastMsg.createdAt,
                lastMessageSenderId: lastMsg.senderId,
              );
            }
          }

          notifyListeners();
        }
      }
    } catch (_) {}
  }

  void onAppResumed() {
    if (_token == null) return;
    if (_channel == null && !_isConnecting) {
      _connectWebSocket();
    }
    loadConversations();
    if (_activeConversationId != null && _activeConversationId!.isNotEmpty) {
      loadMessages(_activeConversationId!);
    }
  }

  Future<void> loadConversations() async {
    if (_token == null) return;
    try {
      final uri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/chat/rooms?include_tasks=false');
      http.Response response = await http.get(
        uri,
        headers: {
          'Authorization': 'Bearer $_token',
          'x-tenant-id': ApiBackendConfig.tenantId,
        },
      );
      if (response.statusCode == 404) {
        final fallbackUri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/api/v1/chat/rooms?include_tasks=false');
        response = await http.get(
          fallbackUri,
          headers: {
            'Authorization': 'Bearer $_token',
            'x-tenant-id': ApiBackendConfig.tenantId,
          },
        );
      }
      if (response.statusCode == 200) {
        _requiresSubscription = false;
        _subscriptionErrorMessage = null;
        final data = jsonDecode(response.body)['data'] as List;
        _conversations = data
            .map((json) => Conversation.fromApi(json))
            .where((c) => !c.isTask)
            .toList();
        notifyListeners();
      } else if (response.statusCode == 402) {
        _requiresSubscription = true;
        _subscriptionErrorMessage = 'Workspace subscription required. Please select a plan.';
        notifyListeners();
      }
    } catch (e) {
      debugPrint('[ApiBackendService] Error loading conversations: $e');
    }
  }

  /// Clear all messages in a conversation
  Future<void> clearConversationMessages(String chatId) async {
    _messages[chatId] = [];
    final idx = _conversations.indexWhere((c) => c.id == chatId);
    if (idx != -1) {
      _conversations[idx] = _conversations[idx].copyWith(
        lastMessageText: '',
      );
    }
    notifyListeners();

    if (_token == null) return;
    try {
      final uri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/chat/rooms/$chatId/messages');
      http.Response response = await http.delete(
        uri,
        headers: {
          'Authorization': 'Bearer $_token',
          'x-tenant-id': ApiBackendConfig.tenantId,
        },
      );
      if (response.statusCode == 404) {
        final fallbackUri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/api/v1/chat/rooms/$chatId/messages');
        response = await http.delete(
          fallbackUri,
          headers: {
            'Authorization': 'Bearer $_token',
            'x-tenant-id': ApiBackendConfig.tenantId,
          },
        );
      }
    } catch (e) {
      debugPrint('[ApiBackendService] Error clearing messages: $e');
    }
  }

  /// Delete a single message from a conversation
  Future<void> deleteMessage(String chatId, String messageId) async {
    final list = _messages[chatId];
    if (list != null) {
      list.removeWhere((m) => m.id == messageId);
      final idx = _conversations.indexWhere((c) => c.id == chatId);
      if (idx != -1) {
        final lastMsg = list.isNotEmpty ? list.last : null;
        _conversations[idx] = _conversations[idx].copyWith(
          lastMessageText: lastMsg != null
              ? (lastMsg.text.isNotEmpty
                  ? lastMsg.text
                  : (lastMsg.type == MessageType.image ? '📷 Photo' : '📎 Attachment'))
              : '',
          lastMessageTime: lastMsg?.createdAt ?? _conversations[idx].lastMessageTime,
        );
      }
      notifyListeners();
    }

    if (_token == null) return;
    try {
      final uri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/chat/rooms/$chatId/messages/$messageId');
      http.Response response = await http.delete(
        uri,
        headers: {
          'Authorization': 'Bearer $_token',
          'x-tenant-id': ApiBackendConfig.tenantId,
        },
      );
      if (response.statusCode == 404) {
        final fallbackUri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/api/v1/chat/rooms/$chatId/messages/$messageId');
        response = await http.delete(
          fallbackUri,
          headers: {
            'Authorization': 'Bearer $_token',
            'x-tenant-id': ApiBackendConfig.tenantId,
          },
        );
      }
    } catch (e) {
      debugPrint('[ApiBackendService] Error deleting message: $e');
    }
  }

  /// Delete a conversation room entirely
  Future<void> deleteConversation(String chatId) async {
    _conversations.removeWhere((c) => c.id == chatId);
    _messages.remove(chatId);
    if (_activeConversationId == chatId) {
      _activeConversationId = null;
    }
    notifyListeners();

    if (_token == null) return;
    try {
      final uri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/chat/rooms/$chatId');
      http.Response response = await http.delete(
        uri,
        headers: {
          'Authorization': 'Bearer $_token',
          'x-tenant-id': ApiBackendConfig.tenantId,
        },
      );
      if (response.statusCode == 404) {
        final fallbackUri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/api/v1/chat/rooms/$chatId');
        response = await http.delete(
          fallbackUri,
          headers: {
            'Authorization': 'Bearer $_token',
            'x-tenant-id': ApiBackendConfig.tenantId,
          },
        );
      }
    } catch (e) {
      debugPrint('[ApiBackendService] Error deleting conversation: $e');
    }
  }

  /// Delete a user directly from backend directory and local memory
  Future<void> deleteUser(String userId) async {
    _users.removeWhere((u) => u.id == userId);
    _conversations.removeWhere((c) => c.isDirect && c.participantIds.contains(userId));
    notifyListeners();

    if (_token == null) return;
    try {
      final uri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/app-users/$userId');
      http.Response response = await http.delete(
        uri,
        headers: {
          'Authorization': 'Bearer $_token',
          'x-tenant-id': ApiBackendConfig.tenantId,
        },
      );
      if (response.statusCode == 404) {
        final fallbackUri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/api/v1/app-users/$userId');
        response = await http.delete(
          fallbackUri,
          headers: {
            'Authorization': 'Bearer $_token',
            'x-tenant-id': ApiBackendConfig.tenantId,
          },
        );
      }
    } catch (e) {
      debugPrint('[ApiBackendService] Error deleting user: $e');
    }

    if (_currentUser?.id == userId) {
      await signOut();
    }
  }


  Future<void> loadUsers({String? query}) async {
    if (_token == null) return;
    try {
      final qParam = (query != null && query.trim().isNotEmpty)
          ? '?search=${Uri.encodeComponent(query.trim())}'
          : '';

      http.Response response;
      try {
        final uri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/chat/users$qParam');
        response = await http.get(
          uri,
          headers: {
            'Authorization': 'Bearer $_token',
            'x-tenant-id': ApiBackendConfig.tenantId,
          },
        );
        if (response.statusCode == 404) {
          final fallbackUri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/api/v1/chat/users$qParam');
          response = await http.get(
            fallbackUri,
            headers: {
              'Authorization': 'Bearer $_token',
              'x-tenant-id': ApiBackendConfig.tenantId,
            },
          );
        }
      } catch (_) {
        response = await http.get(
          Uri.parse('${ApiBackendConfig.restApiBaseUrl}/app-users$qParam'),
          headers: {
            'Authorization': 'Bearer $_token',
            'x-tenant-id': ApiBackendConfig.tenantId,
          },
        );
      }

      if (response.statusCode == 404) {
        response = await http.get(
          Uri.parse('${ApiBackendConfig.restApiBaseUrl}/app-users$qParam'),
          headers: {
            'Authorization': 'Bearer $_token',
            'x-tenant-id': ApiBackendConfig.tenantId,
          },
        );
        if (response.statusCode == 404) {
          response = await http.get(
            Uri.parse('${ApiBackendConfig.restApiBaseUrl}/api/v1/app-users$qParam'),
            headers: {
              'Authorization': 'Bearer $_token',
              'x-tenant-id': ApiBackendConfig.tenantId,
            },
          );
        }
      }

      if (response.statusCode == 200) {
        final body = jsonDecode(response.body);
        final list = (body['data'] as List?) ?? (body is List ? body : []);
        final fetchedUsers = list.map((json) {
          final uid = (json['uid'] ?? json['id'] ?? '').toString();
          final fullName = (json['full_name'] ??
                  json['displayName'] ??
                  '${json['first_name'] ?? ''} ${json['last_name'] ?? ''}'.trim())
              .toString();
          final username = (json['username'] ?? '').toString();
          final displayName = fullName.isNotEmpty ? fullName : (username.isNotEmpty ? username : 'User');
          final initials = displayName.isNotEmpty
              ? displayName.split(RegExp(r'\s+')).map((e) => e.isNotEmpty ? e[0] : '').take(2).join().toUpperCase()
              : 'U';
          final photo = (json['photo'] ??
                  json['avatar_url'] ??
                  json['avatarUrl'] ??
                  (json['publicProfile'] is Map ? json['publicProfile']['avatarUrl'] : null))
              ?.toString();

          return UserProfile(
            id: uid,
            displayName: displayName,
            username: username,
            avatarInitials: initials,
            avatarColorHex: '0xFF6366F1',
            about: (json['about'] ?? json['designation'] ?? json['role'] ?? '').toString(),
            presence: PresenceState.online,
            lastSeenAt: DateTime.now(),
            isVerified: false,
            email: (json['email'] ?? '').toString(),
            phone: (json['mobile'] ?? json['phone'] ?? '').toString(),
            safetyNumber: '',
            avatarUrl: photo,
          );
        }).toList();

        if (query == null || query.trim().isEmpty) {
          _users = fetchedUsers;
        } else {
          for (final u in fetchedUsers) {
            final idx = _users.indexWhere((x) => x.id == u.id);
            if (idx == -1) {
              _users.add(u);
            } else {
              _users[idx] = u;
            }
          }
        }

        // Enrich current user display name, initials, and photo if found in users list
        if (_currentUser != null) {
          final me = _users.where((u) => u.id == _currentUser!.id).firstOrNull;
          if (me != null) {
            _currentUser = _currentUser!.copyWith(
              displayName: me.displayName.isNotEmpty ? me.displayName : _currentUser!.displayName,
              avatarInitials: me.avatarInitials,
              about: me.about.isNotEmpty ? me.about : _currentUser!.about,
              email: me.email.isNotEmpty ? me.email : _currentUser!.email,
              avatarUrl: me.avatarUrl ?? _currentUser!.avatarUrl,
            );
            unawaited(() async {
              try {
                final prefs = await SharedPreferences.getInstance();
                await prefs.setString('api_hub_user', jsonEncode(_currentUser!.toJson()));
              } catch (_) {}
            }());
          }
        }

        notifyListeners();
      } else {
        debugPrint('[ApiBackendService] loadUsers status ${response.statusCode}');
      }
    } catch (e) {
      debugPrint('[ApiBackendService] Error loading users: $e');
    }
  }

  Future<List<UserProfile>> searchUsers(String query, {bool includeSelf = false}) async {
    final q = query.toLowerCase().trim();
    // Query remote users directly for fresh directory results
    await loadUsers(query: q);

    return _users.where((u) {
      if (!includeSelf && u.id == _currentUser?.id) return false;
      if (q.isEmpty) return true;
      return u.displayName.toLowerCase().contains(q) ||
          u.username.toLowerCase().contains(q) ||
          u.email.toLowerCase().contains(q);
    }).toList();
  }

  Future<Conversation> createOrGetDirectConversation(String targetUserId) async {
    if (_token == null) throw Exception('Not authenticated');
    final response = await http.post(
      Uri.parse('${ApiBackendConfig.restApiBaseUrl}/chat/rooms/direct'),
      headers: {
        'Authorization': 'Bearer $_token',
        'Content-Type': 'application/json',
        'x-tenant-id': ApiBackendConfig.tenantId,
      },
      body: jsonEncode({'target_user_id': targetUserId}),
    );
    if (response.statusCode == 200) {
      final body = jsonDecode(response.body);
      final roomData = body['data'] ?? body;
      final conv = Conversation.fromApi(roomData);

      final idx = _conversations.indexWhere((c) => c.id == conv.id);
      if (idx == -1) {
        _conversations.insert(0, conv);
      } else {
        _conversations[idx] = conv;
      }
      notifyListeners();
      return conv;
    }
    if (response.statusCode == 402) {
      _requiresSubscription = true;
      _subscriptionErrorMessage = 'Workspace subscription required. Please choose a plan to chat.';
      notifyListeners();
      throw Exception('No active subscription plan found. Please enroll in a product plan to continue.');
    }
    throw Exception('Failed to start chat (${response.statusCode})');
  }

  Future<Conversation> createGroupRoom(String name, List<String> memberUserIds) async {
    if (_token == null) throw Exception('Not authenticated');
    final payload = {
      'name': name.trim(),
      'member_user_ids': memberUserIds,
    };
    http.Response response;
    final primaryUri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/chat/rooms/group');
    try {
      response = await http.post(
        primaryUri,
        headers: {
          'Authorization': 'Bearer $_token',
          'Content-Type': 'application/json',
          'x-tenant-id': ApiBackendConfig.tenantId,
        },
        body: jsonEncode(payload),
      );
      if (response.statusCode == 404) {
        final fallbackUri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/api/v1/chat/rooms/group');
        response = await http.post(
          fallbackUri,
          headers: {
            'Authorization': 'Bearer $_token',
            'Content-Type': 'application/json',
            'x-tenant-id': ApiBackendConfig.tenantId,
          },
          body: jsonEncode(payload),
        );
      }
    } catch (e) {
      throw Exception('Failed to connect to chat server: $e');
    }

    if (response.statusCode == 200) {
      final body = jsonDecode(response.body);
      final roomData = body['data'] ?? body;
      final conv = Conversation.fromApi(roomData);

      final idx = _conversations.indexWhere((c) => c.id == conv.id);
      if (idx == -1) {
        _conversations.insert(0, conv);
      } else {
        _conversations[idx] = conv;
      }
      notifyListeners();
      return conv;
    }
    throw Exception('Failed to create group (${response.statusCode})');
  }

  Future<void> loadMessages(String chatId) async {
    if (_token == null) return;
    try {
      final uri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/chat/rooms/$chatId/messages');
      http.Response response = await http.get(
        uri,
        headers: {
          'Authorization': 'Bearer $_token',
          'x-tenant-id': ApiBackendConfig.tenantId,
        },
      );
      if (response.statusCode == 404) {
        final fallbackUri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/api/v1/chat/rooms/$chatId/messages');
        response = await http.get(
          fallbackUri,
          headers: {
            'Authorization': 'Bearer $_token',
            'x-tenant-id': ApiBackendConfig.tenantId,
          },
        );
      }
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body)['data'] as List;
        final parsed = data.map((json) => ChatMessage.fromApi(json)).toList();
        
        final currentList = _messages[chatId] ?? [];
        final pending = currentList
            .where((m) => (m.deliveryState == DeliveryState.sending || m.id.startsWith('temp_')) && !parsed.any((p) => p.text == m.text))
            .toList();
        final merged = [...parsed, ...pending]..sort((a, b) => a.createdAt.compareTo(b.createdAt));
        _messages[chatId] = merged;
        notifyListeners();
      }
    } catch (e) {
      debugPrint('[ApiBackendService] Error loading messages: $e');
    }
  }

  Future<ChatMessage?> sendMessage(
    String chatId,
    String content, {
    MessageType type = MessageType.text,
    MessageAttachment? attachment,
    Map<String, dynamic>? extraMetadata,
  }) async {
    final effectiveContent = attachment != null
        ? jsonEncode({
            'type': type.name,
            'url': attachment.url,
            'name': attachment.name,
            'size': attachment.size,
            'text': content,
            'duration': attachment.durationSeconds,
            if (extraMetadata != null) ...extraMetadata,
          })
        : content;

    final tempId = 'temp_${DateTime.now().millisecondsSinceEpoch}';
    final tempMsg = ChatMessage(
      id: tempId,
      conversationId: chatId,
      senderId: _currentUser?.id ?? '',
      type: type,
      text: content,
      attachment: attachment,
      createdAt: DateTime.now(),
      deliveryState: DeliveryState.sending,
    );

    final list = _messages.putIfAbsent(chatId, () => []);
    list.add(tempMsg);
    notifyListeners();

    ChatMessage? confirmedMsg;

    // 1. Send via HTTP POST to persist message to database
    if (_token != null) {
      try {
        final uri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/chat/rooms/$chatId/messages');
        http.Response response = await http.post(
          uri,
          headers: {
            'Authorization': 'Bearer $_token',
            'Content-Type': 'application/json',
            'x-tenant-id': ApiBackendConfig.tenantId,
          },
          body: jsonEncode({'content': effectiveContent}),
        );

        if (response.statusCode == 404) {
          final fallbackUri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/api/v1/chat/rooms/$chatId/messages');
          response = await http.post(
            fallbackUri,
            headers: {
              'Authorization': 'Bearer $_token',
              'Content-Type': 'application/json',
              'x-tenant-id': ApiBackendConfig.tenantId,
            },
            body: jsonEncode({'content': effectiveContent}),
          );
        }

        if (response.statusCode == 200) {
          final body = jsonDecode(response.body);
          final rawMsg = body['data'] ?? body;
          confirmedMsg = ChatMessage.fromApi(rawMsg);

          final idx = list.indexWhere((m) => m.id == tempId);
          if (idx != -1) {
            list[idx] = confirmedMsg;
          } else {
            list.add(confirmedMsg);
          }
          list.sort((a, b) => a.createdAt.compareTo(b.createdAt));

          final convIdx = _conversations.indexWhere((c) => c.id == chatId);
          if (convIdx != -1) {
            final oldConv = _conversations[convIdx];
            _conversations.removeAt(convIdx);
            _conversations.insert(
              0,
              oldConv.copyWith(
                lastMessageText: confirmedMsg.text.isNotEmpty
                    ? confirmedMsg.text
                    : (confirmedMsg.type == MessageType.image ? '📷 Photo' : '📎 Attachment'),
                lastMessageTime: confirmedMsg.createdAt,
                lastMessageSenderId: confirmedMsg.senderId,
              ),
            );
          }
          notifyListeners();
        } else {
          debugPrint('[ApiBackendService] sendMessage failed: ${response.statusCode} ${response.body}');
        }
      } catch (e) {
        debugPrint('[ApiBackendService] Error in HTTP sendMessage: $e');
      }
    }

    // 2. Fallback to WebSocket only if HTTP send did not succeed
    if (confirmedMsg == null && _channel != null) {
      try {
        final payload = {
          'type': 'send_message',
          'room_id': chatId,
          'content': effectiveContent,
        };
        _channel!.sink.add(jsonEncode(payload));
      } catch (e) {
        debugPrint('[ApiBackendService] Error pushing message to websocket: $e');
      }
    }

    return confirmedMsg;
  }

  /// Upload chat media/photo directly to backend server static storage
  Future<Map<String, dynamic>> uploadMedia(File file) async {
    if (_token == null) throw Exception('Not authenticated');
    final uri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/chat/upload');
    final length = await file.length();
    final filename = file.path.split(RegExp(r'[/\\]')).last;

    Future<http.Response> doUpload(Uri targetUri) async {
      final request = http.MultipartRequest('POST', targetUri);
      request.headers['Authorization'] = 'Bearer $_token';
      request.headers['x-tenant-id'] = ApiBackendConfig.tenantId;
      request.files.add(
        http.MultipartFile(
          'file',
          file.openRead(),
          length,
          filename: filename,
        ),
      );
      final streamedResponse = await request.send();
      return http.Response.fromStream(streamedResponse);
    }

    http.Response response;
    try {
      response = await doUpload(uri);
      if (response.statusCode == 404) {
        final fallbackUri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/api/v1/chat/upload');
        response = await doUpload(fallbackUri);
      }
    } catch (e) {
      throw Exception('Failed to connect to upload server: $e');
    }

    if (response.statusCode == 200) {
      final body = jsonDecode(response.body);
      final data = body['data'] ?? body;
      return Map<String, dynamic>.from(data);
    } else {
      String detail = 'Failed to upload media (${response.statusCode})';
      try {
        final body = jsonDecode(response.body);
        detail = body['detail'] ?? body['message'] ?? detail;
      } catch (_) {}
      throw Exception(detail);
    }
  }

  /// Send password reset recovery link
  Future<String> forgotPassword(String email, {String? tenantId}) async {
    final cleanEmail = email.trim();
    final cleanTenant = (tenantId ?? ApiBackendConfig.tenantId).trim();
    final primaryUri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/auth/forgot-password');
    http.Response response;
    try {
      response = await http.post(
        primaryUri,
        headers: {
          'Content-Type': 'application/json',
          'x-tenant-id': cleanTenant,
        },
        body: jsonEncode({'email': cleanEmail}),
      );
      if (response.statusCode == 404) {
        final fallbackUri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/api/v1/auth/forgot-password');
        response = await http.post(
          fallbackUri,
          headers: {
            'Content-Type': 'application/json',
            'x-tenant-id': cleanTenant,
          },
          body: jsonEncode({'email': cleanEmail}),
        );
      }
    } catch (e) {
      throw Exception('Unable to reach server. Please check your internet connection.');
    }

    if (response.statusCode == 200) {
      try {
        final body = jsonDecode(response.body);
        final data = body['data'] ?? body;
        return (data['message'] ?? 'If an account exists with this email, a reset link has been sent.').toString();
      } catch (_) {
        return 'If an account exists with this email, a reset link has been sent.';
      }
    } else {
      String errorMsg = 'Failed to request password reset (${response.statusCode})';
      try {
        final body = jsonDecode(response.body);
        final detail = body['detail'] ?? body['message'] ?? errorMsg;
        if (detail is List && detail.isNotEmpty) {
          errorMsg = detail.map((e) => e['msg'] ?? e.toString()).join(', ');
        } else {
          errorMsg = detail.toString();
        }
      } catch (_) {}
      throw Exception(errorMsg);
    }
  }

  /// Update current user profile details
  Future<UserProfile> updateProfile({
    String? displayName,
    String? about,
    String? username,
    String? avatarUrl,
  }) async {
    if (_token == null || _currentUser == null) throw Exception('Not authenticated');

    final payload = <String, dynamic>{
      if (displayName != null) 'full_name': displayName.trim(),
      if (about != null) 'about': about.trim(),
      if (username != null) 'username': username.trim(),
      'publicProfile': <String, dynamic>{
        if (about != null) 'about': about.trim(),
        if (avatarUrl != null) 'avatarUrl': avatarUrl.trim(),
      },
    };

    final uri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/app-users/profile');
    http.Response response;
    try {
      response = await http.put(
        uri,
        headers: {
          'Authorization': 'Bearer $_token',
          'Content-Type': 'application/json',
          'x-tenant-id': ApiBackendConfig.tenantId,
        },
        body: jsonEncode(payload),
      );
      if (response.statusCode == 404) {
        final fallbackUri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/api/v1/app-users/profile');
        response = await http.put(
          fallbackUri,
          headers: {
            'Authorization': 'Bearer $_token',
            'Content-Type': 'application/json',
            'x-tenant-id': ApiBackendConfig.tenantId,
          },
          body: jsonEncode(payload),
        );
      }
    } catch (e) {
      throw Exception('Unable to reach server to update profile: $e');
    }

    if (response.statusCode == 200) {
      final effectiveName = displayName?.trim().isNotEmpty == true ? displayName!.trim() : _currentUser!.displayName;
      final initials = effectiveName.isNotEmpty ? effectiveName.substring(0, 1).toUpperCase() : _currentUser!.avatarInitials;

      _currentUser = _currentUser!.copyWith(
        displayName: effectiveName,
        username: username?.trim().isNotEmpty == true ? username!.trim() : _currentUser!.username,
        about: about?.trim() ?? _currentUser!.about,
        avatarUrl: avatarUrl?.trim() ?? _currentUser!.avatarUrl,
        avatarInitials: initials,
      );

      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('api_hub_user', jsonEncode(_currentUser!.toJson()));
      notifyListeners();
      return _currentUser!;
    } else {
      String detail = 'Failed to update profile (${response.statusCode})';
      try {
        final body = jsonDecode(response.body);
        detail = body['detail'] ?? body['message'] ?? detail;
      } catch (_) {}
      throw Exception(detail);
    }
  }

  /// Upload avatar/profile photo
  Future<String> uploadProfilePhoto(File file) async {
    if (_token == null) throw Exception('Not authenticated');
    final uri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/app-users/upload-photo');

    final length = await file.length();
    final filename = file.path.split(RegExp(r'[/\\]')).last;

    Future<http.Response> doUpload(Uri targetUri) async {
      final request = http.MultipartRequest('POST', targetUri);
      request.headers['Authorization'] = 'Bearer $_token';
      request.headers['x-tenant-id'] = ApiBackendConfig.tenantId;
      request.files.add(
        http.MultipartFile(
          'file',
          file.openRead(),
          length,
          filename: filename,
        ),
      );
      final streamedResponse = await request.send();
      return http.Response.fromStream(streamedResponse);
    }

    http.Response response;
    try {
      response = await doUpload(uri);
      if (response.statusCode == 404) {
        final fallbackUri = Uri.parse('${ApiBackendConfig.restApiBaseUrl}/api/v1/app-users/upload-photo');
        response = await doUpload(fallbackUri);
      }
    } catch (e) {
      throw Exception('Unable to reach server to upload photo: $e');
    }

    if (response.statusCode == 200) {
      final body = jsonDecode(response.body);
      final data = body['data'] ?? body;
      final avatarUrl = (data['avatarUrl'] ?? data['url'] ?? '').toString();
      if (avatarUrl.isNotEmpty && _currentUser != null) {
        _currentUser = _currentUser!.copyWith(avatarUrl: avatarUrl);
        final prefs = await SharedPreferences.getInstance();
        await prefs.setString('api_hub_user', jsonEncode(_currentUser!.toJson()));
        notifyListeners();
      }
      return avatarUrl;
    } else {
      String detail = 'Failed to upload profile photo (${response.statusCode})';
      try {
        final body = jsonDecode(response.body);
        detail = body['detail'] ?? body['message'] ?? detail;
      } catch (_) {}
      throw Exception(detail);
    }
  }

  List<ChatTask> _tasks = [];
  List<ChatTask> get tasks => _tasks;

  Future<void> loadTasks() async {
    if (_token == null) return;
    try {
      final response = await http.get(
        Uri.parse('${ApiBackendConfig.restApiBaseUrl}/tasks'),
        headers: {
          'Authorization': 'Bearer $_token',
          'x-tenant-id': ApiBackendConfig.tenantId,
        },
      );
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body)['data'] as List;
        _tasks = data.map((json) => ChatTask.fromApi(json)).toList();
        notifyListeners();
      }
    } catch (e) {
      debugPrint('[ApiBackendService] Error loading tasks: $e');
    }
  }

  Future<ChatTask> createTask({
    required String title,
    String? description,
    List<String> assigneeIds = const [],
    String priority = 'medium',
    DateTime? dueAt,
  }) async {
    final response = await http.post(
      Uri.parse('${ApiBackendConfig.restApiBaseUrl}/tasks'),
      headers: {
        'Authorization': 'Bearer $_token',
        'Content-Type': 'application/json',
        'x-tenant-id': ApiBackendConfig.tenantId,
      },
      body: jsonEncode({
        'title': title,
        'description': description,
        'priority': priority,
        'due_date': dueAt?.toIso8601String(),
        'assigned_to_ids': assigneeIds,
      }),
    );
    if (response.statusCode == 200) {
      final data = jsonDecode(response.body)['data'];
      final task = ChatTask.fromApi(data);
      _tasks.insert(0, task);
      notifyListeners();
      return task;
    }
    throw Exception('Failed to create task');
  }

  Future<void> updateTaskStatus(String taskId, TaskStatus status) async {
    String statusStr = 'todo';
    switch (status) {
      case TaskStatus.inbox:
        statusStr = 'todo';
        break;
      case TaskStatus.assigned:
        statusStr = 'assigned';
        break;
      case TaskStatus.inProgress:
        statusStr = 'in_progress';
        break;
      case TaskStatus.blocked:
        statusStr = 'blocked';
        break;
      case TaskStatus.completed:
        statusStr = 'completed';
        break;
      case TaskStatus.archived:
        statusStr = 'archived';
        break;
    }

    final response = await http.put(
      Uri.parse('${ApiBackendConfig.restApiBaseUrl}/tasks/$taskId'),
      headers: {
        'Authorization': 'Bearer $_token',
        'Content-Type': 'application/json',
        'x-tenant-id': ApiBackendConfig.tenantId,
      },
      body: jsonEncode({'status': statusStr}),
    );
    if (response.statusCode == 200) {
      final data = jsonDecode(response.body)['data'];
      final updatedTask = ChatTask.fromApi(data);
      final index = _tasks.indexWhere((t) => t.id == taskId);
      if (index != -1) {
        _tasks[index] = updatedTask;
        notifyListeners();
      }
    }
  }

  Future<void> updateTask({
    required String taskId,
    required String title,
    required String description,
    required List<String> assigneeIds,
    required String priority,
    required DateTime dueAt,
  }) async {
    final response = await http.put(
      Uri.parse('${ApiBackendConfig.restApiBaseUrl}/tasks/$taskId'),
      headers: {
        'Authorization': 'Bearer $_token',
        'Content-Type': 'application/json',
        'x-tenant-id': ApiBackendConfig.tenantId,
      },
      body: jsonEncode({
        'title': title,
        'description': description,
        'priority': priority,
        'due_date': dueAt.toIso8601String(),
        'assigned_to_ids': assigneeIds,
      }),
    );
    if (response.statusCode == 200) {
      final data = jsonDecode(response.body)['data'];
      final updatedTask = ChatTask.fromApi(data);
      final index = _tasks.indexWhere((t) => t.id == taskId);
      if (index != -1) {
        _tasks[index] = updatedTask;
        notifyListeners();
      }
    }
  }

  Future<void> deleteTask(String taskId) async {
    final response = await http.delete(
      Uri.parse('${ApiBackendConfig.restApiBaseUrl}/tasks/$taskId'),
      headers: {
        'Authorization': 'Bearer $_token',
        'x-tenant-id': ApiBackendConfig.tenantId,
      },
    );
    if (response.statusCode == 200) {
      _tasks.removeWhere((t) => t.id == taskId);
      notifyListeners();
    }
  }

  Future<void> signOut() async {
    _token = null;
    _currentUser = null;
    _reconnectTimer?.cancel();
    _reconnectTimer = null;
    _syncTimer?.cancel();
    _syncTimer = null;
    _activeConversationId = null;
    _cleanupWebSocket();
    _conversations.clear();
    _users.clear();
    _messages.clear();
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove('api_hub_token');
      await prefs.remove('api_hub_user');
    } catch (_) {}
    notifyListeners();
  }

  Future<void> setPresence(PresenceState state) async {
    if (_token == null) return;
    debugPrint('Setting presence to $state');
  }
}
