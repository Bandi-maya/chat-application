import 'package:flutter/material.dart';

/// Shared source of truth for the Universal Notification Icon picker and the
/// in-app notification toast. Selecting an icon must change both preview and
/// runtime rendering rather than only saving a display label.
@immutable
class ChatyNotificationIconPreset {
  final String name;
  final Color color;
  final IconData icon;

  const ChatyNotificationIconPreset({
    required this.name,
    required this.color,
    required this.icon,
  });
}

abstract final class ChatyNotificationIconPresets {
  static const List<ChatyNotificationIconPreset> values = [
    ChatyNotificationIconPreset(name: 'White', color: Colors.white, icon: Icons.chat_bubble_outline_rounded),
    ChatyNotificationIconPreset(name: 'Black', color: Colors.black87, icon: Icons.chat_bubble_rounded),
    ChatyNotificationIconPreset(name: 'Blue', color: Colors.blue, icon: Icons.forum_rounded),
    ChatyNotificationIconPreset(name: 'Red', color: Colors.redAccent, icon: Icons.mode_comment_rounded),
    ChatyNotificationIconPreset(name: 'Green', color: Colors.green, icon: Icons.sms_rounded),
    ChatyNotificationIconPreset(name: 'Yellow', color: Colors.amber, icon: Icons.chat_rounded),
    ChatyNotificationIconPreset(name: 'Orange', color: Colors.deepOrange, icon: Icons.mark_chat_unread_rounded),
    ChatyNotificationIconPreset(name: 'Gold', color: Color(0xFFFFD700), icon: Icons.message_rounded),
    ChatyNotificationIconPreset(name: 'Cyan', color: Colors.cyan, icon: Icons.bubble_chart_rounded),
    ChatyNotificationIconPreset(name: 'Pink', color: Colors.pinkAccent, icon: Icons.chat_bubble_rounded),
    ChatyNotificationIconPreset(name: 'Magenta', color: Colors.purpleAccent, icon: Icons.chat_bubble_outline_rounded),
    ChatyNotificationIconPreset(name: 'Purple', color: Colors.deepPurple, icon: Icons.forum_outlined),
    ChatyNotificationIconPreset(name: 'Notifybar 12', color: Colors.grey, icon: Icons.mode_comment_outlined),
    ChatyNotificationIconPreset(name: 'Notifybar 13', color: Colors.pink, icon: Icons.chat_bubble_outline_rounded),
    ChatyNotificationIconPreset(name: 'Notifybar 14', color: Colors.teal, icon: Icons.forum_rounded),
    ChatyNotificationIconPreset(name: 'Notifybar 15', color: Colors.deepPurpleAccent, icon: Icons.message_outlined),
    ChatyNotificationIconPreset(name: 'Notifybar 16', color: Colors.orangeAccent, icon: Icons.chat_rounded),
    ChatyNotificationIconPreset(name: 'Notifybar 17', color: Colors.amberAccent, icon: Icons.mark_chat_unread_rounded),
    ChatyNotificationIconPreset(name: 'Notifybar 18', color: Colors.lightGreen, icon: Icons.chat_bubble_rounded),
    ChatyNotificationIconPreset(name: 'Notifybar 19', color: Colors.red, icon: Icons.sms_outlined),
    ChatyNotificationIconPreset(name: 'Notifybar 20', color: Colors.pink, icon: Icons.mode_comment_rounded),
    ChatyNotificationIconPreset(name: 'Notifybar 21', color: Colors.grey, icon: Icons.forum_outlined),
    ChatyNotificationIconPreset(name: 'Notifybar 22', color: Colors.redAccent, icon: Icons.chat_bubble_outline_rounded),
    ChatyNotificationIconPreset(name: 'Notifybar 23', color: Colors.greenAccent, icon: Icons.mark_chat_unread_rounded),
    ChatyNotificationIconPreset(name: 'Notifybar 24', color: Colors.pinkAccent, icon: Icons.message_rounded),
    ChatyNotificationIconPreset(name: 'Notifybar 25', color: Colors.amber, icon: Icons.forum_rounded),
    ChatyNotificationIconPreset(name: 'Notifybar 26', color: Colors.lightBlueAccent, icon: Icons.chat_bubble_rounded),
    ChatyNotificationIconPreset(name: 'Notifybar 27', color: Colors.brown, icon: Icons.chat_rounded),
  ];

  static ChatyNotificationIconPreset forName(String? name) {
    for (final preset in values) {
      if (preset.name == name) return preset;
    }
    return values.first;
  }
}
