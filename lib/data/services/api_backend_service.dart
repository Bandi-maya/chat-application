import 'dart:async';
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

  List<Conversation> _conversations = [];
  List<UserProfile> _users = [];
  final Map<String, List<ChatMessage>> _messages = {};

  bool _requiresSubscription = false;
  String? _subscriptionErrorMessage;

  bool get isInitialized => _isInitialized;
  bool get isAuthenticated => _token != null && _currentUser != null;
  bool get requiresSubscription => _requiresSubscription;
  String? get subscriptionErrorMessage => _subscriptionErrorMessage;
  UserProfile? get currentUser => _currentUser;
  Map<String, dynamic>? get currentOrg => _currentOrg;
  Map<String, dynamic>? get currentOrganization => _currentOrg;
  List<Conversation> get conversations => _conversations;
  List<UserProfile> get users => _users;

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
        _connectWebSocket();
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
    final response = await http.post(
      Uri.parse('${ApiBackendConfig.restApiBaseUrl}/auth/token'),
      headers: {
        'Content-Type': 'application/json',
        'x-tenant-id': ApiBackendConfig.tenantId,
      },
      body: jsonEncode({'username': identifier, 'password': password}),
    );
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

    if (response != null && response.statusCode == 200) {
      final body = jsonDecode(response.body);
      final data = body['data'] ?? body;
      return Map<String, dynamic>.from(data);
    } else if (response != null && response.statusCode == 404) {
      throw Exception('Organization "$clean" not found. Please verify the ID or register a new organization.');
    } else {
      String detail = 'Organization check failed (${response?.statusCode ?? 'No response'})';
      try {
        if (response != null) {
          final body = jsonDecode(response.body);
          detail = body['detail'] ?? body['message'] ?? detail;
        }
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
    final response = await http.post(
      Uri.parse('${ApiBackendConfig.restApiBaseUrl}/auth/register'),
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
      final userObj = data['user'] ?? {};

      final userId = (userObj['id'] ?? userObj['uid'] ?? payload['uid'] ?? '').toString();
      final fullName = (userObj['full_name'] ?? userObj['display_name'] ?? userObj['name'] ?? payload['sub'] ?? 'User').toString();
      final username = (userObj['username'] ?? payload['sub'] ?? '').toString();
      final initials = fullName.isNotEmpty ? fullName.substring(0, 1).toUpperCase() : 'U';

      _currentUser = UserProfile(
        id: userId,
        displayName: fullName,
        username: username,
        avatarInitials: initials,
        avatarColorHex: '0xFF6366F1',
        about: (userObj['role'] ?? payload['role'] ?? '').toString(),
        presence: PresenceState.online,
        lastSeenAt: DateTime.now(),
        isVerified: false,
        email: (userObj['email'] ?? '').toString(),
        phone: '',
        safetyNumber: '',
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

  void _connectWebSocket() {
    if (_token == null || _currentUser == null) return;
    _reconnectTimer?.cancel();

    try {
      final wsUrl = Uri.parse(
        ApiBackendConfig.getWebSocketUrl(_currentUser!.id),
      );
      debugPrint('[ApiBackendService] Connecting WebSocket to: $wsUrl');
      _channel = WebSocketChannel.connect(wsUrl);

      _channel?.stream.listen((message) {
        debugPrint('[ApiBackendService] Received message: $message');
        try {
          final payload = jsonDecode(message);
          if (payload['type'] == 'new_message') {
            final rawData = payload['data'] ?? payload['message'];
            if (rawData != null) {
              final chatMsg = ChatMessage.fromApi(rawData);
              final roomId = chatMsg.conversationId;
              final list = _messages.putIfAbsent(roomId, () => []);

              // Replace optimistic sending message if matching content
              final optIdx = list.indexWhere(
                (m) => m.deliveryState == DeliveryState.sending && m.text == chatMsg.text,
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
                    lastMessageText: chatMsg.text,
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
      }, onDone: () {
        debugPrint('[ApiBackendService] WebSocket Disconnected. Reconnecting in 4s...');
        _reconnectTimer = Timer(const Duration(seconds: 4), () {
          if (_token != null) _connectWebSocket();
        });
      });
    } catch (e) {
      debugPrint('[ApiBackendService] Error connecting to WebSocket: $e');
    }
  }

  Future<void> loadConversations() async {
    if (_token == null) return;
    try {
      final response = await http.get(
        Uri.parse('${ApiBackendConfig.restApiBaseUrl}/chat/rooms'),
        headers: {
          'Authorization': 'Bearer $_token',
          'x-tenant-id': ApiBackendConfig.tenantId,
        },
      );
      if (response.statusCode == 200) {
        _requiresSubscription = false;
        _subscriptionErrorMessage = null;
        final data = jsonDecode(response.body)['data'] as List;
        _conversations = data.map((json) => Conversation.fromApi(json)).toList();
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


  Future<void> loadUsers() async {
    if (_token == null) return;
    try {
      final response = await http.get(
        Uri.parse('${ApiBackendConfig.restApiBaseUrl}/app-users'),
        headers: {
          'Authorization': 'Bearer $_token',
          'x-tenant-id': ApiBackendConfig.tenantId,
        },
      );
      if (response.statusCode == 200) {
        final body = jsonDecode(response.body);
        final list = (body['data'] as List?) ?? [];
        _users = list.map((json) {
          final uid = (json['uid'] ?? json['id'] ?? '').toString();
          final fullName = (json['full_name'] ??
                  '${json['first_name'] ?? ''} ${json['last_name'] ?? ''}'.trim())
              .toString();
          final username = (json['username'] ?? '').toString();
          final displayName = fullName.isNotEmpty ? fullName : (username.isNotEmpty ? username : 'User');
          final initials = displayName.isNotEmpty ? displayName.substring(0, 1).toUpperCase() : 'U';

          return UserProfile(
            id: uid,
            displayName: displayName,
            username: username,
            avatarInitials: initials,
            avatarColorHex: '0xFF6366F1',
            about: (json['role'] ?? '').toString(),
            presence: PresenceState.online,
            lastSeenAt: DateTime.now(),
            isVerified: false,
            email: (json['email'] ?? '').toString(),
            phone: (json['mobile'] ?? '').toString(),
            safetyNumber: '',
          );
        }).toList();

        // Enrich current user display name and initials if found in users list
        if (_currentUser != null) {
          final me = _users.where((u) => u.id == _currentUser!.id).firstOrNull;
          if (me != null && (me.displayName.isNotEmpty && me.displayName != _currentUser!.displayName)) {
            _currentUser = _currentUser!.copyWith(
              displayName: me.displayName,
              avatarInitials: me.avatarInitials,
              about: me.about.isNotEmpty ? me.about : _currentUser!.about,
              email: me.email.isNotEmpty ? me.email : _currentUser!.email,
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
      }
    } catch (e) {
      debugPrint('[ApiBackendService] Error loading users: $e');
    }
  }

  Future<List<UserProfile>> searchUsers(String query, {bool includeSelf = false}) async {
    if (_users.isEmpty) {
      await loadUsers();
    }
    final q = query.toLowerCase().trim();
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

  Future<void> loadMessages(String chatId) async {
    if (_token == null) return;
    try {
      final response = await http.get(
        Uri.parse(
          '${ApiBackendConfig.restApiBaseUrl}/chat/rooms/$chatId/messages',
        ),
        headers: {
          'Authorization': 'Bearer $_token',
          'x-tenant-id': ApiBackendConfig.tenantId,
        },
      );
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body)['data'] as List;
        final parsed = data.map((json) => ChatMessage.fromApi(json)).toList();
        parsed.sort((a, b) => a.createdAt.compareTo(b.createdAt));
        _messages[chatId] = parsed;
        notifyListeners();
      }
    } catch (e) {
      debugPrint('[ApiBackendService] Error loading messages: $e');
    }
  }

  Future<void> sendMessage(String chatId, String content) async {
    final payload = {
      'type': 'send_message',
      'room_id': chatId,
      'content': content,
    };

    // Optimistic message append
    final tempMsg = ChatMessage(
      id: 'temp_${DateTime.now().millisecondsSinceEpoch}',
      conversationId: chatId,
      senderId: _currentUser?.id ?? '',
      text: content,
      createdAt: DateTime.now(),
      deliveryState: DeliveryState.sending,
    );
    final list = _messages.putIfAbsent(chatId, () => []);
    list.add(tempMsg);
    notifyListeners();

    if (_channel != null) {
      _channel!.sink.add(jsonEncode(payload));
    } else if (_token != null) {
      // Fallback via HTTP REST
      try {
        await http.post(
          Uri.parse('${ApiBackendConfig.restApiBaseUrl}/chat/rooms/$chatId/messages'),
          headers: {
            'Authorization': 'Bearer $_token',
            'Content-Type': 'application/json',
            'x-tenant-id': ApiBackendConfig.tenantId,
          },
          body: jsonEncode({'content': content}),
        );
        await loadMessages(chatId);
      } catch (e) {
        debugPrint('[ApiBackendService] Error in sendMessage fallback: $e');
      }
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
    _channel?.sink.close();
    _channel = null;
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
