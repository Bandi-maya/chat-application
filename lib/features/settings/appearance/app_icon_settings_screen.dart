import 'dart:io';
import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';

import '../../../ui/core/controllers/app_icon_controller.dart';
import '../../../ui/core/design_system/chaty_settings_primitives.dart';
import '../../../ui/core/widgets/chaty_brand_icon.dart';

class AppIconSettingsScreen extends StatefulWidget {
  final AppIconController appIconController;

  const AppIconSettingsScreen({
    super.key,
    required this.appIconController,
  });

  @override
  State<AppIconSettingsScreen> createState() => _AppIconSettingsScreenState();
}

class _AppIconSettingsScreenState extends State<AppIconSettingsScreen>
    with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      widget.appIconController.refreshNativeLauncherState();
    }
  }

  Future<void> _selectLauncherIcon(LauncherIconVariant variant) async {
    final controller = widget.appIconController;
    if (controller.isBusy ||
        (controller.brandIconSource == BrandIconSource.bundled &&
            variant == controller.launcherIcon)) {
      return;
    }

    final shouldApply = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text('Use ${variant.title} icon?'),
        content: const Text(
          'This switches Chaty back to a packaged Android launcher icon. Your saved custom image is kept so you can return to it later.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Apply'),
          ),
        ],
      ),
    );
    if (shouldApply != true || !mounted) return;

    final success = await controller.applyLauncherIcon(variant);
    if (!mounted) return;
    if (!success) {
      _showError(controller.lastError ?? 'Launcher icon change failed.');
      return;
    }

    final restart = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Launcher icon applied'),
        content: const Text(
          'Some launchers cache packaged icons briefly. Restart Chaty now if you want to force a clean app restart.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Continue'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Restart now'),
          ),
        ],
      ),
    );
    if (restart == true) await SystemNavigator.pop();
  }

  Future<void> _openCustomSourcePicker() async {
    if (widget.appIconController.isBusy) return;

    final source = await showModalBottomSheet<CustomIconInputSource>(
      context: context,
      showDragHandle: true,
      builder: (sheetContext) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(12, 4, 12, 12),
                child: Text(
                  'Choose custom icon image',
                  style: Theme.of(sheetContext).textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w800,
                      ),
                ),
              ),
              ListTile(
                leading: const Icon(Icons.photo_library_outlined),
                title: const Text('Photos'),
                subtitle: const Text('Use Android’s private photo picker'),
                onTap: () => Navigator.of(sheetContext).pop(CustomIconInputSource.photos),
              ),
              ListTile(
                leading: const Icon(Icons.photo_camera_outlined),
                title: const Text('Camera'),
                subtitle: const Text('Capture a new image for the icon'),
                onTap: () => Navigator.of(sheetContext).pop(CustomIconInputSource.camera),
              ),
            ],
          ),
        ),
      ),
    );
    if (source == null || !mounted) return;
    await _loadCustomImage(source);
  }

  Future<void> _loadCustomImage(CustomIconInputSource source) async {
    final controller = widget.appIconController;
    final preparedPath = await controller.pickCustomIconImage(source);
    if (!mounted) return;
    if (preparedPath == null || preparedPath.isEmpty) {
      if (controller.lastError != null) _showError(controller.lastError!);
      return;
    }

    final preparedFile = File(preparedPath);
    try {
      if (!await preparedFile.exists()) {
        _showError('The selected image is no longer available.');
        return;
      }
      final bytes = await preparedFile.readAsBytes();
      if (bytes.isEmpty) {
        _showError('The selected image is empty or corrupted.');
        return;
      }

      final decoded = await decodeImageFromList(bytes);
      final width = decoded.width;
      final height = decoded.height;
      decoded.dispose();
      if (width < 128 || height < 128) {
        _showError('Use an image that is at least 128 × 128 pixels.');
        return;
      }

      if (!mounted) return;
      await Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => _CustomBrandIconEditor(
            imageBytes: bytes,
            controller: controller,
          ),
        ),
      );
    } catch (error) {
      if (mounted) _showError('The selected image could not be opened.');
    } finally {
      try {
        if (await preparedFile.exists()) await preparedFile.delete();
      } catch (_) {
        // Temporary picker files are best-effort cleanup only.
      }
    }
  }

  Future<void> _activateSavedCustomIcon() async {
    final controller = widget.appIconController;
    if (controller.isBusy) return;
    if (!controller.hasSavedCustomIcon) {
      await _openCustomSourcePicker();
      return;
    }

    final success = await controller.activateSavedCustomIcon();
    if (!mounted) return;
    if (!success) {
      _showError(controller.lastError ?? 'Could not activate the saved custom icon.');
      return;
    }
    _showCustomStateMessage();
  }

  Future<void> _removeCustomIcon() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Remove custom icon?'),
        content: const Text(
          'The saved custom image will be deleted and Chaty will return to the selected packaged launcher icon.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Remove'),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      await widget.appIconController.removeCustomBrandIcon();
    }
  }

  void _showCustomStateMessage() {
    final state = widget.appIconController.customLauncherState;
    final message = switch (state) {
      CustomLauncherState.active => 'Custom Chaty launcher icon is active.',
      CustomLauncherState.pending =>
        'Approve “Add to Home screen” in the Android launcher to activate the custom icon.',
      CustomLauncherState.unsupported =>
        'This launcher cannot pin a custom Home icon. The selected image is still used inside Chaty.',
      CustomLauncherState.failed =>
        'The custom image was saved, but Android could not activate the Home launcher entry.',
      CustomLauncherState.inactive => 'Custom image saved.',
    };
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  void _showError(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  String _launcherStatus(AppIconController controller) {
    if (controller.brandIconSource != BrandIconSource.custom) {
      return 'Launcher: ${controller.launcherIcon.title}';
    }
    return switch (controller.customLauncherState) {
      CustomLauncherState.active => 'Launcher: Custom active',
      CustomLauncherState.pending => 'Launcher: Waiting for Home-screen approval',
      CustomLauncherState.unsupported => 'Launcher: ${controller.launcherIcon.title} fallback',
      CustomLauncherState.failed => 'Launcher: ${controller.launcherIcon.title} recovery',
      CustomLauncherState.inactive => 'Launcher: ${controller.launcherIcon.title} fallback',
    };
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.appIconController,
      builder: (context, _) {
        final controller = widget.appIconController;
        return ChatySettingsPage(
          title: 'App Icon',
          subtitle: 'Launcher icon and Chaty branding',
          children: [
            ChatyPreviewCard(
              title: 'Current icon',
              child: Row(
                children: [
                  ChatyBrandIcon(controller: controller, size: 72, borderRadius: 19),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          controller.brandIconSource == BrandIconSource.custom
                              ? 'Custom Chaty icon'
                              : controller.launcherIcon.title,
                          style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          _launcherStatus(controller),
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                        if (controller.isBusy) ...[
                          const SizedBox(height: 10),
                          const LinearProgressIndicator(minHeight: 2),
                        ],
                      ],
                    ),
                  ),
                ],
              ),
            ),
            ChatySettingsSection(
              title: 'App icon',
              description:
                  'Choose one of Chaty’s packaged launcher icons or use your own photo as a temporary custom Home-screen identity.',
              children: [
                Padding(
                  padding: const EdgeInsets.all(12),
                  child: GridView.builder(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    itemCount: LauncherIconVariant.values.length + 1,
                    gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                      maxCrossAxisExtent: 150,
                      mainAxisExtent: 132,
                      crossAxisSpacing: 10,
                      mainAxisSpacing: 10,
                    ),
                    itemBuilder: (context, index) {
                      if (index == LauncherIconVariant.values.length) {
                        return _CustomIconOption(
                          controller: controller,
                          selected: controller.brandIconSource == BrandIconSource.custom,
                          enabled: !controller.isBusy,
                          onTap: controller.hasSavedCustomIcon
                              ? _activateSavedCustomIcon
                              : _openCustomSourcePicker,
                        );
                      }

                      final variant = LauncherIconVariant.values[index];
                      final selected = controller.brandIconSource == BrandIconSource.bundled &&
                          controller.launcherIcon == variant;
                      return _LauncherIconOption(
                        variant: variant,
                        selected: selected,
                        enabled: !controller.isBusy,
                        onTap: () => _selectLauncherIcon(variant),
                      );
                    },
                  ),
                ),
                if (controller.launcherIcon != LauncherIconVariant.original ||
                    controller.brandIconSource == BrandIconSource.custom)
                  ChatySettingsTile(
                    icon: Icons.restore_rounded,
                    title: 'Restore original launcher icon',
                    subtitle: 'Use Chaty’s original packaged icon',
                    onTap: controller.isBusy
                        ? null
                        : () => _selectLauncherIcon(LauncherIconVariant.original),
                  ),
              ],
            ),
            ChatySettingsSection(
              title: 'Custom icon',
              description:
                  'Photos use Android’s private Photo Picker. Camera capture uses a temporary FileProvider URI. The cropped result is copied into Chaty’s private storage and persists until you replace, remove, or uninstall the app.',
              children: [
                ChatySettingsTile(
                  icon: Icons.add_photo_alternate_outlined,
                  title: controller.hasSavedCustomIcon ? 'Change custom image' : 'Choose custom image',
                  subtitle: 'Photo Picker or Camera • crop, zoom and rotate',
                  onTap: controller.isBusy ? null : _openCustomSourcePicker,
                ),
                if (controller.hasSavedCustomIcon &&
                    controller.brandIconSource != BrandIconSource.custom)
                  ChatySettingsTile(
                    icon: Icons.mobile_friendly_rounded,
                    title: 'Use saved custom icon',
                    subtitle: 'Re-activate the existing custom image without selecting it again',
                    onTap: controller.isBusy ? null : _activateSavedCustomIcon,
                  ),
                if (controller.hasSavedCustomIcon)
                  ChatySettingsTile(
                    icon: Icons.delete_outline_rounded,
                    iconColor: Colors.orangeAccent,
                    title: 'Remove custom image',
                    subtitle: 'Delete the saved custom icon and restore packaged branding',
                    onTap: controller.isBusy ? null : _removeCustomIcon,
                  ),
              ],
            ),
            if (controller.customLauncherState == CustomLauncherState.pending)
              const ChatyInfoTile(
                message:
                    'Custom icon is prepared. Complete the Android launcher’s “Add to Home screen” confirmation. Chaty keeps the packaged launcher entry enabled until approval succeeds.',
                icon: Icons.pending_actions_rounded,
              ),
            if (controller.lastError != null)
              ChatyInfoTile(
                message: controller.lastError!,
                icon: Icons.error_outline_rounded,
                color: Theme.of(context).colorScheme.error,
              ),
          ],
        );
      },
    );
  }
}

