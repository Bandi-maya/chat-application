import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import '../../ui/core/design_system/design_system.dart';
import '../messages/message_bubble.dart';
import '../../domain/models/chat_message.dart';
import '../../ui/core/bubbles/bubble_style_id.dart';
import '../../ui/core/bubbles/bubble_style_preview.dart';
import '../../ui/core/ticks/delivery_icon_style.dart';
import '../../ui/core/ticks/delivery_status_icon.dart';
import '../../ui/core/theme/chaty_theme_manager.dart';
import '../../ui/core/theme/image_theme_generator.dart';

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
          content: Text('Theme exported to ${file.path.split('/').last}'),
          backgroundColor: context.colors.success,
        ),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Export failed: $e')),
      );
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
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Import failed: $e')),
      );
    }
  }

  Future<void> _generateFromImage() async {
    try {
      final result = await FilePicker.pickFile(type: FileType.image);
      if (result == null || result.path == null) return;
      final file = File(result.path!);
      final isDark = _current.brightness == Brightness.dark;
      final generated = await ImageThemeGenerator.generateFromImageFile(file, isDark: isDark);
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

  void _showBubblePicker() {
    final theme = Theme.of(context);
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (ctx) {
        return DraggableScrollableSheet(
          initialChildSize: 0.75,
          minChildSize: 0.5,
          maxChildSize: 0.95,
          builder: (_, scrollController) {
            return Container(
              decoration: BoxDecoration(
                color: theme.cardColor,
                borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
              ),
              child: Column(
                children: [
                  Container(
                    margin: const EdgeInsets.only(top: 10, bottom: 8),
                    width: 36,
                    height: 4,
                    decoration: BoxDecoration(
                      color: theme.colorScheme.onSurfaceVariant.withValues(alpha: 0.3),
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          'Bubble Styles (${BubbleStyleId.values.length})',
                          style: TextStyle(
                            color: theme.colorScheme.onSurface,
                            fontSize: 18,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        IconButton(
                          icon: const Icon(Icons.close_rounded),
                          onPressed: () => Navigator.of(ctx).pop(),
                        ),
                      ],
                    ),
                  ),
                  const Divider(height: 1),
                  Expanded(
                    child: ListView.builder(
                      controller: scrollController,
                      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 12),
                      itemCount: BubbleStyleId.values.length,
                      itemBuilder: (_, idx) {
                        final styleId = BubbleStyleId.values[idx];
                        final isSelected = styleId == _current.bubbleStyle;
                        return BubbleStylePreviewTile(
                          styleId: styleId,
                          label: styleId.displayName,
                          isSelected: isSelected,
                          accentColor: _current.accentColor,
                          onTap: () {
                            setState(() => _current = _current.copyWith(bubbleStyle: styleId));
                            Navigator.of(ctx).pop();
                          },
                        );
                      },
                    ),
                  ),
                ],
              ),
            );
          },
        );
      },
    );
  }

  void _showTickPicker() {
    final theme = Theme.of(context);
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (ctx) {
        return DraggableScrollableSheet(
          initialChildSize: 0.65,
          minChildSize: 0.4,
          maxChildSize: 0.9,
          builder: (_, scrollController) {
            return Container(
              decoration: BoxDecoration(
                color: theme.cardColor,
                borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
              ),
              child: Column(
                children: [
                  Container(
                    margin: const EdgeInsets.only(top: 10, bottom: 8),
                    width: 36,
                    height: 4,
                    decoration: BoxDecoration(
                      color: theme.colorScheme.onSurfaceVariant.withValues(alpha: 0.3),
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          'Delivery Tick Styles (${DeliveryIconStyle.values.length})',
                          style: TextStyle(
                            color: theme.colorScheme.onSurface,
                            fontSize: 18,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        IconButton(
                          icon: const Icon(Icons.close_rounded),
                          onPressed: () => Navigator.of(ctx).pop(),
                        ),
                      ],
                    ),
                  ),
                  const Divider(height: 1),
                  Expanded(
                    child: ListView.builder(
                      controller: scrollController,
                      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 12),
                      itemCount: DeliveryIconStyle.values.length,
                      itemBuilder: (_, idx) {
                        final tickStyle = DeliveryIconStyle.values[idx];
                        final isSelected = tickStyle == _current.deliveryTickStyle;
                        return DeliveryStatusPreviewTile(
                          style: tickStyle,
                          isSelected: isSelected,
                          onTap: () {
                            setState(() => _current = _current.copyWith(deliveryTickStyle: tickStyle));
                            Navigator.of(ctx).pop();
                          },
                          primaryTextColor: _current.primaryTextColor,
                          accentColor: _current.accentColor,
                          unreadColor: _current.secondaryTextColor,
                          readColor: _current.accentColor,
                        );
                      },
                    ),
                  ),
                ],
              ),
            );
          },
        );
      },
    );
  }

  Widget _navModeChip(String label, AppNavigationMode mode) {
    final isSel = _current.navigationMode == mode;
    final themeData = Theme.of(context);
    return ChoiceChip(
      label: Text(label),
      selected: isSel,
      selectedColor: _current.accentColor.withValues(alpha: 0.2),
      backgroundColor: themeData.colorScheme.surface,
      labelStyle: TextStyle(
        color: isSel ? _current.accentColor : themeData.colorScheme.onSurface,
        fontWeight: isSel ? FontWeight.w700 : FontWeight.normal,
        fontSize: 12.5,
      ),
      onSelected: (val) {
        if (val) {
          setState(() => _current = _current.copyWith(navigationMode: mode));
        }
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final themeData = Theme.of(context);

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
                        text: 'Live theme & geometry preview',
                        createdAt: DateTime.now().subtract(const Duration(minutes: 2)),
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
                        text: 'Looks state-of-the-art!',
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

            // Presets
            ChatyGroupedSection(
              title: 'Theme Presets',
              children: [
                Padding(
                  padding: const EdgeInsets.all(ChatySpacing.md),
                  child: SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    physics: const BouncingScrollPhysics(),
                    child: Row(
                      children: ThemePresets.all.map((p) {
                        final isSel = p.id == _current.id;
                        return Padding(
                          padding: const EdgeInsets.only(right: 8.0),
                          child: ChoiceChip(
                            label: Text(p.name),
                            selected: isSel,
                            selectedColor: p.accentColor.withValues(alpha: 0.2),
                            backgroundColor: themeData.colorScheme.surface,
                            onSelected: (val) {
                              if (val) setState(() => _current = p);
                            },
                          ),
                        );
                      }).toList(),
                    ),
                  ),
                ),
              ],
            ),

            const SizedBox(height: ChatySpacing.lg),

            // Discrete Bubble and Tick Pickers
            ChatyGroupedSection(
              title: 'Bubble & Tick Styles',
              children: [
                ChatyListTile(
                  leading: const Icon(Icons.chat_bubble_outline_rounded),
                  title: const Text('Bubble Style Geometry'),
                  subtitle: Text(_current.bubbleStyle.name),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: _showBubblePicker,
                ),
                ChatyListTile(
                  leading: const Icon(Icons.done_all_rounded),
                  title: const Text('Delivery Tick Style'),
                  subtitle: Text(_current.deliveryTickStyle.name),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: _showTickPicker,
                ),
              ],
            ),

            const SizedBox(height: ChatySpacing.lg),

            // Navigation Architecture
            ChatyGroupedSection(
              title: 'Navigation Layout Architecture',
              children: [
                Padding(
                  padding: const EdgeInsets.all(ChatySpacing.md),
                  child: Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      _navModeChip('Bottom Nav Bar', AppNavigationMode.bottomNav),
                      _navModeChip('Top WhatsApp Bar', AppNavigationMode.topWhatsAppBar),
                      _navModeChip('Floating Island Rail', AppNavigationMode.floatingIslandRail),
                      _navModeChip('3D Perspective Drawer', AppNavigationMode.perspective3DDrawer),
                      _navModeChip('Modern Side Menu', AppNavigationMode.modernSideMenu),
                      _navModeChip('Gesture Tabs', AppNavigationMode.gestureTabs),
                    ],
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
