import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../ui/core/controllers/preferences_controller.dart';
import '../repositories/chaty_data_store.dart';

/// Notification payload received from push or local delivery.
class NotificationPayload {
  final String conversationId;
  final String? messageId;
  final String? callId;
  final bool isCall;
  final Map<String, dynamic> extra;

  const NotificationPayload({
    required this.conversationId,
    this.messageId,
    this.callId,
    this.isCall = false,
    this.extra = const {},
  });

  factory NotificationPayload.fromMap(Map<String, dynamic> map) {
    return NotificationPayload(
      conversationId: map['conversation_id']?.toString() ?? '',
      messageId: map['message_id']?.toString(),
      callId: map['call_id']?.toString(),
      isCall: map['is_call'] == true || map['type'] == 'call',
      extra: map,
    );
  }
}

/// Rejects repeated notification tap intents before they reach navigation.
///
/// Android can redeliver the same launch intent during activity recreation,
/// and push SDKs may emit both an initial-message event and a tap event. The
/// server identifiers are stable, so they form a safer deduplication key than
/// timing alone.
class NotificationTapDeduplicator {
  NotificationTapDeduplicator({
    this.window = const Duration(seconds: 10),
    DateTime Function()? clock,
  }) : _clock = clock ?? DateTime.now;

  static const int _maxRemembered = 128;
  final Duration window;
  final DateTime Function() _clock;
  final Map<String, DateTime> _seenAt = <String, DateTime>{};

  bool shouldDispatch(NotificationPayload payload) {
    final key = _keyFor(payload);
    if (key == null) return false;

    final now = _clock();
    final cutoff = now.subtract(window);
    _seenAt.removeWhere((_, seenAt) => seenAt.isBefore(cutoff));
    final previous = _seenAt[key];
    if (previous != null && now.difference(previous) <= window) return false;

    _seenAt[key] = now;
    if (_seenAt.length > _maxRemembered) {
      final oldest = _seenAt.entries.reduce(
        (left, right) => left.value.isBefore(right.value) ? left : right,
      );
      _seenAt.remove(oldest.key);
    }
    return true;
  }

  String? _keyFor(NotificationPayload payload) {
    if (payload.conversationId.isEmpty) return null;
    final callId = payload.callId;
    if (callId != null && callId.isNotEmpty) {
      return 'call:$callId';
    }
    final messageId = payload.messageId;
    if (messageId != null && messageId.isNotEmpty) {
      return 'message:$messageId';
    }
    // Payloads without an event ID cannot be safely distinguished. Use their
    // target and kind to suppress immediate duplicate launch intents.
    return '${payload.isCall ? 'call' : 'conversation'}:${payload.conversationId}';
  }
}

/// Notification Channel Configuration
class ChatyNotificationChannel {
  final String id;
  final String name;
  final String description;
  final int importance; // 1-5
  final bool enableLights;
  final bool enableVibration;
  final bool showBadge;

  const ChatyNotificationChannel({
    required this.id,
    required this.name,
    required this.description,
    required this.importance,
    this.enableLights = true,
    this.enableVibration = true,
    this.showBadge = true,
  });
}

/// Manages Android Notification Channels, Launcher Badges & Deep Link Dispatch.
class NotificationChannelManager extends ChangeNotifier {
  static const String _educationShownKey = 'chaty_notif_education_shown';
  static const String _badgeCountKey = 'chaty_unread_badge_count';

  final ChatyPreferencesController preferences;
  final ChatyDataStore dataStore;

  int _badgeCount = 0;
  bool _educationShown = false;
  final StreamController<NotificationPayload> _deepLinkController =
      StreamController<NotificationPayload>.broadcast();
  final NotificationTapDeduplicator _tapDeduplicator =
      NotificationTapDeduplicator();

  NotificationChannelManager({
    required this.preferences,
    required this.dataStore,
  }) {
    dataStore.addListener(syncUnreadBadgeCount);
    preferences.addListener(syncUnreadBadgeCount);
  }

  int get badgeCount => _badgeCount;
  bool get educationShown => _educationShown;
  Stream<NotificationPayload> get deepLinks => _deepLinkController.stream;

  Future<void> initialize() async {
    final prefs = await SharedPreferences.getInstance();
    _educationShown = prefs.getBool(_educationShownKey) ?? false;
    _badgeCount = prefs.getInt(_badgeCountKey) ?? 0;
    await syncUnreadBadgeCount();
  }

  /// Marks the first-launch notification education modal as completed.
  Future<void> markEducationShown() async {
    _educationShown = true;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_educationShownKey, true);
    notifyListeners();
  }

  /// Calculates real unread message count and updates launcher badge.
  Future<void> syncUnreadBadgeCount() async {
    if (!preferences.notification.enableGlobalNotifications) {
      _badgeCount = 0;
      notifyListeners();
      return;
    }

    int totalUnread = 0;
    for (final convo in dataStore.conversations) {
      if (!convo.isMuted) {
        totalUnread += convo.unreadCount;
      }
    }

    _badgeCount = totalUnread;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_badgeCountKey, _badgeCount);
    notifyListeners();
  }

  /// Clear badge on logout or reset
  Future<void> clearBadge() async {
    _badgeCount = 0;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_badgeCountKey, 0);
    notifyListeners();
  }

  /// Formats notification body according to user privacy preference (Preview ON / OFF).
  String formatPrivacyBody({
    required String senderName,
    required String content,
    bool isCall = false,
  }) {
    if (!preferences.notification.showMessagePreview) {
      return isCall ? 'Incoming call' : 'New message';
    }
    return content;
  }

  /// Routes incoming deep-link payload from push click or system notification tap
  void handleNotificationTap(Map<String, dynamic> rawPayload) {
    final payload = NotificationPayload.fromMap(rawPayload);
    if (_tapDeduplicator.shouldDispatch(payload)) {
      _deepLinkController.add(payload);
    }
  }

  @override
  void dispose() {
    dataStore.removeListener(syncUnreadBadgeCount);
    preferences.removeListener(syncUnreadBadgeCount);
    _deepLinkController.close();
    super.dispose();
  }
}
