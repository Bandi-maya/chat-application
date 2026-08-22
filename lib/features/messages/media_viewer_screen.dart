import 'dart:async';

import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:video_player/video_player.dart';

import '../../data/services/chat_media_service.dart';
import '../../ui/core/design_system/design_system.dart';

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
  VideoPlayerController? _videoController;

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
      final url = await _mediaService.createSignedUrl(
        path,
        expiresInSeconds: 3600,
      );
      VideoPlayerController? video;
      if (widget.type == 'video') {
        video = VideoPlayerController.networkUrl(Uri.parse(url));
        await video.initialize();
        await video.setLooping(false);
      }
      if (!mounted) {
        await video?.dispose();
        return;
      }
      setState(() {
        _signedUrl = url;
        _videoController = video;
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

  @override
  void dispose() {
    unawaited(_videoController?.dispose());
    super.dispose();
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
        const SnackBar(
          content: Text('No application could open this attachment.'),
        ),
      );
    }
  }

  Future<void> _share() async {
    final url = _signedUrl;
    if (url == null) return;
    await SharePlus.instance.share(
      ShareParams(text: url, subject: widget.title),
    );
  }

  Future<void> _toggleVideo() async {
    final controller = _videoController;
    if (controller == null) return;
    if (controller.value.isPlaying) {
      await controller.pause();
    } else {
      await controller.play();
    }
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;

    return Scaffold(
      backgroundColor: colors.surfaceElevated,
      appBar: AppBar(
        backgroundColor: colors.surfaceElevated.withValues(alpha: 0.85),
        foregroundColor: colors.foreground,
        elevation: 0,
        leading: const ChatyBackButton(),
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              widget.title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 15,
                color: colors.foreground,
                fontWeight: FontWeight.w600,
              ),
            ),
            Text(
              widget.size,
              style: TextStyle(
                fontSize: 11.5,
                color: colors.foregroundSecondary,
              ),
            ),
          ],
        ),
        actions: [
          ChatyIconButton(
            tooltip: 'Open with app',
            icon: Icons.open_in_new_rounded,
            color: colors.foreground,
            onPressed: _signedUrl == null ? null : _openExternally,
          ),
          ChatyIconButton(
            tooltip: 'Share file link',
            icon: Icons.share_rounded,
            color: colors.foreground,
            onPressed: _signedUrl == null ? null : _share,
          ),
          const SizedBox(width: ChatySpacing.xs),
        ],
      ),
      body: Center(
        child: _loading
            ? CircularProgressIndicator(strokeWidth: 2.2, color: colors.primary)
            : _error != null
            ? Padding(
                padding: const EdgeInsets.all(ChatySpacing.lg),
                child: Text(
                  _error!,
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    color: colors.foregroundSecondary,
                    fontSize: 14,
                  ),
                ),
              )
            : _buildContent(colors),
      ),
    );
  }

  Widget _buildContent(AppColors colors) {
    final url = _signedUrl!;
    if (widget.type == 'image') {
      return InteractiveViewer(
        minScale: 0.7,
        maxScale: 5,
        child: Image.network(
          url,
          fit: BoxFit.contain,
          loadingBuilder: (context, child, progress) => progress == null
              ? child
              : Center(
                  child: CircularProgressIndicator(
                    strokeWidth: 2.2,
                    color: colors.primary,
                  ),
                ),
          errorBuilder: (_, __, ___) => _fallbackCard(
            Icons.broken_image_outlined,
            'Unable to display this image.',
            colors: colors,
          ),
        ),
      );
    }
    if (widget.type == 'video') {
      final controller = _videoController;
      if (controller == null || !controller.value.isInitialized) {
        return _fallbackCard(
          Icons.videocam_off_outlined,
          'Unable to initialize this video.',
          action: _openExternally,
          colors: colors,
        );
      }
      return SafeArea(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Expanded(
              child: Center(
                child: AspectRatio(
                  aspectRatio: controller.value.aspectRatio == 0
                      ? 16 / 9
                      : controller.value.aspectRatio,
                  child: GestureDetector(
                    onTap: _toggleVideo,
                    child: Stack(
                      fit: StackFit.expand,
                      children: [
                        VideoPlayer(controller),
                        Center(
                          child: AnimatedOpacity(
                            opacity: controller.value.isPlaying ? 0 : 1,
                            duration: const Duration(milliseconds: 160),
                            child: Container(
                              padding: const EdgeInsets.all(12),
                              decoration: BoxDecoration(
                                color: colors.surface.withValues(alpha: 0.7),
                                shape: BoxShape.circle,
                              ),
                              child: Icon(
                                Icons.play_arrow_rounded,
                                color: colors.foreground,
                                size: 54,
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(18, 10, 18, 18),
              child: Row(
                children: [
                  ChatyIconButton(
                    onPressed: _toggleVideo,
                    icon: controller.value.isPlaying
                        ? Icons.pause_rounded
                        : Icons.play_arrow_rounded,
                    color: colors.foreground,
                  ),
                  const SizedBox(width: ChatySpacing.sm),
                  Expanded(
                    child: VideoProgressIndicator(
                      controller,
                      allowScrubbing: true,
                      colors: VideoProgressColors(
                        playedColor: colors.primary,
                        bufferedColor: colors.primary.withValues(alpha: 0.3),
                        backgroundColor: colors.borderSubtle,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      );
    }

    final icon = switch (widget.type) {
      'audio' => Icons.graphic_eq_rounded,
      'document' => Icons.description_rounded,
      _ => Icons.insert_drive_file_rounded,
    };
    return _fallbackCard(
      icon,
      'This private ${widget.type} is ready to open securely.',
      action: _openExternally,
      colors: colors,
    );
  }

  Widget _fallbackCard(
    IconData icon,
    String text, {
    VoidCallback? action,
    required AppColors colors,
  }) {
    return Padding(
      padding: const EdgeInsets.all(ChatySpacing.xl),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 76, color: colors.foregroundSecondary),
          const SizedBox(height: ChatySpacing.lg),
          Text(
            text,
            textAlign: TextAlign.center,
            style: TextStyle(color: colors.foregroundSecondary, fontSize: 14.5),
          ),
          if (action != null) ...[
            const SizedBox(height: ChatySpacing.lg),
            ChatyPrimaryButton(
              text: 'Open Securely',
              width: 180,
              icon: Icons.open_in_new_rounded,
              onPressed: action,
            ),
          ],
        ],
      ),
    );
  }
}
