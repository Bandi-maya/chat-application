enum PresenceState {
  online,
  away,
  offline,
  typing,
}

class UserProfile {
  final String id;
  final String displayName;
  final String username;
  final String avatarInitials;
  final String avatarColorHex;
  final String about;
  final PresenceState presence;
  final DateTime lastSeenAt;
  final bool isVerified;
  final String email;
  final String phone;
  final String safetyNumber;

  const UserProfile({
    required this.id,
    required this.displayName,
    required this.username,
    required this.avatarInitials,
    required this.avatarColorHex,
    required this.about,
    this.presence = PresenceState.offline,
    required this.lastSeenAt,
    this.isVerified = false,
    this.email = '',
    this.phone = '',
    // Populated only when real device-to-device verification exists.
    this.safetyNumber = '',
  });

  UserProfile copyWith({
    String? id,
    String? displayName,
    String? username,
    String? avatarInitials,
    String? avatarColorHex,
    String? about,
    PresenceState? presence,
    DateTime? lastSeenAt,
    bool? isVerified,
    String? email,
    String? phone,
    String? safetyNumber,
  }) {
    return UserProfile(
      id: id ?? this.id,
      displayName: displayName ?? this.displayName,
      username: username ?? this.username,
      avatarInitials: avatarInitials ?? this.avatarInitials,
      avatarColorHex: avatarColorHex ?? this.avatarColorHex,
      about: about ?? this.about,
      presence: presence ?? this.presence,
      lastSeenAt: lastSeenAt ?? this.lastSeenAt,
      isVerified: isVerified ?? this.isVerified,
      email: email ?? this.email,
      phone: phone ?? this.phone,
      safetyNumber: safetyNumber ?? this.safetyNumber,
    );
  }
}
