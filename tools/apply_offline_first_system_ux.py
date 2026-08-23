#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding='utf-8')


def must_replace(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f'missing marker: {label}')
    return text.replace(old, new, 1)

# ---------------------------------------------------------------------------
# Backend: cache-first hydration. Local encrypted snapshots are presentation
# acceleration only; Supabase remains the authoritative remote source.
# ---------------------------------------------------------------------------
path = 'lib/data/services/backend_service.dart'
text = read(path)
if "import 'local_snapshot_cache_service.dart';" not in text:
    text = text.replace(
        "import 'mls_e2ee_service.dart';",
        "import 'mls_e2ee_service.dart';\nimport 'local_snapshot_cache_service.dart';",
        1,
    )
if 'final LocalSnapshotCacheService _snapshots' not in text:
    text = text.replace(
        "  final Uuid _uuid = const Uuid();",
        "  final Uuid _uuid = const Uuid();\n  final LocalSnapshotCacheService _snapshots = LocalSnapshotCacheService();",
        1,
    )

old_initialize = '''  Future<void> initialize() async {
    if (_isInitialized) return;

    _client.auth.onAuthStateChange.listen((AuthState state) {
      unawaited(_handleSession(state.session));
    });

    await _handleSession(_client.auth.currentSession);
    _isInitialized = true;
    notifyListeners();
  }
'''
new_initialize = '''  Future<void> initialize() async {
    if (_isInitialized) return;

    _client.auth.onAuthStateChange.listen((AuthState state) {
      unawaited(_handleSession(state.session));
    });

    final session = _client.auth.currentSession;
    if (session != null) {
      _currentSession = _mapSession(session);
      _seedAuthProfile(session);
      await _hydrateCachedState(session.user.id);
    }
    _isInitialized = true;
    notifyListeners();

    // Network, MLS and realtime initialization happen after the first usable
    // frame. Cached conversations/messages stay visible if refresh fails.
    if (session != null) unawaited(_refreshAuthenticatedSession(session));
  }
'''
if old_initialize in text:
    text = text.replace(old_initialize, new_initialize, 1)

