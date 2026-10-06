import 'package:flutter/material.dart';

import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart';
import '../../../ui/core/design_system/gb_design_system.dart';

class ConversationActionBarScreen extends StatefulWidget {
  final ChatyPreferencesController preferencesController;

  const ConversationActionBarScreen({
    super.key,
    required this.preferencesController,
  });

  @override
  State<ConversationActionBarScreen> createState() => _ConversationActionBarScreenState();
}

class _ConversationActionBarScreenState extends State<ConversationActionBarScreen> {
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
        final colors = context.colors;
        final prefs = widget.preferencesController.conversation;

        final barColor = prefs.actionBarColor != null ? Color(prefs.actionBarColor!) : theme.surfaceColor;
        final statusBg = prefs.contactStatusBgColor != null ? Color(prefs.contactStatusBgColor!) : colors.surfaceSecondary;
        final statusText = prefs.contactStatusTextColor != null ? Color(prefs.contactStatusTextColor!) : theme.secondaryTextColor;

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
              'Action Bar',
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
                // Top Live Chat Header Preview (Matching Image 4)
                GbLivePreviewCard(
                  padding: EdgeInsets.zero,
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                        decoration: BoxDecoration(
                          color: barColor,
                          borderRadius: const BorderRadius.vertical(top: Radius.circular(18)),
                        ),
                        child: Row(
                          children: [
                            Icon(Icons.arrow_back_rounded, color: theme.primaryTextColor, size: 22),
                            const SizedBox(width: 8),
                            if (!prefs.hideProfilePicture) ...[
                              CircleAvatar(
                                radius: 18,
                                backgroundColor: colors.surfaceSecondary,
                                child: Icon(Icons.person, color: theme.secondaryTextColor, size: 20),
                              ),
                              const SizedBox(width: 10),
                            ],
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  if (!prefs.hideContactName)
                                    Text(
                                      'Tyler Durden',
                                      style: TextStyle(
                                        color: theme.primaryTextColor,
                                        fontWeight: FontWeight.w700,
                                        fontSize: 15,
                                      ),
                                    ),
                                  Text(
                                    'Online',
                                    style: TextStyle(
                                      color: theme.accentColor,
                                      fontSize: 12,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            if (!prefs.hideCallButton) ...[
                              Icon(Icons.call_rounded, color: theme.primaryTextColor, size: 20),
                              const SizedBox(width: 14),
                              Icon(Icons.videocam_rounded, color: theme.primaryTextColor, size: 22),
                              const SizedBox(width: 12),
                            ],
                            Icon(Icons.more_vert_rounded, color: theme.primaryTextColor, size: 20),
                          ],
                        ),
                      ),
                      if (!prefs.disableContactStatus)
                        Container(
                          width: double.infinity,
                          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                          decoration: BoxDecoration(
                            color: statusBg,
                            borderRadius: const BorderRadius.vertical(bottom: Radius.circular(18)),
                          ),
                          child: Text(
                            'Life is too short. Enjoy it and Support GBWA!',
                            style: TextStyle(
                              color: statusText,
                              fontSize: 12,
                              fontStyle: FontStyle.italic,
                            ),
                            textAlign: TextAlign.center,
                          ),
                        ),
                    ],
                  ),
                ),
                const SizedBox(height: 14),

                // Settings Rows & Colors
                GbCardContainer(
                  children: [
                    GbSettingRow(
                      icon: Icons.web_rounded,
                      title: 'Action Bar',
                      subtitle: 'Change Color of ActionBar on Chat Screen',
                      colorValue: prefs.actionBarColor,
                      onColorTap: () => _pickColor(
                        title: 'Action Bar Color',
                        currentColor: prefs.actionBarColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(actionBarColor: val),
                          );
                        },
                      ),
                    ),
                    GbSwitchTile(
                      icon: Icons.account_circle_outlined,
                      title: 'Hide contact profile picture',
                      subtitle: 'Removes the contact profile picture from the header in conversation screen',
                      value: prefs.hideProfilePicture,
                      onChanged: (val) {
                        widget.preferencesController.updateConversation(
                          prefs.copyWith(hideProfilePicture: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.person_off_outlined,
                      title: 'Hide Contact Name',
                      subtitle: 'Hides the name of the person you\'re talking to',
                      value: prefs.hideContactName,
                      onChanged: (val) {
                        widget.preferencesController.updateConversation(
                          prefs.copyWith(hideContactName: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.phone_disabled_rounded,
                      title: 'Hide Call Button',
                      subtitle: 'Hide call button from conversation action bar',
                      value: prefs.hideCallButton,
                      onChanged: (val) {
                        widget.preferencesController.updateConversation(
                          prefs.copyWith(hideCallButton: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.speaker_notes_off_rounded,
                      title: 'Disable Contact Status',
                      subtitle: 'Removes the Contact status line that\'s under the header in conversation',
                      value: prefs.disableContactStatus,
                      onChanged: (val) {
                        widget.preferencesController.updateConversation(
                          prefs.copyWith(disableContactStatus: val),
                        );
                      },
                    ),
                    GbSettingRow(
                      icon: Icons.format_paint_rounded,
                      title: 'Contact Status Background',
                      subtitle: 'Change General Contact status background in Conversation screen',
                      colorValue: prefs.contactStatusBgColor,
                      onColorTap: () => _pickColor(
                        title: 'Status Background Color',
                        currentColor: prefs.contactStatusBgColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(contactStatusBgColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.format_color_text_rounded,
                      title: 'Contact Status Text Color',
                      subtitle: 'Change the text of the General contact status in conversation screen',
                      colorValue: prefs.contactStatusTextColor,
                      onColorTap: () => _pickColor(
                        title: 'Status Text Color',
                        currentColor: prefs.contactStatusTextColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateConversation(
                            prefs.copyWith(contactStatusTextColor: val),
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
