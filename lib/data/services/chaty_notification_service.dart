import 'package:flutter/material.dart';

class ChatyEventNotification {
  final String id;
  final String title;
  final String body;
  final IconData icon;
  final Color color;
  final DateTime timestamp;
  final String? userId;
  final String? avatarInitials;
  final String? avatarColorHex;

  const ChatyEventNotification({
    required this.id,
    required this.title,
    required this.body,
    this.icon = Icons.notifications_active_rounded,
    this.color = const Color(0xFF6366F1),
    required this.timestamp,
    this.userId,
    this.avatarInitials,
    this.avatarColorHex,
  });
}

class ChatyNotificationService extends ChangeNotifier {
  final List<ChatyEventNotification> _notifications = <ChatyEventNotification>[];

  List<ChatyEventNotification> get notifications => List<ChatyEventNotification>.unmodifiable(_notifications);
  ChatyEventNotification? get latest => _notifications.isEmpty ? null : _notifications.first;

  void triggerEventNotification({
    required String title,
    required String body,
    IconData icon = Icons.notifications_rounded,
    Color color = const Color(0xFF6366F1),
    String? userId,
    String? avatarInitials,
    String? avatarColorHex,
  }) {
    final notification = ChatyEventNotification(
      id: 'notif_${DateTime.now().microsecondsSinceEpoch}',
      title: title,
      body: body,
      icon: icon,
      color: color,
      timestamp: DateTime.now(),
      userId: userId,
      avatarInitials: avatarInitials,
      avatarColorHex: avatarColorHex,
    );
    _notifications.insert(0, notification);
    if (_notifications.length > 100) _notifications.removeRange(100, _notifications.length);
    notifyListeners();
  }

  void clearAll() {
    if (_notifications.isEmpty) return;
    _notifications.clear();
    notifyListeners();
  }
}
