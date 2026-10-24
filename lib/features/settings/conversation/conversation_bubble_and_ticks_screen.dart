import 'package:flutter/material.dart';

import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart';
import '../../../ui/core/design_system/gb_design_system.dart';

import '../../../ui/core/bubbles/bubble_style_id.dart';
import '../../../ui/core/ticks/delivery_icon_style.dart';
import '../../../ui/core/ticks/delivery_status_icon_painter.dart';
import '../../../domain/models/chat_message.dart';
import '../../../ui/core/bubbles/bubble_style_preview.dart';

class ConversationBubbleAndTicksScreen extends StatefulWidget {
  final ChatyPreferencesController preferencesController;

  const ConversationBubbleAndTicksScreen({
    super.key,
    required this.preferencesController,
  });

  @override
  State<ConversationBubbleAndTicksScreen> createState() =>
      _ConversationBubbleAndTicksScreenState();
}

class _ConversationBubbleAndTicksScreenState
    extends State<ConversationBubbleAndTicksScreen> {
  /// Every tick style Chaty already draws, Stock first (stored as display name).
  // GB reference picker order (all 87 styles) followed by Chaty's own extras.
  final List<DeliveryIconStyle> _tickStyleIds = <DeliveryIconStyle>[
    DeliveryIconStyle.stock,
    DeliveryIconStyle.allo,
    DeliveryIconStyle.alien,
    DeliveryIconStyle.altCrayonV2,
    DeliveryIconStyle.altCrayon,
    DeliveryIconStyle.aran,
    DeliveryIconStyle.bar,
    DeliveryIconStyle.bat,
    DeliveryIconStyle.bbm,
    DeliveryIconStyle.blend,
    DeliveryIconStyle.ngAndroidBot,
    DeliveryIconStyle.bpgCircle,
    DeliveryIconStyle.gbCircles,
    DeliveryIconStyle.samyadeepCircles,
    DeliveryIconStyle.coalfield,
    DeliveryIconStyle.colorSimple,
    DeliveryIconStyle.ctoon,
    DeliveryIconStyle.eclipsis,
    DeliveryIconStyle.faceSwitch,
    DeliveryIconStyle.feet,
    DeliveryIconStyle.flatCircles,
    DeliveryIconStyle.gifcon,
    DeliveryIconStyle.googleV2,
    DeliveryIconStyle.google,
    DeliveryIconStyle.grw,
    DeliveryIconStyle.sidzyHd,
    DeliveryIconStyle.svHeart,
    DeliveryIconStyle.hike,
    DeliveryIconStyle.inbox,
    DeliveryIconStyle.insect,
    DeliveryIconStyle.gbIos,
    DeliveryIconStyle.jalopy,
    DeliveryIconStyle.jessy,
    DeliveryIconStyle.joaquins,
    DeliveryIconStyle.jokerTraffic,
    DeliveryIconStyle.letter,
    DeliveryIconStyle.like,
    DeliveryIconStyle.loveSwitch,
    DeliveryIconStyle.materialV2,
    DeliveryIconStyle.materialSwitch,
    DeliveryIconStyle.messenger,
    DeliveryIconStyle.mickeyHands,
    DeliveryIconStyle.minions,
    DeliveryIconStyle.newWaca,
    DeliveryIconStyle.neyHeart,
    DeliveryIconStyle.nhsc,
    DeliveryIconStyle.nazaNh,
    DeliveryIconStyle.nlCircle,
    DeliveryIconStyle.oldWaca,
    DeliveryIconStyle.pacman,
    DeliveryIconStyle.pixelated,
    DeliveryIconStyle.pokemon,
    DeliveryIconStyle.quinn,
    DeliveryIconStyle.snapChat,
    DeliveryIconStyle.sDot,
    DeliveryIconStyle.seasons,
    DeliveryIconStyle.smile,
    DeliveryIconStyle.star,
    DeliveryIconStyle.flatSwitch,
    DeliveryIconStyle.timeBomb,
    DeliveryIconStyle.jokerTraffic2,
    DeliveryIconStyle.treble,
    DeliveryIconStyle.twitterConcept,
    DeliveryIconStyle.wbg,
    DeliveryIconStyle.x,
    DeliveryIconStyle.pointer,
    DeliveryIconStyle.ticTicCircle2,
    DeliveryIconStyle.iceCream,
    DeliveryIconStyle.pony,
    DeliveryIconStyle.kitty,
    DeliveryIconStyle.flower,
    DeliveryIconStyle.sticker,
    DeliveryIconStyle.rcIos11,
    DeliveryIconStyle.bbmV2,
    DeliveryIconStyle.bwTicks,
    DeliveryIconStyle.cirCheck,
    DeliveryIconStyle.circlePrint,
    DeliveryIconStyle.gabCircle,
    DeliveryIconStyle.gabFace,
    DeliveryIconStyle.gabIflo,
    DeliveryIconStyle.greenTick,
    DeliveryIconStyle.ios2,
    DeliveryIconStyle.letterCircle,
    DeliveryIconStyle.rcAlo,
    DeliveryIconStyle.rcTick,
    DeliveryIconStyle.triangle,
    DeliveryIconStyle.vantCircle,
    // Chaty's own styles (not part of the GB catalog).
    DeliveryIconStyle.wings,
    DeliveryIconStyle.trafficLights,
    DeliveryIconStyle.stars,
    DeliveryIconStyle.hearts,
    DeliveryIconStyle.batman,
    DeliveryIconStyle.doubleSword,
    DeliveryIconStyle.glowingDots,
    DeliveryIconStyle.material3Rounded,
  ];

  /// Every bubble geometry Chaty already ships, Stock first. Stored values are
  /// the display names, which BubbleStyleIdExtension.fromString resolves back to the
  /// same id; older saved labels still resolve through its legacy aliases.
  // Exact GB reference picker order (all 59 styles).
  final List<BubbleStyleId> _bubbleStyleIds = <BubbleStyleId>[
    BubbleStyleId.stock,
    BubbleStyleId.facebookMessenger,
    BubbleStyleId.oldHangouts,
    BubbleStyleId.threeD,
    BubbleStyleId.rounded,
    BubbleStyleId.materialized,
    BubbleStyleId.md,
    BubbleStyleId.samyadeepCrayonAlt,
    BubbleStyleId.newHangouts4,
    BubbleStyleId.waPaperRedesigned,
    BubbleStyleId.transparent,
    BubbleStyleId.twitter,
    BubbleStyleId.whatsappLb,
    BubbleStyleId.bbm,
    BubbleStyleId.ios,
    BubbleStyleId.hike,
    BubbleStyleId.textra,
    BubbleStyleId.goSms,
    BubbleStyleId.bubbleDrop,
    BubbleStyleId.samyadeepDual,
    BubbleStyleId.plusMessengerEd,
    BubbleStyleId.waChatOn,
    BubbleStyleId.telegram,
    BubbleStyleId.inBubble,
    BubbleStyleId.bryed,
    BubbleStyleId.fold,
    BubbleStyleId.foldV2,
    BubbleStyleId.ilkhang,
    BubbleStyleId.line,
    BubbleStyleId.popzup,
    BubbleStyleId.doodleHang,
    BubbleStyleId.aranbor,
    BubbleStyleId.win,
    BubbleStyleId.mood,
    BubbleStyleId.crMessenger,
    BubbleStyleId.rcBurbuja2,
    BubbleStyleId.rcBurbuja5,
    BubbleStyleId.threeDV2,
    BubbleStyleId.kitty,
    BubbleStyleId.amor,
    BubbleStyleId.rcSamsBord,
    BubbleStyleId.rcIos11,
    BubbleStyleId.rcGoogleAssistan,
    BubbleStyleId.gabiOutline,
    BubbleStyleId.gabiSqua,
    BubbleStyleId.gabiDot,
    BubbleStyleId.rcLine,
    BubbleStyleId.gabiDot2,
    BubbleStyleId.roundle,
    BubbleStyleId.gabyRon,
    BubbleStyleId.cvIos,
    BubbleStyleId.rcPal,
    BubbleStyleId.rcIosLine,
    BubbleStyleId.rcImLine,
    BubbleStyleId.rcGoSms,
    BubbleStyleId.rcTranLine,
    BubbleStyleId.rcImess,
    BubbleStyleId.gabiMess,
    BubbleStyleId.rcFancy,
  ];

  void _pickColor({
    required String title,
    required int? currentColor,
    required ValueChanged<int?> onColorSelected,
  }) {
    final theme = locator<ThemeController>().globalTheme;
    final initial = currentColor != null
        ? Color(currentColor)
        : theme.accentColor;
    GbColorPickerModal.show(
      context,
      title: title,
      currentColor: initial,
      onColorChanged: (c) {
        onColorSelected(c.value);
      },
    );
  }

  void _setTickStyle(DeliveryIconStyle id) {
    final prefs = widget.preferencesController.conversation;
    // gbIos/gbCircles share display names with legacy Chaty aliases
    // ('iOS' -> RC iOS 11, 'Circles' -> CirCheck). Persist those two by
    // enum name so the new GB styles survive a save/load round trip
    // without stealing the legacy meaning of the saved string.
    final key = (id == DeliveryIconStyle.gbIos ||
            id == DeliveryIconStyle.gbCircles)
        ? id.name
        : id.displayName;
    widget.preferencesController.updateConversation(
      prefs.copyWith(tickStyle: key),
    );
  }

  void _setBubbleStyle(BubbleStyleId id) {
    final prefs = widget.preferencesController.conversation;
    final val = id.displayName;
    // The runtime theme resolves bubble geometry from the legacy
    // `bubble_style` key first. Update that key through the semantic alias
    // so it and the typed ConversationPreferences stay in sync; otherwise
    // an older saved GB value can silently override the selection.
    widget.preferencesController.updateConversation(
      prefs.copyWith(bubbleStyle: val, bubbleShape: val),
    );
    widget.preferencesController.updateGbFeatures(<String, Object?>{
      'bubble_style': val,
    }, logTitle: 'Bubbles Style');
  }

  Widget _buildBubblePreview(BubbleStyleId id, Color color) {
    return SizedBox(
      width: 64,
      child: ChatyBubbleContainer(
        styleId: id,
        isMe: true,
        fillColor: color.withValues(alpha: 0.85),
        accentColor: color,
        child: const Text(
          'Hi',
          style: TextStyle(color: Colors.white, fontSize: 11),
        ),
      ),
    );
  }

  Widget _buildTickPreview(DeliveryIconStyle id, Color color) {
    return SizedBox(
      width: 22,
      height: 16,
      child: CustomPaint(
        painter: DeliveryStatusIconPainter(
          style: id,
          state: DeliveryState.read,
          unreadColor: color,
          readColor: color,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final themeController = locator<ThemeController>();
    return ListenableBuilder(
      listenable: Listenable.merge([
        widget.preferencesController,
        themeController,
      ]),
      builder: (context, _) {
        final theme = themeController.globalTheme;
        final colors = context.colors;
        final prefs = widget.preferencesController.conversation;

        final convBg = prefs.conversationBackgroundColor != null
            ? Color(prefs.conversationBackgroundColor!)
            : colors.surface;
        final rightBubbleBg = prefs.rightBubbleColor != null
            ? Color(prefs.rightBubbleColor!)
            : theme.accentColor;
        final rightTextCol = prefs.rightChatBubbleTextColor != null
            ? Color(prefs.rightChatBubbleTextColor!)
            : Colors.white;
        final rightTimeCol = prefs.rightBubbleTimeColor != null
            ? Color(prefs.rightBubbleTimeColor!)
            : Colors.white.withValues(alpha: 0.7);

        final leftBubbleBg = prefs.leftBubbleColor != null
            ? Color(prefs.leftBubbleColor!)
            : colors.surfaceSecondary;
        final leftTextCol = prefs.leftChatBubbleTextColor != null
            ? Color(prefs.leftChatBubbleTextColor!)
            : theme.primaryTextColor;
        final leftTimeCol = prefs.leftBubbleTimeColor != null
            ? Color(prefs.leftBubbleTimeColor!)
            : theme.secondaryTextColor;

        final quotedBg = prefs.quotedBackgroundColor != null
            ? Color(prefs.quotedBackgroundColor!)
            : colors.surface;
        final quotedDivider = prefs.quotedDividerColor != null
            ? Color(prefs.quotedDividerColor!)
            : theme.accentColor;
        final quotedName = prefs.quotedNameColor != null
            ? Color(prefs.quotedNameColor!)
            : theme.accentColor;
        final quotedMsg = prefs.quotedMessageColor != null
            ? Color(prefs.quotedMessageColor!)
            : theme.secondaryTextColor;

        return Scaffold(
          backgroundColor: theme.backgroundColor,
          appBar: AppBar(
            automaticallyImplyLeading: false,
            backgroundColor: theme.surfaceColor,
            elevation: 0,
            leading: const Padding(
              padding: EdgeInsets.all(8.0),
              child: ChatyBackButton(),
            ),
            title: Text(
              'Bubbles And Ticks',
              style: TextStyle(
                color: theme.primaryTextColor,
                fontSize: 19 * theme.fontScale,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
          body: SafeArea(
            top: false,
            child: Column(
              children: [
                // Top Live Chat Bubbles & Ticks Preview (Pinned, does not scroll away)
                Padding(
                  padding: const EdgeInsets.fromLTRB(14, 10, 14, 4),
                  child: GbLivePreviewCard(
                    padding: const EdgeInsets.all(12),
                    child: Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: convBg,
                        borderRadius: BorderRadius.circular(16),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          // Left Incoming Bubble 1
                          Align(
                            alignment: Alignment.centerLeft,
                            child: Container(
                              margin: const EdgeInsets.only(bottom: 6),
                              padding: const EdgeInsets.symmetric(
                                horizontal: 12,
                                vertical: 8,
                              ),
                              constraints: const BoxConstraints(maxWidth: 240),
                              decoration: BoxDecoration(
                                color: leftBubbleBg,
                                borderRadius: const BorderRadius.only(
                                  topLeft: Radius.circular(16),
                                  topRight: Radius.circular(16),
                                  bottomRight: Radius.circular(16),
                                  bottomLeft: Radius.circular(4),
                                ),
                              ),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    'Tyler Durden',
                                    style: TextStyle(
                                      fontSize: 11,
                                      fontWeight: FontWeight.w700,
                                      color: theme.accentColor,
                                    ),
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    'Hello, I am Tyler Durden',
                                    style: TextStyle(
                                      color: leftTextCol,
                                      fontSize: prefs.messageTextSize
                                          .toDouble(),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),

                          // Left Incoming Bubble 2
                          Align(
                            alignment: Alignment.centerLeft,
                            child: Container(
                              margin: const EdgeInsets.only(bottom: 6),
                              padding: const EdgeInsets.symmetric(
                                horizontal: 12,
                                vertical: 8,
                              ),
                              constraints: const BoxConstraints(maxWidth: 240),
                              decoration: BoxDecoration(
                                color: leftBubbleBg,
                                borderRadius: const BorderRadius.only(
                                  topLeft: Radius.circular(16),
                                  topRight: Radius.circular(16),
                                  bottomRight: Radius.circular(16),
                                  bottomLeft: Radius.circular(4),
                                ),
                              ),
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Flexible(
                                    child: Text(
                                      'Do you like my theme?',
                                      style: TextStyle(
                                        color: leftTextCol,
                                        fontSize: prefs.messageTextSize
                                            .toDouble(),
                                      ),
                                    ),
                                  ),
                                  const SizedBox(width: 8),
                                  Text(
                                    '07:11 PM',
                                    style: TextStyle(
                                      color: leftTimeCol,
                                      fontSize: 10,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),

                          // Right Outgoing Bubble with Quoted Box & Ticks
                          Align(
                            alignment: Alignment.centerRight,
                            child: ConstrainedBox(
                              constraints: const BoxConstraints(maxWidth: 250),
                              child: ChatyBubbleContainer(
                                styleId: BubbleStyleIdExtension.fromString(
                                  prefs.bubbleStyle,
                                ),
                                isMe: true,
                                fillColor: rightBubbleBg,
                                accentColor: theme.accentColor,
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.end,
                                  children: [
                                    // Quoted message snippet
                                    Container(
                                      padding: const EdgeInsets.all(6),
                                      margin: const EdgeInsets.only(bottom: 4),
                                      decoration: BoxDecoration(
                                        color: quotedBg,
                                        borderRadius: BorderRadius.circular(8),
                                        border: Border(
                                          left: BorderSide(
                                            color: quotedDivider,
                                            width: 3.5,
                                          ),
                                        ),
                                      ),
                                      child: Column(
                                        crossAxisAlignment:
                                            CrossAxisAlignment.start,
                                        children: [
                                          Text(
                                            'Tyler Durden',
                                            style: TextStyle(
                                              color: quotedName,
                                              fontSize: 10,
                                              fontWeight: FontWeight.bold,
                                            ),
                                          ),
                                          Text(
                                            'Do you like my theme?',
                                            style: TextStyle(
                                              color: quotedMsg,
                                              fontSize: 11,
                                            ),
                                          ),
                                        ],
                                      ),
                                    ),
                                    Text(
                                      'Yes, your theme looks awesome!',
                                      style: TextStyle(
                                        color: rightTextCol,
                                        fontSize: prefs.messageTextSize
                                            .toDouble(),
                                      ),
                                    ),
                                    const SizedBox(height: 3),
                                    Row(
                                      mainAxisSize: MainAxisSize.min,
                                      children: [
                                        Text(
                                          '07:11 PM',
                                          style: TextStyle(
                                            color: rightTimeCol,
                                            fontSize: 10,
                                          ),
                                        ),
                                        const SizedBox(width: 4),
                                        _buildTickPreview(
                                          DeliveryIconStyleExtension.fromString(
                                            prefs.tickStyle,
                                          ),
                                          rightTimeCol,
                                        ),
                                      ],
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
                // Bottom content scrolls underneath the pinned live preview
                Expanded(
                  child: ListView(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 14,
                      vertical: 8,
                    ),
                    children: [
                      // SECTION 1: Styles (Look and feel)
                      GbCardContainer(
                        headerText: 'Styles (Look and feel)',
                        children: [
                          ChatyOptionGallery<DeliveryIconStyle>(
                            title: 'Ticks style',
                            subtitle:
                                'Tap a style. The preview above updates right away.',
                            values: _tickStyleIds,
                            selected: DeliveryIconStyleExtension.fromString(
                              prefs.tickStyle,
                            ),
                            labelOf: (id) =>
                                DeliveryIconStyleExtension
                                        .gbVectorInterpretations
                                        .contains(id)
                                    ? '${id.displayName} (vector redraw)'
                                    : id.displayName,
                            previewOf: (id) =>
                                _buildTickPreview(id, theme.accentColor),
                            onSelected: _setTickStyle,
                          ),
                          ChatyOptionGallery<BubbleStyleId>(
                            title: 'Bubbles style',
                            subtitle:
                                'Tap a shape to preview it in the conversation.',
                            values: _bubbleStyleIds,
                            selected: BubbleStyleIdExtension.fromString(
                              prefs.bubbleStyle,
                            ),
                            labelOf: (id) =>
                                BubbleStyleIdExtension.gbVectorInterpretations
                                        .contains(id)
                                    ? '${id.displayName} (vector redraw)'
                                    : id.displayName,
                            previewOf: (id) =>
                                _buildBubblePreview(id, theme.accentColor),
                            onSelected: _setBubbleStyle,
                          ),
                          GbSliderTile(
                            title: 'Message Text Size',
                            subtitle: 'Set chat text font size in points',
                            value: prefs.messageTextSize.toDouble(),
                            min: 10,
                            max: 26,
                            divisions: 16,
                            onChanged: (val) {
                              widget.preferencesController.updateConversation(
                                prefs.copyWith(messageTextSize: val.toDouble()),
                              );
                            },
                          ),
                        ],
                      ),
                      const SizedBox(height: 14),

                      // SECTION 2: Colors
                      GbCardContainer(
                        headerText: 'Colors',
                        children: [
                          GbSettingRow(
                            icon: Icons.wallpaper_rounded,
                            title: 'Conversation Background Color',
                            subtitle:
                                'Change background color of conversation, when set to "no wallpaper"',
                            colorValue: prefs.conversationBackgroundColor,
                            onColorTap: () => _pickColor(
                              title: 'Conversation Background Color',
                              currentColor: prefs.conversationBackgroundColor,
                              onColorSelected: (val) {
                                widget.preferencesController.updateConversation(
                                  prefs.copyWith(
                                    conversationBackgroundColor: val,
                                  ),
                                );
                              },
                            ),
                          ),
                          GbSettingRow(
                            icon: Icons.chat_bubble_rounded,
                            title: 'Right Bubble Color',
                            subtitle: 'Change right chat bubble color',
                            colorValue: prefs.rightBubbleColor,
                            onColorTap: () => _pickColor(
                              title: 'Right Bubble Color',
                              currentColor: prefs.rightBubbleColor,
                              onColorSelected: (val) {
                                widget.preferencesController.updateConversation(
                                  prefs.copyWith(rightBubbleColor: val),
                                );
                              },
                            ),
                          ),
                          GbSettingRow(
                            icon: Icons.format_color_text_rounded,
                            title: 'Right Chat Bubble Text Color',
                            subtitle: 'Sets Right chat bubble text color',
                            colorValue: prefs.rightChatBubbleTextColor,
                            onColorTap: () => _pickColor(
                              title: 'Right Bubble Text Color',
                              currentColor: prefs.rightChatBubbleTextColor,
                              onColorSelected: (val) {
                                widget.preferencesController.updateConversation(
                                  prefs.copyWith(rightChatBubbleTextColor: val),
                                );
                              },
                            ),
                          ),
                          GbSettingRow(
                            icon: Icons.access_time_rounded,
                            title: 'Right Bubble time color',
                            subtitle:
                                'Change the color of the time inside the right bubble',
                            colorValue: prefs.rightBubbleTimeColor,
                            onColorTap: () => _pickColor(
                              title: 'Right Bubble Time Color',
                              currentColor: prefs.rightBubbleTimeColor,
                              onColorSelected: (val) {
                                widget.preferencesController.updateConversation(
                                  prefs.copyWith(rightBubbleTimeColor: val),
                                );
                              },
                            ),
                          ),
                          GbSettingRow(
                            icon: Icons.chat_bubble_outline_rounded,
                            title: 'Left Bubble Color',
                            subtitle: 'Change left chat bubble color',
                            colorValue: prefs.leftBubbleColor,
                            onColorTap: () => _pickColor(
                              title: 'Left Bubble Color',
                              currentColor: prefs.leftBubbleColor,
                              onColorSelected: (val) {
                                widget.preferencesController.updateConversation(
                                  prefs.copyWith(leftBubbleColor: val),
                                );
                              },
                            ),
                          ),
                          GbSettingRow(
                            icon: Icons.format_color_text_rounded,
                            title: 'Left Chat Bubble Text Color',
                            subtitle: 'Sets Left chat bubble text color',
                            colorValue: prefs.leftChatBubbleTextColor,
                            onColorTap: () => _pickColor(
                              title: 'Left Bubble Text Color',
                              currentColor: prefs.leftChatBubbleTextColor,
                              onColorSelected: (val) {
                                widget.preferencesController.updateConversation(
                                  prefs.copyWith(leftChatBubbleTextColor: val),
                                );
                              },
                            ),
                          ),
                          GbSettingRow(
                            icon: Icons.access_time_rounded,
                            title: 'Left Bubble time color',
                            subtitle:
                                'Change the color of the time inside the left bubble',
                            colorValue: prefs.leftBubbleTimeColor,
                            onColorTap: () => _pickColor(
                              title: 'Left Bubble Time Color',
                              currentColor: prefs.leftBubbleTimeColor,
                              onColorSelected: (val) {
                                widget.preferencesController.updateConversation(
                                  prefs.copyWith(leftBubbleTimeColor: val),
                                );
                              },
                            ),
                          ),
                          GbSettingRow(
                            icon: Icons.delete_outline_rounded,
                            title: 'Deleted Message Icon Color',
                            subtitle:
                                'Change the color of the revoked icon on deleted messages',
                            colorValue: prefs.deletedMessageIconColor,
                            onColorTap: () => _pickColor(
                              title: 'Deleted Message Icon Color',
                              currentColor: prefs.deletedMessageIconColor,
                              onColorSelected: (val) {
                                widget.preferencesController.updateConversation(
                                  prefs.copyWith(deletedMessageIconColor: val),
                                );
                              },
                            ),
                          ),
                          GbSettingRow(
                            icon: Icons.vertical_split_rounded,
                            title: 'Quoted Divider Color',
                            subtitle: 'Color of the vertical bar beside quote',
                            colorValue: prefs.quotedDividerColor,
                            onColorTap: () => _pickColor(
                              title: 'Quoted Divider Color',
                              currentColor: prefs.quotedDividerColor,
                              onColorSelected: (val) {
                                widget.preferencesController.updateConversation(
                                  prefs.copyWith(quotedDividerColor: val),
                                );
                              },
                            ),
                          ),
                          GbSettingRow(
                            icon: Icons.person_pin_rounded,
                            title: 'Quoted Name Color',
                            subtitle: 'Sender name color inside quoted reply',
                            colorValue: prefs.quotedNameColor,
                            onColorTap: () => _pickColor(
                              title: 'Quoted Name Color',
                              currentColor: prefs.quotedNameColor,
                              onColorSelected: (val) {
                                widget.preferencesController.updateConversation(
                                  prefs.copyWith(quotedNameColor: val),
                                );
                              },
                            ),
                          ),
                          GbSettingRow(
                            icon: Icons.notes_rounded,
                            title: 'Quoted Message Color',
                            subtitle: 'Quoted body text color',
                            colorValue: prefs.quotedMessageColor,
                            onColorTap: () => _pickColor(
                              title: 'Quoted Message Color',
                              currentColor: prefs.quotedMessageColor,
                              onColorSelected: (val) {
                                widget.preferencesController.updateConversation(
                                  prefs.copyWith(quotedMessageColor: val),
                                );
                              },
                            ),
                          ),
                          GbSettingRow(
                            icon: Icons.layers_rounded,
                            title: 'Quoted Background Color',
                            subtitle: 'Background of quote container',
                            colorValue: prefs.quotedBackgroundColor,
                            onColorTap: () => _pickColor(
                              title: 'Quoted Background Color',
                              currentColor: prefs.quotedBackgroundColor,
                              onColorSelected: (val) {
                                widget.preferencesController.updateConversation(
                                  prefs.copyWith(quotedBackgroundColor: val),
                                );
                              },
                            ),
                          ),
                          GbSwitchTile(
                            icon: Icons.select_all_rounded,
                            title: 'Make Text Selectable',
                            subtitle: 'Enable chat bubble text selection',
                            value: prefs.makeTextSelectable,
                            onChanged: (val) {
                              widget.preferencesController.updateConversation(
                                prefs.copyWith(makeTextSelectable: val),
                              );
                            },
                          ),
                          GbSwitchTile(
                            icon: Icons.read_more_rounded,
                            title: 'Remove Read more...',
                            subtitle:
                                'Remove the Read more... button and display full long text',
                            value: prefs.removeReadMore,
                            onChanged: (val) {
                              widget.preferencesController.updateConversation(
                                prefs.copyWith(removeReadMore: val),
                              );
                            },
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}
