import 'dart:io';
import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:file_picker/file_picker.dart';
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

class _AppIconSettingsScreenState extends State<AppIconSettingsScreen> {
  Future<void> _selectLauncherIcon(LauncherIconVariant variant) async {
    if (widget.appIconController.isBusy || variant == widget.appIconController.launcherIcon) return;

    final shouldApply = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text('Use ${variant.title} icon?'),
        content: const Text(
          'This changes Chaty’s real Android launcher icon and uses the same design for Chaty branding inside the app.',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(dialogContext).pop(false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.of(dialogContext).pop(true), child: const Text('Apply')),
        ],
      ),
    );
    if (shouldApply != true || !mounted) return;

    final success = await widget.appIconController.applyLauncherIcon(variant);
    if (!mounted) return;
    if (!success) {
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(widget.appIconController.lastError ?? 'Launcher icon change failed.')));
      return;
    }

    final restart = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Launcher icon applied'),
        content: const Text(
          'Android launchers can cache icons briefly. Restart Chaty now to finish the visual refresh, or continue using the app.',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(dialogContext).pop(false), child: const Text('Continue')),
          FilledButton(onPressed: () => Navigator.of(dialogContext).pop(true), child: const Text('Restart now')),
        ],
      ),
    );
    if (restart == true) await SystemNavigator.pop();
  }

  Future<void> _pickCustomImage() async {
    if (widget.appIconController.isBusy) return;
    try {
      final files = await FilePicker.pickFiles(type: FileType.image);
      if (!mounted || files.isEmpty) return;
      final selected = files.single;
      final selectedPath = selected.path;
      if (selectedPath == null || selectedPath.isEmpty) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('This image is not accessible as a local file on this device.')),
        );
        return;
      }

      final source = File(selectedPath);
      if (!await source.exists()) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('The selected image no longer exists.')));
        return;
      }

      const maxInputBytes = 25 * 1024 * 1024;
      final fileSize = await source.length();
      if (fileSize > maxInputBytes) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Choose an image smaller than 25 MB.')),
        );
        return;
      }
      if (fileSize < 1024) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Choose a valid image file.')));
        return;
      }

      final bytes = await source.readAsBytes();
      final decoded = await decodeImageFromList(bytes);
      if (decoded.width < 128 || decoded.height < 128) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Use an image that is at least 128 × 128 pixels.')));
        return;
      }

      if (!mounted) return;
      await Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => _CustomBrandIconEditor(
            imageBytes: bytes,
            controller: widget.appIconController,
          ),
        ),
      );
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('The selected image could not be opened.')));
    }
  }

  Future<void> _removeCustomIcon() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Remove custom brand icon?'),
        content: const Text('Chaty will return to the currently selected bundled app icon inside the app.'),
        actions: [
          TextButton(onPressed: () => Navigator.of(dialogContext).pop(false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.of(dialogContext).pop(true), child: const Text('Remove')),
        ],
      ),
    );
    if (confirmed == true) await widget.appIconController.removeCustomBrandIcon();
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
                              ? 'Custom Chaty brand'
                              : controller.launcherIcon.title,
                          style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'Launcher: ${controller.launcherIcon.title}',
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
              title: 'Chaty launcher icons',
              description: 'These designs are bundled into the Android app, so Android can switch the real Home Screen/app-drawer icon safely.',
              children: [
                Padding(
                  padding: const EdgeInsets.all(12),
                  child: GridView.builder(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    itemCount: LauncherIconVariant.values.length,
                    gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                      maxCrossAxisExtent: 150,
                      mainAxisExtent: 132,
                      crossAxisSpacing: 10,
                      mainAxisSpacing: 10,
                    ),
                    itemBuilder: (context, index) {
                      final variant = LauncherIconVariant.values[index];
                      final selected = controller.launcherIcon == variant;
                      return _LauncherIconOption(
                        variant: variant,
                        selected: selected,
                        enabled: !controller.isBusy,
                        onTap: () => _selectLauncherIcon(variant),
                      );
                    },
                  ),
                ),
                if (controller.launcherIcon != LauncherIconVariant.original)
                  ChatySettingsTile(
                    icon: Icons.restore_rounded,
                    title: 'Restore original launcher icon',
                    subtitle: 'Return Chaty to the default launcher design',
                    onTap: controller.isBusy ? null : () => _selectLauncherIcon(LauncherIconVariant.original),
                  ),
              ],
            ),
            ChatySettingsSection(
              title: 'Custom Chaty brand icon',
              description: 'Upload, crop, zoom, pan and rotate your own image. Android does not permit an arbitrary runtime file to become a normal launcher resource, so custom uploads are used for Chaty branding inside the app while the launcher keeps your selected bundled icon.',
              children: [
                ChatySettingsTile(
                  icon: Icons.add_photo_alternate_outlined,
                  title: controller.brandIconSource == BrandIconSource.custom ? 'Replace custom image' : 'Upload image',
                  subtitle: 'Crop and adjust a square Chaty brand image',
                  onTap: controller.isBusy ? null : _pickCustomImage,
                ),
                if (controller.brandIconSource == BrandIconSource.custom)
                  ChatySettingsTile(
                    icon: Icons.restore_rounded,
                    iconColor: Colors.orangeAccent,
                    title: 'Remove custom image',
                    subtitle: 'Return in-app branding to ${controller.launcherIcon.title}',
                    onTap: controller.isBusy ? null : _removeCustomIcon,
                  ),
              ],
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
                    if (selected)
                      Positioned(
                        right: -5,
                        top: -5,
                        child: Container(
                          width: 22,
                          height: 22,
                          decoration: BoxDecoration(color: scheme.primary, shape: BoxShape.circle),
                          child: Icon(Icons.check_rounded, size: 15, color: scheme.onPrimary),
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 9),
                Text(
                  variant.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(fontSize: 12.5, fontWeight: selected ? FontWeight.w800 : FontWeight.w600),
                ),
              ],
            ),
          ),
        ),
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
          'This will set your cropped image as the custom Chaty app icon.',
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
      final png = byteData?.buffer.asUint8List();
      if (png == null || png.isEmpty) throw StateError('Could not encode image.');
      final success = await widget.controller.saveCustomBrandIcon(png);
      if (!mounted) return;
      if (success) {
        Navigator.of(context).pop();

        final restart = await showDialog<bool>(
          context: context,
          barrierDismissible: false,
          builder: (dialogContext) => AlertDialog(
            title: const Text('App icon applied'),
            content: const Text(
              'Android launchers can cache icons briefly. Restart Chaty now to finish the visual refresh, or continue using the app.',
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
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(widget.controller.lastError ?? 'Could not save custom image.')),
        );
      }
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Could not create the cropped icon.')));
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
                'Drag to reposition. Pinch to zoom. Keep important details inside the guide.',
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
                          child: CustomPaint(painter: _SafeAreaGuidePainter(color: scheme.onSurface.withValues(alpha: 0.48))),
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
                      onPressed: _saving ? null : () => setState(() => _quarterTurns = (_quarterTurns - 1) % 4),
                      icon: const Icon(Icons.rotate_left_rounded),
                      label: const Text('Rotate left'),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: _saving ? null : () => setState(() => _quarterTurns = (_quarterTurns + 1) % 4),
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
                      ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                      : const Icon(Icons.check_rounded),
                  label: Text(_saving ? 'Applying…' : 'Apply custom brand icon'),
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
      Rect.fromLTWH(circleInset, circleInset, size.width - circleInset * 2, size.height - circleInset * 2),
      paint,
    );
  }

  @override
  bool shouldRepaint(covariant _SafeAreaGuidePainter oldDelegate) => oldDelegate.color != color;
}
