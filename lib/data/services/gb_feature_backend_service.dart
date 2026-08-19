import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:uuid/uuid.dart';

import '../../domain/models/chaty_preferences.dart';

class ServerScheduledMessage {
  final String id;
  final String conversationId;
  final String body;
  final DateTime scheduledAt;
  final String state;
  final DateTime? executedAt;
  final String? lastError;

  const ServerScheduledMessage({
    required this.id,
    required this.conversationId,
    required this.body,
    required this.scheduledAt,
    required this.state,
    this.executedAt,
    this.lastError,
  });
}

class GbFeatureBackendService {
  GbFeatureBackendService({SupabaseClient? client}) : _client = client ?? Supabase.instance.client;

  final SupabaseClient _client;
  final Uuid _uuid = const Uuid();

  String get _userId {
    final id = _client.auth.currentUser?.id;
    if (id == null) throw StateError('Authentication required.');
    return id;
  }

  Future<void> synchronizeAutomation(MessageAutomationPreferences prefs) async {
    final userId = _userId;
    for (final rule in prefs.autoReplyRules) {
      if (rule.keyword.trim().isEmpty || rule.responseMessage.trim().isEmpty) continue;
      final existing = await _client
          .from('auto_reply_rules')
          .select('id')
          .eq('user_id', userId)
          .ilike('keyword', rule.keyword.trim())
          .eq('response_body', rule.responseMessage.trim())
          .limit(1)
          .maybeSingle();
      if (existing == null) {
        await _client.from('auto_reply_rules').insert(<String, dynamic>{
          'user_id': userId,
          'keyword': rule.keyword.trim(),
          'response_body': rule.responseMessage.trim(),
          'scope': _scope(rule.recipientFilter),
          'enabled': rule.enabled,
        });
      } else {
        await _client.from('auto_reply_rules').update(<String, dynamic>{
          'scope': _scope(rule.recipientFilter),
          'enabled': rule.enabled,
          'updated_at': DateTime.now().toUtc().toIso8601String(),
        }).eq('id', existing['id']);
      }
    }

    for (final template in prefs.quickReplies) {
      final shortcut = template.shortcut.trim();
      final content = template.content.trim();
      if (shortcut.isEmpty || content.isEmpty) continue;
      await _client.from('quick_reply_templates').upsert(<String, dynamic>{
        'user_id': userId,
        'shortcut': shortcut,
        'title': template.title.trim(),
        'content': content,
        'updated_at': DateTime.now().toUtc().toIso8601String(),
      }, onConflict: 'user_id,shortcut');
    }
  }

  Future<String> createAutoReplyRule({required String keyword, required String response, String scope = 'all'}) async {
    final row = await _client.from('auto_reply_rules').insert(<String, dynamic>{
      'user_id': _userId,
      'keyword': keyword.trim(),
      'response_body': response.trim(),
      'scope': scope,
      'enabled': true,
    }).select('id').single();
    return row['id'].toString();
  }

  Future<void> deleteAutoReplyRule(String ruleId) async {
    await _client.from('auto_reply_rules').delete().eq('id', ruleId).eq('user_id', _userId);
  }

  Future<void> deleteAutoReplyRuleBySignature(String keyword, String response) async {
    await _client.from('auto_reply_rules').delete().eq('user_id', _userId).ilike('keyword', keyword.trim()).eq('response_body', response.trim());
  }

  Future<String> scheduleMessage({required String conversationId, required String body, required DateTime scheduledAt}) async {
    final raw = await _client.rpc('schedule_message', params: <String, dynamic>{
      'p_conversation_id': conversationId,
      'p_body': body.trim(),
      'p_scheduled_at': scheduledAt.toUtc().toIso8601String(),
      'p_metadata': <String, dynamic>{'source': 'chaty_scheduler'},
    });
    return raw.toString();
  }

  Future<void> cancelScheduledMessage(String id) async {
    await _client.rpc('cancel_scheduled_message', params: <String, dynamic>{'p_id': id});
  }

