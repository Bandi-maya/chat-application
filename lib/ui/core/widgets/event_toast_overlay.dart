import 'dart:async';

import 'package:flutter/material.dart';

import '../../../data/services/notification_service.dart';
import '../controllers/preferences_controller.dart';
import '../theme/app_theme.dart';

class ChatyEventToastOverlay extends StatefulWidget {
  final ChatyNotificationService notificationService;
  final ChatyPreferencesController preferencesController;
  final Widget child;

  /// Real consumer of `security.hideLockNotificationContent`: while the app
  /// lock gate is showing, the toast body (message detail/preview text) is
  /// replaced by dots so nothing readable leaks through the translucent
  /// barrier. Titles stay visible so the user still knows what happened.
  final bool concealWhileLocked;

  const ChatyEventToastOverlay({
    super.key,
    required this.notificationService,
    required this.preferencesController,
    required this.child,
    this.concealWhileLocked = false,
  });

  @override
  State<ChatyEventToastOverlay> createState() => _ChatyEventToastOverlayState();
}

class _ChatyEventToastOverlayState extends State<ChatyEventToastOverlay> {
  ChatyEventNotification? _visible;
  String? _lastNotificationId;
  Timer? _hideTimer;

  @override
  void initState() {
    super.initState();
    widget.notificationService.addListener(_onNotificationChanged);
  }

  @override
  void didUpdateWidget(covariant ChatyEventToastOverlay oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.notificationService != widget.notificationService) {
      oldWidget.notificationService.removeListener(_onNotificationChanged);
      widget.notificationService.addListener(_onNotificationChanged);
    }
  }

  void _onNotificationChanged() {
    final latest = widget.notificationService.latest;
    if (latest == null || latest.id == _lastNotificationId) return;
    if (!widget.preferencesController.notification.enableGlobalNotifications)
      return;
    _hideTimer?.cancel();
    if (mounted) {
      setState(() {
        _lastNotificationId = latest.id;
        _visible = latest;
      });
    }
    final seconds = widget.preferencesController
        .gbInt('event_toast_duration_seconds', fallback: 3)
        .clamp(2, 8);
    _hideTimer = Timer(Duration(seconds: seconds), () {
      if (mounted && _visible?.id == latest.id) setState(() => _visible = null);
    });
  }

  Alignment _alignment() {
    switch (widget.preferencesController
        .gbString('event_toast_position', fallback: 'Top')
        .toLowerCase()) {
      case 'center':
        return Alignment.center;
      case 'bottom':
        return Alignment.bottomCenter;
      default:
        return Alignment.topCenter;
    }
  }

  EdgeInsets _padding(BuildContext context) {
    final position = widget.preferencesController
        .gbString('event_toast_position', fallback: 'Top')
        .toLowerCase();
    final safe = MediaQuery.paddingOf(context);
    if (position == 'bottom')
      return EdgeInsets.fromLTRB(14, 12, 14, safe.bottom + 92);
    if (position == 'center')
      return const EdgeInsets.symmetric(horizontal: 20, vertical: 20);
    return EdgeInsets.fromLTRB(14, safe.top + 10, 14, 12);
  }

  Color _avatarColor(ChatyEventNotification notification) {
    final raw = notification.avatarColorHex;
    if (raw == null || raw.isEmpty) return notification.color;
    final normalized = raw.replaceFirst('0x', '').replaceFirst('#', '');
    final parsed = int.tryParse(normalized, radix: 16);
    if (parsed == null) return notification.color;
    return Color(normalized.length <= 6 ? 0xFF000000 | parsed : parsed);
  }

  @override
  void dispose() {
    _hideTimer?.cancel();
    widget.notificationService.removeListener(_onNotificationChanged);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final notification = _visible;
    final theme = Theme.of(context);
    return Stack(
      fit: StackFit.expand,
      children: [
        widget.child,
        IgnorePointer(
          ignoring: notification == null,
          child: AnimatedAlign(
            alignment: _alignment(),
            duration: const Duration(milliseconds: 180),
            curve: Curves.easeOutCubic,
            child: AnimatedOpacity(
              opacity: notification == null ? 0 : 1,
              duration: const Duration(milliseconds: 160),
              child: AnimatedSlide(
                offset: notification == null
                    ? const Offset(0, -0.08)
                    : Offset.zero,
                duration: const Duration(milliseconds: 180),
                curve: Curves.easeOutCubic,
                child: notification == null
                    ? const SizedBox.shrink()
                    : Padding(
                        padding: _padding(context),
                        child: ConstrainedBox(
                          constraints: const BoxConstraints(maxWidth: 430),
                          child: Material(
                            elevation: 10,
                            color: context.colors.surface,
                            shadowColor: context.colors.shadow,
                            borderRadius: BorderRadius.circular(18),
                            child: Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 12,
                                vertical: 11,
                              ),
                              decoration: BoxDecoration(
                                borderRadius: BorderRadius.circular(18),
                                border: Border.all(
                                  color: context.colors.borderSubtle,
                                ),
                              ),
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  if (widget
                                          .preferencesController
                                          .notification
                                          .showSenderAvatar &&
                                      notification.avatarInitials != null) ...[
                                    CircleAvatar(
                                      radius: 20,
                                      backgroundColor: _avatarColor(
                                        notification,
                                      ),
                                      foregroundColor: context.colors.onPrimary,
                                      child: Text(
                                        notification.avatarInitials!,
                                        style: const TextStyle(
                                          fontWeight: FontWeight.w800,
                                          fontSize: 12,
                                        ),
                                      ),
                                    ),
                                  ] else
                                    Container(
                                      width: 40,
                                      height: 40,
                                      decoration: BoxDecoration(
                                        color: notification.color.withValues(
                                          alpha: 0.14,
                                        ),
                                        shape: BoxShape.circle,
                                      ),
                                      child: Icon(
                                        notification.icon,
                                        color: notification.color,
                                        size: 21,
                                      ),
                                    ),
                                  const SizedBox(width: 11),
                                  Flexible(
                                    child: Column(
                                      mainAxisSize: MainAxisSize.min,
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                        Text(
                                          widget
                                                  .preferencesController
                                                  .notification
                                                  .showSenderName
                                              ? notification.title
                                              : 'Chaty',
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                          style: theme.textTheme.titleSmall
                                              ?.copyWith(
                                                fontWeight: FontWeight.w800,
                                                // GB toast `_tc` text tint.
                                                color: notification.textColor,
                                              ),
                                        ),
                                        if (widget
                                            .preferencesController
                                            .notification
                                            .showMessagePreview) ...[
                                          const SizedBox(height: 2),
                                          Text(
                                            widget.concealWhileLocked
                                                ? '••••••'
                                                : notification.body,
                                            maxLines: 2,
                                            overflow: TextOverflow.ellipsis,
                                            style: theme.textTheme.bodySmall
                                                ?.copyWith(
                                                  color:
                                                      notification.textColor ??
                                                      theme
                                                          .colorScheme
                                                          .onSurfaceVariant,
                                                ),
                                          ),
                                        ],
                                      ],
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ),
                      ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}
