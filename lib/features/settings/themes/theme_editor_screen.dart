import 'package:flutter/material.dart';
import '../../../../ui/core/theme/theme_config.dart';
import '../../../../ui/core/theme/theme_controller.dart';
import '../../../../ui/core/theme/theme_presets.dart';
import '../../../ui/core/design_system/settings_primitives.dart';
import '../../../ui/core/design_system/color_picker.dart';

class ChatyThemeEditorScreen extends StatefulWidget {
  final ThemeController themeController;

  const ChatyThemeEditorScreen({super.key, required this.themeController});

  @override
  State<ChatyThemeEditorScreen> createState() => _ChatyThemeEditorScreenState();
}

class _ChatyThemeEditorScreenState extends State<ChatyThemeEditorScreen> {
  late ThemeConfig _editingTheme;

  @override
  void initState() {
    super.initState();
    _editingTheme = widget.themeController.globalTheme;
  }

  void _applyTheme(ThemeConfig config) {
    widget.themeController.setGlobalTheme(config);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('Applied "${config.name}" theme!'),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  void _pickColor(
    String name,
    Color current,
    ValueChanged<Color> onSelected, {
    Color? bgContext,
  }) async {
    final picked = await ChatyColorPickerModal.show(
      context,
      title: name,
      currentColor: current,
      backgroundContextColor: bgContext,
    );
    if (picked != null) {
      onSelected(picked);
      setState(() {
        _editingTheme = _editingTheme.copyWith();
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return ChatySettingsPage(
      title: 'Chaty Themes & Color Engine',
      subtitle: 'Presets, Custom Themes & Token Customization',
      children: [
        // Presets Carousel Section
        ChatySettingsSection(
          title: 'Bundled Theme Presets',
          description:
              'Select a high-craft preset optimized for dark & light environments.',
          children: [
            SizedBox(
              height: 110,
              child: ListView.separated(
                padding: const EdgeInsets.all(12),
                scrollDirection: Axis.horizontal,
                itemCount: ThemePresets.all.length,
                separatorBuilder: (context, index) => const SizedBox(width: 12),
                itemBuilder: (context, idx) {
                  final preset = ThemePresets.all[idx];
                  final isSelected =
                      widget.themeController.globalTheme.id == preset.id;
                  return GestureDetector(
                    onTap: () {
                      setState(() => _editingTheme = preset);
                      _applyTheme(preset);
                    },
                    child: Container(
                      width: 95,
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: preset.surfaceColor,
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(
                          color: isSelected
                              ? theme.colorScheme.primary
                              : theme.dividerColor.withValues(alpha: 0.2),
                          width: isSelected ? 2.5 : 1,
                        ),
                      ),
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Container(
                                width: 14,
                                height: 14,
                                decoration: BoxDecoration(
                                  color: preset.accentColor,
                                  shape: BoxShape.circle,
                                ),
                              ),
                              const SizedBox(width: 4),
                              Container(
                                width: 14,
                                height: 14,
                                decoration: BoxDecoration(
                                  color: preset.outgoingBubbleColor,
                                  shape: BoxShape.circle,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 8),
                          Text(
                            preset.name,
                            style: TextStyle(
                              fontSize: 12,
                              fontWeight: isSelected
                                  ? FontWeight.bold
                                  : FontWeight.w500,
                              color: preset.primaryTextColor,
                            ),
                            overflow: TextOverflow.ellipsis,
                          ),
                          if (isSelected)
                            Icon(
                              Icons.check_circle_rounded,
                              size: 14,
                              color: theme.colorScheme.primary,
                            ),
                        ],
                      ),
                    ),
                  );
                },
              ),
            ),
          ],
        ),

        // Interactive Live Theme Preview Card
        ChatyPreviewCard(
          title: 'Live Theme Preview',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 8,
                ),
                decoration: BoxDecoration(
                  color: _editingTheme.surfaceColor,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Row(
                  children: [
                    Text(
                      'Chaty Live Preview',
                      style: TextStyle(
                        color: _editingTheme.primaryTextColor,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const Spacer(),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 6,
                        vertical: 2,
                      ),
                      decoration: BoxDecoration(
                        color: _editingTheme.accentColor,
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: const Text(
                        'NEW',
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 9,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 10),
              // Incoming Message Bubble Preview
              Align(
                alignment: Alignment.centerLeft,
                child: Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: _editingTheme.incomingBubbleColor,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    'Incoming message with custom token colors!',
                    style: TextStyle(
                      color: _editingTheme.incomingTextColor,
                      fontSize: 13,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 8),
              // Outgoing Message Bubble Preview
              Align(
                alignment: Alignment.centerRight,
                child: Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: _editingTheme.outgoingBubbleColor,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    'Outgoing message preview text',
                    style: TextStyle(
                      color: _editingTheme.outgoingTextColor,
                      fontSize: 13,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),

        // Color Token Controls
        ChatySettingsSection(
          title: 'Color Tokens & Overrides',
          description:
              'Customize core brand colors. Real-time updates update live UI.',
          children: [
            ChatyColorTile(
              title: 'Accent Brand Color',
              subtitle:
                  'Primary action color across app buttons and highlights',
              color: _editingTheme.accentColor,
              onTap: () =>
                  _pickColor('Accent Color', _editingTheme.accentColor, (c) {
                    _editingTheme = _editingTheme.copyWith(accentColor: c);
                    _applyTheme(_editingTheme);
                  }),
            ),
            ChatyColorTile(
              title: 'App Background',
              subtitle: 'Scaffold background surface',
              color: _editingTheme.backgroundColor,
              onTap: () => _pickColor(
                'App Background',
                _editingTheme.backgroundColor,
                (c) {
                  _editingTheme = _editingTheme.copyWith(backgroundColor: c);
                  _applyTheme(_editingTheme);
                },
              ),
            ),
            ChatyColorTile(
              title: 'Surface / Card Color',
              subtitle: 'Container and card background color',
              color: _editingTheme.surfaceColor,
              onTap: () =>
                  _pickColor('Surface Color', _editingTheme.surfaceColor, (c) {
                    _editingTheme = _editingTheme.copyWith(
                      surfaceColor: c,
                      cardColor: c,
                    );
                    _applyTheme(_editingTheme);
                  }),
            ),
            ChatyColorTile(
              title: 'Incoming Message Bubble',
              subtitle: 'Received message background color',
              color: _editingTheme.incomingBubbleColor,
              onTap: () => _pickColor(
                'Incoming Bubble Color',
                _editingTheme.incomingBubbleColor,
                (c) {
                  _editingTheme = _editingTheme.copyWith(
                    incomingBubbleColor: c,
                  );
                  _applyTheme(_editingTheme);
                },
                bgContext: _editingTheme.backgroundColor,
              ),
            ),
            ChatyColorTile(
              title: 'Outgoing Message Bubble',
              subtitle: 'Sent message background color',
              color: _editingTheme.outgoingBubbleColor,
              onTap: () => _pickColor(
                'Outgoing Bubble Color',
                _editingTheme.outgoingBubbleColor,
                (c) {
                  _editingTheme = _editingTheme.copyWith(
                    outgoingBubbleColor: c,
                  );
                  _applyTheme(_editingTheme);
                },
                bgContext: _editingTheme.backgroundColor,
              ),
            ),
          ],
        ),

        // Reset & Save Actions
        ChatySettingsSection(
          title: 'Theme Management',
          children: [
            ChatySettingsTile(
              icon: Icons.restore_rounded,
              title: 'Reset Theme to Default',
              subtitle: 'Restore default Chaty Midnight preset',
              onTap: () {
                _applyTheme(ThemePresets.midnight);
                setState(() => _editingTheme = ThemePresets.midnight);
              },
            ),
          ],
        ),
      ],
    );
  }
}
