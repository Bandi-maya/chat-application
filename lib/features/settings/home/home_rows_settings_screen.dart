import 'package:flutter/material.dart';

import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart';
import '../../../ui/core/design_system/gb_design_system.dart';

class HomeRowsSettingsScreen extends StatefulWidget {
  final ChatyPreferencesController preferencesController;

  const HomeRowsSettingsScreen({
    super.key,
    required this.preferencesController,
  });

  @override
  State<HomeRowsSettingsScreen> createState() => _HomeRowsSettingsScreenState();
}

class _HomeRowsSettingsScreenState extends State<HomeRowsSettingsScreen> {
  Color? _color(int? v) => v != null ? Color(v) : null;

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
        final prefs = widget.preferencesController.home;

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
              'Rows',
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
                // Top Live Chat Tile Preview matching Image 3 Rows screen
                GbCardContainer(
                  children: [
                    GbLivePreviewCard(
                      child: Row(
                        children: [
                          Stack(
                            clipBehavior: Clip.none,
                            children: [
                              CircleAvatar(
                                radius: 24,
                                backgroundColor: theme.accentColor.withValues(alpha: 0.15),
                                child: Text(
                                  'JD',
                                  style: TextStyle(
                                    fontWeight: FontWeight.bold,
                                    color: theme.primaryTextColor,
                                  ),
                                ),
                              ),
                              if (!prefs.disableOnlineDot)
                                Positioned(
                                  right: 0,
                                  bottom: 0,
                                  child: Container(
                                    width: 13,
                                    height: 13,
                                    decoration: BoxDecoration(
                                      color: _color(prefs.onlineDotColor) ?? Colors.greenAccent,
                                      shape: BoxShape.circle,
                                      border: Border.all(color: theme.surfaceColor, width: 2),
                                    ),
                                  ),
                                ),
                            ],
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                  children: [
                                    Text(
                                      'John Doe',
                                      style: TextStyle(
                                        color: _color(prefs.contactNameColor) ?? theme.primaryTextColor,
                                        fontSize: prefs.screenTextSize.toDouble(),
                                        fontWeight: FontWeight.w700,
                                      ),
                                    ),
                                    Text(
                                      prefs.elapsedTime ? '5m ago' : '10:42 AM',
                                      style: TextStyle(
                                        color: _color(prefs.contactOnlineColor) ?? theme.secondaryTextColor,
                                        fontSize: 12,
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 3),
                                Row(
                                  children: [
                                    Expanded(
                                      child: Text(
                                        'Hey, are you free for a call?',
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                        style: TextStyle(
                                          color: _color(prefs.textColor) ?? theme.secondaryTextColor,
                                          fontSize: (prefs.screenTextSize - 2).clamp(10, 22).toDouble(),
                                        ),
                                      ),
                                    ),
                                    const SizedBox(width: 8),
                                    Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                      decoration: BoxDecoration(
                                        color: _color(prefs.unreadCounterColor) ?? theme.accentColor,
                                        borderRadius: BorderRadius.circular(10),
                                      ),
                                      child: Text(
                                        '3',
                                        style: TextStyle(
                                          color: _color(prefs.unreadCounterTextColor) ?? Colors.white,
                                          fontSize: 11,
                                          fontWeight: FontWeight.bold,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),

                // Text Size Slider
                GbCardContainer(
                  children: [
                    GbSliderTile(
                      title: 'Main/Calls/Contacts Screen Text Size',
                      subtitle: 'Change the size of the text in the whole main screen',
                      value: prefs.screenTextSize.toDouble(),
                      min: 10,
                      max: 24,
                      divisions: 14,
                      onChanged: (val) {
                        widget.preferencesController.updateHome(
                          prefs.copyWith(screenTextSize: val),
                        );
                      },
                    ),
                  ],
                ),
                const SizedBox(height: 14),

                // Colors Controls
                GbCardContainer(
                  children: [
                    GbSettingRow(
                      icon: Icons.palette_outlined,
                      title: 'Home screen text color',
                      subtitle: 'Change general message text color',
                      colorHex: prefs.textColor,
                      onColorTap: () => _pickColor(
                        title: 'Home Screen Text Color',
                        currentColor: prefs.textColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateHome(
                            prefs.copyWith(textColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.person_rounded,
                      title: 'Contact Name Color',
                      subtitle: 'Change contact name color on home screen',
                      colorHex: prefs.contactNameColor,
                      onColorTap: () => _pickColor(
                        title: 'Contact Name Color',
                        currentColor: prefs.contactNameColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateHome(
                            prefs.copyWith(contactNameColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.mark_chat_unread_rounded,
                      title: 'Unread message counter color',
                      subtitle: 'Change the background color for the unread message counter indicator',
                      colorHex: prefs.unreadCounterColor,
                      onColorTap: () => _pickColor(
                        title: 'Unread Counter Background',
                        currentColor: prefs.unreadCounterColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateHome(
                            prefs.copyWith(unreadCounterColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.format_color_text_rounded,
                      title: 'Unread counter text color',
                      subtitle: 'Change the text color of the unread message counter',
                      colorHex: prefs.unreadCounterTextColor,
                      onColorTap: () => _pickColor(
                        title: 'Unread Counter Text Color',
                        currentColor: prefs.unreadCounterTextColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateHome(
                            prefs.copyWith(unreadCounterTextColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.wifi_tethering_rounded,
                      title: 'Contact Online Color',
                      subtitle: 'Change Color of Online in Main Screen',
                      colorHex: prefs.contactOnlineColor,
                      onColorTap: () => _pickColor(
                        title: 'Contact Online Color',
                        currentColor: prefs.contactOnlineColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateHome(
                            prefs.copyWith(contactOnlineColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.history_rounded,
                      title: 'Last Seen Color',
                      subtitle: 'Change Color of Last Seen in Main Screen',
                      colorHex: prefs.lastSeenColor,
                      onColorTap: () => _pickColor(
                        title: 'Last Seen Color',
                        currentColor: prefs.lastSeenColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateHome(
                            prefs.copyWith(lastSeenColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.alternate_email_rounded,
                      title: 'Mention Indicator Background',
                      subtitle: 'Change background for @ mention indicator',
                      colorHex: prefs.mentionIndicatorBgColor,
                      onColorTap: () => _pickColor(
                        title: 'Mention Indicator Background',
                        currentColor: prefs.mentionIndicatorBgColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateHome(
                            prefs.copyWith(mentionIndicatorBgColor: val),
                          );
                        },
                      ),
                    ),
                    GbSettingRow(
                      icon: Icons.tag_rounded,
                      title: 'Mention Icon Color',
                      subtitle: 'Change icon color for @ mention indicator',
                      colorHex: prefs.mentionIconColor,
                      onColorTap: () => _pickColor(
                        title: 'Mention Icon Color',
                        currentColor: prefs.mentionIconColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateHome(
                            prefs.copyWith(mentionIconColor: val),
                          );
                        },
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),

                // Toggles
                GbCardContainer(
                  children: [
                    GbSwitchTile(
                      icon: Icons.archive_outlined,
                      title: 'Hide Archived Chats',
                      subtitle: 'Hide archived chats button from conversations screen (needs restart)',
                      value: prefs.hideArchivedChats,
                      onChanged: (val) {
                        widget.preferencesController.updateHome(
                          prefs.copyWith(hideArchivedChats: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.unarchive_rounded,
                      title: 'Archive Chats on top',
                      subtitle: 'Show Archive Chats on top of chats',
                      value: prefs.archiveChatsOnTop,
                      onChanged: (val) {
                        widget.preferencesController.updateHome(
                          prefs.copyWith(archiveChatsOnTop: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.visibility_off_rounded,
                      title: 'Disable Contact Online/Last Seen status',
                      subtitle: 'Removes the Contact Online/Last seen Status from the Home Screen',
                      value: prefs.disableContactOnlineLastSeen,
                      onChanged: (val) {
                        widget.preferencesController.updateHome(
                          prefs.copyWith(disableContactOnlineLastSeen: val),
                        );
                      },
                    ),
                    GbSwitchTile(
                      icon: Icons.do_not_disturb_on_rounded,
                      title: 'Disable Online Dot',
                      subtitle: 'Hide online indicator dot next to contact avatar',
                      value: prefs.disableOnlineDot,
                      onChanged: (val) {
                        widget.preferencesController.updateHome(
                          prefs.copyWith(disableOnlineDot: val),
                        );
                      },
                    ),
                    GbSettingRow(
                      icon: Icons.circle_rounded,
                      title: 'Online Dot Color',
                      subtitle: 'Change the online indicator dot color',
                      colorHex: prefs.onlineDotColor,
                      onColorTap: () => _pickColor(
                        title: 'Online Dot Color',
                        currentColor: prefs.onlineDotColor,
                        onColorSelected: (val) {
                          widget.preferencesController.updateHome(
                            prefs.copyWith(onlineDotColor: val),
                          );
                        },
                      ),
                    ),
                    GbSwitchTile(
                      icon: Icons.access_time_rounded,
                      title: 'Elapsed time',
                      subtitle: 'Use Elapsed time (last sees 1hr and mins ago) in Chaty in Home and Conversation screen!',
                      value: prefs.elapsedTime,
                      onChanged: (val) {
                        widget.preferencesController.updateHome(
                          prefs.copyWith(elapsedTime: val),
                        );
                      },
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
