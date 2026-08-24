import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import '../../ui/core/design_system/design_system.dart';
import '../../ui/core/design_system/components/adaptive_selection_panel.dart';
import '../messages/message_bubble.dart';
import '../../domain/models/chat_message.dart';
import '../../ui/core/bubbles/bubble_style_id.dart';
import '../../ui/core/bubbles/bubble_painter.dart';
import '../../ui/core/ticks/delivery_icon_style.dart';
import '../../ui/core/ticks/delivery_status_icon.dart';
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

            // Presets Header & Grid
            ChatyGroupedSection(
              title: 'Theme Presets (${ThemePresets.all.length})',
              children: [
                Padding(
                  padding: const EdgeInsets.all(ChatySpacing.md),
                  child: LayoutBuilder(
                    builder: (context, constraints) {
                      final isWide = constraints.maxWidth >= 600;
                      final crossAxisCount = isWide ? 3 : (constraints.maxWidth >= 380 ? 2 : 1);

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

            const SizedBox(height: ChatySpacing.lg),

            // Discrete Bubble and Tick Pickers
            ChatyGroupedSection(
              title: 'Bubble & Tick Styles',
              children: [
                ChatyListTile(
                  leading: const Icon(Icons.chat_bubble_outline_rounded),
                  title: const Text('Bubble Style Geometry'),
                  subtitle: Text(_current.bubbleStyle.displayName),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: () async {
                    final selected = await AdaptiveSelectionPanel.show<BubbleStyleId>(
                      context: context,
                      title: 'Bubble Style Geometry',
                      subtitle: 'Choose from 48 discrete bubble contours',
                      selectedValue: _current.bubbleStyle,
                      options: BubbleStyleId.values.map((styleId) {
                        return SelectionOptionItem<BubbleStyleId>(
                          value: styleId,
                          title: styleId.displayName,
                          subtitle: 'Custom shape geometry',
                          preview: SizedBox(
                            width: 60,
                            height: 32,
                            child: CustomPaint(
                              painter: BubblePainter(
                                styleId: styleId,
                                isMe: true,
                                fillColor: _current.accentColor,
                                strokeColor: _current.accentColor.withValues(alpha: 0.3),
                                accentColor: _current.accentColor,
                              ),
                            ),
                          ),
                        );
                      }).toList(),
                    );
                    if (selected != null) {
                      setState(() => _current = _current.copyWith(bubbleStyle: selected));
                    }
                  },
                ),
                ChatyListTile(
                  leading: const Icon(Icons.done_all_rounded),
                  title: const Text('Delivery Tick Style'),
                  subtitle: Text(_current.deliveryTickStyle.displayName),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: () async {
                    final selected = await AdaptiveSelectionPanel.show<DeliveryIconStyle>(
                      context: context,
                      title: 'Delivery Tick Style',
                      subtitle: 'Choose from 16 custom vector delivery ticks',
                      selectedValue: _current.deliveryTickStyle,
                      options: DeliveryIconStyle.values.map((tickStyle) {
                        return SelectionOptionItem<DeliveryIconStyle>(
                          value: tickStyle,
                          title: tickStyle.displayName,
                          subtitle: 'Vector status glyph',
                          preview: DeliveryStatusIcon(
                            style: tickStyle,
                            state: DeliveryState.read,
                            unreadColor: _current.secondaryTextColor,
                            readColor: _current.accentColor,
                            size: 18,
                          ),
                        );
                      }).toList(),
                    );
                    if (selected != null) {
                      setState(() => _current = _current.copyWith(deliveryTickStyle: selected));
                    }
                  },
                ),
              ],
            ),

            const SizedBox(height: ChatySpacing.lg),

            // Navigation Architecture Selection
            ChatyGroupedSection(
              title: 'Navigation Layout Architecture',
              children: [
                ChatyListTile(
                  leading: const Icon(Icons.dock_rounded),
                  title: const Text('Navigation Bar Layout'),
                  subtitle: Text(_current.navigationMode.name),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: () async {
                    final selected = await AdaptiveSelectionPanel.show<AppNavigationMode>(
                      context: context,
                      title: 'Navigation Layout Architecture',
                      subtitle: 'Select your preferred main screen navigation structure',
                      selectedValue: _current.navigationMode,
                      options: [
                        const SelectionOptionItem(
                          value: AppNavigationMode.bottomNav,
                          title: 'Bottom Navigation Bar',
                          subtitle: 'Classic bottom tabs with animated indicator',
                          leadingIcon: Icons.call_to_action_outlined,
                        ),
                        const SelectionOptionItem(
                          value: AppNavigationMode.topWhatsAppBar,
                          title: 'Top WhatsApp Bar',
                          subtitle: 'WhatsApp-style top tab bar with camera shortcut',
                          leadingIcon: Icons.table_rows_outlined,
                        ),
                        const SelectionOptionItem(
                          value: AppNavigationMode.floatingIslandRail,
                          title: 'Floating Island Rail',
                          subtitle: 'Detached floating navigation capsule dock',
                          leadingIcon: Icons.lens_blur_rounded,
                        ),
                        const SelectionOptionItem(
                          value: AppNavigationMode.perspective3DDrawer,
                          title: '3D Perspective Drawer',
                          subtitle: 'Spatial 3D folding side menu panel',
                          leadingIcon: Icons.view_in_ar_rounded,
                        ),
                        const SelectionOptionItem(
                          value: AppNavigationMode.modernSideMenu,
                          title: 'Modern Side Menu',
                          subtitle: 'Sleek swipeable navigation side drawer',
                          leadingIcon: Icons.menu_open_rounded,
                        ),
                        const SelectionOptionItem(
                          value: AppNavigationMode.gestureTabs,
                          title: 'Gesture Tabs',
                          subtitle: 'Full gesture swipe-based screen transitions',
                          leadingIcon: Icons.swipe_rounded,
                        ),
                      ],
                    );
                    if (selected != null) {
                      setState(() => _current = _current.copyWith(navigationMode: selected));
                    }
                  },
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
