import 'package:flutter/material.dart';

import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart';
import '../../../ui/core/design_system/gb_design_system.dart';

class ConversationMoreOptionsScreen extends StatefulWidget {
  final ChatyPreferencesController preferencesController;

  const ConversationMoreOptionsScreen({
    super.key,
    required this.preferencesController,
  });

  @override
  State<ConversationMoreOptionsScreen> createState() => _ConversationMoreOptionsScreenState();
}

class _ConversationMoreOptionsScreenState extends State<ConversationMoreOptionsScreen> {
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

  @override
  Widget build(BuildContext context) {
    final themeController = locator<ThemeController>();
    return ListenableBuilder(
      listenable: Listenable.merge([widget.preferencesController, themeController]),
      builder: (context, _) {
        final theme = themeController.globalTheme;
        final prefs = widget.preferencesController.conversation;

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
              'More options',
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
                // Top Live Preview Card
                GbCardContainer(
                  children: [
                    GbLivePreviewCard(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Live Visual Options Preview',
                            style: TextStyle(
                              color: theme.primaryTextColor,
                              fontWeight: FontWeight.w700,
                              fontSize: 14,
                            ),
                          ),
                          const SizedBox(height: 12),
                          // Voice Note Preview Bar
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                            decoration: BoxDecoration(
                              color: prefs.voiceNotePlayingBarColor != null
                                  ? Color(prefs.voiceNotePlayingBarColor!)
                                  : theme.surfaceColor,
                              borderRadius: BorderRadius.circular(16),
                            ),
                            child: Row(
                              children: [
                                CircleAvatar(
                                  radius: 16,
                                  backgroundColor: prefs.voiceNotePlayButtonColor != null
                                      ? Color(prefs.voiceNotePlayButtonColor!)
                                      : theme.accentColor,
                                  child: const Icon(Icons.play_arrow_rounded, color: Colors.white, size: 20),
                                ),
                                const SizedBox(width: 10),
                                Expanded(
                                  child: Container(
                                    height: 4,
                                    decoration: BoxDecoration(
                                      color: theme.accentColor.withValues(alpha: 0.4),
                                      borderRadius: BorderRadius.circular(2),
                                    ),
                                  ),
                                ),
                                const SizedBox(width: 10),
                                Text(
                                  '0:42',
                                  style: TextStyle(
                                    color: theme.secondaryTextColor,
                                    fontSize: 12,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(height: 10),
                          // Info balloon Preview
                          Center(
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                              decoration: BoxDecoration(
                                color: prefs.infoBalloonsBgColor != null
                                    ? Color(prefs.infoBalloonsBgColor!)
                                    : (theme.brightness == Brightness.dark
                                        ? const Color(0xFF1E2630)
                                        : const Color(0xFFE2E8F0)),
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: Text(
                                'Messages and calls are end-to-end encrypted',
                                style: TextStyle(
                                  color: prefs.infoBalloonsTextColor != null
                                      ? Color(prefs.infoBalloonsTextColor!)
                                      : theme.secondaryTextColor,
                                  fontSize: 11,
                                  fontWeight: FontWeight.w500,
                                ),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),

                // Color Configuration List
                GbCardContainer(
                  children: [
                    GbSettingRow(
                      icon: Icons.emoji_emotions_outlined,
                      title: 'Emoji header color',
                      subtitle: 'Change Emoji popup header background color',
                      colorHex: prefs.emojiHeaderColor,
                      onColorTap: () => _pickColor(
                        title: 'Emoji Header Color',
                        currentColor: prefs.emojiHeaderColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(emojiHeaderColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.sentiment_satisfied_alt_rounded,
                      title: 'Emoji header icons color',
                      subtitle: 'Change Emoji popup icons color',
                      colorHex: prefs.emojiHeaderIconsColor,
                      onColorTap: () => _pickColor(
                        title: 'Emoji Header Icons Color',
                        currentColor: prefs.emojiHeaderIconsColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(emojiHeaderIconsColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.palette_outlined,
                      title: 'Emoji picker background color',
                      subtitle: 'Change Emoji popup background color',
                      colorHex: prefs.emojiPickerBgColor,
                      onColorTap: () => _pickColor(
                        title: 'Emoji Picker Background Color',
                        currentColor: prefs.emojiPickerBgColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(emojiPickerBgColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.link_rounded,
                      title: 'Hyperlinks colors',
                      subtitle: 'Change Hyperlinks color in Chat Bubbles',
                      colorHex: prefs.hyperlinksColor,
                      onColorTap: () => _pickColor(
                        title: 'Hyperlinks Color',
                        currentColor: prefs.hyperlinksColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(hyperlinksColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.info_outline_rounded,
                      title: 'Info balloons Text colour',
                      subtitle: 'Date and Missed call Text colour bubbles',
                      colorHex: prefs.infoBalloonsTextColor,
                      onColorTap: () => _pickColor(
                        title: 'Info Balloons Text Color',
                        currentColor: prefs.infoBalloonsTextColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(infoBalloonsTextColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.mark_chat_unread_outlined,
                      title: 'Info balloons background colour',
                      subtitle: 'Date and Missed call bubbles background colour',
                      colorHex: prefs.infoBalloonsBgColor,
                      onColorTap: () => _pickColor(
                        title: 'Info Balloons Background Color',
                        currentColor: prefs.infoBalloonsBgColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(infoBalloonsBgColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.group_outlined,
                      title: 'Group participant name color',
                      subtitle: 'Change color of group member/participant name on message',
                      colorHex: prefs.groupParticipantNameColor,
                      onColorTap: () => _pickColor(
                        title: 'Participant Name Color',
                        currentColor: prefs.groupParticipantNameColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(groupParticipantNameColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.graphic_eq_rounded,
                      title: 'Voice note playing bar',
                      subtitle: 'Change color of voice message playing bar',
                      colorHex: prefs.voiceNotePlayingBarColor,
                      onColorTap: () => _pickColor(
                        title: 'Voice Note Playing Bar Color',
                        currentColor: prefs.voiceNotePlayingBarColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(voiceNotePlayingBarColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.play_circle_fill_rounded,
                      title: 'Voice note Play button',
                      subtitle: 'Change color of voice message Play button',
                      colorHex: prefs.voiceNotePlayButtonColor,
                      onColorTap: () => _pickColor(
                        title: 'Voice Note Play Button Color',
                        currentColor: prefs.voiceNotePlayButtonColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(voiceNotePlayButtonColor: val),
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