old_session_tail = '''    _currentSession = _mapSession(session);
    await _hydrateAuthenticatedState();
    await _subscribeRealtime();
  }
'''
new_session_tail = '''    _currentSession = _mapSession(session);
    _seedAuthProfile(session);
    await _hydrateCachedState(session.user.id);
    notifyListeners();
    unawaited(_refreshAuthenticatedSession(session));
  }

  Future<void> _refreshAuthenticatedSession(Session session) async {
    if (_client.auth.currentSession?.user.id != session.user.id) return;
    try {
      await _hydrateAuthenticatedState();
      if (_client.auth.currentSession?.user.id == session.user.id) {
        await _subscribeRealtime();
      }
    } catch (error, stackTrace) {
      debugPrint('Chaty remote refresh deferred: $error\\n$stackTrace');
    }
  }

  void _seedAuthProfile(Session session) {
    if (_currentUser?.id == session.user.id) return;
    final displayName = session.user.userMetadata?['display_name']?.toString().trim();
    final username = session.user.userMetadata?['username']?.toString().trim();
    final effectiveName = displayName != null && displayName.isNotEmpty
        ? displayName
        : (session.user.email?.split('@').first ?? 'Chaty User');
    final profile = UserProfile(
      id: session.user.id,
      displayName: effectiveName,
      username: username != null && username.isNotEmpty
          ? username
          : (session.user.email?.split('@').first ?? 'user'),
      avatarInitials: _initials(effectiveName),
      avatarColorHex:
          session.user.userMetadata?['avatar_color_hex']?.toString() ??
          '0xFF0F766E',
      about: session.user.userMetadata?['about']?.toString() ?? '',
      presence: PresenceState.online,
      lastSeenAt: DateTime.now(),
      isVerified: false,
      email: session.user.email ?? '',
      phone: session.user.phone ?? '',
      safetyNumber: '',
      avatarUrl: session.user.userMetadata?['avatar_url']?.toString(),
      bannerUrl: session.user.userMetadata?['banner_url']?.toString(),
    );
    _currentUser = profile;
    _usersById[profile.id] = profile;
  }

  Future<void> _hydrateCachedState(String userId) async {
    try {
      final profileValue = await _snapshots.readJson(
        userId: userId,
        scope: 'profile',
      );
      if (profileValue is Map) {
        final row = profileValue.map(
          (key, value) => MapEntry(key.toString(), value),
        );
        final authUser = _client.auth.currentUser;
        final profile = _profileFromRow(
          row,
          email: authUser?.email ?? '',
          phone: authUser?.phone ?? '',
        );
        _currentUser = profile;
        _usersById[profile.id] = profile;
      }

      final conversationValue = await _snapshots.readJson(
        userId: userId,
        scope: 'conversations',
      );
      if (conversationValue is List) {
        final rows = conversationValue
            .whereType<Map>()
            .map(
              (row) => row.map(
                (key, value) => MapEntry(key.toString(), value),
              ),
            )
            .toList(growable: false);
        _applyConversationRows(rows, replace: true);
        for (final conversationId in _conversationsById.keys) {
          await _hydrateCachedMessages(userId, conversationId);
        }
      }
      notifyListeners();
    } catch (error, stackTrace) {
      debugPrint('Chaty cache hydration skipped: $error\\n$stackTrace');
    }
  }

  Future<bool> _hydrateCachedMessages(
    String userId,
    String conversationId,
  ) async {
    final value = await _snapshots.readJson(
      userId: userId,
      scope: 'messages_$conversationId',
    );
    if (value is! List) return false;
    final rows = value
        .whereType<Map>()
        .map(
          (row) => row.map((key, value) => MapEntry(key.toString(), value)),
        )
        .toList(growable: false);
    if (rows.isEmpty) return false;
    final messages = rows.map(_messageFromRow).toList()
      ..sort((a, b) => a.createdAt.compareTo(b.createdAt));
    _messagesByChatId[conversationId] = messages;
    return true;
  }
'''
if old_session_tail in text:
    text = text.replace(old_session_tail, new_session_tail, 1)

# Replace repeated conversation row mapping with a reusable method and persist
# exactly the authoritative RPC rows after successful refresh.
old_load_conv_start = '''      final rows = _asRows(raw);
      final next = <String, Conversation>{};

      for (final row in rows) {
        final id = row['conversation_id']?.toString() ?? '';
        if (id.isEmpty) continue;
        final participantIds = _stringList(row['participant_ids']);
        final adminIds = _stringList(row['admin_ids']);

        final conversation = Conversation(
          id: id,
          type: row['kind'] == 'group'
              ? ConversationType.group
              : ConversationType.direct,
          title: row['title']?.toString() ?? 'Conversation',
          participantIds: participantIds,
          adminIds: adminIds,
          avatarInitials: row['avatar_initials']?.toString(),
          avatarColorHex: row['avatar_color_hex']?.toString(),
          lastMessageText: row['last_message']?.toString() ?? '',
          lastMessageTime: _date(row['last_message_at']) ?? DateTime.now(),
          lastMessageSenderId: row['last_message_sender_id']?.toString() ?? '',
          unreadCount: _integer(row['unread_count']),
          isPinned: row['is_pinned'] == true,
          isArchived: row['is_archived'] == true,
          isMuted: row['is_muted'] == true,
          draftText: row['draft_text']?.toString() ?? '',
          encryptionStatus: EncryptionStatus.verificationNeeded,
        );
        next[id] = conversation;
      }

      _conversationsById
        ..clear()
        ..addAll(next);
      _messagesByChatId.removeWhere((id, _) => !next.containsKey(id));

      if (loadMembers) {
        await Future.wait<void>(next.keys.map(_loadConversationMembers));
      }
'''
new_load_conv_start = '''      final rows = _asRows(raw);
      _applyConversationRows(rows, replace: true);
      final userId = _client.auth.currentUser?.id;
      if (userId != null) {
        unawaited(
          _snapshots.writeJson(
            userId: userId,
            scope: 'conversations',
            value: rows,
          ),
        );
      }

      if (loadMembers) {
        await Future.wait<void>(
          _conversationsById.keys.map(_loadConversationMembers),
        );
      }
'''
if old_load_conv_start in text:
    text = text.replace(old_load_conv_start, new_load_conv_start, 1)

