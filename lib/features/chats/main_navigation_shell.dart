import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../data/repositories/chaty_data_store.dart';
import '../../data/services/notification_service.dart';
import '../../domain/models/conversation.dart';
import '../../ui/core/controllers/appearance_variant_controller.dart';
import '../../ui/core/controllers/preferences_controller.dart';
import '../../ui/core/gb/gb_theme_overrides.dart';
import '../calls/calls_screen.dart';
import '../settings/settings_root_screen.dart';
import '../tasks/tasks_screen.dart';
import '../updates/updates_screen.dart';
import 'chats_home_screen.dart';
import 'linked_devices_qr_screen.dart';
import '../../injection/locator.dart';
import '../../ui/core/design_system/design_system.dart';
import '../../ui/core/widgets/app_avatar.dart';
import '../../ui/core/templates/template_shell.dart';

class MainNavigationShell extends StatefulWidget {
  const MainNavigationShell({super.key});

  @override
  State<MainNavigationShell> createState() => _MainNavigationShellState();
}

class _MainNavigationShellState extends State<MainNavigationShell> {
  int _currentIndex = 0;
  DateTime? _lastExitAttempt;
  late final PageController _pageController;

  @override
  void initState() {
    super.initState();
    _pageController = PageController(initialPage: _currentIndex);
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  void _selectRootDestination(int next) {
    if (next == _currentIndex) return;
    HapticFeedback.selectionClick();
    setState(() => _currentIndex = next);
    if (_pageController.hasClients && _pageController.page?.round() != next) {
      _pageController.animateToPage(
        next,
        duration: const Duration(milliseconds: 260),
        curve: Curves.easeOutCubic,
      );
    }
  }

  Future<void> _handleRootBack() async {
    if (_currentIndex != 0) {
      _selectRootDestination(0);
      return;
    }
    final now = DateTime.now();
    final previous = _lastExitAttempt;
    if (previous == null ||
        now.difference(previous) > const Duration(seconds: 2)) {
      _lastExitAttempt = now;
      if (mounted) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(
            const SnackBar(
              content: Text('Press back again to exit Chaty'),
              duration: Duration(seconds: 2),
            ),
          );
      }
      return;
    }
    await SystemNavigator.pop();
  }

