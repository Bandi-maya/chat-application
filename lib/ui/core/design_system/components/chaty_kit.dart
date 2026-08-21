import 'package:flutter/material.dart';

/// ---------------------------------------------------------------------------
/// CHATY KIT — the single source of truth for WhatsApp-iOS component chrome.
///
/// Every screen renders avatars, presence dots, unread badges, section
/// headers and time labels through these primitives so proportions and
/// behavior are IDENTICAL everywhere. Colors stay theme-driven by design;
/// only geometry, weight and press behavior live here.
/// ---------------------------------------------------------------------------

/// Canonical avatar paint: a FLAT circle (or squircle/square) with centered
/// white initials. Premium-iOS rule: no glows, no gradients, no borders —
/// depth comes from placement, never from decoration.
class ChatyAvatarCore extends StatelessWidget {
  final String initials;
  final Color color;
  final double size;

  /// 'circle' | 'squircle' | 'roundedSquare'
  final String shape;

  const ChatyAvatarCore({
    super.key,
    required this.initials,
    required this.color,
    required this.size,
    this.shape = 'circle',
  });

  BorderRadius get _radius {
    switch (shape) {
      case 'squircle':
        return BorderRadius.circular(size * 0.35);
      case 'roundedSquare':
        return BorderRadius.circular(size * 0.22);
      default:
        return BorderRadius.circular(size / 2);
    }
  }

  @override
  Widget build(BuildContext context) {
    final initialText = initials.trim().isEmpty
        ? '?'
        : initials.trim().characters.take(2).toString().toUpperCase();
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(color: color, borderRadius: _radius),
      alignment: Alignment.center,
      child: Text(
        initialText,
        maxLines: 1,
        style: TextStyle(
          color: Colors.white,
          fontWeight: FontWeight.w600,
          fontSize: size * 0.38,
          letterSpacing: 0.3,
          height: 1.0,
        ),
      ),
    );
  }
}

/// WhatsApp-style presence dot: sits BOTTOM-RIGHT of an avatar, scales with
/// the avatar size, and carries a 2px ring in the surrounding surface color
/// so it reads as punched through.
class ChatyOnlineDot extends StatelessWidget {
  final bool active;
  final double avatarSize;
  final Color color;
  final Color ringColor;

  const ChatyOnlineDot({
    super.key,
    required this.active,
    required this.avatarSize,
    required this.color,
    required this.ringColor,
  });

  @override
  Widget build(BuildContext context) {
    if (!active) return const SizedBox.shrink();
    final diameter = (avatarSize * 0.28).clamp(10.0, 14.0);
    return Container(
      width: diameter,
      height: diameter,
      decoration: BoxDecoration(
        color: color,
        shape: BoxShape.circle,
        border: Border.all(color: ringColor, width: 2),
      ),
    );
  }
}

/// Unread-count pill: full-round, fixed 20dp height, bold 12pt digits.
class ChatyCountBadge extends StatelessWidget {
  final int count;
  final Color color;
  final Color textColor;

  const ChatyCountBadge({
    super.key,
    required this.count,
    required this.color,
    required this.textColor,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(minWidth: 20, minHeight: 20),
      padding: const EdgeInsets.symmetric(horizontal: 7),
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        '$count',
        style: TextStyle(
          color: textColor,
          fontSize: 12,
          fontWeight: FontWeight.w700,
          height: 1.0,
        ),
      ),
    );
  }
}

/// Uppercase grouped-section header ('RECENT UPDATES', 'PINNED', …).
class ChatySectionHeader extends StatelessWidget {
  final String text;
  final Color color;
  final double fontSize;

  const ChatySectionHeader({
    super.key,
    required this.text,
    required this.color,
    this.fontSize = 11.5,
  });

  @override
  Widget build(BuildContext context) {
    return Text(
      text.toUpperCase(),
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: TextStyle(
        color: color,
        fontSize: fontSize,
        fontWeight: FontWeight.w800,
        letterSpacing: 0.8,
        height: 1.0,
      ),
    );
  }
}

/// Row timestamp. Renders quiet gray normally and flips to the accent color
/// with bold weight when [highlight] is set (unread conversations).
class ChatyTimeLabel extends StatelessWidget {
  final String text;
  final bool highlight;
  final Color color;
  final Color highlightColor;

  const ChatyTimeLabel({
    super.key,
    required this.text,
    required this.color,
    this.highlight = false,
    required this.highlightColor,
  });

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      maxLines: 1,
      style: TextStyle(
        color: highlight ? highlightColor : color,
        fontSize: 12,
        fontWeight: highlight ? FontWeight.w700 : FontWeight.w500,
        height: 1.0,
      ),
    );
  }
}

/// Hairline divider indented past leading content — iOS inset style.
class ChatyInsetDivider extends StatelessWidget {
  final Color color;
  final double indent;

  const ChatyInsetDivider({
    super.key,
    required this.color,
    this.indent = 66,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 0.6,
      margin: EdgeInsets.only(left: indent),
      color: color.withValues(alpha: 0.12),
    );
  }
}
