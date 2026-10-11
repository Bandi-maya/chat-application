import 'dart:math' as math;

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../data/repositories/chaty_data_store.dart';
import '../../data/services/notification_service.dart';
import '../../domain/models/conversation.dart';
import '../../ui/core/controllers/appearance_variant_controller.dart';
import '../../ui/core/controllers/home_preset_normalizer.dart';
import '../../ui/core/controllers/preferences_controller.dart';
import '../../ui/core/gb/gb_theme_overrides.dart';
import '../calls/calls_screen.dart';
import '../tasks/tasks_screen.dart';
import '../updates/updates_screen.dart';
import 'chats_home_screen.dart';
import 'new_chat_screen.dart';
import 'linked_devices_qr_screen.dart';
import '../../injection/locator.dart';
import '../../ui/core/design_system/design_system.dart';
import '../../ui/core/widgets/app_avatar.dart';
import '../../ui/core/templates/template_shell.dart';
import '../../ui/core/templates/template_controller.dart';
import '../../ui/core/templates/template_models.dart';
import '../camera/effects/widgets/effect_picker_sheet.dart';
import '../camera/camera_capture_screen.dart';
import '../../data/services/status_service.dart';
import '../../data/services/call_signaling_service.dart';
import '../calls/ongoing_call_screen.dart';
import '../settings/settings_root_screen.dart';
import '../tasks/task_create_edit_modal.dart';

class MainNavigationShell extends StatefulWidget {
  const MainNavigationShell({super.key});

  @override
  State<MainNavigationShell> createState() => _MainNavigationShellState();
}

class _MainNavigationShellState extends State<MainNavigationShell> {
  int _currentIndex = 0;
  String _currentDestinationId = 'chats';
  DateTime? _lastExitAttempt;
  Offset? _rootSwipeStart;
  int? _rootSwipePointer;
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

  /// Tracks raw touch movement above every root destination, independently of
  /// child gesture recognizers, so all shell styles share the same direction.
  Widget _buildSwipeableRootStack({
    required List<Widget> screens,
    required List<_NavDestinationItem> navItems,
    required int selectedIndex,
  }) {
    return Listener(
      behavior: HitTestBehavior.translucent,
      onPointerDown: _recordRootPointerDown,
      onPointerUp: (event) => _handleRootPointerUp(event, navItems),
      onPointerCancel: _recordRootPointerCancel,
      child: IndexedStack(index: selectedIndex, children: screens),
    );
  }

  void _recordRootPointerDown(PointerDownEvent event) {
    if (event.kind != PointerDeviceKind.touch || _rootSwipePointer != null) {
      return;
    }
    _rootSwipePointer = event.pointer;
    _rootSwipeStart = event.position;
  }

  void _recordRootPointerCancel(PointerCancelEvent event) {
    if (event.pointer == _rootSwipePointer) {
      _rootSwipePointer = null;
      _rootSwipeStart = null;
    }
  }

  void _handleRootPointerUp(
    PointerUpEvent event,
    List<_NavDestinationItem> navItems,
  ) {
    if (event.pointer != _rootSwipePointer) return;
    final start = _rootSwipeStart;
    _rootSwipePointer = null;
    _rootSwipeStart = null;
    if (start == null) return;

    final delta = event.position - start;
    // Ignore taps, vertical scrolling, and diagonal drags.
    if (delta.dx.abs() < 78 || delta.dx.abs() < delta.dy.abs() * 1.3) {
      return;
    }

    // Requested direction: left -> right advances; right -> left goes back.
    final nextIndex = _currentIndex + (delta.dx > 0 ? 1 : -1);
    if (nextIndex < 0 || nextIndex >= navItems.length) return;
    _selectRootDestination(
      nextIndex,
      destinationId: navItems[nextIndex].id,
    );
  }

