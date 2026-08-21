import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';

import '../../../ui/core/design_system/settings_primitives.dart';
import '../../../ui/core/controllers/preferences_controller.dart';

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
      final picked = await FilePicker.pickFile(
        type: FileType.image,
      );
      final sourcePath = picked?.path;
      if (sourcePath == null || sourcePath.isEmpty) return;
      final docs = await getApplicationDocumentsDirectory();
      var ext = sourcePath.contains('.') ? sourcePath.split('.').last.toLowerCase() : 'png';
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
  static const List<String> _bubbleShapes = [
    'Rounded',
    'Compact',
    'Classic',
    'Tail',
    'Tail-less',
    'Squircle',
    'Minimal',
    'Card',
  ];

  static const List<String> _tickStyles = [
    'Default',
    'Double Check',
    'iOS Style',
    'Minimal',
    'Neon',
  ];

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

  @override
  Widget build(BuildContext context) {
    final conv = widget.preferencesController.conversation;

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
                'Shape: ${conv.bubbleShape} • Radius: ${conv.bubbleRadius.toInt()}px • Ticks: ${conv.tickStyle} • Wallpaper: ${conv.wallpaperType}',
                style: TextStyle(
                  fontSize: 12,
                  color: Theme.of(context).textTheme.bodySmall?.color,
                ),
              ),
              const SizedBox(height: 8),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Theme.of(context).cardColor,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: Theme.of(
                      context,
                    ).dividerColor.withValues(alpha: 0.2),
                  ),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Align(
                      alignment: Alignment.centerLeft,
                      child: Container(
                        padding: EdgeInsets.all(conv.bubblePadding),
                        decoration: BoxDecoration(
                          color: Theme.of(context).colorScheme.surface,
                          borderRadius: BorderRadius.circular(
                            conv.bubbleRadius,
                          ),
                        ),
                        child: const Text(
                          'Incoming message sample with active styling.',
                          style: TextStyle(fontSize: 13),
                        ),
                      ),
                    ),
                    const SizedBox(height: 8),
                    Align(
                      alignment: Alignment.centerRight,
                      child: Container(
                        padding: EdgeInsets.all(conv.bubblePadding),
                        decoration: BoxDecoration(
                          color: Theme.of(context).colorScheme.primary,
                          borderRadius: BorderRadius.circular(
                            conv.bubbleRadius,
                          ),
                        ),
                        child: const Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text(
                              'Outgoing reply!',
                              style: TextStyle(
                                color: Colors.white,
                                fontSize: 13,
                              ),
                            ),
                            SizedBox(width: 6),
                            Icon(
                              Icons.done_all_rounded,
                              size: 14,
                              color: Colors.cyanAccent,
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),

        // Bubbles and Ticks Section
        ChatySettingsSection(
          title: 'Bubbles & Ticks',
          description:
              'Customize chat bubble geometry, colors, padding, and tick markers.',
          children: [
            ChatyChoiceTile<String>(
              title: 'Bubble Shape Geometry',
              options: _bubbleShapes,
              selectedOption: conv.bubbleShape,
              optionLabel: (s) => s,
              onSelected: (shape) {
                widget.preferencesController.updateConversation(
                  conv.copyWith(bubbleShape: shape),
                  logTitle: 'Bubble Shape',
                );
              },
            ),
            ChatySliderTile(
              icon: Icons.rounded_corner_rounded,
              title: 'Bubble Corner Radius',
              value: conv.bubbleRadius,
              min: 4.0,
              max: 24.0,
              divisions: 20,
              valueFormatter: (v) => '${v.toInt()}px',
              onChanged: (v) {
                widget.preferencesController.updateConversation(
                  conv.copyWith(bubbleRadius: v),
                  logTitle: 'Bubble Radius',
                );
              },
            ),
            ChatyChoiceTile<String>(
              title: 'Delivery Tick Style',
              options: _tickStyles,
              selectedOption: conv.tickStyle,
              optionLabel: (s) => s,
              onSelected: (tick) {
                widget.preferencesController.updateConversation(
                  conv.copyWith(tickStyle: tick),
                  logTitle: 'Tick Style',
                );
              },
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
              iconColor: Colors.tealAccent,
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
              icon: Icons.touch_app_rounded,
              iconColor: Colors.amberAccent,
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
                iconColor: Colors.pinkAccent,
                title: 'Choose background image',
                subtitle: conv.wallpaperPath.isEmpty
                    ? 'No image selected yet'
                    : 'Custom image imported',
                onTap: () => _pickWallpaperImage(),
              ),
              if (conv.wallpaperPath.isNotEmpty)
                ChatySettingsTile(
                  icon: Icons.delete_sweep_rounded,
                  iconColor: Colors.redAccent,
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
