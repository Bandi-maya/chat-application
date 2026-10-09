import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import '../../ui/core/design_system/design_system.dart';
import '../messages/message_bubble.dart';
import '../../domain/models/chat_message.dart';
import '../../ui/core/theme/chaty_theme_manager.dart';
import '../../ui/core/theme/image_theme_generator.dart';
import '../../ui/core/theme/theme_preview_card.dart';

class ThemeEditorScreen extends StatefulWidget {
  final ThemeController themeController;

  const ThemeEditorScreen({super.key, required this.themeController});

  @override
  State<ThemeEditorScreen> createState() => _ThemeEditorScreenState();
}

class _ThemeEditorScreenState extends State<ThemeEditorScreen> {
  static const List<({String name, Color accent, Color link, Color outgoing})>
      _paletteOptions = [
    (
      name: 'Ocean Blue',
      accent: Color(0xFF229ED9),
      link: Color(0xFF60A5FA),
      outgoing: Color(0xFF176B9A),
    ),
    (
      name: 'Emerald',
      accent: Color(0xFF10B981),
      link: Color(0xFF34D399),
      outgoing: Color(0xFF047857),
    ),
    (
      name: 'Violet',
      accent: Color(0xFF8B5CF6),
      link: Color(0xFFA78BFA),
      outgoing: Color(0xFF6D28D9),
    ),
    (
      name: 'Sunset',
      accent: Color(0xFFF43F5E),
      link: Color(0xFFFB923C),
      outgoing: Color(0xFFBE123C),
    ),
    (
      name: 'Rose',
      accent: Color(0xFFEC4899),
      link: Color(0xFFF472B6),
      outgoing: Color(0xFFBE185D),
    ),
    (
      name: 'Amber',
      accent: Color(0xFFF59E0B),
      link: Color(0xFFFBBF24),
      outgoing: Color(0xFFB45309),
    ),
    (
      name: 'Monochrome',
      accent: Color(0xFFFFFFFF),
      link: Color(0xFFE4E4E7),
      outgoing: Color(0xFF27272A),
    ),
  ];
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
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Imported "${theme.name}" successfully!'),
          backgroundColor: context.colors.success,
        ),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Import failed: $e')));
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
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: const Text('Palette extracted from image!'),
          backgroundColor: context.colors.success,
        ),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Could not generate theme from image: $e')),
      );
    }
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
            icon: const Icon(Icons.file_download_outlined),
            tooltip: 'Import Theme JSON',
            onPressed: _importTheme,
          ),
          IconButton(
            icon: const Icon(Icons.file_upload_outlined),
            tooltip: 'Export Theme JSON',
            onPressed: _exportTheme,
          ),
          IconButton(
            icon: const Icon(Icons.check_rounded),
            tooltip: 'Save Theme',
            onPressed: _applyAndSave,
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

            // Named color combinations. These edit the draft palette and
            // are applied across Chaty only when Save Theme is pressed.
            ChatyGroupedSection(
              title: 'Color Combinations',
              description: 'Choose a coordinated accent, link and outgoing-bubble palette.',
              children: [
                Padding(
                  padding: const EdgeInsets.all(ChatySpacing.md),
                  child: Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: _paletteOptions.map((palette) {
                      final selected =
                          _current.accentColor == palette.accent &&
                          _current.linkColor == palette.link &&
                          _current.outgoingBubbleColor == palette.outgoing;
                      return InkWell(
                        borderRadius: BorderRadius.circular(12),
                        onTap: () => setState(() {
                          _current = _current.copyWith(
                            accentColor: palette.accent,
                            linkColor: palette.link,
                            outgoingBubbleColor: palette.outgoing,
                          );
                        }),
                        child: AnimatedContainer(
                          duration: const Duration(milliseconds: 180),
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                          decoration: BoxDecoration(
                            color: selected
                                ? palette.accent.withValues(alpha: 0.12)
                                : context.colors.surfaceSecondary,
                            borderRadius: BorderRadius.circular(12),
                            border: Border.all(
                              color: selected ? palette.accent : context.colors.border,
                              width: selected ? 1.8 : 1,
                            ),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Container(
                                width: 18,
                                height: 18,
                                decoration: BoxDecoration(
                                  color: palette.accent,
                                  shape: BoxShape.circle,
                                  border: Border.all(color: context.colors.border),
                                ),
                              ),
                              const SizedBox(width: 8),
                              Text(
                                palette.name,
                                style: TextStyle(
                                  fontWeight: selected ? FontWeight.w800 : FontWeight.w600,
                                  color: context.colors.foreground,
                                ),
                              ),
                              if (selected) ...[
                                const SizedBox(width: 6),
                                Icon(Icons.check_circle_rounded, color: palette.accent, size: 17),
                              ],
                            ],
                          ),
                        ),
                      );
                    }).toList(growable: false),
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
                          mainAxisExtent: 220,
                        ),
                        itemBuilder: (context, index) {
                          final preset = ThemePresets.all[index];
                          final isSelected = preset.id == _current.id;

                          return ThemePreviewCard(
                            themeConfig: preset,
                            isSelected: isSelected,
                            onTap: () {
                              setState(() => _current = preset);
                            },
                          );
                        },
                      );
                    },
                  ),
                ),
              ],
            ),

            const SizedBox(height: ChatySpacing.xxl),
          ],
        ),
      ),
    );
  }
}
