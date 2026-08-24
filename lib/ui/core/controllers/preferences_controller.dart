import 'dart:async';

import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../../../data/services/local_lock_service.dart';
import '../../../domain/models/preferences.dart';
import '../gb/gb_feature_catalog.dart';
import '../persistence/preference_keys.dart';
import '../persistence/preferences_migrator.dart';
import '../persistence/preferences_storage.dart';

class PreferenceHistoryEntry {
  final String key;
  final String title;
  final dynamic previousValue;
  final dynamic newValue;
  final DateTime timestamp;

  const PreferenceHistoryEntry({
    required this.key,
    required this.title,
    required this.previousValue,
    required this.newValue,
    required this.timestamp,
  });
}

class ChatyPreferencesController extends ChangeNotifier {
  PrivacyPreferences _privacy = const PrivacyPreferences();
  SecurityPreferences _security = const SecurityPreferences();
  HomePreferences _home = const HomePreferences();
  ConversationPreferences _conversation = const ConversationPreferences();
  NotificationPreferences _notification = const NotificationPreferences();
  MessageAutomationPreferences _automation =
      const MessageAutomationPreferences();
  NavigationEffectPreferences _effects = const NavigationEffectPreferences();
  Map<String, Object?> _gbFeatures = GbFeatureCatalog.defaults;

  final Set<String> _starredFavorites = <String>{};
  final List<PreferenceHistoryEntry> _history = <PreferenceHistoryEntry>[];
  final LocalLockService _lockService;
  String? _scopeUserId;
  Timer? _remoteSyncDebounce;
  StreamSubscription<AuthState>? _authSubscription;
  bool _disposed = false;
  bool _preferencesReady = false;

  ChatyPreferencesController({LocalLockService? lockService})
    : _lockService = lockService ?? LocalLockService() {
    _init();
    final client = _clientOrNull();
    if (client != null) {
      _authSubscription = client.auth.onAuthStateChange.listen(
        _handleAuthStateChange,
      );
    }
  }

  SupabaseClient? _clientOrNull() {
    try {
      return Supabase.instance.client;
    } catch (_) {
      return null;
    }
  }

  PrivacyPreferences get privacy => _privacy;
  bool get preferencesReady => _preferencesReady;
  SecurityPreferences get security => _security;
  HomePreferences get home => _home;
  ConversationPreferences get conversation => _conversation;
  NotificationPreferences get notification => _notification;
  MessageAutomationPreferences get automation => _automation;
  NavigationEffectPreferences get effects => _effects;
  Map<String, Object?> get gbFeatures =>
      Map<String, Object?>.unmodifiable(_gbFeatures);
  Set<String> get starredFavorites =>
      Set<String>.unmodifiable(_starredFavorites);
  List<PreferenceHistoryEntry> get history =>
      List<PreferenceHistoryEntry>.unmodifiable(_history);

  T? gbValue<T>(String key) {
    final value = _gbFeatures[key];
    return value is T ? value : null;
  }

  bool gbBool(String key, {bool fallback = false}) {
    final value = _gbFeatures[key];
    if (value is bool) return value;
    if (value is String) return value.toLowerCase() == 'true';
    if (value is num) return value != 0;
    return fallback;
  }

  String gbString(String key, {String fallback = ''}) =>
      _gbFeatures[key]?.toString() ?? fallback;

  double gbDouble(String key, {double fallback = 0}) {
    final value = _gbFeatures[key];
    if (value is num) return value.toDouble();
    return double.tryParse(value?.toString() ?? '') ?? fallback;
  }

  int gbInt(String key, {int fallback = 0}) {
    final value = _gbFeatures[key];
    if (value is int) return value;
    if (value is num) return value.round();
    return int.tryParse(value?.toString() ?? '') ?? fallback;
  }