marker = '''  Future<void> _loadConversationMembers(String conversationId) async {'''
if 'void _applyConversationRows(' not in text:
    helper = '''  void _applyConversationRows(
    List<Map<String, dynamic>> rows, {
    required bool replace,
  }) {
    final next = <String, Conversation>{};
    for (final row in rows) {
      final id = row['conversation_id']?.toString() ?? '';
      if (id.isEmpty) continue;
      next[id] = Conversation(
        id: id,
        type: row['kind'] == 'group'
            ? ConversationType.group
            : ConversationType.direct,
        title: row['title']?.toString() ?? 'Conversation',
        participantIds: _stringList(row['participant_ids']),
        adminIds: _stringList(row['admin_ids']),
        avatarInitials: row['avatar_initials']?.toString(),
        avatarColorHex: row['avatar_color_hex']?.toString(),
        lastMessageText: row['last_message']?.toString() ?? '',
        lastMessageTime: _date(row['last_message_at']) ?? DateTime.now(),
        lastMessageSenderId: row['last_message_sender_id']?.toString() ?? '',
        unreadCount: _integer(row['unread_count']),
        isPinned: row['is_pinned'] == true,
        isArchived: row['is_archived'] == true,
        isMuted: row['is_muted'] == true,
        draftText: row['draft_text']?.toString() ?? '',
        encryptionStatus: EncryptionStatus.verificationNeeded,
      );
    }
    if (replace) {
      _conversationsById
        ..clear()
        ..addAll(next);
      _messagesByChatId.removeWhere((id, _) => !next.containsKey(id));
    } else {
      _conversationsById.addAll(next);
    }
  }

'''
    text = text.replace(marker, helper + marker, 1)

# Cache profile RPC row immediately after a successful network load.
profile_insert = '''        _currentUser = profile;
        _usersById[profile.id] = profile;'''
if "scope: 'profile'" not in text[text.find('Future<void> _loadCurrentProfile'):text.find('Future<void> _loadConversations')]:
    text = text.replace(
        profile_insert,
        profile_insert + '''
        unawaited(
          _snapshots.writeJson(
            userId: user.id,
            scope: 'profile',
            value: Map<String, dynamic>.from(row),
          ),
        );''',
        1,
    )

# Cache decrypted message snapshots (still encrypted on disk by snapshot cache).
messages_assignment = '''    _messagesByChatId[conversationId] = messages;

    final conversation = _conversationsById[conversationId];'''
if "scope: 'messages_$conversationId'" not in text:
    text = text.replace(
        messages_assignment,
        '''    _messagesByChatId[conversationId] = messages;
    final userId = _client.auth.currentUser?.id;
    if (userId != null) {
      unawaited(
        _snapshots.writeJson(
          userId: userId,
          scope: 'messages_$conversationId',
          value: hydratedRows,
        ),
      );
    }

    final conversation = _conversationsById[conversationId];''',
        1,
    )

