import 'dart:async';
import 'dart:io';
import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:path_provider/path_provider.dart';

import '../../ui/core/design_system/design_system.dart';

/// Interactive in-app image editor and cropper.
///
/// Supports interactive pan/zoom, 90-degree rotations, aspect ratio frames
/// (Square / 1:1 for Avatars, 16:9 for Banners), and renders the cropped
/// result directly to a local temporary file with zero third-party crop dependencies.
class ImageEditorCropperDialog extends StatefulWidget {
  final File sourceImageFile;
  final bool isAvatar; // true = 1:1 circular/square avatar, false = 16:9 banner
  final String title;

  const ImageEditorCropperDialog({
    super.key,
    required this.sourceImageFile,
    this.isAvatar = true,
    this.title = 'Edit & Crop Photo',
  });

  static Future<File?> open(
    BuildContext context, {
    required File imageFile,
    bool isAvatar = true,
    String title = 'Edit & Crop Photo',
  }) {
    return Navigator.of(context).push<File>(
      MaterialPageRoute(
        fullscreenDialog: true,
        builder: (_) => ImageEditorCropperDialog(
          sourceImageFile: imageFile,
          isAvatar: isAvatar,
          title: title,
        ),
      ),
    );
  }

  @override
  State<ImageEditorCropperDialog> createState() =>
      _ImageEditorCropperDialogState();
}

class _ImageEditorCropperDialogState extends State<ImageEditorCropperDialog> {
  final GlobalKey _cropKey = GlobalKey();
  final TransformationController _transformController =
      TransformationController();
  int _quarterRotations = 0;
  bool _isProcessing = false;
  Size? _sourceImageSize;

  @override
  void initState() {
    super.initState();
    unawaited(_loadSourceImageSize());
  }

  Future<void> _loadSourceImageSize() async {
    ui.Codec? codec;
    ui.FrameInfo? frame;
    try {
      final bytes = await widget.sourceImageFile.readAsBytes();
      codec = await ui.instantiateImageCodec(
        bytes,
        targetWidth: 1280,
        targetHeight: 1280,
      );
      frame = await codec.getNextFrame();
      final image = frame.image;
      final decodedSize = Size(
        image.width.toDouble(),
        image.height.toDouble(),
      );
      if (mounted) setState(() => _sourceImageSize = decodedSize);
    } catch (error) {
      debugPrint('Chaty crop source sizing skipped: $error');
    } finally {
      frame?.image.dispose();
      codec?.dispose();
    }
  }

  void _rotateClockwise() {
    setState(() {
      _quarterRotations = (_quarterRotations + 1) % 4;
    });
  }

  void _resetTransform() {
    setState(() {
      _transformController.value = Matrix4.identity();
      _quarterRotations = 0;
    });
  }

  Future<void> _captureAndSaveCrop() async {
    if (_isProcessing) return;
    setState(() => _isProcessing = true);

    try {
      final boundary =
          _cropKey.currentContext?.findRenderObject() as RenderRepaintBoundary?;
      if (boundary == null) {
        throw Exception('Crop boundary unavailable');
      }

      // High density snapshot (2.5x). Always release the rasterized image
      // after extracting bytes; long crop sessions can otherwise retain native
      // image memory, especially on older phones.
      final ui.Image image = await boundary.toImage(pixelRatio: 2.5);
      late final Uint8List buffer;
      try {
        final byteData = await image.toByteData(format: ui.ImageByteFormat.png);
        if (byteData == null) {
          throw Exception('Unable to encode cropped image bytes');
        }
        buffer = byteData.buffer.asUint8List();
      } finally {
        image.dispose();
      }

      if (buffer.isEmpty) {
        throw Exception('The crop is empty. Reposition the image inside the frame.');
      }
      final tempDir = await getTemporaryDirectory();
      final tag = widget.isAvatar ? 'avatar' : 'banner';
      final timestamp = DateTime.now().millisecondsSinceEpoch;
      final file = File('${tempDir.path}/cropped_${tag}_$timestamp.png');
      await file.writeAsBytes(buffer, flush: true);
      if (await file.length() == 0) {
        throw Exception('The crop could not be saved. Try again.');
      }

      if (!mounted) return;
      Navigator.of(context).pop(file);
    } catch (e) {
      if (mounted) {
        setState(() => _isProcessing = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to save cropped image: $e')),
        );
      }
    }
  }