  Color? gbColor(String key) {
    final raw = _gbFeatures[key];
    if (raw is int && raw != 0) return Color(raw);
    if (raw is String) {
      final normalized = raw.replaceFirst('#', '').replaceFirst('0x', '');
      final value = int.tryParse(normalized, radix: 16);
      if (value != null)
        return Color(normalized.length <= 6 ? 0xFF000000 | value : value);
    }
    return null;
  }

  Future<void> _init() async {
    _scopeUserId = _clientOrNull()?.auth.currentUser?.id;
    await _loadForUser(_scopeUserId);
  }

  /// Reacts to sign-in, sign-out, and account switches. On any change of the
  /// authenticated user the previous account's in-memory state is dropped
  /// before the new scope is loaded, so preferences can never bleed across
  /// accounts on a shared device.
  void _handleAuthStateChange(AuthState state) {
    final newUserId = state.session?.user.id;
    if (newUserId == _scopeUserId) {
      // Same session (e.g. a token refresh) — just reconcile with the backend.
      if (newUserId != null) unawaited(_syncFromRemote());
      return;
    }
    _scopeUserId = newUserId;
    _preferencesReady = false;
    _resetInMemory();
    if (!_disposed) notifyListeners();
    unawaited(_loadForUser(newUserId));
  }

  /// Loads, migrates, and applies the preference blob for [userId] (null when
  /// signed out), then reconciles with the backend.
  Future<void> _loadForUser(String? userId) async {
    final raw = await _resolveLocalBlob(userId);

    final migration = PreferencesMigrator.migrate(raw);
    if (raw.isNotEmpty) _applyMap(migration.data);
    if (migration.migratedSecrets) await _migrateLegacySecrets(migration);

    // Rewrite the cleaned/upgraded blob so stripped plaintext secrets and stale
    // schema versions never survive to a second load.
    if (raw.isNotEmpty &&
        (migration.migratedSecrets ||
            migration.fromVersion < PreferenceKeys.currentSchemaVersion)) {
      await LocalPreferencesStorage.savePreferences(
        _snapshot(),
        userId: userId,
      );
    }

    if (_scopeUserId == userId) _preferencesReady = true;
    if (!_disposed) notifyListeners();
    await _syncFromRemote();
  }

  /// Resolves the correct local blob for [userId], performing a one-time
  /// adoption of a pre-namespacing legacy global blob into the user's namespace
  /// when it is safe to do so.
  Future<Map<String, dynamic>> _resolveLocalBlob(String? userId) async {
    if (userId == null || userId.isEmpty) {
      // Signed-out / device-global bucket.
      return LocalPreferencesStorage.loadPreferences();
    }

    final hasScoped = await LocalPreferencesStorage.hasScopedPreferences(
      userId,
    );
    if (!hasScoped) {
      final legacy = await LocalPreferencesStorage.loadPreferences();
      if (legacy.isNotEmpty) {
        // Only adopt the un-owned/legacy blob if it is not already claimed by a
        // different account; otherwise a second account could inherit the
        // first account's settings.
        final owner = await LocalPreferencesStorage.getStoredUserId();
        if (owner == null || owner == userId) {
          await LocalPreferencesStorage.savePreferences(legacy, userId: userId);
        }
        // Retire the un-namespaced blob regardless, so it can never be adopted
        // again by a different account.
        await LocalPreferencesStorage.clearGlobalPreferences();
      }
    }

    await LocalPreferencesStorage.setStoredUserId(userId);
    return LocalPreferencesStorage.loadPreferences(userId: userId);
  }

  /// Moves any plaintext credentials found in a pre-v2 blob into platform
  /// secure storage. Fail-safe: an existing secure credential is never
  /// overwritten, and a legacy secret that no longer passes validation is
  /// dropped (the user simply re-sets their lock).
  Future<void> _migrateLegacySecrets(MigrationResult migration) async {
    const methodByLabel = <String, String>{
      'PIN': 'pin',
      'Pattern': 'pattern',
      'Password': 'password',
    };
    for (final entry in migration.legacySecrets.entries) {
      final method = methodByLabel[entry.key];
      if (method == null) continue;
      try {
        if (await _lockService.hasCredential(method)) continue;
        await _lockService.setCredential(
          method,
          entry.value,
          pinLength: method == 'pin' ? migration.legacyPinLength : null,
        );
      } catch (_) {
        // Invalid legacy secret; plaintext is already stripped, so this is safe.
      }
    }
  }

