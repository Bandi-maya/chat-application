import 'dart:async';

import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../../../domain/models/chaty_preferences.dart';
import '../gb/gb_feature_catalog.dart';
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
  MessageAutomationPreferences _automation = const MessageAutomationPreferences();
  NavigationEffectPreferences _effects = const NavigationEffectPreferences();
  Map<String, Object?> _gbFeatures = GbFeatureCatalog.defaults;

  final Set<String> _starredFavorites = <String>{};
  final List<PreferenceHistoryEntry> _history = <PreferenceHistoryEntry>[];
  Timer? _remoteSyncDebounce;
  StreamSubscription<AuthState>? _authSubscription;
  bool _disposed = false;

  ChatyPreferencesController() {
    _init();
    final client = _clientOrNull();
    if (client != null) {
      _authSubscription = client.auth.onAuthStateChange.listen((state) {
        if (state.session != null) unawaited(_syncFromRemote());
      });
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
  SecurityPreferences get security => _security;
  HomePreferences get home => _home;
  ConversationPreferences get conversation => _conversation;
  NotificationPreferences get notification => _notification;
  MessageAutomationPreferences get automation => _automation;
  NavigationEffectPreferences get effects => _effects;
  Map<String, Object?> get gbFeatures => Map<String, Object?>.unmodifiable(_gbFeatures);
  Set<String> get starredFavorites => Set<String>.unmodifiable(_starredFavorites);
  List<PreferenceHistoryEntry> get history => List<PreferenceHistoryEntry>.unmodifiable(_history);

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

  String gbString(String key, {String fallback = ''}) => _gbFeatures[key]?.toString() ?? fallback;

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
      if (value != null) return Color(normalized.length <= 6 ? 0xFF000000 | value : value);
    }
    return null;
  }

  Future<void> _init() async {
    final data = await LocalPreferencesStorage.loadPreferences();
    if (data.isNotEmpty) _applyMap(data);
    await _syncFromRemote();
    if (!_disposed) notifyListeners();
  }

  void _applyMap(Map<String, dynamic> data) {
    if (data['privacy'] is Map) _privacy = PrivacyPreferences.fromMap(Map<String, dynamic>.from(data['privacy'] as Map));
    if (data['security'] is Map) _security = SecurityPreferences.fromMap(Map<String, dynamic>.from(data['security'] as Map));
    if (data['home'] is Map) _home = HomePreferences.fromMap(Map<String, dynamic>.from(data['home'] as Map));
    if (data['conversation'] is Map) _conversation = ConversationPreferences.fromMap(Map<String, dynamic>.from(data['conversation'] as Map));
    if (data['notification'] is Map) _notification = NotificationPreferences.fromMap(Map<String, dynamic>.from(data['notification'] as Map));
    if (data['automation'] is Map) _automation = MessageAutomationPreferences.fromMap(Map<String, dynamic>.from(data['automation'] as Map));
    if (data['effects'] is Map) _effects = NavigationEffectPreferences.fromMap(Map<String, dynamic>.from(data['effects'] as Map));
    if (data['gbFeatures'] is Map) {
      _gbFeatures = <String, Object?>{
        ...GbFeatureCatalog.defaults,
        ...Map<String, Object?>.from(data['gbFeatures'] as Map),
      };
    }
    if (data['favorites'] is List) {
      _starredFavorites
        ..clear()
        ..addAll((data['favorites'] as List).map((e) => e.toString()));
    }
  }

  Map<String, dynamic> _snapshot() => <String, dynamic>{
        'privacy': _privacy.toMap(),
        'security': _security.toMap(),
        'home': _home.toMap(),
        'conversation': _conversation.toMap(),
        'notification': _notification.toMap(),
        'automation': _automation.toMap(),
        'effects': _effects.toMap(),
        'gbFeatures': _gbFeatures,
        'favorites': _starredFavorites.toList(growable: false),
      };

  void _persist() {
    final snapshot = _snapshot();
    unawaited(LocalPreferencesStorage.savePreferences(snapshot));
    _remoteSyncDebounce?.cancel();
    if (_clientOrNull() != null) {
      _remoteSyncDebounce = Timer(const Duration(milliseconds: 650), () => unawaited(_pushRemote(snapshot)));
    }
  }

  Future<void> _syncFromRemote() async {
    final client = _clientOrNull();
    if (client == null) return;
    final user = client.auth.currentUser;
    if (user == null) return;
    try {
      final row = await client.from('user_feature_settings').select('settings').eq('user_id', user.id).maybeSingle();
      if (row == null || row['settings'] is! Map) {
        await _pushRemote(_snapshot());
        return;
      }
      final remote = Map<String, dynamic>.from(row['settings'] as Map);
      _applyMap(remote);
      await LocalPreferencesStorage.savePreferences(_snapshot());
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
    final flag = value is bool ? value : value is num ? value != 0 : value?.toString().toLowerCase() == 'true';
    final text = value?.toString() ?? '';
    if (key == 'yoHideSeen') { _privacy = _privacy.copyWith(readReceipts: !flag); return; }
    if (key == 'anti_vw_once') { _privacy = _privacy.copyWith(antiViewOnce: flag); return; }
    if (key == 'yoDisableFwd') { _privacy = _privacy.copyWith(disableForwardedLabel: flag); return; }
    if (key == 'yoCallsPrivacy') { _privacy = _privacy.copyWith(whoCanCallMe: text); return; }
    if (key == 'Saleh_HidePrivacy') { _privacy = _privacy.copyWith(hidePrivacyOption: flag); return; }
    if (key == 'Saleh_HideCUpdates') { _privacy = _privacy.copyWith(hideUpdateOption: flag); return; }
    if (key == 'abu_saleh_channels') { _privacy = _privacy.copyWith(disableChannels: flag); return; }
    if (key == 'yoHideStatViewV2') { _privacy = _privacy.copyWith(hideViewStatus: flag); return; }
    if (key == 'yoAntiRevokeStatus') { _privacy = _privacy.copyWith(antiDeleteStatus: flag); return; }
    if (key == 'AntiRevokeStatusNotif') { _privacy = _privacy.copyWith(statusRevocationAlert: flag); return; }
    if (key == 'disappearing_message_key') { _privacy = _privacy.copyWith(antiDisappearingMessages: flag); return; }
    if (key == 'key_chat_editview') { _privacy = _privacy.copyWith(showEditedMessage: flag); return; }
    if (key == 'yoAntiRevoke') { _privacy = _privacy.copyWith(antiDeleteMessages: flag); return; }
    if (key == 'AntiRevokeMsgNotif') { _privacy = _privacy.copyWith(messageRevokeAlert: flag); return; }
    if (key == 'yoBlueOnReply') { _privacy = _privacy.copyWith(showBlueTicksAfterReply: flag); return; }
    if (key == 'home_stories_key') { _home = _home.copyWith(enableStoriesStrip: flag); return; }
    if (key == 'home_stories_style') { _home = _home.copyWith(storiesStyle: text); return; }
    if (key == 'enable_grp_separationV2') { _home = _home.copyWith(separateChatsAndGroups: flag); return; }
    if (key == 'my_name') { _home = _home.copyWith(myNameOverride: text); return; }
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
    if (key == 'yo_want_airplanemode') { _home = _home.copyWith(airplaneModeSimulator: flag); return; }
    if (key == 'yo_want_toolbar_cam') { _home = _home.copyWith(showCameraIcon: flag); return; }
    if (key == 'yo_multi_account_menu') { _home = _home.copyWith(showAddAccount: flag); return; }
    if (key == 'ui_home_styleV3') { _home = _home.copyWith(homeStyle: text); return; }
    if (key == 'bubble_style') { _conversation = _conversation.copyWith(bubbleShape: text); return; }
    if (key == 'tick_style') { _conversation = _conversation.copyWith(tickStyle: text); return; }
    if (key == 'abu_saleh_quickcontact') {
      _conversation = _conversation.copyWith(enableQuickContactSidebar: text != 'Off', sidebarPosition: text == 'Left' ? 'Left' : 'Right');
      return;
    }
    if (key == 'inconvo_trans_option') { _conversation = _conversation.copyWith(enableTranslation: text != 'Off'); return; }
    if (key == 'trans_def_to') { _conversation = _conversation.copyWith(targetLanguage: text); return; }
    if (key == 'key_pager_animation') { _effects = _effects.copyWith(pageTransitionStyle: text); return; }
    if (key == 'tap_effect_enabled') { _effects = _effects.copyWith(enableClickParticles: flag); return; }
    if (key == 'tap_emoji') { _effects = _effects.copyWith(clickParticleSymbol: text); return; }
    if (key == 'fall_effect_enabled') { _effects = _effects.copyWith(enableFallingParticles: flag); return; }
    if (key == 'fall_emoji') { _effects = _effects.copyWith(fallingParticleObject: text); return; }
    if (key == 'Pop_Heds') { _notification = _notification.copyWith(enableGlobalNotifications: flag); return; }
    if (key == 'abu_saleh_toast_online') { _notification = _notification.copyWith(notifyContactOnline: flag); return; }
    if (key == 'abu_saleh_toast_status') { _notification = _notification.copyWith(notifyStatusViewed: flag); return; }
    if (key == 'abu_saleh_toast_typing') { _notification = _notification.copyWith(notifyTypingStarted: flag); return; }
  }

  void updateGbFeature(String key, Object? value, {String? logTitle}) {
    final previous = _gbFeatures[key];
    _gbFeatures = <String, Object?>{..._gbFeatures, key: value};
    _applyGbSemanticAlias(key, value);
    _logHistory('gb:$key', logTitle ?? GbFeatureCatalog.byKey(key)?.title ?? key, previous, value);
    _persist();
    notifyListeners();
  }

  void updateGbFeatures(Map<String, Object?> values, {String logTitle = 'GB feature bundle'}) {
    final previous = <String, Object?>{..._gbFeatures};
    _gbFeatures = <String, Object?>{..._gbFeatures, ...values};
    for (final entry in values.entries) {
      _applyGbSemanticAlias(entry.key, entry.value);
    }
    _logHistory('gb:bundle', logTitle, previous, values);
    _persist();
    notifyListeners();
  }

  void updatePrivacy(PrivacyPreferences newPrefs, {String? logTitle, dynamic prevVal, dynamic newVal}) { _privacy = newPrefs; if (logTitle != null) _logHistory('privacy', logTitle, prevVal, newVal); _persist(); notifyListeners(); }
  void updateSecurity(SecurityPreferences newPrefs, {String? logTitle, dynamic prevVal, dynamic newVal}) { _security = newPrefs; if (logTitle != null) _logHistory('security', logTitle, prevVal, newVal); _persist(); notifyListeners(); }
  void updateHome(HomePreferences newPrefs, {String? logTitle, dynamic prevVal, dynamic newVal}) { _home = newPrefs; if (logTitle != null) _logHistory('home', logTitle, prevVal, newVal); _persist(); notifyListeners(); }
  void updateConversation(ConversationPreferences newPrefs, {String? logTitle, dynamic prevVal, dynamic newVal}) { _conversation = newPrefs; if (logTitle != null) _logHistory('conversation', logTitle, prevVal, newVal); _persist(); notifyListeners(); }
  void updateNotification(NotificationPreferences newPrefs, {String? logTitle, dynamic prevVal, dynamic newVal}) { _notification = newPrefs; if (logTitle != null) _logHistory('notification', logTitle, prevVal, newVal); _persist(); notifyListeners(); }
  void updateAutomation(MessageAutomationPreferences newPrefs, {String? logTitle, dynamic prevVal, dynamic newVal}) { _automation = newPrefs; if (logTitle != null) _logHistory('automation', logTitle, prevVal, newVal); _persist(); notifyListeners(); }
  void updateEffects(NavigationEffectPreferences newPrefs, {String? logTitle, dynamic prevVal, dynamic newVal}) { _effects = newPrefs; if (logTitle != null) _logHistory('effects', logTitle, prevVal, newVal); _persist(); notifyListeners(); }

  void toggleFavorite(String settingKey) { if (_starredFavorites.contains(settingKey)) { _starredFavorites.remove(settingKey); } else { _starredFavorites.add(settingKey); } _persist(); notifyListeners(); }
  bool isFavorite(String settingKey) => _starredFavorites.contains(settingKey);
  
  bool isConversationLocked(String conversationId) => _security.lockedConversationIds.contains(conversationId);

  void toggleLockConversation(String conversationId, {bool? lock}) {
    final list = List<String>.from(_security.lockedConversationIds);
    final shouldLock = lock ?? !list.contains(conversationId);
    if (shouldLock) {
      if (!list.contains(conversationId)) list.add(conversationId);
    } else {
      list.remove(conversationId);
    }
    updateSecurity(_security.copyWith(lockedConversationIds: list), logTitle: shouldLock ? 'Lock Chat' : 'Unlock Chat');
  }

  void clearPreferenceHistory() { _history.clear(); notifyListeners(); }

  void _logHistory(String key, String title, dynamic prevVal, dynamic newVal) {
    _history.insert(0, PreferenceHistoryEntry(key: key, title: title, previousValue: prevVal, newValue: newVal, timestamp: DateTime.now()));
    if (_history.length > 50) _history.removeLast();
  }

  void resetPrivacy() { _privacy = const PrivacyPreferences(); _persist(); notifyListeners(); }
  void resetHome() { _home = const HomePreferences(); _persist(); notifyListeners(); }
  void resetConversation() { _conversation = const ConversationPreferences(); _persist(); notifyListeners(); }
  void resetNotifications() { _notification = const NotificationPreferences(); _persist(); notifyListeners(); }
  void resetEffects() { _effects = const NavigationEffectPreferences(); _persist(); notifyListeners(); }
  void resetGbFeatures() { _gbFeatures = GbFeatureCatalog.defaults; _persist(); notifyListeners(); }

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
