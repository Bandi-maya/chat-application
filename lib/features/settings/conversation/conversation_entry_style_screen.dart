import 'package:flutter/material.dart';

import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart';
import '../../../ui/core/design_system/gb_design_system.dart';

class ConversationEntryStyleScreen extends StatefulWidget {
  final ChatyPreferencesController preferencesController;

  const ConversationEntryStyleScreen({
    super.key,
    required this.preferencesController,
  });

  @override
  State<ConversationEntryStyleScreen> createState() => _ConversationEntryStyleScreenState();
}

class _ConversationEntryStyleScreenState extends State<ConversationEntryStyleScreen> {
  static const List<String> _entryStyles = [
    'Stock (Chaty Default)',
    'Telegram Modern',
    'iOS Clean Rounded',
    'Hangouts Minimal',
    'Simple Pill',
    'RC-Flat Floating',
    'WhatsApp Classic 3D',
    'Signal Compact Bubble',
  ];

  void _pickColor({
    required String title,
    required int? currentColor,
    required ValueChanged<int?> onColorSelected,
  }) {
    final theme = locator<ThemeController>().globalTheme;
    final initial = currentColor != null ? Color(currentColor) : theme.accentColor;
    GbColorPickerModal.show(
      context,
      title: title,
      currentColor: initial,
      onColorChanged: (c) {
        onColorSelected(c.value);
      },
    );
  }