  void _resetInMemory() {
    _privacy = const PrivacyPreferences();
    _security = const SecurityPreferences();
    _home = const HomePreferences();
    _conversation = const ConversationPreferences();
    _notification = const NotificationPreferences();
    _automation = const MessageAutomationPreferences();
    _effects = const NavigationEffectPreferences();
    _gbFeatures = GbFeatureCatalog.defaults;
    _starredFavorites.clear();
    _history.clear();
  }

  void _applyMap(Map<String, dynamic> data) {
    void apply(String key, void Function(Map<String, dynamic>) applyGroup) {
      final value = data[key];
      if (value is! Map) return;
      try {
        applyGroup(Map<String, dynamic>.from(value));
      } catch (_) {
        // A single corrupt group must not abort the whole load; leave it at its
        // current (default) value and continue with the rest.
      }
    }

    apply(
      PreferenceKeys.privacy,
      (m) => _privacy = PrivacyPreferences.fromMap(m),
    );
    apply(
      PreferenceKeys.security,
      (m) => _security = SecurityPreferences.fromMap(m),
    );
    apply(PreferenceKeys.home, (m) => _home = HomePreferences.fromMap(m));
    apply(
      PreferenceKeys.conversation,
      (m) => _conversation = ConversationPreferences.fromMap(m),
    );
    apply(
      PreferenceKeys.notification,
      (m) => _notification = NotificationPreferences.fromMap(m),
    );
    apply(
      PreferenceKeys.automation,
      (m) => _automation = MessageAutomationPreferences.fromMap(m),
    );
    apply(
      PreferenceKeys.effects,
      (m) => _effects = NavigationEffectPreferences.fromMap(m),
    );

    final gb = data[PreferenceKeys.gbFeatures];
    if (gb is Map) {
      _gbFeatures = <String, Object?>{
        ...GbFeatureCatalog.defaults,
        ...Map<String, Object?>.from(gb),
      };
    }
    // One-time heal: the header/list visibility keys (PicProf, NameProf, …)
    // were exposed as Feature-Center toggles BEFORE they had consumers, so
    // devices can carry stored `false` values that hide the contact name /
    // avatar / call buttons / presence line even though the intended default
    // is visible. Clear them once so they return to unset=visible. The
    // marker persists inside this user's blob, so deliberate later choices
    // are never touched again.
    const visibilityHealMarker = 'chaty_visibility_heal_v1';
    if (_gbFeatures[visibilityHealMarker] != true) {
      const visibilityKeys = <String>[
        'PicProf',
        'NameProf',
        'Conv_call_btn',
        'statuschat',
        'onlinechat',
        'onlineDotchat',
      ];
      var healedAny = false;
      for (final key in visibilityKeys) {
        if (_gbFeatures.containsKey(key)) {
          _gbFeatures.remove(key);
          healedAny = true;
        }
      }
      _gbFeatures[visibilityHealMarker] = true;
      if (healedAny) _persist();
    }
    final favorites = data[PreferenceKeys.favorites];
    if (favorites is List) {
      _starredFavorites
        ..clear()
        ..addAll(favorites.map((e) => e.toString()));
    }
  }

  int _lastLocalMutation = DateTime.now().millisecondsSinceEpoch;

