import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart';
import '../../../ui/core/design_system/gb_design_system.dart';
import 'conversation_action_bar_screen.dart';
import 'conversation_bubble_and_ticks_screen.dart';
import 'conversation_entry_style_screen.dart';
import 'conversation_more_options_screen.dart';

/// Conversation Screen Settings matching Image 5.
/// Grouped in clean modern cards without hardcoded green borders, using global theme colors.
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
  void _openSubScreen(Widget child) {
    HapticFeedback.selectionClick();
    Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => child),
    );
  }

  void _showColorPicker(String title, int currentColor, ValueChanged<int> onColorChanged) {
    GbColorPickerModal.show(
      context,
      title: title,
      currentColor: Color(currentColor),
      onColorChanged: (c) => onColorChanged(c.value),
    );
  }

  @override
  Widget build(BuildContext context) {
    final themeController = locator<ThemeController>();
    final theme = themeController.globalTheme;
    final colors = context.colors;
    final isDark = theme.brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: theme.backgroundColor,
      appBar: AppBar(
        backgroundColor: theme.backgroundColor,
        elevation: 0,
        scrolledUnderElevation: 0,
        leading: Padding(
          padding: const EdgeInsets.all(8.0),
          child: ChatyBackButton(
            onPressed: () => Navigator.of(context).pop(),
          ),
        ),
        title: Text(
          'Conversation Screen',
          style: TextStyle(
            color: theme.primaryTextColor,
            fontSize: 20 * theme.fontScale,
            fontWeight: FontWeight.w700,
            letterSpacing: -0.3,
          ),
        ),
      ),
      body: SafeArea(
        top: false,
        child: ListenableBuilder(
          listenable: widget.preferencesController,
          builder: (context, _) {
            final convPrefs = widget.preferencesController.conversation;

            return ListView(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              children: [
                // Card 1: Top Navigation Subpages
                _buildCardContainer(
                  theme: theme,
                  colors: colors,
                  isDark: isDark,
                  children: [
                    _buildSubpageRow(
                      icon: Icons.table_chart_outlined,
                      title: 'Action Bar',
                      theme: theme,
                      colors: colors,
                      onTap: () => _openSubScreen(
                        ConversationActionBarScreen(
                          preferencesController: widget.preferencesController,
                        ),
                      ),
                    ),
                    _buildDivider(colors),
                    _buildSubpageRow(
                      icon: Icons.chat_bubble_outline_rounded,
                      title: 'Bubble And Ticks',
                      theme: theme,
                      colors: colors,
                      onTap: () => _openSubScreen(
                        ConversationBubbleAndTicksScreen(
                          preferencesController: widget.preferencesController,
                        ),
                      ),
                    ),
                    _buildDivider(colors),
                    _buildSubpageRow(
                      icon: Icons.edit_note_rounded,
                      title: 'Conversation Entry style',
                      theme: theme,
                      colors: colors,
                      onTap: () => _openSubScreen(
                        ConversationEntryStyleScreen(
                          preferencesController: widget.preferencesController,
                        ),
                      ),
                    ),
                    _buildDivider(colors),
                    _buildSubpageRow(
                      icon: Icons.more_horiz_rounded,
                      title: 'More options',
                      theme: theme,
                      colors: colors,
                      onTap: () => _openSubScreen(
                        ConversationMoreOptionsScreen(
                          preferencesController: widget.preferencesController,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),

                // Card 2: Mods, Groups, Quick Replies, Options, Translate, Wallpaper, Voice Notes
                _buildCardContainer(
                  theme: theme,
                  colors: colors,
                  isDark: isDark,
                  children: [
                    // Section: Mods
                    _buildSectionDivider(label: 'Mods', accent: theme.accentColor),
                    _buildSwitchRow(
                      icon: Icons.link_rounded,
                      title: 'Switch direct contact link.',
                      subtitle: "Currently direct contact links are creating with the 'https://api.whatsapp.com/' prefix",
                      value: convPrefs.switchDirectContactLink,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updateConversation(
                          convPrefs.copyWith(switchDirectContactLink: val),
                          logTitle: 'Switch direct contact link',
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.sticky_note_2_outlined,
                      title: 'Confirm before sending a Sticker',
                      subtitle: 'Ask for confirmation before sending a sticker to chat',
                      value: convPrefs.confirmBeforeSendingSticker,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updateConversation(
                          convPrefs.copyWith(confirmBeforeSendingSticker: val),
                          logTitle: 'Confirm sticker',
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.attachment_rounded,
                      title: 'New Attachment Picker UI',
                      subtitle: 'Use modern bottom sheet attachment picker',
                      value: convPrefs.newAttachmentPickerUi,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updateConversation(
                          convPrefs.copyWith(newAttachmentPickerUi: val),
                          logTitle: 'Attachment picker',
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.copy_rounded,
                      title: 'Hide date and name',
                      subtitle: 'Hide the date and the name when copying 2 messages or more',
                      value: convPrefs.hideDateAndName,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updateConversation(
                          convPrefs.copyWith(hideDateAndName: val),
                          logTitle: 'Hide date and name on copy',
                        );
                      },
                    ),

                    // Section: Groups
                    _buildSectionDivider(label: 'Groups', accent: theme.accentColor),
                    _buildSwitchRow(
                      icon: Icons.admin_panel_settings_outlined,
                      title: 'Hide icon next to admin name',
                      subtitle: 'Hide the icon next to the admin name in groups',
                      value: convPrefs.hideAdminNameIcon,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updateConversation(
                          convPrefs.copyWith(hideAdminNameIcon: val),
                          logTitle: 'Hide admin icon',
                        );
                      },
                    ),
                    _buildChevronRow(
                      icon: Icons.verified_user_outlined,
                      title: 'Group admin icon',
                      subtitle: convPrefs.groupAdminIcon.isNotEmpty
                          ? convPrefs.groupAdminIcon
                          : 'Default',
                      theme: theme,
                      colors: colors,
                      onTap: () {
                        GbRadioSelectionModal.show<String>(
                          context: context,
                          title: 'Group Admin Icon',
                          options: const ['Default', 'Star', 'Shield', 'Crown', 'Badge'],
                          selectedOption: convPrefs.groupAdminIcon,
                          onSelected: (val) {
                            widget.preferencesController.updateConversation(
                              convPrefs.copyWith(groupAdminIcon: val),
                              logTitle: 'Group admin icon',
                            );
                          },
                        );
                      },
                    ),

                    // Section: Quick Replies
                    _buildSectionDivider(label: 'Quick Replies', accent: theme.accentColor),
                    _buildSwitchRow(
                      icon: Icons.view_sidebar_outlined,
                      title: 'Quick Contact Sidebar',
                      subtitle: convPrefs.enableQuickContactSidebar ? 'Enabled' : 'Disabled',
                      value: convPrefs.enableQuickContactSidebar,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updateConversation(
                          convPrefs.copyWith(enableQuickContactSidebar: val),
                          logTitle: 'Quick Contact Sidebar',
                        );
                      },
                    ),
                    _buildChevronRow(
                      icon: Icons.align_vertical_top_rounded,
                      title: 'Quick Contact Sidebar Position',
                      subtitle: convPrefs.quickContactSidebarPosition,
                      theme: theme,
                      colors: colors,
                      onTap: () {
                        final next = convPrefs.quickContactSidebarPosition == 'Top' ? 'Bottom' : 'Top';
                        widget.preferencesController.updateConversation(
                          convPrefs.copyWith(quickContactSidebarPosition: next),
                        );
                      },
                    ),
                    _buildColorRow(
                      icon: Icons.format_color_fill_rounded,
                      title: 'Quick Contact Background',
                      colorValue: convPrefs.quickContactBgColor,
                      theme: theme,
                      colors: colors,
                      onTap: () => _showColorPicker(
                        'Quick Contact Background',
                        convPrefs.quickContactBgColor,
                        (c) => widget.preferencesController.updateConversation(
                          convPrefs.copyWith(quickContactBgColor: c),
                        ),
                      ),
                    ),
                    _buildColorRow(
                      icon: Icons.color_lens_outlined,
                      title: 'Quick Contact Text Color',
                      colorValue: convPrefs.quickContactTextColor,
                      theme: theme,
                      colors: colors,
                      onTap: () => _showColorPicker(
                        'Quick Contact Text Color',
                        convPrefs.quickContactTextColor,
                        (c) => widget.preferencesController.updateConversation(
                          convPrefs.copyWith(quickContactTextColor: c),
                        ),
                      ),
                    ),
                    _buildSwitchRow(
                      icon: Icons.add_circle_outline_rounded,
                      title: 'Hide Chat FAB',
                      subtitle: 'Hide the plus button on the top right of the chat screen',
                      value: convPrefs.hideChatFab,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updateConversation(
                          convPrefs.copyWith(hideChatFab: val),
                          logTitle: 'Hide Chat FAB',
                        );
                      },
                    ),

                    // Section: Options Click on Hours
                    _buildSectionDivider(label: 'Options Click on Hours', accent: theme.accentColor),
                    _buildSwitchRow(
                      icon: Icons.menu_open_rounded,
                      title: 'iOS style pop-up menu',
                      subtitle: 'Show the new iOS style popup menu in chats',
                      value: convPrefs.iosStylePopupMenu,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updateConversation(
                          convPrefs.copyWith(iosStylePopupMenu: val),
                          logTitle: 'iOS style pop-up menu',
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.chat_bubble_outline_rounded,
                      title: 'Disable More Options Dialog from Bubble',
                      subtitle: 'You can disable more options dialog from chat bubble',
                      value: convPrefs.disableMoreOptionsFromBubble,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updateConversation(
                          convPrefs.copyWith(disableMoreOptionsFromBubble: val),
                          logTitle: 'Disable bubble options dialog',
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.touch_app_outlined,
                      title: 'Disable Double Tap Reaction',
                      subtitle: 'Suppress emoji reaction on double tapping a message',
                      value: convPrefs.disableDoubleTapReaction,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updateConversation(
                          convPrefs.copyWith(disableDoubleTapReaction: val),
                          logTitle: 'Disable double tap reaction',
                        );
                      },
                    ),

                    // Section: Translate Option Settings
                    _buildSectionDivider(label: 'Translate Option Settings', accent: theme.accentColor),
                    _buildChevronRow(
                      icon: Icons.translate_rounded,
                      title: 'Translate Option Settings',
                      subtitle: convPrefs.translateOptionSettings,
                      theme: theme,
                      colors: colors,
                      onTap: () {
                        GbRadioSelectionModal.show<String>(
                          context: context,
                          title: 'Translate Option Settings',
                          options: const [
                            'Server',
                            'In-outside apps',
                            'Server + In-outside apps',
                          ],
                          selectedOption: convPrefs.translateOptionSettings,
                          onSelected: (val) {
                            widget.preferencesController.updateConversation(
                              convPrefs.copyWith(translateOptionSettings: val),
                              logTitle: 'Translate option',
                            );
                          },
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.g_translate_rounded,
                      title: 'Hide Message Translation Icon',
                      subtitle: 'Dont show message translation icon in conversation entry',
                      value: convPrefs.hideMessageTranslationIcon,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updateConversation(
                          convPrefs.copyWith(hideMessageTranslationIcon: val),
                          logTitle: 'Hide message translation icon',
                        );
                      },
                    ),

                    // Section: Wallpaper
                    _buildSectionDivider(label: 'Wallpaper', accent: theme.accentColor),
                    _buildSwitchRow(
                      icon: Icons.wallpaper_rounded,
                      title: 'Custom Wallpaper per contact',
                      subtitle: 'Allows you to set custom wallpaper for each person/conversation',
                      value: convPrefs.customWallpaperPerContact,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updateConversation(
                          convPrefs.copyWith(customWallpaperPerContact: val),
                          logTitle: 'Custom wallpaper per contact',
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.portrait_rounded,
                      title: 'Profile Pic Wallpaper',
                      subtitle: 'Set profile pic as wallpaper if exists',
                      value: convPrefs.profilePicWallpaper,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updateConversation(
                          convPrefs.copyWith(profilePicWallpaper: val),
                          logTitle: 'Profile Pic Wallpaper',
                        );
                      },
                    ),

                    // Section: Voice Notes/Audio Mods
                    _buildSectionDivider(label: 'Voice Notes/Audio Mods', accent: theme.accentColor),
                    _buildSwitchRow(
                      icon: Icons.sensors_rounded,
                      title: 'Enable Proximity Sensor',
                      subtitle: 'Enabled by default. Disable to turn it off.',
                      value: convPrefs.enableProximitySensor,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updateConversation(
                          convPrefs.copyWith(enableProximitySensor: val),
                          logTitle: 'Enable Proximity Sensor',
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.volume_up_outlined,
                      title: 'Disable Output Switching',
                      subtitle: 'Prevents speaker/earpiece switching while playing',
                      value: convPrefs.disableOutputSwitching,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updateConversation(
                          convPrefs.copyWith(disableOutputSwitching: val),
                          logTitle: 'Disable Output Switching',
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.play_arrow_outlined,
                      title: 'Play Voice Notes',
                      subtitle: 'Disables continuous playback of voice notes',
                      value: convPrefs.playVoiceNotes,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updateConversation(
                          convPrefs.copyWith(playVoiceNotes: val),
                          logTitle: 'Play Voice Notes',
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.forward_rounded,
                      title: 'Forward as voice note',
                      subtitle: 'If enabled, audio messages will be forwarded as voice notes',
                      value: convPrefs.forwardAsVoiceNote,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updateConversation(
                          convPrefs.copyWith(forwardAsVoiceNote: val),
                          logTitle: 'Forward as voice note',
                        );
                      },
                    ),
                    _buildChevronRow(
                      icon: Icons.notifications_none_rounded,
                      title: 'Incoming message ringtone',
                      subtitle: convPrefs.incomingMessageRingtone.isNotEmpty
                          ? convPrefs.incomingMessageRingtone
                          : 'Default',
                      theme: theme,
                      colors: colors,
                      onTap: () {
                        GbRadioSelectionModal.show<String>(
                          context: context,
                          title: 'Incoming Message Ringtone',
                          options: const [
                            'Default',
                            'Soft Ping',
                            'Pop Bubble',
                            'Chime Bell',
                            'Silent',
                          ],
                          selectedOption: convPrefs.incomingMessageRingtone,
                          onSelected: (val) {
                            widget.preferencesController.updateConversation(
                              convPrefs.copyWith(incomingMessageRingtone: val),
                              logTitle: 'Incoming ringtone',
                            );
                          },
                        );
                      },
                    ),
                    _buildChevronRow(
                      icon: Icons.notification_important_outlined,
                      title: 'Send message ringtone',
                      subtitle: convPrefs.sendMessageRingtone.isNotEmpty
                          ? convPrefs.sendMessageRingtone
                          : 'Default',
                      theme: theme,
                      colors: colors,
                      onTap: () {
                        GbRadioSelectionModal.show<String>(
                          context: context,
                          title: 'Send Message Ringtone',
                          options: const [
                            'Default',
                            'Soft Ping',
                            'Pop Bubble',
                            'Chime Bell',
                            'Silent',
                          ],
                          selectedOption: convPrefs.sendMessageRingtone,
                          onSelected: (val) {
                            widget.preferencesController.updateConversation(
                              convPrefs.copyWith(sendMessageRingtone: val),
                              logTitle: 'Send ringtone',
                            );
                          },
                        );
                      },
                    ),
                    const SizedBox(height: 8),
                  ],
                ),
                const SizedBox(height: 32),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _buildCardContainer({
    required dynamic theme,
    required dynamic colors,
    required bool isDark,
    required List<Widget> children,
  }) {
    return Container(
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF1E2124) : colors.surface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: colors.borderSubtle,
          width: 0.9,
        ),
        boxShadow: [
          BoxShadow(
            color: colors.shadow.withValues(alpha: isDark ? 0.25 : 0.04),
            blurRadius: 10,
            offset: const Offset(0, 3),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: children,
        ),
      ),
    );
  }

  Widget _buildDivider(dynamic colors) {
    return Divider(
      height: 1,
      thickness: 0.8,
      indent: 64,
      endIndent: 16,
      color: colors.borderSubtle.withValues(alpha: 0.4),
    );
  }

  Widget _buildSectionDivider({
    required String label,
    required Color accent,
  }) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 16),
      child: Row(
        children: [
          Expanded(
            child: Container(
              height: 1.2,
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [
                    accent.withValues(alpha: 0.0),
                    accent.withValues(alpha: 0.7),
                  ],
                ),
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 10),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 5,
                  height: 5,
                  decoration: BoxDecoration(color: accent, shape: BoxShape.circle),
                ),
                const SizedBox(width: 6),
                Text(
                  label,
                  style: TextStyle(
                    color: accent,
                    fontWeight: FontWeight.w700,
                    fontSize: 13,
                    letterSpacing: 0.5,
                  ),
                ),
                const SizedBox(width: 6),
                Container(
                  width: 5,
                  height: 5,
                  decoration: BoxDecoration(color: accent, shape: BoxShape.circle),
                ),
              ],
            ),
          ),
          Expanded(
            child: Container(
              height: 1.2,
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [
                    accent.withValues(alpha: 0.7),
                    accent.withValues(alpha: 0.0),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSubpageRow({
    required IconData icon,
    required String title,
    required dynamic theme,
    required dynamic colors,
    required VoidCallback onTap,
  }) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () {
          HapticFeedback.selectionClick();
          onTap();
        },
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          child: Row(
            children: [
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: theme.accentColor.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(icon, color: theme.accentColor, size: 21),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Text(
                  title,
                  style: TextStyle(
                    color: theme.primaryTextColor,
                    fontSize: 15.5 * theme.fontScale,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              Icon(
                Icons.chevron_right_rounded,
                color: theme.accentColor,
                size: 22,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSwitchRow({
    required IconData icon,
    required String title,
    required String subtitle,
    required bool value,
    required dynamic theme,
    required dynamic colors,
    required ValueChanged<bool> onChanged,
  }) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () {
          HapticFeedback.selectionClick();
          onChanged(!value);
        },
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Container(
                width: 38,
                height: 38,
                decoration: BoxDecoration(
                  color: theme.accentColor.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(11),
                ),
                child: Icon(icon, color: theme.accentColor, size: 20),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      title,
                      style: TextStyle(
                        color: theme.primaryTextColor,
                        fontSize: 14.5 * theme.fontScale,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: TextStyle(
                        color: theme.secondaryTextColor,
                        fontSize: 11.5 * theme.fontScale,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              IgnorePointer(
                child: Switch(
                  value: value,
                  activeColor: theme.accentColor,
                  onChanged: null,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildChevronRow({
    required IconData icon,
    required String title,
    required String subtitle,
    required dynamic theme,
    required dynamic colors,
    required VoidCallback onTap,
  }) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () {
          HapticFeedback.selectionClick();
          onTap();
        },
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 11),
          child: Row(
            children: [
              Container(
                width: 38,
                height: 38,
                decoration: BoxDecoration(
                  color: theme.accentColor.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(11),
                ),
                child: Icon(icon, color: theme.accentColor, size: 20),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      title,
                      style: TextStyle(
                        color: theme.primaryTextColor,
                        fontSize: 14.5 * theme.fontScale,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: TextStyle(
                        color: theme.secondaryTextColor,
                        fontSize: 11.5 * theme.fontScale,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Icon(
                Icons.chevron_right_rounded,
                color: theme.accentColor,
                size: 22,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildColorRow({
    required IconData icon,
    required String title,
    required int colorValue,
    required dynamic theme,
    required dynamic colors,
    required VoidCallback onTap,
  }) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () {
          HapticFeedback.selectionClick();
          onTap();
        },
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 11),
          child: Row(
            children: [
              Container(
                width: 38,
                height: 38,
                decoration: BoxDecoration(
                  color: theme.accentColor.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(11),
                ),
                child: Icon(icon, color: theme.accentColor, size: 20),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Text(
                  title,
                  style: TextStyle(
                    color: theme.primaryTextColor,
                    fontSize: 14.5 * theme.fontScale,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              Container(
                width: 26,
                height: 26,
                decoration: BoxDecoration(
                  color: Color(colorValue),
                  shape: BoxShape.circle,
                  border: Border.all(color: colors.borderSubtle, width: 1.5),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
