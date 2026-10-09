import 'dart:convert';
import 'package:flutter/foundation.dart';

@immutable
class CustomizationSnapshot {
  static const int currentSchemaVersion = 1;

  final int schemaVersion;
  final String globalThemeId;
  final String navigationVariant;
  final String headerVariant;
  final String composerVariant;
  final String bubbleVariant;
  final String tickVariant;
  final String chatListVariant;
  final String reactionVariant;
  final String popupVariant;
  final String buttonVariant;
  final String textFieldVariant;
  final String avatarVariant;
  final String wallpaperId;
  final String motionProfile;
  final String densityProfile;
  final Map<String, Map<String, String>> screenOverrides;
  final Map<String, Map<String, String>> conversationOverrides;
  final Map<String, dynamic> metadata;

  const CustomizationSnapshot({
    this.schemaVersion = currentSchemaVersion,
    this.globalThemeId = 'chaty_emerald',
    this.navigationVariant = 'classic_label_bar',
    this.headerVariant = 'standard_identity',
    this.composerVariant = 'chaty_capsule',
    this.bubbleVariant = 'stock',
    this.tickVariant = 'rc_ios_11',
    this.chatListVariant = 'spacious_content_first',
    this.reactionVariant = 'standard_rail',
    this.popupVariant = 'sheet',
    this.buttonVariant = 'standard_filled',
    this.textFieldVariant = 'standard_outlined',
    this.avatarVariant = 'circle',
    this.wallpaperId = 'default_solid',
    this.motionProfile = 'standard',
    this.densityProfile = 'comfortable',
    this.screenOverrides = const {},
    this.conversationOverrides = const {},
    this.metadata = const {},
  });

  static const CustomizationSnapshot defaultSnapshot = CustomizationSnapshot();

  CustomizationSnapshot copyWith({
    int? schemaVersion,
    String? globalThemeId,
    String? navigationVariant,
    String? headerVariant,
    String? composerVariant,
    String? bubbleVariant,
    String? tickVariant,
    String? chatListVariant,
    String? reactionVariant,
    String? popupVariant,
    String? buttonVariant,
    String? textFieldVariant,
    String? avatarVariant,
    String? wallpaperId,
    String? motionProfile,
    String? densityProfile,
    Map<String, Map<String, String>>? screenOverrides,
    Map<String, Map<String, String>>? conversationOverrides,
    Map<String, dynamic>? metadata,
  }) {
    return CustomizationSnapshot(
      schemaVersion: schemaVersion ?? this.schemaVersion,
      globalThemeId: globalThemeId ?? this.globalThemeId,
      navigationVariant: navigationVariant ?? this.navigationVariant,
      headerVariant: headerVariant ?? this.headerVariant,
      composerVariant: composerVariant ?? this.composerVariant,
      bubbleVariant: bubbleVariant ?? this.bubbleVariant,
      tickVariant: tickVariant ?? this.tickVariant,
      chatListVariant: chatListVariant ?? this.chatListVariant,
      reactionVariant: reactionVariant ?? this.reactionVariant,
      popupVariant: popupVariant ?? this.popupVariant,
      buttonVariant: buttonVariant ?? this.buttonVariant,
      textFieldVariant: textFieldVariant ?? this.textFieldVariant,
      avatarVariant: avatarVariant ?? this.avatarVariant,
      wallpaperId: wallpaperId ?? this.wallpaperId,
      motionProfile: motionProfile ?? this.motionProfile,
      densityProfile: densityProfile ?? this.densityProfile,
      screenOverrides: screenOverrides ?? this.screenOverrides,
      conversationOverrides: conversationOverrides ?? this.conversationOverrides,
      metadata: metadata ?? this.metadata,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'schemaVersion': schemaVersion,
      'globalThemeId': globalThemeId,
      'navigationVariant': navigationVariant,
      'headerVariant': headerVariant,
      'composerVariant': composerVariant,
      'bubbleVariant': bubbleVariant,
      'tickVariant': tickVariant,
      'chatListVariant': chatListVariant,
      'reactionVariant': reactionVariant,
      'popupVariant': popupVariant,
      'buttonVariant': buttonVariant,
      'textFieldVariant': textFieldVariant,
      'avatarVariant': avatarVariant,
      'wallpaperId': wallpaperId,
      'motionProfile': motionProfile,
      'densityProfile': densityProfile,
      'screenOverrides': screenOverrides,
      'conversationOverrides': conversationOverrides,
      'metadata': metadata,
    };
  }

  factory CustomizationSnapshot.fromJson(Map<String, dynamic> json) {
    Map<String, Map<String, String>> parseNestedMap(dynamic raw) {
      if (raw is! Map) return {};
      final result = <String, Map<String, String>>{};
      for (final entry in raw.entries) {
        if (entry.value is Map) {
          result[entry.key.toString()] = (entry.value as Map)
              .map((k, v) => MapEntry(k.toString(), v.toString()));
        }
      }
      return result;
    }

    return CustomizationSnapshot(
      schemaVersion: json['schemaVersion'] as int? ?? currentSchemaVersion,
      globalThemeId: json['globalThemeId'] as String? ?? 'chaty_emerald',
      navigationVariant: json['navigationVariant'] as String? ?? 'classic_label_bar',
      headerVariant: json['headerVariant'] as String? ?? 'standard_identity',
      composerVariant: json['composerVariant'] as String? ?? 'chaty_capsule',
      bubbleVariant: json['bubbleVariant'] as String? ?? 'stock',
      tickVariant: json['tickVariant'] as String? ?? 'rc_ios_11',
      chatListVariant: json['chatListVariant'] as String? ?? 'spacious_content_first',
      reactionVariant: json['reactionVariant'] as String? ?? 'standard_rail',
      popupVariant: json['popupVariant'] as String? ?? 'sheet',
      buttonVariant: json['buttonVariant'] as String? ?? 'standard_filled',
      textFieldVariant: json['textFieldVariant'] as String? ?? 'standard_outlined',
      avatarVariant: json['avatarVariant'] as String? ?? 'circle',
      wallpaperId: json['wallpaperId'] as String? ?? 'default_solid',
      motionProfile: json['motionProfile'] as String? ?? 'standard',
      densityProfile: json['densityProfile'] as String? ?? 'comfortable',
      screenOverrides: parseNestedMap(json['screenOverrides']),
      conversationOverrides: parseNestedMap(json['conversationOverrides']),
      metadata: json['metadata'] is Map<String, dynamic>
          ? json['metadata'] as Map<String, dynamic>
          : {},
    );
  }

  String encode() => jsonEncode(toJson());

  static CustomizationSnapshot? decode(String? raw) {
    if (raw == null || raw.isEmpty) return null;
    try {
      final decoded = jsonDecode(raw);
      if (decoded is Map<String, dynamic>) {
        return CustomizationSnapshot.fromJson(decoded);
      }
    } catch (_) {}
    return null;
  }
}
