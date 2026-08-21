import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'preference_keys.dart';

/// Local persistence for Chaty preferences.
///
/// Preference blobs are namespaced per authenticated user so that two accounts
/// on the same device can never read or overwrite each other's settings (see
/// [PreferenceKeys.scopedPreferences]). The bare, un-namespaced key
/// ([PreferenceKeys.preferencesBase]) is retained only for (a) the signed-out
/// device-global bucket and (b) reading a pre-namespacing legacy blob so it can
/// be adopted once into its rightful owner's namespace.
///
/// Device-global display state (theme) is stored separately under
/// [PreferenceKeys.themeState] because it is not account-sensitive and must be
/// available before authentication is known (to avoid a theme flash on launch).
///
/// NOTE: errors are logged with generic messages only — never the underlying
/// exception or payload — because preference content and any transported values
/// are considered sensitive and must not leak into logs.
class LocalPreferencesStorage {
  const LocalPreferencesStorage._();

  /// Loads a serialized preference blob. When [userId] is provided the
  /// per-user namespaced key is used; otherwise the device-global/legacy key is
  /// read. Returns an empty map on absence or any error.
  static Future<Map<String, dynamic>> loadPreferences({String? userId}) async {
    return _loadJsonMap(_preferencesKeyFor(userId), 'preferences');
  }

  /// Persists a serialized preference blob. When [userId] is provided the
  /// per-user namespaced key is used; otherwise the device-global/legacy key is
  /// written.
  static Future<bool> savePreferences(
    Map<String, dynamic> data, {
    String? userId,
  }) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      return await prefs.setString(
        _preferencesKeyFor(userId),
        jsonEncode(data),
      );
    } catch (_) {
      debugPrint('LocalPreferencesStorage: failed to save preferences');
      return false;
    }
  }

  /// Whether a per-user blob already exists for [userId]. Used to decide
  /// whether a legacy global blob may be adopted into this namespace.
  static Future<bool> hasScopedPreferences(String userId) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final content = prefs.getString(PreferenceKeys.scopedPreferences(userId));
      return content != null && content.isNotEmpty;
    } catch (_) {
      return false;
    }
  }

  /// Removes the legacy/global (un-namespaced) preference blob. Called exactly
  /// once after its contents have been adopted into a user's namespace, so a
  /// second account on the device can never inherit the first account's data.
  static Future<void> clearGlobalPreferences() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove(PreferenceKeys.preferencesBase);
    } catch (_) {
      debugPrint('LocalPreferencesStorage: failed to clear global preferences');
    }
  }

  // ---------------------------------------------------------------------------
  // Device-global theme/display state (not account-sensitive).
  // ---------------------------------------------------------------------------

  static Future<Map<String, dynamic>> loadThemeState() async {
    return _loadJsonMap(PreferenceKeys.themeState, 'theme state');
  }

  static Future<bool> saveThemeState(Map<String, dynamic> data) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      return await prefs.setString(PreferenceKeys.themeState, jsonEncode(data));
    } catch (_) {
      debugPrint('LocalPreferencesStorage: failed to save theme state');
      return false;
    }
  }

  // ---------------------------------------------------------------------------
  // Stored authenticated-user id (used to safely adopt a legacy global blob).
  // ---------------------------------------------------------------------------

  static Future<String?> getStoredUserId() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      return prefs.getString(PreferenceKeys.storedUserId);
    } catch (_) {
      debugPrint('LocalPreferencesStorage: failed to read stored user id');
      return null;
    }
  }

  static Future<void> setStoredUserId(String userId) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(PreferenceKeys.storedUserId, userId);
    } catch (_) {
      debugPrint('LocalPreferencesStorage: failed to persist stored user id');
    }
  }

  static Future<void> saveUserId(String userId) => setStoredUserId(userId);

  static Future<void> clearStoredUserId() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove(PreferenceKeys.storedUserId);
    } catch (_) {
      debugPrint('LocalPreferencesStorage: failed to clear stored user id');
    }
  }

  // ---------------------------------------------------------------------------
  // Internals.
  // ---------------------------------------------------------------------------

  static String _preferencesKeyFor(String? userId) {
    if (userId != null && userId.isNotEmpty) {
      return PreferenceKeys.scopedPreferences(userId);
    }
    return PreferenceKeys.preferencesBase;
  }

  static Future<Map<String, dynamic>> _loadJsonMap(
    String key,
    String label,
  ) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final content = prefs.getString(key);
      if (content != null && content.isNotEmpty) {
        final data = jsonDecode(content);
        if (data is Map<String, dynamic>) return data;
        if (data is Map) return Map<String, dynamic>.from(data);
      }
    } catch (_) {
      // Corrupt JSON: swallow and return empty so the caller falls back to
      // defaults instead of crashing. Never log the payload.
      debugPrint(
        'LocalPreferencesStorage: failed to load $label (using defaults)',
      );
    }
    return <String, dynamic>{};
  }
}
