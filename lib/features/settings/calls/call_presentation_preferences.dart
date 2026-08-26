import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

@immutable
class CallPresentationPreferences {
  const CallPresentationPreferences({
    this.dynamicIslandEnabled = true,
    this.pictureInPictureEnabled = true,
    this.lowDataUsageEnabled = false,
  });

  final bool dynamicIslandEnabled;
  final bool pictureInPictureEnabled;
  final bool lowDataUsageEnabled;

  CallPresentationPreferences copyWith({
    bool? dynamicIslandEnabled,
    bool? pictureInPictureEnabled,
    bool? lowDataUsageEnabled,
  }) {
    return CallPresentationPreferences(
      dynamicIslandEnabled:
          dynamicIslandEnabled ?? this.dynamicIslandEnabled,
      pictureInPictureEnabled:
          pictureInPictureEnabled ?? this.pictureInPictureEnabled,
      lowDataUsageEnabled: lowDataUsageEnabled ?? this.lowDataUsageEnabled,
    );
  }
}

/// Device-local call presentation preferences.
///
/// These settings intentionally live outside the account privacy model: they
/// describe capabilities and presentation choices for this physical device.
class CallPresentationPreferencesStore extends ChangeNotifier {
  CallPresentationPreferencesStore({this._preferences});

  /// Shared runtime store consumed by settings and call-presentation policy.
  static final CallPresentationPreferencesStore instance =
      CallPresentationPreferencesStore();

  static const String _dynamicIslandKey =
      'chaty.calls.dynamic_island_enabled.v1';
  static const String _pictureInPictureKey =
      'chaty.calls.picture_in_picture_enabled.v1';
  static const String _lowDataUsageKey =
      'chaty.calls.low_data_usage_enabled.v1';

  SharedPreferences? _preferences;
  CallPresentationPreferences _value = const CallPresentationPreferences();
  Future<void>? _initialization;

  CallPresentationPreferences get value => _value;
  bool get isInitialized => _preferences != null;

  Future<void> initialize() {
    return _initialization ??= _load();
  }

  Future<void> _load() async {
    final preferences = _preferences ??= await SharedPreferences.getInstance();
    _value = CallPresentationPreferences(
      dynamicIslandEnabled: preferences.getBool(_dynamicIslandKey) ?? true,
      pictureInPictureEnabled:
          preferences.getBool(_pictureInPictureKey) ?? true,
      lowDataUsageEnabled: preferences.getBool(_lowDataUsageKey) ?? false,
    );
    notifyListeners();
  }

  Future<void> setDynamicIslandEnabled(bool enabled) async {
    await initialize();
    if (_value.dynamicIslandEnabled == enabled) return;
    _value = _value.copyWith(dynamicIslandEnabled: enabled);
    notifyListeners();
    await _preferences!.setBool(_dynamicIslandKey, enabled);
  }

  Future<void> setPictureInPictureEnabled(bool enabled) async {
    await initialize();
    if (_value.pictureInPictureEnabled == enabled) return;
    _value = _value.copyWith(pictureInPictureEnabled: enabled);
    notifyListeners();
    await _preferences!.setBool(_pictureInPictureKey, enabled);
  }

  Future<void> setLowDataUsageEnabled(bool enabled) async {
    await initialize();
    if (_value.lowDataUsageEnabled == enabled) return;
    _value = _value.copyWith(lowDataUsageEnabled: enabled);
    notifyListeners();
    await _preferences!.setBool(_lowDataUsageKey, enabled);
  }
}
