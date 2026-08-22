import 'dart:async';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:mime/mime.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:uuid/uuid.dart';

import '../../injection/locator.dart';
import '../../ui/core/controllers/preferences_controller.dart';
import 'backend_service.dart';
import 'notification_service.dart';

class StatusRecord {
  final String id;
  final String userId;
  final String text;
  final String mediaType;
  final String? mediaPath;
  final String? mediaName;
  final int mediaSize;
  final int durationSeconds;
  final DateTime createdAt;
  final DateTime expiresAt;
  final DateTime? deletedAt;

  const StatusRecord({
    required this.id,
    required this.userId,
    required this.text,
    required this.mediaType,
    required this.mediaPath,
    required this.mediaName,
    required this.mediaSize,
    required this.durationSeconds,
    required this.createdAt,
    required this.expiresAt,
    required this.deletedAt,
  });

  bool get hasMedia => mediaPath != null && mediaPath!.isNotEmpty;
  bool get isDeleted => deletedAt != null;
}

class StatusService {
  StatusService({
    SupabaseClient? client,
    ChatyPreferencesController? preferences,
  }) : _client = client ?? Supabase.instance.client,
       _preferences = preferences ?? locator<ChatyPreferencesController>();

  static const String bucket = 'status-media';

  final SupabaseClient _client;
  final ChatyPreferencesController _preferences;
  final Uuid _uuid = const Uuid();

  StreamSubscription<List<Map<String, dynamic>>>? _revocationSub;
  final Set<String> _revocationSeenAlive = <String>{};
  final Set<String> _revocationAlerted = <String>{};
  // New-status alert state (abu_saleh_toast_status family).
  final Set<String> _statusAlertSeenIds = <String>{};
  bool _statusAlertBaselineDone = false;
  // Status-VIEWED alert state (notification.notifyStatusViewed consumer).
  StreamSubscription<List<Map<String, dynamic>>>? _viewEventsSub;
  final Set<String> _statusViewAlerted = <String>{};
  bool _statusViewBaselineDone = false;

  /// Real consumer for `privacy.statusRevocationAlert` +
  /// `notification.notifyStatusDeleted`.
  ///
  /// Watches the RAW status_updates realtime stream app-wide (unfiltered, so
  /// deletions are observable even without anti-delete retention) and fires
  /// an event notification the moment a CONTACT's status transitions from
  /// alive to deleted. The first snapshot only establishes the baseline, so
  /// already-deleted statuses never alert on app start — same policy as the
  /// message-revoke alert in RichChatRealtimeService.
  void startRevocationWatch() {
    if (_revocationSub != null) return;
    _startViewEventsWatch();
    _revocationSub = _client
        .from('status_updates')
        .stream(primaryKey: const <String>['id'])
        .listen((rows) {
          final myId = _client.auth.currentUser?.id;
          final baselineBatch = !_statusAlertBaselineDone;
          for (final raw in rows) {
            final row = Map<String, dynamic>.from(raw);
            final id = row['id']?.toString() ?? '';
            if (id.isEmpty) continue;
            final rowUserId = row['user_id']?.toString() ?? '';
            final deletedRaw = row['deleted_at'];
            if (deletedRaw == null) {
              final unseen = !_statusAlertSeenIds.contains(id);
              _statusAlertSeenIds.add(id);
              _revocationSeenAlive.add(id);
              // Real consumer for `abu_saleh_toast_status` (+ `_bc`/`_tc`):
              // a contact publishing a NEW status after our baseline.
              if (baselineBatch || !unseen) continue;
              if (rowUserId.isEmpty || rowUserId == myId) continue;
              if (!_preferences.notification.enableGlobalNotifications) {
                continue;
              }
              if (!_preferences.gbBool('abu_saleh_toast_status')) continue;
              try {
                final profile = locator<ChatyBackendService>().getUserById(
                  rowUserId,
                );
                final text = (row['content']?.toString() ?? '').trim();
                locator<ChatyNotificationService>().triggerEventNotification(
                  title:
                      '${profile?.displayName ?? 'Someone'}'
                      ' added a new status',
                  body: text.isEmpty
                      ? 'New status update'
                      : (text.length > 80 ? '${text.substring(0, 80)}…' : text),
                  icon: Icons.auto_awesome_rounded,
                  color:
                      _preferences.gbColor('abu_saleh_toast_status_bc') ??
                      const Color(0xFF6366F1),
                  textColor: _preferences.gbColor('abu_saleh_toast_status_tc'),
                  userId: rowUserId,
                  avatarInitials: profile?.avatarInitials,
                  avatarColorHex: profile?.avatarColorHex,
                );
              } catch (_) {
                // Notification failures must never break the stream listener.
              }
              continue;
            }
            // Deleted row: only alert if we saw it alive first and have not
            // alerted for it already.
            if (!_revocationSeenAlive.contains(id)) continue;
            if (_revocationAlerted.contains(id)) continue;
            _revocationAlerted.add(id);
            final userId = row['user_id']?.toString() ?? '';
            if (userId.isEmpty || userId == myId) continue;
            final notifications = _preferences.notification;
            if (!notifications.enableGlobalNotifications) continue;
            if (!_preferences.privacy.statusRevocationAlert) continue;
            if (!notifications.notifyStatusDeleted) continue;
            try {
              final profile = locator<ChatyBackendService>().getUserById(
                userId,
              );
              locator<ChatyNotificationService>().triggerEventNotification(
                title: 'Status update removed',
                body: '${profile?.displayName ?? 'Someone'} deleted a status.',
                icon: Icons.history_toggle_off_rounded,
                color: const Color(0xFF6366F1),
                userId: userId,
                avatarInitials: profile?.avatarInitials,
                avatarColorHex: profile?.avatarColorHex,
              );
            } catch (_) {
              // Notification failures must never break the stream listener.
            }
          }
        });
  }

