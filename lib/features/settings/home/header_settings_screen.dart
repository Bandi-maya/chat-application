import 'package:flutter/material.dart';
import '../../../data/repositories/chaty_data_store.dart';
import '../../../domain/models/preferences.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/gb_design_system.dart';

/// Header Settings Screen with pinned Live Preview at top matching Image 2 & 3.
/// Changes to settings update the live preview above lively and in real time.
class HeaderSettingsScreen extends StatefulWidget {
  final ChatyPreferencesController preferencesController;
  final ChatyDataStore? dataStore;

  const HeaderSettingsScreen({
    super.key,
    required this.preferencesController,
    this.dataStore,
  });

  @override
  State<HeaderSettingsScreen> createState() => _HeaderSettingsScreenState();
}

class _HeaderSettingsScreenState extends State<HeaderSettingsScreen> {
  static const List<String> _homeUiStyles = [
    'ONE UI',
    'WhatsApp UI Stock',
    'IOS STYLE',
    'BUBBLES TAB STYLE',
    'BASIC TAB STYLE',
    'WhatsApp OLD UI',
  ];

  static const List<String> _storiesStyles = [
    'Instagram',
    'Facebook',
    'Stock',
    'Circular',
    'Squircle',
    'Card',
  ];

  static const List<String> _pager3dStyles = [
    'Cube 3D',
    'Accordion Fold',
    'Card Flip',
    'Depth Zoom',
    'Stack Rotate',
  ];

  static const List<String> _tabBubbleStyles = [
    'Capsule Pill',
    'Glassmorphism Glow',
    'Soft Rounded Pill',
    'Outlined Bubble',
    'Segmented Tab',
  ];

