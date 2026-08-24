import 'dart:async';
import 'dart:io';
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

/// What the caller wants to do with the capture. Drives the confirm-pane
/// caption copy and whether the chosen look is returned as metadata (chat)
/// or baked into the pixels (stories, which carry no metadata column).
enum ChatyCaptureMode { chat, story }

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

  CameraController? _controller;
  _CameraPhase _phase = _CameraPhase.initializing;
  String? _errorMessage;
  bool _flashOn = false;
  bool _effectsOpen = false;
  bool _capturing = false;

  bool _switchingCamera = false;

  XFile? _capturedFile;
  final TextEditingController _captionCtrl = TextEditingController();
  final FocusNode _captionFocus = FocusNode();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
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
      imageFormatGroup: ImageFormatGroup.jpeg,
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
    unawaited(_controller?.dispose());
    _engine.dispose();
    super.dispose();
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
    setState(() {
      _capturedFile = XFile(path);
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
      if (!mounted) return;
      setState(() => _capturedFile = file);
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
    var effectId = engineEffect.id;
    var intensity = _engine.intensity;

    // Stories carry no metadata channel, so the look must live in the pixels.
    if (widget.mode == ChatyCaptureMode.story &&
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
        Positioned(
          left: 0,
          right: 0,
          bottom: 0,
          child: _buildBottomControls(),
        ),
      ],
    );
  }

  Widget _buildBottomControls() {
    final ready = _phase == _CameraPhase.ready;
    final allEffects = EffectRegistry.allEffects;

    return Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Colors.transparent, Colors.black87, Colors.black],
        ),
      ),
      padding: EdgeInsets.only(
        bottom: MediaQuery.paddingOf(context).bottom + 12,
        top: 16,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Filter carousel centered horizontally with snap physics
          if (ready && _effectsOpen) ...[
            SizedBox(
              height: 74,
              child: ListView.builder(
                scrollDirection: Axis.horizontal,
                physics: const BouncingScrollPhysics(),
                padding: EdgeInsets.symmetric(
                  horizontal: MediaQuery.sizeOf(context).width * 0.38,
                ),
                itemCount: allEffects.length,
                itemBuilder: (context, index) {
                  final effect = allEffects[index];
                  final isSelected = _engine.activeEffect.id == effect.id;

                  return Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 6),
                    child: GestureDetector(
                      onTap: () {
                        ChatyHaptics.selection();
                        _engine.selectEffect(effect);
                      },
                      child: AnimatedScale(
                        scale: isSelected ? 1.08 : 0.86,
                        duration: const Duration(milliseconds: 200),
                        curve: Curves.easeOutCubic,
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Container(
                              width: 48,
                              height: 48,
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                color: effect.previewColor.withValues(
                                  alpha: isSelected ? 0.95 : 0.4,
                                ),
                                border: Border.all(
                                  color: isSelected
                                      ? Colors.white
                                      : Colors.white30,
                                  width: isSelected ? 2.5 : 1.2,
                                ),
                              ),
                              child: Icon(
                                effect.icon,
                                size: 22,
                                color: isSelected ? Colors.black : Colors.white,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              effect.name,
                              style: TextStyle(
                                color: isSelected
                                    ? Colors.white
                                    : Colors.white60,
                                fontSize: 10,
                                fontWeight: isSelected
                                    ? FontWeight.w700
                                    : FontWeight.w500,
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
            const SizedBox(height: 12),
          ],

          // Main Controls Row: Gallery, Capture Shutter, Flip Camera
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Semantics(
                  button: true,
                  label: 'Pick image from gallery',
                  child: InkWell(
                    borderRadius: BorderRadius.circular(26),
                    onTap: _pickFromGallery,
                    child: const SizedBox.square(
                      dimension: 52,
                      child: Center(
                        child: Icon(
                          Icons.photo_library_rounded,
                          color: Colors.white,
                          size: 28,
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
                      dimension: 52,
                      child: Icon(
                        Icons.cameraswitch_rounded,
                        color: ready ? Colors.white : Colors.white30,
                        size: 28,
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
                  child: KeyedSubtree(
                    key: ValueKey(_engine.activeEffect.id),
                    child: _engine.renderEffect(
                      child: CameraPreview(controller),
                    ),
                  ),
                ),
              ),
            ),
          ),
        },
        _buildTopBar(),
      ],
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
            icon: Icons.close_rounded,
            onPressed: () => Navigator.of(context).maybePop(),
          ),
          const Spacer(),
          if (_phase == _CameraPhase.ready) ...[
            _RoundIconAction(
              semanticsLabel: _flashOn ? 'Turn flash off' : 'Turn flash on',
              icon: _flashOn ? Icons.flash_on_rounded : Icons.flash_off_rounded,
              onPressed: _toggleFlash,
            ),
            const SizedBox(width: 10),
            _RoundIconAction(
              semanticsLabel: _effectsOpen ? 'Hide effects' : 'Show effects',
              icon: Icons.auto_awesome_rounded,
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
    required this.icon,
    required this.onPressed,
    this.highlighted = false,
  });

  final String semanticsLabel;
  final IconData icon;
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
          child: Icon(icon, color: Colors.white, size: 21),
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
