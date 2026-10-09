import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart';
import 'header_settings_screen.dart';
import 'home_rows_settings_screen.dart';
import 'home_fab_settings_screen.dart';
import 'status_settings_screen.dart';

/// Home Screen Settings matching Image 4.
/// Grouped in clean modern cards without hardcoded green borders, using global theme colors.
class HomeScreenSettingsPage extends StatefulWidget {
  final ChatyPreferencesController preferencesController;

  const HomeScreenSettingsPage({
    super.key,
    required this.preferencesController,
  });

  @override
  State<HomeScreenSettingsPage> createState() => _HomeScreenSettingsPageState();
}

class _HomeScreenSettingsPageState extends State<HomeScreenSettingsPage> {
  void _openSubScreen(String title, Widget child) {
    HapticFeedback.selectionClick();
    Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => child),
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
          'Home Screen',
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
            final homePrefs = widget.preferencesController.home;

            return ListView(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              children: [
                // Card 1: Sub-screens (Header, Rows, Floating Action Button, Status)
                _buildCardContainer(
                  theme: theme,
                  colors: colors,
                  isDark: isDark,
                  children: [
                    _buildSubpageRow(
                      icon: Icons.view_headline_rounded,
                      title: 'Header',
                      theme: theme,
                      colors: colors,
                      onTap: () => _openSubScreen(
                        'Header',
                        HeaderSettingsScreen(
                          preferencesController: widget.preferencesController,
                        ),
                      ),
                    ),
                    _buildDivider(colors),
                    _buildSubpageRow(
                      icon: Icons.view_agenda_outlined,
                      title: 'Rows',
                      theme: theme,
                      colors: colors,
                      onTap: () => _openSubScreen(
                        'Rows',
                        HomeRowsSettingsScreen(
                          preferencesController: widget.preferencesController,
                        ),
                      ),
                    ),
                    _buildDivider(colors),
                    _buildSubpageRow(
                      icon: Icons.add_circle_outline_rounded,
                      title: 'Floating Action Button',
                      theme: theme,
                      colors: colors,
                      onTap: () => _openSubScreen(
                        'Floating Action Button',
                        HomeFabSettingsScreen(
                          preferencesController: widget.preferencesController,
                        ),
                      ),
                    ),
                    _buildDivider(colors),
                    _buildSubpageRow(
                      icon: Icons.account_circle_outlined,
                      title: 'Status',
                      theme: theme,
                      colors: colors,
                      onTap: () => _openSubScreen(
                        'Status',
                        StatusSettingsScreen(
                          preferencesController: widget.preferencesController,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),

                // Card 2: Mods & Forward Settings
                _buildCardContainer(
                  theme: theme,
                  colors: colors,
                  isDark: isDark,
                  children: [
                    _buildSectionDivider(label: 'Mods', accent: theme.accentColor),
                    _buildSwitchRow(
                      icon: Icons.horizontal_rule_rounded,
                      title: 'Hide Chats Divider',
                      subtitle: 'Removes grey line between chats in Main Screen',
                      value: homePrefs.hideChatsDivider,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updateHome(
                          homePrefs.copyWith(hideChatsDivider: val),
                          logTitle: 'Hide Chats Divider',
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.person_off_outlined,
                      title: 'Hide unsaved numbers',
                      subtitle: 'If contact number not saved then it ll set default contact name',
                      value: homePrefs.hideUnsavedNumbers,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updateHome(
                          homePrefs.copyWith(hideUnsavedNumbers: val),
                          logTitle: 'Hide unsaved numbers',
                        );
                      },
                    ),
                    _buildSectionDivider(label: 'Forward Settings', accent: theme.accentColor),
                    _buildSwitchRow(
                      icon: Icons.person_search_outlined,
                      title: 'Frequently contacted',
                      subtitle: 'Hide Frequently contacted from Forward section',
                      value: homePrefs.hideFrequentlyContacted,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updateHome(
                          homePrefs.copyWith(hideFrequentlyContacted: val),
                          logTitle: 'Hide Frequently contacted',
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.group_outlined,
                      title: 'Other contacts',
                      subtitle: 'Hide Other contacts from Forward section',
                      value: homePrefs.hideOtherContacts,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updateHome(
                          homePrefs.copyWith(hideOtherContacts: val),
                          logTitle: 'Hide Other contacts',
                        );
                      },
                    ),
                    _buildSwitchRow(
                      icon: Icons.chat_bubble_outline_rounded,
                      title: 'Recent chats',
                      subtitle: 'Hide Recent chats from Forward section',
                      value: homePrefs.hideRecentChats,
                      theme: theme,
                      colors: colors,
                      onChanged: (val) {
                        widget.preferencesController.updateHome(
                          homePrefs.copyWith(hideRecentChats: val),
                          logTitle: 'Hide Recent chats',
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
    return Padding(
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
          Switch(
            value: value,
            activeColor: theme.accentColor,
            onChanged: (newVal) {
              HapticFeedback.selectionClick();
              onChanged(newVal);
            },
          ),
        ],
      ),
    );
  }
}