  void _updateHome(HomePreferences newPrefs, {String? logTitle}) {
    widget.preferencesController.updateHome(newPrefs, logTitle: logTitle);
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.preferencesController,
      builder: (context, _) {
        final home = widget.preferencesController.home;
        final currentUser = widget.dataStore?.currentUser;
        final displayName = home.myNameOverride.isNotEmpty
            ? home.myNameOverride
            : (currentUser?.displayName.isNotEmpty == true
                ? currentUser!.displayName
                : 'Bandi Maya');
        final statusText = currentUser?.about.isNotEmpty == true
            ? currentUser!.about
            : 'Available';

        return Scaffold(
          backgroundColor: Colors.black,
          appBar: AppBar(
            backgroundColor: Colors.black,
            elevation: 0,
            leading: IconButton(
              icon: const Icon(Icons.arrow_back, color: Colors.white),
              onPressed: () => Navigator.of(context).pop(),
            ),
            title: const Text(
              'Header',
              style: TextStyle(
                color: Colors.white,
                fontSize: 19,
                fontWeight: FontWeight.w700,
                letterSpacing: -0.2,
              ),
            ),
          ),
          body: Column(
            children: [
              // 1. PINNED LIVE PREVIEW AT TOP (Image 2 & 3)
              GbLiveHeaderPreview(
                displayName: displayName,
                statusText: statusText,
                setMyName: home.setMyName,
                disableStatusUnderName: home.disableStatusUnderName,
                disableSearchBar: home.disableSearchBar,
                separateChatsAndGroups: home.separateChatsAndGroups,
                homeStyle: home.homeStyle,
                tabBubbleStyle: home.tabBubbleStyle,
                pagerTransition3d: home.pagerTransition3d,
                onThreeDotsClick: () {
                  GbHeaderOverflowMenu.show(
                    context,
                    onSettings: () => Navigator.of(context).pop(),
                  );
                },
              ),
              const SizedBox(height: 8),

              // 2. SCROLLABLE SETTINGS CARD (Image 2 & 3)
              Expanded(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.fromLTRB(14, 4, 14, 24),
                  child: Container(
                    decoration: BoxDecoration(
                      color: GbColors.surfaceCard,
                      borderRadius: BorderRadius.circular(24),
                      border: Border.all(
                        color: GbColors.borderSubtle,
                        width: 1.1,
                      ),
                    ),
                    clipBehavior: Clip.antiAlias,
                    child: Column(
                      children: [
                        // Home UI Style
                        GbSettingRow(
                          icon: const Icon(
                            Icons.layers_outlined,
                            color: GbColors.activeGreen,
                            size: 21,
                          ),
                          title: 'Home UI Style',
                          subtitle: home.homeStyle.isNotEmpty
                              ? home.homeStyle
                              : 'WhatsApp OLD UI',
                          showChevron: true,
                          onTap: () async {
                            final chosen = await GbRadioSelectionDialog.show(
                              context: context,
                              title: 'Home UI Style',
                              options: _homeUiStyles,
                              selectedOption: home.homeStyle.isNotEmpty
                                  ? home.homeStyle
                                  : 'WhatsApp OLD UI',
                            );
                            if (chosen != null && mounted) {
                              _updateHome(
                                home.copyWith(homeStyle: chosen),
                                logTitle: 'Home UI Style',
                              );
                            }
                          },
                        ),
                        _divider(),

                        // Tab Bubble Style
                        GbSettingRow(
                          icon: const Icon(
                            Icons.chat_bubble_outline_rounded,
                            color: GbColors.activeGreen,
                            size: 21,
                          ),
                          title: 'Tab Bubble Style',
                          subtitle: home.tabBubbleStyle.isNotEmpty
                              ? home.tabBubbleStyle
                              : 'Capsule Pill',
                          showChevron: true,
                          onTap: () async {
                            final chosen = await GbRadioSelectionDialog.show(
                              context: context,
                              title: 'Tab Bubble Style',
                              options: _tabBubbleStyles,
                              selectedOption: home.tabBubbleStyle.isNotEmpty
                                  ? home.tabBubbleStyle
                                  : 'Capsule Pill',
                            );
                            if (chosen != null && mounted) {
                              _updateHome(
                                home.copyWith(tabBubbleStyle: chosen),
                                logTitle: 'Tab Bubble Style',
                              );
                            }
                          },
                        ),
                        _divider(),

                        // 3D Pager / Slider Transition (5 options)
                        GbSettingRow(
                          icon: const Icon(
                            Icons.view_in_ar_rounded,
                            color: GbColors.activeGreen,
                            size: 21,
                          ),
                          title: '3D Pager / Slider Effect',
                          subtitle: home.pagerTransition3d.isNotEmpty
                              ? home.pagerTransition3d
                              : 'Cube 3D',
                          showChevron: true,
                          onTap: () async {
                            final chosen = await GbRadioSelectionDialog.show(
                              context: context,
                              title: '3D Slider Transition',
                              options: _pager3dStyles,
                              selectedOption: home.pagerTransition3d.isNotEmpty
                                  ? home.pagerTransition3d
                                  : 'Cube 3D',
                            );
                            if (chosen != null && mounted) {
                              _updateHome(
                                home.copyWith(pagerTransition3d: chosen),
                                logTitle: '3D Slider Effect',
                              );
                            }
                          },
                        ),
                        _divider(),

                        // Enable Instagram-like Stories
                        GbSettingRow(
                          icon: const Icon(
                            Icons.donut_large_rounded,
                            color: GbColors.activeGreen,
                            size: 21,
                          ),
                          title: 'Enable Instagram-like Stories',
                          switchValue: home.enableStoriesStrip,
                          onSwitchChanged: (val) {
                            _updateHome(
                              home.copyWith(enableStoriesStrip: val),
                              logTitle: 'Stories Strip',
                            );
                          },
                        ),
                        _divider(),

                        // Carousel View
                        GbSettingRow(
                          icon: const Icon(
                            Icons.bubble_chart_outlined,
                            color: GbColors.activeGreen,
                            size: 21,
                          ),
                          title: 'Carousel View',
                          subtitle: 'Enable Instagram-like Stories',
                          switchValue: home.carouselView,
                          onSwitchChanged: (val) {
                            _updateHome(
                              home.copyWith(carouselView: val),
                              logTitle: 'Carousel View',
                            );
                          },
                        ),
                        _divider(),

                        // Stories Style
                        GbSettingRow(
                          icon: const Icon(
                            Icons.bubble_chart_rounded,
                            color: GbColors.activeGreen,
                            size: 21,
                          ),
                          title: 'Stories Style',
                          subtitle: home.storiesStyle.isNotEmpty
                              ? home.storiesStyle
                              : 'Instagram',
                          showChevron: true,
                          onTap: () async {
                            final chosen = await GbRadioSelectionDialog.show(
                              context: context,
                              title: 'Stories Style',
                              options: _storiesStyles,
                              selectedOption: home.storiesStyle.isNotEmpty
                                  ? home.storiesStyle
                                  : 'Instagram',
                            );
                            if (chosen != null && mounted) {
                              _updateHome(
                                home.copyWith(storiesStyle: chosen),
                                logTitle: 'Stories Style',
                              );
                            }
                          },
                        ),
                        _divider(),

                        // Separate Chats/Groups
                        GbSettingRow(
                          icon: const Icon(
                            Icons.alt_route_rounded,
                            color: GbColors.activeGreen,
                            size: 21,
                          ),
                          title: 'Separate Chats/Groups',
                          subtitle:
                              'The Status page will be replaced with Group chats, and "Instagram-like stories" will be forced.',
                          switchValue: home.separateChatsAndGroups,
                          onSwitchChanged: (val) {
                            _updateHome(
                              home.copyWith(separateChatsAndGroups: val),
                              logTitle: 'Separate Chats/Groups',
                            );
                          },
                        ),
                        _divider(),

                        // Set My Name
                        GbSettingRow(
                          icon: const Icon(
                            Icons.badge_outlined,
                            color: GbColors.activeGreen,
                            size: 21,
                          ),
                          title: 'Set My Name',
                          subtitle:
                              'Set your name instead of Chaty in Home Screen',
                          switchValue: home.setMyName,
                          onSwitchChanged: (val) {
                            _updateHome(
                              home.copyWith(setMyName: val),
                              logTitle: 'Set My Name',
                            );
                          },
                        ),
                        _divider(),

                        // Disable Status under my name
                        GbSettingRow(
                          icon: const Icon(
                            Icons.event_busy_outlined,
                            color: GbColors.activeGreen,
                            size: 21,
                          ),
                          title: 'Disable Status under my name',
                          subtitle:
                              'Removes your status line under your name in Main Screen',
                          switchValue: home.disableStatusUnderName,
                          onSwitchChanged: (val) {
                            _updateHome(
                              home.copyWith(disableStatusUnderName: val),
                              logTitle: 'Disable Status',
                            );
                          },
                        ),
                        _divider(),

                        // Hide chat sort list
                        GbSettingRow(
                          icon: const Icon(
                            Icons.inbox_outlined,
                            color: GbColors.activeGreen,
                            size: 21,
                          ),
                          title: 'Hide chat sort list',
                          subtitle: 'Hide the chat sort list from the main screen.',
                          switchValue: home.hideChatSortList,
                          onSwitchChanged: (val) {
                            _updateHome(
                              home.copyWith(hideChatSortList: val),
                              logTitle: 'Hide Sort List',
                            );
                          },
                        ),
                        _divider(),

                        // Disable search bar
                        GbSettingRow(
                          icon: const Icon(
                            Icons.saved_search_outlined,
                            color: GbColors.activeGreen,
                            size: 21,
                          ),
                          title: 'Disable search bar',
                          subtitle:
                              'You can disable the search bar on the main screen',
                          switchValue: home.disableSearchBar,
                          onSwitchChanged: (val) {
                            _updateHome(
                              home.copyWith(disableSearchBar: val),
                              logTitle: 'Disable Search Bar',
                            );
                          },
                        ),
                        _divider(),

                        // Themes
                        GbSettingRow(
                          icon: const Icon(
                            Icons.palette_outlined,
                            color: GbColors.activeGreen,
                            size: 21,
                          ),
                          title: 'Themes',
                          switchValue: home.themesEnabled,
                          onSwitchChanged: (val) {
                            _updateHome(
                              home.copyWith(themesEnabled: val),
                              logTitle: 'Themes Enabled',
                            );
                          },
                        ),
                        _divider(),

                        // Show icon Add Account
                        GbSettingRow(
                          icon: const Icon(
                            Icons.person_add_alt_1_outlined,
                            color: GbColors.activeGreen,
                            size: 21,
                          ),
                          title: 'Show icon Add Account',
                          switchValue: home.showIconAddAccount,
                          onSwitchChanged: (val) {
                            _updateHome(
                              home.copyWith(showIconAddAccount: val),
                              logTitle: 'Show Add Account',
                            );
                          },
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _divider() {
    return const Divider(
      height: 1,
      thickness: 0.8,
      indent: 68,
      endIndent: 16,
      color: GbColors.divider,
    );
  }
}
