import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../ui/core/theme/theme_controller.dart';
import '../../data/repositories/mock_data_store.dart';
import '../../data/services/chaty_notification_service.dart';
import '../../domain/models/conversation.dart';
import '../../ui/core/controllers/appearance_variant_controller.dart';
import '../../ui/core/controllers/chaty_preferences_controller.dart';
import '../../ui/core/gb/gb_theme_overrides.dart';
import '../calls/calls_screen.dart';
import '../settings/settings_root_screen.dart';
import '../tasks/tasks_screen.dart';
import '../updates/updates_screen.dart';
import 'chats_home_screen.dart';
import 'package:chat/injection/locator.dart';

class MainNavigationShell extends StatefulWidget {
  const MainNavigationShell({super.key});

  @override
  State<MainNavigationShell> createState() => _MainNavigationShellState();
}

class _MainNavigationShellState extends State<MainNavigationShell> {
  int _currentIndex = 0;
  DateTime? _lastExitAttempt;

  void _selectRootDestination(int next) {
    if (next == _currentIndex) return;
    HapticFeedback.selectionClick();
    setState(() => _currentIndex = next);
  }

  Future<void> _handleRootBack() async {
    if (_currentIndex != 0) {
      _selectRootDestination(0);
      return;
    }
    final now = DateTime.now();
    final previous = _lastExitAttempt;
    if (previous == null || now.difference(previous) > const Duration(seconds: 2)) {
      _lastExitAttempt = now;
      if (mounted) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(const SnackBar(content: Text('Press back again to exit Chaty'), duration: Duration(seconds: 2)));
      }
      return;
    }
    await SystemNavigator.pop();
  }

  @override
  Widget build(BuildContext context) {
    final themeController = locator<ThemeController>();
    final dataStore = locator<MockDataStore>();
    final preferencesController = locator<ChatyPreferencesController>();
    final appearanceController = locator<AppearanceVariantController>();
    final notificationService = locator<ChatyNotificationService>();

    return ListenableBuilder(
      listenable: Listenable.merge(<Listenable>[themeController, preferencesController, appearanceController, dataStore]),
      builder: (context, _) {
        final theme = GbThemeOverrides.resolve(themeController.globalTheme, preferencesController);
        final separateGroups = preferencesController.home.separateChatsAndGroups ||
            preferencesController.gbBool('enable_grp_separationV2');

        final navItems = <_NavDestinationItem>[
          const _NavDestinationItem(label: 'Chats', icon: Icons.chat_bubble_outline_rounded, activeIcon: Icons.chat_bubble_rounded),
          if (separateGroups)
            const _NavDestinationItem(label: 'Groups', icon: Icons.groups_outlined, activeIcon: Icons.groups_rounded),
          const _NavDestinationItem(label: 'Updates', icon: Icons.update_outlined, activeIcon: Icons.update_rounded),
          const _NavDestinationItem(label: 'Tasks', icon: Icons.checklist_rtl_rounded, activeIcon: Icons.checklist_rounded),
          const _NavDestinationItem(label: 'Calls', icon: Icons.call_outlined, activeIcon: Icons.call_rounded),
          const _NavDestinationItem(label: 'Settings', icon: Icons.settings_outlined, activeIcon: Icons.settings_rounded),
        ];

        final screens = <Widget>[
          ChatsHomeScreen(
            theme: theme,
            dataStore: dataStore,
            preferencesController: preferencesController,
            themeController: themeController,
            notificationService: notificationService,
            forcedType: separateGroups ? ConversationType.direct : null,
            pageTitle: separateGroups ? 'Chats' : null,
          ),
          if (separateGroups)
            ChatsHomeScreen(
              theme: theme,
              dataStore: dataStore,
              preferencesController: preferencesController,
              themeController: themeController,
              notificationService: notificationService,
              forcedType: ConversationType.group,
              pageTitle: 'Groups',
            ),
          UpdatesScreen(theme: theme, dataStore: dataStore, preferencesController: preferencesController),
          TasksScreen(theme: theme, dataStore: dataStore),
          CallsScreen(theme: theme, dataStore: dataStore),
          SettingsRootScreen(
            preferencesController: preferencesController,
            themeController: themeController,
            dataStore: dataStore,
            notificationService: notificationService,
          ),
        ];

        final effectiveIndex = _currentIndex.clamp(0, screens.length - 1);
        if (effectiveIndex != _currentIndex) {
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (mounted) setState(() => _currentIndex = 0);
          });
        }

        return PopScope(
          canPop: false,
          onPopInvokedWithResult: (didPop, result) {
            if (!didPop) _handleRootBack();
          },
          child: LayoutBuilder(
            builder: (context, constraints) {
              final navIndex = appearanceController.navigationIndex;
              final forceRail = <int>{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18}.contains(navIndex);
              final useRail = constraints.maxWidth >= (forceRail ? 720 : 900);
              final content = IndexedStack(index: effectiveIndex, children: screens);
              if (useRail) {
                return _buildRailShell(
                  theme: theme,
                  content: content,
                  appearance: appearanceController,
                  maxWidth: constraints.maxWidth,
                  navItems: navItems,
                  selectedIndex: effectiveIndex,
                );
              }
              return _buildBottomShell(
                theme: theme,
                content: content,
                appearance: appearanceController,
                navItems: navItems,
                selectedIndex: effectiveIndex,
              );
            },
          ),
        );
      },
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
    final showAllLabels = <int>{0, 3, 4, 8, 9, 17, 18}.contains(index) && maxWidth >= 900;
    final indicatorRadius = <double>[18, 12, 8, 24, 10, 20, 8, 30, 14, 20, 16, 24, 12, 22, 14, 8, 24, 10, 16, 8][index];

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
              indicatorColor: theme.accentColor.withValues(alpha: <int>{2, 7, 9, 19}.contains(index) ? 0.08 : 0.16),
              indicatorShape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(indicatorRadius)),
              selectedIconTheme: IconThemeData(color: theme.accentColor, size: compact ? 20 : 23),
              unselectedIconTheme: IconThemeData(color: theme.secondaryTextColor, size: compact ? 19 : 21),
              selectedLabelTextStyle: TextStyle(color: theme.primaryTextColor, fontWeight: FontWeight.w700),
              unselectedLabelTextStyle: TextStyle(color: theme.secondaryTextColor),
              labelType: showAllLabels ? NavigationRailLabelType.none : NavigationRailLabelType.selected,
              destinations: navItems
                  .map((item) => NavigationRailDestination(icon: Icon(item.icon), selectedIcon: Icon(item.activeIcon), label: Text(item.label)))
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
  }) {
    final index = appearance.bottomBarIndex;
    final radius = <double>[32, 14, 25, 22, 28, 30, 18, 12, 8, 22, 16, 20, 4, 10, 34, 27, 18, 0, 14, 10][index];
    final sideMargin = <double>[12, 0, 28, 18, 12, 18, 36, 8, 0, 18, 8, 16, 0, 4, 8, 24, 14, 0, 10, 6][index];
    final bottomMargin = <double>[10, 0, 10, 12, 10, 12, 12, 6, 0, 10, 7, 12, 0, 4, 10, 12, 10, 0, 8, 5][index];
    final requestedHeight = <double>[60, 64, 54, 58, 60, 60, 54, 60, 50, 64, 58, 60, 58, 52, 64, 54, 60, 58, 58, 52][index];
    final height = requestedHeight < 56 ? 56.0 : requestedHeight;
    final compact = <int>{2, 6, 8, 13, 15, 19}.contains(index);
    final showLabels = navItems.length <= 5 && !<int>{6, 8, 13, 19}.contains(index);
    final selectedFilled = !<int>{4, 8, 12, 17}.contains(index);

    return Scaffold(
      extendBody: false,
      backgroundColor: theme.backgroundColor,
      body: content,
      bottomNavigationBar: SafeArea(
        top: false,
        child: Padding(
          padding: EdgeInsets.fromLTRB(sideMargin, 0, sideMargin, bottomMargin),
          child: SizedBox(
            height: height,
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 220),
              curve: Curves.easeOutCubic,
              padding: EdgeInsets.symmetric(horizontal: compact ? 2 : 6, vertical: compact ? 3 : 6),
              decoration: BoxDecoration(
                color: theme.surfaceColor,
                borderRadius: BorderRadius.circular(radius),
                border: Border.all(
                  color: <int>{4, 16}.contains(index) ? theme.accentColor.withValues(alpha: 0.35) : theme.cardColor,
                  width: <int>{4, 16}.contains(index) ? 1.3 : 1,
                ),
                boxShadow: sideMargin > 0
                    ? <BoxShadow>[
                        BoxShadow(
                          color: Colors.black.withValues(alpha: theme.brightness == Brightness.dark ? 0.26 : 0.09),
                          blurRadius: <int>{3, 5, 9, 16}.contains(index) ? 24 : 14,
                          offset: const Offset(0, 6),
                        ),
                      ]
                    : const <BoxShadow>[],
              ),
              child: Row(
                mainAxisSize: MainAxisSize.max,
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: List.generate(navItems.length, (navItemIndex) {
                  final item = navItems[navItemIndex];
                  return _buildBottomItem(
                    item: item,
                    isSelected: selectedIndex == navItemIndex,
                    theme: theme,
                    compact: compact,
                    showLabel: showLabels,
                    selectedFilled: selectedFilled,
                    styleIndex: index,
                    onTap: () => _selectRootDestination(navItemIndex),
                  );
                }),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildBottomItem({
    required _NavDestinationItem item,
    required bool isSelected,
    required dynamic theme,
    required bool compact,
    required bool showLabel,
    required bool selectedFilled,
    required int styleIndex,
    required VoidCallback onTap,
  }) {
    final iconColor = isSelected ? theme.accentColor : theme.secondaryTextColor;
    final fill = isSelected && selectedFilled ? theme.accentColor.withValues(alpha: 0.13) : Colors.transparent;
    final radius = <int>{1, 7, 12, 17}.contains(styleIndex) ? 10.0 : 24.0;
    return Flexible(
      child: Tooltip(
        message: item.label,
        child: Semantics(
          button: true,
          selected: isSelected,
          label: item.label,
          child: InkWell(
            onTap: onTap,
            borderRadius: BorderRadius.circular(radius),
            child: ConstrainedBox(
              constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 180),
                curve: Curves.easeOutCubic,
                padding: EdgeInsets.symmetric(horizontal: compact ? 6 : 8, vertical: compact ? 5 : 6),
                decoration: BoxDecoration(color: fill, borderRadius: BorderRadius.circular(radius)),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(isSelected ? item.activeIcon : item.icon, color: iconColor, size: compact ? 19 : 21),
                    if (isSelected && showLabel && MediaQuery.sizeOf(context).width >= 380 && navItemsCanShowLabel(styleIndex)) ...[
                      const SizedBox(width: 5),
                      Flexible(
                        child: Text(
                          item.label,
                          maxLines: 1,
                          overflow: TextOverflow.fade,
                          softWrap: false,
                          style: TextStyle(
                            color: theme.primaryTextColor,
                            fontSize: compact ? 10.5 : 11.5,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  bool navItemsCanShowLabel(int styleIndex) => !<int>{6, 8, 13, 19}.contains(styleIndex);
}

class _NavDestinationItem {
  final String label;
  final IconData icon;
  final IconData activeIcon;
  const _NavDestinationItem({required this.label, required this.icon, required this.activeIcon});
}
