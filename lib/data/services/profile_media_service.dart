import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:image_picker/image_picker.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:flutter_image_compress/flutter_image_compress.dart';
import 'package:uuid/uuid.dart';

/// Source of the media: device camera or the system gallery.
enum ProfileMediaSource { camera, gallery }

/// Uploads the authenticated user's avatar photo and profile banner to the
/// public `profile-media` storage bucket (owner-prefixed paths, enforced by
/// the bucket's storage policies) and returns the public URL that is then
/// persisted on `profiles` via the normal profile update path.
class ProfileMediaService {
  final SupabaseClient _client;
  final ImagePicker _picker = ImagePicker();
  final Uuid _uuid = const Uuid();

  ProfileMediaService({SupabaseClient? client})
    : _client = client ?? Supabase.instance.client;

  static const String bucket = 'profile-media';
  static const int maxBytesBeforeCompress = 6 * 1024 * 1024;
  static const int maxBytesAfterCompress = 2 * 1024 * 1024;
  static const List<String> allowedMime = <String>[
    'image/jpeg',
    'image/png',
    'image/webp',
  ];

  Future<String> uploadAvatar({
    required ProfileMediaSource source,
  }) => _pickCompressUpload(
    source: source,
    folder: 'avatars',
    square: 512,
    quality: 88,
  );

  Future<String> uploadBanner({required ProfileMediaSource source}) =>
      _pickCompressUpload(
        source: source,
        folder: 'banners',
        square: 1280,
        quality: 85,
      );

  Future<String> _pickCompressUpload({
    required ProfileMediaSource source,
    required String folder,
    required int square,
    required int quality,
  }) async {
    final userId = _client.auth.currentUser?.id;
    if (userId == null || userId.isEmpty) {
      throw Exception('Sign in to change your profile media.');
    }

    final picked = await _picker.pickImage(
      source: source == ProfileMediaSource.camera
          ? ImageSource.camera
          : ImageSource.gallery,
      maxWidth: square.toDouble() * 1.5,
      maxHeight: square.toDouble() * 1.5,
      imageQuality: 92,
    );
    if (picked == null) throw const _CancelledException();
    final original = File(picked.path);
    if (!await original.exists()) {
      throw Exception('The selected image is no longer accessible.');
    }
    final size = await original.length();
    if (size > maxBytesBeforeCompress) {
      throw Exception('Image is too large (max 6 MB before compression).');
    }

    // Normalize to JPEG at the target size so avatars/banners stay small and
    // consistent regardless of the source format.
    final compressedPath =
        '${original.parent.path}/'
        'chaty_${folder}_'
        '${DateTime.now().millisecondsSinceEpoch}.jpg';
    final compressed = await FlutterImageCompress.compressAndGetFile(
      original.path,
      compressedPath,
      format: CompressFormat.jpeg,
      quality: quality,
      minWidth: square,
      minHeight: square,
      keepExif: false,
    );
    final file = compressed != null ? File(compressed.path) : original;
    if (!await file.exists()) {
      throw Exception('Image processing failed. Try a different picture.');
    }
    if (await file.length() > maxBytesAfterCompress) {
      throw Exception('Processed image is still too large. Try another one.');
    }

    final objectPath = '$userId/$folder/${_uuid.v4()}.jpg';
    try {
      await _client.storage.from(bucket).upload(
        objectPath,
        file,
        fileOptions: const FileOptions(
          cacheControl: '3600',
          upsert: false,
          contentType: 'image/jpeg',
        ),
      );
    } on StorageException {
      rethrow;
    } catch (_) {
      rethrow;
    } finally {
      try {
        await original.delete();
        if (compressed != null && await File(compressed.path).exists()) {
          await File(compressed.path).delete();
        }
      } catch (_) {}
    }
    return _client.storage.from(bucket).getPublicUrl(objectPath);
  }
}

class _CancelledException implements Exception {
  const _CancelledException();

  @override
  String toString() => 'cancelled';
}

@visibleForTesting
bool isSupportedImageMime(String mime) =>
    ProfileMediaService.allowedMime.contains(mime);