  @override
  Widget build(BuildContext context) {
    final themeController = locator<ThemeController>();
    final dataStore = locator<ChatyDataStore>();
    final preferencesController = locator<ChatyPreferencesController>();
    final appearanceController = locator<AppearanceVariantController>();
    final notificationService = locator<ChatyNotificationService>();

    return ListenableBuilder(
      listenable: Listenable.merge(<Listenable>[
        themeController,
        preferencesController,
        appearanceController,
        dataStore,
      ]),
      builder: (context, _) {
        final theme = GbThemeOverrides.resolve(
          themeController.globalTheme,
          preferencesController,
        );
        // Single source of truth: the structured Home setting.
        final separateGroups =
            preferencesController.home.separateChatsAndGroups;
        final showDesktopIcon = preferencesController.home.showDesktopIcon;

        // Base candidate destinations
        final allDestinations = <_NavDestinationItem>[
          _NavDestinationItem(
            id: 'chats',
            label: 'Chats',
            icon: Icons.chat_bubble_outline_rounded,
            activeIcon: Icons.chat_bubble_rounded,
            builder: (ctx) => ChatsHomeScreen(
              theme: theme,
              dataStore: dataStore,
              preferencesController: preferencesController,
              themeController: themeController,
              notificationService: notificationService,
              forcedType: separateGroups ? ConversationType.direct : null,
              pageTitle: separateGroups ? 'Chats' : null,
            ),
          ),
          if (separateGroups)
            _NavDestinationItem(
              id: 'groups',
              label: 'Groups',
              icon: Icons.groups_outlined,
              activeIcon: Icons.groups_rounded,
              builder: (ctx) => ChatsHomeScreen(
                theme: theme,
                dataStore: dataStore,
                preferencesController: preferencesController,
                themeController: themeController,
                notificationService: notificationService,
                forcedType: ConversationType.group,
                pageTitle: 'Groups',
              ),
            ),
          _NavDestinationItem(
            id: 'updates',
            label: 'Updates',
            icon: Icons.update_outlined,
            activeIcon: Icons.update_rounded,
            builder: (ctx) => UpdatesScreen(
              theme: theme,
              dataStore: dataStore,
              preferencesController: preferencesController,
            ),
          ),
          _NavDestinationItem(
            id: 'tasks',
            label: 'Tasks',
            icon: Icons.checklist_rtl_rounded,
            activeIcon: Icons.checklist_rounded,
            builder: (ctx) => TasksScreen(theme: theme, dataStore: dataStore),
          ),
          _NavDestinationItem(
            id: 'calls',
            label: 'Calls',
            icon: Icons.call_outlined,
            activeIcon: Icons.call_rounded,
            builder: (ctx) => CallsScreen(theme: theme, dataStore: dataStore),
          ),
          _NavDestinationItem(
            id: 'settings',
            label: 'Settings',
            icon: Icons.settings_outlined,
            activeIcon: Icons.settings_rounded,
            builder: (ctx) => SettingsRootScreen(
              preferencesController: preferencesController,
              themeController: themeController,
              dataStore: dataStore,
              notificationService: notificationService,
            ),
          ),
          if (!showDesktopIcon)
            _NavDestinationItem(
              id: 'desktop',
              label: 'Desktop',
              icon: Icons.devices_rounded,
              activeIcon: Icons.devices_rounded,
              builder: (ctx) => LinkedDevicesQrScreen(
                dataStore: dataStore,
                relationshipService: locator(),
                preferencesController: preferencesController,
                themeController: themeController,
                devicesOnly: true,
              ),
            ),
        ];

        // Apply max 4 direct navigation items rule:
        // <= 4: Display all directly
        // > 4: Display first 3 + More as 4th item
        final bool hasOverflow = allDestinations.length > 4;
        final List<_NavDestinationItem> primaryDestinations = hasOverflow
            ? allDestinations.take(3).toList()
            : allDestinations;
        final List<_NavDestinationItem> overflowDestinations = hasOverflow
            ? allDestinations.skip(3).toList()
            : const <_NavDestinationItem>[];

        final List<_NavDestinationItem> navItems = [
          ...primaryDestinations,
          if (hasOverflow)
            const _NavDestinationItem(
              id: 'more',
              label: 'More',
              icon: Icons.more_horiz_rounded,
              activeIcon: Icons.more_horiz_rounded,
            ),
        ];

        final List<Widget> screens = allDestinations
            .map((item) => item.builder(context))
            .toList(growable: false);

        final effectiveIndex = _currentIndex.clamp(
          0,
          allDestinations.length - 1,
        );
        if (effectiveIndex != _currentIndex) {
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (mounted) setState(() => _currentIndex = 0);
          });
        }

        // Active index in bottom navigation bar
        final int bottomNavSelectedIndex = hasOverflow
            ? (effectiveIndex < 3 ? effectiveIndex : 3)
            : effectiveIndex;

        return PopScope(
          canPop: false,
          onPopInvokedWithResult: (didPop, result) {
            if (!didPop) _handleRootBack();
          },
          child: LayoutBuilder(
            builder: (context, constraints) {
              final navIndex = appearanceController.navigationIndex;
              final forceRail = <int>{
                0,
                1,
                2,
                3,
                4,
                5,
                6,
                7,
                8,
                9,
                17,
                18,
              }.contains(navIndex);
              final autoRail = constraints.maxWidth >= (forceRail ? 720 : 900);
              final navMode = themeController.navigationMode;

              // 1. TOP WHATSAPP-STYLE GREEN TAB BAR (Image 1)
              if (navMode == AppNavigationMode.topWhatsAppBar) {
                return _buildTopWhatsAppShell(
                  theme: theme,
                  screens: screens,
                  navItems: allDestinations,
                  selectedIndex: effectiveIndex,
                );
              }

              // 2. FLOATING DARK ISLAND RAIL (Image 2)
              if (navMode == AppNavigationMode.floatingIslandRail) {
                return _buildFloatingIslandRailShell(
                  theme: theme,
                  content: PageView(
                    controller: _pageController,
                    onPageChanged: (idx) {
                      if (_currentIndex != idx) {
                        setState(() => _currentIndex = idx);
                      }
                    },
                    children: screens,
                  ),
                  navItems: allDestinations,
                  selectedIndex: effectiveIndex,
                );
              }

              // 3. 3D PERSPECTIVE DRAWER MENU (Image 3)
              if (navMode == AppNavigationMode.perspective3DDrawer) {
                return _build3DPerspectiveDrawerShell(
                  theme: theme,
                  screens: screens,
                  navItems: allDestinations,
                  selectedIndex: effectiveIndex,
                );
              }

              // 4. MODERN SIDE MENU DRAWER (Image 4 & 5)
              if (navMode == AppNavigationMode.modernSideMenu) {
                return _buildModernSideMenuShell(
                  theme: theme,
                  screens: screens,
                  navItems: allDestinations,
                  selectedIndex: effectiveIndex,
                );
              }

              // 5. STANDARD BOTTOM NAVIGATION / GESTURE TABS / ADAPTIVE RAIL
              final layoutMode = themeController.layoutMode;
              final useRail =
                  navMode == AppNavigationMode.compactRail ||
                  (navMode != AppNavigationMode.gestureTabs &&
                      (layoutMode == UILayoutMode.tabletDesktop || autoRail));
              Widget content = PageView(
                controller: _pageController,
                physics: navMode == AppNavigationMode.gestureTabs
                    ? const BouncingScrollPhysics()
                    : const PageScrollPhysics(),
                onPageChanged: (idx) {
                  if (_currentIndex != idx) {
                    setState(() => _currentIndex = idx);
                  }
                },
                children: screens,
              );
              if (useRail) {
                return _buildRailShell(
                  theme: theme,
                  content: content,
                  appearance: appearanceController,
                  maxWidth: constraints.maxWidth,
                  navItems: allDestinations,
                  selectedIndex: effectiveIndex,
                );
              }
              return _buildBottomShell(
                theme: theme,
                content: content,
                appearance: appearanceController,
                navItems: navItems,
                selectedIndex: bottomNavSelectedIndex,
                onDestinationTap: (idx) {
                  ChatyHaptics.selection();
                  if (hasOverflow && idx == 3) {
                    _showMoreMenu(
                      context,
                      theme: theme,
                      overflowDestinations: overflowDestinations,
                      onSelect: (overflowIdx) {
                        final realIndex = 3 + overflowIdx;
                        _selectRootDestination(realIndex);
                      },
                    );
                  } else {
                    _selectRootDestination(idx);
                  }
                },
              );
            },
          ),
        );
      },
    );
  }

  // ---------------------------------------------------------------------------
  // 1. Top WhatsApp Style Tab Bar (Image 1)
  // ---------------------------------------------------------------------------
  Widget _buildTopWhatsAppShell({
    required dynamic theme,
    required List<Widget> screens,
    required List<_NavDestinationItem> navItems,
    required int selectedIndex,
  }) {
    final colors = context.colors;
    final brandPrimary = theme.accentColor as Color;
    final indicatorColor = colors.onPrimary;

    return DefaultTabController(
      length: navItems.length,
      initialIndex: selectedIndex,
      child: Scaffold(
        backgroundColor: theme.backgroundColor,
        body: SafeArea(
          bottom: false,
          child: Column(
            children: [
              Container(
                color: brandPrimary,
                child: TabBar(
                  isScrollable: navItems.length > 4,
                  indicatorColor: indicatorColor,
                  indicatorWeight: 3.5,
                  labelColor: colors.onPrimary,
                  unselectedLabelColor: colors.onPrimary.withValues(
                    alpha: 0.72,
                  ),
                  labelStyle: const TextStyle(
                    fontWeight: FontWeight.w700,
                    fontSize: 14,
                    letterSpacing: 0.2,
                  ),
                  unselectedLabelStyle: const TextStyle(
                    fontWeight: FontWeight.w500,
                    fontSize: 14,
                  ),
                  tabs: navItems.map((item) => Tab(text: item.label)).toList(),
                  onTap: _selectRootDestination,
                ),
              ),
              Expanded(
                child: IndexedStack(index: selectedIndex, children: screens),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // 2. Floating Island Rail (Image 2)
  // ---------------------------------------------------------------------------
  Widget _buildFloatingIslandRailShell({
    required dynamic theme,
    required Widget content,
    required List<_NavDestinationItem> navItems,
    required int selectedIndex,
  }) {
    final colors = context.colors;

    return Scaffold(
      backgroundColor: theme.backgroundColor,
      body: SafeArea(
        child: Row(
          children: [
            Container(
              width: 68,
              margin: const EdgeInsets.fromLTRB(10, 12, 0, 12),
              decoration: BoxDecoration(
                color: colors.surface,
                borderRadius: BorderRadius.circular(30),
                boxShadow: [
                  BoxShadow(
                    color: colors.shadow,
                    blurRadius: 18,
                    offset: const Offset(0, 4),
                  ),
                ],
              ),
              child: Column(
                children: [
                  const SizedBox(height: 14),
                  // Traffic dots indicator
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      CircleAvatar(radius: 3.5, backgroundColor: colors.error),
                      const SizedBox(width: 4),
                      CircleAvatar(
                        radius: 3.5,
                        backgroundColor: colors.warning,
                      ),
                      const SizedBox(width: 4),
                      CircleAvatar(
                        radius: 3.5,
                        backgroundColor: colors.success,
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  // Logo mark
                  Icon(Icons.bolt_rounded, color: colors.primary, size: 24),
                  const SizedBox(height: 16),
                  Divider(
                    color: colors.divider,
                    height: 1,
                    indent: 12,
                    endIndent: 12,
                  ),
                  const SizedBox(height: 12),
                  Expanded(
                    child: ListView.builder(
                      itemCount: navItems.length,
                      itemBuilder: (context, i) {
                        final item = navItems[i];
                        final isSel = selectedIndex == i;
                        return Padding(
                          padding: const EdgeInsets.symmetric(
                            vertical: 6,
                            horizontal: 10,
                          ),
                          child: InkWell(
                            onTap: () => _selectRootDestination(i),
                            borderRadius: BorderRadius.circular(16),
                            child: AnimatedContainer(
                              duration: const Duration(milliseconds: 200),
                              padding: const EdgeInsets.all(10),
                              decoration: BoxDecoration(
                                color: isSel
                                    ? colors.primary
                                    : Colors.transparent,
                                borderRadius: BorderRadius.circular(16),
                              ),
                              child: Icon(
                                isSel ? item.activeIcon : item.icon,
                                size: 20,
                                color: isSel
                                    ? colors.onPrimary
                                    : colors.foregroundSecondary,
                              ),
                            ),
                          ),
                        );
                      },
                    ),
                  ),
                  Divider(
                    color: colors.divider,
                    height: 1,
                    indent: 12,
                    endIndent: 12,
                  ),
                  const SizedBox(height: 12),
                  // Real signed-in user identity (was a hardcoded letter).
                  Builder(
                    builder: (context) {
                      final user = locator<ChatyDataStore>().currentUser;
                      return CircleAvatar(
                        radius: 16,
                        backgroundColor: colors.surfaceSecondary,
                        child: Text(
                          user.avatarInitials.isNotEmpty
                              ? user.avatarInitials
                              : 'CU',
                          style: TextStyle(
                            color: colors.foreground,
                            fontWeight: FontWeight.bold,
                            fontSize: 13,
                          ),
                        ),
                      );
                    },
                  ),
                  const SizedBox(height: 14),
                ],
              ),
            ),
            Expanded(child: content),
          ],
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // 3. 3D Perspective Drawer (Image 3)
  // ---------------------------------------------------------------------------
  Widget _build3DPerspectiveDrawerShell({
    required dynamic theme,
    required List<Widget> screens,
    required List<_NavDestinationItem> navItems,
    required int selectedIndex,
  }) {
    return _PerspectiveDrawerScaffold(
      theme: theme,
      selectedIndex: selectedIndex,
      navItems: navItems,
      onSelect: _selectRootDestination,
      child: IndexedStack(index: selectedIndex, children: screens),
    );
  }

  // ---------------------------------------------------------------------------
  // 4. Modern Side Menu Drawer (Image 4 & 5)
  // ---------------------------------------------------------------------------
  Widget _buildModernSideMenuShell({
    required dynamic theme,
    required List<Widget> screens,
    required List<_NavDestinationItem> navItems,
    required int selectedIndex,
  }) {
    final GlobalKey<ScaffoldState> scaffoldKey = GlobalKey<ScaffoldState>();
    final colors = context.colors;
    final prefs = locator<ChatyPreferencesController>();
    final dataStore = locator<ChatyDataStore>();
    final user = dataStore.currentUser;

    final displayName = prefs.home.myNameOverride.isNotEmpty
        ? prefs.home.myNameOverride
        : user.displayName.isNotEmpty
        ? user.displayName
        : 'Chaty User';

    return Scaffold(
      key: scaffoldKey,
      backgroundColor: theme.backgroundColor,
      drawer: Drawer(
        backgroundColor: colors.surfaceSecondary,
        child: SafeArea(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.all(20),
                child: Row(
                  children: [
                    AppAvatar(
                      initials: user.avatarInitials.isNotEmpty
                          ? user.avatarInitials
                          : 'CU',
                      colorHex: user.avatarColorHex,
                      size: 48,
                      showOnlineBadge: true,
                      presence: user.presence,
                    ),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            displayName,
                            style: TextStyle(
                              color: colors.foreground,
                              fontWeight: FontWeight.w800,
                              fontSize: 16,
                            ),
                          ),
                          Text(
                            '@${user.username.isNotEmpty ? user.username : 'chaty'}',
                            style: TextStyle(
                              color: colors.foregroundSecondary,
                              fontSize: 12,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              Divider(color: colors.divider, height: 1),
              Expanded(
                child: ListView.builder(
                  padding: const EdgeInsets.symmetric(
                    vertical: 12,
                    horizontal: 10,
                  ),
                  itemCount: navItems.length,
                  itemBuilder: (context, i) {
                    final item = navItems[i];
                    final isSel = selectedIndex == i;
                    return ListTile(
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(14),
                      ),
                      selected: isSel,
                      selectedTileColor: theme.accentColor.withValues(
                        alpha: 0.16,
                      ),
                      leading: Icon(
                        isSel ? item.activeIcon : item.icon,
                        color: isSel
                            ? theme.accentColor
                            : colors.foregroundSecondary,
                      ),
                      title: Text(
                        item.label,
                        style: TextStyle(
                          color: isSel
                              ? colors.foreground
                              : colors.foregroundSecondary,
                          fontWeight: isSel ? FontWeight.w700 : FontWeight.w500,
                        ),
                      ),
                      onTap: () {
                        Navigator.of(context).pop();
                        _selectRootDestination(i);
                      },
                    );
                  },
                ),
              ),
            ],
          ),
        ),
      ),
      body: Column(
          children: [
            TemplateShellHeader(
              title: navItems[selectedIndex].label,
              navigation: IconButton(
                icon: const Icon(Icons.menu_rounded),
                color: theme.primaryTextColor,
                tooltip: 'Open menu',
                onPressed: () => scaffoldKey.currentState?.openDrawer(),
              ),
              backgroundColor: theme.surfaceColor,
              foregroundColor: theme.primaryTextColor,
              dividerColor: colors.border,
            ),
            Expanded(
              child: IndexedStack(index: selectedIndex, children: screens),
            ),
          ],
      ),
    );
  }

  Widget _buildRailShell({
    required dynamic theme,
    required Widget content,
    required AppearanceVariantController appearance,
    required double maxWidth,
    required List<_NavDestinationItem> navItems,
    required int selectedIndex,
  }) {
    final index = appearance.navigationIndex;
    final compact = <int>{1, 2, 6, 7, 15, 19}.contains(index);
    final showAllLabels =
        <int>{0, 3, 4, 8, 9, 17, 18}.contains(index) && maxWidth >= 900;
    final indicatorRadius = <double>[
      18,
      12,
      8,
      24,
      10,
      20,
      8,
      30,
      14,
      20,
      16,
      24,
      12,
      22,
      14,
      8,
      24,
      10,
      16,
      8,
    ][index];

    return Scaffold(
      backgroundColor: theme.backgroundColor,
      body: SafeArea(
        child: Row(
          children: [
            NavigationRail(
              selectedIndex: selectedIndex,
              onDestinationSelected: _selectRootDestination,
              minWidth: compact ? 58 : 72,
              minExtendedWidth: 190,
              extended: showAllLabels,
              groupAlignment: <int>{5, 16}.contains(index) ? 0 : -0.72,
              backgroundColor: theme.surfaceColor,
              indicatorColor: theme.accentColor.withValues(
                alpha: <int>{2, 7, 9, 19}.contains(index) ? 0.08 : 0.16,
              ),
              indicatorShape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(indicatorRadius),
              ),
              selectedIconTheme: IconThemeData(
                color: theme.accentColor,
                size: compact ? 20 : 23,
              ),
              unselectedIconTheme: IconThemeData(
                color: theme.secondaryTextColor,
                size: compact ? 19 : 21,
              ),
              selectedLabelTextStyle: TextStyle(
                color: theme.primaryTextColor,
                fontWeight: FontWeight.w700,
              ),
              unselectedLabelTextStyle: TextStyle(
                color: theme.secondaryTextColor,
              ),
              labelType: showAllLabels
                  ? NavigationRailLabelType.none
                  : NavigationRailLabelType.selected,
              destinations: navItems
                  .map(
                    (item) => NavigationRailDestination(
                      icon: Icon(item.icon),
                      selectedIcon: Icon(item.activeIcon),
                      label: Text(item.label),
                    ),
                  )
                  .toList(growable: false),
            ),
            VerticalDivider(width: 1, color: theme.cardColor),
            Expanded(child: content),
          ],
        ),
      ),
    );
  }

  Widget _buildBottomShell({
    required dynamic theme,
    required Widget content,
    required AppearanceVariantController appearance,
    required List<_NavDestinationItem> navItems,
    required int selectedIndex,
    required ValueChanged<int> onDestinationTap,
  }) {
    final styleName = appearance.bottomBarStyle;
    final isDark = theme.brightness == Brightness.dark;
    final colors = context.colors;
    final accent = theme.accentColor as Color;
    final bg = theme.backgroundColor as Color;

    // Sizing & layout properties per style
    final double sideMargin = switch (styleName) {
      'Floating Pill' => 16.0,
      'Active Pill Chip' => 14.0,
      'Top Indicator Line' => 0.0,
      'Bottom Indicator Dot' => 0.0,
      'Circle Accent Pop' => 12.0,
      'Curved Notch Teardrop' => 0.0,
      'Floating Dynamic Island' => 20.0,
      'Raised Center Action' => 0.0,
      'Segmented Glass Dock' => 14.0,
      'Minimal Icon Dock' => 24.0,
      'Classic Label Bar' => 0.0,
      'Soft Square Tile' => 0.0,
      _ => 14.0,
    };

    final double bottomMargin = switch (styleName) {
      'Floating Pill' => 12.0,
      'Active Pill Chip' => 10.0,
      'Top Indicator Line' => 0.0,
      'Bottom Indicator Dot' => 0.0,
      'Circle Accent Pop' => 10.0,
      'Curved Notch Teardrop' => 0.0,
      'Floating Dynamic Island' => 14.0,
      'Raised Center Action' => 0.0,
      'Segmented Glass Dock' => 10.0,
      'Minimal Icon Dock' => 12.0,
      'Classic Label Bar' => 0.0,
      'Soft Square Tile' => 0.0,
      _ => 8.0,
    };

    final double barHeight = switch (styleName) {
      'Floating Pill' => 64.0,
      'Active Pill Chip' => 62.0,
      'Top Indicator Line' => 60.0,
      'Bottom Indicator Dot' => 60.0,
      'Circle Accent Pop' => 64.0,
      'Curved Notch Teardrop' => 62.0,
      'Floating Dynamic Island' => 60.0,
      'Raised Center Action' => 64.0,
      'Segmented Glass Dock' => 62.0,
      'Minimal Icon Dock' => 56.0,
      'Classic Label Bar' => 62.0,
      'Soft Square Tile' => 60.0,
      _ => 60.0,
    };

    final double barRadius = switch (styleName) {
      'Floating Pill' => 32.0,
      'Active Pill Chip' => 24.0,
      'Top Indicator Line' => 0.0,
      'Bottom Indicator Dot' => 0.0,
      'Circle Accent Pop' => 26.0,
      'Curved Notch Teardrop' => 0.0,
      'Floating Dynamic Island' => 30.0,
      'Raised Center Action' => 0.0,
      'Segmented Glass Dock' => 22.0,
      'Minimal Icon Dock' => 28.0,
      'Classic Label Bar' => 0.0,
      'Soft Square Tile' => 0.0,
      _ => 20.0,
    };

    final barBg = switch (styleName) {
      'Floating Dynamic Island' => colors.surfaceElevated,
      'Segmented Glass Dock' => colors.surface.withValues(
        alpha: isDark ? 0.85 : 0.92,
      ),
      _ => colors.surface,
    };

    final border = switch (styleName) {
      'Segmented Glass Dock' => Border.all(color: colors.border, width: 1.2),
      'Floating Pill' || 'Active Pill Chip' || 'Floating Dynamic Island' =>
        Border.all(color: colors.borderSubtle, width: 0.8),
      'Top Indicator Line' ||
      'Bottom Indicator Dot' ||
      'Curved Notch Teardrop' ||
      'Classic Label Bar' ||
      'Soft Square Tile' => Border(
        top: BorderSide(color: colors.borderSubtle, width: 1.0),
      ),
      _ => Border.all(color: colors.borderSubtle, width: 0.8),
    };

    final hasShadow = sideMargin > 0;

    return Scaffold(
      extendBody: false,
      backgroundColor: bg,
      body: content,
      bottomNavigationBar: SafeArea(
        top: false,
        child: Padding(
          padding: EdgeInsets.fromLTRB(sideMargin, 0, sideMargin, bottomMargin),
          child: SizedBox(
            height: barHeight,
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 220),
              curve: Curves.easeOutCubic,
              decoration: BoxDecoration(
                color: barBg,
                borderRadius: BorderRadius.circular(barRadius),
                border: border,
                boxShadow: hasShadow
                    ? [
                        BoxShadow(
                          color: colors.shadow,
                          blurRadius: 18,
                          offset: const Offset(0, 5),
                        ),
                      ]
                    : null,
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: List.generate(navItems.length, (i) {
                  final item = navItems[i];
                  final isSelected = selectedIndex == i;
                  final isCenter =
                      (navItems.length % 2 == 1) && (i == navItems.length ~/ 2);

                  return _buildCustomNavItem(
                    item: item,
                    isSelected: isSelected,
                    isCenter: isCenter,
                    styleName: styleName,
                    theme: theme,
                    accent: accent,
                    isDark: isDark,
                    onTap: () => onDestinationTap(i),
                  );
                }),
              ),
            ),
          ),
        ),
      ),
    );
  }

  void _showMoreMenu(
    BuildContext context, {
    required dynamic theme,
    required List<_NavDestinationItem> overflowDestinations,
    required ValueChanged<int> onSelect,
  }) {
    final colors = context.colors;
    showGeneralDialog<void>(
      context: context,
      barrierDismissible: true,
      barrierLabel: 'More Menu',
      barrierColor: Colors.black54,
      transitionDuration: const Duration(milliseconds: 180),
      pageBuilder: (dialogContext, animation, secondaryAnimation) {
        return Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(
              maxWidth: 380,
              maxHeight: 520,
            ),
            child: Material(
              color: Colors.transparent,
              child: Container(
                margin: const EdgeInsets.symmetric(horizontal: 24, vertical: 24),
                decoration: BoxDecoration(
                  color: colors.surfaceElevated,
                  borderRadius: BorderRadius.circular(24),
                  border: Border.all(color: colors.borderSubtle, width: 0.8),
                  boxShadow: [
                    BoxShadow(
                      color: colors.shadow,
                      blurRadius: 28,
                      offset: const Offset(0, 10),
                    ),
                  ],
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(20, 18, 16, 12),
                      child: Row(
                        children: [
                          Container(
                            padding: const EdgeInsets.all(8),
                            decoration: BoxDecoration(
                              color: colors.primary.withValues(alpha: 0.12),
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: Icon(
                              Icons.grid_view_rounded,
                              color: colors.primary,
                              size: 20,
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  'More Destinations',
                                  style: TextStyle(
                                    color: colors.foreground,
                                    fontSize: 16,
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                                Text(
                                  'Quick access to extended views',
                                  style: TextStyle(
                                    color: colors.foregroundSecondary,
                                    fontSize: 12,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          IconButton(
                            icon: const Icon(Icons.close_rounded),
                            iconSize: 20,
                            color: colors.foregroundSecondary,
                            onPressed: () => Navigator.of(dialogContext).pop(),
                          ),
                        ],
                      ),
                    ),
                    Divider(color: colors.divider, height: 1),
                    Flexible(
                      child: ListView.separated(
                        shrinkWrap: true,
                        padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 8),
                        itemCount: overflowDestinations.length,
                        separatorBuilder: (_, index) =>
                            Divider(color: colors.divider.withValues(alpha: 0.5), height: 1, indent: 48),
                        itemBuilder: (context, i) {
                          final item = overflowDestinations[i];
                          return ListTile(
                            dense: true,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                            leading: Container(
                              width: 36,
                              height: 36,
                              decoration: BoxDecoration(
                                color: colors.surfaceSecondary,
                                borderRadius: BorderRadius.circular(10),
                              ),
                              child: Icon(item.icon, color: colors.primary, size: 20),
                            ),
                            title: Text(
                              item.label,
                              style: TextStyle(
                                color: colors.foreground,
                                fontWeight: FontWeight.w600,
                                fontSize: 14.5,
                              ),
                            ),
                            trailing: Icon(
                              Icons.arrow_forward_ios_rounded,
                              color: colors.foregroundTertiary,
                              size: 14,
                            ),
                            onTap: () {
                              Navigator.of(dialogContext).pop();
                              onSelect(i);
                            },
                          );
                        },
                      ),
                    ),
                    const SizedBox(height: 6),
                  ],
                ),
              ),
            ),
          ),
        );
      },
      transitionBuilder: (context, animation, secondaryAnimation, child) {
        final curved = CurvedAnimation(
          parent: animation,
          curve: Curves.easeOutCubic,
        );
        return FadeTransition(
          opacity: curved,
          child: ScaleTransition(
            scale: Tween<double>(begin: 0.94, end: 1.0).animate(curved),
            child: child,
          ),
        );
      },
    );
  }

  Widget _buildCustomNavItem({
    required _NavDestinationItem item,
    required bool isSelected,
    required bool isCenter,
    required String styleName,
    required dynamic theme,
    required Color accent,
    required bool isDark,
    required VoidCallback onTap,
  }) {
    final unselectedFg = context.colors.foregroundSecondary;

    return Expanded(
      child: Tooltip(
        message: item.label,
        child: InkWell(
          onTap: () {
            ChatyMotion.selection();
            onTap();
          },
          borderRadius: BorderRadius.circular(20),
          child: Center(
            child: switch (styleName) {
              // 1. ACTIVE PILL CHIP (Image 2 dark portfolio & Image 1 row 2)
              'Active Pill Chip' => AnimatedContainer(
                duration: const Duration(milliseconds: 240),
                curve: Curves.easeOutCubic,
                padding: EdgeInsets.symmetric(
                  horizontal: isSelected ? 14 : 8,
                  vertical: 7,
                ),
                decoration: BoxDecoration(
                  color: isSelected
                      ? accent.withValues(alpha: isDark ? 0.22 : 0.14)
                      : Colors.transparent,
                  borderRadius: BorderRadius.circular(30),
                  border: isSelected
                      ? Border.all(
                          color: accent.withValues(alpha: 0.3),
                          width: 1,
                        )
                      : null,
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      isSelected ? item.activeIcon : item.icon,
                      size: 20,
                      color: isSelected ? accent : unselectedFg,
                    ),
                    if (isSelected) ...[
                      const SizedBox(width: 6),
                      Text(
                        item.label,
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                          color: accent,
                        ),
                      ),
                    ],
                  ],
                ),
              ),

              // 2. TOP INDICATOR LINE (Image 1 top right & Image 3, 5)
              'Top Indicator Line' => Column(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 200),
                    curve: Curves.easeOutCubic,
                    width: isSelected ? 26 : 0,
                    height: 3.5,
                    decoration: BoxDecoration(
                      color: isSelected ? accent : Colors.transparent,
                      borderRadius: const BorderRadius.vertical(
                        bottom: Radius.circular(3),
                      ),
                    ),
                  ),
                  Icon(
                    isSelected ? item.activeIcon : item.icon,
                    size: 22,
                    color: isSelected ? accent : unselectedFg,
                  ),
                  Text(
                    item.label,
                    style: TextStyle(
                      fontSize: 10,
                      fontWeight: isSelected
                          ? FontWeight.w700
                          : FontWeight.w500,
                      color: isSelected ? accent : unselectedFg,
                    ),
                  ),
                  const SizedBox(height: 2),
                ],
              ),

              // 3. BOTTOM INDICATOR DOT / DASH (Image 1 top left & Image 5)
              'Bottom Indicator Dot' => Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(
                    isSelected ? item.activeIcon : item.icon,
                    size: 22,
                    color: isSelected ? accent : unselectedFg,
                  ),
                  const SizedBox(height: 4),
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 200),
                    curve: Curves.easeOutCubic,
                    width: isSelected ? 5.5 : 0,
                    height: isSelected ? 5.5 : 0,
                    decoration: BoxDecoration(
                      color: isSelected ? accent : Colors.transparent,
                      shape: BoxShape.circle,
                    ),
                  ),
                ],
              ),

              // 4. CIRCLE ACCENT POP (Image 1 bottom row, Image 4)
              'Circle Accent Pop' => AnimatedScale(
                scale: isSelected ? 1.0 : 0.95,
                duration: const Duration(milliseconds: 180),
                child: Container(
                  width: isSelected ? 42 : 36,
                  height: isSelected ? 42 : 36,
                  decoration: BoxDecoration(
                    color: isSelected ? accent : Colors.transparent,
                    shape: BoxShape.circle,
                    boxShadow: isSelected
                        ? [
                            BoxShadow(
                              color: accent.withValues(alpha: 0.35),
                              blurRadius: 10,
                              offset: const Offset(0, 3),
                            ),
                          ]
                        : null,
                  ),
                  child: Icon(
                    isSelected ? item.activeIcon : item.icon,
                    size: 20,
                    color: isSelected ? context.colors.onPrimary : unselectedFg,
                  ),
                ),
              ),

              // 5. CURVED NOTCH TEARDROP (Image 3 middle left, Image 5)
              'Curved Notch Teardrop' => Column(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 220),
                    curve: Curves.easeOutCubic,
                    width: isSelected ? 28 : 0,
                    height: 5,
                    decoration: BoxDecoration(
                      color: isSelected ? accent : Colors.transparent,
                      borderRadius: const BorderRadius.vertical(
                        bottom: Radius.circular(8),
                      ),
                    ),
                  ),
                  Icon(
                    isSelected ? item.activeIcon : item.icon,
                    size: 21,
                    color: isSelected ? accent : unselectedFg,
                  ),
                  Text(
                    item.label,
                    style: TextStyle(
                      fontSize: 10,
                      fontWeight: isSelected
                          ? FontWeight.w700
                          : FontWeight.w500,
                      color: isSelected ? accent : unselectedFg,
                    ),
                  ),
                  const SizedBox(height: 3),
                ],
              ),

              // 6. FLOATING DYNAMIC ISLAND (Image 2 dark capsule)
              'Floating Dynamic Island' => AnimatedContainer(
                duration: const Duration(milliseconds: 240),
                curve: Curves.easeOutCubic,
                padding: EdgeInsets.symmetric(
                  horizontal: isSelected ? 12 : 8,
                  vertical: 6,
                ),
                decoration: BoxDecoration(
                  color: isSelected
                      ? context.colors.primary
                      : Colors.transparent,
                  borderRadius: BorderRadius.circular(24),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      isSelected ? item.activeIcon : item.icon,
                      size: 19,
                      color: isSelected
                          ? context.colors.onPrimary
                          : unselectedFg,
                    ),
                    if (isSelected) ...[
                      const SizedBox(width: 5),
                      Text(
                        item.label,
                        style: TextStyle(
                          fontSize: 11.5,
                          fontWeight: FontWeight.w800,
                          color: context.colors.onPrimary,
                        ),
                      ),
                    ],
                  ],
                ),
              ),

              // 7. RAISED CENTER ACTION (Image 4 center button)
              'Raised Center Action' =>
                isCenter
                    ? Container(
                        width: 46,
                        height: 46,
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            colors: [accent, accent.withValues(alpha: 0.85)],
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                          ),
                          shape: BoxShape.circle,
                          boxShadow: [
                            BoxShadow(
                              color: accent.withValues(alpha: 0.4),
                              blurRadius: 12,
                              offset: const Offset(0, 4),
                            ),
                          ],
                        ),
                        child: Icon(
                          item.activeIcon,
                          size: 24,
                          color: context.colors.onPrimary,
                        ),
                      )
                    : Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(
                            isSelected ? item.activeIcon : item.icon,
                            size: 20,
                            color: isSelected ? accent : unselectedFg,
                          ),
                          const SizedBox(height: 2),
                          Text(
                            item.label,
                            style: TextStyle(
                              fontSize: 10,
                              fontWeight: isSelected
                                  ? FontWeight.w700
                                  : FontWeight.w500,
                              color: isSelected ? accent : unselectedFg,
                            ),
                          ),
                        ],
                      ),

              // 8. SEGMENTED GLASS DOCK (Frosted look with subtle inner border)
              'Segmented Glass Dock' => AnimatedContainer(
                duration: const Duration(milliseconds: 200),
                curve: Curves.easeOutCubic,
                padding: const EdgeInsets.symmetric(
                  horizontal: 10,
                  vertical: 6,
                ),
                decoration: BoxDecoration(
                  color: isSelected
                      ? accent.withValues(alpha: isDark ? 0.25 : 0.15)
                      : Colors.transparent,
                  borderRadius: BorderRadius.circular(16),
                  border: isSelected
                      ? Border.all(
                          color: accent.withValues(alpha: 0.4),
                          width: 1,
                        )
                      : null,
                ),
                child: Icon(
                  isSelected ? item.activeIcon : item.icon,
                  size: 21,
                  color: isSelected ? accent : unselectedFg,
                ),
              ),

              // 9. MINIMAL ICON DOCK (Image 1 top left)
              'Minimal Icon Dock' => AnimatedScale(
                scale: isSelected ? 1.15 : 1.0,
                duration: const Duration(milliseconds: 180),
                child: Icon(
                  isSelected ? item.activeIcon : item.icon,
                  size: 22,
                  color: isSelected ? accent : unselectedFg,
                ),
              ),

              // 10. CLASSIC LABEL BAR (Standard native tabs)
              'Classic Label Bar' => Column(
                mainAxisAlignment: MainAxisAlignment.center,

                children: [
                  Icon(
                    isSelected ? item.activeIcon : item.icon,
                    size: 21,
                    color: isSelected ? accent : unselectedFg,
                  ),
                  const SizedBox(height: 2),
                  Text(
                    item.label,
                    style: TextStyle(
                      fontSize: 10.5,
                      fontWeight: isSelected
                          ? FontWeight.w700
                          : FontWeight.w500,
                      color: isSelected ? accent : unselectedFg,
                    ),
                  ),
                ],
              ),

              // 11. SOFT SQUARE TILE (Image 3 middle row)
              'Soft Square Tile' => AnimatedContainer(
                duration: const Duration(milliseconds: 200),
                padding: const EdgeInsets.all(7),
                decoration: BoxDecoration(
                  color: isSelected
                      ? accent.withValues(alpha: isDark ? 0.2 : 0.12)
                      : Colors.transparent,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      isSelected ? item.activeIcon : item.icon,
                      size: 19,
                      color: isSelected ? accent : unselectedFg,
                    ),
                    Text(
                      item.label,
                      style: TextStyle(
                        fontSize: 9.5,
                        fontWeight: isSelected
                            ? FontWeight.w700
                            : FontWeight.w500,
                        color: isSelected ? accent : unselectedFg,
                      ),
                    ),
                  ],
                ),
              ),

              // 12. FLOATING PILL (Default smooth floating capsule)
              _ => AnimatedContainer(
                duration: const Duration(milliseconds: 220),
                padding: const EdgeInsets.symmetric(
                  horizontal: 10,
                  vertical: 6,
                ),
                decoration: BoxDecoration(
                  color: isSelected
                      ? accent.withValues(alpha: isDark ? 0.2 : 0.12)
                      : Colors.transparent,
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      isSelected ? item.activeIcon : item.icon,
                      size: 20,
                      color: isSelected ? accent : unselectedFg,
                    ),
                    if (isSelected) ...[
                      const SizedBox(width: 5),
                      Text(
                        item.label,
                        style: TextStyle(
                          fontSize: 11.5,
                          fontWeight: FontWeight.w700,
                          color: accent,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            },
          ),
        ),
      ),
    );
  }
}