  Map<String, dynamic> _snapshot() => <String, dynamic>{
    PreferenceKeys.schemaVersion: PreferenceKeys.currentSchemaVersion,
    'local_updated_at': _lastLocalMutation,
    PreferenceKeys.privacy: _privacy.toMap(),
    PreferenceKeys.security: _security.toMap(),
    PreferenceKeys.home: _home.toMap(),
    PreferenceKeys.conversation: _conversation.toMap(),
    PreferenceKeys.notification: _notification.toMap(),
    PreferenceKeys.automation: _automation.toMap(),
    PreferenceKeys.effects: _effects.toMap(),
    PreferenceKeys.gbFeatures: _gbFeatures,
    PreferenceKeys.favorites: _starredFavorites.toList(growable: false),
  };

  void _persist() {
    _lastLocalMutation = DateTime.now().millisecondsSinceEpoch;
    final snapshot = _snapshot();
    unawaited(
      LocalPreferencesStorage.savePreferences(snapshot, userId: _scopeUserId),
    );
    _remoteSyncDebounce?.cancel();
    if (_clientOrNull() != null) {
      _remoteSyncDebounce = Timer(
        const Duration(milliseconds: 650),
        () => unawaited(_pushRemote(snapshot)),
      );
    }
  }

  Future<void> _syncFromRemote() async {
    final client = _clientOrNull();
    if (client == null) return;
    final user = client.auth.currentUser;
    if (user == null) return;
    try {
      final row = await client
          .from('user_feature_settings')
          .select('settings, updated_at')
          .eq('user_id', user.id)
          .maybeSingle();
      if (row == null || row['settings'] is! Map) {
        await _pushRemote(_snapshot());
        return;
      }
      final remoteRaw = Map<String, dynamic>.from(row['settings'] as Map);
      
      // Timestamp reconciliation: if local mutation is strictly newer than remote updated_at,
      // push local state to remote rather than overwriting fresh local choices with stale remote data.
      final remoteUpdatedAtStr = row['updated_at']?.toString() ?? remoteRaw['local_updated_at']?.toString();
      if (remoteUpdatedAtStr != null) {
        final remoteTime = DateTime.tryParse(remoteUpdatedAtStr)?.millisecondsSinceEpoch ??
            int.tryParse(remoteUpdatedAtStr) ?? 0;
        if (remoteTime > 0 && _lastLocalMutation > remoteTime + 2000) {
          // Local changes are strictly newer; push to server
          await _pushRemote(_snapshot());
          return;
        }
      }

      final needsPurge = PreferencesMigrator.rawHasLegacySecrets(remoteRaw);
      final migration = PreferencesMigrator.migrate(remoteRaw);
      _applyMap(migration.data);
      if (migration.migratedSecrets) await _migrateLegacySecrets(migration);
      await LocalPreferencesStorage.savePreferences(
        _snapshot(),
        userId: _scopeUserId,
      );
      // Overwrite any cloud-stored plaintext or stale-schema blob with the clean one.
      if (needsPurge ||
          migration.fromVersion < PreferenceKeys.currentSchemaVersion) {
        await _pushRemote(_snapshot());
      }
      if (!_disposed) notifyListeners();
    } catch (_) {
      // Local preferences remain authoritative when the network is unavailable.
    }
  }

  Future<void> _pushRemote(Map<String, dynamic> snapshot) async {
    final client = _clientOrNull();
    if (client == null) return;
    final user = client.auth.currentUser;
    if (user == null) return;
    try {
      await client.from('user_feature_settings').upsert(<String, dynamic>{
        'user_id': user.id,
        'settings': snapshot,
        'updated_at': DateTime.now().toUtc().toIso8601String(),
      });
    } catch (_) {
      // Keep the local copy; the next mutation retries the account sync.
    }
  }