  /// Real consumer for `notification.notifyStatusViewed`.
  ///
  /// Watches the status_view_events realtime stream and fires an event
  /// notification when a CONTACT views one of MY statuses. Rows the viewer
  /// marked hidden never pass the owner RLS policy, so hidden visits are
  /// structurally undeliverable here. The first snapshot only establishes a
  /// dedupe baseline so existing viewers never re-alert on app start.
  void _startViewEventsWatch() {
    if (_viewEventsSub != null) return;
    _viewEventsSub = _client
        .from('status_view_events')
        .stream(primaryKey: const <String>['id'])
        .listen((rows) {
          final myId = _client.auth.currentUser?.id;
          if (myId == null || myId.isEmpty) return;
          final baselineBatch = !_statusViewBaselineDone;
          for (final raw in rows) {
            final row = Map<String, dynamic>.from(raw);
            final ownerId = row['status_owner_id']?.toString() ?? '';
            if (ownerId != myId) continue;
            final viewerId = row['viewer_id']?.toString() ?? '';
            final statusId = row['status_id']?.toString() ?? '';
            if (viewerId.isEmpty || viewerId == myId) continue;
            final key = '$statusId:$viewerId';
            if (_statusViewAlerted.contains(key)) continue;
            _statusViewAlerted.add(key);
            // Baseline batch only seeds dedupe state — never alerts, so the
            // backlog of past viewers is silent on app start.
            if (baselineBatch) continue;
            if (!_preferences.notification.enableGlobalNotifications) {
              continue;
            }
            if (!_preferences.notification.notifyStatusViewed) continue;
            try {
              final profile = locator<ChatyBackendService>().getUserById(
                viewerId,
              );
              locator<ChatyNotificationService>().triggerEventNotification(
                title: 'Status viewed',
                body:
                    '${profile?.displayName ?? 'Someone'}'
                    ' viewed your status.',
                icon: Icons.visibility_rounded,
                color: const Color(0xFF10B981),
                userId: viewerId,
                avatarInitials: profile?.avatarInitials,
                avatarColorHex: profile?.avatarColorHex,
              );
            } catch (_) {
              // Notification failures must never break the stream listener.
            }
          }
          _statusViewBaselineDone = true;
        });
  }

  /// Clears baseline/alert tracking (used on account switch).
  void resetRevocationTracking() {
    _revocationSeenAlive.clear();
    _revocationAlerted.clear();
    _statusAlertSeenIds.clear();
    _statusAlertBaselineDone = false;
    _statusViewAlerted.clear();
    _statusViewBaselineDone = false;
  }

  void stopRevocationWatch() {
    _revocationSub?.cancel();
    _revocationSub = null;
    _viewEventsSub?.cancel();
    _viewEventsSub = null;
    resetRevocationTracking();
  }

  bool get isRevocationWatchActive => _revocationSub != null;

  Stream<List<StatusRecord>> watchActiveStatuses() {
    return _client
        .from('status_updates')
        .stream(primaryKey: const <String>['id'])
        .order('created_at', ascending: false)
        .map((rows) {
          final now = DateTime.now();
          final keepDeleted =
              _preferences.privacy.antiDeleteStatus ||
              _preferences.gbBool('yoAntiRevokeStatus');
          return rows
              .map((row) => _fromRow(Map<String, dynamic>.from(row)))
              .where(
                (status) =>
                    status.expiresAt.isAfter(now) &&
                    (!status.isDeleted || keepDeleted),
              )
              .toList(growable: false);
        });
  }

  Future<StatusRecord> publishText(String text) async {
    final value = text.trim();
    if (value.isEmpty) throw Exception('Enter status text before publishing.');
    return _insert(text: value, mediaType: 'text');
  }

