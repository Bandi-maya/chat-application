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
      selectedOption: _normalizeStyle(prefs.entryStyle),
      onSelected: (val) {
        widget.preferencesController.updateConversation(
          prefs.copyWith(entryStyle: val),
        );
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(
            SnackBar(
              duration: const Duration(seconds: 2),
              content: Text('$val applied to the conversation entry bar'),
            ),
          );
      },
    );
  }

  /// Older saves stored the bare value 'Stock'; map it onto the picker's
  /// current stock option so the radio selection and preview agree.
  static String _normalizeStyle(String raw) {
    final v = raw.trim();
    if (v.isEmpty || v == 'Stock') return 'Stock (Chaty Default)';
    return v;
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
                // Top Live Entry Bar Preview - mirrors the arrangement the
                // selected Conversation Entry style applies in the chat screen.
                GbLivePreviewCard(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 16),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                    decoration: BoxDecoration(
                       color: uiEntryBg,
                       borderRadius: BorderRadius.circular(24),
                    ),
                    child: _buildStylePreview(
                      _normalizeStyle(prefs.entryStyle),
                      buttonsCol: buttonsCol,
                      emojiCol: emojiCol,
                      sendCol: sendCol,
                      micCircleCol: micCircleCol,
                      textEntryBg: textEntryBg,
                      textEntryCol: textEntryCol,
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
                      subtitle: 'Change the style of the Bottom Entry in Conversation screen\nSelected: ${_normalizeStyle(prefs.entryStyle)}',
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

  /// Schematic preview of how the selected Conversation Entry style arranges
  /// the bottom entry bar in the conversation screen.
  Widget _buildStylePreview(
    String style, {
    required Color buttonsCol,
    required Color emojiCol,
    required Color sendCol,
    required Color micCircleCol,
    required Color textEntryBg,
    required Color textEntryCol,
  }) {
    Widget emojiI({double size = 22}) =>
        Icon(Icons.emoji_emotions_outlined, color: emojiCol, size: size);
    Widget attachI({double size = 20}) =>
        Icon(Icons.attach_file_rounded, color: buttonsCol, size: size);
    Widget cameraI({double size = 20}) =>
        Icon(Icons.camera_alt_outlined, color: buttonsCol, size: size);
    Widget micCircle() => Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(color: micCircleCol, shape: BoxShape.circle),
          child: Icon(Icons.mic_rounded, color: sendCol, size: 22),
        );

    Widget field({
      List<Widget> leading = const [],
      List<Widget> trailing = const [],
      double radius = 20,
      bool transparent = false,
      bool border = false,
      bool shadow = false,
    }) {
      return Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: BoxDecoration(
          color: transparent ? Colors.transparent : textEntryBg,
          borderRadius: BorderRadius.circular(radius),
          border: border
              ? Border.all(color: textEntryCol.withValues(alpha: 0.3))
              : null,
          boxShadow: shadow
              ? [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.16),
                    blurRadius: 6,
                    offset: const Offset(0, 2),
                  ),
                ]
              : null,
        ),
        child: Row(
          children: [
            ...leading,
            if (leading.isNotEmpty) const SizedBox(width: 8),
            Expanded(
              child: Text(
                'Message',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  color: textEntryCol.withValues(alpha: 0.6),
                  fontSize: 15,
                ),
              ),
            ),
            if (trailing.isNotEmpty) const SizedBox(width: 8),
            ...trailing,
          ],
        ),
      );
    }

    switch (style) {
      case 'Telegram Modern':
        return Row(
          children: [
            Icon(Icons.attach_file_rounded, color: buttonsCol, size: 24),
            const SizedBox(width: 8),
            Expanded(child: field(radius: 14, trailing: [emojiI(size: 20)])),
            const SizedBox(width: 8),
            micCircle(),
          ],
        );
      case 'iOS Clean Rounded':
        return Row(
          children: [
            Container(
              width: 36,
              height: 36,
              decoration:
                  BoxDecoration(color: buttonsCol, shape: BoxShape.circle),
              child: const Icon(Icons.add_rounded,
                  color: Colors.white, size: 22),
            ),
            const SizedBox(width: 8),
            Expanded(child: field(radius: 22)),
            const SizedBox(width: 8),
            micCircle(),
          ],
        );
      case 'Hangouts Minimal':
        return Row(
          children: [
            emojiI(),
            const SizedBox(width: 8),
            Expanded(child: field(radius: 8, transparent: true, border: true)),
            const SizedBox(width: 8),
            attachI(size: 22),
            const SizedBox(width: 8),
            micCircle(),
          ],
        );
      case 'Simple Pill':
        return Row(
          children: [
            Expanded(child: field(radius: 24, leading: [emojiI(size: 20)])),
            const SizedBox(width: 8),
            attachI(size: 22),
            const SizedBox(width: 6),
            cameraI(size: 22),
            const SizedBox(width: 8),
            micCircle(),
          ],
        );
      case 'RC-Flat Floating':
        return Container(
          padding: const EdgeInsets.all(4),
          decoration: BoxDecoration(
            color: textEntryBg,
            borderRadius: BorderRadius.circular(16),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.18),
                blurRadius: 10,
                offset: const Offset(0, 3),
              ),
            ],
          ),
          child: Row(
            children: [
              Expanded(
                child: field(
                  radius: 12,
                  transparent: true,
                  leading: [emojiI(size: 20)],
                  trailing: [attachI()],
                ),
              ),
              micCircle(),
            ],
          ),
        );
      case 'WhatsApp Classic 3D':
        return Row(
          children: [
            Expanded(
              child: field(
                radius: 22,
                shadow: true,
                leading: [emojiI(size: 20)],
                trailing: [
                  attachI(),
                  const SizedBox(width: 8),
                  cameraI(),
                ],
              ),
            ),
            const SizedBox(width: 8),
            micCircle(),
          ],
        );
      case 'Signal Compact Bubble':
        return Row(
          children: [
            Expanded(child: field(radius: 18, trailing: [attachI()])),
            const SizedBox(width: 8),
            micCircle(),
          ],
        );
      case 'Stock (Chaty Default)':
      default:
        return Row(
          children: [
            Expanded(
              child: field(leading: [emojiI()], trailing: [
                attachI(),
                const SizedBox(width: 8),
                cameraI(),
              ]),
            ),
            const SizedBox(width: 8),
            micCircle(),
          ],
        );
    }
  }
}
