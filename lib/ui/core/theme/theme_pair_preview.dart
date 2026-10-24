import 'package:flutter/material.dart';

import '../../../domain/models/chat_message.dart';
import '../bubbles/bubble_painter.dart';
import '../ticks/delivery_status_icon.dart';
import 'theme_config.dart';

/// Two miniature phones side by side: the Home (chat list) screen and an open
/// Conversation, both painted from one resolved [ThemeConfig]. What the card
/// shows is what applying that theme resolves to (surface, accent, text and
/// bubble tokens plus the theme's own bubble and tick style).
///
/// It is a pure painter of tokens. It reads no stores and starts no streams,
/// so it is safe inside lists. Text inside the miniatures ignores the system
/// text scale on purpose: they are thumbnails, and their geometry must not
/// change with the user's font size.
class ThemePairPreview extends StatelessWidget {
  const ThemePairPreview({
    super.key,
    required this.themeConfig,
    this.phoneHeight = 176,
  });

  final ThemeConfig themeConfig;
  final double phoneHeight;

  @override
  Widget build(BuildContext context) {
    final phoneWidth = phoneHeight * 0.56;
    return Semantics(
      label:
          '${themeConfig.name}: home screen and conversation preview',
      excludeSemantics: true,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          _Labeled(
            label: 'Home',
            color: themeConfig.secondaryTextColor,
            child: _PhoneFrame(
              themeConfig: themeConfig,
              width: phoneWidth,
              height: phoneHeight,
              child: _HomeScreenMini(themeConfig: themeConfig),
            ),
          ),
          _Labeled(
            label: 'Conversation',
            color: themeConfig.secondaryTextColor,
            child: _PhoneFrame(
              themeConfig: themeConfig,
              width: phoneWidth,
              height: phoneHeight,
              child: _ConversationMini(themeConfig: themeConfig),
            ),
          ),
        ],
      ),
    );
  }
}

class _Labeled extends StatelessWidget {
  const _Labeled({
    required this.label,
    required this.color,
    required this.child,
  });

  final String label;
  final Color color;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        child,
        const SizedBox(height: 4),
        Text(
          label,
          textScaler: TextScaler.noScaling,
          style: TextStyle(
            color: color,
            fontSize: 10,
            fontWeight: FontWeight.w600,
          ),
        ),
      ],
    );
  }
}

class _PhoneFrame extends StatelessWidget {
  const _PhoneFrame({
    required this.themeConfig,
    required this.width,
    required this.height,
    required this.child,
  });

  final ThemeConfig themeConfig;
  final double width;
  final double height;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(width * 0.14);
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        color: themeConfig.backgroundColor,
        borderRadius: radius,
        border: Border.all(
          color: themeConfig.secondaryTextColor.withValues(alpha: 0.45),
          width: 1.5,
        ),
      ),
      clipBehavior: Clip.antiAlias,
      child: child,
    );
  }
}

Widget _line(Color color, double width, double height) => Container(
  width: width,
  height: height,
  decoration: BoxDecoration(
    color: color,
    borderRadius: BorderRadius.circular(height / 2),
  ),
);

class _HomeScreenMini extends StatelessWidget {
  const _HomeScreenMini({required this.themeConfig});

  final ThemeConfig themeConfig;

