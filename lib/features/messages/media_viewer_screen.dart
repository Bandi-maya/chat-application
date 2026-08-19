import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../data/services/chat_media_service.dart';
import '../../../ui/core/theme/theme_config.dart';

class MediaViewerScreen extends StatefulWidget {
  final ThemeConfig theme;
  final String title;
  final String type;
  final String size;
  final String? storagePath;

  const MediaViewerScreen({
    super.key,
    required this.theme,
    required this.title,
    required this.type,
    required this.size,
    this.storagePath,
  });

  @override
  State<MediaViewerScreen> createState() => _MediaViewerScreenState();
}

class _MediaViewerScreenState extends State<MediaViewerScreen> {
  final ChatMediaService _mediaService = ChatMediaService();
  String? _signedUrl;
  String? _error;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final path = widget.storagePath;
    if (path == null || path.isEmpty) {
      if (mounted) {
        setState(() {
          _loading = false;
          _error = 'This attachment does not have a valid storage path.';
        });
      }
      return;
    }
    try {
      final url = await _mediaService.createSignedUrl(path);
      if (!mounted) return;
      setState(() {
        _signedUrl = url;
        _loading = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = error.toString().replaceFirst('Exception: ', '');
      });
    }
  }

  Future<void> _openExternally() async {
    final url = _signedUrl;
    if (url == null) return;
    final ok = await launchUrl(
      Uri.parse(url),
      mode: LaunchMode.externalApplication,
    );
    if (!ok && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('No application could open this attachment.')),
      );
    }
  }

  Future<void> _share() async {
    final url = _signedUrl;
    if (url == null) return;
    await SharePlus.instance.share(
      ShareParams(
        text: url,
        subject: widget.title,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black54,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              widget.title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 15, color: Colors.white),
            ),
            Text(
              widget.size,
              style: const TextStyle(fontSize: 11, color: Colors.white70),
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: 'Open / download',
            icon: const Icon(Icons.download_rounded, color: Colors.white),
            onPressed: _signedUrl == null ? null : _openExternally,
          ),
          IconButton(
            tooltip: 'Share temporary link',
            icon: const Icon(Icons.share_rounded, color: Colors.white),
            onPressed: _signedUrl == null ? null : _share,
          ),
        ],
      ),
      body: Center(
        child: _loading
            ? const CircularProgressIndicator(color: Colors.white)
            : _error != null
                ? Padding(
                    padding: const EdgeInsets.all(28),
                    child: Text(
                      _error!,
                      textAlign: TextAlign.center,
                      style: const TextStyle(color: Colors.white70),
                    ),
                  )
                : _buildContent(),
      ),
    );
  }

  Widget _buildContent() {
    final url = _signedUrl!;
    if (widget.type == 'image') {
      return InteractiveViewer(
        minScale: 0.7,
        maxScale: 5,
        child: Image.network(
          url,
          fit: BoxFit.contain,
          loadingBuilder: (context, child, progress) {
            if (progress == null) return child;
            return const Center(
              child: CircularProgressIndicator(color: Colors.white),
            );
          },
          errorBuilder: (_, __, ___) => _fallbackCard(
            Icons.broken_image_outlined,
            'Unable to display this image.',
          ),
        ),
      );
    }

    final icon = switch (widget.type) {
      'video' => Icons.play_circle_fill_rounded,
      'audio' => Icons.graphic_eq_rounded,
      'document' => Icons.description_rounded,
      _ => Icons.insert_drive_file_rounded,
    };
    return _fallbackCard(
      icon,
      'This private ${widget.type} is ready. Open it with an installed application.',
      action: _openExternally,
    );
  }

  Widget _fallbackCard(
    IconData icon,
    String text, {
    VoidCallback? action,
  }) {
    return Padding(
      padding: const EdgeInsets.all(28),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 86, color: Colors.white70),
          const SizedBox(height: 20),
          Text(
            text,
            textAlign: TextAlign.center,
            style: const TextStyle(color: Colors.white70, fontSize: 14),
          ),
          if (action != null) ...[
            const SizedBox(height: 20),
            FilledButton.icon(
              onPressed: action,
              icon: const Icon(Icons.open_in_new_rounded),
              label: const Text('Open securely'),
            ),
          ],
        ],
      ),
    );
  }
}