old_ensure = '''  Future<void> ensureConversationLoaded(String conversationId) async {
    if (!_conversationsById.containsKey(conversationId)) {
      await _loadConversations();
    }
    await _loadConversationMembers(conversationId);
    await _loadMessages(conversationId);
    await markAsRead(conversationId);
  }
'''
new_ensure = '''  Future<void> ensureConversationLoaded(String conversationId) async {
    final userId = _client.auth.currentUser?.id;
    var hasLocalMessages = _messagesByChatId[conversationId]?.isNotEmpty == true;
    if (!hasLocalMessages && userId != null) {
      hasLocalMessages = await _hydrateCachedMessages(userId, conversationId);
      if (hasLocalMessages) notifyListeners();
    }

    Future<void> refresh() async {
      if (!_conversationsById.containsKey(conversationId)) {
        await _loadConversations();
      }
      await _loadConversationMembers(conversationId);
      await _loadMessages(conversationId);
      await markAsRead(conversationId);
      notifyListeners();
    }

    if (hasLocalMessages) {
      unawaited(
        refresh().catchError((Object error, StackTrace stackTrace) {
          debugPrint('Chaty cached chat refresh deferred: $error\\n$stackTrace');
        }),
      );
      return;
    }
    await refresh();
  }
'''
if old_ensure in text:
    text = text.replace(old_ensure, new_ensure, 1)
write(path, text)

# ---------------------------------------------------------------------------
# Chat screen: cached timeline renders immediately; first frame is always at
# newest message. Raw database/native errors never reach the user.
# ---------------------------------------------------------------------------
path = 'lib/features/chats/chat_detail_screen.dart'
text = read(path)
if "components/signature_components.dart" not in text:
    text = text.replace(
        "import '../../ui/core/design_system/components/app_components.dart';",
        "import '../../ui/core/design_system/components/app_components.dart';\nimport '../../ui/core/design_system/components/signature_components.dart';",
        1,
    )
text = text.replace(
    '  bool _loadingMessages = true;',
    '  bool _loadingMessages = true;',
    1,
)
init_marker = '''    widget.dataStore.addListener(_onDataStoreChanged);
    _scrollToBottom(animate: false);
    _loadConversation();'''
if init_marker in text:
    text = text.replace(
        init_marker,
        '''    widget.dataStore.addListener(_onDataStoreChanged);
    final cachedMessages = widget.dataStore.getMessages(widget.conversationId);
    _lastKnownMessageCount = cachedMessages.length;
    _loadingMessages = cachedMessages.isEmpty;
    _scrollToBottom(animate: false);
    unawaited(_loadConversation());''',
        1,
    )
load_start = '''  Future<void> _loadConversation() async {
    // Keep _loadingMessages=true until cached/remote messages are actually
    // available — clearing it here used to show a false “No messages yet”
    // empty state while the first query was still in flight.
    if (mounted) {
      setState(() => _loadError = null);
    }
'''
if load_start in text:
    text = text.replace(
        load_start,
        '''  Future<void> _loadConversation() async {
    if (mounted) {
      setState(() {
        _loadError = null;
        if (widget.dataStore.getMessages(widget.conversationId).isNotEmpty) {
          _loadingMessages = false;
        }
      });
      _scrollToBottom(animate: false);
    }
''',
        1,
    )
# Generic error path for send message.
raw_send = '''      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(error.toString().replaceFirst('Exception: ', '')),
        ),
      );'''
if raw_send in text:
    text = text.replace(
        raw_send,
        '''      debugPrint('Chaty send failed: $error');
      ChatyActivityIsland.show(
        context,
        icon: Icons.cloud_off_rounded,
        title: _friendlySendFailure(error),
        subtitle: 'Your draft is preserved. Chaty will retry when secure sync is ready.',
      );''',
        1,
    )
raw_voice = '''        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Unable to send voice note: $error')),
        );'''
if raw_voice in text:
    text = text.replace(
        raw_voice,
        '''        debugPrint('Chaty voice-note send failed: $error');
        ChatyActivityIsland.show(
          context,
          icon: Icons.mic_off_rounded,
          title: _friendlySendFailure(error),
          subtitle: 'The voice note remains local until secure sync is available.',
        );''',
        1,
    )
helper_marker = '''  Future<void> _beginVoice({required bool locked}) async {'''
if '_friendlySendFailure(Object error)' not in text:
    helper = '''  String _friendlySendFailure(Object error) {
    final value = error.toString().toLowerCase();
    if (value.contains('every conversation member must register an mls device') ||
        value.contains('mls device')) {
      return 'Waiting for secure chat setup';
    }
    if (value.contains('network') ||
        value.contains('socket') ||
        value.contains('connection')) {
      return 'Saved locally — waiting for connection';
    }
    return 'Couldn’t send this item';
  }

'''
    text = text.replace(helper_marker, helper + helper_marker, 1)
