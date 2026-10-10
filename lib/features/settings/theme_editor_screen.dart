import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../ui/core/design_system/design_system.dart';
import '../messages/message_bubble.dart';
import '../../domain/models/chat_message.dart';
import '../../ui/core/theme/chaty_theme_manager.dart';
import '../../ui/core/theme/image_theme_generator.dart';
import '../../ui/core/theme/theme_preview_card.dart';
import '../../ui/core/ticks/delivery_icon_style.dart';

class ThemeEditorScreen extends StatefulWidget {
  final ThemeController themeController;

  const ThemeEditorScreen({super.key, required this.themeController});

  @override
  State<ThemeEditorScreen> createState() => _ThemeEditorScreenState();
}

class _ThemeEditorScreenState extends State<ThemeEditorScreen> {
  late ThemeConfig _current;

  @override
  void initState() {
    super.initState();
    _current = widget.themeController.globalTheme;
  }

  void _applyAndSave() {
    widget.themeController.updateThemeConfig(_current);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: const Text('Theme customization saved!'),
        backgroundColor: context.colors.success,
      ),
    );
  }

  Future<void> _exportTheme() async {
    try {
      final file = await ChatyThemeManager.saveThemeToFile(_current);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Theme exported to ${file.path.split("/").last}'),
          backgroundColor: context.colors.success,
        ),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Export failed: $e')));
    }
  }

  Future<void> _importTheme() async {
    try {
      final result = await FilePicker.pickFile(
        type: FileType.custom,
        allowedExtensions: ['json'],
      );
      if (result == null || result.path == null) return;
      final file = File(result.path!);
      final content = await file.readAsString();
      final theme = ChatyThemeManager.validateAndImportTheme(content);
      setState(() => _current = theme);
      widget.themeController.updateThemeConfig(theme);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Imported and applied "${theme.name}" successfully.'),
          backgroundColor: context.colors.success,
        ),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Import failed: ${e is FormatException ? e.message : "Invalid theme JSON"}'),
          backgroundColor: context.colors.error,
        ),
      );
    }
  }

  Future<void> _generateFromImage() async {
    try {
      final result = await FilePicker.pickFile(type: FileType.image);
      if (result == null || result.path == null) return;
      final file = File(result.path!);
      final isDark = _current.brightness == Brightness.dark;
      final generated = await ImageThemeGenerator.generateFromImageFile(
        file,
        isDark: isDark,
      );
      setState(() => _current = generated);
      widget.themeController.updateThemeConfig(generated);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: const Text('Palette extracted and applied from photo!'),
          backgroundColor: context.colors.success,
        ),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Could not generate a theme from that image. Try another image.'),
        ),
      );
    }
  }

  Future<void> _confirmResetTheme() async {
    HapticFeedback.mediumImpact();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Reset Theme to Defaults?'),
        content: const Text(
          'This will restore the factory default theme and clear your custom studio settings. '
          'Per-conversation themes are preserved.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: context.colors.error,
            ),
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('Reset'),
          ),
        ],
      ),
    );

    if (confirmed == true && mounted) {
      widget.themeController.resetToDefaults();
      setState(() {
        _current = widget.themeController.globalTheme;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: const Text('Theme reset to system defaults.'),
          backgroundColor: context.colors.success,
        ),
      );
    }
  }

  void _showThemeDownloadGuide() {
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: context.colors.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) {
        return Padding(
          padding: const EdgeInsets.all(ChatySpacing.lg),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(Icons.cloud_download_rounded, color: context.colors.primary, size: 28),
                  const SizedBox(width: 12),
                  Text(
                    'Theme Packs & Offline Store',
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                      color: context.colors.foreground,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Text(
                'Chaty includes 23 authentic built-in themes (WhatsApp, iOS, Midnight, AMOLED, Paper, Monochrome, and more). '
                'You can also download and import custom community theme files (.json) or extract palettes from wallpapers directly.',
                style: TextStyle(
                  fontSize: 14,
                  height: 1.4,
                  color: context.colors.foregroundSecondary,
                ),
              ),
              const SizedBox(height: 20),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton.icon(
                      icon: const Icon(Icons.file_download_outlined),
                      label: const Text('Import JSON'),
                      onPressed: () {
                        Navigator.of(ctx).pop();
                        _importTheme();
                      },
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: FilledButton.icon(
                      icon: const Icon(Icons.palette_outlined),
                      label: const Text('Photo Palette'),
                      onPressed: () {
                        Navigator.of(ctx).pop();
                        _generateFromImage();
                      },
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
            ],
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return ChatyScaffold(
      appBar: ChatyAppBar(
        title: 'Theme & Design Studio',
        leading: const ChatyBackButton(),
        actions: [
          IconButton(
            icon: const Icon(Icons.palette_outlined),
            tooltip: 'Extract from photo',
            onPressed: _generateFromImage,
          ),
          IconButton(
            icon: const Icon(Icons.cloud_download_outlined),
            tooltip: 'Theme Packs & Download Guide',
            onPressed: _showThemeDownloadGuide,
          ),
          IconButton(
            icon: const Icon(Icons.file_download_outlined),
            tooltip: 'Import Theme JSON',
            onPressed: _importTheme,
          ),
          IconButton(
            icon: const Icon(Icons.file_upload_outlined),
            tooltip: 'Export Theme JSON',
            onPressed: _exportTheme,
          ),
          PopupMenuButton<String>(
            icon: const Icon(Icons.more_vert_rounded),
            tooltip: 'More options',
            onSelected: (value) {
              if (value == 'reset') {
                _confirmResetTheme();
              } else if (value == 'save') {
                _applyAndSave();
              }
            },
            itemBuilder: (ctx) => [
              const PopupMenuItem(
                value: 'save',
                child: Row(
                  children: [
                    Icon(Icons.check_rounded, size: 20),
                    SizedBox(width: 10),
                    Text('Save Theme'),
                  ],
                ),
              ),
              const PopupMenuDivider(),
              const PopupMenuItem(
                value: 'reset',
                child: Row(
                  children: [
                    Icon(Icons.restart_alt_rounded, color: Colors.redAccent, size: 20),
                    SizedBox(width: 10),
                    Text('Reset to Defaults', style: TextStyle(color: Colors.redAccent)),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(
          horizontal: ChatySpacing.base,
          vertical: ChatySpacing.md,
        ),
        physics: const BouncingScrollPhysics(),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Preview message bubble live
            Padding(
              padding: const EdgeInsets.only(bottom: ChatySpacing.md),
              child: ChatyCard(
                child: Column(
                  children: [
                    MessageBubble(
                      message: ChatMessage(
                        id: 'prev_1',
                        conversationId: 'c1',
                        senderId: 'contact_1',
                        text: 'Live theme palette preview',
                        createdAt: DateTime.now().subtract(
                          const Duration(minutes: 2),
                        ),
                        deliveryState: DeliveryState.read,
                      ),
                      isMe: false,
                      theme: _current,
                      onLongPress: () {},
                    ),
                    const SizedBox(height: 8),
                    MessageBubble(
                      message: ChatMessage(
                        id: 'prev_2',
                        conversationId: 'c1',
                        senderId: 'user_me',
                        text: 'Warm neutral & high-contrast ready!',
                        createdAt: DateTime.now(),
                        deliveryState: DeliveryState.read,
                      ),
                      isMe: true,
                      theme: _current,
                      onLongPress: () {},
                    ),
                  ],
                ),
              ),
            ),

            // Live Fine-Tuning Controls
            ChatyGroupedSection(
              title: 'Fine-Tuning & Geometry',
              children: [
                Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: ChatySpacing.md,
                    vertical: ChatySpacing.xs,
                  ),
                  child: Column(
                    children: [
                      // Bubble Corner Radius
                      Row(
                        children: [
                          Expanded(
                            child: Text(
                              'Bubble Corner Radius (${_current.cornerRadius.toInt()}px)',
                              style: TextStyle(
                                fontSize: 14,
                                fontWeight: FontWeight.w600,
                                color: context.colors.foreground,
                              ),
                            ),
                          ),
                        ],
                      ),
                      Slider(
                        value: _current.cornerRadius.clamp(4.0, 28.0),
                        min: 4.0,
                        max: 28.0,
                        divisions: 12,
                        label: '${_current.cornerRadius.toInt()}px',
                        onChanged: (val) {
                          setState(() {
                            _current = _current.copyWith(cornerRadius: val);
                          });
                          widget.themeController.updateThemeConfig(_current);
                        },
                      ),

                      // Font Scaling Slider
                      Row(
                        children: [
                          Expanded(
                            child: Text(
                              'Font Scale (${(_current.fontScale * 100).toInt()}%)',
                              style: TextStyle(
                                fontSize: 14,
                                fontWeight: FontWeight.w600,
                                color: context.colors.foreground,
                              ),
                            ),
                          ),
                        ],
                      ),
                      Slider(
                        value: _current.fontScale.clamp(0.85, 1.30),
                        min: 0.85,
                        max: 1.30,
                        divisions: 9,
                        label: '${(_current.fontScale * 100).toInt()}%',
                        onChanged: (val) {
                          setState(() {
                            _current = _current.copyWith(fontScale: val);
                          });
                          widget.themeController.updateThemeConfig(_current);
                        },
                      ),

                      // Spacing Density Slider
                      Row(
                        children: [
                          Expanded(
                            child: Text(
                              'UI Density (${(_current.density * 100).toInt()}%)',
                              style: TextStyle(
                                fontSize: 14,
                                fontWeight: FontWeight.w600,
                                color: context.colors.foreground,
                              ),
                            ),
                          ),
                        ],
                      ),
                      Slider(
                        value: _current.density.clamp(0.8, 1.2),
                        min: 0.8,
                        max: 1.2,
                        divisions: 8,
                        label: '${(_current.density * 100).toInt()}%',
                        onChanged: (val) {
                          setState(() {
                            _current = _current.copyWith(density: val);
                          });
                          widget.themeController.updateThemeConfig(_current);
                        },
                      ),

                      const SizedBox(height: 6),

                      // Delivery Tick Selector
                      Row(
                        children: [
                          Text(
                            'Tick Indicator Style',
                            style: TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w600,
                              color: context.colors.foreground,
                            ),
                          ),
                          const Spacer(),
                          DropdownButton<DeliveryIconStyle>(
                            value: _current.deliveryTickStyle,
                            underline: const SizedBox.shrink(),
                            dropdownColor: context.colors.surface,
                            items: DeliveryIconStyle.values.map((style) {
                              return DropdownMenuItem<DeliveryIconStyle>(
                                value: style,
                                child: Text(
                                  style.displayName,
                                  style: TextStyle(
                                    fontSize: 13,
                                    color: context.colors.foreground,
                                  ),
                                ),
                              );
                            }).toList(),
                            onChanged: (newStyle) {
                              if (newStyle == null) return;
                              setState(() {
                                _current = _current.copyWith(
                                  deliveryTickStyle: newStyle,
                                  tickStyle: newStyle.displayName,
                                );
                              });
                              widget.themeController.updateThemeConfig(_current);
                            },
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),

            const SizedBox(height: ChatySpacing.md),

            // Presets Header & Grid
            ChatyGroupedSection(
              title: 'Theme Presets (${ThemePresets.all.length})',
              children: [
                Padding(
                  padding: const EdgeInsets.all(ChatySpacing.md),
                  child: LayoutBuilder(
                    builder: (context, constraints) {
                      final isWide = constraints.maxWidth >= 600;
                      final crossAxisCount = isWide
                          ? 3
                          : (constraints.maxWidth >= 380 ? 2 : 1);

                      return GridView.builder(
                        shrinkWrap: true,
                        physics: const NeverScrollableScrollPhysics(),
                        itemCount: ThemePresets.all.length,
                        gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                          crossAxisCount: crossAxisCount,
                          mainAxisSpacing: 12,
                          crossAxisSpacing: 12,
                          mainAxisExtent: 240,
                        ),
                        itemBuilder: (context, index) {
                          final preset = ThemePresets.all[index];
                          final isSelected = preset.id == _current.id;

                          return ThemePreviewCard(
                            themeConfig: preset,
                            isSelected: isSelected,
                            onTap: () {
                              setState(() => _current = preset);
                              widget.themeController.updateThemeConfig(preset);
                            },
                          );
                        },
                      );
                    },
                  ),
                ),
              ],
            ),

            const SizedBox(height: ChatySpacing.md),

            // Danger & Reset Section
            Padding(
              padding: const EdgeInsets.symmetric(vertical: ChatySpacing.sm),
              child: OutlinedButton.icon(
                icon: const Icon(Icons.restart_alt_rounded, color: Colors.redAccent),
                label: const Text(
                  'Reset to Default System Theme',
                  style: TextStyle(color: Colors.redAccent, fontWeight: FontWeight.w600),
                ),
                style: OutlinedButton.styleFrom(
                  side: const BorderSide(color: Colors.redAccent, width: 1.2),
                  padding: const EdgeInsets.symmetric(vertical: 14),
                ),
                onPressed: _confirmResetTheme,
              ),
            ),

            const SizedBox(height: ChatySpacing.xxl),
          ],
        ),
      ),
    );
  }
}
