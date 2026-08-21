import 'package:flutter/material.dart';
import '../../../ui/core/design_system/settings_primitives.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/controllers/appearance_variant_controller.dart';
import '../../../ui/core/theme/theme_controller.dart';
import '../../../ui/core/theme/theme_config.dart';
import '../../../injection/locator.dart';

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
  static const List<String> _homeStyles = [
    'Chaty Default',
    'Classic',
    'Compact',
    'Expressive',
    'Minimal',
    'Stories First',
    'Productivity',
    'Tablet Split View',
  ];

  static const List<String> _storiesStyles = [
    'Circular',
    'Squircle',
    'Card',
    'Minimal',
    'Compact',
  ];

  static const List<String> _avatarShapes = [
    'circle',
    'squircle',
    'roundedSquare',
  ];

  @override
  Widget build(BuildContext context) {
    final home = widget.preferencesController.home;

    return ChatySettingsPage(
      title: 'Home Screen Customization',
      subtitle: 'Styles, Stories Strip, Tabs, Header & Ghost Mode',
      children: [
        // Live Preview Card at Top
        ChatyPreviewCard(
          title: 'Live Home Layout Preview',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Current Style: ${home.homeStyle} • Stories: ${home.enableStoriesStrip ? "Visible" : "Hidden"} • Avatar: ${home.avatarShape}',
                style: TextStyle(
                  fontSize: 12,
                  color: Theme.of(context).textTheme.bodySmall?.color,
                ),
              ),
              const SizedBox(height: 8),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: home.headerBackgroundColorHex != 0
                      ? Color(home.headerBackgroundColorHex)
                      : Theme.of(context).cardColor,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: Theme.of(
                      context,
                    ).dividerColor.withValues(alpha: 0.2),
                  ),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        ChatyAvatar(
                          initials: 'AR',
                          color: const Color(0xFF6366F1),
                          size: 36,
                          shape: home.avatarShape,
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Text(
                            home.myNameOverride.isNotEmpty
                                ? home.myNameOverride
                                : 'Alex Rivera',
                            style: TextStyle(
                              fontWeight: FontWeight.bold,
                              color: home.headerTextColorHex != 0
                                  ? Color(home.headerTextColorHex)
                                  : null,
                            ),
                          ),
                        ),
                        if (home.ghostMode)
                          const Icon(
                            Icons.visibility_off_rounded,
                            size: 18,
                            color: Colors.purpleAccent,
                          ),
                      ],
                    ),
                    if (home.enableStoriesStrip) ...[
                      const SizedBox(height: 10),
                      Row(
                        children: [
                          Container(
                            padding: const EdgeInsets.all(2),
                            decoration: const BoxDecoration(
                              shape: BoxShape.circle,
                              gradient: LinearGradient(
                                colors: [
                                  Colors.pinkAccent,
                                  Colors.purpleAccent,
                                ],
                              ),
                            ),
                            child: ChatyAvatar(
                              initials: 'ER',
                              color: const Color(0xFFEC4899),
                              size: 28,
                              shape: home.avatarShape,
                            ),
                          ),
                          const SizedBox(width: 8),
                          Container(
                            padding: const EdgeInsets.all(2),
                            decoration: const BoxDecoration(
                              shape: BoxShape.circle,
                              gradient: LinearGradient(
                                colors: [
                                  Colors.purpleAccent,
                                  Colors.blueAccent,
                                ],
                              ),
                            ),
                            child: ChatyAvatar(
                              initials: 'DC',
                              color: const Color(0xFF10B981),
                              size: 28,
                              shape: home.avatarShape,
                            ),
                          ),
                          const SizedBox(width: 8),
                          Container(
                            padding: const EdgeInsets.all(2),
                            decoration: const BoxDecoration(
                              shape: BoxShape.circle,
                              gradient: LinearGradient(
                                colors: [
                                  Colors.amberAccent,
                                  Colors.orangeAccent,
                                ],
                              ),
                            ),
                            child: ChatyAvatar(
                              initials: 'ML',
                              color: const Color(0xFFF59E0B),
                              size: 28,
                              shape: home.avatarShape,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
        ),

        // Home UI Style
        ChatySettingsSection(
          title: 'Home UI Style Preset',
          description: 'Dynamically restructures the main home screen layout.',
          children: [
            ChatyChoiceTile<String>(
              title: 'Home Style',
              options: _homeStyles,
              selectedOption: home.homeStyle,
              optionLabel: (s) => s,
              onSelected: (style) {
                widget.preferencesController.updateHome(
                  home.copyWith(homeStyle: style),
                  logTitle: 'Home UI Style',
                );
              },
            ),
          ],
        ),

        // Instagram-Like Stories Strip
        ChatySettingsSection(
          title: 'Instagram-Like Stories Bar',
          description:
              'Horizontal story avatars positioned above the conversation list.',
          children: [
            ChatySwitchTile(
              icon: Icons.history_edu_rounded,
              iconColor: Colors.pinkAccent,
              title: 'Enable Stories Strip',
              subtitle: 'Show horizontal story avatars on home screen',
              value: home.enableStoriesStrip,
              onChanged: (val) {
                widget.preferencesController.updateHome(
                  home.copyWith(enableStoriesStrip: val),
                  logTitle: 'Stories Strip',
                );
              },
            ),
            if (home.enableStoriesStrip)
              ChatyChoiceTile<String>(
                title: 'Stories Avatar Shape',
                options: _storiesStyles,
                selectedOption: home.storiesStyle,
                optionLabel: (s) => s,
                onSelected: (style) {
                  widget.preferencesController.updateHome(
                    home.copyWith(storiesStyle: style),
                    logTitle: 'Stories Style',
                  );
                },
              ),
          ],
        ),

        // Chat / Group Organization
        ChatySettingsSection(
          title: 'Navigation & Tab Organization',
          children: [
            ListenableBuilder(
              listenable: locator<ThemeController>(),
              builder: (context, _) {
                final themeCtrl = locator<ThemeController>();
                final currentMode = themeCtrl.navigationMode;
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    ChatyChoiceTile<AppNavigationMode>(
                      title: 'Navigation Layout Architecture',
                      subtitle: 'Switch between the 5 primary navigation layouts (Top Bar, Island Rail, 3D Drawer, Side Menu, Bottom Bar)',
                      options: const [
                        AppNavigationMode.bottomNav,
                        AppNavigationMode.topWhatsAppBar,
                        AppNavigationMode.floatingIslandRail,
                        AppNavigationMode.perspective3DDrawer,
                        AppNavigationMode.modernSideMenu,
                        AppNavigationMode.gestureTabs,
                      ],
                      selectedOption: currentMode,
                      optionLabel: (mode) => switch (mode) {
                        AppNavigationMode.bottomNav => 'Bottom Nav Bar',
                        AppNavigationMode.topWhatsAppBar => 'Top WhatsApp Bar (Image 1)',
                        AppNavigationMode.floatingIslandRail => 'Floating Island Rail (Image 2)',
                        AppNavigationMode.perspective3DDrawer => '3D Perspective Drawer (Image 3)',
                        AppNavigationMode.modernSideMenu => 'Modern Side Menu (Image 4 & 5)',
                        AppNavigationMode.gestureTabs => 'Gesture Tabs',
                        AppNavigationMode.compactRail => 'Compact Rail',
                      },
                      onSelected: (mode) {
                        themeCtrl.setNavigationMode(mode);
                      },
                    ),
                  ],
                );
              },
            ),
            ListenableBuilder(
              listenable: locator<AppearanceVariantController>(),
              builder: (context, _) {
                final appearance = locator<AppearanceVariantController>();
                return ChatyChoiceTile<String>(
                  title: 'Bottom Navigation Bar Style',
                  subtitle: 'Select from 12 custom animated navigation designs (for Bottom Nav Bar)',
                  options: AppearanceVariantController.bottomBarStyles,
                  selectedOption: appearance.bottomBarStyle,
                  optionLabel: (s) => s,
                  onSelected: (style) {
                    appearance.setBottomBarStyle(style);
                  },
                );
              },
            ),
            ChatySwitchTile(
              icon: Icons.splitscreen_rounded,
              iconColor: Colors.indigoAccent,
              title: 'Separate Chats & Groups',
              subtitle:
                  'Reorganize navigation into Direct Messages & Groups tabs',
              value: home.separateChatsAndGroups,
              onChanged: (val) {
                widget.preferencesController.updateHome(
                  home.copyWith(separateChatsAndGroups: val),
                  logTitle: 'Separate Chats & Groups',
                );
              },
            ),
            ChatySwitchTile(
              icon: Icons.search_rounded,
              title: 'Show Top Search Bar',
              subtitle: 'Display interactive search bar on home header',
              value: home.showSearchBar,
              onChanged: (val) {
                widget.preferencesController.updateHome(
                  home.copyWith(showSearchBar: val),
                  logTitle: 'Show Search Bar',
                );
              },
            ),
            ChatySwitchTile(
              icon: Icons.camera_alt_rounded,
              title: 'Show Camera Action Icon',
              subtitle: 'Quick camera launch button on header',
              value: home.showCameraIcon,
              onChanged: (val) {
                widget.preferencesController.updateHome(
                  home.copyWith(showCameraIcon: val),
                  logTitle: 'Show Camera Icon',
                );
              },
            ),
          ],
        ),

        // User Identity Customization
        ChatySettingsSection(
          title: 'Header Identity & Avatars',
          children: [
            ChatySettingsTile(
              icon: Icons.badge_rounded,
              title: 'Display Name Override',
              subtitle: 'Current: "${home.myNameOverride}"',
              onTap: () {
                final ctrl = TextEditingController(text: home.myNameOverride);
                showDialog(
                  context: context,
                  builder: (ctx) => AlertDialog(
                    title: const Text('Set My Display Name'),
                    content: TextField(
                      controller: ctrl,
                      decoration: const InputDecoration(
                        border: OutlineInputBorder(),
                      ),
                    ),
                    actions: [
                      TextButton(
                        onPressed: () => Navigator.of(ctx).pop(),
                        child: const Text('Cancel'),
                      ),
                      ElevatedButton(
                        onPressed: () {
                          if (ctrl.text.isNotEmpty) {
                            widget.preferencesController.updateHome(
                              home.copyWith(myNameOverride: ctrl.text.trim()),
                              logTitle: 'Set My Name',
                            );
                            Navigator.of(ctx).pop();
                          }
                        },
                        child: const Text('Save'),
                      ),
                    ],
                  ),
                );
              },
            ),
            ChatyChoiceTile<String>(
              title: 'Avatar Shape',
              options: _avatarShapes,
              selectedOption: home.avatarShape,
              optionLabel: (s) => s[0].toUpperCase() + s.substring(1),
              onSelected: (shape) {
                widget.preferencesController.updateHome(
                  home.copyWith(avatarShape: shape),
                  logTitle: 'Avatar Shape',
                );
              },
            ),
          ],
        ),

        // Ghost Mode & Special Toggles
        ChatySettingsSection(
          title: 'Privacy Bundles & Modes',
          children: [
            ChatySwitchTile(
              icon: Icons.shield_moon_rounded,
              iconColor: Colors.purpleAccent,
              title: 'Ghost Mode',
              subtitle: home.ghostMode
                  ? 'Active: Hidden last seen, hidden online, disabled read receipts & typing indicators.'
                  : 'Activate total stealth privacy bundle with one tap.',
              value: home.ghostMode,
              onChanged: (val) {
                final priv = widget.preferencesController.privacy;
                widget.preferencesController.updateHome(
                  home.copyWith(ghostMode: val),
                  logTitle: 'Ghost Mode',
                );
                if (val) {
                  widget.preferencesController.updatePrivacy(
                    priv.copyWith(
                      freezeLastSeen: true,
                      readReceipts: false,
                      typingIndicators: false,
                      hideLastSeenAudience: 'Nobody',
                    ),
                  );
                }
              },
            ),
            ChatySwitchTile(
              icon: Icons.airplanemode_active_rounded,
              iconColor: Colors.amberAccent,
              title: 'Airplane Mode Simulator',
              subtitle:
                  'Pauses simulated incoming messages and network state transitions',
              value: home.airplaneModeSimulator,
              onChanged: (val) {
                widget.preferencesController.updateHome(
                  home.copyWith(airplaneModeSimulator: val),
                  logTitle: 'Airplane Mode',
                );
              },
            ),
          ],
        ),
      ],
    );
  }
}
