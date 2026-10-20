enum PresenceState { online, away, offline, typing }

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

  /// Remote photo/banner URLs (public profile-media objects); null when the
  /// user has not uploaded media yet.
  final String? avatarUrl;
  final String? bannerUrl;

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
    this.avatarUrl,
    this.bannerUrl,
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
    String? avatarUrl,
    String? bannerUrl,
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
      avatarUrl: avatarUrl ?? this.avatarUrl,
      bannerUrl: bannerUrl ?? this.bannerUrl,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'displayName': displayName,
    'username': username,
    'avatarInitials': avatarInitials,
    'avatarColorHex': avatarColorHex,
    'about': about,
    'presence': presence.name,
    'lastSeenAt': lastSeenAt.toIso8601String(),
    'isVerified': isVerified,
    'email': email,
    'phone': phone,
    'safetyNumber': safetyNumber,
    'avatarUrl': avatarUrl,
    'bannerUrl': bannerUrl,
  };

  factory UserProfile.fromJson(Map<String, dynamic> json) => UserProfile(
    id: json['id']?.toString() ?? '',
    displayName: json['displayName']?.toString() ?? '',
    username: json['username']?.toString() ?? '',
    avatarInitials: json['avatarInitials']?.toString() ?? 'U',
    avatarColorHex: json['avatarColorHex']?.toString() ?? '0xFF6366F1',
    about: json['about']?.toString() ?? '',
    presence: PresenceState.values.firstWhere(
      (p) => p.name == json['presence'],
      orElse: () => PresenceState.offline,
    ),
    lastSeenAt: json['lastSeenAt'] != null
        ? DateTime.tryParse(json['lastSeenAt'].toString()) ?? DateTime.now()
        : DateTime.now(),
    isVerified: json['isVerified'] == true,
    email: json['email']?.toString() ?? '',
    phone: json['phone']?.toString() ?? '',
    safetyNumber: json['safetyNumber']?.toString() ?? '',
    avatarUrl: json['avatarUrl']?.toString(),
    bannerUrl: json['bannerUrl']?.toString(),
  );
}