  void _applyGbSemanticAlias(String key, Object? value) {
    final flag = value is bool
        ? value
        : value is num
        ? value != 0
        : value?.toString().toLowerCase() == 'true';
    final text = value?.toString() ?? '';
    if (key == 'yoHideSeen') {
      _privacy = _privacy.copyWith(readReceipts: !flag);
      return;
    }
    if (key == 'anti_vw_once') {
      _privacy = _privacy.copyWith(antiViewOnce: flag);
      return;
    }
    if (key == 'yoDisableFwd') {
      _privacy = _privacy.copyWith(disableForwardedLabel: flag);
      return;
    }
    if (key == 'yoCallsPrivacy') {
      _privacy = _privacy.copyWith(whoCanCallMe: text);
      return;
    }
    if (key == 'Saleh_HidePrivacy') {
      _privacy = _privacy.copyWith(hidePrivacyOption: flag);
      return;
    }
    if (key == 'yoHideStatViewV2') {
      _privacy = _privacy.copyWith(hideViewStatus: flag);
      return;
    }
    if (key == 'yoAntiRevokeStatus') {
      _privacy = _privacy.copyWith(antiDeleteStatus: flag);
      return;
    }
    if (key == 'AntiRevokeStatusNotif') {
      _privacy = _privacy.copyWith(statusRevocationAlert: flag);
      return;
    }
    if (key == 'key_chat_editview') {
      _privacy = _privacy.copyWith(showEditedMessage: flag);
      return;
    }
    if (key == 'yoAntiRevoke') {
      _privacy = _privacy.copyWith(antiDeleteMessages: flag);
      return;
    }
    if (key == 'AntiRevokeMsgNotif') {
      _privacy = _privacy.copyWith(messageRevokeAlert: flag);
      return;
    }
    if (key == 'yoBlueOnReply') {
      _privacy = _privacy.copyWith(showBlueTicksAfterReply: flag);
      return;
    }
    if (key == 'home_stories_key') {
      _home = _home.copyWith(enableStoriesStrip: flag);
      return;
    }
    if (key == 'home_stories_style') {
      _home = _home.copyWith(storiesStyle: text);
      return;
    }
    if (key == 'enable_grp_separationV2') {
      _home = _home.copyWith(separateChatsAndGroups: flag);
      return;
    }
    if (key == 'my_name') {
      _home = _home.copyWith(myNameOverride: text);
      return;
    }
    if (key == 'yo_want_ghostmode') {
      _home = _home.copyWith(ghostMode: flag);
      if (flag) {
        _privacy = _privacy.copyWith(
          freezeLastSeen: true,
          readReceipts: false,
          typingIndicators: false,
          recordingIndicators: false,
          hideViewStatus: true,
        );
      }
      return;
    }
    if (key == 'yo_want_airplanemode') {
      _home = _home.copyWith(airplaneModeSimulator: flag);
      return;
    }
    if (key == 'yo_want_toolbar_cam') {
      _home = _home.copyWith(showCameraIcon: flag);
      return;
    }
    if (key == 'ui_home_styleV3') {
      _home = _home.copyWith(homeStyle: text);
      return;
    }
    if (key == 'bubble_style') {
      _conversation = _conversation.copyWith(bubbleShape: text);
      return;
    }
    if (key == 'tick_style') {
      _conversation = _conversation.copyWith(tickStyle: text);
      return;
    }
    if (key == 'abu_saleh_quickcontact') {
      _conversation = _conversation.copyWith(
        enableQuickContactSidebar: text != 'Off',
        sidebarPosition: text == 'Left' ? 'Left' : 'Right',
      );
      return;
    }
    if (key == 'tap_effect_enabled') {
      _effects = _effects.copyWith(enableClickParticles: flag);
      return;
    }
    if (key == 'tap_emoji') {
      _effects = _effects.copyWith(clickParticleSymbol: text);
      return;
    }
    if (key == 'fall_effect_enabled') {
      _effects = _effects.copyWith(enableFallingParticles: flag);
      return;
    }
    if (key == 'fall_emoji') {
      _effects = _effects.copyWith(fallingParticleObject: text);
      return;
    }
    if (key == 'Pop_Heds') {
      _notification = _notification.copyWith(enableGlobalNotifications: flag);
      return;
    }
    if (key == 'abu_saleh_toast_online') {
      _notification = _notification.copyWith(notifyContactOnline: flag);
      return;
    }
    if (key == 'abu_saleh_toast_status') {
      _notification = _notification.copyWith(notifyStatusViewed: flag);
      return;
    }
    if (key == 'abu_saleh_toast_typing') {
      _notification = _notification.copyWith(notifyTypingStarted: flag);
      return;
    }
  }