  Future<StatusRecord?> pickAndPublish({
    required String mediaType,
    String text = '',
  }) async {
    if (mediaType == 'audio' && !_preferences.gbBool('abu9aleh_status_audio')) {
      throw Exception('Enable Audio Status in Advanced Features first.');
    }

    final fileType = switch (mediaType) {
      'image' => FileType.image,
      'video' => FileType.video,
      'audio' => FileType.audio,
      _ => FileType.any,
    };
    final files = await FilePicker.pickFiles(type: fileType);
    if (files.isEmpty) return null;
    final picked = files.single;
    final path = picked.path;
    if (path == null || path.isEmpty)
      throw Exception('The selected file is not accessible on this device.');

    final file = File(path);
    if (!await file.exists())
      throw Exception('The selected file no longer exists.');
    final size = await file.length();
    if (size <= 0) throw Exception('The selected file is empty.');
    final configuredMb = mediaType == 'audio'
        ? _preferences.gbDouble('abo_saleh_audio_limit_check', fallback: 50)
        : _preferences.gbDouble('Up_size_limit', fallback: 100);
    final maxBytes = configuredMb.clamp(1, 2048).round() * 1024 * 1024;
    if (size > maxBytes)
      throw Exception(
        'Selected media exceeds the configured ${configuredMb.round()} MB limit.',
      );

    final user = _client.auth.currentUser;
    if (user == null) throw Exception('Authentication required.');
    final safeName = _safeFileName(picked.name);
    final objectPath = '${user.id}/${_uuid.v4()}_$safeName';
    final mimeType = lookupMimeType(path) ?? _fallbackMime(mediaType);

    await _client.storage
        .from(bucket)
        .upload(
          objectPath,
          file,
          fileOptions: FileOptions(
            cacheControl: '3600',
            upsert: false,
            contentType: mimeType,
          ),
        );

    try {
      return await _insert(
        text: text.trim(),
        mediaType: mediaType,
        mediaPath: objectPath,
        mediaName: picked.name,
        mediaSize: size,
        mimeType: mimeType,
      );
    } catch (_) {
      await _client.storage.from(bucket).remove(<String>[objectPath]);
      rethrow;
    }
  }

  Future<String> createSignedUrl(String path, {int expiresInSeconds = 900}) {
    return _client.storage.from(bucket).createSignedUrl(path, expiresInSeconds);
  }

  Future<void> markViewed(StatusRecord status) async {
    final hide =
        _preferences.privacy.hideViewStatus ||
        _preferences.gbBool('yoHideStatViewV2');
    await _client.rpc(
      'mark_status_viewed',
      params: <String, dynamic>{'p_status_id': status.id, 'p_hide_view': hide},
    );
  }

  Future<void> deleteStatus(StatusRecord status) async {
    final user = _client.auth.currentUser;
    if (user == null || user.id != status.userId)
      throw Exception('You can only delete your own status.');
    await _client.rpc(
      'delete_status_update',
      params: <String, dynamic>{'p_status_id': status.id},
    );
    // Media is retained until status expiry so anti-delete viewers can still
    // access the original signed object. Storage cleanup can safely happen
    // after the status expiry window.
  }

  Future<StatusRecord> _insert({
    required String text,
    required String mediaType,
    String? mediaPath,
    String? mediaName,
    String? mimeType,
    int mediaSize = 0,
    int durationSeconds = 0,
  }) async {
    final user = _client.auth.currentUser;
    if (user == null) throw Exception('Authentication required.');
    final now = DateTime.now().toUtc();
    final row = await _client
        .from('status_updates')
        .insert(<String, dynamic>{
          'user_id': user.id,
          'content': text,
          'media_path': mediaPath,
          'media_type': mediaType,
          'mime_type': mimeType,
          'media_name': mediaName,
          'media_size': mediaSize,
          'duration_seconds': durationSeconds,
          'expires_at': now.add(const Duration(hours: 24)).toIso8601String(),
        })
        .select()
        .single();
    return _fromRow(Map<String, dynamic>.from(row));
  }

  void dispose() {
    stopRevocationWatch();
  }

  static StatusRecord _fromRow(Map<String, dynamic> row) {
    return StatusRecord(
      id: row['id']?.toString() ?? '',
      userId: row['user_id']?.toString() ?? '',
      text: row['content']?.toString() ?? '',
      mediaType: row['media_type']?.toString() ?? 'text',
      mediaPath: row['media_path']?.toString(),
      mediaName: row['media_name']?.toString(),
      mediaSize: row['media_size'] is num
          ? (row['media_size'] as num).round()
          : int.tryParse(row['media_size']?.toString() ?? '') ?? 0,
      durationSeconds: row['duration_seconds'] is num
          ? (row['duration_seconds'] as num).round()
          : 0,
      createdAt:
          DateTime.tryParse(row['created_at']?.toString() ?? '')?.toLocal() ??
          DateTime.now(),
      expiresAt:
          DateTime.tryParse(row['expires_at']?.toString() ?? '')?.toLocal() ??
          DateTime.now(),
      deletedAt: row['deleted_at'] == null
          ? null
          : DateTime.tryParse(row['deleted_at'].toString())?.toLocal(),
    );
  }

  static String _safeFileName(String source) {
    final cleaned = source
        .replaceAll(RegExp(r'[^A-Za-z0-9._-]'), '_')
        .replaceAll(RegExp(r'_+'), '_');
    if (cleaned.isEmpty) return 'status';
    return cleaned.length > 120
        ? cleaned.substring(cleaned.length - 120)
        : cleaned;
  }

  static String _fallbackMime(String type) {
    return switch (type) {
      'image' => 'image/jpeg',
      'video' => 'video/mp4',
      'audio' => 'audio/mp4',
      _ => 'application/octet-stream',
    };
  }
}
