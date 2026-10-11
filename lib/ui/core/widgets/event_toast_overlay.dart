import 'dart:async';

import 'package:flutter/material.dart';

import '../../../data/services/notification_service.dart';
import '../controllers/preferences_controller.dart';
import '../theme/app_theme.dart';
import '../notifications/notification_icon_preset.dart';

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
  bool _islandExpanded = false;

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
    if (!widget.preferencesController.notification.enableGlobalNotifications ||
        widget.preferencesController.universal.disableHeadsUpNotification) {
      return;
    }
    _hideTimer?.cancel();
    if (mounted) {
      setState(() {
        _lastNotificationId = latest.id;
        _visible = latest;
        _islandExpanded = false;
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


  void _rescheduleDismiss({required bool isExpanded}) {
    _hideTimer?.cancel();
    final baseSeconds = widget.preferencesController
        .gbInt('event_toast_duration_seconds', fallback: 3)
        .clamp(2, 8);
    final seconds = isExpanded ? 15 : baseSeconds;
    _hideTimer = Timer(Duration(seconds: seconds), () {
      if (mounted) {
        setState(() {
          _visible = null;
          _islandExpanded = false;
        });
      }
    });
  }

  Widget _buildDynamicIsland(
    BuildContext context,
    ChatyEventNotification notification,
    ChatyPreferencesController prefs,
  ) {
    final recent = widget.notificationService.notifications.take(3).toList();
    final expanded = _islandExpanded;
    return GestureDetector(
      onTap: () {
        setState(() => _islandExpanded = !_islandExpanded);
        _rescheduleDismiss(isExpanded: _islandExpanded);
      },
      onLongPress: () {
        if (!_islandExpanded) {
          setState(() => _islandExpanded = true);
          _rescheduleDismiss(isExpanded: true);
        }
      },
      onVerticalDragEnd: (details) {
        if (details.primaryVelocity != null && details.primaryVelocity! < -100) {
          // Swiped up -> collapse
          if (_islandExpanded) {
            setState(() => _islandExpanded = false);
            _rescheduleDismiss(isExpanded: false);
          }
        }
      },
      onHorizontalDragEnd: (details) {
        if (details.primaryVelocity != null && details.primaryVelocity!.abs() > 150) {
          // Swiped sideways -> dismiss
          setState(() {
            _visible = null;
            _islandExpanded = false;
          });
        }
      },
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 360),
        curve: Curves.easeOutCubic,
        width: expanded ? (MediaQuery.sizeOf(context).width - 36).clamp(260.0, 390.0).toDouble() : 174.0,
        constraints: BoxConstraints(maxWidth: MediaQuery.sizeOf(context).width - 28),
        padding: EdgeInsets.symmetric(horizontal: expanded ? 16 : 13, vertical: expanded ? 13 : 9),
        decoration: BoxDecoration(
          color: const Color(0xFF08090B),
          borderRadius: BorderRadius.circular(expanded ? 26 : 32),
          border: Border.all(color: Colors.white.withValues(alpha: 0.12)),
          boxShadow: [
            BoxShadow(color: Colors.black.withValues(alpha: 0.25), blurRadius: 22, offset: const Offset(0, 8)),
          ],
        ),
        child: AnimatedSize(
          duration: const Duration(milliseconds: 320),
          curve: Curves.easeOutCubic,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                children: [
                  Container(
                    width: 27,
                    height: 27,
                    decoration: BoxDecoration(color: notification.color.withValues(alpha: 0.2), shape: BoxShape.circle),
                    child: Icon(notification.icon, color: notification.color, size: 16),
                  ),
                  const SizedBox(width: 9),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(notification.title, maxLines: 1, overflow: TextOverflow.ellipsis,
                          style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700, fontSize: 12)),
                        if (expanded)
                          Padding(
                            padding: const EdgeInsets.only(top: 3),
                            child: Text(widget.concealWhileLocked ? '••••••' : notification.body, maxLines: 2, overflow: TextOverflow.ellipsis,
                              style: TextStyle(color: Colors.white.withValues(alpha: 0.72), fontSize: 12, height: 1.25)),
                          ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 7),
                  Container(width: 5, height: 5, decoration: const BoxDecoration(color: Color(0xFF48D597), shape: BoxShape.circle)),
                ],
              ),
              if (expanded && recent.length > 1) ...[
                const SizedBox(height: 10),
                Divider(height: 1, color: Colors.white.withValues(alpha: 0.12)),
                for (final item in recent.skip(1))
                  Padding(
                    padding: const EdgeInsets.only(top: 9),
                    child: Row(children: [
                      Icon(item.icon, color: item.color, size: 16),
                      const SizedBox(width: 8),
                      Expanded(child: Text(item.title, maxLines: 1, overflow: TextOverflow.ellipsis,
                        style: TextStyle(color: Colors.white.withValues(alpha: 0.9), fontSize: 12, fontWeight: FontWeight.w600))),
                      Expanded(child: Text(item.body, maxLines: 1, overflow: TextOverflow.ellipsis,
                        textAlign: TextAlign.end,
                        style: TextStyle(color: Colors.white.withValues(alpha: 0.55), fontSize: 11))),
                    ]),
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
    final notification = _visible;
    final theme = Theme.of(context);
    // Toast appearance is fully setting-driven and dimension-adaptive: the
    // row hugs its content (no fixed width), height follows the avatar size,
    // padding scales with accessibility text scale, and the max width never
    // exceeds the screen.
    final prefs = widget.preferencesController;
    final notificationIconPreset = ChatyNotificationIconPresets.forName(
      prefs.universal.notificationIcon,
    );
    final avatarSize = prefs
        .gbDouble('toast_avatar_size', fallback: 40)
        .clamp(24.0, 56.0);
    final cornerRadius = prefs
        .gbDouble('toast_corner_radius', fallback: 18)
        .clamp(4.0, 32.0);
    final elevation = prefs
        .gbDouble('toast_elevation', fallback: 10)
        .clamp(0.0, 24.0);
    final backgroundOverride = prefs.gbColor('event_toast_bg');
    final horizontalPad = (12 * MediaQuery.textScalerOf(context).scale(1.0))
        .clamp(10.0, 20.0);
    final maxWidth = (MediaQuery.sizeOf(context).width * 0.9).clamp(
      260.0,
      430.0,
    );
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
                child: notification == null ||
                        prefs.gbBool('dynamic_island_notifications', fallback: false)
                    ? const SizedBox.shrink()
                    : Padding(
                        padding: _padding(context),
                        child: ConstrainedBox(
                          constraints: BoxConstraints(maxWidth: maxWidth),
                          child: Material(
                            elevation: elevation,
                            color: backgroundOverride ?? context.colors.surface,
                            shadowColor: context.colors.shadow,
                            borderRadius: BorderRadius.circular(cornerRadius),
                            child: Container(
                              padding: EdgeInsets.symmetric(
                                horizontal: horizontalPad,
                                vertical: 11,
                              ),
                              decoration: BoxDecoration(
                                borderRadius: BorderRadius.circular(
                                  cornerRadius,
                                ),
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
                                    Stack(
                                      clipBehavior: Clip.none,
                                      children: [
                                        CircleAvatar(
                                          radius: avatarSize / 2,
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
                                        Positioned(
                                          right: -2,
                                          bottom: -2,
                                          child: Container(
                                            width: 19,
                                            height: 19,
                                            decoration: BoxDecoration(
                                              color: notificationIconPreset.color
                                                          .computeLuminance() >
                                                      0.86
                                                  ? context.colors.foreground
                                                  : context.colors.surfaceElevated,
                                              shape: BoxShape.circle,
                                              border: Border.all(
                                                color: context.colors.surface,
                                                width: 1.2,
                                              ),
                                            ),
                                            child: Icon(
                                              notificationIconPreset.icon,
                                              color: notificationIconPreset.color,
                                              size: 11,
                                            ),
                                          ),
                                        ),
                                      ],
                                    ),
                                  ] else
                                    Container(
                                      width: avatarSize,
                                      height: avatarSize,
                                      decoration: BoxDecoration(
                                        color: notificationIconPreset.color
                                                    .computeLuminance() >
                                                0.86
                                            ? context.colors.foreground
                                            : notificationIconPreset.color
                                                .withValues(alpha: 0.14),
                                        shape: BoxShape.circle,
                                      ),
                                      child: Icon(
                                        notificationIconPreset.icon,
                                        color: notificationIconPreset.color,
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
        IgnorePointer(
          ignoring: notification == null ||
              !prefs.notification.enableGlobalNotifications ||
              prefs.universal.disableHeadsUpNotification ||
              !prefs.gbBool('dynamic_island_notifications', fallback: false),
          child: AnimatedAlign(
            alignment: Alignment.topCenter,
            duration: const Duration(milliseconds: 320),
            curve: Curves.easeOutBack,
            child: AnimatedPadding(
              duration: const Duration(milliseconds: 320),
              curve: Curves.easeOutCubic,
              padding: EdgeInsets.only(top: MediaQuery.paddingOf(context).top + 1),
              child: AnimatedOpacity(
                opacity: notification != null &&
                        prefs.notification.enableGlobalNotifications &&
                        !prefs.universal.disableHeadsUpNotification &&
                        prefs.gbBool('dynamic_island_notifications', fallback: false)
                    ? 1
                    : 0,
                duration: const Duration(milliseconds: 180),
                child: notification == null
                    ? const SizedBox.shrink()
                    : _buildDynamicIsland(context, notification, prefs),
              ),
            ),
          ),
        ),
      ],
    );
  }
}