  void updateGbFeature(String key, Object? value, {String? logTitle}) {
    final previous = _gbFeatures[key];
    _gbFeatures = <String, Object?>{..._gbFeatures, key: value};
    _applyGbSemanticAlias(key, value);
    _logHistory(
      'gb:$key',
      logTitle ?? GbFeatureCatalog.byKey(key)?.title ?? key,
      previous,
      value,
    );
    _persist();
    notifyListeners();
  }

  void updateGbFeatures(
    Map<String, Object?> values, {
    String logTitle = 'GB feature bundle',
  }) {
    final previous = <String, Object?>{..._gbFeatures};
    _gbFeatures = <String, Object?>{..._gbFeatures, ...values};
    for (final entry in values.entries) {
      _applyGbSemanticAlias(entry.key, entry.value);
    }
    _logHistory('gb:bundle', logTitle, previous, values);
    _persist();
    notifyListeners();
  }

  void updatePrivacy(
    PrivacyPreferences newPrefs, {
    String? logTitle,
    dynamic prevVal,
    dynamic newVal,
  }) {
    _privacy = newPrefs;
    if (logTitle != null) _logHistory('privacy', logTitle, prevVal, newVal);
    _persist();
    notifyListeners();
  }

  void updateSecurity(
    SecurityPreferences newPrefs, {
    String? logTitle,
    dynamic prevVal,
    dynamic newVal,
  }) {
    _security = newPrefs;
    if (logTitle != null) _logHistory('security', logTitle, prevVal, newVal);
    _persist();
    notifyListeners();
  }

  void updateHome(
    HomePreferences newPrefs, {
    String? logTitle,
    dynamic prevVal,
    dynamic newVal,
  }) {
    _home = newPrefs;
    if (logTitle != null) _logHistory('home', logTitle, prevVal, newVal);
    _persist();
    notifyListeners();
  }

  void updateConversation(
    ConversationPreferences newPrefs, {
    String? logTitle,
    dynamic prevVal,
    dynamic newVal,
  }) {
    _conversation = newPrefs;
    if (logTitle != null)
      _logHistory('conversation', logTitle, prevVal, newVal);
    _persist();
    notifyListeners();
  }

  void updateNotification(
    NotificationPreferences newPrefs, {
    String? logTitle,
    dynamic prevVal,
    dynamic newVal,
  }) {
    _notification = newPrefs;
    if (logTitle != null)
      _logHistory('notification', logTitle, prevVal, newVal);
    _persist();
    notifyListeners();
  }

  void updateAutomation(
    MessageAutomationPreferences newPrefs, {
    String? logTitle,
    dynamic prevVal,
    dynamic newVal,
  }) {
    _automation = newPrefs;
    if (logTitle != null) _logHistory('automation', logTitle, prevVal, newVal);
    _persist();
    notifyListeners();
  }

  void updateEffects(
    NavigationEffectPreferences newPrefs, {
    String? logTitle,
    dynamic prevVal,
    dynamic newVal,
  }) {
    _effects = newPrefs;
    if (logTitle != null) _logHistory('effects', logTitle, prevVal, newVal);
    _persist();
    notifyListeners();
  }

  void toggleFavorite(String settingKey) {
    if (_starredFavorites.contains(settingKey)) {
      _starredFavorites.remove(settingKey);
    } else {
      _starredFavorites.add(settingKey);
    }
    _persist();
    notifyListeners();
  }

