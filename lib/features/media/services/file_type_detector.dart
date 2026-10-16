import 'dart:io';
import 'dart:typed_data';
import 'package:mime/mime.dart';
import '../models/media_file_descriptor.dart';

class FileTypeDetector {
  static const int _headerBytesToRead = 32;

  static Future<FileClassification> classifyFile(String filePath) async {
    final file = File(filePath);
    if (!await file.exists()) {
      return FileClassification(
        mimeType: 'application/octet-stream',
        category: MediaCategory.other,
        extension: _getExtension(filePath),
        isSafe: false,
      );
    }

    Uint8List headerBytes = Uint8List(0);
    try {
      final raf = await file.open(mode: FileMode.read);
      headerBytes = await raf.read(_headerBytesToRead);
      await raf.close();
    } catch (_) {
      // Failed to open file, fallback to extension
    }

    return classifyBytes(headerBytes, fileName: file.path);
  }

  static FileClassification classifyBytes(Uint8List bytes, {String? fileName}) {
    final ext = fileName != null ? _getExtension(fileName) : '';

    // Check Magic Bytes
    if (bytes.length >= 4) {
      // PDF: %PDF- (0x25 0x50 0x44 0x46 0x2D)
      if (bytes[0] == 0x25 && bytes[1] == 0x50 && bytes[2] == 0x44 && bytes[3] == 0x46) {
        return FileClassification(
          mimeType: 'application/pdf',
          category: MediaCategory.pdf,
          extension: 'pdf',
          isSafe: true,
        );
      }

      // PNG: 89 50 4E 47 0D 0A 1A 0A
      if (bytes[0] == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
        return FileClassification(
          mimeType: 'image/png',
          category: MediaCategory.image,
          extension: 'png',
          isSafe: true,
        );
      }

      // JPEG: FF D8 FF
      if (bytes[0] == 0xFF && bytes[1] == 0xD8 && bytes[2] == 0xFF) {
        return FileClassification(
          mimeType: 'image/jpeg',
          category: MediaCategory.image,
          extension: 'jpg',
          isSafe: true,
        );
      }

      // GIF: GIF87a or GIF89a
      if (bytes[0] == 0x47 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x38) {
        return FileClassification(
          mimeType: 'image/gif',
          category: MediaCategory.image,
          extension: 'gif',
          isSafe: true,
        );
      }

      // ZIP / DOCX / XLSX / APK: PK (0x50 0x4B 0x03 0x04)
      if (bytes[0] == 0x50 && bytes[1] == 0x4B && bytes[2] == 0x03 && bytes[3] == 0x04) {
        if (ext == 'docx' || ext == 'xlsx' || ext == 'pptx') {
          return FileClassification(
            mimeType: lookupMimeType(fileName ?? '') ?? 'application/vnd.openxmlformats-officedocument',
            category: MediaCategory.document,
            extension: ext,
            isSafe: true,
          );
        }
        return FileClassification(
          mimeType: 'application/zip',
          category: MediaCategory.archive,
          extension: 'zip',
          isSafe: true,
        );
      }

      // MP3: ID3 (0x49 0x44 0x33)
      if (bytes[0] == 0x49 && bytes[1] == 0x44 && bytes[2] == 0x33) {
        return FileClassification(
          mimeType: 'audio/mpeg',
          category: MediaCategory.audio,
          extension: 'mp3',
          isSafe: true,
        );
      }
    }

    // MP4 / MOV: ftyp at offset 4
    if (bytes.length >= 12 &&
        bytes[4] == 0x66 && bytes[5] == 0x74 && bytes[6] == 0x79 && bytes[7] == 0x70) {
      return FileClassification(
        mimeType: 'video/mp4',
        category: MediaCategory.video,
        extension: 'mp4',
        isSafe: true,
      );
    }

    // WebP: RIFF ... WEBP
    if (bytes.length >= 12 &&
        bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46 &&
        bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) {
      return FileClassification(
        mimeType: 'image/webp',
        category: MediaCategory.image,
        extension: 'webp',
        isSafe: true,
      );
    }

    // Text and Document extensions fallback
    final mime = (fileName != null ? lookupMimeType(fileName) : null) ?? 'application/octet-stream';
    MediaCategory cat = MediaCategory.other;

    if (mime.startsWith('image/')) {
      cat = MediaCategory.image;
    } else if (mime.startsWith('video/')) {
      cat = MediaCategory.video;
    } else if (mime.startsWith('audio/')) {
      cat = MediaCategory.audio;
    } else if (ext == 'pdf' || mime == 'application/pdf') {
      cat = MediaCategory.pdf;
    } else if (ext == 'txt' || ext == 'md' || ext == 'json' || ext == 'csv' ||
               ext == 'doc' || ext == 'docx' || ext == 'xls' || ext == 'xlsx' ||
               ext == 'ppt' || ext == 'pptx' || ext == 'rtf' || ext == 'xml' ||
               mime.startsWith('text/')) {
      cat = MediaCategory.document;
    } else if (ext == 'zip' || ext == 'tar' || ext == 'gz' || ext == 'rar' || ext == '7z') {
      cat = MediaCategory.archive;
    }

    return FileClassification(
      mimeType: mime,
      category: cat,
      extension: ext,
      isSafe: true,
    );
  }

  static String _getExtension(String path) {
    final idx = path.lastIndexOf('.');
    if (idx == -1 || idx == path.length - 1) return '';
    return path.substring(idx + 1).toLowerCase();
  }
}

class FileClassification {
  final String mimeType;
  final MediaCategory category;
  final String extension;
  final bool isSafe;

  const FileClassification({
    required this.mimeType,
    required this.category,
    required this.extension,
    required this.isSafe,
  });
}
