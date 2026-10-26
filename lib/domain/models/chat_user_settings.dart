import 'package:flutter/foundation.dart';

@immutable
class ChatUserSettings {
  final String userId;
  final String? tenantId;
  final DateTime? lastSeen;
  final bool freezeLastSeen;
  final String whoCanSeeLastSeen; // 'everyone' | 'contacts' | 'nobody'
  final String whoCanSeeOnline; // 'everyone' | 'same_as_last_seen' | 'nobody'
  final bool readReceiptsEnabled; // true | false (Blue Ticks)
  final String whoCanSeeReadReceipts; // 'everyone' | 'contacts' | 'nobody'
  final bool blueTicksAfterReply; // true | false
  final bool typingIndicatorsEnabled; // true | false
  final bool isDeactivated;
  final DateTime? deactivatedAt;
  final String? deactivationReason;
  final bool isOnline;

  const ChatUserSettings({
    required this.userId,
    this.tenantId,
    this.lastSeen,
    this.freezeLastSeen = false,
    this.whoCanSeeLastSeen = 'everyone',
    this.whoCanSeeOnline = 'everyone',
    this.readReceiptsEnabled = true,
    this.whoCanSeeReadReceipts = 'everyone',
    this.blueTicksAfterReply = false,
    this.typingIndicatorsEnabled = true,
    this.isDeactivated = false,
    this.deactivatedAt,
    this.deactivationReason,
    this.isOnline = false,
  });

  factory ChatUserSettings.fromJson(Map<String, dynamic> json) {
    DateTime? parseDate(dynamic val) {
      if (val == null) return null;
      if (val is DateTime) return val;
      final str = val.toString().trim();
      if (str.isEmpty) return null;
      final normalized = str.endsWith('Z') || RegExp(r'[+-]\d{2}(:\d{2})?$').hasMatch(str) ? str : '${str}Z';
      return DateTime.tryParse(normalized);
    }

    return ChatUserSettings(
      userId: (json['user_id'] ?? json['userId'] ?? '').toString(),
      tenantId: json['tenant_id']?.toString() ?? json['tenantId']?.toString(),
      lastSeen: parseDate(json['last_seen'] ?? json['lastSeen']),
      freezeLastSeen: json['freeze_last_seen'] == true || json['freezeLastSeen'] == true,
      whoCanSeeLastSeen: (json['who_can_see_last_seen'] ?? json['whoCanSeeLastSeen'] ?? 'everyone').toString().toLowerCase(),
      whoCanSeeOnline: (json['who_can_see_online'] ?? json['whoCanSeeOnline'] ?? 'everyone').toString().toLowerCase(),
      readReceiptsEnabled: json['read_receipts_enabled'] != false && json['readReceiptsEnabled'] != false,
      whoCanSeeReadReceipts: (json['who_can_see_read_receipts'] ?? json['whoCanSeeReadReceipts'] ?? 'everyone').toString().toLowerCase(),
      blueTicksAfterReply: json['blue_ticks_after_reply'] == true || json['blueTicksAfterReply'] == true,
      typingIndicatorsEnabled: json['typing_indicators_enabled'] != false && json['typingIndicatorsEnabled'] != false,
      isDeactivated: json['is_deactivated'] == true || json['isDeactivated'] == true,
      deactivatedAt: parseDate(json['deactivated_at'] ?? json['deactivatedAt']),
      deactivationReason: json['deactivation_reason']?.toString() ?? json['deactivationReason']?.toString(),
      isOnline: json['is_online'] == true || json['isOnline'] == true,
    );
  }

  Map<String, dynamic> toJson() {
    return <String, dynamic>{
      'user_id': userId,
      if (tenantId != null) 'tenant_id': tenantId,
      'freeze_last_seen': freezeLastSeen,
      'who_can_see_last_seen': whoCanSeeLastSeen,
      'who_can_see_online': whoCanSeeOnline,
      'read_receipts_enabled': readReceiptsEnabled,
      'who_can_see_read_receipts': whoCanSeeReadReceipts,
      'blue_ticks_after_reply': blueTicksAfterReply,
      'typing_indicators_enabled': typingIndicatorsEnabled,
      'is_deactivated': isDeactivated,
      if (deactivatedAt != null) 'deactivated_at': deactivatedAt!.toIso8601String(),
      if (deactivationReason != null) 'deactivation_reason': deactivationReason,
    };
  }

  ChatUserSettings copyWith({
    String? userId,
    String? tenantId,
    DateTime? lastSeen,
    bool? freezeLastSeen,
    String? whoCanSeeLastSeen,
    String? whoCanSeeOnline,
    bool? readReceiptsEnabled,
    String? whoCanSeeReadReceipts,
    bool? blueTicksAfterReply,
    bool? typingIndicatorsEnabled,
    bool? isDeactivated,
    DateTime? deactivatedAt,
    String? deactivationReason,
    bool? isOnline,
  }) {
    return ChatUserSettings(
      userId: userId ?? this.userId,
      tenantId: tenantId ?? this.tenantId,
      lastSeen: lastSeen ?? this.lastSeen,
      freezeLastSeen: freezeLastSeen ?? this.freezeLastSeen,
      whoCanSeeLastSeen: whoCanSeeLastSeen ?? this.whoCanSeeLastSeen,
      whoCanSeeOnline: whoCanSeeOnline ?? this.whoCanSeeOnline,
      readReceiptsEnabled: readReceiptsEnabled ?? this.readReceiptsEnabled,
      whoCanSeeReadReceipts: whoCanSeeReadReceipts ?? this.whoCanSeeReadReceipts,
      blueTicksAfterReply: blueTicksAfterReply ?? this.blueTicksAfterReply,
      typingIndicatorsEnabled: typingIndicatorsEnabled ?? this.typingIndicatorsEnabled,
      isDeactivated: isDeactivated ?? this.isDeactivated,
      deactivatedAt: deactivatedAt ?? this.deactivatedAt,
      deactivationReason: deactivationReason ?? this.deactivationReason,
      isOnline: isOnline ?? this.isOnline,
    );
  }
}
