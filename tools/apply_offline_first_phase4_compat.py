#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / 'lib/data/services/backend_service.dart'
text = path.read_text(encoding='utf-8')

# The Phase-4 backend adds pagination/outbox state after the baseline session
# handler. Preserve those semantics while making cache hydration first-frame.
old_tail = '''    _currentSession = _mapSession(session);
    await _hydrateAuthenticatedState();
    await _subscribeRealtime();
    unawaited(_flushEncryptedOutbox());
  }
'''
new_tail = '''    _currentSession = _mapSession(session);
    _seedAuthProfile(session);
    await _hydrateCachedState(session.user.id);
    notifyListeners();
    unawaited(_refreshAuthenticatedSession(session));
  }
'''
if old_tail in text:
    text = text.replace(old_tail, new_tail, 1)

if 'Future<void> _refreshAuthenticatedSession(Session session)' not in text:
    marker = '  AuthSession _mapSession(Session session) {'
    if marker not in text:
        raise SystemExit('Phase-4 session insertion marker missing')
    helpers = r'''  Future<void> _refreshAuthenticatedSession(Session session) async {
    if (_client.auth.currentSession?.user.id != session.user.id) return;
    try {
      await _hydrateAuthenticatedState();
      if (_client.auth.currentSession?.user.id != session.user.id) return;
      await _subscribeRealtime();
      unawaited(_flushEncryptedOutbox());
    } catch (error, stackTrace) {
      debugPrint('Chaty remote refresh deferred: $error\n$stackTrace');
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
      debugPrint('Chaty cache hydration skipped: $error\n$stackTrace');
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
    _messageHasMoreByChatId.putIfAbsent(conversationId, () => true);
    return true;
  }

'''
    text = text.replace(marker, helpers + marker, 1)

# Make the reusable conversation mapper authoritative for Phase-4 too.
start = text.find('      final rows = _asRows(raw);\n      final next = <String, Conversation>{};', text.find('Future<void> _loadConversations'))
end_marker = '      if (loadMembers) {\n        await Future.wait<void>(next.keys.map(_loadConversationMembers));\n      }'
end = text.find(end_marker, start) if start >= 0 else -1
if start >= 0 and end >= 0:
    end += len(end_marker)
    replacement = '''      final rows = _asRows(raw);
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
      }'''
    text = text[:start] + replacement + text[end:]

# Ensure mapper also prunes pagination metadata when remote list removes chats.
text = text.replace(
    "      _messagesByChatId.removeWhere((id, _) => !next.containsKey(id));\n    } else {",
    "      _messagesByChatId.removeWhere((id, _) => !next.containsKey(id));\n"
    "      _messageHasMoreByChatId.removeWhere((id, _) => !next.containsKey(id));\n    } else {",
    1,
)

old_ensure = '''  Future<void> ensureConversationLoaded(String conversationId) async {
    if (!_conversationsById.containsKey(conversationId)) {
      await _loadConversations();
    }
    await _loadConversationMembers(conversationId);
    await _loadMessages(conversationId, replaceTimeline: true);
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
      await _loadMessages(conversationId, replaceTimeline: true);
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

# Persist the decrypted latest page after a successful fetch. Older pagination
# remains network-backed; startup needs the newest page only.
assignment = '    _messagesByChatId[conversationId] = next;\n\n    final conversation ='
if assignment in text and "scope: 'messages_$conversationId'" not in text[text.find('Future<void> _loadMessages'):text.find('Future<Map<String, dynamic>> _hydrateEncryptedMessageRow')]:
    text = text.replace(
        assignment,
        '''    _messagesByChatId[conversationId] = next;
    if (before == null && !appendOlder) {
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
    }

    final conversation =''',
        1,
    )

# Sanity invariants: these are compile-critical, not optional.
for needle in (
    'Future<void> _refreshAuthenticatedSession(Session session)',
    'void _seedAuthProfile(Session session)',
    'Future<void> _hydrateCachedState(String userId)',
    'Future<bool> _hydrateCachedMessages(',
    '_applyConversationRows(rows, replace: true)',
):
    if needle not in text:
        raise SystemExit(f'Phase-4 cache invariant missing: {needle}')

path.write_text(text, encoding='utf-8')
print('Phase-4 offline-first compatibility applied.')
