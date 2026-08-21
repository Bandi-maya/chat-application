import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

class AppearanceVariantController extends ChangeNotifier {
  static const List<String> navigationStyles = <String>[
    'Adaptive Rail',
    'Compact Rail',
    'Minimal Rail',
    'Pill Rail',
    'Outlined Rail',
    'Floating Rail',
    'Dense Rail',
    'Icon Rail',
    'Label Rail',
    'Soft Rail',
    'Classic Tabs',
    'Segmented Tabs',
    'Underline Tabs',
    'Pill Tabs',
    'Icon Tabs',
    'Compact Tabs',
    'Floating Tabs',
    'Sidebar Tabs',
    'Workspace Tabs',
    'Focus Tabs',
  ];

  static const List<String> bottomBarStyles = <String>[
    'Floating Pill', // Floating rounded container with subtle border & shadow
    'Active Pill Chip', // Expanding pill container behind active tab (Image 2 & 1)
    'Top Indicator Line', // Crisp accent bar positioned at the top of active tab (Image 1 top right, Image 3, Image 5)
    'Bottom Indicator Dot', // Clean dot/underline centered below active icon (Image 1 top left, Image 5)
    'Circle Accent Pop', // Solid vibrant circular container popping active icon (Image 1 bottom row, Image 4 middle)
    'Curved Notch Teardrop', // Smooth teardrop inverted notch above active icon (Image 3 middle left, Image 5)
    'Floating Dynamic Island', // Black/dark capsule dock with segmented pills (Image 2 dark portfolio style)
    'Raised Center Action', // Center action / middle tab prominently elevated & scaled (Image 4 center orange/purple)
    'Segmented Glass Dock', // Glassmorphic segmented container with border glow
    'Minimal Icon Dock', // Label-less pure icon bar with subtle scale transition (Image 1 top left)
    'Classic Label Bar', // Standard native iOS/Android style with icons and persistent labels
    'Soft Square Tile', // Subtle rounded square background on active tab (Image 3 middle row)
  ];

  static const List<String> appIconStyles = <String>[
    'Chaty Original',
    'Outline Chat',
    'Solid Chat',
    'Soft Square',
    'Circle Chat',
    'Monochrome',
    'High Contrast',
    'Minimal Mark',
    'Rounded Mark',
    'Sharp Mark',
    'Message Stack',
    'Double Bubble',
    'Wave Bubble',
    'Bolt Chat',
    'Orbit Chat',
    'Pixel Chat',
    'Business Chat',
    'Creator Chat',
    'Privacy Chat',
    'Focus Chat',
  ];

  static const List<String> notificationIconStyles = <String>[
    'Chat Bubble',
    'Outline Bubble',
    'Double Tick',
    'Bell Dot',
    'Message Dot',
    'Minimal Dot',
    'Priority Ring',
    'Contact Circle',
    'Group Stack',
    'Task Check',
    'Call Ring',
    'Video Ring',
    'Status Ring',
    'Muted Bell',
    'Secure Lock',
    'Shield Chat',
    'Compact Badge',
    'Workspace Badge',
    'Monochrome Badge',
    'Focus Badge',
  ];

  static const List<String> typographyStyles = <String>[
    'System Default',
    'Compact',
    'Comfortable',
    'Editorial',
    'Product Sans',
    'Geometric',
    'Humanist',
    'Rounded',
    'Technical',
    'Monospace Accent',
    'Dense UI',
    'Large UI',
    'Accessibility',
    'Minimal',
    'Business',
    'Creator',
    'Classic',
    'Modern',
    'Soft',
    'Focus',
  ];

  static const List<String> entryAnimations = <String>[
    'Fade + Slide',
    'Fade',
    'Slide Right',
    'Slide Left',
    'Slide Up',
    'Slide Down',
    'Scale In',
    'Soft Zoom',
    'Shared Axis X',
    'Shared Axis Y',
    'Fade Through',
    'Cupertino Push',
    'Spring Push',
    'Soft Reveal',
    'Card Lift',
    'Blur-free Reveal',
    'Quick Snap',
    'Gentle Drift',
    'Focus In',
    'None',
  ];

  static const List<String> exitAnimations = <String>[
    'Fade',
    'Fade + Slide',
    'Slide Right',
    'Slide Left',
    'Slide Up',
    'Slide Down',
    'Scale Out',
    'Soft Zoom',
    'Shared Axis X',
    'Shared Axis Y',
    'Fade Through',
    'Cupertino Pop',
    'Spring Pop',
    'Soft Conceal',
    'Card Drop',
    'Quick Snap',
    'Gentle Drift',
    'Focus Out',
    'Cross Fade',
    'None',
  ];

