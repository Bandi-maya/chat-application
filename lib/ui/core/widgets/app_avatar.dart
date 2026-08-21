import 'package:flutter/material.dart';

import '../../../domain/models/user_profile.dart';
import '../design_system/components/chaty_kit.dart';

/// Thin adapter over [ChatyAvatarCore] (the kit's canonical avatar) so all
/// legacy call sites get the flat premium-iOS paint automatically.
class AppAvatar extends StatelessWidget {
  final String initials;
  final String? colorHex;
  final double size;
  final bool showOnlineBadge;
  final PresenceState presence;

  const AppAvatar({
    super.key,
    required this.initials,
    this.colorHex,
    this.size = 44,
    this.showOnlineBadge = false,
    this.presence = PresenceState.offline,
  });

  Color _parseColor(String? hex) {
    if (hex == null || hex.isEmpty) return const Color(0xFF6366F1);
    try {
      final clean = hex.replaceAll('#', '').replaceAll('0x', '');
      return Color(
        int.parse(clean.length == 6 ? 'FF$clean' : clean, radix: 16),
      );
    } catch (_) {
      return const Color(0xFF6366F1);
    }
  }

  Color _getPresenceColor() {
    switch (presence) {
      case PresenceState.online:
        return const Color(0xFF10B981);
      case PresenceState.away:
        return const Color(0xFFF59E0B);
      case PresenceState.typing:
        return const Color(0xFF6366F1);
      case PresenceState.offline:
        return const Color(0xFF94A3B8);
    }
  }

  @override
  Widget build(BuildContext context) {
    final ringColor = Theme.of(context).scaffoldBackgroundColor;
    return Stack(
      clipBehavior: Clip.none,
      children: [
        ChatyAvatarCore(
          initials: initials,
          color: _parseColor(colorHex),
          size: size,
        ),
        if (showOnlineBadge)
          Positioned(
            right: -1,
            bottom: -1,
            child: ChatyOnlineDot(
              active: true,
              avatarSize: size,
              color: _getPresenceColor(),
              ringColor: ringColor,
            ),
          ),
      ],
    );
  }
}