  @override
  Widget build(BuildContext context) {
    final t = themeConfig;
    return Column(
      children: [
        Container(
          height: 22,
          color: t.surfaceColor,
          padding: const EdgeInsets.symmetric(horizontal: 6),
          child: Row(
            children: [
              Expanded(
                child: Text(
                  'Chats',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  textScaler: TextScaler.noScaling,
                  style: TextStyle(
                    color: t.primaryTextColor,
                    fontSize: 9,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
              Icon(Icons.search_rounded, size: 10, color: t.secondaryTextColor),
            ],
          ),
        ),
        Expanded(
          child: Stack(
            children: [
              Column(
                children: [
                  for (var i = 0; i < 5; i++)
                    Padding(
                      padding: const EdgeInsets.fromLTRB(6, 5, 6, 0),
                      child: Row(
                        children: [
                          Container(
                            width: 14,
                            height: 14,
                            decoration: BoxDecoration(
                              color: i == 0
                                  ? t.accentColor.withValues(alpha: 0.85)
                                  : t.cardColor,
                              shape: BoxShape.circle,
                            ),
                          ),
                          const SizedBox(width: 5),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                _line(
                                  t.primaryTextColor.withValues(alpha: 0.8),
                                  24.0 + (i % 3) * 6,
                                  3,
                                ),
                                const SizedBox(height: 3),
                                _line(
                                  t.secondaryTextColor.withValues(alpha: 0.55),
                                  36.0 - (i % 2) * 8,
                                  2.5,
                                ),
                              ],
                            ),
                          ),
                          if (i < 2)
                            Container(
                              width: 7,
                              height: 7,
                              decoration: BoxDecoration(
                                color: t.accentColor,
                                shape: BoxShape.circle,
                              ),
                            ),
                        ],
                      ),
                    ),
                ],
              ),
              Positioned(
                right: 6,
                bottom: 6,
                child: Container(
                  width: 15,
                  height: 15,
                  decoration: BoxDecoration(
                    color: t.accentColor,
                    borderRadius: BorderRadius.circular(5),
                  ),
                  child: Icon(
                    Icons.add_rounded,
                    size: 11,
                    color: t.outgoingTextColor,
                  ),
                ),
              ),
            ],
          ),
        ),
        Container(
          height: 18,
          color: t.surfaceColor,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              for (var i = 0; i < 3; i++)
                Container(
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: i == 0
                        ? t.accentColor
                        : t.secondaryTextColor.withValues(alpha: 0.5),
                    shape: BoxShape.circle,
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

class _ConversationMini extends StatelessWidget {
  const _ConversationMini({required this.themeConfig});

  final ThemeConfig themeConfig;

  Widget _bubble({
    required bool isMe,
    required String text,
    required Color fill,
    required Color textColor,
    required Color stroke,
    bool tick = false,
  }) {
    final t = themeConfig;
    return Align(
      alignment: isMe ? Alignment.centerRight : Alignment.centerLeft,
      child: CustomPaint(
        painter: BubblePainter(
          styleId: t.bubbleStyle,
          isMe: isMe,
          fillColor: fill,
          strokeColor: stroke,
          accentColor: t.accentColor,
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Flexible(
                child: Text(
                  text,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  textScaler: TextScaler.noScaling,
                  style: TextStyle(color: textColor, fontSize: 7.5),
                ),
              ),
              if (tick) ...[
                const SizedBox(width: 3),
                DeliveryStatusIcon(
                  style: t.deliveryTickStyle,
                  state: DeliveryState.read,
                  unreadColor: textColor.withValues(alpha: 0.6),
                  readColor: t.brightness == Brightness.dark
                      ? t.accentColor
                      : t.outgoingTextColor,
                  size: 8,
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final t = themeConfig;
    return Column(
      children: [
        Container(
          height: 22,
          color: t.surfaceColor,
          padding: const EdgeInsets.symmetric(horizontal: 6),
          child: Row(
            children: [
              Container(
                width: 11,
                height: 11,
                decoration: BoxDecoration(
                  color: t.cardColor,
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: 4),
              Expanded(
                child: Text(
                  'Maya',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  textScaler: TextScaler.noScaling,
                  style: TextStyle(
                    color: t.primaryTextColor,
                    fontSize: 8.5,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              Icon(Icons.more_vert_rounded, size: 10, color: t.secondaryTextColor),
            ],
          ),
        ),
        Expanded(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 6),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                _bubble(
                  isMe: false,
                  text: 'Lunch?',
                  fill: t.incomingBubbleColor,
                  textColor: t.incomingTextColor,
                  stroke: t.secondaryTextColor.withValues(alpha: 0.12),
                ),
                const SizedBox(height: 4),
                _bubble(
                  isMe: true,
                  text: 'Sounds good',
                  fill: t.outgoingBubbleColor,
                  textColor: t.outgoingTextColor,
                  stroke: t.accentColor.withValues(alpha: 0.2),
                  tick: true,
                ),
                const SizedBox(height: 4),
                _bubble(
                  isMe: false,
                  text: 'At 1?',
                  fill: t.incomingBubbleColor,
                  textColor: t.incomingTextColor,
                  stroke: t.secondaryTextColor.withValues(alpha: 0.12),
                ),
              ],
            ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(4, 0, 4, 5),
          child: Row(
            children: [
              Expanded(
                child: Container(
                  height: 13,
                  decoration: BoxDecoration(
                    color: t.surfaceColor,
                    borderRadius: BorderRadius.circular(7),
                    border: Border.all(
                      color: t.secondaryTextColor.withValues(alpha: 0.25),
                      width: 0.7,
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 3),
              Container(
                width: 13,
                height: 13,
                decoration: BoxDecoration(
                  color: t.accentColor,
                  shape: BoxShape.circle,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
