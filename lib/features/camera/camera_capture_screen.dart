import 'dart:async';
import 'dart:io';
import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:camera/camera.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_image_compress/flutter_image_compress.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';

import '../../ui/core/design_system/chaty_haptics.dart';
import 'effects/effect_engine.dart';
import 'effects/effect_model.dart';
import 'effects/effect_registry.dart';
import 'lens/camera_face_detection_bridge.dart';
import 'lens/face_tracker.dart';
import 'lens/lens_carousel.dart';
import 'lens/lens_definition.dart';
import 'lens/lens_manager.dart';
import 'lens/lens_overlay.dart';

/// What the caller wants to do with the capture. Drives the confirm-pane
/// caption copy and whether the chosen look is returned as metadata (chat)
/// or baked into the pixels (stories, which carry no metadata column).
enum ChatyCaptureMode { chat, story }

enum _CaptureViewMode { photo, document }

const ChatyCameraEffect _documentScanEffect = ChatyCameraEffect(
  id: 'document_scan',
  name: 'Document Scan',
  category: EffectCategory.color,
  icon: Icons.document_scanner_rounded,
  previewColor: Color(0xFFCBD5E1),
  colorMatrix: [
    0.48, 0.94, 0.08, 0.0, -45.0,
    0.48, 0.94, 0.08, 0.0, -45.0,
    0.48, 0.94, 0.08, 0.0, -45.0,
    0.0, 0.0, 0.0, 1.0, 0.0,
  ],
);

class ChatyCaptureResult {
  const ChatyCaptureResult({
    required this.path,
    required this.effectId,
    required this.effectIntensity,
    this.caption = '',
  });

  final String path;
  final String effectId;
  final double effectIntensity;

  /// Optional caption typed on the confirm pane.
  final String caption;
}

/// Full-screen camera capture, WhatsApp-style: live filtered viewfinder,
/// shutter / flip / flash / gallery, an effects tray backed by the shared
/// [EffectRegistry], and a confirm pane with caption before anything sends.
///
/// Effects are honest end-to-end: in chat mode the look travels as message
/// metadata and receivers render it too ([EffectRegistry.applyStored]); in
/// story mode it is baked into the published image because statuses have no
/// metadata channel.
class ChatyCameraCaptureScreen extends StatefulWidget {
  const ChatyCameraCaptureScreen({
    super.key,
    required this.mode,
    this.contactName,
  });

  final ChatyCaptureMode mode;

  /// Shown in the confirm pane hint for chat mode.
  final String? contactName;

  static Future<ChatyCaptureResult?> open(
    BuildContext context, {
    required ChatyCaptureMode mode,
    String? contactName,
  }) {
    return Navigator.of(context, rootNavigator: true).push(
      MaterialPageRoute<ChatyCaptureResult>(
        fullscreenDialog: true,
        builder: (_) =>
            ChatyCameraCaptureScreen(mode: mode, contactName: contactName),
      ),
    );
  }

  @override
  State<ChatyCameraCaptureScreen> createState() =>
      _ChatyCameraCaptureScreenState();
}

