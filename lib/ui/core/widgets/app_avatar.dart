import 'package:flutter/material.dart';

import '../../../domain/models/user_profile.dart';
import '../design_system/design_system.dart';

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

  Color _parseColor(BuildContext context, String? hex) {
    if (hex == null || hex.isEmpty) return context.colors.primary;
    try {
      final clean = hex.replaceAll('#', '').replaceAll('0x', '');
      return Color(
        int.parse(clean.length == 6 ? 'FF$clean' : clean, radix: 16),
      );
    } catch (_) {
      return context.colors.primary;
    }
  }

  Color _getPresenceColor(BuildContext context) {
    final colors = context.colors;
    switch (presence) {
      case PresenceState.online:
        return colors.success;
      case PresenceState.away:
        return colors.warning;
      case PresenceState.typing:
        return colors.primary;
      case PresenceState.offline:
        return colors.foregroundTertiary;
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
          color: _parseColor(context, colorHex),
          size: size,
        ),
        if (showOnlineBadge)
          Positioned(
            right: -1,
            bottom: -1,
            child: ChatyOnlineDot(
              active: true,
              avatarSize: size,
              color: _getPresenceColor(context),
              ringColor: ringColor,
            ),
          ),
      ],
    );
  }
}
