import 'package:flutter/material.dart';

/// ---------------------------------------------------------------------------
/// SHARED DIALOGS / SHEETS / FEEDBACK — replaces the per-screen copies.
/// ---------------------------------------------------------------------------

/// Standard confirm dialog. Returns true only when the user confirms.
/// Every screen previously hand-rolled this exact AlertDialog.
class ChatyConfirmDialog {
  static Future<bool> show(
    BuildContext context, {
    required String title,
    required String message,
    String confirmLabel = 'Confirm',
    String cancelLabel = 'Cancel',
    bool destructive = false,
    bool barrierDismissible = true,
  }) async {
    final confirmed = await showDialog<bool>(
      context: context,
      barrierDismissible: barrierDismissible,
      builder: (dialogContext) => AlertDialog(
        title: Text(title),
        content: Text(message),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: Text(cancelLabel),
          ),
          FilledButton(
            style: destructive
                ? FilledButton.styleFrom(
                    backgroundColor: Theme.of(context).colorScheme.error,
                    foregroundColor: Colors.white,
                  )
                : null,
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: Text(confirmLabel),
          ),
        ],
      ),
    );
    return confirmed == true;
  }
}

/// Uniform snackbar feedback.
class ChatyToast {
  static void show(BuildContext context, String message, {Color? background}) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(message),
          backgroundColor: background,
          duration: const Duration(seconds: 2),
        ),
      );
  }
}

/// Centered icon + title + message, used for every empty screen.
class ChatyEmptyState extends StatelessWidget {
  final IconData icon;
  final String title;
  final String message;
  final Color? iconColor;
  final Color? titleColor;
  final Color? messageColor;

  const ChatyEmptyState({
    super.key,
    required this.icon,
    required this.title,
    required this.message,
    this.iconColor,
    this.titleColor,
    this.messageColor,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              icon,
              size: 46,
              color:
                  iconColor ??
                  theme.colorScheme.onSurface.withValues(alpha: 0.35),
            ),
            const SizedBox(height: 14),
            Text(
              title,
              textAlign: TextAlign.center,
              style: TextStyle(
                color: titleColor ?? theme.colorScheme.onSurface,
                fontSize: 17,
                fontWeight: FontWeight.w800,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              message,
              textAlign: TextAlign.center,
              style: TextStyle(
                color:
                    messageColor ??
                    theme.colorScheme.onSurface.withValues(alpha: 0.6),
                fontSize: 13,
                height: 1.35,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// One row inside [ChatyMenuSheet].
class ChatyMenuItem {
  final IconData icon;
  final String label;
  final bool destructive;
  final VoidCallback onTap;

  const ChatyMenuItem({
    required this.icon,
    required this.label,
    required this.onTap,
    this.destructive = false,
  });
}

/// iOS-style bottom overflow menu: titled surface, icon rows, destructive
/// tinting. Rows auto-pop the sheet BEFORE running their callback.
class ChatyMenuSheet {
  static Future<void> show(
    BuildContext context, {
    required String title,
    required List<ChatyMenuItem> items,
    Color? surfaceColor,
    Color? textColor,
    Color? accentColor,
    Color? dangerColor,
  }) {
    final theme = Theme.of(context);
    final surface = surfaceColor ?? theme.colorScheme.surface;
    final ink = textColor ?? theme.colorScheme.onSurface;
    final accent = accentColor ?? theme.colorScheme.primary;
    final danger = dangerColor ?? theme.colorScheme.error;
    return showModalBottomSheet<void>(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (sheetContext) => SafeArea(
        child: Container(
          decoration: BoxDecoration(
            color: surface,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(18)),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const SizedBox(height: 14),
              Text(
                title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  color: ink,
                  fontWeight: FontWeight.w800,
                  fontSize: 15,
                ),
              ),
              const SizedBox(height: 6),
              for (final item in items)
                ListTile(
                  leading: Icon(
                    item.icon,
                    size: 21,
                    color: item.destructive ? danger : accent,
                  ),
                  title: Text(
                    item.label,
                    style: TextStyle(
                      color: item.destructive ? danger : ink,
                      fontSize: 14.5,
                    ),
                  ),
                  onTap: () {
                    Navigator.of(sheetContext).pop();
                    item.onTap();
                  },
                ),
              const SizedBox(height: 10),
            ],
          ),
        ),
      ),
    );
  }
}

/// ---------------------------------------------------------------------------
/// CHATY KIT — the single source of truth for WhatsApp-iOS component chrome.
///
/// Every screen renders avatars, presence dots, unread badges, section
/// headers and time labels through these primitives so proportions and
/// behavior are IDENTICAL everywhere. Colors stay theme-driven by design;
/// only geometry, weight and press behavior live here.
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

  const ChatyInsetDivider({super.key, required this.color, this.indent = 66});

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 0.6,
      margin: EdgeInsets.only(left: indent),
      color: color.withValues(alpha: 0.12),
    );
  }
}

