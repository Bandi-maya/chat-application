import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:web_socket_channel/web_socket_channel.dart';
import 'dart:convert';
import 'backend_config.dart';
import '../../domain/models/user_profile.dart';
import '../../domain/models/conversation.dart';
import '../../domain/models/chat_message.dart';
import '../../domain/models/chat_task.dart';

class ApiBackendService extends ChangeNotifier {
  static final ApiBackendService _instance = ApiBackendService._internal();
  factory ApiBackendService() => _instance;
  ApiBackendService._internal();

  bool _isInitialized = false;
  UserProfile? _currentUser;
  String? _token;
  WebSocketChannel? _channel;

  List<Conversation> _conversations = [];
  final Map<String, List<ChatMessage>> _messages = {};

  bool get isInitialized => _isInitialized;
  bool get isAuthenticated => _token != null && _currentUser != null;
  UserProfile? get currentUser => _currentUser;
  List<Conversation> get conversations => _conversations;

  List<ChatMessage> getMessages(String chatId) => _messages[chatId] ?? [];

  Future<void> initialize() async {
    if (_isInitialized) return;
    _isInitialized = true;
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

      _currentUser = UserProfile(
        id: userObj['id'] ?? payload['uid'] ?? '',
        displayName: userObj['display_name'] ?? payload['sub'] ?? 'User',
        username: userObj['username'] ?? payload['sub'] ?? '',
        avatarInitials: 'XX',
        avatarColorHex: '0xFF6366F1',
        about: '',
        presence: PresenceState.online,
        lastSeenAt: DateTime.now(),
        isVerified: false,
        email: userObj['email'] ?? '',
        phone: '',
        safetyNumber: '',
      );
      _connectWebSocket();
      loadConversations();
      loadTasks();
      notifyListeners();
      return _currentUser!;
    } else {
      throw Exception('Authentication failed: ${response.statusCode}');
    }
  }

  void _connectWebSocket() {
    if (_token == null) return;
    try {
      final wsUrl = Uri.parse(
        ApiBackendConfig.getWebSocketUrl(_currentUser!.id),
      );
      _channel = WebSocketChannel.connect(wsUrl);

      _channel?.stream.listen((message) {
        debugPrint('Received message: $message');
        try {
          final payload = jsonDecode(message);
          if (payload['type'] == 'new_message') {
            final data = payload['data'];
            final chatMsg = ChatMessage.fromApi(data);
            final roomId = chatMsg.conversationId;
            if (_messages[roomId] == null) {
              _messages[roomId] = [];
            }
            _messages[roomId]!.insert(0, chatMsg);
            notifyListeners();
          }
        } catch (e) {
          debugPrint('Error parsing websocket message: $e');
        }
      }, onError: (error) {
          debugPrint('WebSocket Error: $error');
        },
        onDone: () {
          debugPrint('WebSocket Disconnected');
        },
      );
    } catch (e) {
      debugPrint('Error connecting to WebSocket: $e');
    }
  }

  Future<void> loadConversations() async {
    if (_token == null) return;
    final response = await http.get(
      Uri.parse('${ApiBackendConfig.restApiBaseUrl}/chat/rooms'),
      headers: {
        'Authorization': 'Bearer $_token',
        'x-tenant-id': ApiBackendConfig.tenantId,
      },
    );
    if (response.statusCode == 200) {
      final data = jsonDecode(response.body)['data'] as List;
      _conversations = data.map((json) => Conversation.fromApi(json)).toList();
      notifyListeners();
    }
  }

  Future<void> loadMessages(String chatId) async {
    if (_token == null) return;
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
      _messages[chatId] = parsed.reversed.toList();
      notifyListeners();
    }
  }

  List<ChatTask> _tasks = [];
  List<ChatTask> get tasks => _tasks;

  Future<void> loadTasks() async {
    if (_token == null) return;
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

  void sendMessage(String chatId, String content) {
    if (_channel != null) {
      final payload = {
        'type': 'send_message',
        'room_id': chatId,
        'content': content,
      };
      _channel!.sink.add(jsonEncode(payload));
    } else {
      debugPrint('Cannot send message: WebSocket is disconnected');
    }
  }

  Future<void> signOut() async {
    _token = null;
    _currentUser = null;
    _channel?.sink.close();
    _channel = null;
    notifyListeners();
  }

  Future<void> setPresence(PresenceState state) async {
    if (_token == null) return;
    // Presence update via REST or WebSocket
    debugPrint('Setting presence to $state');
  }
}