  @override
  void dispose() {
    _transformController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final size = MediaQuery.of(context).size;
    final cropBoxWidth = size.width - 48;
    final cropBoxHeight = widget.isAvatar
        ? cropBoxWidth
        : (cropBoxWidth * 9 / 16);

    // Size the decoded source to cover the crop viewport without destroying
    // its aspect ratio. The content can extend beyond the crop window so users
    // can reposition a portrait or landscape image before saving.
    final sourceSize =
        _sourceImageSize ?? Size(cropBoxWidth, cropBoxHeight);
    final isRotated = _quarterRotations.isOdd;
    final rotatedSourceSize = isRotated
        ? Size(sourceSize.height, sourceSize.width)
        : sourceSize;
    final coverScale = math.max(
      cropBoxWidth / rotatedSourceSize.width,
      cropBoxHeight / rotatedSourceSize.height,
    ).toDouble();
    final rotatedDisplaySize = Size(
      rotatedSourceSize.width * coverScale,
      rotatedSourceSize.height * coverScale,
    );
    final childDisplaySize = isRotated
        ? Size(rotatedDisplaySize.height, rotatedDisplaySize.width)
        : rotatedDisplaySize;

    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.close_rounded, color: Colors.white),
          onPressed: () => Navigator.of(context).pop(null),
        ),
        title: Text(
          widget.title,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 18,
            fontWeight: FontWeight.w700,
          ),
        ),
        actions: [
          IconButton(
            tooltip: 'Reset',
            icon: const Icon(Icons.refresh_rounded, color: Colors.white70),
            onPressed: _resetTransform,
          ),
          IconButton(
            tooltip: 'Rotate 90°',
            icon: const Icon(Icons.rotate_right_rounded, color: Colors.white),
            onPressed: _rotateClockwise,
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            const SizedBox(height: 12),
            Text(
              widget.isAvatar
                  ? 'Pinch or drag to position inside the circular avatar'
                  : 'Pinch or drag to frame the banner',
              textAlign: TextAlign.center,
              style: const TextStyle(color: Colors.white70, fontSize: 13),
            ),
            const SizedBox(height: 16),
            Expanded(
              child: Center(
                child: Container(
                  width: cropBoxWidth,
                  height: cropBoxHeight,
                  decoration: BoxDecoration(
                    color: const Color(0xFF1E293B),
                    borderRadius: BorderRadius.circular(
                      widget.isAvatar ? cropBoxWidth / 2 : 16,
                    ),
                    border: Border.all(
                      color: colors.primary.withValues(alpha: 0.8),
                      width: 2.5,
                    ),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withValues(alpha: 0.6),
                        blurRadius: 24,
                        spreadRadius: 8,
                      ),
                    ],
                  ),
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(
                      widget.isAvatar ? cropBoxWidth / 2 : 14,
                    ),
                    child: RepaintBoundary(
                      key: _cropKey,
                      child: InteractiveViewer(
                        transformationController: _transformController,
                        // Keep the bitmap inside the crop window. Infinite
                        // margins allow panning the entire image out of frame,
                        // which can save a blank banner after editing.
                        clipBehavior: Clip.hardEdge,
                        constrained: false,
                        alignment: Alignment.center,
                        minScale: 1.0,
                        maxScale: 4.0,
                        boundaryMargin: EdgeInsets.zero,
                        child: RotatedBox(
                          quarterTurns: _quarterRotations,
                          child: SizedBox(
                            width: childDisplaySize.width,
                            height: childDisplaySize.height,
                            child: Image.file(
                              widget.sourceImageFile,
                              fit: BoxFit.fill,
                              width: childDisplaySize.width,
                              height: childDisplaySize.height,
                              filterQuality: FilterQuality.medium,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
            const SizedBox(height: 20),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
              child: Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      style: OutlinedButton.styleFrom(
                        foregroundColor: Colors.white70,
                        side: const BorderSide(color: Colors.white24),
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(14),
                        ),
                      ),
                      onPressed: () => Navigator.of(context).pop(null),
                      child: const Text(
                        'Cancel',
                        style: TextStyle(fontWeight: FontWeight.w600),
                      ),
                    ),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: ElevatedButton.icon(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: colors.primary,
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(14),
                        ),
                        elevation: 0,
                      ),
                      onPressed: _isProcessing ? null : _captureAndSaveCrop,
                      icon: _isProcessing
                          ? const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Colors.white,
                              ),
                            )
                          : const Icon(Icons.check_rounded, size: 20),
                      label: Text(
                        _isProcessing ? 'Cropping…' : 'Crop & Use',
                        style: const TextStyle(fontWeight: FontWeight.w700),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
