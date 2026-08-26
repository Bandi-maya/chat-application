import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';

import '../../../ui/core/design_system/settings_primitives.dart';
import '../../../ui/core/design_system/components/adaptive_selection_panel.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/theme/app_theme.dart';
import '../../../ui/core/bubbles/bubble_style_id.dart';
import '../../../ui/core/bubbles/bubble_style_registry.dart';
import '../../../ui/core/bubbles/bubble_painter.dart';
import '../../../ui/core/ticks/delivery_icon_style.dart';
import '../../../ui/core/ticks/delivery_status_icon.dart';
import '../../../domain/models/chat_message.dart';

class ConversationSettingsPage extends StatefulWidget {
  final ChatyPreferencesController preferencesController;

  const ConversationSettingsPage({
    super.key,
    required this.preferencesController,
  });

  @override
  State<ConversationSettingsPage> createState() =>
      _ConversationSettingsPageState();
}

class _ConversationSettingsPageState extends State<ConversationSettingsPage> {
  /// Real CONTROL side for wallpaperType 'Image': picks an image via
  /// file_picker and copies it into the app documents directory so it
  /// survives cache cleanup, then persists the path for the consumer.
  Future<void> _pickWallpaperImage() async {
    try {
      final picked = await FilePicker.pickFile(type: FileType.image);
      final sourcePath = picked?.path;
      if (sourcePath == null || sourcePath.isEmpty) return;
      final docs = await getApplicationDocumentsDirectory();
      var ext = sourcePath.contains('.')
          ? sourcePath.split('.').last.toLowerCase()
          : 'png';
      if (!RegExp(r'^[a-z0-9]{2,5}$').hasMatch(ext)) ext = 'png';
      final target = File(
        '${docs.path}/chaty_wallpaper_${DateTime.now().millisecondsSinceEpoch}.$ext',
      );
      await File(sourcePath).copy(target.path);
      if (!mounted) return;
      widget.preferencesController.updateConversation(
        widget.preferencesController.conversation.copyWith(
          wallpaperType: 'Image',
          wallpaperPath: target.path,
        ),
        logTitle: 'Wallpaper Image',
      );
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Could not import that image.')),
      );
    }
  }

  static const List<String> _reactionEmojis = [
    '❤️',
    '👍',
    '🔥',
    '😂',
    '😮',
    '🙏',
  ];

  static const List<String> _wallpaperTypes = [
    'Pattern',
    'Solid',
    'Gradient',
    'Image',
    'ProfileBlur',
  ];

  static const List<double> _playbackSpeeds = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0];

  Future<void> _showBubbleStylePicker(
    BuildContext context,
    String currentStyle,
  ) async {
    final activeId = BubbleStyleIdExtension.fromString(currentStyle);
    final theme = Theme.of(context);

    final selected = await AdaptiveSelectionPanel.show<BubbleStyleId>(
      context: context,
      title: 'Bubble Style Geometry',
      subtitle: 'Choose from 48 discrete bubble contours',
      selectedValue: activeId,
      showApplyButton: true,
      preferCenteredDialog: true,
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
                fillColor: theme.colorScheme.primary,
                strokeColor: theme.colorScheme.primary.withValues(alpha: 0.3),
                accentColor: theme.colorScheme.primary,
              ),
            ),
          ),
        );
      }).toList(),
    );

    if (selected != null) {
      widget.preferencesController.updateConversation(
        widget.preferencesController.conversation.copyWith(
          bubbleStyle: selected.displayName,
        ),
        logTitle: 'Bubble Style',
      );
    }
  }

  Future<void> _showTickStylePicker(
    BuildContext context,
    String currentTick,
  ) async {
    final activeStyle = DeliveryIconStyleExtension.fromString(currentTick);
    final theme = Theme.of(context);

    final selected = await AdaptiveSelectionPanel.show<DeliveryIconStyle>(
      context: context,
      title: 'Delivery Tick Style',
      subtitle: 'Choose from 16 custom vector delivery ticks',
      selectedValue: activeStyle,
      showApplyButton: true,
      preferCenteredDialog: true,
      options: DeliveryIconStyle.values.map((tickStyle) {
        return SelectionOptionItem<DeliveryIconStyle>(
          value: tickStyle,
          title: tickStyle.displayName,
          subtitle: 'Vector status glyph',
          preview: DeliveryStatusIcon(
            style: tickStyle,
            state: DeliveryState.read,
            unreadColor: theme.colorScheme.onSurfaceVariant.withValues(
              alpha: 0.7,
            ),
            readColor: theme.colorScheme.primary,
            size: 18,
          ),
        );
      }).toList(),
    );

    if (selected != null) {
      widget.preferencesController.updateConversation(
        widget.preferencesController.conversation.copyWith(
          tickStyle: selected.displayName,
        ),
        logTitle: 'Tick Style',
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final conv = widget.preferencesController.conversation;
    final activeBubbleId = BubbleStyleIdExtension.fromString(conv.bubbleStyle);
    final activeTickStyle = DeliveryIconStyleExtension.fromString(
      conv.tickStyle,
    );

    return ChatySettingsPage(
      title: 'Conversation Screen Settings',
      subtitle: 'Bubbles, Ticks, Action Bar, Wallpaper & Sidebar',
      children: [
        // Live Preview Card at Top
        ChatyPreviewCard(
          title: 'Live Conversation Bubble Preview',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                'Bubble: ${conv.bubbleStyle} • Ticks: ${conv.tickStyle} • Wallpaper: ${conv.wallpaperType}',
                style: TextStyle(
                  fontSize: 12,
                  color: Theme.of(context).textTheme.bodySmall?.color,
                ),
              ),
              const SizedBox(height: 10),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: context.colors.surface,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Column(
                  children: [
                    // Incoming Bubble Preview
                    Align(
                      alignment: Alignment.centerLeft,
                      child: Container(
                        margin: BubbleStyleRegistry.getGeometry(
                          activeBubbleId,
                        ).bubbleMargin,
                        child: CustomPaint(
                          painter: BubblePainter(
                            styleId: activeBubbleId,
                            isMe: false,
                            fillColor: context.colors.surfaceSecondary,
                            strokeColor: context.colors.primary.withValues(
                              alpha: 0.4,
                            ),
                            accentColor: context.colors.primary,
                          ),
                          child: Padding(
                            padding: BubbleStyleRegistry.getGeometry(
                              activeBubbleId,
                            ).contentPadding,
                            child: Text(
                              'Incoming message preview',
                              style: TextStyle(
                                color: context.colors.onSurface,
                                fontSize: 13,
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 8),
                    // Outgoing Bubble Preview with Selected Tick Style
                    Align(
                      alignment: Alignment.centerRight,
                      child: Container(
                        margin: BubbleStyleRegistry.getGeometry(
                          activeBubbleId,
                        ).bubbleMargin,
                        child: CustomPaint(
                          painter: BubblePainter(
                            styleId: activeBubbleId,
                            isMe: true,
                            fillColor: context.colors.primary,
                            strokeColor: context.colors.primary.withValues(
                              alpha: 0.4,
                            ),
                            accentColor: context.colors.primary,
                          ),
                          child: Padding(
                            padding: BubbleStyleRegistry.getGeometry(
                              activeBubbleId,
                            ).contentPadding,
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Text(
                                  'Outgoing reply!',
                                  style: TextStyle(
                                    color: context.colors.onPrimary,
                                    fontSize: 13,
                                  ),
                                ),
                                const SizedBox(width: 8),
                                DeliveryStatusIcon(
                                  style: activeTickStyle,
                                  state: DeliveryState.read,
                                  unreadColor: context.colors.onPrimary
                                      .withValues(alpha: 0.7),
                                  readColor: Colors.white,
                                  size: 16,
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),

        // Discrete Bubbles and Ticks Section
        ChatySettingsSection(
          title: 'Bubbles & Ticks',
          description:
              'Choose from 48 discrete bubble contours and 16 custom vector delivery ticks.',
          children: [
            ListTile(
              leading: const Icon(Icons.chat_bubble_outline_rounded),
              title: const Text('Bubble Style'),
              subtitle: Text(conv.bubbleStyle),
              trailing: const Icon(Icons.chevron_right_rounded),
              onTap: () => _showBubbleStylePicker(context, conv.bubbleStyle),
            ),
            ListTile(
              leading: const Icon(Icons.done_all_rounded),
              title: const Text('Delivery Tick Style'),
              subtitle: Text(conv.tickStyle),
              trailing: const Icon(Icons.chevron_right_rounded),
              onTap: () => _showTickStylePicker(context, conv.tickStyle),
            ),
          ],
        ),

        // Quick Contact Sidebar
        ChatySettingsSection(
          title: 'Quick Contact Sidebar',
          description:
              'Docked sidebar panel for rapid contact navigation in chat.',
          children: [
            ChatySwitchTile(
              icon: Icons.dock_rounded,
              iconColor: context.colors.primary,
              title: 'Enable Quick Contact Sidebar',
              subtitle:
                  'Show quick contact switcher panel inside active conversations',
              value: conv.enableQuickContactSidebar,
              onChanged: (val) {
                widget.preferencesController.updateConversation(
                  conv.copyWith(enableQuickContactSidebar: val),
                  logTitle: 'Quick Contact Sidebar',
                );
              },
            ),
            if (conv.enableQuickContactSidebar) ...[
              ChatyChoiceTile<String>(
                title: 'Sidebar Position',
                requireApply: true,
                options: const ['Left', 'Right'],
                selectedOption: conv.sidebarPosition,
                optionLabel: (s) => s,
                onSelected: (pos) {
                  widget.preferencesController.updateConversation(
                    conv.copyWith(sidebarPosition: pos),
                    logTitle: 'Sidebar Position',
                  );
                },
              ),
              ChatySliderTile(
                icon: Icons.opacity_rounded,
                title: 'Sidebar Opacity',
                value: conv.sidebarOpacity,
                min: 0.3,
                max: 1.0,
                divisions: 14,
                valueFormatter: (v) => '${(v * 100).toInt()}%',
                onChanged: (v) {
                  widget.preferencesController.updateConversation(
                    conv.copyWith(sidebarOpacity: v),
                    logTitle: 'Sidebar Opacity',
                  );
                },
              ),
            ],
          ],
        ),

        // Interaction & Reactions
        ChatySettingsSection(
          title: 'Reactions & Interaction Menus',
          children: [
            ChatySwitchTile(
              icon: Icons.auto_awesome_rounded,
              iconColor: context.colors.primary,
              title: 'Animated Emojis',
              subtitle:
                  'Play vector animations for emojis and reactions throughout chats',
              value: conv.enableAnimatedEmojis,
              onChanged: (val) {
                widget.preferencesController.updateConversation(
                  conv.copyWith(enableAnimatedEmojis: val),
                  logTitle: 'Animated Emojis',
                );
              },
            ),
            ChatySwitchTile(
              icon: Icons.touch_app_rounded,
              iconColor: context.colors.accent,
              title: 'iOS-Style Context Popup Menu',
              subtitle:
                  'Use modern iOS-style floating menu on message long-press',
              value: conv.iosStylePopupMenu,
              onChanged: (val) {
                widget.preferencesController.updateConversation(
                  conv.copyWith(iosStylePopupMenu: val),
                  logTitle: 'iOS Popup Menu',
                );
              },
            ),
            ChatyChoiceTile<String>(
              title: 'Double-Tap Reaction Emoji',
              requireApply: true,
              options: _reactionEmojis,
              selectedOption: conv.doubleTapReactionEmoji,
              optionLabel: (s) => s,
              onSelected: (emoji) {
                widget.preferencesController.updateConversation(
                  conv.copyWith(doubleTapReactionEmoji: emoji),
                  logTitle: 'Double Tap Reaction',
                );
              },
            ),
          ],
        ),

        // Conversation Wallpaper
        ChatySettingsSection(
          title: 'Wallpaper & Audio Playback',
          children: [
            ChatyChoiceTile<String>(
              title: 'Background Wallpaper',
              requireApply: true,
              options: _wallpaperTypes,
              selectedOption: conv.wallpaperType,
              optionLabel: (s) => s,
              onSelected: (wp) {
                widget.preferencesController.updateConversation(
                  conv.copyWith(wallpaperType: wp),
                  logTitle: 'Wallpaper Type',
                );
              },
            ),
            if (conv.wallpaperType == 'Image') ...[
              ChatySettingsTile(
                icon: Icons.image_rounded,
                iconColor: context.colors.primary,
                title: 'Choose background image',
                subtitle: conv.wallpaperPath.isEmpty
                    ? 'No image selected yet'
                    : 'Custom image imported',
                onTap: () => _pickWallpaperImage(),
              ),
              if (conv.wallpaperPath.isNotEmpty)
                ChatySettingsTile(
                  icon: Icons.delete_sweep_rounded,
                  iconColor: context.colors.error,
                  title: 'Remove custom image',
                  subtitle: 'Fall back to the themed gradient background',
                  onTap: () {
                    widget.preferencesController.updateConversation(
                      conv.copyWith(wallpaperPath: ''),
                      logTitle: 'Wallpaper Image Removed',
                    );
                  },
                ),
            ],
            ChatyChoiceTile<double>(
              title: 'Voice Note Speed',
              requireApply: true,
              options: _playbackSpeeds,
              selectedOption: conv.voicePlaybackSpeed,
              optionLabel: (v) => '${v}x',
              onSelected: (speed) {
                widget.preferencesController.updateConversation(
                  conv.copyWith(voicePlaybackSpeed: speed),
                  logTitle: 'Voice Speed',
                );
              },
            ),
          ],
        ),
      ],
    );
  }
}
