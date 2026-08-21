import 'package:flutter/material.dart';
import '../../../ui/core/theme/theme_config.dart';
import '../../../ui/core/theme/theme_presets.dart';
import '../../../ui/core/theme/theme_controller.dart';
import '../../ui/core/design_system/design_system.dart';
import '../messages/message_bubble.dart';
import '../../domain/models/chat_message.dart';

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
      const SnackBar(
        content: Text('Theme customization saved!'),
        backgroundColor: Color(0xFF10B981),
      ),
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
    final theme = _current;
    final themeData = Theme.of(context);

    return ChatyScaffold(
      appBar: ChatyAppBar(
        title: 'Theme & Appearance Editor',
        leading: const ChatyBackButton(),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: ChatySpacing.sm),
            child: ChatyPrimaryButton(
              text: 'Save',
              width: 72,
              height: 34,
              onPressed: _applyAndSave,
            ),
          ),
        ],
      ),
      body: SingleChildScrollView(
        physics: const BouncingScrollPhysics(),
        padding: const EdgeInsets.symmetric(
          horizontal: ChatySpacing.base,
          vertical: ChatySpacing.md,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Live Interactive Chat Preview Box
            ChatyGroupedSection(
              title: 'Live Preview',
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(
                    vertical: ChatySpacing.md,
                    horizontal: ChatySpacing.xs,
                  ),
                  decoration: BoxDecoration(
                    color: theme.backgroundColor,
                    borderRadius: BorderRadius.circular(ChatyRadius.md),
                  ),
                  child: Column(
                    children: [
                      MessageBubble(
                        message: ChatMessage(
                          id: 'prev_1',
                          conversationId: 'prev',
                          senderId: 'other',
                          text:
                              'Hey Alex! How does this custom bubble palette look?',
                          createdAt: DateTime.now().subtract(
                            const Duration(minutes: 5),
                          ),
                        ),
                        isMe: false,
                        theme: theme,
                        senderName: 'Dr. Elena Rostova',
                        onLongPress: () {},
                      ),
                      MessageBubble(
                        message: ChatMessage(
                          id: 'prev_2',
                          conversationId: 'prev',
                          senderId: 'me',
                          text:
                              'It looks crisp and accessible! Full contrast guaranteed.',
                          createdAt: DateTime.now().subtract(
                            const Duration(minutes: 4),
                          ),
                          deliveryState: DeliveryState.read,
                        ),
                        isMe: true,
                        theme: theme,
                        onLongPress: () {},
                      ),
                    ],
                  ),
                ),
              ],
            ),

            // Contrast Warning Card (Accessibility check)
            if (theme.hasContrastIssue) ...[
              const SizedBox(height: ChatySpacing.md),
              Container(
                padding: const EdgeInsets.all(ChatySpacing.md),
                decoration: BoxDecoration(
                  color: const Color(0xFFEF4444).withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(ChatyRadius.md),
                  border: Border.all(
                    color: const Color(0xFFEF4444).withValues(alpha: 0.4),
                  ),
                ),
                child: const Row(
                  children: [
                    Icon(
                      Icons.warning_amber_rounded,
                      color: Color(0xFFEF4444),
                      size: 20,
                    ),
                    SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        'Warning: The current color combination falls below WCAG contrast guidelines.',
                        style: TextStyle(
                          color: Color(0xFFFCA5A5),
                          fontSize: 12.5,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],

            const SizedBox(height: ChatySpacing.lg),

            // Preset Selector
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
                            labelStyle: TextStyle(
                              color: isSel
                                  ? p.accentColor
                                  : themeData.colorScheme.onSurface,
                              fontWeight: isSel
                                  ? FontWeight.w600
                                  : FontWeight.normal,
                              fontSize: 13,
                            ),
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

            // UI Layout Presets
            ChatyGroupedSection(
              title: 'UI Layout Preset',
              children: [
                Padding(
                  padding: const EdgeInsets.all(ChatySpacing.md),
                  child: Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: UILayoutMode.values.map((mode) {
                      final isSel = _current.layoutMode == mode;
                      return ChoiceChip(
                        label: Text(mode.name.toUpperCase()),
                        selected: isSel,
                        selectedColor: theme.accentColor.withValues(alpha: 0.2),
                        onSelected: (val) {
                          if (val) {
                            setState(() {
                              _current = _current.copyWith(layoutMode: mode);
                              if (mode == UILayoutMode.compact) {
                                _current = _current.copyWith(
                                  density: 0.85,
                                  fontScale: 0.9,
                                );
                              } else if (mode == UILayoutMode.expressive) {
                                _current = _current.copyWith(
                                  density: 1.15,
                                  cornerRadius: 20,
                                );
                              }
                            });
                          }
                        },
                      );
                    }).toList(),
                  ),
                ),
              ],
            ),

            const SizedBox(height: ChatySpacing.lg),

            // Navigation Shell Architecture (5 Archetypes)
            ChatyGroupedSection(
              title: 'Navigation Layout Architecture',
              description: 'Switch between the 5 distinct mobile navigation archetypes',
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

            const SizedBox(height: ChatySpacing.lg),

            // Bubble Geometry & Style
            ChatyGroupedSection(
              title: 'Bubble Shape & Geometry',
              children: [
                Padding(
                  padding: const EdgeInsets.all(ChatySpacing.md),
                  child: Wrap(
                    spacing: 8,
                    children: AppBubbleStyle.values.map((s) {
                      final isSel = _current.bubbleStyle == s;
                      return ChoiceChip(
                        label: Text(s.name),
                        selected: isSel,
                        selectedColor: theme.accentColor.withValues(alpha: 0.2),
                        onSelected: (val) {
                          if (val) {
                            setState(
                              () =>
                                  _current = _current.copyWith(bubbleStyle: s),
                            );
                          }
                        },
                      );
                    }).toList(),
                  ),
                ),
              ],
            ),

            const SizedBox(height: ChatySpacing.lg),

            // Sliders (Corner radius, Font scaling, Density)
            ChatyGroupedSection(
              title: 'Geometry & Typography Sliders',
              children: [
                Padding(
                  padding: const EdgeInsets.all(ChatySpacing.md),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Corner Radius (${_current.cornerRadius.toInt()}px)',
                        style: TextStyle(
                          color: themeData.colorScheme.onSurface,
                          fontSize: 13.5,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      Slider(
                        value: _current.cornerRadius,
                        min: 4,
                        max: 28,
                        divisions: 12,
                        activeColor: theme.accentColor,
                        onChanged: (v) => setState(
                          () => _current = _current.copyWith(cornerRadius: v),
                        ),
                      ),
                      const SizedBox(height: ChatySpacing.sm),
                      Text(
                        'Font Scale (${_current.fontScale.toStringAsFixed(2)}x)',
                        style: TextStyle(
                          color: themeData.colorScheme.onSurface,
                          fontSize: 13.5,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      Slider(
                        value: _current.fontScale,
                        min: 0.85,
                        max: 1.3,
                        divisions: 9,
                        activeColor: theme.accentColor,
                        onChanged: (v) => setState(
                          () => _current = _current.copyWith(fontScale: v),
                        ),
                      ),
                      const SizedBox(height: ChatySpacing.sm),
                      Text(
                        'Message Density (${_current.density.toStringAsFixed(2)}x)',
                        style: TextStyle(
                          color: themeData.colorScheme.onSurface,
                          fontSize: 13.5,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      Slider(
                        value: _current.density,
                        min: 0.7,
                        max: 1.4,
                        divisions: 7,
                        activeColor: theme.accentColor,
                        onChanged: (v) => setState(
                          () => _current = _current.copyWith(density: v),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),

            const SizedBox(height: ChatySpacing.lg),

            // Navigation Modes
            ChatyGroupedSection(
              title: 'Navigation Pattern',
              children: [
                Padding(
                  padding: const EdgeInsets.all(ChatySpacing.md),
                  child: SegmentedButton<AppNavigationMode>(
                    segments: const [
                      ButtonSegment(
                        value: AppNavigationMode.bottomNav,
                        label: Text('Bottom Nav'),
                      ),
                      ButtonSegment(
                        value: AppNavigationMode.compactRail,
                        label: Text('Rail'),
                      ),
                      ButtonSegment(
                        value: AppNavigationMode.gestureTabs,
                        label: Text('Tabs'),
                      ),
                    ],
                    selected: {_current.navigationMode},
                    onSelectionChanged: (val) {
                      setState(
                        () => _current = _current.copyWith(
                          navigationMode: val.first,
                        ),
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