  Future<List<ServerScheduledMessage>> getScheduledMessages() async {
    final rows = await _client
        .from('scheduled_messages')
        .select('id,conversation_id,body,scheduled_at,state,executed_at,last_error')
        .eq('user_id', _userId)
        .order('scheduled_at', ascending: false);
    return (rows as List)
        .whereType<Map>()
        .map((raw) {
          final row = Map<String, dynamic>.from(raw);
          return ServerScheduledMessage(
            id: row['id'].toString(),
            conversationId: row['conversation_id'].toString(),
            body: row['body']?.toString() ?? '',
            scheduledAt: DateTime.parse(row['scheduled_at'].toString()).toLocal(),
            state: row['state']?.toString() ?? 'pending',
            executedAt: row['executed_at'] == null ? null : DateTime.tryParse(row['executed_at'].toString())?.toLocal(),
            lastError: row['last_error']?.toString(),
          );
        })
        .toList(growable: false);
  }

  Future<String> upsertQuickReply({required String shortcut, required String title, required String content}) async {
    final row = await _client.from('quick_reply_templates').upsert(<String, dynamic>{
      'user_id': _userId,
      'shortcut': shortcut.trim(),
      'title': title.trim(),
      'content': content.trim(),
      'updated_at': DateTime.now().toUtc().toIso8601String(),
    }, onConflict: 'user_id,shortcut').select('id').single();
    return row['id'].toString();
  }

  Future<void> deleteQuickReply(String shortcut) async {
    await _client.from('quick_reply_templates').delete().eq('user_id', _userId).eq('shortcut', shortcut.trim());
  }

  Future<List<Map<String, dynamic>>> getBlockedUsers() async {
    final raw = await _client.rpc('get_my_blocked_users');
    if (raw is! List) return <Map<String, dynamic>>[];
    return raw.whereType<Map>().map((row) => Map<String, dynamic>.from(row)).toList(growable: false);
  }

  Future<void> blockUser(String userId) async {
    await _client.rpc('block_user', params: <String, dynamic>{'p_user_id': userId});
  }

  Future<void> unblockUser(String userId) async {
    await _client.rpc('unblock_user', params: <String, dynamic>{'p_user_id': userId});
  }

  Future<int> massSend({required List<String> conversationIds, required String body}) async {
    final raw = await _client.rpc('mass_send_message', params: <String, dynamic>{
      'p_conversation_ids': conversationIds,
      'p_body': body.trim(),
    });
    return raw is int ? raw : int.tryParse(raw?.toString() ?? '') ?? 0;
  }

  Future<String> sendStructuredMessage({
    required String conversationId,
    required String type,
    required String body,
    Map<String, dynamic> metadata = const <String, dynamic>{},
  }) async {
    final raw = await _client.rpc('send_message', params: <String, dynamic>{
      'p_conversation_id': conversationId,
      'p_client_message_id': _uuid.v4(),
      'p_body': body,
      'p_type': type,
      'p_metadata': metadata,
    });
    return raw.toString();
  }

  Future<String> createPoll({
    required String conversationId,
    required String question,
    required List<String> options,
    bool allowMultiple = false,
  }) async {
    final clean = options.map((value) => value.trim()).where((value) => value.isNotEmpty).toList(growable: false);
    if (clean.length < 2) throw Exception('A poll requires at least two options.');
    final raw = await _client.rpc('create_poll', params: <String, dynamic>{
      'p_conversation_id': conversationId,
      'p_client_message_id': _uuid.v4(),
      'p_question': question.trim(),
      'p_options': clean,
      'p_allow_multiple': allowMultiple,
    });
    return raw.toString();
  }

  Future<void> votePoll({required String messageId, required String optionId}) async {
    await _client.rpc('vote_poll', params: <String, dynamic>{'p_message_id': messageId, 'p_option_id': optionId});
  }

  Future<void> setTyping(String conversationId, bool isTyping) async {
    await _client.rpc('set_typing_state', params: <String, dynamic>{
      'p_conversation_id': conversationId,
      'p_is_typing': isTyping,
    });
  }

  Future<void> editMessage(String messageId, String body) async {
    await _client.rpc('edit_chat_message', params: <String, dynamic>{
      'p_message_id': messageId,
      'p_body': body.trim(),
    });
  }

  static String _scope(String recipientFilter) {
    switch (recipientFilter.toLowerCase()) {
      case 'groups':
      case 'group':
        return 'group';
      case 'contacts':
      case 'direct':
        return 'direct';
      default:
        return 'all';
    }
  }
}