/// ---------------------------------------------------------------------------
/// iOS-style swipe actions for list rows.
///
/// Dragging the row left reveals trailing actions that stretch as the reveal
/// grows (icons first, then labels — the iOS list behavior). Snaps open or
/// closed with a threshold + velocity check; taps pass through to the row
/// only when fully closed.
/// ---------------------------------------------------------------------------
class ChatySwipeAction {
  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback onTriggered;

  const ChatySwipeAction({
    required this.icon,
    required this.label,
    required this.color,
    required this.onTriggered,
  });
}

class ChatySwipeActions extends StatefulWidget {
  final Widget child;
  final List<ChatySwipeAction> actions;
  final double actionExtent;

  /// Opaque backdrop painted behind the ROW CONTENT. Rows with transparent
  /// backgrounds MUST pass the list's background color here, otherwise the
  /// action layer bleeds through before any swipe.
  final Color? backgroundColor;

  const ChatySwipeActions({
    super.key,
    required this.child,
    required this.actions,
    this.actionExtent = 74,
    this.backgroundColor,
  });

  @override
  State<ChatySwipeActions> createState() => _ChatySwipeActionsState();
}

class _ChatySwipeActionsState extends State<ChatySwipeActions>
    with SingleTickerProviderStateMixin {
  late final AnimationController _snap = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 220),
    value: 1.0,
  );
  double _dragOffset = 0;
  double get _maxDrag => widget.actionExtent * widget.actions.length;

  void _settle(double target) {
    final start = _dragOffset;
    void listener() {
      final t = Curves.easeOutCubic.transform(_snap.value);
      setState(() => _dragOffset = start + (target - start) * (1 - t));
    }

    _snap
      ..reset()
      ..addListener(listener)
      ..forward().whenComplete(() => _snap.removeListener(listener));
  }

  @override
  void dispose() {
    _snap.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.translucent,
      onHorizontalDragStart: (_) => _snap.stop(),
      onHorizontalDragUpdate: (details) {
        setState(() {
          _dragOffset = (_dragOffset - details.delta.dx).clamp(0.0, _maxDrag);
        });
      },
      onHorizontalDragEnd: (details) {
        final velocity = -(details.primaryVelocity ?? 0.0);
        final open =
            _dragOffset > _maxDrag / 2 || (velocity > 420 && _dragOffset > 12);
        _settle(open ? _maxDrag : 0.0);
      },
      child: Stack(
        fit: StackFit.passthrough,
        children: [
          // Action layer behind the row.
          Positioned.fill(
            child: Row(
              children: [
                const Spacer(),
                for (final action in widget.actions)
                  Expanded(
                    flex: (_dragOffset / widget.actionExtent)
                        .clamp(0.6, 1.4)
                        .toInt(),
                    child: Material(
                      color: action.color,
                      child: InkWell(
                        onTap: _dragOffset > 6
                            ? () {
                                action.onTriggered();
                                _settle(0.0);
                              }
                            : null,
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(action.icon, size: 21, color: Colors.white),
                            if (_dragOffset > _maxDrag * 0.72) ...[
                              const SizedBox(height: 4),
                              Text(
                                action.label,
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 10.5,
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                            ],
                          ],
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),
          // The row itself slides left to reveal the actions. The opaque
          // backdrop guarantees the action layer stays invisible until an
          // actual drag, even for fully transparent row content.
          Transform.translate(
            offset: Offset(-_dragOffset, 0),
            child: ColoredBox(
              color:
                  widget.backgroundColor ??
                  Theme.of(context).scaffoldBackgroundColor,
              child: AbsorbPointer(
                absorbing: _dragOffset > 4,
                child: widget.child,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
