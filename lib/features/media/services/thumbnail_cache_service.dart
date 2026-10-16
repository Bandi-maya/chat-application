import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' as ui;

class ThumbnailCacheService {
  ThumbnailCacheService._();
  static final ThumbnailCacheService instance = ThumbnailCacheService._();

  final Map<String, Uint8List> _memoryCache = <String, Uint8List>{};
  static const int _maxCacheEntries = 120;

  bool hasThumbnail(String filePath) => _memoryCache.containsKey(filePath);

  Uint8List? getThumbnailBytes(String filePath) => _memoryCache[filePath];

  void putThumbnailBytes(String filePath, Uint8List bytes) {
    if (_memoryCache.length >= _maxCacheEntries) {
      final firstKey = _memoryCache.keys.first;
      _memoryCache.remove(firstKey);
    }
    _memoryCache[filePath] = bytes;
  }

  void clear() {
    _memoryCache.clear();
  }

  Future<Uint8List?> generateImageThumbnail(String imagePath, {int targetWidth = 180}) async {
    if (_memoryCache.containsKey(imagePath)) {
      return _memoryCache[imagePath];
    }
    final file = File(imagePath);
    if (!await file.exists()) return null;

    try {
      final bytes = await file.readAsBytes();
      final codec = await ui.instantiateImageCodec(
        bytes,
        targetWidth: targetWidth,
      );
      final frame = await codec.getNextFrame();
      final image = frame.image;
      final byteData = await image.toByteData(format: ui.ImageByteFormat.png);
      if (byteData != null) {
        final thumbBytes = byteData.buffer.asUint8List();
        putThumbnailBytes(imagePath, thumbBytes);
        return thumbBytes;
      }
    } catch (_) {
      // Fallback
    }
    return null;
  }
}
