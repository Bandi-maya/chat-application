import 'package:flutter/material.dart';
import '../../../data/repositories/chaty_data_store.dart';
import '../../../domain/models/preferences.dart';
import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/gb_design_system.dart';
import '../../../ui/core/design_system/design_system.dart';

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

class _HomePreviewCurveClipper extends CustomClipper<Path> {
  @override
  Path getClip(Size size) => Path()
    ..moveTo(0, 0)
    ..lineTo(size.width, 0)
    ..cubicTo(
      size.width * 1.02,
      size.height * 0.45,
      size.width * 0.72,
      size.height * 0.98,
      0,
      size.height * 0.78,
    )
    ..close();

  @override
  bool shouldReclip(covariant CustomClipper<Path> oldClipper) => false;
}

class _HeaderSettingsScreenState extends State<HeaderSettingsScreen> {
  static const List<String> _homeUiStyles = [
    // Keep the existing labels, but each now has its own layout implementation.
    'ONE UI',
    'WhatsApp UI Stock',
    'IOS STYLE',
    'BUBBLES TAB STYLE',
    'BASIC TAB STYLE',
    'WhatsApp OLD UI',
    'Floating Rail',
    '3D Perspective Drawer',
    'Modern Side Menu',
    'Curved Radial Menu',
    'Instagram Style',
    'Telegram Style',
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

  /// Keep the Home UI selector wired to the same persisted navigation-mode
  /// controller used by the main navigation shell. The selected home preset and
  /// the app shell therefore change together instead of leaving the shell stale.
  AppNavigationMode _navigationModeForHomeUiStyle(String style) {
    switch (style.trim().toLowerCase()) {
      // Every selector option resolves to a different layout mode.
      case 'one ui':
        return AppNavigationMode.oneUi;
      case 'whatsapp ui stock':
        return AppNavigationMode.bottomNav;
      case 'ios style':
        return AppNavigationMode.iosStyle;
      case 'bubbles tab style':
        return AppNavigationMode.gestureTabs;
      case 'basic tab style':
        return AppNavigationMode.basicTabStyle;
      case 'whatsapp old ui':
      case 'whatsapp-style top bar':
      case 'whatsapp style top bar':
        return AppNavigationMode.topWhatsAppBar;
      case 'floating rail':
        return AppNavigationMode.floatingIslandRail;
      case '3d perspective drawer':
        return AppNavigationMode.perspective3DDrawer;
      case 'modern side menu':
        return AppNavigationMode.modernSideMenu;
      case 'curved radial menu':
        return AppNavigationMode.curvedRadialDrawer;
      case 'instagram style':
        return AppNavigationMode.instagramStyle;
      case 'telegram style':
        return AppNavigationMode.telegramStyle;
      default:
        return locator<ThemeController>().navigationMode;
    }
  }

  String _selectedHomeUiStyle(HomePreferences home) {
    final saved = home.homeStyle.trim();
    if (_homeUiStyles.contains(saved)) return saved;

    // Older versions sometimes persisted the canonical Home preset instead
    // of the label shown by this selector. Reverse-map those values so a radio
    // choice remains visibly selected after an upgrade.
    switch (saved.toLowerCase()) {
      case 'classic':
        return 'WhatsApp OLD UI';
      case 'chaty default':
        return 'WhatsApp UI Stock';
      case 'cards':
        return 'ONE UI';
      case 'stories first':
        return 'IOS STYLE';
      case 'compact':
        return 'BUBBLES TAB STYLE';
      case 'productivity':
        return 'BASIC TAB STYLE';
    }

    // For newer shell modes, use the restored runtime mode when the stored
    // Home label came from a previous canonicalization step.
    return switch (locator<ThemeController>().navigationMode) {
      AppNavigationMode.oneUi => 'ONE UI',
      AppNavigationMode.iosStyle => 'IOS STYLE',
      AppNavigationMode.instagramStyle => 'Instagram Style',
      AppNavigationMode.telegramStyle => 'Telegram Style',
      AppNavigationMode.basicTabStyle => 'BASIC TAB STYLE',
      AppNavigationMode.topWhatsAppBar => 'WhatsApp OLD UI',
      AppNavigationMode.floatingIslandRail => 'Floating Rail',
      AppNavigationMode.perspective3DDrawer => '3D Perspective Drawer',
      AppNavigationMode.modernSideMenu => 'Modern Side Menu',
      AppNavigationMode.curvedRadialDrawer => 'Curved Radial Menu',
      AppNavigationMode.gestureTabs => 'BUBBLES TAB STYLE',
      AppNavigationMode.compactRail => 'BASIC TAB STYLE',
      AppNavigationMode.bottomNav => 'WhatsApp UI Stock',
    };
  }

  String _homeUiStyleDescription(String style) => switch (style) {
    'ONE UI' => 'Large title, spacious rows, and One UI bottom navigation',
    'WhatsApp UI Stock' => 'Classic conversation rows with a standard bottom bar',
    'IOS STYLE' => 'Minimal iOS-inspired icon dock and airy stories header',
    'BUBBLES TAB STYLE' => 'Swipe-first pages with circular bubble destinations',
    'BASIC TAB STYLE' => 'Compact square icon-and-label tabs',
    'WhatsApp OLD UI' => 'Classic WhatsApp top tabs aligned with the header',
    'Floating Rail' => 'Floating vertical icon rail',
    '3D Perspective Drawer' => 'Perspective-shifted content with a side drawer',
    'Modern Side Menu' => 'Labeled side drawer with identity header',
    'Curved Radial Menu' => 'Circular icon positions inside a themed curved menu',
    'Instagram Style' => 'Clean icon-only bottom dock with creator-style hierarchy',
    'Telegram Style' => 'Compact labeled navigation dock inspired by Telegram',
    _ => 'Home layout and navigation preview',
  };

  Widget _buildHomeUiPreview(
    String style,
    ThemeConfig theme, {
    Key? key,
    bool large = false,
  }) {
    final accent = theme.accentColor;
    final fg = theme.primaryTextColor;
    final muted = theme.secondaryTextColor;
    final surface = theme.surfaceColor;
    final bg = theme.backgroundColor;
    final mode = _navigationModeForHomeUiStyle(style);
    final scale = large ? 1.0 : 0.55;
    final previewWidth = large ? double.infinity : 100.0;
    final topTabs = mode == AppNavigationMode.topWhatsAppBar;
    final rail = mode == AppNavigationMode.floatingIslandRail;
    final isInstagram = mode == AppNavigationMode.instagramStyle;
    final isTelegram = mode == AppNavigationMode.telegramStyle;
    final isOneUi = mode == AppNavigationMode.oneUi;
    final isIos = mode == AppNavigationMode.iosStyle;
    final isBubbles = mode == AppNavigationMode.gestureTabs;
    final isBasic = mode == AppNavigationMode.basicTabStyle;
    final showBottomDock = !rail &&
        mode != AppNavigationMode.perspective3DDrawer &&
        mode != AppNavigationMode.modernSideMenu &&
        mode != AppNavigationMode.curvedRadialDrawer &&
        !topTabs;
    final sideDrawer = mode == AppNavigationMode.perspective3DDrawer ||
        mode == AppNavigationMode.modernSideMenu ||
        mode == AppNavigationMode.curvedRadialDrawer;
    final previewHeight = large
        ? (mode == AppNavigationMode.curvedRadialDrawer ? 205.0 : 128.0)
        : 62.0;

    return Container(
      key: key,
      width: previewWidth,
      height: previewHeight,
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.zero,
        border: Border.all(color: accent.withValues(alpha: 0.55)),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.zero,
        child: Stack(
          fit: StackFit.expand,
          children: [
            ColoredBox(color: bg),
            if (rail)
              Positioned(
                left: 3 * scale,
                top: 3 * scale,
                bottom: 3 * scale,
                width: 21 * scale,
                child: Container(
                  decoration: BoxDecoration(
                    color: surface,
                    borderRadius: BorderRadius.circular(8 * scale),
                    border: Border.all(color: accent.withValues(alpha: 0.3)),
                  ),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      for (final icon in <IconData>[
                        Icons.chat_bubble_rounded,
                        Icons.auto_stories_rounded,
                        Icons.call_rounded,
                        Icons.settings_rounded,
                      ])
                        Icon(icon, size: 11 * scale, color: accent),
                    ],
                  ),
                ),
              ),
            if (sideDrawer && mode != AppNavigationMode.curvedRadialDrawer)
              Positioned(
                left: 0,
                top: 0,
                bottom: 0,
                width: (large ? 92 : 36) * scale,
                child: Container(
                  padding: EdgeInsets.all(5 * scale),
                  color: surface,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      for (int i = 0; i < 4; i++)
                        Row(
                          children: [
                            Icon(
                              <IconData>[
                                Icons.chat_bubble_rounded,
                                Icons.auto_stories_rounded,
                                Icons.call_rounded,
                                Icons.settings_rounded,
                              ][i],
                              size: 10 * scale,
                              color: i == 0 ? accent : muted,
                            ),
                            if (large) ...[
                              const SizedBox(width: 4),
                              Expanded(
                                child: Container(
                                  height: 3,
                                  decoration: BoxDecoration(
                                    color: muted.withValues(alpha: 0.55),
                                    borderRadius: BorderRadius.circular(3),
                                  ),
                                ),
                              ),
                            ],
                          ],
                        ),
                    ],
                  ),
                ),
              ),
            Positioned(
              left: (rail ? 29 : sideDrawer && mode != AppNavigationMode.curvedRadialDrawer
                  ? (large ? 100 : 40)
                  : 0) * scale,
              right: 0,
              top: 0,
              bottom: (showBottomDock ? (large ? 21 : 12) : 4) * scale,
              child: Padding(
                padding: EdgeInsets.all(7 * scale),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        if (topTabs) ...[
                          Icon(Icons.menu_rounded, size: 12 * scale, color: accent),
                          SizedBox(width: 3 * scale),
                        ],
                        Expanded(
                          child: Container(
                            height: 4 * scale,
                            decoration: BoxDecoration(
                              color: topTabs ? accent : fg.withValues(alpha: 0.85),
                              borderRadius: BorderRadius.circular(4),
                            ),
                          ),
                        ),
                        if (large) ...[
                          const SizedBox(width: 8),
                          Icon(Icons.search_rounded, size: 13, color: fg),
                        ],
                      ],
                    ),
                    SizedBox(height: 5 * scale),
                    if (topTabs) ...[
                      Container(
                        height: 16 * scale,
                        decoration: BoxDecoration(
                          color: accent,
                          borderRadius: BorderRadius.circular(3 * scale),
                        ),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                          children: [
                            for (final icon in <IconData>[
                              Icons.chat_bubble_rounded,
                              Icons.auto_stories_rounded,
                              Icons.call_rounded,
                              Icons.settings_rounded,
                            ])
                              Icon(
                                icon,
                                size: 9 * scale,
                                color: theme.onAccentColor,
                              ),
                          ],
                        ),
                      ),
                      SizedBox(height: 3 * scale),
                    ],
                    SizedBox(height: 7 * scale),
                    if (style == 'IOS STYLE')
                      Row(
                        children: List.generate(
                          4,
                          (i) => Expanded(
                            child: Padding(
                              padding: EdgeInsets.only(right: 3 * scale),
                              child: Container(
                                height: 17 * scale,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: accent.withValues(alpha: 0.16 + i * 0.08),
                                  border: Border.all(color: accent.withValues(alpha: 0.65)),
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
                    for (int i = 0; i < 3; i++) ...[
                      SizedBox(height: 5 * scale),
                      Row(
                        children: [
                          Container(
                            width: 13 * scale,
                            height: 13 * scale,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: accent.withValues(alpha: 0.32 + i * 0.12),
                            ),
                          ),
                          SizedBox(width: 5 * scale),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Container(
                                  height: 3 * scale,
                                  width: (large ? 120.0 : 58.0) * scale,
                                  decoration: BoxDecoration(
                                    color: fg.withValues(alpha: 0.8),
                                    borderRadius: BorderRadius.circular(3),
                                  ),
                                ),
                                SizedBox(height: 3 * scale),
                                Container(
                                  height: 2 * scale,
                                  width: (large ? 170.0 : 64.0) * scale,
                                  decoration: BoxDecoration(
                                    color: muted.withValues(alpha: 0.7),
                                    borderRadius: BorderRadius.circular(3),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ],
                  ],
                ),
              ),
            ),
            if (mode == AppNavigationMode.curvedRadialDrawer)
              Positioned(
                left: 0,
                top: 0,
                width: large ? 246 : 58,
                height: large ? 174 : 37,
                child: ClipPath(
                  clipper: _HomePreviewCurveClipper(),
                  child: ColoredBox(
                    color: accent,
                    child: Stack(
                      clipBehavior: Clip.none,
                      children: [
                        Positioned(
                          right: large ? 7 : 2,
                          top: large ? 7 : 2,
                          child: Icon(
                            Icons.close_rounded,
                            color: theme.onAccentColor,
                            size: large ? 14 : 7,
                          ),
                        ),
                        for (int i = 0; i < 5; i++)
                          Positioned(
                            left: large ? 16 + i * 19.0 : 4 + i * 4.2,
                            top: large ? 10 + i * 27.0 : 7 + i * 5.1,
                            child: Container(
                              width: large ? 25 : 9,
                              height: large ? 25 : 9,
                              alignment: Alignment.center,
                              decoration: BoxDecoration(
                                color: theme.onAccentColor,
                                shape: BoxShape.circle,
                              ),
                              child: Icon(
                                <IconData>[
                                  Icons.chat_bubble_rounded,
                                  Icons.photo_camera_rounded,
                                  Icons.call_rounded,
                                  Icons.checklist_rounded,
                                  Icons.settings_rounded,
                                ][i],
                                color: accent,
                                size: large ? 12 : 5.5,
                              ),
                            ),
                          ),
                        if (large)
                          Positioned(
                            left: 48,
                            top: 17,
                            child: Text(
                              'CHATS',
                              style: TextStyle(
                                color: theme.onAccentColor,
                                fontSize: 9,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          ),
                        if (large)
                          Positioned(
                            left: 67,
                            top: 45,
                            child: Text(
                              'UPDATES',
                              style: TextStyle(
                                color: theme.onAccentColor,
                                fontSize: 9,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          ),
                      ],
                    ),
                  ),
                ),
              ),
            if (showBottomDock)
              Positioned(
                left: 0,
                right: 0,
                bottom: 0,
                height: (isInstagram ? 18.0 : isBasic ? 26.0 : 25.0) * scale,
                child: Container(
                  decoration: BoxDecoration(
                    color: surface,
                    border: Border(
                      top: BorderSide(color: muted.withValues(alpha: 0.28), width: 0.7),
                    ),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      for (int i = 0; i < 4; i++)
                        Expanded(
                          child: Padding(
                            padding: EdgeInsets.symmetric(horizontal: 1 * scale),
                            child: isBubbles
                                ? Center(
                                    child: Container(
                                      width: 16 * scale,
                                      height: 16 * scale,
                                      decoration: BoxDecoration(
                                        color: i == 0 ? accent.withValues(alpha: 0.18) : Colors.transparent,
                                        shape: BoxShape.circle,
                                        border: Border.all(
                                          color: i == 0 ? accent : muted.withValues(alpha: 0.45),
                                          width: 0.65,
                                        ),
                                      ),
                                      child: Icon(
                                        <IconData>[
                                          Icons.chat_bubble_rounded,
                                          Icons.auto_stories_rounded,
                                          Icons.call_rounded,
                                          Icons.settings_rounded,
                                        ][i],
                                        size: 8 * scale,
                                        color: i == 0 ? accent : muted,
                                      ),
                                    ),
                                  )
                                : isOneUi
                                    ? Column(
                                        mainAxisAlignment: MainAxisAlignment.center,
                                        children: [
                                          Container(
                                            width: i == 0 ? 14 * scale : 0,
                                            height: 1.5 * scale,
                                            color: accent,
                                          ),
                                          Icon(
                                            <IconData>[
                                              Icons.chat_bubble_rounded,
                                              Icons.auto_stories_rounded,
                                              Icons.call_rounded,
                                              Icons.settings_rounded,
                                            ][i],
                                            size: 9 * scale,
                                            color: i == 0 ? accent : muted,
                                          ),
                                          Container(
                                            width: 10 * scale,
                                            height: 1 * scale,
                                            color: i == 0 ? fg.withValues(alpha: 0.65) : muted.withValues(alpha: 0.45),
                                          ),
                                        ],
                                      )
                                    : isIos
                                        ? Center(
                                            child: Container(
                                              width: 16 * scale,
                                              height: 16 * scale,
                                              decoration: BoxDecoration(
                                                shape: BoxShape.circle,
                                                color: i == 0 ? accent.withValues(alpha: 0.18) : Colors.transparent,
                                              ),
                                              child: Icon(
                                                <IconData>[
                                                  Icons.chat_bubble_outline_rounded,
                                                  Icons.search_rounded,
                                                  Icons.add_box_outlined,
                                                  Icons.person_outline_rounded,
                                                ][i],
                                                size: 9 * scale,
                                                color: i == 0 ? accent : muted,
                                              ),
                                            ),
                                          )
                                        : isInstagram
                                            ? Center(
                                                child: Icon(
                                                  <IconData>[
                                                    Icons.home_rounded,
                                                    Icons.search_rounded,
                                                    Icons.add_box_outlined,
                                                    Icons.person_outline_rounded,
                                                  ][i],
                                                  size: 11 * scale,
                                                  color: i == 0 ? fg : muted,
                                                ),
                                              )
                                            : isTelegram
                                                ? Column(
                                                    mainAxisAlignment: MainAxisAlignment.center,
                                                    children: [
                                                      Icon(
                                                        <IconData>[
                                                          Icons.chat_bubble_rounded,
                                                          Icons.contacts_rounded,
                                                          Icons.call_rounded,
                                                          Icons.settings_rounded,
                                                        ][i],
                                                        size: 9 * scale,
                                                        color: i == 0 ? accent : muted,
                                                      ),
                                                      Container(
                                                        margin: EdgeInsets.only(top: 2 * scale),
                                                        width: 10 * scale,
                                                        height: 1 * scale,
                                                        color: i == 0 ? accent : muted.withValues(alpha: 0.4),
                                                      ),
                                                    ],
                                                  )
                                                : isBasic
                                                    ? Column(
                                                        mainAxisAlignment: MainAxisAlignment.center,
                                                        children: [
                                                          Icon(
                                                            <IconData>[
                                                              Icons.chat_bubble_rounded,
                                                              Icons.auto_stories_rounded,
                                                              Icons.call_rounded,
                                                              Icons.settings_rounded,
                                                            ][i],
                                                            size: 9 * scale,
                                                            color: i == 0 ? accent : muted,
                                                          ),
                                                          Container(
                                                            margin: EdgeInsets.only(top: 2 * scale),
                                                            width: 15 * scale,
                                                            height: 1.3 * scale,
                                                            color: i == 0 ? fg : muted.withValues(alpha: 0.4),
                                                          ),
                                                        ],
                                                      )
                                                    : Icon(
                                                        <IconData>[
                                                          Icons.chat_bubble_rounded,
                                                          Icons.auto_stories_rounded,
                                                          Icons.call_rounded,
                                                          Icons.settings_rounded,
                                                        ][i],
                                                        size: 10 * scale,
                                                        color: i == 0 ? accent : muted,
                                                      ),
                          ),
                        ),
                    ],
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  void _updateHome(HomePreferences newPrefs, {String? logTitle}) {
    widget.preferencesController.updateHome(newPrefs, logTitle: logTitle);
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge([
        widget.preferencesController,
        locator<ThemeController>(),
      ]),
      builder: (context, _) {
        final theme = locator<ThemeController>().globalTheme;
        final colors = context.colors;
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
          backgroundColor: colors.background,
          appBar: AppBar(
            backgroundColor: colors.background,
            elevation: 0,
            leading: IconButton(
              icon: Icon(Icons.arrow_back, color: colors.foreground),
              onPressed: () => Navigator.of(context).pop(),
            ),
            title: Text(
              'Header',
              style: TextStyle(
                color: colors.foreground,
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
                homeStyle: _selectedHomeUiStyle(home),
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
                      color: colors.surface,
                      borderRadius: BorderRadius.zero,
                      border: Border.all(
                        color: colors.borderSubtle,
                        width: 1.1,
                      ),
                    ),
                    clipBehavior: Clip.hardEdge,
                    child: Column(
                      children: [
                        // Home UI Style
                        GbSettingRow(
                          icon: Icon(
                            Icons.layers_outlined,
                            color: colors.primary,
                            size: 21,
                          ),
                          title: 'Home UI Style',
                          subtitle: _selectedHomeUiStyle(home),
                          showChevron: true,
                          onTap: () async {
                            final selected = _selectedHomeUiStyle(home);
                            final theme = locator<ThemeController>().globalTheme;
                            final chosen = await showModalBottomSheet<String>(
                              context: context,
                              isScrollControlled: true,
                              useSafeArea: true,
                              showDragHandle: false,
                              shape: const RoundedRectangleBorder(
                                borderRadius: BorderRadius.zero,
                              ),
                              clipBehavior: Clip.hardEdge,
                              backgroundColor: theme.surfaceColor,
                              builder: (sheetContext) {
                                var draft = selected;
                                return StatefulBuilder(
                                  builder: (context, setSheetState) {
                                    final colors = context.colors;
                                    return SizedBox(
                                      height: MediaQuery.sizeOf(context).height * 0.84,
                                      child: SafeArea(
                                        child: Column(
                                          crossAxisAlignment: CrossAxisAlignment.stretch,
                                          children: [
                                            Padding(
                                              padding: const EdgeInsets.fromLTRB(20, 2, 20, 0),
                                              child: Text(
                                                'Home UI Style',
                                                style: TextStyle(
                                                  color: colors.foreground,
                                                  fontSize: 20,
                                                  fontWeight: FontWeight.w800,
                                                ),
                                              ),
                                            ),
                                            Padding(
                                              padding: const EdgeInsets.fromLTRB(20, 4, 20, 10),
                                              child: Text(
                                                'Select a layout to see a live preview, then apply it.',
                                                style: TextStyle(
                                                  color: colors.foregroundSecondary,
                                                  fontSize: 12,
                                                ),
                                              ),
                                            ),
                                            Padding(
                                              padding: const EdgeInsets.symmetric(horizontal: 18),
                                              child: ListenableBuilder(
                                                listenable: locator<ThemeController>(),
                                                builder: (context, _) {
                                                  final liveTheme =
                                                      locator<ThemeController>().globalTheme;
                                                  return AnimatedSwitcher(
                                                    duration: const Duration(milliseconds: 180),
                                                    child: _buildHomeUiPreview(
                                                      draft,
                                                      liveTheme,
                                                      key: ValueKey<String>(draft),
                                                      large: true,
                                                    ),
                                                  );
                                                },
                                              ),
                                            ),
                                            const SizedBox(height: 8),
                                            Expanded(
                                              child: ListView.separated(
                                                padding: const EdgeInsets.fromLTRB(14, 2, 14, 10),
                                                itemCount: _homeUiStyles.length,
                                                separatorBuilder: (_, __) => Divider(
                                                  height: 1,
                                                  color: colors.divider,
                                                ),
                                                itemBuilder: (context, index) {
                                                  final option = _homeUiStyles[index];
                                                  final isSelected = draft == option;
                                                  return InkWell(
                                                    borderRadius: BorderRadius.zero,
                                                    onTap: () => setSheetState(() => draft = option),
                                                    child: Padding(
                                                      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 7),
                                                      child: Row(
                                                        children: [
                                                          SizedBox(
                                                            width: 100,
                                                            child: ListenableBuilder(
                                                              listenable: locator<ThemeController>(),
                                                              builder: (context, _) => _buildHomeUiPreview(
                                                                option,
                                                                locator<ThemeController>().globalTheme,
                                                                large: false,
                                                              ),
                                                            ),
                                                          ),
                                                          const SizedBox(width: 12),
                                                          Expanded(
                                                            child: Column(
                                                              crossAxisAlignment: CrossAxisAlignment.start,
                                                              children: [
                                                                Text(
                                                                  option,
                                                                  style: TextStyle(
                                                                    color: isSelected ? colors.primary : colors.foreground,
                                                                    fontSize: 13,
                                                                    fontWeight: isSelected ? FontWeight.w800 : FontWeight.w600,
                                                                  ),
                                                                ),
                                                                const SizedBox(height: 2),
                                                                Text(
                                                                  _homeUiStyleDescription(option),
                                                                  maxLines: 2,
                                                                  overflow: TextOverflow.ellipsis,
                                                                  style: TextStyle(
                                                                    color: colors.foregroundSecondary,
                                                                    fontSize: 10.5,
                                                                    height: 1.2,
                                                                  ),
                                                                ),
                                                              ],
                                                            ),
                                                          ),
                                                          const SizedBox(width: 8),
                                                          Icon(
                                                            isSelected ? Icons.radio_button_checked_rounded : Icons.radio_button_unchecked_rounded,
                                                            color: isSelected ? colors.primary : colors.foregroundTertiary,
                                                            size: 20,
                                                          ),
                                                        ],
                                                      ),
                                                    ),
                                                  );
                                                },
                                              ),
                                            ),
                                            Padding(
                                              padding: const EdgeInsets.fromLTRB(18, 6, 18, 14),
                                              child: Row(
                                                children: [
                                                  Expanded(
                                                    child: OutlinedButton(
                                                      onPressed: () => Navigator.of(sheetContext).pop(),
                                                      child: const Text('Cancel'),
                                                    ),
                                                  ),
                                                  const SizedBox(width: 12),
                                                  Expanded(
                                                    child: FilledButton(
                                                      onPressed: () => Navigator.of(sheetContext).pop(draft),
                                                      child: const Text('Apply style'),
                                                    ),
                                                  ),
                                                ],
                                              ),
                                            ),
                                          ],
                                        ),
                                      ),
                                    );
                                  },
                                );
                              },
                            );
                            if (chosen != null && mounted) {
                              locator<ThemeController>().setNavigationMode(
                                _navigationModeForHomeUiStyle(chosen),
                              );
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
                          icon: Icon(
                            Icons.chat_bubble_outline_rounded,
                            color: colors.primary,
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
                          icon: Icon(
                            Icons.view_in_ar_rounded,
                            color: colors.primary,
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
                          icon: Icon(
                            Icons.donut_large_rounded,
                            color: colors.primary,
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
                          icon: Icon(
                            Icons.bubble_chart_outlined,
                            color: colors.primary,
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
                          icon: Icon(
                            Icons.bubble_chart_rounded,
                            color: colors.primary,
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
                          icon: Icon(
                            Icons.alt_route_rounded,
                            color: colors.primary,
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
                          icon: Icon(
                            Icons.badge_outlined,
                            color: colors.primary,
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
                          icon: Icon(
                            Icons.event_busy_outlined,
                            color: colors.primary,
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
                          icon: Icon(
                            Icons.inbox_outlined,
                            color: colors.primary,
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
                          icon: Icon(
                            Icons.saved_search_outlined,
                            color: colors.primary,
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
                          icon: Icon(
                            Icons.palette_outlined,
                            color: colors.primary,
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
                          icon: Icon(
                            Icons.person_add_alt_1_outlined,
                            color: colors.primary,
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
      color: colors.divider,
    );
  }
}
