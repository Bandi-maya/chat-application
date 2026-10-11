import 'package:flutter/material.dart';

import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart';
import '../../../ui/core/design_system/gb_design_system.dart';
import '../../../ui/core/design_system/components/adaptive_selection_panel.dart';

class ConversationBubbleAndTicksScreen extends StatefulWidget {
  final ChatyPreferencesController preferencesController;

  const ConversationBubbleAndTicksScreen({
    super.key,
    required this.preferencesController,
  });

  @override
  State<ConversationBubbleAndTicksScreen> createState() => _ConversationBubbleAndTicksScreenState();
}

class _ConversationBubbleAndTicksScreenState extends State<ConversationBubbleAndTicksScreen> {
  final List<String> _tickStyles = [
    'Default',
    'iOS Ticks',
    'Wings',
    'Traffic Lights',
    'Circles',
    'Stars',
    'Hearts',
    'Batman',
    'Double Sword',
    'Glowing Dots',
    'Material 3 Rounded',
  ];

  final List<String> _bubbleStyles = [
    'WhatsApp Default',
    'Rounded Pill',
    'Chaty Modern',
    'Squircle Soft',
    'Paper Chat',
    'Glassmorphic',
    'iOS Flat',
    'Telegram Arc',
    '3D Shadow',
    'Minimal Border',
    'Retro Bubble',
    'Futuristic Clip',
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

  void _showTicksStyleModal() {
    final prefs = widget.preferencesController.conversation;
    final theme = locator<ThemeController>().globalTheme;
    AdaptiveSelectionPanel.show<String>(
      context: context,
      title: 'Choose your Ticks style',
      selectedValue: prefs.tickStyle,
      showApplyButton: true,
      preferCenteredDialog: true,
      options: _tickStyles
          .map((item) => SelectionOptionItem<String>(
                value: item,
                title: item,
                preview: _buildTickIcon(item, theme.accentColor),
              ))
          .toList(),
    ).then((val) {
      if (val != null) {
        widget.preferencesController.updateConversation(
          prefs.copyWith(tickStyle: val),
        );
      }
    });
  }

  void _showBubblesStyleModal() {
    final prefs = widget.preferencesController.conversation;
    final theme = locator<ThemeController>().globalTheme;
    AdaptiveSelectionPanel.show<String>(
      context: context,
      title: 'Choose your Bubbles style',
      selectedValue: prefs.bubbleStyle,
      showApplyButton: true,
      preferCenteredDialog: true,
      options: _bubbleStyles
          .map((item) => SelectionOptionItem<String>(
                value: item,
                title: item,
                preview: _buildBubblePreview(item, theme.accentColor),
              ))
          .toList(),
    ).then((val) {
      if (val != null) {
        // The runtime theme resolves bubble geometry from the legacy
        // `bubble_style` key first. Update that key through the semantic alias
        // so it and the typed ConversationPreferences stay in sync; otherwise
        // an older saved GB value can silently override the selection.
        widget.preferencesController.updateGbFeatures(
          <String, Object?>{'bubble_style': val},
          logTitle: 'Bubbles Style',
        );
      }
    });
  }

  Widget _buildBubblePreview(String bubbleStyle, Color color) {
    BorderRadius radius = BorderRadius.circular(10);
    if (bubbleStyle.contains('Pill')) {
      radius = BorderRadius.circular(16);
    } else if (bubbleStyle.contains('Squircle')) {
      radius = BorderRadius.circular(8);
    } else if (bubbleStyle.contains('Arc')) {
      radius = const BorderRadius.only(
        topLeft: Radius.circular(12),
        topRight: Radius.circular(12),
        bottomLeft: Radius.circular(2),
        bottomRight: Radius.circular(12),
      );
    } else if (bubbleStyle.contains('Futuristic')) {
      radius = const BorderRadius.only(
        topLeft: Radius.circular(14),
        bottomRight: Radius.circular(14),
      );
    }
    return Container(
      width: 34,
      height: 24,
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.18),
        borderRadius: radius,
        border: Border.all(color: color, width: 1.2),
      ),
      child: Center(
        child: Icon(Icons.chat_bubble_outline_rounded, size: 13, color: color),
      ),
    );
  }

  Widget _buildTickIcon(String tickStyle, Color color) {
    if (tickStyle.contains('Star')) {
      return Icon(Icons.star, size: 14, color: color);
    } else if (tickStyle.contains('Heart')) {
      return Icon(Icons.favorite, size: 13, color: color);
    } else if (tickStyle.contains('Circle')) {
      return Icon(Icons.radio_button_checked, size: 13, color: color);
    } else if (tickStyle.contains('Traffic')) {
      return Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(width: 5, height: 5, decoration: BoxDecoration(shape: BoxShape.circle, color: color)),
          const SizedBox(width: 2),
          Container(width: 5, height: 5, decoration: BoxDecoration(shape: BoxShape.circle, color: color)),
        ],
      );
    } else if (tickStyle.contains('Batman') || tickStyle.contains('Wings')) {
      return Icon(Icons.flight, size: 14, color: color);
    } else if (tickStyle.contains('Double Sword')) {
      return Icon(Icons.close_rounded, size: 14, color: color);
    } else if (tickStyle.contains('Glowing Dots')) {
      return Icon(Icons.more_horiz_rounded, size: 15, color: color);
    } else if (tickStyle.contains('Material 3 Rounded')) {
      return Icon(Icons.check_circle_rounded, size: 14, color: color);
    }
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(Icons.check, size: 14, color: color),
        Transform.translate(
          offset: const Offset(-8, 0),
          child: Icon(Icons.check, size: 14, color: color),
        ),
      ],
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

        final convBg = prefs.conversationBackgroundColor != null
            ? Color(prefs.conversationBackgroundColor!)
            : colors.surface;
        final rightBubbleBg = prefs.rightBubbleColor != null
            ? Color(prefs.rightBubbleColor!)
            : theme.accentColor;
        final rightTextCol = prefs.rightChatBubbleTextColor != null
            ? Color(prefs.rightChatBubbleTextColor!)
            : Colors.white;
        final rightTimeCol = prefs.rightBubbleTimeColor != null
            ? Color(prefs.rightBubbleTimeColor!)
            : Colors.white.withValues(alpha: 0.7);

        final leftBubbleBg = prefs.leftBubbleColor != null
            ? Color(prefs.leftBubbleColor!)
            : colors.surfaceSecondary;
        final leftTextCol = prefs.leftChatBubbleTextColor != null
            ? Color(prefs.leftChatBubbleTextColor!)
            : theme.primaryTextColor;
        final leftTimeCol = prefs.leftBubbleTimeColor != null
            ? Color(prefs.leftBubbleTimeColor!)
            : theme.secondaryTextColor;

        final quotedBg = prefs.quotedBackgroundColor != null
            ? Color(prefs.quotedBackgroundColor!)
            : colors.surface;
        final quotedDivider = prefs.quotedDividerColor != null
            ? Color(prefs.quotedDividerColor!)
            : theme.accentColor;
        final quotedName = prefs.quotedNameColor != null
            ? Color(prefs.quotedNameColor!)
            : theme.accentColor;
        final quotedMsg = prefs.quotedMessageColor != null
            ? Color(prefs.quotedMessageColor!)
            : theme.secondaryTextColor;

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
              'Bubbles And Ticks',
              style: TextStyle(
                color: theme.primaryTextColor,
                fontSize: 19 * theme.fontScale,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
          body: SafeArea(
            top: false,
            child: Column(
              children: [
                // Top Live Chat Bubbles & Ticks Preview (Pinned, does not scroll away)
                Padding(
                  padding: const EdgeInsets.fromLTRB(14, 10, 14, 4),
                  child: GbLivePreviewCard(
                    padding: const EdgeInsets.all(12),
                    child: Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: convBg,
                        borderRadius: BorderRadius.circular(16),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          // Left Incoming Bubble 1
                          Align(
                            alignment: Alignment.centerLeft,
                            child: Container(
                              margin: const EdgeInsets.only(bottom: 6),
                              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                              constraints: const BoxConstraints(maxWidth: 240),
                              decoration: BoxDecoration(
                                color: leftBubbleBg,
                                borderRadius: const BorderRadius.only(
                                  topLeft: Radius.circular(16),
                                  topRight: Radius.circular(16),
                                  bottomRight: Radius.circular(16),
                                  bottomLeft: Radius.circular(4),
                                ),
                              ),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    'Tyler Durden',
                                    style: TextStyle(
                                      fontSize: 11,
                                      fontWeight: FontWeight.w700,
                                      color: theme.accentColor,
                                    ),
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    'Hello, I am Tyler Durden',
                                    style: TextStyle(
                                      color: leftTextCol,
                                      fontSize: prefs.messageTextSize.toDouble(),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),

                          // Left Incoming Bubble 2
                          Align(
                            alignment: Alignment.centerLeft,
                            child: Container(
                              margin: const EdgeInsets.only(bottom: 6),
                              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                              constraints: const BoxConstraints(maxWidth: 240),
                              decoration: BoxDecoration(
                                color: leftBubbleBg,
                                borderRadius: const BorderRadius.only(
                                  topLeft: Radius.circular(16),
                                  topRight: Radius.circular(16),
                                  bottomRight: Radius.circular(16),
                                  bottomLeft: Radius.circular(4),
                                ),
                              ),
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Text(
                                    'Do you like my theme?',
                                    style: TextStyle(
                                      color: leftTextCol,
                                      fontSize: prefs.messageTextSize.toDouble(),
                                    ),
                                  ),
                                  const SizedBox(width: 8),
                                  Text(
                                    '07:11 PM',
                                    style: TextStyle(color: leftTimeCol, fontSize: 10),
                                  ),
                                ],
                              ),
                            ),
                          ),

                          // Right Outgoing Bubble with Quoted Box & Ticks
                          Align(
                            alignment: Alignment.centerRight,
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                              constraints: const BoxConstraints(maxWidth: 250),
                              decoration: BoxDecoration(
                                color: rightBubbleBg,
                                borderRadius: const BorderRadius.only(
                                  topLeft: Radius.circular(16),
                                  topRight: Radius.circular(16),
                                  bottomLeft: Radius.circular(16),
                                  bottomRight: Radius.circular(4),
                                ),
                              ),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.end,
                                children: [
                                  // Quoted message snippet
                                  Container(
                                    padding: const EdgeInsets.all(6),
                                    margin: const EdgeInsets.only(bottom: 4),
                                    decoration: BoxDecoration(
                                      color: quotedBg,
                                      borderRadius: BorderRadius.circular(8),
                                      border: Border(
                                        left: BorderSide(color: quotedDivider, width: 3.5),
                                      ),
                                    ),
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Text(
                                          'Tyler Durden',
                                          style: TextStyle(
                                            color: quotedName,
                                            fontSize: 10,
                                            fontWeight: FontWeight.bold,
                                          ),
                                        ),
                                        Text(
                                          'Do you like my theme?',
                                          style: TextStyle(
                                            color: quotedMsg,
                                            fontSize: 11,
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                  Text(
                                    'Yes, your theme looks awesome!',
                                    style: TextStyle(
                                      color: rightTextCol,
                                      fontSize: prefs.messageTextSize.toDouble(),
                                    ),
                                  ),
                                  const SizedBox(height: 3),
                                  Row(
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      Text(
                                        '07:11 PM',
                                        style: TextStyle(color: rightTimeCol, fontSize: 10),
                                      ),
                                      const SizedBox(width: 4),
                                      _buildTickIcon(prefs.tickStyle, rightTimeCol),
                                    ],
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
                // Bottom content scrolls underneath the pinned live preview
                Expanded(
                  child: ListView(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                    children: [

                // SECTION 1: Styles (Look and feel)
                GbCardContainer(
                  headerText: 'Styles (Look and feel)',
                  children: [
                    GbSettingRow(
                      icon: Icons.done_all_rounded,
                      title: 'Ticks Style',
                      subtitle: prefs.tickStyle,
                      hasSubScreen: true,
                      onTap: _showTicksStyleModal,
                    ),
                    GbSettingRow(
                      icon: Icons.chat_bubble_outline_rounded,
                      title: 'Bubbles Style',
                      subtitle: prefs.bubbleStyle,
                      hasSubScreen: true,
                      onTap: _showBubblesStyleModal,
                    ),
                    GbSliderTile(
                      title: 'Message Text Size',
                      subtitle: 'Set chat text font size in points',
                      value: prefs.messageTextSize.toDouble(),
                      min: 10,
                      max: 26,
                      divisions: 16,
                      onChanged: (val) {
                        widget.preferencesController.updateConversation(
                          prefs.copyWith(messageTextSize: val.toDouble()),
                        );
                      },
                    ),
                  ],
                ),
                const SizedBox(height: 14),

                // SECTION 2: Colors
                GbCardContainer(
                  headerText: 'Colors',
                  children: [
                    GbSettingRow(
                      icon: Icons.wallpaper_rounded,
                      title: 'Conversation Background Color',
                      subtitle: 'Change background color of conversation, when set to "no wallpaper"',
                      colorValue: prefs.conversationBackgroundColor,
                      onColorTap: () => _pickColor(
                        title: 'Conversation Background Color',
                        currentColor: prefs.conversationBackgroundColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(conversationBackgroundColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.chat_bubble_rounded,
                      title: 'Right Bubble Color',
                      subtitle: 'Change right chat bubble color',
                      colorValue: prefs.rightBubbleColor,
                      onColorTap: () => _pickColor(
                        title: 'Right Bubble Color',
                        currentColor: prefs.rightBubbleColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(rightBubbleColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.format_color_text_rounded,
                      title: 'Right Chat Bubble Text Color',
                      subtitle: 'Sets Right chat bubble text color',
                      colorValue: prefs.rightChatBubbleTextColor,
                      onColorTap: () => _pickColor(
                        title: 'Right Bubble Text Color',
                        currentColor: prefs.rightChatBubbleTextColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(rightChatBubbleTextColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.access_time_rounded,
                      title: 'Right Bubble time color',
                      subtitle: 'Change the color of the time inside the right bubble',
                      colorValue: prefs.rightBubbleTimeColor,
                      onColorTap: () => _pickColor(
                        title: 'Right Bubble Time Color',
                        currentColor: prefs.rightBubbleTimeColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(rightBubbleTimeColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.chat_bubble_outline_rounded,
                      title: 'Left Bubble Color',
                      subtitle: 'Change left chat bubble color',
                      colorValue: prefs.leftBubbleColor,
                      onColorTap: () => _pickColor(
                        title: 'Left Bubble Color',
                        currentColor: prefs.leftBubbleColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(leftBubbleColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.format_color_text_rounded,
                      title: 'Left Chat Bubble Text Color',
                      subtitle: 'Sets Left chat bubble text color',
                      colorValue: prefs.leftChatBubbleTextColor,
                      onColorTap: () => _pickColor(
                        title: 'Left Bubble Text Color',
                        currentColor: prefs.leftChatBubbleTextColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(leftChatBubbleTextColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.access_time_rounded,
                      title: 'Left Bubble time color',
                      subtitle: 'Change the color of the time inside the left bubble',
                      colorValue: prefs.leftBubbleTimeColor,
                      onColorTap: () => _pickColor(
                        title: 'Left Bubble Time Color',
                        currentColor: prefs.leftBubbleTimeColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(leftBubbleTimeColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.delete_outline_rounded,
                      title: 'Deleted Message Icon Color',
                      subtitle: 'Change the color of the revoked icon on deleted messages',
                      colorValue: prefs.deletedMessageIconColor,
                      onColorTap: () => _pickColor(
                        title: 'Deleted Message Icon Color',
                        currentColor: prefs.deletedMessageIconColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(deletedMessageIconColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.vertical_split_rounded,
                      title: 'Quoted Divider Color',
                      subtitle: 'Color of the vertical bar beside quote',
                      colorValue: prefs.quotedDividerColor,
                      onColorTap: () => _pickColor(
                        title: 'Quoted Divider Color',
                        currentColor: prefs.quotedDividerColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(quotedDividerColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.person_pin_rounded,
                      title: 'Quoted Name Color',
                      subtitle: 'Sender name color inside quoted reply',
                      colorValue: prefs.quotedNameColor,
                      onColorTap: () => _pickColor(
                        title: 'Quoted Name Color',
                        currentColor: prefs.quotedNameColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(quotedNameColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.notes_rounded,
                      title: 'Quoted Message Color',
                      subtitle: 'Quoted body text color',
                      colorValue: prefs.quotedMessageColor,
                      onColorTap: () => _pickColor(
                        title: 'Quoted Message Color',
                        currentColor: prefs.quotedMessageColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(quotedMessageColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.layers_rounded,
                      title: 'Quoted Background Color',
                      subtitle: 'Background of quote container',
                      colorValue: prefs.quotedBackgroundColor,
                      onColorTap: () => _pickColor(
                        title: 'Quoted Background Color',
                        currentColor: prefs.quotedBackgroundColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(quotedBackgroundColor: val),
                          );
                        },
                      ),
                    ),
                    GbSwitchTile(
                      icon: Icons.select_all_rounded,
                      title: 'Make Text Selectable',
                      subtitle: 'Enable chat bubble text selection',
                      value: prefs.makeTextSelectable,
                      onChanged: (val) {
                        widget.preferencesController.updateConversation(
                          prefs.copyWith(makeTextSelectable: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.read_more_rounded,
                      title: 'Remove Read more...',
                      subtitle: 'Remove the Read more... button and display full long text',
                      value: prefs.removeReadMore,
                      onChanged: (val) {
                        widget.preferencesController.updateConversation(
                          prefs.copyWith(removeReadMore: val),
                        );
                      },
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    ),
  );
      },
    );
  }
}