  bool isFavorite(String settingKey) => _starredFavorites.contains(settingKey);

  bool isConversationLocked(String conversationId) =>
      _security.lockedConversationIds.contains(conversationId);

  bool isConversationHidden(String conversationId) =>
      _security.hiddenConversationIds.contains(conversationId);

  bool isConversationProtected(String conversationId) =>
      isConversationLocked(conversationId) || isConversationHidden(conversationId);

  void toggleLockConversation(String conversationId, {bool? lock}) {
    final list = List<String>.from(_security.lockedConversationIds);
    final shouldLock = lock ?? !list.contains(conversationId);
    if (shouldLock) {
      if (!list.contains(conversationId)) list.add(conversationId);
    } else {
      list.remove(conversationId);
    }
    updateSecurity(
      _security.copyWith(lockedConversationIds: list),
      logTitle: shouldLock ? 'Lock Chat' : 'Unlock Chat',
    );
  }

  void toggleHideConversation(String conversationId, {bool? hide}) {
    final list = List<String>.from(_security.hiddenConversationIds);
    final lockedList = List<String>.from(_security.lockedConversationIds);
    final shouldHide = hide ?? !list.contains(conversationId);
    if (shouldHide) {
      if (!list.contains(conversationId)) list.add(conversationId);
      // Hiding a chat also marks it locked
      if (!lockedList.contains(conversationId)) lockedList.add(conversationId);
    } else {
      list.remove(conversationId);
    }
    updateSecurity(
      _security.copyWith(
        hiddenConversationIds: list,
        lockedConversationIds: lockedList,
      ),
      logTitle: shouldHide ? 'Hide Locked Chat' : 'Unhide Chat',
    );
  }

  void unlockConversationCompletely(String conversationId) {
    final hiddenList = List<String>.from(_security.hiddenConversationIds)
      ..remove(conversationId);
    final lockedList = List<String>.from(_security.lockedConversationIds)
      ..remove(conversationId);
    updateSecurity(
      _security.copyWith(
        hiddenConversationIds: hiddenList,
        lockedConversationIds: lockedList,
      ),
      logTitle: 'Unlock Chat Permanently',
    );
  }

  void clearPreferenceHistory() {
    _history.clear();
    notifyListeners();
  }

  void _logHistory(String key, String title, dynamic prevVal, dynamic newVal) {
    _history.insert(
      0,
      PreferenceHistoryEntry(
        key: key,
        title: title,
        previousValue: prevVal,
        newValue: newVal,
        timestamp: DateTime.now(),
      ),
    );
    if (_history.length > 50) _history.removeLast();
  }

  void resetPrivacy() {
    _privacy = const PrivacyPreferences();
    _persist();
    notifyListeners();
  }

  void resetHome() {
    _home = const HomePreferences();
    _persist();
    notifyListeners();
  }

  void resetConversation() {
    _conversation = const ConversationPreferences();
    _persist();
    notifyListeners();
  }

  void resetNotifications() {
    _notification = const NotificationPreferences();
    _persist();
    notifyListeners();
  }

  void resetEffects() {
    _effects = const NavigationEffectPreferences();
    _persist();
    notifyListeners();
  }

  void resetGbFeatures() {
    _gbFeatures = GbFeatureCatalog.defaults;
    _persist();
    notifyListeners();
  }

  void resetAll() {
    _privacy = const PrivacyPreferences();
    _security = const SecurityPreferences();
    _home = const HomePreferences();
    _conversation = const ConversationPreferences();
    _notification = const NotificationPreferences();
    _automation = const MessageAutomationPreferences();
    _effects = const NavigationEffectPreferences();
    _gbFeatures = GbFeatureCatalog.defaults;
    _starredFavorites.clear();
    _history.clear();
    _persist();
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _remoteSyncDebounce?.cancel();
    unawaited(_authSubscription?.cancel());
    super.dispose();
  }
}