  String _navigationStyle = navigationStyles.first;
  String _bottomBarStyle = bottomBarStyles.first;
  String _appIconStyle = appIconStyles.first;
  String _notificationIconStyle = notificationIconStyles.first;
  String _typographyStyle = typographyStyles.first;
  String _entryAnimation = entryAnimations.first;
  String _exitAnimation = exitAnimations.first;
  bool _loaded = false;

  AppearanceVariantController() {
    _load();
  }

  String get navigationStyle => _navigationStyle;
  String get bottomBarStyle => _bottomBarStyle;
  String get appIconStyle => _appIconStyle;
  String get notificationIconStyle => _notificationIconStyle;
  String get typographyStyle => _typographyStyle;
  String get entryAnimation => _entryAnimation;
  String get exitAnimation => _exitAnimation;
  bool get loaded => _loaded;

  int get navigationIndex => navigationStyles
      .indexOf(_navigationStyle)
      .clamp(0, navigationStyles.length - 1);
  int get bottomBarIndex => bottomBarStyles
      .indexOf(_bottomBarStyle)
      .clamp(0, bottomBarStyles.length - 1);
  int get typographyIndex => typographyStyles
      .indexOf(_typographyStyle)
      .clamp(0, typographyStyles.length - 1);

  double get textScale {
    const scales = <double>[
      1.0,
      0.94,
      1.04,
      1.02,
      1.0,
      1.0,
      1.02,
      1.03,
      0.96,
      0.98,
      0.90,
      1.10,
      1.18,
      0.96,
      0.98,
      1.02,
      1.0,
      1.0,
      1.04,
      0.96,
    ];
    return scales[typographyIndex];
  }

  Future<void> _load() async {
    final prefs = await SharedPreferences.getInstance();
    _navigationStyle = _validated(
      prefs.getString('appearance.navigation'),
      navigationStyles,
    );
    _bottomBarStyle = _validated(
      prefs.getString('appearance.bottomBar'),
      bottomBarStyles,
    );
    _appIconStyle = _validated(
      prefs.getString('appearance.appIcon'),
      appIconStyles,
    );
    _notificationIconStyle = _validated(
      prefs.getString('appearance.notificationIcon'),
      notificationIconStyles,
    );
    _typographyStyle = _validated(
      prefs.getString('appearance.typography'),
      typographyStyles,
    );
    _entryAnimation = _validated(
      prefs.getString('appearance.entryAnimation'),
      entryAnimations,
    );
    _exitAnimation = _validated(
      prefs.getString('appearance.exitAnimation'),
      exitAnimations,
    );
    _loaded = true;
    notifyListeners();
  }

  String _validated(String? value, List<String> options) {
    return value != null && options.contains(value) ? value : options.first;
  }

  Future<void> setNavigationStyle(String value) => _set(
    value: value,
    options: navigationStyles,
    key: 'appearance.navigation',
    apply: (next) => _navigationStyle = next,
  );

  Future<void> setBottomBarStyle(String value) => _set(
    value: value,
    options: bottomBarStyles,
    key: 'appearance.bottomBar',
    apply: (next) => _bottomBarStyle = next,
  );

  Future<void> setAppIconStyle(String value) => _set(
    value: value,
    options: appIconStyles,
    key: 'appearance.appIcon',
    apply: (next) => _appIconStyle = next,
  );

  Future<void> setNotificationIconStyle(String value) => _set(
    value: value,
    options: notificationIconStyles,
    key: 'appearance.notificationIcon',
    apply: (next) => _notificationIconStyle = next,
  );

  Future<void> setTypographyStyle(String value) => _set(
    value: value,
    options: typographyStyles,
    key: 'appearance.typography',
    apply: (next) => _typographyStyle = next,
  );

  Future<void> setEntryAnimation(String value) => _set(
    value: value,
    options: entryAnimations,
    key: 'appearance.entryAnimation',
    apply: (next) => _entryAnimation = next,
  );

  Future<void> setExitAnimation(String value) => _set(
    value: value,
    options: exitAnimations,
    key: 'appearance.exitAnimation',
    apply: (next) => _exitAnimation = next,
  );

  Future<void> _set({
    required String value,
    required List<String> options,
    required String key,
    required ValueChanged<String> apply,
  }) async {
    if (!options.contains(value)) return;
    apply(value);
    notifyListeners();
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(key, value);
  }
}