  void _showEntryStyleModal() {
    final prefs = widget.preferencesController.conversation;
    GbRadioSelectionModal.show<String>(
      context: context,
      title: 'Conversation Entry style',
      options: _entryStyles,
      selectedOption: prefs.entryStyle,
      onSelected: (val) {
        widget.preferencesController.updateConversation(
          prefs.copyWith(entryStyle: val),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final themeController = locator<ThemeController>();
    return ListenableBuilder(
      listenable: Listenable.merge([widget.preferencesController, themeController]),
      builder: (context, _) {
        final theme = themeController.globalTheme;
        final colors = context.colors;
        final prefs = widget.preferencesController.conversation;

        final uiEntryBg = prefs.uiEntryBackgroundColor != null
            ? Color(prefs.uiEntryBackgroundColor!)
            : theme.surfaceColor;
        final buttonsCol = prefs.uiButtonsColor != null
            ? Color(prefs.uiButtonsColor!)
            : theme.secondaryTextColor;
        final emojiCol = prefs.emojiButtonColor != null
            ? Color(prefs.emojiButtonColor!)
            : theme.secondaryTextColor;
        final sendCol = prefs.sendButtonColor != null
            ? Color(prefs.sendButtonColor!)
            : Colors.white;
        final micCircleCol = prefs.micSendBgCircleColor != null
            ? Color(prefs.micSendBgCircleColor!)
            : theme.accentColor;
        final textEntryBg = prefs.textEntryBackgroundColor != null
            ? Color(prefs.textEntryBackgroundColor!)
            : colors.surfaceSecondary;
        final textEntryCol = prefs.textEntryColor != null
            ? Color(prefs.textEntryColor!)
            : theme.primaryTextColor;

        return Scaffold(
          backgroundColor: theme.backgroundColor,
          appBar: AppBar(
            automaticallyImplyLeading: false,
            backgroundColor: theme.surfaceColor,
            elevation: 0,
            leading: const Padding(
              padding: EdgeInsets.all(8.0),
              child: ChatyBackButton(),
            ),
            title: Text(
              'Conversation Entry style',
              style: TextStyle(
                color: theme.primaryTextColor,
                fontSize: 19 * theme.fontScale,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
          body: SafeArea(
            top: false,
            child: ListView(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
              children: [
                // Top Live Entry Bar Preview (Matching Image 4)
                GbLivePreviewCard(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 16),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                    decoration: BoxDecoration(
                       color: uiEntryBg,
                       borderRadius: BorderRadius.circular(24),
                    ),
                    child: Row(
                      children: [
                        Expanded(
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                            decoration: BoxDecoration(
                              color: textEntryBg,
                              borderRadius: BorderRadius.circular(20),
                            ),
                            child: Row(
                              children: [
                                Icon(Icons.emoji_emotions_outlined, color: emojiCol, size: 22),
                                const SizedBox(width: 8),
                                Expanded(
                                  child: Text(
                                    'Message',
                                    style: TextStyle(
                                      color: textEntryCol.withValues(alpha: 0.6),
                                      fontSize: 15,
                                    ),
                                  ),
                                ),
                                Icon(Icons.attach_file_rounded, color: buttonsCol, size: 20),
                                const SizedBox(width: 8),
                                Icon(Icons.camera_alt_outlined, color: buttonsCol, size: 20),
                              ],
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Container(
                          width: 44,
                          height: 44,
                          decoration: BoxDecoration(
                            color: micCircleCol,
                            shape: BoxShape.circle,
                          ),
                          child: Icon(Icons.mic_rounded, color: sendCol, size: 22),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 14),

                // Controls
                GbCardContainer(
                  children: [
                    GbSettingRow(
                      icon: Icons.style_rounded,
                      title: 'Conversation Entry style',
                      subtitle: 'Change the style of the Bottom Entry in Conversation screen\nSelected: ${prefs.entryStyle}',
                      hasSubScreen: true,
                      onTap: _showEntryStyleModal,
                    ),
                    GbSettingRow(
                      icon: Icons.layers_rounded,
                      title: 'Conversation UI Entry Background',
                      subtitle: 'Change the Color of the Entry background',
                      colorHex: prefs.uiEntryBackgroundColor,
                      onColorTap: () => _pickColor(
                        title: 'Entry Background Color',
                        currentColor: prefs.uiEntryBackgroundColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(uiEntryBackgroundColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.smart_button_rounded,
                      title: 'Conversation UI Buttons Color',
                      subtitle: 'Change the Color of the Entry icons',
                      colorHex: prefs.uiButtonsColor,
                      onColorTap: () => _pickColor(
                        title: 'UI Buttons Color',
                        currentColor: prefs.uiButtonsColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(uiButtonsColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.mood_rounded,
                      title: 'Emoji Button Color',
                      subtitle: 'Emoji button in conversation screen color',
                      colorHex: prefs.emojiButtonColor,
                      onColorTap: () => _pickColor(
                        title: 'Emoji Button Color',
                        currentColor: prefs.emojiButtonColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(emojiButtonColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.send_rounded,
                      title: 'Send Button Color',
                      subtitle: 'Send/Mic color in the conversation screen',
                      colorHex: prefs.sendButtonColor,
                      onColorTap: () => _pickColor(
                        title: 'Send Button Color',
                        currentColor: prefs.sendButtonColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(sendButtonColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.radio_button_checked_rounded,
                      title: 'Mic/Send Background circle',
                      subtitle: 'Change the color of the circle behind Send/Mic',
                      colorHex: prefs.micSendBgCircleColor,
                      onColorTap: () => _pickColor(
                        title: 'Mic/Send Circle Color',
                        currentColor: prefs.micSendBgCircleColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(micSendBgCircleColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.format_color_fill_rounded,
                      title: 'Text Entry Background',
                      subtitle: 'Text Entry Background',
                      colorHex: prefs.textEntryBackgroundColor,
                      onColorTap: () => _pickColor(
                        title: 'Text Entry Background Color',
                        currentColor: prefs.textEntryBackgroundColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(textEntryBackgroundColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.format_color_text_rounded,
                      title: 'Text Entry Color',
                      subtitle: 'Change text color in the conversation box',
                      colorHex: prefs.textEntryColor,
                      onColorTap: () => _pickColor(
                        title: 'Text Entry Color',
                        currentColor: prefs.textEntryColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(textEntryColor: val),
                          );
                        },
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}