write(path, text)

# ---------------------------------------------------------------------------
# Startup: show Flutter immediately; initialize noncritical services after the
# first frame. This removes avoidable startup waits before the chat cache can
# paint.
# ---------------------------------------------------------------------------
path = 'lib/main.dart'
text = read(path)
old_main = '''  setupLocator();
  await locator<ThemeController>().init();
  await locator<AppIconController>().initialize();
  await locator<NotificationChannelManager>().initialize();
  await locator<PushTokenService>().initialize();
  runApp(const ChatyApp());
}'''
new_main = '''  setupLocator();
  await locator<ThemeController>().init();
  runApp(const ChatyApp());

  // Do not block first paint on launcher-icon/channel/push housekeeping.
  unawaited(locator<AppIconController>().initialize());
  unawaited(locator<NotificationChannelManager>().initialize());
  unawaited(locator<PushTokenService>().initialize());
}'''
if old_main in text:
    text = text.replace(old_main, new_main, 1)
# Cache the merged listenable once instead of allocating/discarding one during
# every root build. This also removes a class of lifecycle assertion churn.
if 'late final Listenable _rootListenable;' not in text:
    text = text.replace(
        '  late final StatusService _statusService;',
        '  late final StatusService _statusService;\n  late final Listenable _rootListenable;',
        1,
    )
    text = text.replace(
        '''    _statusService = StatusService();''',
        '''    _statusService = StatusService();
    _rootListenable = Listenable.merge(<Listenable>[
      _themeController,
      _preferencesController,
      _appearanceController,
      _backend,
      _richRealtime,
      _callService,
    ]);''',
        1,
    )
    text = text.replace(
        '''      listenable: Listenable.merge(<Listenable>[
        _themeController,
        _preferencesController,
        _appearanceController,
        _backend,
        _richRealtime,
        _callService,
      ]),''',
        '      listenable: _rootListenable,',
        1,
    )
write(path, text)

# ---------------------------------------------------------------------------
# Push token truthfulness: generated UUIDs are device registration IDs, not FCM
# tokens. Do not label them or treat them as a working push transport.
# ---------------------------------------------------------------------------
path = 'lib/data/services/push_token_service.dart'
text = read(path)
text = text.replace(
    "  bool _isRegistered = false;",
    "  bool _isRegistered = false;\n  bool _hasRealPushTransport = false;",
    1,
)
if 'bool get hasRealPushTransport' not in text:
    text = text.replace(
        '  bool get isRegistered => _isRegistered;',
        '  bool get isRegistered => _isRegistered;\n  bool get hasRealPushTransport => _hasRealPushTransport;',
        1,
    )
text = text.replace(
    '// Load or generate a production-ready unique push device token',
    '// Load or generate a stable Chaty device registration id. This is NOT an FCM token.',
)
text = text.replace(
    "token = 'fcm_${platformName}_${_uuid.v4()}';",
    "token = 'device_${platformName}_${_uuid.v4()}';",
)
text = text.replace(
    '''    _currentToken = token;

    final registeredUser =''',
    '''    _currentToken = token;
    _hasRealPushTransport = false;

    final registeredUser =''',
    1,
)
write(path, text)

# ---------------------------------------------------------------------------
# Android privacy: app-private cache is not backed up to cloud/device-transfer.
# ---------------------------------------------------------------------------
path = 'android/app/src/main/AndroidManifest.xml'
text = read(path)
if 'android:allowBackup="false"' not in text:
    text = text.replace(
        '    <application\n        android:label="Chaty"',
        '    <application\n        android:allowBackup="false"\n        android:fullBackupContent="false"\n        android:label="Chaty"',
        1,
    )
write(path, text)

print('Offline-first/system UX patch applied.')