class _LauncherIconOption extends StatelessWidget {
  final LauncherIconVariant variant;
  final bool selected;
  final bool enabled;
  final VoidCallback onTap;

  const _LauncherIconOption({
    required this.variant,
    required this.selected,
    required this.enabled,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Semantics(
      button: true,
      selected: selected,
      label: '${variant.title} launcher icon',
      child: Material(
        color: selected ? scheme.primaryContainer.withValues(alpha: 0.45) : scheme.surfaceContainerLow,
        borderRadius: BorderRadius.circular(16),
        child: InkWell(
          onTap: enabled ? onTap : null,
          borderRadius: BorderRadius.circular(16),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 180),
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: selected ? scheme.primary : scheme.outlineVariant.withValues(alpha: 0.35),
                width: selected ? 1.5 : 1,
              ),
            ),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Stack(
                  clipBehavior: Clip.none,
                  children: [
                    LauncherIconPreview(variant: variant, size: 64, borderRadius: 15),
                    if (selected) _SelectedBadge(scheme: scheme),
                  ],
                ),
                const SizedBox(height: 9),
                Text(
                  variant.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: 12.5,
                    fontWeight: selected ? FontWeight.w800 : FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _CustomIconOption extends StatelessWidget {
  final AppIconController controller;
  final bool selected;
  final bool enabled;
  final VoidCallback onTap;

  const _CustomIconOption({
    required this.controller,
    required this.selected,
    required this.enabled,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final path = controller.customBrandIconPath;
    final hasPreview = controller.hasSavedCustomIcon && path != null;

    return Semantics(
      button: true,
      selected: selected,
      label: 'Custom launcher icon',
      child: Material(
        color: selected ? scheme.primaryContainer.withValues(alpha: 0.45) : scheme.surfaceContainerLow,
        borderRadius: BorderRadius.circular(16),
        child: InkWell(
          onTap: enabled ? onTap : null,
          borderRadius: BorderRadius.circular(16),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 180),
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: selected ? scheme.primary : scheme.outlineVariant.withValues(alpha: 0.35),
                width: selected ? 1.5 : 1,
              ),
            ),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Stack(
                  clipBehavior: Clip.none,
                  children: [
                    ClipRRect(
                      borderRadius: BorderRadius.circular(15),
                      child: SizedBox(
                        width: 64,
                        height: 64,
                        child: hasPreview
                            ? Image.file(
                                File(path),
                                fit: BoxFit.cover,
                                gaplessPlayback: true,
                                errorBuilder: (_, _, _) => _CustomPlaceholder(scheme: scheme),
                              )
                            : _CustomPlaceholder(scheme: scheme),
                      ),
                    ),
                    if (selected) _SelectedBadge(scheme: scheme),
                  ],
                ),
                const SizedBox(height: 9),
                Text(
                  'Custom',
                  maxLines: 1,
                  style: TextStyle(
                    fontSize: 12.5,
                    fontWeight: selected ? FontWeight.w800 : FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _CustomPlaceholder extends StatelessWidget {
  final ColorScheme scheme;

  const _CustomPlaceholder({required this.scheme});

  @override
  Widget build(BuildContext context) {
    return ColoredBox(
      color: scheme.surfaceContainerHighest,
      child: Center(
        child: Icon(Icons.add_photo_alternate_outlined, color: scheme.primary, size: 30),
      ),
    );
  }
}

class _SelectedBadge extends StatelessWidget {
  final ColorScheme scheme;

  const _SelectedBadge({required this.scheme});

  @override
  Widget build(BuildContext context) {
    return Positioned(
      right: -5,
      top: -5,
      child: Container(
        width: 22,
        height: 22,
        decoration: BoxDecoration(color: scheme.primary, shape: BoxShape.circle),
        child: Icon(Icons.check_rounded, size: 15, color: scheme.onPrimary),
      ),
    );
  }
}

class _CustomBrandIconEditor extends StatefulWidget {
  final Uint8List imageBytes;
  final AppIconController controller;

  const _CustomBrandIconEditor({
    required this.imageBytes,
    required this.controller,
  });

  @override
  State<_CustomBrandIconEditor> createState() => _CustomBrandIconEditorState();
}

class _CustomBrandIconEditorState extends State<_CustomBrandIconEditor> {
  final GlobalKey _captureKey = GlobalKey();
  final TransformationController _transformationController = TransformationController();
  int _quarterTurns = 0;
  bool _saving = false;

  @override
  void dispose() {
    _transformationController.dispose();
    super.dispose();
  }

  void _reset() {
    _transformationController.value = Matrix4.identity();
    setState(() => _quarterTurns = 0);
  }

  Future<void> _apply() async {
    if (_saving) return;

    final shouldApply = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Apply custom app icon?'),
        content: const Text(
          'Chaty will save this crop privately and ask Android to create or update the custom Home-screen launcher entry.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Apply'),
          ),
        ],
      ),
    );
    if (shouldApply != true || !mounted) return;

    setState(() => _saving = true);
    try {
      await WidgetsBinding.instance.endOfFrame;
      final boundary = _captureKey.currentContext?.findRenderObject() as RenderRepaintBoundary?;
      if (boundary == null) throw StateError('Preview is not ready.');
      final image = await boundary.toImage(pixelRatio: 3);
      final byteData = await image.toByteData(format: ui.ImageByteFormat.png);
      image.dispose();
      final png = byteData?.buffer.asUint8List();
      if (png == null || png.isEmpty) throw StateError('Could not encode image.');

      final success = await widget.controller.saveCustomBrandIcon(png);
      if (!mounted) return;
      if (success) {
        Navigator.of(context).pop();
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(widget.controller.lastError ?? 'Could not save custom image.')),
        );
      }
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Could not create the cropped icon.')),
      );
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Adjust custom icon'),
        actions: [
          TextButton(onPressed: _saving ? null : _reset, child: const Text('Reset')),
          const SizedBox(width: 8),
        ],
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 20),
          child: Column(
            children: [
              Text(
                'Drag to reposition. Pinch to zoom. The circle and rounded-square guides show common launcher masks.',
                style: Theme.of(context).textTheme.bodyMedium,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 18),
              Expanded(
                child: Center(
                  child: AspectRatio(
                    aspectRatio: 1,
                    child: Stack(
                      fit: StackFit.expand,
                      children: [
                        RepaintBoundary(
                          key: _captureKey,
                          child: ClipRRect(
                            borderRadius: BorderRadius.circular(28),
                            child: ColoredBox(
                              color: scheme.surfaceContainerHighest,
                              child: InteractiveViewer(
                                transformationController: _transformationController,
                                minScale: 0.75,
                                maxScale: 6,
                                boundaryMargin: const EdgeInsets.all(180),
                                child: Transform.rotate(
                                  angle: _quarterTurns * math.pi / 2,
                                  child: SizedBox.expand(
                                    child: Image.memory(
                                      widget.imageBytes,
                                      fit: BoxFit.cover,
                                      gaplessPlayback: true,
                                      filterQuality: FilterQuality.high,
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ),
                        IgnorePointer(
                          child: CustomPaint(
                            painter: _SafeAreaGuidePainter(
                              color: scheme.onSurface.withValues(alpha: 0.48),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 18),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: _saving
                          ? null
                          : () => setState(() => _quarterTurns = (_quarterTurns - 1) % 4),
                      icon: const Icon(Icons.rotate_left_rounded),
                      label: const Text('Rotate left'),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: _saving
                          ? null
                          : () => setState(() => _quarterTurns = (_quarterTurns + 1) % 4),
                      icon: const Icon(Icons.rotate_right_rounded),
                      label: const Text('Rotate right'),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              SizedBox(
                width: double.infinity,
                height: 50,
                child: FilledButton.icon(
                  onPressed: _saving ? null : _apply,
                  icon: _saving
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.check_rounded),
                  label: Text(_saving ? 'Applying…' : 'Apply custom icon'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _SafeAreaGuidePainter extends CustomPainter {
  final Color color;

  const _SafeAreaGuidePainter({required this.color});

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.5;
    final inset = size.shortestSide * 0.11;
    canvas.drawRRect(
      RRect.fromRectAndRadius(
        Rect.fromLTWH(inset, inset, size.width - inset * 2, size.height - inset * 2),
        const Radius.circular(42),
      ),
      paint,
    );
    final circleInset = size.shortestSide * 0.18;
    canvas.drawOval(
      Rect.fromLTWH(
        circleInset,
        circleInset,
        size.width - circleInset * 2,
        size.height - circleInset * 2,
      ),
      paint,
    );
  }

  @override
  bool shouldRepaint(covariant _SafeAreaGuidePainter oldDelegate) => oldDelegate.color != color;
}
