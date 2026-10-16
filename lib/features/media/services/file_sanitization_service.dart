import 'dart:io';
import 'package:path_provider/path_provider.dart';

class FileSanitizationService {
  FileSanitizationService._();
  static final FileSanitizationService instance = FileSanitizationService._();

  static const int maxSafeFileSize = 100 * 1024 * 1024; // 100 MB
  static const int maxArchiveEntries = 5000;

  bool validateSafePath(String targetDirectory, String requestedFilePath) {
    final canonicalTarget = Directory(targetDirectory).resolveSymbolicLinksSync();
    final canonicalFile = File(requestedFilePath).resolveSymbolicLinksSync();
    return canonicalFile.startsWith(canonicalTarget);
  }

  String sanitizeFileName(String inputName) {
    final sanitized = inputName.replaceAll(RegExp(r'[\\/:*?"<>|\x00-\x1F]'), '_').trim();
    if (sanitized.isEmpty) return 'unnamed_file';
    return sanitized.length > 120 ? sanitized.substring(0, 120) : sanitized;
  }

  Future<Directory> getScopedTempDirectory() async {
    final appTemp = await getTemporaryDirectory();
    final mediaTemp = Directory('${appTemp.path}/chaty_media');
    if (!await mediaTemp.exists()) {
      await mediaTemp.create(recursive: true);
    }
    return mediaTemp;
  }

  Future<void> cleanExpiredTempFiles({Duration maxAge = const Duration(hours: 24)}) async {
    try {
      final dir = await getScopedTempDirectory();
      final now = DateTime.now();
      await for (final entity in dir.list()) {
        if (entity is File) {
          final stat = await entity.stat();
          if (now.difference(stat.modified) > maxAge) {
            await entity.delete();
          }
        }
      }
    } catch (_) {
      // Safe ignore
    }
  }
}
