import 'package:flutter/material.dart';

import 'chaty_kit.dart';
import '../settings_primitives.dart';

/// Looks for the home stories strip. UI only: each look changes how a tile
/// is drawn, never what a tile does.
enum ChatyStoryLook {
  circular,
  instagram,
  stock,
  facebook,
  squircle,
  card,
  wideCard,
}

ChatyStoryLook chatyStoryLookFor(String style) {
  switch (style) {
    case 'Instagram':
      return ChatyStoryLook.instagram;
    case 'Stock':
      return ChatyStoryLook.stock;
    case 'Facebook':
      return ChatyStoryLook.facebook;
    case 'Squircle':
      return ChatyStoryLook.squircle;
    case 'Card':
    case 'Card View':
      return ChatyStoryLook.card;
    case 'Wide Card':
      return ChatyStoryLook.wideCard;
    default:
      return ChatyStoryLook.circular;
  }
}

/// Strip height needed by a look, never smaller than [base].
double chatyStoryStripHeight(ChatyStoryLook look, double base) {
  switch (look) {
    case ChatyStoryLook.facebook:
      return base < 118 ? 118 : base;
    case ChatyStoryLook.wideCard:
      return base < 76 ? 76 : base;
    case ChatyStoryLook.card:
      return base < 88 ? 88 : base;
    default:
      return base;
  }
}

double chatyStoryTileWidth(ChatyStoryLook look, double base) {
  switch (look) {
    case ChatyStoryLook.facebook:
      return 80;
    case ChatyStoryLook.wideCard:
      return 156;
    default:
      return base;
  }
}

class ChatyStoryTile extends StatelessWidget {
  final ChatyStoryLook look;
  final String initials;
  final Color avatarColor;
  final String name;
  final bool online;
  final double avatarSize;
  final double baseWidth;
  final double nameFontSize;
  final bool wrapInCard;
  final Color accent;
  final Color cardColor;
  final Color borderColor;
  final Color nameColor;
  final Color onlineColor;
  final Color backgroundColor;

  const ChatyStoryTile({
    super.key,
    required this.look,
    required this.initials,
    required this.avatarColor,
    required this.name,
    required this.online,
    required this.avatarSize,
    required this.baseWidth,
    required this.nameFontSize,
    required this.accent,
    required this.cardColor,
    required this.borderColor,
    required this.nameColor,
    required this.onlineColor,
    required this.backgroundColor,
    this.wrapInCard = false,
  });

  Widget _avatar(double size, String shape) {
    return Stack(
      clipBehavior: Clip.none,
      children: [
        ChatyAvatar(
          initials: initials,
          color: avatarColor,
          size: size,
          shape: shape,
        ),
        if (online)
          Positioned(
            right: -1,
            bottom: -1,
            child: ChatyOnlineDot(
              active: true,
              avatarSize: size,
              color: onlineColor,
              ringColor: backgroundColor,
            ),
          ),
      ],
    );
  }

  Widget _label({Color? color}) {
    return Text(
      name,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: TextStyle(color: color ?? nameColor, fontSize: nameFontSize),
    );
  }

  Widget _ring(double outer, double width, double gap, Color color, Widget child) {
    return Container(
      width: outer,
      height: outer,
      padding: EdgeInsets.all(gap),
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        border: Border.all(color: color, width: width),
      ),
      child: child,
    );
  }

  @override
  Widget build(BuildContext context) {
    final width = chatyStoryTileWidth(look, baseWidth);
    switch (look) {
      case ChatyStoryLook.facebook:
        return _portrait(width);
      case ChatyStoryLook.wideCard:
        return _wide(width);
      case ChatyStoryLook.instagram:
        final inner = avatarSize - 10;
        return _column(
          width,
          _ring(avatarSize, 2.5, 2.5, accent, _avatar(inner, 'circle')),
        );
      case ChatyStoryLook.stock:
        final inner = avatarSize - 6;
        return _column(
          width,
          _ring(
            avatarSize,
            1.5,
            1.5,
            accent.withValues(alpha: 0.75),
            _avatar(inner, 'circle'),
          ),
        );
      case ChatyStoryLook.squircle:
        return _column(width, _avatar(avatarSize, 'squircle'));
      case ChatyStoryLook.card:
        return _column(width, _avatar(avatarSize, 'roundedSquare'), card: true);
      case ChatyStoryLook.circular:
        return _column(width, _avatar(avatarSize, 'circle'));
    }
  }

  Widget _column(double width, Widget avatar, {bool card = false}) {
    final inner = SizedBox(
      width: card || wrapInCard ? width - 8 : width,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [avatar, const SizedBox(height: 4), _label()],
      ),
    );
    if (!card && !wrapInCard) return inner;
    return Container(
      width: width,
      margin: const EdgeInsets.symmetric(vertical: 2),
      padding: const EdgeInsets.fromLTRB(4, 6, 4, 6),
      decoration: BoxDecoration(
        color: cardColor,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: borderColor),
      ),
      child: inner,
    );
  }

  /// Tall portrait card: tinted body, small ringed avatar top left, name on a
  /// dark scrim at the bottom.
  Widget _portrait(double width) {
    return Container(
      key: const ValueKey<String>('story-portrait-card'),
      width: width,
      margin: const EdgeInsets.symmetric(vertical: 2),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: borderColor),
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            avatarColor.withValues(alpha: 0.95),
            avatarColor.withValues(alpha: 0.55),
          ],
        ),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(13),
        child: Stack(
          children: [
            Positioned(
              left: 6,
              top: 6,
              child: _ring(
                36,
                2,
                1.5,
                accent,
                _avatar(29, 'circle'),
              ),
            ),
            Align(
              alignment: Alignment.center,
              child: Text(
                initials,
                style: TextStyle(
                  color: Colors.white.withValues(alpha: 0.55),
                  fontSize: 26,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              child: Container(
                color: Colors.black.withValues(alpha: 0.38),
                padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 4),
                child: _label(color: Colors.white),
              ),
            ),
          ],
        ),
      ),
    );
  }

  /// Wide card: avatar on the left, name on the right.
  Widget _wide(double width) {
    return Container(
      key: const ValueKey<String>('story-wide-card'),
      width: width,
      margin: const EdgeInsets.symmetric(vertical: 4),
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
      decoration: BoxDecoration(
        color: cardColor,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: borderColor),
      ),
      child: Row(
        children: [
          _avatar(40, 'circle'),
          const SizedBox(width: 8),
          Expanded(child: _label()),
        ],
      ),
    );
  }
}
