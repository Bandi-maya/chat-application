import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter_image_compress/flutter_image_compress.dart';
import 'package:mime/mime.dart';
import 'package:path_provider/path_provider.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:uuid/uuid.dart';

import '../../domain/models/chat_message.dart';
import '../../injection/locator.dart';
import '../../ui/core/controllers/chaty_preferences_controller.dart';

class ChatMediaService {
  ChatMediaService({SupabaseClient? client, ChatyPreferencesController? preferences})
      : _client = client ?? Supabase.instance.client,
        _preferences = preferences ?? locator<ChatyPreferencesController>();

  static const String bucket = 'chat-media';
  final SupabaseClient _client;
  final ChatyPreferencesController _preferences;
  final Uuid _uuid = const Uuid();

  Future<MessageAttachment?> pickAndUpload({required String conversationId, required String type}) async {
    final picked = await FilePicker.pickFile(type: _pickerType(type));
    if (picked == null) return null;
    final sourcePath = picked.path;
    if (sourcePath == null || sourcePath.isEmpty) throw Exception('The selected file is not accessible on this device.');
    return uploadFile(conversationId: conversationId, type: type, sourcePath: sourcePath, displayName: picked.name);
  }

  Future<List<MessageAttachment>> pickAndUploadMultiple({required String conversationId, required String type}) async {
    final limit = _preferences.gbDouble('Img_share_limit', fallback: 30).clamp(1, 100).round();
    final picked = await FilePicker.pickFiles(type: _pickerType(type));
    if (picked.isEmpty) return <MessageAttachment>[];
    if (picked.length > limit) throw Exception('You selected ${picked.length} files; your configured send limit is $limit.');
    final result = <MessageAttachment>[];
    for (final item in picked) {
      final path = item.path;
      if (path == null || path.isEmpty) continue;
      result.add(await uploadFile(conversationId: conversationId, type: type, sourcePath: path, displayName: item.name));
    }
    return result;
  }

  Future<MessageAttachment> uploadFile({
    required String conversationId,
    required String type,
    required String sourcePath,
    String? displayName,
    int durationSeconds = 0,
  }) async {
    var file = File(sourcePath);
    if (!await file.exists()) throw Exception('The selected file no longer exists.');
    var rawName = displayName ?? sourcePath.split(Platform.pathSeparator).last;
    var mimeType = lookupMimeType(sourcePath) ?? _fallbackMimeForType(type);

    if (type == 'image' && mimeType != 'image/gif') {
      final quality = _preferences.gbDouble('Img_highres_seek', fallback: 85).clamp(10, 100).round();
      if (quality < 100) {
        try {
          final temp = await getTemporaryDirectory();
          final targetPath = '${temp.path}/chaty_${_uuid.v4()}.jpg';
          final compressed = await FlutterImageCompress.compressAndGetFile(
            file.absolute.path,
            targetPath,
            quality: quality,
            minWidth: 2560,
            minHeight: 2560,
            keepExif: false,
          );
          if (compressed != null) {
            final candidate = File(compressed.path);
            if (await candidate.exists() && await candidate.length() > 0) {
              file = candidate;
              final dot = rawName.lastIndexOf('.');
              rawName = '${dot > 0 ? rawName.substring(0, dot) : rawName}.jpg';
              mimeType = 'image/jpeg';
            }
          }
        } catch (_) {
          // Keep original image if the platform encoder cannot process it.
        }
      }
    }

    final size = await file.length();
    if (size <= 0) throw Exception('The selected file is empty.');
    final maxBytes = _maxBytes(type);
    if (size > maxBytes) {
      final limitMb = (maxBytes / (1024 * 1024)).round();
      throw Exception('This $type exceeds your configured $limitMb MB media limit.');
    }

    final authUser = _client.auth.currentUser;
    if (authUser == null) throw Exception('Authentication required.');
    final safeName = _safeFileName(rawName);
    final objectPath = '${authUser.id}/$conversationId/${_uuid.v4()}_$safeName';

    await _client.storage.from(bucket).upload(
      objectPath,
      file,
      fileOptions: FileOptions(cacheControl: '3600', upsert: false, contentType: mimeType),
    );

    return MessageAttachment(
      id: _uuid.v4(),
      type: type,
      name: rawName,
      size: _formatBytes(size),
      url: objectPath,
      durationSeconds: durationSeconds,
    );
  }

  int _maxBytes(String type) {
    final configured = type == 'audio'
        ? _preferences.gbDouble('abo_saleh_audio_limit_check', fallback: 50)
        : _preferences.gbDouble('Up_size_limit', fallback: 100);
    return configured.clamp(1, 2048).round() * 1024 * 1024;
  }

  Future<String> createSignedUrl(String objectPath, {int expiresInSeconds = 900}) async {
    if (objectPath.trim().isEmpty) throw Exception('Attachment path is missing.');
    return _client.storage.from(bucket).createSignedUrl(objectPath, expiresInSeconds);
  }

  Future<void> deleteOwnAttachment(String objectPath) async {
    if (objectPath.trim().isEmpty) return;
    await _client.storage.from(bucket).remove(<String>[objectPath]);
  }

  static FileType _pickerType(String type) {
    return switch (type) {
      'image' => FileType.image,
      'video' => FileType.video,
      'audio' => FileType.audio,
      _ => FileType.any,
    };
  }

  static String _safeFileName(String source) {
    final cleaned = source.replaceAll(RegExp(r'[^A-Za-z0-9._-]'), '_').replaceAll(RegExp(r'_+'), '_');
    if (cleaned.isEmpty) return 'attachment';
    return cleaned.length > 120 ? cleaned.substring(cleaned.length - 120) : cleaned;
  }

  static String _fallbackMimeForType(String type) {
    return switch (type) {
      'image' => 'image/jpeg',
      'video' => 'video/mp4',
      'audio' => 'audio/mp4',
      _ => 'application/octet-stream',
    };
  }

  static String _formatBytes(int bytes) {
    if (bytes < 1024) return '$bytes B';
    final kb = bytes / 1024;
    if (kb < 1024) return '${kb.toStringAsFixed(1)} KB';
    final mb = kb / 1024;
    return '${mb.toStringAsFixed(1)} MB';
  }
}
