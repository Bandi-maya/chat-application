import 'package:flutter/material.dart';

import '../../theme/app_theme.dart';
import '../chaty_haptics.dart';
import '../chaty_motion.dart';

class ChatyComposerActionButton extends StatefulWidget {
  const ChatyComposerActionButton({
    super.key,
    required this.theme,
    required this.semanticsLabel,
    this.tooltip,
    this.icon,
    this.iconColor,
    this.fillColor,
    this.onTap,
    this.onLongPressStart,
    this.onLongPressMoveUpdate,
    this.onLongPressEnd,
    this.busy = false,
    this.emphasized = false,
  });

  final ThemeConfig theme;
  final String semanticsLabel;
  final String? tooltip;
  final IconData? icon;
  final Color? iconColor;
  final Color? fillColor;
  final VoidCallback? onTap;
  final GestureLongPressStartCallback? onLongPressStart;
  final GestureLongPressMoveUpdateCallback? onLongPressMoveUpdate;
  final GestureLongPressEndCallback? onLongPressEnd;
  final bool busy;
  final bool emphasized;

  @override
  State<ChatyComposerActionButton> createState() =>
      _ChatyComposerActionButtonState();
}

class _ChatyComposerActionButtonState
    extends State<ChatyComposerActionButton> {
  bool _pressed = false;

  bool get _enabled =>
      !widget.busy &&
      (widget.onTap != null || widget.onLongPressStart != null);

  void _setPressed(bool value) {
    if (_pressed == value || !mounted) return;
    setState(() => _pressed = value);
  }

  void _handleTap() {
    if (widget.emphasized) ChatyHaptics.success();
    widget.onTap?.call();
  }

  @override
  Widget build(BuildContext context) {
    final duration = ChatyMotion.duration(
      context,
      preferred: ChatyMotion.fast,
    );
    final content = SizedBox.square(
      dimension: 48,
      child: DecoratedBox(
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          color: widget.fillColor ?? Colors.transparent,
        ),
        child: widget.busy
            ? Padding(
                padding: const EdgeInsets.all(13),
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  color: widget.iconColor,
                ),
              )
            : Icon(widget.icon, size: 22, color: widget.iconColor),
      ),
    );

    Widget button = GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: !_enabled || widget.onTap == null ? null : _handleTap,
      onLongPressStart: _enabled ? widget.onLongPressStart : null,
      onLongPressMoveUpdate: _enabled ? widget.onLongPressMoveUpdate : null,
      onLongPressEnd: _enabled ? widget.onLongPressEnd : null,
      onTapDown: _enabled ? (_) => _setPressed(true) : null,
      onTapCancel: _enabled ? () => _setPressed(false) : null,
      onTapUp: _enabled ? (_) => _setPressed(false) : null,
      child: AnimatedScale(
        scale: _pressed ? 0.94 : 1,
        duration: duration,
        curve: ChatyMotion.enter,
        child: AnimatedOpacity(
          duration: duration,
          opacity: !_enabled ? 0.45 : (_pressed ? 0.85 : 1),
          child: content,
        ),
      ),
    );

    button = Semantics(
      button: true,
      enabled: _enabled,
      label: widget.semanticsLabel,
      child: button,
    );
    if (widget.tooltip != null) {
      button = Tooltip(
        message: widget.tooltip!,
        excludeFromSemantics: true,
        child: button,
      );
    }
    return button;
  }
}

class ChatyVoiceLevelMeter extends StatelessWidget {
  const ChatyVoiceLevelMeter({
    super.key,
    required this.levels,
    required this.theme,
  });

  final List<double> levels;
  final ThemeConfig theme;

  @override
  Widget build(BuildContext context) {
    final duration = ChatyMotion.duration(
      context,
      preferred: ChatyMotion.instant,
    );
    return Semantics(
      label: 'Live voice level',
      readOnly: true,
      child: ExcludeSemantics(
        child: SizedBox(
          height: 26,
          child: Row(
            children: [
              for (final rawLevel in levels)
                Expanded(
                  child: Center(
                    child: AnimatedContainer(
                      duration: duration,
                      curve: ChatyMotion.enter,
                      width: 3,
                      height: 4 + 20 * rawLevel.clamp(0, 1),
                      decoration: BoxDecoration(
                        color: theme.accentColor.withValues(
                          alpha: 0.45 + 0.55 * rawLevel.clamp(0, 1),
                        ),
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
