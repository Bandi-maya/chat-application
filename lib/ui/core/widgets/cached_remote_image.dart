import 'dart:convert';
import 'dart:io';

import 'package:cryptography/cryptography.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';

/// Lightweight Chaty-owned disk cache for profile/banner and other identity
/// imagery. A previously downloaded image is painted first; network refresh is
/// best effort and atomically replaces the cached file.
class ChatyCachedRemoteImage extends StatefulWidget {
  const ChatyCachedRemoteImage({
    super.key,
    required this.url,
    required this.fit,
    required this.fallback,
  });

  final String url;
  final BoxFit fit;
  final Widget fallback;

  @override
  State<ChatyCachedRemoteImage> createState() => _ChatyCachedRemoteImageState();
}

class _ChatyCachedRemoteImageState extends State<ChatyCachedRemoteImage> {
  File? _file;
  bool _refreshing = false;

  @override
  void initState() {
    super.initState();
    _restoreThenRefresh();
  }

  @override
  void didUpdateWidget(covariant ChatyCachedRemoteImage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.url != widget.url) {
      _file = null;
      _restoreThenRefresh();
    }
  }

  Future<void> _restoreThenRefresh() async {
    final url = widget.url.trim();
    if (url.isEmpty || !(url.startsWith('https://') || url.startsWith('http://'))) {
      return;
    }
    final file = await _cacheFile(url);
    if (await file.exists() && mounted) setState(() => _file = file);
    if (_refreshing) return;
    _refreshing = true;
    try {
      final request = await HttpClient().getUrl(Uri.parse(url));
      final response = await request.close();
      if (response.statusCode < 200 || response.statusCode >= 300) return;
      final bytes = await consolidateHttpClientResponseBytes(response);
      if (bytes.isEmpty) return;
      final tmp = File('${file.path}.tmp');
      await tmp.writeAsBytes(bytes, flush: true);
      if (await file.exists()) await file.delete();
      await tmp.rename(file.path);
      if (mounted) setState(() => _file = file);
    } catch (_) {
      // Existing cached image remains visible; network failures are silent.
    } finally {
      _refreshing = false;
    }
  }

  Future<File> _cacheFile(String url) async {
    final directory = await getApplicationSupportDirectory();
    final hash = await Sha256().hash(utf8.encode(url));
    final key = base64UrlEncode(hash.bytes).replaceAll('=', '');
    return File('${directory.path}/chaty_remote_$key.img');
  }

  @override
  Widget build(BuildContext context) {
    final file = _file;
    if (file != null) {
      return Image.file(
        file,
        fit: widget.fit,
        gaplessPlayback: true,
        errorBuilder: (_, __, ___) => widget.fallback,
      );
    }
    return widget.fallback;
  }
}
