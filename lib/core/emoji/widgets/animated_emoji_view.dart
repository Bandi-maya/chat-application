import 'package:animated_emoji/animated_emoji.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../chaty_emoji_playback_manager.dart';
import '../chaty_ios_animated_emoji.dart';
import '../chaty_emoji_style.dart';
import '../chaty_motion_emoji.dart';
import '../emoji_resolver.dart';
import '../models/parsed_emoji_span.dart';

/// Production-ready Telegram-quality Animated Emoji View.
/// Handles:
/// - Automatic playback on first message appearance (sent/received)
/// - Prevention of animation storms on list scroll or widget rebuilds
/// - Smooth vector rendering via ChatyMotionEmoji and AnimatedEmoji
/// - Interactive tap-to-replay animation with Telegram-style elastic scale bounce
/// - Automatic fallback to static Unicode if asset is missing or disabled
/// - Respects UniversalPreferences emojiStyle (10 selectable styles)
/// - Semantics and screen-reader accessibility labels
/// - Offscreen lifecycle management and controller disposal
class AnimatedEmojiView extends StatefulWidget {
  final String unicode;
  final double size;
  final bool animate;
  final bool interactive;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;
  final EmojiDisplayMode mode;
  final String? messageId;
  final int emojiIndex;

  const AnimatedEmojiView({
    super.key,
    required this.unicode,
    this.size = 24.0,
    this.animate = true,
    this.interactive = false,
    this.onTap,
    this.onLongPress,
    this.mode = EmojiDisplayMode.inline,
    this.messageId,
    this.emojiIndex = 0,
  });

  @override
  State<AnimatedEmojiView> createState() => _AnimatedEmojiViewState();
}

class _AnimatedEmojiViewState extends State<AnimatedEmojiView>
    with SingleTickerProviderStateMixin {
  late final AnimationController _bounceController;
  late final Animation<double> _scaleAnimation;
  int _replayKey = 0;
  bool _hasStartedAutoPlay = false;

  @override
  void initState() {
    super.initState();
    _bounceController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 360),
    );
    _scaleAnimation = TweenSequence<double>([
      TweenSequenceItem(
        tween: Tween<double>(
          begin: 1.0,
          end: 1.28,
        ).chain(CurveTween(curve: Curves.easeOutBack)),
        weight: 45,
      ),
      TweenSequenceItem(
        tween: Tween<double>(
          begin: 1.28,
          end: 0.92,
        ).chain(CurveTween(curve: Curves.easeInOut)),
        weight: 30,
      ),
      TweenSequenceItem(
        tween: Tween<double>(
          begin: 0.92,
          end: 1.0,
        ).chain(CurveTween(curve: Curves.easeOut)),
        weight: 25,
      ),
    ]).animate(_bounceController);

    _checkAndTriggerAutoPlay();
  }

  void _checkAndTriggerAutoPlay() {
    if (!widget.animate) return;
    final messageId = widget.messageId;
    if (messageId != null && messageId.isNotEmpty) {
      final shouldPlay = ChatyEmojiPlaybackManager.instance.shouldAutoPlay(
        messageId,
        widget.emojiIndex,
      );
      if (shouldPlay && !_hasStartedAutoPlay) {
        _hasStartedAutoPlay = true;
        ChatyEmojiPlaybackManager.instance.notifyAnimationStarted(
          messageId,
          widget.emojiIndex,
        );
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) {
            _bounceController.forward(from: 0.0).then((_) {
              ChatyEmojiPlaybackManager.instance.notifyAnimationCompleted(
                messageId,
                widget.emojiIndex,
              );
            });
          }
        });
      }
    }
  }

  @override
  void didUpdateWidget(covariant AnimatedEmojiView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.messageId != widget.messageId ||
        oldWidget.emojiIndex != widget.emojiIndex) {
      _checkAndTriggerAutoPlay();
    }
  }

  @override
  void dispose() {
    _bounceController.dispose();
    super.dispose();
  }

  void _handleTap() {
    if (widget.interactive) {
      HapticFeedback.lightImpact();
      _bounceController.forward(from: 0.0);
      setState(() => _replayKey++);
    }
    widget.onTap?.call();
  }

  @override
  Widget build(BuildContext context) {
    // Determine active emoji style from preferences
    ChatyEmojiStyleId currentStyle = ChatyEmojiStyleId.chatyMotion;
    if (locator.isRegistered<ChatyPreferencesController>()) {
      final prefs = locator<ChatyPreferencesController>();
      currentStyle = ChatyEmojiStyleId.fromString(prefs.universal.emojiStyle);
    }

    Widget content;

    if (currentStyle == ChatyEmojiStyleId.iosAnimated &&
        ChatyIosAnimatedEmoji.isSupported(widget.unicode)) {
      content = IosAnimatedEmojiView(
        key: ValueKey('iosanim_${widget.unicode}_$_replayKey'),
        unicode: widget.unicode,
        size: widget.size,
        animate: widget.animate,
      );
    } else if (ChatyMotionEmoji.isSupported(widget.unicode)) {
      final shouldPlay = widget.messageId != null
          ? ChatyEmojiPlaybackManager.instance.shouldAutoPlay(
              widget.messageId!,
              widget.emojiIndex,
            )
          : true;

      content = ChatyMotionEmoji(
        key: ValueKey('motion_${widget.unicode}_${currentStyle.name}_$_replayKey'),
        unicode: widget.unicode,
        size: widget.size,
        animate: widget.animate && currentStyle.isAnimated,
        autoStart: shouldPlay,
        style: currentStyle,
        onCompleted: () {
          if (widget.messageId != null) {
            ChatyEmojiPlaybackManager.instance.notifyAnimationCompleted(
              widget.messageId!,
              widget.emojiIndex,
            );
          }
        },
      );
    } else {
      final animatedData = ChatyEmojiResolver.resolve(
        widget.unicode,
        enabled: widget.animate && currentStyle.isAnimated,
        context: context,
      );

      if (animatedData != null) {
        content = AnimatedEmoji(
          animatedData,
          key: ValueKey('${animatedData.id}_$_replayKey'),
          size: widget.size,
          repeat: widget.mode == EmojiDisplayMode.inline ||
              widget.mode == EmojiDisplayMode.picker,
        );
      } else {
        content = Text(
          widget.unicode,
          style: TextStyle(fontSize: widget.size * 0.82, height: 1.0),
        );
      }
    }

    Widget body = Semantics(
      label: 'Emoji ${widget.unicode}',
      child: SizedBox(
        width: widget.size,
        height: widget.size,
        child: Center(child: content),
      ),
    );

    if (widget.interactive ||
        widget.onTap != null ||
        widget.onLongPress != null) {
      body = GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: _handleTap,
        onLongPress: widget.onLongPress,
        child: ScaleTransition(scale: _scaleAnimation, child: body),
      );
    }

    return RepaintBoundary(child: body);
  }
}