  void _selectRootDestination(int next, {String? destinationId}) {
    if (next == _currentIndex &&
        (destinationId == null || destinationId == _currentDestinationId)) {
      return;
    }
    HapticFeedback.selectionClick();
    setState(() {
      _currentIndex = next;
      if (destinationId != null) _currentDestinationId = destinationId;
    });
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
      _selectRootDestination(0, destinationId: 'chats');
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
    final templateController = locator<TemplateController>();

    return ListenableBuilder(
      listenable: Listenable.merge(<Listenable>[
        themeController,
        preferencesController,
        appearanceController,
        templateController,
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
        final destinationCatalog = <_NavDestinationItem>[
          _NavDestinationItem(
            id: 'chats',
            label: 'Chats',
            icon: ChatyGlyph.chatBubble,
            activeIcon: ChatyGlyph.chatBubble,
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
          if (separateGroups || templateController.hasCustomNavigationDestinations)
            _NavDestinationItem(
              id: 'groups',
              label: 'Groups',
              icon: ChatyGlyph.groups,
              activeIcon: ChatyGlyph.groups,
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
            icon: ChatyGlyph.updates,
            activeIcon: ChatyGlyph.updates,
            builder: (ctx) => UpdatesScreen(
              theme: theme,
              dataStore: dataStore,
              preferencesController: preferencesController,
            ),
          ),
          _NavDestinationItem(
            id: 'tasks',
            label: 'Tasks',
            icon: ChatyGlyph.tasks,
            activeIcon: ChatyGlyph.tasks,
            builder: (ctx) => TasksScreen(theme: theme, dataStore: dataStore),
          ),
          _NavDestinationItem(
            id: 'calls',
            label: 'Calls',
            icon: ChatyGlyph.calls,
            activeIcon: ChatyGlyph.calls,
            builder: (ctx) => CallsScreen(theme: theme, dataStore: dataStore),
          ),
          _NavDestinationItem(
            id: 'settings',
            label: 'Settings',
            icon: ChatyGlyph.settings,
            activeIcon: ChatyGlyph.settings,
            builder: (ctx) => SettingsRootScreen(
              preferencesController: preferencesController,
              themeController: themeController,
              dataStore: dataStore,
              notificationService: notificationService,
            ),
          ),
          if (!showDesktopIcon || templateController.hasCustomNavigationDestinations)
            _NavDestinationItem(
              id: 'desktop',
              label: 'Desktop',
              icon: ChatyGlyph.devices,
              activeIcon: ChatyGlyph.devices,
              builder: (ctx) => LinkedDevicesQrScreen(
                dataStore: dataStore,
                relationshipService: locator(),
                preferencesController: preferencesController,
                themeController: themeController,
                devicesOnly: true,
              ),
            ),
        ];

        // Resolve the selected template's destination order without changing
        // destination identity. Keeping a stable ID means applying a layout
        // while Settings is open never sends the user to an unrelated screen.
        final navigationTemplate = templateController.navigation;
        final destinationById = <String, _NavDestinationItem>{
          for (final item in destinationCatalog) item.id: item,
        };
        final orderedDestinations = <_NavDestinationItem>[];
        final preferredDestinationIds = <String>[
          ...navigationTemplate.primaryDestinationIds,
          ...navigationTemplate.overflowDestinationIds,
        ];
        for (final id in preferredDestinationIds) {
          final item = destinationById[id];
          if (item != null &&
              !orderedDestinations.any((existing) => existing.id == item.id)) {
            orderedDestinations.add(item);
          }
        }
        for (final item in destinationCatalog) {
          if (!orderedDestinations.any((existing) => existing.id == item.id)) {
            orderedDestinations.add(item);
          }
        }
        final allDestinations = orderedDestinations;

        final primaryDestinations = <_NavDestinationItem>[];
        for (final id in navigationTemplate.primaryDestinationIds) {
          final item = destinationById[id];
          if (item != null &&
              !primaryDestinations.any((existing) => existing.id == item.id)) {
            primaryDestinations.add(item);
          }
        }
        // A malformed or future template cannot leave navigation unusable.
        if (primaryDestinations.isEmpty) {
          primaryDestinations.addAll(allDestinations.take(3));
        }
        final primaryIds = primaryDestinations.map((item) => item.id).toSet();
        final overflowDestinations = <_NavDestinationItem>[];
        for (final id in navigationTemplate.overflowDestinationIds) {
          final item = destinationById[id];
          if (item != null &&
              !primaryIds.contains(item.id) &&
              !overflowDestinations.any((existing) => existing.id == item.id)) {
            overflowDestinations.add(item);
          }
        }
        for (final item in allDestinations) {
          if (!primaryIds.contains(item.id) &&
              !overflowDestinations.any((existing) => existing.id == item.id)) {
            overflowDestinations.add(item);
          }
        }
        final hasOverflow = overflowDestinations.isNotEmpty;
        final List<_NavDestinationItem> navItems = [
          ...primaryDestinations,
          if (hasOverflow)
            const _NavDestinationItem(
              id: 'more',
              label: 'More',
              icon: ChatyGlyph.more,
              activeIcon: ChatyGlyph.more,
            ),
        ];

        final List<Widget> screens = allDestinations
            .map((item) => item.builder(context))
            .toList(growable: false);

        final currentIndexFromId = allDestinations.indexWhere(
          (item) => item.id == _currentDestinationId,
        );
        final effectiveIndex = currentIndexFromId < 0
            ? 0
            : currentIndexFromId;
        if (effectiveIndex != _currentIndex || currentIndexFromId < 0) {
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (!mounted) return;
            setState(() {
              _currentIndex = effectiveIndex;
              _currentDestinationId = allDestinations[effectiveIndex].id;
            });
            if (_pageController.hasClients &&
                _pageController.page?.round() != effectiveIndex) {
              _pageController.jumpToPage(effectiveIndex);
            }
          });
        }

        final currentDestinationId = allDestinations[effectiveIndex].id;
        final selectedPrimaryIndex = primaryDestinations.indexWhere(
          (item) => item.id == currentDestinationId,
        );
        final int bottomNavSelectedIndex = selectedPrimaryIndex >= 0
            ? selectedPrimaryIndex
            : primaryDestinations.length;

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
              // The Home UI selection is persisted with user preferences.
              // Resolve it before the template-owned fallback so the chosen
              // shell survives startup template synchronization and app restart.
              final navMode =
                  HomePresetNormalizer.navigationMode(
                    preferencesController.home.homeStyle,
                  ) ??
                  themeController.navigationMode;

              // 1. TOP WHATSAPP-STYLE GREEN TAB BAR (Image 1)
              if (navMode == AppNavigationMode.topWhatsAppBar) {
                return _buildTopWhatsAppShell(
                  theme: theme,
                  navigationTemplate: navigationTemplate,
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
                        reverse: true,
                    onPageChanged: (idx) {
                      if (idx >= 0 && idx < allDestinations.length &&
                          (_currentIndex != idx ||
                              _currentDestinationId != allDestinations[idx].id)) {
                        setState(() {
                          _currentIndex = idx;
                          _currentDestinationId = allDestinations[idx].id;
                        });
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
                  floatingActionButton: _buildContextualFab(
                    context: context,
                    theme: theme,
                    colors: context.colors,
                    accent: theme.accentColor,
                    activeItem: allDestinations[effectiveIndex],
                  ),
                  onSelect: (idx) {
                    if (idx >= 0 && idx < allDestinations.length) {
                      _selectRootDestination(
                        idx,
                        destinationId: allDestinations[idx].id,
                      );
                    }
                  },
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

              // 5. Curved radial menu inspired by reference image 1.
              if (navMode == AppNavigationMode.curvedRadialDrawer) {
                return _buildCurvedRadialDrawerShell(
                  theme: theme,
                  screens: screens,
                  navItems: allDestinations,
                  selectedIndex: effectiveIndex,
                  floatingActionButton: _buildContextualFab(
                    context: context,
                    theme: theme,
                    colors: context.colors,
                    accent: theme.accentColor,
                    activeItem: allDestinations[effectiveIndex],
                  ),
                  onSelect: (idx) {
                    if (idx >= 0 && idx < allDestinations.length) {
                      _selectRootDestination(
                        idx,
                        destinationId: allDestinations[idx].id,
                      );
                    }
                  },
                );
              }

              // Dedicated visual systems for the named Home UI choices.
              // All keep the same destinations and screen state; only navigation
              // presentation changes.
              if ({
                AppNavigationMode.oneUi,
                AppNavigationMode.iosStyle,
                AppNavigationMode.instagramStyle,
                AppNavigationMode.telegramStyle,
                AppNavigationMode.basicTabStyle,
                AppNavigationMode.gestureTabs,
              }.contains(navMode)) {
                final Widget presetContent = navMode == AppNavigationMode.gestureTabs
                    ? PageView(
                        controller: _pageController,
                        reverse: true,
                        physics: const BouncingScrollPhysics(),
                        onPageChanged: (idx) {
                          if (idx >= 0 &&
                              idx < allDestinations.length &&
                              (_currentIndex != idx ||
                                  _currentDestinationId != allDestinations[idx].id)) {
                            setState(() {
                              _currentIndex = idx;
                              _currentDestinationId = allDestinations[idx].id;
                            });
                          }
                        },
                        children: screens,
                      )
                    : _buildSwipeableRootStack(
                        screens: screens,
                        navItems: allDestinations,
                        selectedIndex: effectiveIndex,
                      );
                return _buildPresetNavigationShell(
                  mode: navMode,
                  theme: theme,
                  content: presetContent,
                  navItems: allDestinations,
                  selectedIndex: effectiveIndex,
                  floatingActionButton: _buildContextualFab(
                    context: context,
                    theme: theme,
                    colors: context.colors,
                    accent: theme.accentColor,
                    activeItem: allDestinations[effectiveIndex],
                  ),
                  onDestinationTap: (idx) {
                    if (idx >= 0 && idx < allDestinations.length) {
                      _selectRootDestination(
                        idx,
                        destinationId: allDestinations[idx].id,
                      );
                    }
                  },
                );
              }

              // 7. STANDARD BOTTOM NAVIGATION / ADAPTIVE RAIL
              final layoutMode = themeController.layoutMode;
              final useRail =
                  constraints.maxWidth >= 600 &&
                  (navMode == AppNavigationMode.compactRail ||
                      (navMode != AppNavigationMode.gestureTabs &&
                          (layoutMode == UILayoutMode.tabletDesktop || autoRail)));
              Widget content = PageView(
                controller: _pageController,
                        reverse: true,
                physics: navMode == AppNavigationMode.gestureTabs
                    ? const BouncingScrollPhysics()
                    : const PageScrollPhysics(),
                onPageChanged: (idx) {
                  if (idx >= 0 && idx < allDestinations.length &&
                      (_currentIndex != idx ||
                          _currentDestinationId != allDestinations[idx].id)) {
                    setState(() {
                      _currentIndex = idx;
                      _currentDestinationId = allDestinations[idx].id;
                    });
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
                navigationTemplate: navigationTemplate,
                onCenterAction: () => _handleNavigationCenterAction(navigationTemplate),
                navItems: navItems,
                activeItem: allDestinations[effectiveIndex],
                selectedIndex: bottomNavSelectedIndex,
                onDestinationTap: (idx) {
                  ChatyHaptics.selection();
                  if (hasOverflow && idx == primaryDestinations.length) {
                    _showMoreMenu(
                      context,
                      theme: theme,
                      overflowDestinations: overflowDestinations,
                      onSelect: (overflowIdx) {
                        if (overflowIdx < 0 ||
                            overflowIdx >= overflowDestinations.length) {
                          return;
                        }
                        final destination = overflowDestinations[overflowIdx];
                        final realIndex = allDestinations.indexWhere(
                          (item) => item.id == destination.id,
                        );
                        if (realIndex >= 0) {
                          _selectRootDestination(
                            realIndex,
                            destinationId: destination.id,
                          );
                        }
                      },
                    );
                  } else if (idx >= 0 && idx < primaryDestinations.length) {
                    final destination = primaryDestinations[idx];
                    final realIndex = allDestinations.indexWhere(
                      (item) => item.id == destination.id,
                    );
                    if (realIndex >= 0) {
                      _selectRootDestination(
                        realIndex,
                        destinationId: destination.id,
                      );
                    }
                  } else if (idx >= 0 && idx < allDestinations.length) {
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
    required NavigationTemplate navigationTemplate,
    required List<Widget> screens,
    required List<_NavDestinationItem> navItems,
    required int selectedIndex,
  }) {
    final colors = context.colors;
    final brandPrimary = theme.accentColor as Color;
    final indicatorColor = colors.onPrimary;

    return DefaultTabController(
      key: ValueKey<int>(selectedIndex),
      length: navItems.length,
      initialIndex: selectedIndex,
      child: Scaffold(
        backgroundColor: theme.backgroundColor,
        floatingActionButton: _buildContextualFab(
          context: context,
          theme: theme,
          colors: colors,
          accent: brandPrimary,
          activeItem: navItems[selectedIndex],
        ),
        floatingActionButtonLocation: FloatingActionButtonLocation.endFloat,
        body: SafeArea(
          bottom: false,
          child: Column(
            children: [
              Container(
                height: navigationTemplate.height.clamp(48.0, 88.0).toDouble(),
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
                  onTap: (idx) => _selectRootDestination(
                    idx,
                    destinationId: navItems[idx].id,
                  ),
                ),
              ),
              Expanded(
                child: _buildSwipeableRootStack(
        screens: screens,
        navItems: navItems,
        selectedIndex: selectedIndex,
      ),
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
      floatingActionButton: _buildContextualFab(
        context: context,
        theme: theme,
        colors: colors,
        accent: theme.accentColor as Color,
        activeItem: navItems[selectedIndex],
      ),
      floatingActionButtonLocation: FloatingActionButtonLocation.endFloat,
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
                            onTap: () => _selectRootDestination(
                            i,
                            destinationId: navItems[i].id,
                          ),
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
                              child: ChatyGlyphIcon(glyph: isSel ? item.activeIcon : item.icon,
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
    required Widget? floatingActionButton,
    required ValueChanged<int> onSelect,
  }) {
    return _PerspectiveDrawerScaffold(
      theme: theme,
      selectedIndex: selectedIndex,
      navItems: navItems,
      floatingActionButton: floatingActionButton,
      onSelect: onSelect,
      child: _buildSwipeableRootStack(
        screens: screens,
        navItems: navItems,
        selectedIndex: selectedIndex,
      ),
    );
  }

  Widget _buildCurvedRadialDrawerShell({
    required dynamic theme,
    required List<Widget> screens,
    required List<_NavDestinationItem> navItems,
    required int selectedIndex,
    required Widget? floatingActionButton,
    required ValueChanged<int> onSelect,
  }) {
    return _CurvedRadialDrawerScaffold(
      theme: theme,
      selectedIndex: selectedIndex,
      navItems: navItems,
      floatingActionButton: floatingActionButton,
      onSelect: onSelect,
      child: _buildSwipeableRootStack(
        screens: screens,
        navItems: navItems,
        selectedIndex: selectedIndex,
      ),
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
      floatingActionButton: _buildContextualFab(
        context: context,
        theme: theme,
        colors: colors,
        accent: theme.accentColor as Color,
        activeItem: navItems[selectedIndex],
      ),
      floatingActionButtonLocation: FloatingActionButtonLocation.endFloat,
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
                      leading: ChatyGlyphIcon(glyph: isSel ? item.activeIcon : item.icon,
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
                        _selectRootDestination(i, destinationId: navItems[i].id);
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
            child: _buildSwipeableRootStack(
        screens: screens,
        navItems: navItems,
        selectedIndex: selectedIndex,
      ),
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
      floatingActionButton: _buildContextualFab(
        context: context,
        theme: theme,
        colors: context.colors,
        accent: theme.accentColor as Color,
        activeItem: navItems[selectedIndex],
      ),
      floatingActionButtonLocation: FloatingActionButtonLocation.endFloat,
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
                      icon: ChatyGlyphIcon(glyph: item.icon, size: compact ? 20 : 23),
                      selectedIcon: ChatyGlyphIcon(glyph: item.activeIcon, size: compact ? 20 : 23),
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

  Widget _buildPresetNavigationShell({
    required AppNavigationMode mode,
    required dynamic theme,
    required Widget content,
    required List<_NavDestinationItem> navItems,
    required int selectedIndex,
    required Widget? floatingActionButton,
    required ValueChanged<int> onDestinationTap,
  }) {
    final colors = context.colors;
    final accent = theme.accentColor as Color;
    final foreground = theme.primaryTextColor as Color;
    final secondary = theme.secondaryTextColor as Color;
    final isDark = theme.brightness == Brightness.dark;
    final isInstagram = mode == AppNavigationMode.instagramStyle;
    final isIos = mode == AppNavigationMode.iosStyle;
    final isOneUi = mode == AppNavigationMode.oneUi;
    final isTelegram = mode == AppNavigationMode.telegramStyle;
    final isBubbles = mode == AppNavigationMode.gestureTabs;
    final isBasic = mode == AppNavigationMode.basicTabStyle;
    final barHeight = isInstagram
        ? 54.0
        : isBasic
            ? 52.0
            : isIos
                ? 70.0
                : isBubbles
                    ? 68.0
                    : 72.0;
    final horizontalPadding = isIos ? 10.0 : 0.0;
    final itemWidth = isInstagram
        ? 58.0
        : isBasic
            ? 76.0
            : isTelegram
                ? 82.0
                : isOneUi
                    ? 88.0
                    : isBubbles
                        ? 76.0
                        : 80.0;
    final dockBackground = isIos
        ? colors.surface.withValues(alpha: isDark ? 0.96 : 0.93)
        : isTelegram
            ? colors.surfaceElevated
            : colors.surface;
    final dockDecoration = BoxDecoration(
      color: dockBackground,
      border: Border(
        top: BorderSide(
          color: colors.borderSubtle,
          width: isInstagram ? 0.7 : 1.0,
        ),
        bottom: isIos
            ? BorderSide(color: colors.borderSubtle, width: 0.7)
            : BorderSide.none,
      ),
      boxShadow: isIos
          ? <BoxShadow>[
              BoxShadow(
                color: colors.shadow,
                blurRadius: 14,
                offset: const Offset(0, -3),
              ),
            ]
          : null,
    );

    return Scaffold(
      backgroundColor: theme.backgroundColor,
      body: content,
      floatingActionButton: floatingActionButton,
      floatingActionButtonLocation: FloatingActionButtonLocation.endFloat,
      bottomNavigationBar: SafeArea(
        top: false,
        child: Padding(
          padding: EdgeInsets.symmetric(horizontal: horizontalPadding),
          child: Container(
            height: barHeight,
            decoration: dockDecoration,
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              physics: const BouncingScrollPhysics(),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: List<Widget>.generate(navItems.length, (index) {
                  final item = navItems[index];
                  final selected = index == selectedIndex;
                  final icon = selected ? item.activeIcon : item.icon;
                  final useCircle = isIos || isBubbles;
                  final activeIcon = Container(
                    width: isBubbles ? 34 : 32,
                    height: isBubbles ? 34 : 32,
                    alignment: Alignment.center,
                    decoration: useCircle
                        ? BoxDecoration(
                            shape: BoxShape.circle,
                            color: selected
                                ? accent.withValues(alpha: isIos ? 0.18 : 0.22)
                                : Colors.transparent,
                            border: Border.all(
                              color: selected
                                  ? accent
                                  : colors.borderSubtle,
                              width: selected ? 1.3 : 0.7,
                            ),
                          )
                        : BoxDecoration(
                            color: selected && (isOneUi || isTelegram)
                                ? accent.withValues(alpha: 0.13)
                                : Colors.transparent,
                            border: isBasic && selected
                                ? Border(
                                    top: BorderSide(color: accent, width: 2.0),
                                  )
                                : null,
                          ),
                    child: Center(
                      child: ChatyGlyphIcon(
                        glyph: icon,
                        size: isInstagram ? 23 : (isBasic ? 19 : 21),
                        color: selected ? accent : secondary,
                      ),
                    ),
                  );

                  return Semantics(
                    button: true,
                    selected: selected,
                    label: item.label,
                    child: Tooltip(
                      message: item.label,
                      child: InkWell(
                        onTap: () => onDestinationTap(index),
                        child: SizedBox(
                          width: itemWidth,
                          height: barHeight,
                          child: Padding(
                            padding: EdgeInsets.symmetric(
                              horizontal: isBasic ? 2 : 4,
                              vertical: isInstagram ? 0 : 4,
                            ),
                            child: isInstagram
                                ? Center(
                                    child: Container(
                                      padding: const EdgeInsets.symmetric(vertical: 5),
                                      decoration: BoxDecoration(
                                        border: Border(
                                          top: BorderSide(
                                            color: selected
                                                ? accent
                                                : Colors.transparent,
                                            width: 2,
                                          ),
                                        ),
                                      ),
                                      child: ChatyGlyphIcon(
                                        glyph: icon,
                                        size: 23,
                                        color: selected ? foreground : secondary,
                                      ),
                                    ),
                                  )
                                : isBasic
                                    ? Column(
                                        mainAxisAlignment: MainAxisAlignment.center,
                                        children: [
                                          ChatyGlyphIcon(
                                            glyph: icon,
                                            size: 18,
                                            color: selected ? accent : secondary,
                                          ),
                                          const SizedBox(height: 3),
                                          Text(
                                            item.label,
                                            maxLines: 1,
                                            overflow: TextOverflow.ellipsis,
                                            style: TextStyle(
                                              color: selected ? foreground : secondary,
                                              fontSize: 9.5,
                                              fontWeight: selected ? FontWeight.w800 : FontWeight.w500,
                                            ),
                                          ),
                                        ],
                                      )
                                    : Column(
                                        mainAxisAlignment: MainAxisAlignment.center,
                                        mainAxisSize: MainAxisSize.min,
                                        children: [
                                          if (isOneUi)
                                            Container(
                                              width: selected ? 22 : 0,
                                              height: 2,
                                              color: selected ? accent : Colors.transparent,
                                            ),
                                          isTelegram
                                              ? activeIcon
                                              : useCircle
                                                  ? activeIcon
                                                  : Container(
                                                      width: itemWidth - 8,
                                                      height: 34,
                                                      decoration: BoxDecoration(
                                                        color: selected && isOneUi
                                                            ? accent.withValues(alpha: 0.13)
                                                            : Colors.transparent,
                                                        border: isBasic && selected
                                                            ? Border(
                                                                top: BorderSide(color: accent, width: 2),
                                                              )
                                                            : null,
                                                      ),
                                                      alignment: Alignment.center,
                                                      child: ChatyGlyphIcon(
                                                        glyph: icon,
                                                        size: 20,
                                                        color: selected ? accent : secondary,
                                                      ),
                                                    ),
                                          const SizedBox(height: 3),
                                          Text(
                                            item.label,
                                            maxLines: 1,
                                            overflow: TextOverflow.ellipsis,
                                            style: TextStyle(
                                              color: selected ? foreground : secondary,
                                              fontSize: isBubbles ? 9 : 10,
                                              fontWeight: selected ? FontWeight.w800 : FontWeight.w500,
                                            ),
                                          ),
                                          if (isTelegram)
                                            Container(
                                              width: selected ? 16 : 0,
                                              height: 2,
                                              color: selected ? accent : Colors.transparent,
                                            ),
                                        ],
                                      ),
                          ),
                        ),
                      ),
                    ),
                  );
                }),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildBottomShell({
    required dynamic theme,
    required Widget content,
    required AppearanceVariantController appearance,
    required NavigationTemplate navigationTemplate,
    required VoidCallback onCenterAction,
    required List<_NavDestinationItem> navItems,
    required _NavDestinationItem activeItem,
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

    final double barHeight =
        navigationTemplate.height.clamp(48.0, 88.0).toDouble();

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
      floatingActionButton: _buildContextualFab(
        context: context,
        theme: theme,
        colors: colors,
        accent: accent,
        activeItem: activeItem,
      ),
      floatingActionButtonLocation: FloatingActionButtonLocation.endFloat,
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
                children: _buildNavigationChildren(
                  navigationTemplate: navigationTemplate,
                  navItems: navItems,
                  selectedIndex: selectedIndex,
                  styleName: styleName,
                  theme: theme,
                  accent: accent,
                  isDark: isDark,
                  onDestinationTap: onDestinationTap,
                  onCenterAction: onCenterAction,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  List<Widget> _buildNavigationChildren({
    required NavigationTemplate navigationTemplate,
    required List<_NavDestinationItem> navItems,
    required int selectedIndex,
    required String styleName,
    required dynamic theme,
    required Color accent,
    required bool isDark,
    required ValueChanged<int> onDestinationTap,
    required VoidCallback onCenterAction,
  }) {
    final children = <Widget>[];
    final hasCenterAction =
        navigationTemplate.hasCenterAction && navItems.length >= 3;
    final centerSlot = hasCenterAction ? navItems.length ~/ 2 : -1;

    for (var index = 0; index < navItems.length; index++) {
      if (hasCenterAction && index == centerSlot) {
        children.add(
          Expanded(
            child: Center(
              child: Tooltip(
                message: navigationTemplate.centerActionId == 'camera'
                    ? 'Open camera effects'
                    : 'Quick action',
                child: Semantics(
                  button: true,
                  label: navigationTemplate.centerActionId == 'camera'
                      ? 'Open camera effects'
                      : 'Quick action',
                  child: Material(
                    color: Colors.transparent,
                    child: InkWell(
                      onTap: onCenterAction,
                      customBorder: const CircleBorder(),
                      child: Ink(
                        width: 46,
                        height: 46,
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            colors: <Color>[
                              accent,
                              accent.withValues(alpha: 0.82),
                            ],
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                          ),
                          shape: BoxShape.circle,
                          boxShadow: <BoxShadow>[
                            BoxShadow(
                              color: accent.withValues(alpha: 0.30),
                              blurRadius: 10,
                              offset: const Offset(0, 3),
                            ),
                          ],
                        ),
                        child: Icon(
                          navigationTemplate.centerActionIcon ??
                              Icons.bolt_rounded,
                          color: context.colors.onPrimary,
                          size: 23,
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        );
      }

      final item = navItems[index];
      final isSelected = selectedIndex == index;
      final isCenter = !hasCenterAction &&
          navItems.length.isOdd &&
          index == navItems.length ~/ 2;
      children.add(
        _buildCustomNavItem(
          item: item,
          isSelected: isSelected,
          isCenter: isCenter,
          styleName: styleName,
          theme: theme,
          accent: accent,
          isDark: isDark,
          onTap: () => onDestinationTap(index),
        ),
      );
    }

    if (navigationTemplate.hasCenterAction && navItems.length < 3) {
      children.add(
        Expanded(
          child: IconButton(
            tooltip: 'Quick action',
            onPressed: onCenterAction,
            icon: Icon(
              navigationTemplate.centerActionIcon ?? Icons.bolt_rounded,
              color: accent,
            ),
          ),
        ),
      );
    }
    return children;
  }

  Future<void> _handleNavigationCenterAction(
    NavigationTemplate template,
  ) async {
    if (template.centerActionId != 'camera') {
      EffectPickerSheet.show(context);
      return;
    }

    final result = await ChatyCameraCaptureScreen.open(
      context,
      mode: ChatyCaptureMode.story,
    );
    if (result == null || !mounted) return;
    try {
      await StatusService(
        preferences: locator<ChatyPreferencesController>(),
      ).publishMediaFile(
        path: result.path,
        mediaType: 'image',
        text: result.caption,
        displayName: 'chaty_story.jpg',
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Status posted.')),
      );
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(error.toString().replaceFirst('Exception: ', '')),
        ),
      );
    }
  }

  void _showMoreMenu(
    BuildContext context, {
    required dynamic theme,
    required List<_NavDestinationItem> overflowDestinations,
    required ValueChanged<int> onSelect,
  }) {
    final colors = context.colors;
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (sheetContext) {
        return SafeArea(
          child: Container(
            margin: const EdgeInsets.fromLTRB(12, 0, 12, 12),
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
                Center(
                  child: Container(
                    width: 36,
                    height: 4,
                    margin: const EdgeInsets.only(top: 10, bottom: 4),
                    decoration: BoxDecoration(
                      color: colors.foregroundTertiary.withValues(alpha: 0.3),
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 10, 16, 10),
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
                              'Quick access to extended views & settings',
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
                        onPressed: () => Navigator.of(sheetContext).pop(),
                      ),
                    ],
                  ),
                ),
                Divider(color: colors.divider, height: 1),
                Flexible(
                  child: ListView.separated(
                    shrinkWrap: true,
                    padding: const EdgeInsets.symmetric(
                      vertical: 8,
                      horizontal: 8,
                    ),
                    itemCount: overflowDestinations.length,
                    separatorBuilder: (_, index) => Divider(
                      color: colors.divider.withValues(alpha: 0.5),
                      height: 1,
                      indent: 48,
                    ),
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
                          child: ChatyGlyphIcon(glyph: item.icon,
                            color: colors.primary,
                            size: 20,
                          ),
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
                          Navigator.of(sheetContext).pop();
                          onSelect(i);
                        },
                      );
                    },
                  ),
                ),
                const SizedBox(height: 8),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget? _buildContextualFab({
    required BuildContext context,
    required dynamic theme,
    required AppColors colors,
    required Color accent,
    required _NavDestinationItem activeItem,
  }) {
    final prefsController = locator<ChatyPreferencesController>();
    final homePrefs = prefsController.home;

    if (homePrefs.hideFab) {
      return null;
    }

    final (IconData defaultIcon, String tooltip, VoidCallback action) =
        switch (activeItem.id) {
      'chats' => (
          homePrefs.showMetaAiIcon
              ? Icons.psychology_rounded
              : Icons.chat_bubble_rounded,
          'New conversation',
          () {
            HapticFeedback.lightImpact();
            Navigator.of(context).push(
              MaterialPageRoute(
                builder: (_) => NewChatScreen(
                  theme: theme,
                  dataStore: locator<ChatyDataStore>(),
                  preferencesController:
                      locator<ChatyPreferencesController>(),
                ),
              ),
            );
          },
        ),
      'groups' => (
          Icons.group_add_rounded,
          'New group',
          () {
            HapticFeedback.lightImpact();
            Navigator.of(context).push(
              MaterialPageRoute(
                builder: (_) => NewChatScreen(
                  theme: theme,
                  dataStore: locator<ChatyDataStore>(),
                  preferencesController:
                      locator<ChatyPreferencesController>(),
                  startInGroupMode: true,
                ),
              ),
            );
          },
        ),
      'updates' => (
          Icons.camera_alt_rounded,
          'New status',
          () async {
            HapticFeedback.lightImpact();
            final result = await ChatyCameraCaptureScreen.open(
              context,
              mode: ChatyCaptureMode.story,
            );
            if (result != null && context.mounted) {
              try {
                await StatusService().publishMediaFile(
                  path: result.path,
                  mediaType: 'image',
                  text: result.caption,
                  displayName: 'chaty_story.jpg',
                );
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Status posted successfully.')),
                  );
                }
              } catch (e) {
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text('Error: $e')),
                  );
                }
              }
            }
          },
        ),
      'tasks' => (
          Icons.add_task_rounded,
          'Create task',
          () {
            HapticFeedback.lightImpact();
            final conversations =
                locator<ChatyDataStore>().conversations;
            showModalBottomSheet(
              context: context,
              backgroundColor: Colors.transparent,
              isScrollControlled: true,
              builder: (ctx) => TaskCreateEditModal(
                theme: theme,
                dataStore: locator<ChatyDataStore>(),
                sourceConversationId: conversations.isNotEmpty
                    ? conversations.first.id
                    : 'general',
              ),
            );
          },
        ),
      'calls' => (
          Icons.add_ic_call_rounded,
          'New call',
          () {
            HapticFeedback.lightImpact();
            _openNewCallPicker(context, theme);
          },
        ),
      'desktop' => (
          Icons.qr_code_scanner_rounded,
          'Scan QR',
          () {
            HapticFeedback.lightImpact();
            Navigator.of(context).push(
              MaterialPageRoute(
                builder: (_) => LinkedDevicesQrScreen(
                  dataStore: locator<ChatyDataStore>(),
                  relationshipService: locator(),
                  preferencesController:
                      locator<ChatyPreferencesController>(),
                  themeController: locator<ThemeController>(),
                  devicesOnly: true,
                ),
              ),
            );
          },
        ),
      _ => (
          Icons.chat_bubble_rounded,
          'New message',
          () {
            HapticFeedback.lightImpact();
            Navigator.of(context).push(
              MaterialPageRoute(
                builder: (_) => NewChatScreen(
                  theme: theme,
                  dataStore: locator<ChatyDataStore>(),
                  preferencesController:
                      locator<ChatyPreferencesController>(),
                ),
              ),
            );
          },
        ),
    };

    final effectiveFabColor = homePrefs.fabNormalColor != null
        ? Color(homePrefs.fabNormalColor!)
        : accent;
    final effectiveSplashColor = homePrefs.fabPressedColor != null
        ? Color(homePrefs.fabPressedColor!)
        : null;
    final effectiveIconColor = homePrefs.fabIconsColor != null
        ? Color(homePrefs.fabIconsColor!)
        : colors.onPrimary;

    return FloatingActionButton(
      heroTag: 'chaty_contextual_nav_fab',
      tooltip: tooltip,
      shape: const CircleBorder(),
      elevation: 4.5,
      backgroundColor: effectiveFabColor,
      splashColor: effectiveSplashColor,
      foregroundColor: effectiveIconColor,
      onPressed: action,
      child: AnimatedSwitcher(
        duration: const Duration(milliseconds: 250),
        transitionBuilder: (child, anim) {
          return ScaleTransition(
            scale: anim,
            child: RotationTransition(
              turns: Tween<double>(begin: 0.85, end: 1.0).animate(anim),
              child: child,
            ),
          );
        },
        child: activeItem.id == 'chats'
            ? Stack(
                key: ValueKey<String>(
                  'chats_message_${homePrefs.showMetaAiIcon}',
                ),
                clipBehavior: Clip.none,
                children: [
                  Icon(
                    Icons.chat_bubble_rounded,
                    size: 24,
                    color: effectiveIconColor,
                  ),
                  if (homePrefs.showMetaAiIcon)
                    Positioned(
                      right: -5,
                      top: -5,
                      child: Container(
                        width: 16,
                        height: 16,
                        decoration: BoxDecoration(
                          color: effectiveFabColor,
                          shape: BoxShape.circle,
                          border: Border.all(
                            color: effectiveIconColor.withValues(alpha: 0.95),
                            width: 1,
                          ),
                        ),
                        child: Icon(
                          Icons.auto_awesome_rounded,
                          size: 10,
                          color: effectiveIconColor,
                        ),
                      ),
                    ),
                ],
              )
            : Icon(
                defaultIcon,
                key: ValueKey<String>(
                  '${activeItem.id}_${homePrefs.showMetaAiIcon}',
                ),
                size: 24,
                color: effectiveIconColor,
              ),
      ),
    );
  }

  void _openNewCallPicker(BuildContext context, dynamic theme) {
    final dataStore = locator<ChatyDataStore>();
    final contacts = dataStore.contacts;
    final colors = context.colors;

    showModalBottomSheet<void>(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (ctx) => Container(
        height: MediaQuery.of(ctx).size.height * 0.65,
        decoration: BoxDecoration(
          color: colors.surface,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
          border: Border.all(color: colors.borderSubtle, width: 0.8),
        ),
        child: Column(
          children: [
            Container(
              margin: const EdgeInsets.symmetric(vertical: 12),
              width: 44,
              height: 4,
              decoration: BoxDecoration(
                color: colors.borderSubtle,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 4, 12, 12),
              child: Row(
                children: [
                  Text(
                    'Select Contact to Call',
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w700,
                      color: colors.foreground,
                    ),
                  ),
                  const Spacer(),
                  IconButton(
                    tooltip: 'Dial a number',
                    icon: Icon(Icons.dialpad_rounded, color: colors.primary),
                    onPressed: () {
                      Navigator.of(ctx).pop();
                      _openDialpad(context, theme);
                    },
                  ),
                  IconButton(
                    icon: Icon(Icons.close_rounded, color: colors.foregroundSecondary),
                    onPressed: () => Navigator.of(ctx).pop(),
                  ),
                ],
              ),
            ),
            Divider(height: 1, color: colors.divider),
            Expanded(
              child: contacts.isEmpty
                  ? Center(
                      child: Text(
                        'No contacts available',
                        style: TextStyle(color: colors.foregroundSecondary),
                      ),
                    )
                  : ListView.builder(
                      itemCount: contacts.length,
                      itemBuilder: (ctx, i) {
                        final contact = contacts[i];
                        return ListTile(
                          contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 4),
                          leading: AppAvatar(
                            initials: contact.displayName.isNotEmpty
                                ? contact.displayName.substring(0, 1)
                                : '?',
                            colorHex: contact.avatarColorHex,
                            size: 44,
                          ),
                          title: Text(
                            contact.displayName,
                            style: TextStyle(
                              color: colors.foreground,
                              fontWeight: FontWeight.w600,
                              fontSize: 15,
                            ),
                          ),
                          subtitle: Text(
                            contact.about.isNotEmpty ? contact.about : contact.phone,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: colors.foregroundSecondary,
                              fontSize: 13,
                            ),
                          ),
                          trailing: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              IconButton(
                                icon: Icon(Icons.call_rounded, color: colors.primary),
                                tooltip: 'Voice Call',
                                onPressed: () {
                                  Navigator.of(ctx).pop();
                                  locator<CallSignalingService>().initiateCall(
                                    remoteUserId: contact.id,
                                    remoteDisplayName: contact.displayName,
                                    remoteAvatarInitials: contact.displayName.isNotEmpty
                                        ? contact.displayName.substring(0, 1)
                                        : '?',
                                    isVideo: false,
                                  );
                                  Navigator.of(context).push(
                                    MaterialPageRoute<void>(
                                      builder: (_) => OngoingCallScreen(theme: theme),
                                    ),
                                  );
                                },
                              ),
                              IconButton(
                                icon: Icon(Icons.videocam_rounded, color: colors.primary),
                                tooltip: 'Video Call',
                                onPressed: () {
                                  Navigator.of(ctx).pop();
                                  locator<CallSignalingService>().initiateCall(
                                    remoteUserId: contact.id,
                                    remoteDisplayName: contact.displayName,
                                    remoteAvatarInitials: contact.displayName.isNotEmpty
                                        ? contact.displayName.substring(0, 1)
                                        : '?',
                                    isVideo: true,
                                  );
                                  Navigator.of(context).push(
                                    MaterialPageRoute<void>(
                                      builder: (_) => OngoingCallScreen(theme: theme),
                                    ),
                                  );
                                },
                              ),
                            ],
                          ),
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }


  void _openDialpad(BuildContext context, dynamic theme) {
    final numberController = TextEditingController();

    showModalBottomSheet<void>(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (sheetContext) => StatefulBuilder(
        builder: (sheetContext, setSheetState) {
          final colors = sheetContext.colors;
          const keys = <String>[
            '1', '2', '3',
            '4', '5', '6',
            '7', '8', '9',
            '*', '0', '#',
          ];
          return Padding(
            padding: EdgeInsets.only(
              bottom: MediaQuery.viewInsetsOf(sheetContext).bottom,
            ),
            child: Container(
              height: MediaQuery.sizeOf(sheetContext).height * 0.72,
              padding: const EdgeInsets.fromLTRB(20, 12, 20, 20),
              decoration: BoxDecoration(
                color: colors.surface,
                borderRadius: const BorderRadius.vertical(
                  top: Radius.circular(28),
                ),
                border: Border.all(color: colors.borderSubtle),
              ),
              child: Column(
                children: [
                  Container(
                    width: 44,
                    height: 4,
                    decoration: BoxDecoration(
                      color: colors.borderSubtle,
                      borderRadius: BorderRadius.circular(4),
                    ),
                  ),
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          'Dial a Chaty contact',
                          style: TextStyle(
                            color: colors.foreground,
                            fontSize: 19,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ),
                      IconButton(
                        tooltip: 'Close keypad',
                        onPressed: () => Navigator.of(sheetContext).pop(),
                        icon: Icon(Icons.close_rounded,
                            color: colors.foregroundSecondary),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: numberController,
                    autofocus: false,
                    keyboardType: TextInputType.phone,
                    textAlign: TextAlign.center,
                    onChanged: (_) => setSheetState(() {}),
                    style: TextStyle(
                      color: colors.foreground,
                      fontSize: 27,
                      fontWeight: FontWeight.w600,
                      letterSpacing: 1.2,
                    ),
                    decoration: InputDecoration(
                      hintText: '+ country code / phone number',
                      hintStyle: TextStyle(
                        color: colors.foregroundTertiary,
                        fontSize: 14,
                        letterSpacing: 0,
                      ),
                      filled: true,
                      fillColor: colors.surfaceSecondary,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(16),
                        borderSide: BorderSide.none,
                      ),
                      suffixIcon: numberController.text.isEmpty
                          ? null
                          : IconButton(
                              tooltip: 'Delete last digit',
                              onPressed: () {
                                final text = numberController.text;
                                if (text.isNotEmpty) {
                                  numberController.text =
                                      text.substring(0, text.length - 1);
                                  numberController.selection =
                                      TextSelection.collapsed(
                                    offset: numberController.text.length,
                                  );
                                  setSheetState(() {});
                                }
                              },
                              icon: Icon(Icons.backspace_outlined,
                                  color: colors.foregroundSecondary),
                            ),
                    ),
                  ),
                  const SizedBox(height: 14),
                  Expanded(
                    child: Center(
                      child: ConstrainedBox(
                        constraints: const BoxConstraints(maxWidth: 330),
                        child: GridView.count(
                          physics: const NeverScrollableScrollPhysics(),
                          crossAxisCount: 3,
                          mainAxisSpacing: 8,
                          crossAxisSpacing: 22,
                          childAspectRatio: 1.25,
                          children: [
                            for (final key in keys)
                              InkWell(
                                borderRadius: BorderRadius.circular(22),
                                onTap: () {
                                  numberController.text += key;
                                  numberController.selection =
                                      TextSelection.collapsed(
                                    offset: numberController.text.length,
                                  );
                                  setSheetState(() {});
                                },
                                child: Center(
                                  child: Container(
                                    width: 66,
                                    height: 52,
                                    alignment: Alignment.center,
                                    decoration: BoxDecoration(
                                      color: colors.surfaceSecondary,
                                      borderRadius: BorderRadius.circular(18),
                                    ),
                                    child: Text(
                                      key,
                                      style: TextStyle(
                                        color: colors.foreground,
                                        fontSize: 23,
                                        fontWeight: FontWeight.w600,
                                      ),
                                    ),
                                  ),
                                ),
                              ),
                          ],
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  SizedBox(
                    width: 66,
                    height: 58,
                    child: FilledButton(
                      onPressed: numberController.text
                              .replaceAll(RegExp(r'[^0-9]'), '')
                              .length <
                          5
                          ? null
                          : () async {
                              final number = numberController.text.trim();
                              Navigator.of(sheetContext).pop();
                              await _callChatyContactByPhone(
                                context,
                                theme,
                                number,
                              );
                            },
                      style: FilledButton.styleFrom(
                        shape: const CircleBorder(),
                        backgroundColor: colors.success,
                        foregroundColor: colors.onPrimary,
                      ),
                      child: const Icon(Icons.call_rounded, size: 25),
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'Calls connect to registered Chaty contacts only.',
                    style: TextStyle(
                      color: colors.foregroundSecondary,
                      fontSize: 11.5,
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    ).whenComplete(numberController.dispose);
  }

  Future<void> _callChatyContactByPhone(
    BuildContext context,
    dynamic theme,
    String phoneNumber,
  ) async {
    final digits = phoneNumber.replaceAll(RegExp(r'[^0-9]'), '');
    final dataStore = locator<ChatyDataStore>();
    dynamic matchedContact;

    for (final contact in dataStore.contacts) {
      final contactDigits = contact.phone.replaceAll(RegExp(r'[^0-9]'), '');
      if (contactDigits.isEmpty) continue;
      final isExact = contactDigits == digits;
      final isCountryCodeVariant = contactDigits.length >= 7 &&
          digits.length >= 7 &&
          (contactDigits.endsWith(digits) || digits.endsWith(contactDigits));
      if (isExact || isCountryCodeVariant) {
        matchedContact = contact;
        break;
      }
    }

    if (matchedContact == null) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'No Chaty contact matches that number. Calls need a registered Chaty account.',
            ),
          ),
        );
      }
      return;
    }

    try {
      await locator<CallSignalingService>().initiateCall(
        remoteUserId: matchedContact.id as String,
        remoteDisplayName: matchedContact.displayName as String,
        remoteAvatarInitials: (matchedContact.displayName as String).isNotEmpty
            ? (matchedContact.displayName as String).substring(0, 1)
            : '?',
        remoteAvatarColorHex: matchedContact.avatarColorHex as String?,
        isVideo: false,
      );
      if (!context.mounted) return;
      await Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => OngoingCallScreen(theme: theme),
        ),
      );
    } catch (error) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Unable to start call: ${error.toString().replaceFirst('Exception: ', '')}',
          ),
        ),
      );
    }
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
                    ChatyGlyphIcon(
                      glyph: isSelected ? item.activeIcon : item.icon,
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
                  ChatyGlyphIcon(
                    glyph: isSelected ? item.activeIcon : item.icon,
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
                  ChatyGlyphIcon(
                    glyph: isSelected ? item.activeIcon : item.icon,
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
                  child: ChatyGlyphIcon(glyph: isSelected ? item.activeIcon : item.icon,
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
                  ChatyGlyphIcon(
                    glyph: isSelected ? item.activeIcon : item.icon,
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
                    ChatyGlyphIcon(
                      glyph: isSelected ? item.activeIcon : item.icon,
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
                        child: ChatyGlyphIcon(glyph: item.activeIcon,
                          size: 24,
                          color: context.colors.onPrimary,
                        ),
                      )
                    : Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          ChatyGlyphIcon(
                            glyph: isSelected ? item.activeIcon : item.icon,
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
                child: ChatyGlyphIcon(glyph: isSelected ? item.activeIcon : item.icon,
                  size: 21,
                  color: isSelected ? accent : unselectedFg,
                ),
              ),

              // 9. MINIMAL ICON DOCK (Image 1 top left)
              'Minimal Icon Dock' => AnimatedScale(
                scale: isSelected ? 1.15 : 1.0,
                duration: const Duration(milliseconds: 180),
                child: ChatyGlyphIcon(glyph: isSelected ? item.activeIcon : item.icon,
                  size: 22,
                  color: isSelected ? accent : unselectedFg,
                ),
              ),

              // 10. CLASSIC LABEL BAR (Standard native tabs)
              'Classic Label Bar' => Column(
                mainAxisAlignment: MainAxisAlignment.center,

                children: [
                  ChatyGlyphIcon(
                    glyph: isSelected ? item.activeIcon : item.icon,
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
                    ChatyGlyphIcon(
                      glyph: isSelected ? item.activeIcon : item.icon,
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
                    ChatyGlyphIcon(
                      glyph: isSelected ? item.activeIcon : item.icon,
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
  final ChatyGlyph icon;
  final ChatyGlyph activeIcon;
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

class _CurvedRadialDrawerScaffold extends StatefulWidget {
  final dynamic theme;
  final int selectedIndex;
  final List<_NavDestinationItem> navItems;
  final ValueChanged<int> onSelect;
  final Widget child;
  final Widget? floatingActionButton;

  const _CurvedRadialDrawerScaffold({
    required this.theme,
    required this.selectedIndex,
    required this.navItems,
    required this.onSelect,
    required this.child,
    required this.floatingActionButton,
  });

  @override
  State<_CurvedRadialDrawerScaffold> createState() =>
      _CurvedRadialDrawerScaffoldState();
}

class _CurvedRadialDrawerScaffoldState
    extends State<_CurvedRadialDrawerScaffold> {
  bool _menuOpen = false;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    return Scaffold(
      backgroundColor: widget.theme.backgroundColor as Color,
      floatingActionButton: widget.floatingActionButton,
      floatingActionButtonLocation: FloatingActionButtonLocation.endFloat,
      body: LayoutBuilder(
        builder: (context, constraints) {
          // Keep the arc compact enough to preserve visible chat context.
          // Width and height scale with the device, including older/smaller phones.
          // Fill the viewport edge-to-edge at the top, as in the reference.
          // Cap only wide tablet layouts so the radial menu stays compact.
          final panelWidth =
              constraints.maxWidth.clamp(0.0, 420.0).toDouble();
          final availableHeight = constraints.maxHeight;
          final panelHeight = availableHeight < 250
              ? availableHeight
              : (availableHeight * 0.56).clamp(240.0, 330.0).toDouble();
          final accent = widget.theme.accentColor as Color;
          final onAccent = widget.theme.onAccentColor as Color;

          return Stack(
            fit: StackFit.expand,
            children: [
              widget.child,
              if (_menuOpen)
                Positioned(
                  top: MediaQuery.paddingOf(context).top + 3,
                  left: 0,
                  width: panelWidth,
                  height: panelHeight,
                  child: ClipPath(
                    clipper: const _CurvedRadialMenuClipper(),
                    child: Container(
                      color: accent,
                      child: Stack(
                        clipBehavior: Clip.none,
                        children: [
                          Positioned(
                            right: 12,
                            top: 6,
                            child: Material(
                              color: onAccent.withValues(alpha: 0.96),
                              shape: const CircleBorder(),
                              child: IconButton(
                                tooltip: 'Close navigation menu',
                                visualDensity: VisualDensity.compact,
                                onPressed: () => setState(() => _menuOpen = false),
                                icon: Icon(
                                  Icons.close_rounded,
                                  color: accent,
                                  size: 19,
                                ),
                              ),
                            ),
                          ),
                          ..._radialMenuItems(
                            context,
                            panelWidth,
                            panelHeight,
                            widget.navItems,
                            widget.selectedIndex,
                            colors,
                            accent,
                            onAccent,
                          ),
                        ],
                      ),
                    ),
                  ),
                )
              else
                Positioned(
                  top: MediaQuery.paddingOf(context).top + 8,
                  left: 10,
                  child: Material(
                    elevation: 5,
                    color: colors.surface,
                    shape: const CircleBorder(),
                    child: IconButton(
                      tooltip: 'Open navigation menu',
                      onPressed: () => setState(() => _menuOpen = true),
                      icon: Icon(Icons.menu_rounded, color: colors.foreground),
                    ),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }

  List<Widget> _radialMenuItems(
    BuildContext context,
    double width,
    double height,
    List<_NavDestinationItem> navItems,
    int selectedIndex,
    AppColors colors,
    Color accent,
    Color onAccent,
  ) {
    if (navItems.isEmpty) return const <Widget>[];
    final count = navItems.length;
    final iconSize = count > 7 ? 23.0 : 26.0;

    // Match the reference's broad quarter-circle sweep: the first few icons
    // tuck toward the left edge before the arc opens out toward the bottom-right.
    return List<Widget>.generate(count, (index) {
      final progress = count <= 1 ? 0.0 : index / (count - 1);
      final left = (width *
              (0.20 -
                  0.09 * math.sin(math.pi * progress) +
                  0.44 * progress * progress))
          .toDouble();
      final top = (height *
              (0.04 + 0.50 * math.pow(progress, 0.95).toDouble()))
          .toDouble();
      final item = navItems[index];
      final activeIndex = selectedIndex < 0
          ? 0
          : selectedIndex >= count
              ? count - 1
              : selectedIndex;
      final selected = item.id == navItems[activeIndex].id;
      final labelWidth =
          (width - left - iconSize - 18).clamp(48.0, 180.0).toDouble();

      return Positioned(
        left: left,
        top: top,
        child: SizedBox(
          height: iconSize + 2,
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Material(
                color: Colors.transparent,
                shape: const CircleBorder(),
                child: InkWell(
                  customBorder: const CircleBorder(),
                  onTap: () {
                    widget.onSelect(index);
                    setState(() => _menuOpen = false);
                  },
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 180),
                    curve: Curves.easeOutCubic,
                    width: iconSize,
                    height: iconSize,
                    decoration: BoxDecoration(
                      color: selected ? onAccent : onAccent.withValues(alpha: 0.86),
                      shape: BoxShape.circle,
                      border: Border.all(
                        color: selected ? onAccent : onAccent.withValues(alpha: 0.7),
                        width: selected ? 1.4 : 0.7,
                      ),
                      boxShadow: selected
                          ? <BoxShadow>[
                              BoxShadow(
                                color: colors.shadow.withValues(alpha: 0.34),
                                blurRadius: 5,
                                offset: const Offset(0, 2),
                              ),
                            ]
                          : null,
                    ),
                    child: Center(
                      child: ChatyGlyphIcon(
                        glyph: selected ? item.activeIcon : item.icon,
                        size: iconSize * 0.52,
                        color: accent,
                      ),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 7),
              Transform.rotate(
                angle: -0.04 - progress * 0.30,
                alignment: Alignment.centerLeft,
                child: SizedBox(
                  width: labelWidth,
                  child: Material(
                    color: Colors.transparent,
                    child: InkWell(
                      onTap: () {
                        widget.onSelect(index);
                        setState(() => _menuOpen = false);
                      },
                      child: Align(
                        alignment: Alignment.centerLeft,
                        child: Text(
                          item.label.toUpperCase(),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            color: onAccent,
                            fontSize: selected ? 10.5 : 9.5,
                            fontWeight: selected ? FontWeight.w900 : FontWeight.w700,
                            letterSpacing: 0.25,
                            shadows: <Shadow>[
                              Shadow(
                                color: colors.shadow.withValues(alpha: 0.28),
                                blurRadius: 2,
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      );
    });
  }
}

class _CurvedRadialMenuClipper extends CustomClipper<Path> {
  const _CurvedRadialMenuClipper();

  @override
  Path getClip(Size size) {
    return Path()
      // The pink shape starts inset at the top-left, like the reference.
      ..moveTo(size.width * 0.16, 0)
      ..lineTo(size.width, 0)
      ..lineTo(size.width, size.height * 0.62)
      // Long outer curve forms the lower-right edge of the large circular panel.
      ..cubicTo(
        size.width * 0.78,
        size.height * 0.74,
        size.width * 0.52,
        size.height * 0.72,
        size.width * 0.30,
        size.height * 0.62,
      )
      // Return around the left side to create the open, sweeping quarter-circle.
      ..cubicTo(
        size.width * 0.08,
        size.height * 0.51,
        -size.width * 0.05,
        size.height * 0.31,
        size.width * 0.055,
        size.height * 0.12,
      )
      ..cubicTo(
        size.width * 0.075,
        size.height * 0.055,
        size.width * 0.11,
        0,
        size.width * 0.16,
        0,
      )
      ..close();
  }

  @override
  bool shouldReclip(covariant CustomClipper<Path> oldClipper) => false;
}

class _PerspectiveDrawerScaffold extends StatefulWidget {
  final dynamic theme;
  final int selectedIndex;
  final List<_NavDestinationItem> navItems;
  final ValueChanged<int> onSelect;
  final Widget child;
  final Widget? floatingActionButton;

  const _PerspectiveDrawerScaffold({
    required this.theme,
    required this.selectedIndex,
    required this.navItems,
    required this.onSelect,
    required this.child,
    required this.floatingActionButton,
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
      floatingActionButton: widget.floatingActionButton,
      floatingActionButtonLocation: FloatingActionButtonLocation.endFloat,
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
                                leading: ChatyGlyphIcon(glyph: isSel ? item.activeIcon : item.icon,
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
