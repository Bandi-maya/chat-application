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
  Future<void> _writeQueue = Future<void>.value();

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

  Future<void> setDynamicIslandEnabled(bool enabled) {
    return _setBool(
      key: _dynamicIslandKey,
      enabled: enabled,
      read: (value) => value.dynamicIslandEnabled,
      update: (value, next) => value.copyWith(dynamicIslandEnabled: next),
    );
  }

  Future<void> setPictureInPictureEnabled(bool enabled) {
    return _setBool(
      key: _pictureInPictureKey,
      enabled: enabled,
      read: (value) => value.pictureInPictureEnabled,
      update: (value, next) => value.copyWith(pictureInPictureEnabled: next),
    );
  }

  Future<void> setLowDataUsageEnabled(bool enabled) {
    return _setBool(
      key: _lowDataUsageKey,
      enabled: enabled,
      read: (value) => value.lowDataUsageEnabled,
      update: (value, next) => value.copyWith(lowDataUsageEnabled: next),
    );
  }

  Future<void> _setBool({
    required String key,
    required bool enabled,
    required bool Function(CallPresentationPreferences value) read,
    required CallPresentationPreferences Function(
      CallPresentationPreferences value,
      bool next,
    ) update,
  }) async {
    await initialize();
    final operation = _writeQueue.then((_) async {
      if (read(_value) == enabled) return;
      final saved = await _preferences!.setBool(key, enabled);
      if (!saved) {
        throw StateError('Unable to save this call presentation preference.');
      }
      // Publish only after storage succeeds. Build from the latest state so
      // simultaneous changes to different preferences cannot clobber each other.
      _value = update(_value, enabled);
      notifyListeners();
    });
    // Keep the queue usable after a failed write while returning the original
    // failure to the caller so the UI can report it.
    _writeQueue = operation.then<void>(
      (_) {},
      onError: (Object error, StackTrace stackTrace) {},
    );
    return operation;
  }
}
