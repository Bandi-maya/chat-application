import 'package:flutter/material.dart';

import '../../../domain/models/chat_message.dart';
import '../bubbles/bubble_painter.dart';
import '../ticks/delivery_status_icon.dart';
import '../templates/applied_preview_model.dart';
import '../templates/template_models.dart';
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
    this.layout,
    this.phoneHeight = 176,
  });

  final ThemeConfig themeConfig;

  /// Resolved template specs (header, chat list, navigation, composer). When
  /// null the miniatures use one neutral layout and only the palette varies.
  final PreviewLayout? layout;
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
            label: layout == null
                ? 'Home'
                : 'Home \u00b7 ${layout!.homeStylePreset}',
            maxWidth: phoneWidth + 24,
            color: themeConfig.secondaryTextColor,
            child: _PhoneFrame(
              themeConfig: themeConfig,
              width: phoneWidth,
              height: phoneHeight,
              child: _HomeScreenMini(themeConfig: themeConfig, layout: layout),
            ),
          ),
          _Labeled(
            label: 'Conversation',
            color: themeConfig.secondaryTextColor,
            child: _PhoneFrame(
              themeConfig: themeConfig,
              width: phoneWidth,
              height: phoneHeight,
              child: _ConversationMini(themeConfig: themeConfig, layout: layout),
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
    this.maxWidth = double.infinity,
  });

  final double maxWidth;
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
        ConstrainedBox(
          constraints: BoxConstraints(maxWidth: maxWidth),
          child: Text(
          label,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          textAlign: TextAlign.center,
          textScaler: TextScaler.noScaling,
          style: TextStyle(
            color: color,
            fontSize: 10,
            fontWeight: FontWeight.w600,
          ),
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
  const _HomeScreenMini({required this.themeConfig, this.layout});

  final ThemeConfig themeConfig;
  final PreviewLayout? layout;

  @override
  Widget build(BuildContext context) {
    final t = themeConfig;
    final l = layout;
    final prominent = l?.home.headerStyle == HomeHeaderStyle.prominentIdentity;
    final searchForward = l?.home.headerStyle == HomeHeaderStyle.searchForward;
    final showStories = l?.showStories ?? false;
    final rowCount = switch (l?.chatList.density) {
      ChatListDensity.compact => 6,
      ChatListDensity.comfortable => 4,
      _ => 5,
    };
    final roundAvatar = (l?.chatList.avatarShape ?? 'circle') == 'circle';
    BoxDecoration avatar(Color color) => BoxDecoration(
      color: color,
      shape: roundAvatar ? BoxShape.circle : BoxShape.rectangle,
      borderRadius: roundAvatar ? null : BorderRadius.circular(4),
    );
    return Column(
      children: [
        Container(
          height: prominent ? 28 : 22,
          color: t.surfaceColor,
          padding: const EdgeInsets.symmetric(horizontal: 6),
          child: Row(
            children: [
              if (prominent) ...[
                Container(
                  width: 14,
                  height: 14,
                  decoration: avatar(t.accentColor.withValues(alpha: 0.6)),
                ),
                const SizedBox(width: 4),
              ],
              if (searchForward)
                Expanded(
                  child: Container(
                    height: 12,
                    decoration: BoxDecoration(
                      color: t.cardColor,
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Icon(
                      Icons.search_rounded,
                      size: 9,
                      color: t.secondaryTextColor,
                    ),
                  ),
                )
              else ...[
                Expanded(
                  child: Text(
                    'Chats',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    textScaler: TextScaler.noScaling,
                    style: TextStyle(
                      color: t.primaryTextColor,
                      fontSize: prominent ? 10.5 : 9,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
                Icon(
                  Icons.search_rounded,
                  size: 10,
                  color: t.secondaryTextColor,
                ),
              ],
            ],
          ),
        ),
        if (showStories)
          Container(
            height: 16,
            color: t.surfaceColor,
            padding: const EdgeInsets.symmetric(horizontal: 6),
            child: Row(
              children: [
                for (var i = 0; i < 5; i++)
                  Padding(
                    padding: const EdgeInsets.only(right: 4),
                    child: Container(
                      width: 11,
                      height: 11,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        border: Border.all(
                          color: i < 2 ? t.accentColor : t.cardColor,
                          width: 1.2,
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        Expanded(
          child: Stack(
            children: [
              Column(
                children: [
                  for (var i = 0; i < rowCount; i++)
                    Padding(
                      padding: const EdgeInsets.fromLTRB(6, 4, 6, 0),
                      child: Row(
                        children: [
                          Container(
                            width: 13,
                            height: 13,
                            decoration: avatar(
                              i == 0
                                  ? t.accentColor.withValues(alpha: 0.85)
                                  : t.cardColor,
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
                                const SizedBox(height: 2.5),
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
                              width: 6,
                              height: 6,
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
                bottom: 5,
                child: Container(
                  width: 14,
                  height: 14,
                  decoration: BoxDecoration(
                    color: t.accentColor,
                    borderRadius: BorderRadius.circular(5),
                  ),
                  child: Icon(
                    Icons.add_rounded,
                    size: 10,
                    color: t.outgoingTextColor,
                  ),
                ),
              ),
            ],
          ),
        ),
        _NavBarMini(themeConfig: t, layout: l),
      ],
    );
  }
}

class _ConversationMini extends StatelessWidget {
  const _ConversationMini({required this.themeConfig, this.layout});

  final ThemeConfig themeConfig;
  final PreviewLayout? layout;

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
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (layout?.composer.actionPlacement ==
                  ComposerActionPlacement.powerRow)
                Padding(
                  padding: const EdgeInsets.only(bottom: 3),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceAround,
                    children: [
                      for (var i = 0; i < 4; i++)
                        Container(
                          width: 6,
                          height: 6,
                          decoration: BoxDecoration(
                            color: t.secondaryTextColor.withValues(alpha: 0.6),
                            borderRadius: BorderRadius.circular(2),
                          ),
                        ),
                    ],
                  ),
                ),
              Row(
                children: [
                  if (layout?.composer.actionPlacement ==
                      ComposerActionPlacement.split) ...[
                    Container(
                      width: 13,
                      height: 13,
                      decoration: BoxDecoration(
                        color: t.cardColor,
                        shape: BoxShape.circle,
                      ),
                    ),
                    const SizedBox(width: 3),
                  ],
                  Expanded(
                    child: Container(
                      height: 13,
                      decoration: BoxDecoration(
                        color: t.surfaceColor,
                        borderRadius: BorderRadius.circular(
                          ((layout?.composer.cornerRadius ?? 24) / 24 * 7)
                              .clamp(3.0, 7.0),
                        ),
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
            ],
          ),
        ),
      ],
    );
  }
}

IconData _navIcon(String id) => switch (id) {
  'groups' => Icons.groups_rounded,
  'updates' => Icons.auto_stories_rounded,
  'tasks' => Icons.task_alt_rounded,
  'calls' => Icons.call_rounded,
  'settings' => Icons.settings_rounded,
  'desktop' => Icons.desktop_windows_rounded,
  _ => Icons.chat_bubble_rounded,
};

/// Bottom bar miniature. Destinations are the resolved IDs (hidden Tasks and
/// Updates tabs already removed); the container and active-tab treatment
/// follow the bottom bar style name.
class _NavBarMini extends StatelessWidget {
  const _NavBarMini({required this.themeConfig, this.layout});

  final ThemeConfig themeConfig;
  final PreviewLayout? layout;

  @override
  Widget build(BuildContext context) {
    final t = themeConfig;
    final style = layout?.bottomBarStyle ?? 'Classic Label Bar';
    final ids = (layout?.destinationIds.isNotEmpty ?? false)
        ? layout!.destinationIds
        : const ['chats', 'updates', 'calls'];
    final floating = const {
      'Floating Pill',
      'Floating Dynamic Island',
      'Segmented Glass Dock',
    }.contains(style);
    final dark = style == 'Floating Dynamic Island';
    final labels = style == 'Classic Label Bar';
    final raisedCenter = style == 'Raised Center Action';
    final barColor = dark ? t.primaryTextColor.withValues(alpha: 0.9) : t.surfaceColor;
    final inactive = dark
        ? t.backgroundColor.withValues(alpha: 0.7)
        : t.secondaryTextColor.withValues(alpha: 0.7);
    Widget item(int i) {
      final active = i == 0;
      final raised = raisedCenter && i == ids.length ~/ 2;
      final chip = active &&
          const {'Active Pill Chip', 'Soft Square Tile', 'Circle Accent Pop'}
              .contains(style);
      final icon = Icon(
        _navIcon(ids[i]),
        size: raised ? 10 : 9,
        color: raised
            ? t.outgoingTextColor
            : (active ? (chip ? t.accentColor : t.accentColor) : inactive),
      );
      Widget core = icon;
      if (raised) {
        core = Container(
          width: 14,
          height: 14,
          decoration: BoxDecoration(color: t.accentColor, shape: BoxShape.circle),
          child: Center(child: icon),
        );
      } else if (chip) {
        core = Container(
          padding: EdgeInsets.symmetric(
            horizontal: style == 'Active Pill Chip' ? 6 : 3,
            vertical: 1.5,
          ),
          decoration: BoxDecoration(
            color: t.accentColor.withValues(alpha: 0.2),
            borderRadius: BorderRadius.circular(
              style == 'Soft Square Tile' ? 3 : 8,
            ),
            shape: style == 'Circle Accent Pop'
                ? BoxShape.circle
                : BoxShape.rectangle,
          ),
          child: icon,
        );
      }
      return Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          if (active && style == 'Top Indicator Line')
            Container(width: 10, height: 1.5, color: t.accentColor)
          else if (style == 'Top Indicator Line')
            const SizedBox(height: 1.5),
          core,
          if (labels)
            Padding(
              padding: const EdgeInsets.only(top: 1),
              child: _line(
                active ? t.accentColor : inactive,
                9,
                1.5,
              ),
            ),
          if (active &&
              (style == 'Bottom Indicator Dot' ||
                  style == 'Curved Notch Teardrop'))
            Container(
              width: 3,
              height: 3,
              margin: const EdgeInsets.only(top: 1),
              decoration: BoxDecoration(
                color: t.accentColor,
                shape: BoxShape.circle,
              ),
            ),
        ],
      );
    }

    return Container(
      height: 20,
      margin: floating
          ? const EdgeInsets.fromLTRB(8, 0, 8, 3)
          : EdgeInsets.zero,
      decoration: BoxDecoration(
        color: barColor,
        borderRadius: floating ? BorderRadius.circular(10) : null,
        border: floating ? Border.all(color: t.cardColor, width: 0.8) : null,
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [for (var i = 0; i < ids.length; i++) item(i)],
      ),
    );
  }
}
