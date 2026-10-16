import 'dart:io';

enum MediaCategory {
  image,
  video,
  audio,
  document,
  pdf,
  archive,
  other,
}

class MediaFileDescriptor {
  final String id;
  final String name;
  final String path;
  final int sizeBytes;
  final String mimeType;
  final MediaCategory category;
  final DateTime modifiedAt;
  final String? thumbnailPath;
  final int durationSeconds;
  final int pageCount;
  final bool isEncrypted;
  final bool isFavorite;
  final Map<String, dynamic> metadata;

  const MediaFileDescriptor({
    required this.id,
    required this.name,
    required this.path,
    required this.sizeBytes,
    required this.mimeType,
    required this.category,
    required this.modifiedAt,
    this.thumbnailPath,
    this.durationSeconds = 0,
    this.pageCount = 0,
    this.isEncrypted = false,
    this.isFavorite = false,
    this.metadata = const <String, dynamic>{},
  });

  String get humanReadableSize {
    if (sizeBytes < 1024) return '$sizeBytes B';
    if (sizeBytes < 1024 * 1024) return '${(sizeBytes / 1024).toStringAsFixed(1)} KB';
    if (sizeBytes < 1024 * 1024 * 1024) return '${(sizeBytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    return '${(sizeBytes / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
  }

  String get extension {
    final idx = name.lastIndexOf('.');
    if (idx == -1 || idx == name.length - 1) return '';
    return name.substring(idx + 1).toLowerCase();
  }

  bool get exists => File(path).existsSync();

  MediaFileDescriptor copyWith({
    String? id,
    String? name,
    String? path,
    int? sizeBytes,
    String? mimeType,
    MediaCategory? category,
    DateTime? modifiedAt,
    String? thumbnailPath,
    int? durationSeconds,
    int? pageCount,
    bool? isEncrypted,
    bool? isFavorite,
    Map<String, dynamic>? metadata,
  }) {
    return MediaFileDescriptor(
      id: id ?? this.id,
      name: name ?? this.name,
      path: path ?? this.path,
      sizeBytes: sizeBytes ?? this.sizeBytes,
      mimeType: mimeType ?? this.mimeType,
      category: category ?? this.category,
      modifiedAt: modifiedAt ?? this.modifiedAt,
      thumbnailPath: thumbnailPath ?? this.thumbnailPath,
      durationSeconds: durationSeconds ?? this.durationSeconds,
      pageCount: pageCount ?? this.pageCount,
      isEncrypted: isEncrypted ?? this.isEncrypted,
      isFavorite: isFavorite ?? this.isFavorite,
      metadata: metadata ?? this.metadata,
    );
  }
}