class _ChatyCameraCaptureScreenState extends State<ChatyCameraCaptureScreen>
    with WidgetsBindingObserver {
  final EffectEngine _engine = EffectEngine();
  final LensManager _lensManager = LensManager();
  final FaceAnchorSolver _faceSolver = FaceAnchorSolver();
  final CameraFaceDetectionBridge _faceDetection = CameraFaceDetectionBridge();
  final ValueNotifier<List<FacePose>> _facePoses =
      ValueNotifier<List<FacePose>>(const <FacePose>[]);

  CameraController? _controller;
  _CameraPhase _phase = _CameraPhase.initializing;
  String? _errorMessage;
  bool _flashOn = false;
  bool _effectsOpen = false;
  bool _capturing = false;
  _CaptureViewMode _captureViewMode = _CaptureViewMode.photo;
  bool _processingFaceFrame = false;
  bool _faceStreamSyncing = false;
  bool _faceStreamSyncRequested = false;
  int _lastFaceFrameMs = 0;
  Size _previewViewportSize = Size.zero;

  bool get _wantsFaceTracking =>
      _captureViewMode == _CaptureViewMode.photo &&
      _lensManager.selected.requiresFaceTracking;

  bool _switchingCamera = false;

  XFile? _capturedFile;
  final TextEditingController _captionCtrl = TextEditingController();
  final FocusNode _captionFocus = FocusNode();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _lensManager.addListener(_onLensManagerChanged);
    unawaited(_bootstrap());
  }

  Future<void> _bootstrap() async {
    if (!await Permission.camera.request().isGranted) {
      if (!mounted) return;
      setState(() => _phase = _CameraPhase.permissionDenied);
      return;
    }
    List<CameraDescription> cameras;
    try {
      cameras = await availableCameras();
    } catch (error) {
      // CameraException, or platforms with no camera integration at all
      // (desktop builds): degrade to gallery sharing instead of crashing.
      if (!mounted) return;
      setState(() => _phase = _CameraPhase.noCamera);
      return;
    }
    if (!mounted) return;
    if (cameras.isEmpty) {
      setState(() => _phase = _CameraPhase.noCamera);
      return;
    }
    await _startController(
      cameras.firstWhere(
        (camera) => camera.lensDirection == CameraLensDirection.back,
        orElse: () => cameras.first,
      ),
    );
  }

  Future<void> _startController(CameraDescription description) async {
    if (_controller != null) {
      setState(() => _phase = _CameraPhase.initializing);
    }
    final previous = _controller;
    _controller = null;
    if (previous != null) {
      try {
        await previous.dispose();
      } catch (_) {}
    }

    final controller = CameraController(
      description,
      ResolutionPreset.high,
      enableAudio: false,
      imageFormatGroup: ImageFormatGroup.yuv420,
    );
    try {
      await controller.initialize();
      if (!mounted) {
        await controller.dispose();
        return;
      }
      _controller = controller;
      if (_flashOn && description.lensDirection == CameraLensDirection.back) {
        try {
          await controller.setFlashMode(FlashMode.torch);
        } catch (_) {
          if (mounted) setState(() => _flashOn = false);
        }
      } else if (_flashOn) {
        if (mounted) setState(() => _flashOn = false);
      }
      setState(() => _phase = _CameraPhase.ready);
      unawaited(_syncFaceDetectionStream());
    } on CameraException catch (error) {
      await controller.dispose();
      if (!mounted) return;
      setState(() {
        _errorMessage = error.description ?? 'The camera could not start.';
        _phase = _CameraPhase.noCamera;
      });
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    final controller = _controller;
    if (controller == null || !controller.value.isInitialized) return;
    if (state == AppLifecycleState.inactive ||
        state == AppLifecycleState.paused) {
      unawaited(controller.dispose());
      _controller = null;
      _faceSolver.reset();
      _facePoses.value = const <FacePose>[];
      if (mounted && _capturedFile == null) {
        setState(() => _phase = _CameraPhase.initializing);
        unawaited(_bootstrap());
      }
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _captionCtrl.dispose();
    _captionFocus.dispose();
    _lensManager.removeListener(_onLensManagerChanged);
    _lensManager.dispose();
    _facePoses.dispose();
    unawaited(_controller?.dispose());
    _engine.dispose();
    super.dispose();
  }


  void _onLensManagerChanged() {
    if (mounted) setState(() {});
    if (!_wantsFaceTracking) {
      _faceSolver.reset();
      _facePoses.value = const <FacePose>[];
    }
    unawaited(_syncFaceDetectionStream());
  }

  Future<void> _syncFaceDetectionStream() async {
    if (_faceStreamSyncing) {
      _faceStreamSyncRequested = true;
      return;
    }
    _faceStreamSyncing = true;
    try {
      do {
        _faceStreamSyncRequested = false;
        final controller = _controller;
        if (controller == null || !controller.value.isInitialized) break;
        try {
          if (_wantsFaceTracking) {
            if (!controller.value.isStreamingImages) {
              await controller.startImageStream((image) {
                unawaited(_processFaceFrame(image, controller));
              });
            }
          } else {
            if (controller.value.isStreamingImages) {
              await controller.stopImageStream();
            }
            _faceSolver.reset();
            _facePoses.value = const <FacePose>[];
          }
        } on CameraException {
          // Keep photo capture available when image streaming is unsupported.
          _faceSolver.reset();
          _facePoses.value = const <FacePose>[];
        }
      } while (_faceStreamSyncRequested);
    } finally {
      _faceStreamSyncing = false;
      if (_faceStreamSyncRequested) unawaited(_syncFaceDetectionStream());
    }
  }

  Future<void> _processFaceFrame(
    CameraImage image,
    CameraController sourceController,
  ) async {
    if (!_wantsFaceTracking || _processingFaceFrame) return;
    final nowMs = DateTime.now().millisecondsSinceEpoch;
    if (nowMs - _lastFaceFrameMs < 90) return;
    _lastFaceFrameMs = nowMs;
    _processingFaceFrame = true;
    try {
      final detection = await _faceDetection.detect(
        image,
        sensorOrientation: sourceController.description.sensorOrientation,
      );
      if (!mounted ||
          !identical(sourceController, _controller) ||
          !_wantsFaceTracking) {
        return;
      }

      final previewSize = _previewViewportSize == Size.zero
          ? MediaQuery.sizeOf(context)
          : _previewViewportSize;
      final imageSize = Size(
        detection.imageWidth.toDouble(),
        detection.imageHeight.toDouble(),
      );
      final scale = math.max(
        previewSize.width / imageSize.width,
        previewSize.height / imageSize.height,
      );
      final scaledWidth = imageSize.width * scale;
      final scaledHeight = imageSize.height * scale;
      final crop = Rect.fromLTWH(
        (previewSize.width - scaledWidth) / 2,
        (previewSize.height - scaledHeight) / 2,
        scaledWidth,
        scaledHeight,
      );
      final poses = _faceSolver.update(
        nowMs: nowMs,
        rawFaces: detection.faces,
        mirrored:
            sourceController.description.lensDirection ==
            CameraLensDirection.front,
        imageExtent: imageSize,
        previewCrop: crop,
        confidenceScale: 1,
      );
      _facePoses.value = poses;
    } catch (_) {
      // Detection is best-effort. Never interrupt capture when unsupported.
    } finally {
      _processingFaceFrame = false;
    }
  }

  void _setCaptureViewMode(_CaptureViewMode mode) {
    if (_captureViewMode == mode) return;
    ChatyHaptics.selection();
    setState(() {
      _captureViewMode = mode;
      _effectsOpen = false;
    });
    unawaited(_syncFaceDetectionStream());
  }

  Future<void> _toggleFlash() async {
    ChatyHaptics.selection();
    final next = !_flashOn;
    try {
      await _controller?.setFlashMode(next ? FlashMode.torch : FlashMode.off);
      if (mounted) setState(() => _flashOn = next);
    } on CameraException {
      if (mounted) setState(() => _flashOn = false);
    }
  }

  Future<void> _flipCamera() async {
    if (_switchingCamera || _phase == _CameraPhase.initializing) return;
    _switchingCamera = true;
    ChatyHaptics.selection();
    try {
      final List<CameraDescription> cameras;
      try {
        cameras = await availableCameras();
      } catch (_) {
        return;
      }
      if (cameras.length < 2) return;
      final current = _controller?.description;
      final next = cameras.firstWhere(
        (camera) =>
            current == null || camera.lensDirection != current.lensDirection,
        orElse: () => cameras.first,
      );
      await _startController(next);
    } finally {
      _switchingCamera = false;
    }
  }

  Future<void> _pickFromGallery() async {
    ChatyHaptics.light();
    final files = await FilePicker.pickFiles(type: FileType.image);
    final path = files.isEmpty ? null : files.single.path;
    if (path == null || path.isEmpty) return;
    if (!mounted) return;
    var selectedPath = path;
    if (_captureViewMode == _CaptureViewMode.document) {
      try {
        selectedPath = await bakeEffectIntoFile(path, _documentScanEffect, 1);
      } catch (_) {
        // Keep the selected original image available if processing fails.
      }
    }
    if (!mounted) return;
    setState(() {
      _capturedFile = XFile(selectedPath);
      _effectsOpen = false;
    });
  }

  Future<void> _shutter() async {
    final controller = _controller;
    if (controller == null || !controller.value.isInitialized || _capturing) {
      return;
    }
    setState(() => _capturing = true);
    ChatyHaptics.light();
    try {
      final file = await controller.takePicture();
      var capturedPath = file.path;
      if (_captureViewMode == _CaptureViewMode.document) {
        try {
          capturedPath = await bakeEffectIntoFile(
            capturedPath,
            _documentScanEffect,
            1,
          );
        } catch (_) {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(
                content: Text('Could not enhance this scan; the original photo is kept.'),
              ),
            );
          }
        }
      } else {
        final lens = _lensManager.selected;
        final poses = _facePoses.value;
        if (!lens.isNoLens &&
            poses.isNotEmpty &&
            _faceSolver.health != TrackingHealth.lost) {
          try {
            final bytes = await composeLensIntoImage(
              sourceBytes: await File(capturedPath).readAsBytes(),
              previewSize: _previewViewportSize == Size.zero
                  ? MediaQuery.sizeOf(context)
                  : _previewViewportSize,
              lens: lens,
              poses: poses,
            );
            final temporary = await getTemporaryDirectory();
            final stamp = DateTime.now().microsecondsSinceEpoch;
            final staged = File('${temporary.path}/chaty_lens_$stamp.png');
            await staged.writeAsBytes(bytes, flush: true);
            final compressed = await FlutterImageCompress.compressAndGetFile(
              staged.absolute.path,
              '${temporary.path}/chaty_lens_$stamp.jpg',
              quality: 92,
            );
            if (compressed != null) {
              capturedPath = compressed.path;
              unawaited(staged.delete().catchError((_) => staged));
            } else {
              capturedPath = staged.path;
            }
          } catch (_) {
            if (mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                  content: Text('The face lens could not be baked into this capture. The photo is kept.'),
                ),
              );
            }
          }
        }
      }
      if (!mounted) return;
      setState(() => _capturedFile = XFile(capturedPath));
    } on CameraException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Capture failed: ${error.description ?? 'try again'}'),
        ),
      );
    } finally {
      if (mounted) setState(() => _capturing = false);
    }
  }

  void _discard() {
    ChatyHaptics.light();
    setState(() => _capturedFile = null);
  }

  Future<void> _confirmAndSend() async {
    ChatyHaptics.success();
    final raw = _capturedFile;
    if (raw == null) return;

    final engineEffect = _engine.activeEffect;
    var path = raw.path;
    var effectId = _captureViewMode == _CaptureViewMode.document
        ? 'none'
        : engineEffect.id;
    var intensity = _captureViewMode == _CaptureViewMode.document
        ? 1.0
        : _engine.intensity;

    // Stories carry no metadata channel, so the look must live in the pixels.
    if (widget.mode == ChatyCaptureMode.story &&
        _captureViewMode != _CaptureViewMode.document &&
        effectId != 'none' &&
        engineEffect.colorMatrix != null) {
      try {
        final baked = await bakeEffectIntoFile(path, engineEffect, intensity);
        path = baked;
      } catch (_) {
        // Fall back to publishing the untouched capture rather than failing.
        effectId = 'none';
      }
    }

    if (!mounted) return;
    Navigator.of(context).pop(
      ChatyCaptureResult(
        path: path,
        effectId: effectId,
        effectIntensity: intensity,
        caption: _captionCtrl.text.trim(),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: _capturedFile == null,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && _capturedFile != null) _discard();
      },
      child: Scaffold(
        backgroundColor: Colors.black,
        body: SafeArea(
          top: false,
          child: _capturedFile != null ? _buildConfirmPane() : _buildCamera(),
        ),
      ),
    );
  }

  Widget _buildCamera() {
    return Stack(
      fit: StackFit.expand,
      children: [
        _buildViewfinder(),
        Positioned(left: 0, right: 0, bottom: 0, child: _buildBottomControls()),
      ],
    );
  }

  Widget _buildBottomControls() {
    final ready = _phase == _CameraPhase.ready;
    final screen = MediaQuery.sizeOf(context);
    final tight = screen.shortestSide < 340 || screen.height < 680;
    final allEffects = EffectRegistry.allEffects
        .where((effect) => !effect.isFaceTrackingRequired)
        .toList(growable: false);

    return Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Colors.transparent, Colors.black87, Colors.black],
        ),
      ),
      padding: EdgeInsets.only(
        bottom: MediaQuery.paddingOf(context).bottom + (tight ? 6 : 12),
        top: tight ? 8 : 14,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (ready && _captureViewMode == _CaptureViewMode.photo) ...[
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Flexible(
                    child: Text(
                      _lensManager.selected.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 13,
                        fontWeight: FontWeight.w700,
                        letterSpacing: .15,
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Container(
                    width: 5,
                    height: 5,
                    decoration: BoxDecoration(
                      color: _lensManager.selected.requiresFaceTracking &&
                              _faceSolver.health == TrackingHealth.tracked
                          ? const Color(0xFF7CFAAE)
                          : Colors.white38,
                      shape: BoxShape.circle,
                    ),
                  ),
                  const SizedBox(width: 6),
                  Flexible(
                    child: Text(
                      !_lensManager.selected.requiresFaceTracking
                          ? 'Swipe to explore'
                          : switch (_faceSolver.health) {
                              TrackingHealth.tracked => 'Face locked',
                              TrackingHealth.temporarilyLost => 'Reacquiring…',
                              TrackingHealth.lost => 'Find a face',
                            },
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: Colors.white70,
                        fontSize: 11,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            SizedBox(height: tight ? 1 : 4),
            LensCarousel(manager: _lensManager),
          ],
          if (ready && _captureViewMode == _CaptureViewMode.document) ...[
            const Padding(
              padding: EdgeInsets.fromLTRB(20, 0, 20, 7),
              child: Text(
                'DOCUMENT  •  Fit the page inside the frame',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 11,
                  letterSpacing: 1.0,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          ],
          if (ready && _effectsOpen && _captureViewMode == _CaptureViewMode.photo) ...[
            SizedBox(
              height: tight ? 66 : 74,
              child: ListView.builder(
                scrollDirection: Axis.horizontal,
                physics: const BouncingScrollPhysics(),
                padding: EdgeInsets.symmetric(
                  horizontal: math.max(8.0, (screen.width - 52) / 2),
                ),
                itemCount: allEffects.length,
                itemBuilder: (context, index) {
                  final effect = allEffects[index];
                  final isSelected = _engine.activeEffect.id == effect.id;
                  return Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 5),
                    child: GestureDetector(
                      onTap: () {
                        ChatyHaptics.selection();
                        setState(() => _engine.selectEffect(effect));
                      },
                      child: AnimatedScale(
                        scale: isSelected ? 1.06 : 0.88,
                        duration: MediaQuery.disableAnimationsOf(context)
                            ? Duration.zero
                            : const Duration(milliseconds: 180),
                        curve: Curves.easeOutCubic,
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Container(
                              width: tight ? 42 : 48,
                              height: tight ? 42 : 48,
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                color: effect.previewColor.withValues(
                                  alpha: isSelected ? 0.95 : 0.4,
                                ),
                                border: Border.all(
                                  color: isSelected ? Colors.white : Colors.white30,
                                  width: isSelected ? 2.5 : 1.2,
                                ),
                              ),
                              child: CustomPaint(
                                size: Size.square(tight ? 20 : 22),
                                painter: _EffectGlyphPainter(
                                  id: effect.id,
                                  category: effect.category,
                                  color: isSelected ? Colors.black : Colors.white,
                                ),
                              ),
                            ),
                            const SizedBox(height: 3),
                            SizedBox(
                              width: tight ? 56 : 64,
                              child: Text(
                                effect.name,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                textAlign: TextAlign.center,
                                style: TextStyle(
                                  color: isSelected ? Colors.white : Colors.white60,
                                  fontSize: 10,
                                  fontWeight: isSelected
                                      ? FontWeight.w700
                                      : FontWeight.w500,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  );
                },
              ),
            ),
            SizedBox(height: tight ? 4 : 9),
          ],
          _buildModeSelector(tight: tight),
          SizedBox(height: tight ? 4 : 8),
          Padding(
            padding: EdgeInsets.symmetric(horizontal: tight ? 16 : 24),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Semantics(
                  button: true,
                  label: 'Pick image from gallery',
                  child: InkWell(
                    borderRadius: BorderRadius.circular(26),
                    onTap: _pickFromGallery,
                    child: SizedBox.square(
                      dimension: tight ? 46 : 52,
                      child: const Center(
                        child: _CameraGlyph(
                          kind: 'gallery',
                          color: Colors.white,
                          size: 27,
                        ),
                      ),
                    ),
                  ),
                ),
                _buildShutter(enabled: ready),
                Semantics(
                  button: true,
                  label: 'Switch camera',
                  child: InkWell(
                    borderRadius: BorderRadius.circular(26),
                    onTap: ready ? _flipCamera : null,
                    child: SizedBox.square(
                      dimension: tight ? 46 : 52,
                      child: _CameraGlyph(
                        kind: 'flip',
                        color: ready ? Colors.white : Colors.white30,
                        size: 27,
                      ),
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

  Widget _buildModeSelector({required bool tight}) {
    Widget modeButton({
      required _CaptureViewMode mode,
      required String label,
      required bool document,
    }) {
      final selected = _captureViewMode == mode;
      return Expanded(
        child: Semantics(
          button: true,
          selected: selected,
          label: '$label camera mode',
          child: GestureDetector(
            onTap: () => _setCaptureViewMode(mode),
            child: AnimatedContainer(
              duration: MediaQuery.disableAnimationsOf(context)
                  ? Duration.zero
                  : const Duration(milliseconds: 180),
              curve: Curves.easeOutCubic,
              height: tight ? 34 : 38,
              padding: EdgeInsets.symmetric(horizontal: tight ? 9 : 14),
              decoration: BoxDecoration(
                color: selected ? Colors.white : Colors.white.withValues(alpha: .08),
                borderRadius: BorderRadius.circular(22),
                border: Border.all(
                  color: selected ? Colors.white : Colors.white30,
                ),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  _CameraGlyph(
                    kind: document ? 'document' : 'camera',
                    color: selected ? Colors.black : Colors.white,
                    size: 16,
                  ),
                  const SizedBox(width: 6),
                  Text(
                    label,
                    style: TextStyle(
                      color: selected ? Colors.black : Colors.white,
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      );
    }

    return Padding(
      padding: EdgeInsets.symmetric(horizontal: tight ? 30 : 62),
      child: Row(
        children: [
          modeButton(
            mode: _CaptureViewMode.photo,
            label: 'Photo',
            document: false,
          ),
          const SizedBox(width: 8),
          modeButton(
            mode: _CaptureViewMode.document,
            label: 'Document',
            document: true,
          ),
        ],
      ),
    );
  }

  Widget _buildShutter({required bool enabled}) {
    return Semantics(
      button: true,
      enabled: enabled,
      label: 'Take photo',
      child: GestureDetector(
        onTap: enabled ? _shutter : null,
        child: Container(
          width: 76,
          height: 76,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            border: Border.all(color: Colors.white, width: 4),
          ),
          padding: const EdgeInsets.all(5),
          child: _capturing
              ? const Padding(
                  padding: EdgeInsets.all(16),
                  child: CircularProgressIndicator(
                    color: Colors.white,
                    strokeWidth: 3,
                  ),
                )
              : DecoratedBox(
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: enabled ? Colors.white : Colors.white38,
                  ),
                ),
        ),
      ),
    );
  }

  Widget _buildViewfinder() {
    final controller = _controller;
    return Stack(
      fit: StackFit.expand,
      children: [
        switch (_phase) {
          _CameraPhase.initializing => const Center(
            child: CircularProgressIndicator(color: Colors.white70),
          ),
          _CameraPhase.permissionDenied => _buildNoCameraPane(
            icon: Icons.no_photography_outlined,
            title: 'Camera permission needed',
            message:
                'Allow Chaty to use the camera to take photos and share them instantly.',
            actionLabel: 'Open settings',
            onAction: () => unawaited(openAppSettings()),
          ),
          _CameraPhase.noCamera => _buildNoCameraPane(
            icon: Icons.videocam_off_rounded,
            title: 'Camera unavailable',
            message:
                _errorMessage ??
                'No usable camera was found on this device. You can still share from your gallery.',
            actionLabel: 'Pick from gallery',
            onAction: _pickFromGallery,
          ),
          _CameraPhase.ready => ClipRect(
            child: OverflowBox(
              alignment: Alignment.center,
              maxWidth: double.infinity,
              child: FittedBox(
                fit: BoxFit.cover,
                child: SizedBox(
                  width: controller!.value.previewSize!.height,
                  height: controller.value.previewSize!.width,
                  child: _buildCameraPreview(controller),
                ),
              ),
            ),
          ),
        },
        if (_phase == _CameraPhase.ready &&
            _captureViewMode == _CaptureViewMode.photo)
          ValueListenableBuilder<List<FacePose>>(
            valueListenable: _facePoses,
            builder: (context, poses, _) => LensOverlay(
              lens: _lensManager.selected,
              poses: poses,
              health: _faceSolver.health,
            ),
          ),
        if (_phase == _CameraPhase.ready &&
            _captureViewMode == _CaptureViewMode.document)
          const Positioned.fill(
            child: IgnorePointer(
              child: CustomPaint(painter: _DocumentGuidePainter()),
            ),
          ),
        _buildTopBar(),
      ],
    );
  }

  Widget _buildCameraPreview(CameraController controller) {
    final camera = CameraPreview(controller);
    if (_captureViewMode == _CaptureViewMode.document) {
      return KeyedSubtree(
        key: const ValueKey<String>('document-preview'),
        child: ColorFiltered(
          colorFilter: ColorFilter.matrix(_documentScanEffect.colorMatrix!),
          child: camera,
        ),
      );
    }
    return KeyedSubtree(
      key: ValueKey<String>('photo-preview:${_engine.activeEffect.id}'),
      child: _engine.renderEffect(child: camera),
    );
  }

  Widget _buildTopBar() {
    return Positioned(
      top: MediaQuery.paddingOf(context).top + 8,
      left: 8,
      right: 8,
      child: Row(
        children: [
          _RoundIconAction(
            semanticsLabel: 'Close camera',
            kind: 'close',
            onPressed: () => Navigator.of(context).maybePop(),
          ),
          const Spacer(),
          if (_phase == _CameraPhase.ready) ...[
            _RoundIconAction(
              semanticsLabel: _flashOn ? 'Turn flash off' : 'Turn flash on',
              kind: 'flash',
              onPressed: _toggleFlash,
            ),
            const SizedBox(width: 10),
            if (_captureViewMode == _CaptureViewMode.photo)
              _RoundIconAction(
                semanticsLabel: _effectsOpen ? 'Hide color filters' : 'Show color filters',
                kind: 'filters',
                highlighted: _effectsOpen || _engine.activeEffect.id != 'none',
                onPressed: () {
                  ChatyHaptics.selection();
                  setState(() => _effectsOpen = !_effectsOpen);
                },
              ),
          ],
        ],
      ),
    );
  }

  // --- Confirm pane ---------------------------------------------------------

  Widget _buildConfirmPane() {
    final isChat = widget.mode == ChatyCaptureMode.chat;
    return Column(
      children: [
        Expanded(
          child: InteractiveViewer(
            maxScale: 4,
            child: Center(
              // The stored look is applied here exactly like receivers will
              // render it, so confirmation never misleads.
              child: EffectRegistry.applyStored(
                Image.file(File(_capturedFile!.path), fit: BoxFit.contain),
                <String, dynamic>{
                  'effect': _engine.activeEffect.id,
                  'effect_intensity': _engine.intensity.toString(),
                },
              ),
            ),
          ),
        ),
        Container(
          color: Colors.black,
          padding: EdgeInsets.fromLTRB(
            16,
            12,
            16,
            MediaQuery.paddingOf(context).bottom + 14,
          ),
          child: Row(
            children: [
              Semantics(
                button: true,
                label: 'Discard photo',
                child: InkWell(
                  customBorder: const CircleBorder(),
                  onTap: _discard,
                  child: const SizedBox.square(
                    dimension: 48,
                    child: Icon(
                      Icons.delete_outline_rounded,
                      color: Colors.white,
                      size: 26,
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: TextField(
                  controller: _captionCtrl,
                  focusNode: _captionFocus,
                  maxLines: 2,
                  minLines: 1,
                  style: const TextStyle(color: Colors.white),
                  decoration: InputDecoration(
                    hintText: isChat && widget.contactName != null
                        ? 'Message ${widget.contactName}…'
                        : 'Add a caption…',
                    hintStyle: const TextStyle(color: Colors.white54),
                    filled: true,
                    fillColor: Colors.white12,
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 10,
                    ),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(26),
                      borderSide: BorderSide.none,
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Semantics(
                button: true,
                label: isChat ? 'Send photo' : 'Post to my status',
                child: InkWell(
                  customBorder: const CircleBorder(),
                  onTap: _confirmAndSend,
                  child: CircleAvatar(
                    radius: 25,
                    backgroundColor: Theme.of(context).colorScheme.primary,
                    child: Icon(
                      isChat ? Icons.send_rounded : Icons.check_rounded,
                      color: Theme.of(context).colorScheme.onPrimary,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildNoCameraPane({
    required IconData icon,
    required String title,
    required String message,
    required String actionLabel,
    required VoidCallback onAction,
  }) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 46, color: Colors.white54),
            const SizedBox(height: 14),
            Text(
              title,
              textAlign: TextAlign.center,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 17,
                fontWeight: FontWeight.w800,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(color: Colors.white60, height: 1.35),
            ),
            const SizedBox(height: 20),
            OutlinedButton.icon(
              onPressed: onAction,
              style: OutlinedButton.styleFrom(foregroundColor: Colors.white),
              icon: const Icon(Icons.settings_rounded, size: 18),
              label: Text(actionLabel),
            ),
          ],
        ),
      ),
    );
  }
}

enum _CameraPhase { initializing, ready, permissionDenied, noCamera }

class _RoundIconAction extends StatelessWidget {
  const _RoundIconAction({
    required this.semanticsLabel,
    required this.kind,
    required this.onPressed,
    this.highlighted = false,
  });

  final String semanticsLabel;
  final String kind;
  final VoidCallback onPressed;
  final bool highlighted;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      label: semanticsLabel,
      child: InkWell(
        customBorder: const CircleBorder(),
        onTap: onPressed,
        child: CircleAvatar(
          radius: 21,
          backgroundColor: highlighted
              ? Colors.white.withValues(alpha: .28)
              : Colors.white24,
          child: _CameraGlyph(kind: kind, color: Colors.white, size: 21),
        ),
      ),
    );
  }
}

/// Composites the color matrix of [effect] over the image at [path] and
/// writes a compressed copy, returning the new path. Used where no metadata
/// channel exists (status updates).
Future<String> bakeEffectIntoFile(
  String path,
  ChatyCameraEffect effect,
  double intensity,
) async {
  final matrix = <double>[
    for (var i = 0; i < 20; i++)
      _identity[i] + ((effect.colorMatrix![i]) - _identity[i]) * intensity,
  ];

  final bytes = await File(path).readAsBytes();
  final codec = await ui.instantiateImageCodec(bytes, targetWidth: 1440);
  final frame = await codec.getNextFrame();
  final image = frame.image;

  final recorder = ui.PictureRecorder();
  final canvas = ui.Canvas(recorder);
  canvas.drawImage(
    image,
    ui.Offset.zero,
    Paint()..colorFilter = ColorFilter.matrix(matrix),
  );
  final rendered = await recorder.endRecording().toImage(
    image.width,
    image.height,
  );
  image.dispose();

  final data = await rendered.toByteData(format: ui.ImageByteFormat.png);
  rendered.dispose();

  final temp = await getTemporaryDirectory();
  final staged = File(
    '${temp.path}/chaty_fx_${DateTime.now().millisecondsSinceEpoch}.png',
  );
  await staged.writeAsBytes(data!.buffer.asUint8List());

  final compressed = await FlutterImageCompress.compressAndGetFile(
    staged.absolute.path,
    '${temp.path}/chaty_fx_${DateTime.now().millisecondsSinceEpoch}.jpg',
    quality: 90,
  );
  unawaited(staged.delete().catchError((_) => staged));
  return compressed?.path ?? staged.path;
}

const List<double> _identity = <double>[
  1, 0, 0, 0, 0, //
  0, 1, 0, 0, 0, //
  0, 0, 1, 0, 0, //
  0, 0, 0, 1, 0,
];


class _DocumentGuidePainter extends CustomPainter {
  const _DocumentGuidePainter();

  @override
  void paint(Canvas canvas, Size size) {
    final pageWidth = math.min(size.width * 0.78, size.height * 0.58 / 1.414);
    final pageHeight = pageWidth * 1.414;
    final page = Rect.fromCenter(
      center: Offset(size.width / 2, size.height * 0.47),
      width: pageWidth,
      height: pageHeight,
    );
    final cornerLength = math.min(28.0, pageWidth * 0.12);
    final paint = Paint()
      ..color = const Color(0xFFF8FAFC)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;

    final corners = Path()
      ..moveTo(page.left, page.top + cornerLength)
      ..lineTo(page.left, page.top)
      ..lineTo(page.left + cornerLength, page.top)
      ..moveTo(page.right - cornerLength, page.top)
      ..lineTo(page.right, page.top)
      ..lineTo(page.right, page.top + cornerLength)
      ..moveTo(page.right, page.bottom - cornerLength)
      ..lineTo(page.right, page.bottom)
      ..lineTo(page.right - cornerLength, page.bottom)
      ..moveTo(page.left + cornerLength, page.bottom)
      ..lineTo(page.left, page.bottom)
      ..lineTo(page.left, page.bottom - cornerLength);
    canvas.drawPath(corners, paint);

    final small = Paint()
      ..color = const Color(0xFF60A5FA).withValues(alpha: 0.7)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1;
    canvas.drawRRect(
      RRect.fromRectAndRadius(page.deflate(5), const Radius.circular(3)),
      small,
    );
  }

  @override
  bool shouldRepaint(covariant _DocumentGuidePainter oldDelegate) => false;
}



/// Compact custom vector glyphs for camera actions and capture modes.
class _CameraGlyph extends StatelessWidget {
  const _CameraGlyph({
    required this.kind,
    required this.color,
    required this.size,
  });

  final String kind;
  final Color color;
  final double size;

  @override
  Widget build(BuildContext context) => CustomPaint(
    size: Size.square(size),
    painter: _CameraGlyphPainter(kind: kind, color: color),
  );
}

class _CameraGlyphPainter extends CustomPainter {
  const _CameraGlyphPainter({required this.kind, required this.color});
  final String kind;
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    canvas.save();
    canvas.scale(size.width / 32, size.height / 32);
    final line = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.2
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;
    final fill = Paint()..color = color;

    switch (kind) {
      case 'close':
        canvas.drawLine(const Offset(10, 10), const Offset(22, 22), line);
        canvas.drawLine(const Offset(22, 10), const Offset(10, 22), line);
      case 'flash':
        final bolt = Path()
          ..moveTo(18, 3)
          ..lineTo(8, 17)
          ..lineTo(15, 17)
          ..lineTo(13, 29)
          ..lineTo(24, 13)
          ..lineTo(17, 13)
          ..close();
        canvas.drawPath(bolt, fill);
      case 'filters':
        canvas.drawCircle(const Offset(11, 21), 3, fill);
        canvas.drawCircle(const Offset(22, 12), 2.3, fill);
        final star = Path()
          ..moveTo(16, 2)
          ..lineTo(18, 8)
          ..lineTo(24, 10)
          ..lineTo(18, 12)
          ..lineTo(16, 18)
          ..lineTo(14, 12)
          ..lineTo(8, 10)
          ..lineTo(14, 8)
          ..close();
        canvas.drawPath(star, line);
      case 'camera':
        canvas.drawRRect(
          RRect.fromRectAndRadius(const Rect.fromLTWH(3, 9, 26, 19), const Radius.circular(4)),
          line,
        );
        canvas.drawPath(
          Path()
            ..moveTo(9, 9)
            ..lineTo(12, 5)
            ..lineTo(20, 5)
            ..lineTo(23, 9),
          line,
        );
        canvas.drawCircle(const Offset(16, 18), 5.2, line);
      case 'document':
        final page = Path()
          ..moveTo(8, 3)
          ..lineTo(20, 3)
          ..lineTo(25, 8)
          ..lineTo(25, 29)
          ..lineTo(7, 29)
          ..lineTo(7, 4)
          ..close();
        canvas.drawPath(page, line);
        canvas.drawLine(const Offset(11, 14), const Offset(21, 14), line);
        canvas.drawLine(const Offset(11, 19), const Offset(21, 19), line);
        canvas.drawLine(const Offset(11, 24), const Offset(18, 24), line);
        canvas.drawLine(const Offset(20, 3), const Offset(20, 8), line);
        canvas.drawLine(const Offset(20, 8), const Offset(25, 8), line);
      case 'gallery':
        canvas.drawRRect(
          RRect.fromRectAndRadius(const Rect.fromLTWH(3, 4, 26, 24), const Radius.circular(4)),
          line,
        );
        canvas.drawCircle(const Offset(11, 11), 2.1, line);
        canvas.drawPath(
          Path()
            ..moveTo(5, 24)
            ..lineTo(13, 16)
            ..lineTo(18, 21)
            ..lineTo(22, 17)
            ..lineTo(28, 24),
          line,
        );
      case 'flip':
        canvas.drawArc(const Rect.fromLTWH(3, 5, 20, 20), math.pi * .15, math.pi * 1.28, false, line);
        canvas.drawPath(
          Path()
            ..moveTo(19, 3)
            ..lineTo(26, 6)
            ..lineTo(21, 12),
          line,
        );
        canvas.drawArc(const Rect.fromLTWH(9, 9, 20, 20), math.pi * 1.12, math.pi * 1.25, false, line);
        canvas.drawPath(
          Path()
            ..moveTo(13, 29)
            ..lineTo(6, 26)
            ..lineTo(11, 20),
          line,
        );
      default:
        canvas.drawCircle(const Offset(16, 16), 10, line);
        canvas.drawCircle(const Offset(16, 16), 3, fill);
    }
    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant _CameraGlyphPainter oldDelegate) =>
      oldDelegate.kind != kind || oldDelegate.color != color;
}

/// Distinctive category-specific line art for filter swatches; actual looks
/// remain driven by the effect's color matrix rather than the thumbnail.
class _EffectGlyphPainter extends CustomPainter {
  const _EffectGlyphPainter({
    required this.id,
    required this.category,
    required this.color,
  });

  final String id;
  final EffectCategory category;
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    canvas.save();
    canvas.scale(size.width / 32, size.height / 32);
    final line = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.1
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;
    final fill = Paint()..color = color;

    switch (category) {
      case EffectCategory.beauty:
      case EffectCategory.glow:
      case EffectCategory.faceAR:
      case EffectCategory.celebration:
        for (final point in const <Offset>[
          Offset(9, 9), Offset(23, 7), Offset(22, 23), Offset(9, 24),
        ]) {
          canvas.drawCircle(point, id == 'none' ? 2 : 2.3, fill);
        }
        canvas.drawLine(const Offset(16, 1), const Offset(16, 31), line);
        canvas.drawLine(const Offset(1, 16), const Offset(31, 16), line);
      case EffectCategory.portrait:
        canvas.drawOval(const Rect.fromLTWH(7, 3, 18, 26), line);
        canvas.drawCircle(const Offset(12, 13), 1.8, fill);
        canvas.drawCircle(const Offset(20, 13), 1.8, fill);
        canvas.drawArc(const Rect.fromLTWH(11, 15, 10, 8), 0, math.pi, false, line);
      case EffectCategory.cinematic:
        canvas.drawRRect(
          RRect.fromRectAndRadius(const Rect.fromLTWH(3, 6, 26, 22), const Radius.circular(3)),
          line,
        );
        canvas.drawLine(const Offset(3, 13), const Offset(29, 13), line);
        for (final x in <double>[6, 14, 22]) {
          canvas.drawLine(Offset(x, 6), Offset(x + 4, 13), line);
        }
      case EffectCategory.retro:
      case EffectCategory.film:
        canvas.drawCircle(const Offset(16, 16), 7, line);
        for (var i = 0; i < 8; i++) {
          final angle = i * math.pi / 4;
          canvas.drawLine(
            Offset(16 + math.cos(angle) * 11, 16 + math.sin(angle) * 11),
            Offset(16 + math.cos(angle) * 14, 16 + math.sin(angle) * 14),
            line,
          );
        }
      case EffectCategory.color:
        for (var i = 0; i < 3; i++) {
          final bar = Rect.fromLTWH(5 + i * 8.5, 5 + (i % 2) * 4, 6, 23 - (i % 2) * 8);
          canvas.drawRRect(RRect.fromRectAndRadius(bar, const Radius.circular(2)), fill);
        }
      case EffectCategory.cyber:
        canvas.drawPath(
          Path()
            ..moveTo(3, 7)
            ..lineTo(12, 7)
            ..lineTo(16, 13)
            ..lineTo(24, 13)
            ..lineTo(29, 20)
            ..lineTo(18, 20)
            ..lineTo(14, 26)
            ..lineTo(4, 26),
          line,
        );
        canvas.drawCircle(const Offset(16, 16), 13, line);
      case EffectCategory.background:
        canvas.drawPath(
          Path()
            ..moveTo(2, 27)
            ..lineTo(10, 14)
            ..lineTo(16, 22)
            ..lineTo(22, 9)
            ..lineTo(30, 27)
            ..close(),
          line,
        );
        canvas.drawLine(const Offset(2, 28), const Offset(30, 28), line);
    }
    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant _EffectGlyphPainter oldDelegate) =>
      oldDelegate.id != id ||
      oldDelegate.category != category ||
      oldDelegate.color != color;
}

