import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

@immutable
class CallPresentationPreferences {
  const CallPresentationPreferences({
    this.dynamicIslandEnabled = true,
    this.pictureInPictureEnabled = true,
    this.lowDataUsageEnabled = false,
    this.incomingCallTemplate = 'chatyClassic',
    this.ongoingCallTemplate = 'chatyClassic',
  });

  final bool dynamicIslandEnabled;
  final bool pictureInPictureEnabled;
  final bool lowDataUsageEnabled;

  /// Persisted [CallScreenTemplate.name] for the incoming call screen.
  final String incomingCallTemplate;

  /// Persisted [CallScreenTemplate.name] for the ongoing call screen.
  final String ongoingCallTemplate;

  CallPresentationPreferences copyWith({
    bool? dynamicIslandEnabled,
    bool? pictureInPictureEnabled,
    bool? lowDataUsageEnabled,
    String? incomingCallTemplate,
    String? ongoingCallTemplate,
  }) {
    return CallPresentationPreferences(
      dynamicIslandEnabled:
          dynamicIslandEnabled ?? this.dynamicIslandEnabled,
      pictureInPictureEnabled:
          pictureInPictureEnabled ?? this.pictureInPictureEnabled,
      lowDataUsageEnabled: lowDataUsageEnabled ?? this.lowDataUsageEnabled,
      incomingCallTemplate:
          incomingCallTemplate ?? this.incomingCallTemplate,
      ongoingCallTemplate: ongoingCallTemplate ?? this.ongoingCallTemplate,
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
  static const String _incomingTemplateKey =
      'chaty.calls.incoming_template.v1';
  static const String _ongoingTemplateKey = 'chaty.calls.ongoing_template.v1';

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
      incomingCallTemplate:
          preferences.getString(_incomingTemplateKey) ?? 'chatyClassic',
      ongoingCallTemplate:
          preferences.getString(_ongoingTemplateKey) ?? 'chatyClassic',
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

  Future<void> setIncomingCallTemplate(String template) {
    return _setString(
      key: _incomingTemplateKey,
      template: template,
      read: (value) => value.incomingCallTemplate,
      update: (value, next) => value.copyWith(incomingCallTemplate: next),
    );
  }

  Future<void> setOutgoingCallTemplate(String template) {
    return _setString(
      key: _ongoingTemplateKey,
      template: template,
      read: (value) => value.ongoingCallTemplate,
      update: (value, next) => value.copyWith(ongoingCallTemplate: next),
    );
  }

  Future<void> _setString({
    required String key,
    required String template,
    required String Function(CallPresentationPreferences value) read,
    required CallPresentationPreferences Function(
      CallPresentationPreferences value,
      String next,
    ) update,
  }) async {
    await initialize();
    final operation = _writeQueue.then((_) async {
      if (read(_value) == template) return;
      final saved = await _preferences!.setString(key, template);
      if (!saved) {
        throw StateError('Unable to save this call screen template.');
      }
      _value = update(_value, template);
      notifyListeners();
    });
    _writeQueue = operation.then<void>(
      (_) {},
      onError: (Object error, StackTrace stackTrace) {},
    );
    return operation;
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
