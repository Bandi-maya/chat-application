import 'dart:io';
import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';

import '../../../ui/core/controllers/app_icon_controller.dart';
import '../../../ui/core/design_system/settings_primitives.dart';
import '../../../ui/core/widgets/app_brand_icon.dart';

class AppIconSettingsScreen extends StatefulWidget {
  final AppIconController appIconController;

  const AppIconSettingsScreen({super.key, required this.appIconController});

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
            controller.launcherIcon == variant)) {
      return;
    }

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text('Use ${variant.title} icon?'),
        content: const Text(
          'Chaty will switch to this launcher icon. Your saved custom icons stay available.',
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
    if (confirmed != true || !mounted) return;

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
          'Some launchers cache app icons briefly. Restart Chaty if the old icon is still visible.',
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

  Future<void> _selectCustomPreset(CustomIconPreset preset) async {
    final controller = widget.appIconController;
    if (controller.isBusy) return;
    if (controller.brandIconSource == BrandIconSource.custom &&
        controller.activeCustomPresetId == preset.id &&
        controller.customLauncherState == CustomLauncherState.active) {
      return;
    }

    final success = await controller.activateCustomPreset(preset.id);
    if (!mounted) return;
    if (!success) {
      _showError(controller.lastError ?? 'Could not apply the custom icon.');
      return;
    }
    _showCustomStateMessage();
  }

  Future<void> _deleteCustomPreset(CustomIconPreset preset) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Delete custom icon?'),
        content: const Text(
          'This removes only this saved custom icon. Other custom and built-in icons are not changed.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      await widget.appIconController.removeCustomPreset(preset.id);
    }
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
                  'Add custom app icon',
                  style: Theme.of(sheetContext).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
              ListTile(
                leading: const Icon(Icons.photo_library_outlined),
                title: const Text('Photos'),
                subtitle: const Text(
                  'Choose an image with Android Photo Picker',
                ),
                onTap: () => Navigator.of(
                  sheetContext,
                ).pop(CustomIconInputSource.photos),
              ),
              ListTile(
                leading: const Icon(Icons.photo_camera_outlined),
                title: const Text('Camera'),
                subtitle: const Text('Capture a new image'),
                onTap: () => Navigator.of(
                  sheetContext,
                ).pop(CustomIconInputSource.camera),
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
          builder: (_) =>
              _CustomBrandIconEditor(imageBytes: bytes, controller: controller),
        ),
      );
    } catch (_) {
      if (mounted) _showError('The selected image could not be opened.');
    } finally {
      try {
        if (await preparedFile.exists()) await preparedFile.delete();
      } catch (_) {
        // Temporary picker input is best-effort cleanup only.
      }
    }
  }

  void _showCustomStateMessage() {
    final state = widget.appIconController.customLauncherState;
    final message = switch (state) {
      CustomLauncherState.active => 'Custom Chaty icon applied.',
      CustomLauncherState.pending =>
        'Approve the one-time Android Home-screen request. Later custom icon changes reuse the same launcher entry.',
      CustomLauncherState.unsupported =>
        'This launcher cannot activate the runtime custom Home icon. The preset is still saved.',
      CustomLauncherState.failed =>
        'The custom preset was saved, but Android could not activate it.',
      CustomLauncherState.inactive => 'Custom icon preset saved.',
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
    if (controller.brandIconSource == BrandIconSource.bundled) {
      return 'Launcher: ${controller.launcherIcon.title}';
    }
    return switch (controller.customLauncherState) {
      CustomLauncherState.active => 'Launcher: Custom icon active',
      CustomLauncherState.pending => 'Launcher: Custom icon awaiting approval',
      CustomLauncherState.unsupported => 'Launcher: Custom preset saved',
      CustomLauncherState.failed => 'Launcher: Custom icon activation failed',
      CustomLauncherState.inactive => 'Launcher: Custom preset selected',
    };
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.appIconController,
      builder: (context, _) {
        final controller = widget.appIconController;
        final customPresets = controller.customIconPresets
            .where((preset) => preset.exists)
            .toList(growable: false);
        final totalTiles =
            LauncherIconVariant.values.length + customPresets.length + 1;

        return ChatySettingsPage(
          title: 'App Icon',
          subtitle: 'Launcher icon and Chaty branding',
          children: [
            ChatyPreviewCard(
              title: 'Current icon',
              child: Row(
                children: [
                  ChatyBrandIcon(
                    controller: controller,
                    size: 72,
                    borderRadius: 19,
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          controller.brandIconSource == BrandIconSource.custom
                              ? 'Custom Chaty icon'
                              : controller.launcherIcon.title,
                          style: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w800,
                          ),
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
                  'Built-in icons and every custom image you add live in the same icon library. Tap any icon to apply it.',
              children: [
                Padding(
                  padding: const EdgeInsets.all(12),
                  child: GridView.builder(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    itemCount: totalTiles,
                    gridDelegate:
                        const SliverGridDelegateWithMaxCrossAxisExtent(
                          maxCrossAxisExtent: 150,
                          mainAxisExtent: 132,
                          crossAxisSpacing: 10,
                          mainAxisSpacing: 10,
                        ),
                    itemBuilder: (context, index) {
                      if (index < LauncherIconVariant.values.length) {
                        final variant = LauncherIconVariant.values[index];
                        final selected =
                            controller.brandIconSource ==
                                BrandIconSource.bundled &&
                            controller.launcherIcon == variant;
                        return _LauncherIconOption(
                          variant: variant,
                          selected: selected,
                          enabled: !controller.isBusy,
                          onTap: () => _selectLauncherIcon(variant),
                        );
                      }

                      final customIndex =
                          index - LauncherIconVariant.values.length;
                      if (customIndex < customPresets.length) {
                        final preset = customPresets[customIndex];
                        final selected =
                            controller.brandIconSource ==
                                BrandIconSource.custom &&
                            controller.activeCustomPresetId == preset.id;
                        return _SavedCustomIconOption(
                          preset: preset,
                          selected: selected,
                          enabled: !controller.isBusy,
                          onTap: () => _selectCustomPreset(preset),
                          onDelete: () => _deleteCustomPreset(preset),
                        );
                      }

                      return _AddCustomIconOption(
                        enabled: !controller.isBusy,
                        onTap: _openCustomSourcePicker,
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
                        : () =>
                              _selectLauncherIcon(LauncherIconVariant.original),
                  ),
              ],
            ),
            if (customPresets.isNotEmpty)
              ChatySettingsSection(
                title: 'Custom icon library',
                description:
                    '${customPresets.length} saved custom icon${customPresets.length == 1 ? '' : 's'}. Long-press any custom tile above to delete only that preset.',
                children: [
                  ChatySettingsTile(
                    icon: Icons.add_photo_alternate_outlined,
                    title: 'Add another custom icon',
                    subtitle: 'Photo Picker or Camera • crop, zoom and rotate',
                    onTap: controller.isBusy ? null : _openCustomSourcePicker,
                  ),
                ],
              ),
            if (controller.customLauncherState == CustomLauncherState.pending)
              const ChatyInfoTile(
                message:
                    'The first custom Home icon requires Android launcher approval. After it is added once, switching between your saved custom presets updates the same launcher entry.',
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
    return _IconTileShell(
      selected: selected,
      enabled: enabled,
      label: variant.title,
      onTap: onTap,
      child: LauncherIconPreview(variant: variant, size: 64, borderRadius: 15),
    );
  }
}

class _SavedCustomIconOption extends StatelessWidget {
  final CustomIconPreset preset;
  final bool selected;
  final bool enabled;
  final VoidCallback onTap;
  final VoidCallback onDelete;

  const _SavedCustomIconOption({
    required this.preset,
    required this.selected,
    required this.enabled,
    required this.onTap,
    required this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    return _IconTileShell(
      selected: selected,
      enabled: enabled,
      label: 'Custom',
      onTap: onTap,
      onLongPress: enabled ? onDelete : null,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(15),
        child: Image.file(
          File(preset.path),
          width: 64,
          height: 64,
          fit: BoxFit.cover,
          gaplessPlayback: true,
          errorBuilder: (_, _, _) => const SizedBox(
            width: 64,
            height: 64,
            child: Center(child: Icon(Icons.broken_image_outlined)),
          ),
        ),
      ),
    );
  }
}

class _AddCustomIconOption extends StatelessWidget {
  final bool enabled;
  final VoidCallback onTap;

  const _AddCustomIconOption({required this.enabled, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return _IconTileShell(
      selected: false,
      enabled: enabled,
      label: 'Add',
      onTap: onTap,
      child: Container(
        width: 64,
        height: 64,
        decoration: BoxDecoration(
          color: scheme.surfaceContainerHighest,
          borderRadius: BorderRadius.circular(15),
        ),
        child: Icon(Icons.add_rounded, color: scheme.primary, size: 32),
      ),
    );
  }
}

class _IconTileShell extends StatelessWidget {
  final bool selected;
  final bool enabled;
  final String label;
  final Widget child;
  final VoidCallback onTap;
  final VoidCallback? onLongPress;

  const _IconTileShell({
    required this.selected,
    required this.enabled,
    required this.label,
    required this.child,
    required this.onTap,
    this.onLongPress,
  });

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Semantics(
      button: true,
      selected: selected,
      label: '$label launcher icon',
      child: Material(
        color: selected
            ? scheme.primaryContainer.withValues(alpha: 0.45)
            : scheme.surfaceContainerLow,
        borderRadius: BorderRadius.circular(16),
        child: InkWell(
          onTap: enabled ? onTap : null,
          onLongPress: enabled ? onLongPress : null,
          borderRadius: BorderRadius.circular(16),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 180),
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: selected
                    ? scheme.primary
                    : scheme.outlineVariant.withValues(alpha: 0.35),
                width: selected ? 1.5 : 1,
              ),
            ),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Stack(
                  clipBehavior: Clip.none,
                  children: [
                    child,
                    if (selected)
                      Positioned(
                        right: -5,
                        top: -5,
                        child: Container(
                          width: 22,
                          height: 22,
                          decoration: BoxDecoration(
                            color: scheme.primary,
                            shape: BoxShape.circle,
                          ),
                          child: Icon(
                            Icons.check_rounded,
                            size: 15,
                            color: scheme.onPrimary,
                          ),
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 9),
                Text(
                  label,
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
  final TransformationController _transformationController =
      TransformationController();
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
    setState(() => _saving = true);
    try {
      await WidgetsBinding.instance.endOfFrame;
      final boundary =
          _captureKey.currentContext?.findRenderObject()
              as RenderRepaintBoundary?;
      if (boundary == null) throw StateError('Preview is not ready.');
      final image = await boundary.toImage(pixelRatio: 3);
      final byteData = await image.toByteData(format: ui.ImageByteFormat.png);
      image.dispose();
      final png = byteData?.buffer.asUint8List();
      if (png == null || png.isEmpty)
        throw StateError('Could not encode image.');

      final success = await widget.controller.saveCustomBrandIcon(png);
      if (!mounted) return;
      if (success) {
        Navigator.of(context).pop();
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              widget.controller.lastError ?? 'Could not save custom icon.',
            ),
          ),
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
    final previewSize = math.min(MediaQuery.sizeOf(context).width - 40, 360.0);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Adjust custom icon'),
        actions: [
          TextButton(
            onPressed: _saving ? null : _reset,
            child: const Text('Reset'),
          ),
          const SizedBox(width: 8),
        ],
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 20),
          child: Column(
            children: [
              Text(
                'Move and zoom the image inside the square. Launchers may apply circle, squircle, or rounded-square masks.',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              const SizedBox(height: 20),
              Center(
                child: SizedBox(
                  width: previewSize,
                  height: previewSize,
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(28),
                    child: RepaintBoundary(
                      key: _captureKey,
                      child: ColoredBox(
                        color: scheme.surfaceContainerHighest,
                        child: InteractiveViewer(
                          transformationController: _transformationController,
                          minScale: 1,
                          maxScale: 6,
                          boundaryMargin: const EdgeInsets.all(80),
                          child: RotatedBox(
                            quarterTurns: _quarterTurns,
                            child: Image.memory(
                              widget.imageBytes,
                              width: previewSize,
                              height: previewSize,
                              fit: BoxFit.cover,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 14),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  IconButton.filledTonal(
                    tooltip: 'Rotate left',
                    onPressed: _saving
                        ? null
                        : () => setState(
                            () => _quarterTurns = (_quarterTurns + 3) % 4,
                          ),
                    icon: const Icon(Icons.rotate_left_rounded),
                  ),
                  const SizedBox(width: 12),
                  IconButton.filledTonal(
                    tooltip: 'Rotate right',
                    onPressed: _saving
                        ? null
                        : () => setState(
                            () => _quarterTurns = (_quarterTurns + 1) % 4,
                          ),
                    icon: const Icon(Icons.rotate_right_rounded),
                  ),
                ],
              ),
              const Spacer(),
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  onPressed: _saving ? null : _apply,
                  icon: _saving
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.check_rounded),
                  label: Text(_saving ? 'Saving…' : 'Add & apply icon'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
