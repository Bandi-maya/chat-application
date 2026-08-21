class ContactPrivacyOverride {
  final String ownerUserId;
  final String targetUserId;
  final bool hideDeliveryReceipts;
  final bool hideReadReceipts;
  final bool hideTyping;
  final bool hideRecording;
  final bool hideOnline;
  final bool hideLastSeen;

  const ContactPrivacyOverride({
    required this.ownerUserId,
    required this.targetUserId,
    this.hideDeliveryReceipts = false,
    this.hideReadReceipts = false,
    this.hideTyping = false,
    this.hideRecording = false,
    this.hideOnline = false,
    this.hideLastSeen = false,
  });

  ContactPrivacyOverride copyWith({
    bool? hideDeliveryReceipts,
    bool? hideReadReceipts,
    bool? hideTyping,
    bool? hideRecording,
    bool? hideOnline,
    bool? hideLastSeen,
  }) {
    return ContactPrivacyOverride(
      ownerUserId: ownerUserId,
      targetUserId: targetUserId,
      hideDeliveryReceipts: hideDeliveryReceipts ?? this.hideDeliveryReceipts,
      hideReadReceipts: hideReadReceipts ?? this.hideReadReceipts,
      hideTyping: hideTyping ?? this.hideTyping,
      hideRecording: hideRecording ?? this.hideRecording,
      hideOnline: hideOnline ?? this.hideOnline,
      hideLastSeen: hideLastSeen ?? this.hideLastSeen,
    );
  }

  Map<String, dynamic> toDatabaseMap() => <String, dynamic>{
    'owner_user_id': ownerUserId,
    'target_user_id': targetUserId,
    'hide_delivery_receipts': hideDeliveryReceipts,
    'hide_read_receipts': hideReadReceipts,
    'hide_typing': hideTyping,
    'hide_recording': hideRecording,
    'hide_online': hideOnline,
    'hide_last_seen': hideLastSeen,
    'updated_at': DateTime.now().toUtc().toIso8601String(),
  };

  factory ContactPrivacyOverride.fromDatabaseMap(Map<String, dynamic> map) {
    return ContactPrivacyOverride(
      ownerUserId: map['owner_user_id']?.toString() ?? '',
      targetUserId: map['target_user_id']?.toString() ?? '',
      hideDeliveryReceipts: map['hide_delivery_receipts'] == true,
      hideReadReceipts: map['hide_read_receipts'] == true,
      hideTyping: map['hide_typing'] == true,
      hideRecording: map['hide_recording'] == true,
      hideOnline: map['hide_online'] == true,
      hideLastSeen: map['hide_last_seen'] == true,
    );
  }
}

class ContactConnectionStatus {
  final String? conversationId;
  final bool myAccepted;
  final bool otherAccepted;

  const ContactConnectionStatus({
    this.conversationId,
    this.myAccepted = false,
    this.otherAccepted = false,
  });

  bool get callsAllowed => myAccepted && otherAccepted;
  bool get isPendingIncoming => !myAccepted && otherAccepted;
  bool get isWaitingForOther => myAccepted && !otherAccepted;
  bool get exists => conversationId != null && conversationId!.isNotEmpty;

  factory ContactConnectionStatus.fromDatabaseMap(Map<String, dynamic> map) {
    return ContactConnectionStatus(
      conversationId: map['conversation_id']?.toString(),
      myAccepted: map['my_accepted'] == true,
      otherAccepted: map['other_accepted'] == true,
    );
  }
}
