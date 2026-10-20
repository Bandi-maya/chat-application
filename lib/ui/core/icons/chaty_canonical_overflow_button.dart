import 'package:flutter/material.dart';
import '../design_system/chaty_haptics.dart';
import 'chaty_icon_registry.dart';
import 'chaty_icon_theme.dart';

/// The canonical Chaty three-dot overflow button.
/// Shared by Home Screen and Chat Detail Screen for unified visual appearance,
/// micro-interactions, theme adaptation, and accessible touch targets.
class ChatyCanonicalOverflowButton extends StatefulWidget {
  final VoidCallback onPressed;
  final Color? color;
  final double size;
  final String tooltip;
  final ChatyIconThemeId? themeOverride;

  const ChatyCanonicalOverflowButton({
    super.key,
    required this.onPressed,
    this.color,
    this.size = 24.0,
    this.tooltip = 'More options',
    this.themeOverride,
  });

  @override
  State<ChatyCanonicalOverflowButton> createState() =>
      _ChatyCanonicalOverflowButtonState();
}

class _ChatyCanonicalOverflowButtonState
    extends State<ChatyCanonicalOverflowButton>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<double> _scaleAnimation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 160),
    );
    _scaleAnimation = Tween<double>(begin: 1.0, end: 0.92).animate(
      CurvedAnimation(
        parent: _controller,
        curve: const Cubic(0.23, 1.0, 0.32, 1.0),
      ),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _onTapDown(TapDownDetails _) {
    _controller.forward();
  }

  void _onTapUp(TapUpDetails _) {
    ChatyHaptics.light();
    _controller.reverse();
    widget.onPressed();
  }

  void _onTapCancel() {
    _controller.reverse();
  }

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      label: widget.tooltip,
      child: Tooltip(
        message: widget.tooltip,
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTapDown: _onTapDown,
          onTapUp: _onTapUp,
          onTapCancel: _onTapCancel,
          child: SizedBox.square(
            dimension: 44.0, // WCAG accessible touch target
            child: Center(
              child: ScaleTransition(
                scale: _scaleAnimation,
                child: ChatyIcon(
                  ChatyIconId.more,
                  size: widget.size,
                  color: widget.color,
                  themeOverride: widget.themeOverride,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
