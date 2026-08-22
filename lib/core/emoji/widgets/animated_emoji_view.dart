import 'package:animated_emoji/animated_emoji.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import '../emoji_resolver.dart';
import '../models/parsed_emoji_span.dart';

/// Production-ready Telegram-quality Animated Emoji View.
/// Handles:
/// - Smooth vector rendering
/// - Interactive tap-to-replay animation with Telegram-style elastic scale bounce
/// - Automatic fallback to static Unicode if asset is missing or disabled
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

  const AnimatedEmojiView({
    super.key,
    required this.unicode,
    this.size = 24.0,
    this.animate = true,
    this.interactive = false,
    this.onTap,
    this.onLongPress,
    this.mode = EmojiDisplayMode.inline,
  });

  @override
  State<AnimatedEmojiView> createState() => _AnimatedEmojiViewState();
}

class _AnimatedEmojiViewState extends State<AnimatedEmojiView>
    with SingleTickerProviderStateMixin {
  late final AnimationController _bounceController;
  late final Animation<double> _scaleAnimation;
  int _replayKey = 0;

  @override
  void initState() {
    super.initState();
    _bounceController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 320),
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
    final animatedData = ChatyEmojiResolver.resolve(
      widget.unicode,
      enabled: widget.animate,
      context: context,
    );

    Widget content;
    if (animatedData != null) {
      content = AnimatedEmoji(
        animatedData,
        key: ValueKey('${animatedData.id}_$_replayKey'),
        size: widget.size,
        repeat: widget.mode == EmojiDisplayMode.reaction ? false : true,
      );
    } else {
      content = Text(
        widget.unicode,
        style: TextStyle(fontSize: widget.size * 0.82, height: 1.0),
      );
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

    return body;
  }
}