class _NavDestinationItem {
  final String id;
  final String label;
  final IconData icon;
  final IconData activeIcon;
  final Widget Function(BuildContext) builder;

  const _NavDestinationItem({
    this.id = '',
    required this.label,
    required this.icon,
    required this.activeIcon,
    this.builder = _dummyBuilder,
  });

  static Widget _dummyBuilder(BuildContext context) => const SizedBox.shrink();
}

class _PerspectiveDrawerScaffold extends StatefulWidget {
  final dynamic theme;
  final int selectedIndex;
  final List<_NavDestinationItem> navItems;
  final ValueChanged<int> onSelect;
  final Widget child;

  const _PerspectiveDrawerScaffold({
    required this.theme,
    required this.selectedIndex,
    required this.navItems,
    required this.onSelect,
    required this.child,
  });

  @override
  State<_PerspectiveDrawerScaffold> createState() =>
      _PerspectiveDrawerScaffoldState();
}

class _PerspectiveDrawerScaffoldState extends State<_PerspectiveDrawerScaffold>
    with SingleTickerProviderStateMixin {
  late final AnimationController _ctrl;
  late final Animation<double> _anim;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 280),
    );
    _anim = CurvedAnimation(parent: _ctrl, curve: Curves.easeOutCubic);
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  void _toggle() {
    if (_ctrl.isCompleted) {
      _ctrl.reverse();
    } else {
      _ctrl.forward();
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;

    return Scaffold(
      backgroundColor: colors.surfaceElevated,
      body: AnimatedBuilder(
        animation: _anim,
        builder: (context, _) {
          final val = _anim.value;
          final slide = 220.0 * val;
          final scale = 1.0 - (0.18 * val);
          final angle = -0.12 * val;

          return Stack(
            children: [
              // Left Menu Content
              SafeArea(
                child: Container(
                  width: 210,
                  padding: const EdgeInsets.symmetric(
                    horizontal: 18,
                    vertical: 20,
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Side Menu',
                        style: TextStyle(
                          color: colors.foreground,
                          fontSize: 22,
                          fontWeight: FontWeight.w800,
                          letterSpacing: -0.5,
                        ),
                      ),
                      const SizedBox(height: 24),
                      Expanded(
                        child: ListView.builder(
                          itemCount: widget.navItems.length,
                          itemBuilder: (context, i) {
                            final item = widget.navItems[i];
                            final isSel = widget.selectedIndex == i;
                            return Padding(
                              padding: const EdgeInsets.symmetric(vertical: 4),
                              child: ListTile(
                                dense: true,
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(12),
                                ),
                                selected: isSel,
                                selectedTileColor: colors.primary.withValues(
                                  alpha: 0.15,
                                ),
                                leading: Icon(
                                  isSel ? item.activeIcon : item.icon,
                                  color: isSel
                                      ? colors.primary
                                      : colors.foregroundSecondary,
                                  size: 20,
                                ),
                                title: Text(
                                  item.label,
                                  style: TextStyle(
                                    color: isSel
                                        ? colors.primary
                                        : colors.foregroundSecondary,
                                    fontWeight: isSel
                                        ? FontWeight.w700
                                        : FontWeight.w500,
                                    fontSize: 14,
                                  ),
                                ),
                                onTap: () {
                                  widget.onSelect(i);
                                  _ctrl.reverse();
                                },
                              ),
                            );
                          },
                        ),
                      ),
                    ],
                  ),
                ),
              ),

              // Perspective Transformed Foreground Screen
              Transform(
                transform: Matrix4.identity()
                  ..translate(slide)
                  ..scale(scale)
                  ..setEntry(3, 2, 0.001)
                  ..rotateY(angle),
                alignment: Alignment.centerLeft,
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(val * 28),
                  child: Column(
                    children: [
                      TemplateShellHeader(
                        title: widget.navItems[widget.selectedIndex].label,
                        navigation: IconButton(
                          tooltip: val > 0.5 ? 'Close menu' : 'Open menu',
                          onPressed: _toggle,
                          icon: Icon(
                            val > 0.5
                                ? Icons.close_rounded
                                : Icons.menu_rounded,
                          ),
                        ),
                        backgroundColor: colors.surface,
                        foregroundColor: colors.foreground,
                        dividerColor: colors.divider,
                      ),
                      Expanded(child: widget.child),
                    ],
                  ),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}
