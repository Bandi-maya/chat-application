import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:mime/mime.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:uuid/uuid.dart';

class StatusRecord {
  final String id;
  final String userId;
  final String text;
  final String mediaType;
  final String? mediaPath;
  final String? mediaName;
  final int mediaSize;
  final DateTime createdAt;
  final DateTime expiresAt;

  const StatusRecord({
    required this.id,
    required this.userId,
    required this.text,
    required this.mediaType,
    required this.mediaPath,
    required this.mediaName,
    required this.mediaSize,
    required this.createdAt,
    required this.expiresAt,
  });

  bool get hasMedia => mediaPath != null && mediaPath!.isNotEmpty;
}

class StatusService {
  StatusService({SupabaseClient? client})
      : _client = client ?? Supabase.instance.client;

  static const String bucket = 'status-media';
  static const int maxBytes = 50 * 1024 * 1024;

  final SupabaseClient _client;
  final Uuid _uuid = const Uuid();

  Stream<List<StatusRecord>> watchActiveStatuses() {
    return _client
        .from('status_updates')
        .stream(primaryKey: const <String>['id'])
        .order('created_at', ascending: false)
        .map((rows) {
      final now = DateTime.now();
      return rows
          .map((row) => _fromRow(Map<String, dynamic>.from(row)))
          .where((status) => status.expiresAt.isAfter(now))
          .toList(growable: false);
    });
  }

  Future<StatusRecord> publishText(String text) async {
    final value = text.trim();
    if (value.isEmpty) {
      throw Exception('Enter status text before publishing.');
    }
    return _insert(text: value, mediaType: 'text');
  }

  Future<StatusRecord?> pickAndPublish({
    required String mediaType,
    String text = '',
  }) async {
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
    if (path == null || path.isEmpty) {
      throw Exception('The selected file is not accessible on this device.');
    }

    final file = File(path);
    if (!await file.exists()) throw Exception('The selected file no longer exists.');
    final size = await file.length();
    if (size <= 0) throw Exception('The selected file is empty.');
    if (size > maxBytes) throw Exception('Status media must be 50 MB or smaller.');

    final user = _client.auth.currentUser;
    if (user == null) throw Exception('Authentication required.');

    final safeName = _safeFileName(picked.name);
    final objectPath = '${user.id}/${_uuid.v4()}_$safeName';
    final mimeType = lookupMimeType(path) ?? _fallbackMime(mediaType);

    await _client.storage.from(bucket).upload(
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
      );
    } catch (_) {
      await _client.storage.from(bucket).remove(<String>[objectPath]);
      rethrow;
    }
  }

  Future<String> createSignedUrl(String path, {int expiresInSeconds = 900}) {
    return _client.storage.from(bucket).createSignedUrl(path, expiresInSeconds);
  }

  Future<void> deleteStatus(StatusRecord status) async {
    final user = _client.auth.currentUser;
    if (user == null || user.id != status.userId) {
      throw Exception('You can only delete your own status.');
    }
    await _client.from('status_updates').delete().eq('id', status.id);
    final path = status.mediaPath;
    if (path != null && path.isNotEmpty) {
      await _client.storage.from(bucket).remove(<String>[path]);
    }
  }

  Future<StatusRecord> _insert({
    required String text,
    required String mediaType,
    String? mediaPath,
    String? mediaName,
    int mediaSize = 0,
  }) async {
    final user = _client.auth.currentUser;
    if (user == null) throw Exception('Authentication required.');
    final now = DateTime.now().toUtc();
    final row = await _client
        .from('status_updates')
        .insert(<String, dynamic>{
          'user_id': user.id,
          'text_content': text,
          'media_path': mediaPath,
          'media_type': mediaType,
          'media_name': mediaName,
          'media_size': mediaSize,
          'expires_at': now.add(const Duration(hours: 24)).toIso8601String(),
        })
        .select()
        .single();
    return _fromRow(Map<String, dynamic>.from(row));
  }

  static StatusRecord _fromRow(Map<String, dynamic> row) {
    return StatusRecord(
      id: row['id']?.toString() ?? '',
      userId: row['user_id']?.toString() ?? '',
      text: row['text_content']?.toString() ?? '',
      mediaType: row['media_type']?.toString() ?? 'text',
      mediaPath: row['media_path']?.toString(),
      mediaName: row['media_name']?.toString(),
      mediaSize: row['media_size'] is int
          ? row['media_size'] as int
          : int.tryParse(row['media_size']?.toString() ?? '') ?? 0,
      createdAt: DateTime.tryParse(row['created_at']?.toString() ?? '')?.toLocal() ?? DateTime.now(),
      expiresAt: DateTime.tryParse(row['expires_at']?.toString() ?? '')?.toLocal() ?? DateTime.now(),
    );
  }

  static String _safeFileName(String source) {
    final cleaned = source
        .replaceAll(RegExp(r'[^A-Za-z0-9._-]'), '_')
        .replaceAll(RegExp(r'_+'), '_');
    if (cleaned.isEmpty) return 'status';
    return cleaned.length > 120 ? cleaned.substring(cleaned.length - 120) : cleaned;
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
