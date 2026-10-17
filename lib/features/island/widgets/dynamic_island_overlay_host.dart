import 'dart:math' as math;
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/physics.dart';
import 'package:flutter/services.dart';
import '../models/dynamic_island_payload.dart';
import '../controllers/dynamic_island_controller.dart';
import 'blur_fade_switcher.dart';
import 'cutout_anchor_resolver.dart';
import 'dynamic_island_views.dart';

/// Top-level floating Dynamic Island overlay host replicating the Framer Motion
/// spring-morphing behavior from nithinpjohn/Dynamic-island-notifications pixel-for-pixel.
class DynamicIslandOverlayHost extends StatefulWidget {
  final DynamicIslandController controller;
  final Widget? child;
  final bool enableBlur;

  const DynamicIslandOverlayHost({
    super.key,
    required this.controller,
    this.child,
    this.enableBlur = true,
  });

  @override
  State<DynamicIslandOverlayHost> createState() => _DynamicIslandOverlayHostState();
}

class _DynamicIslandOverlayHostState extends State<DynamicIslandOverlayHost>
    with SingleTickerProviderStateMixin {
  late final AnimationController _springController;

  // Spring physics constants matching reference repo
  // SPRING = { type: "spring", stiffness: 380, damping: 36, mass: 0.9 }
  static const SpringDescription _springDesc = SpringDescription(
    mass: 0.9,
    stiffness: 380.0,
    damping: 36.0,
  );

  IslandMode _lastMode = IslandMode.idle;

  @override
  void initState() {
    super.initState();
    _springController = AnimationController.unbounded(vsync: this);
    widget.controller.addListener(_onControllerChanged);
    _lastMode = widget.controller.mode;
    _springController.value = _modeToTarget(_lastMode);
  }

  @override
  void didUpdateWidget(covariant DynamicIslandOverlayHost oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_onControllerChanged);
      widget.controller.addListener(_onControllerChanged);
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(_onControllerChanged);
    _springController.dispose();
    super.dispose();
  }

  void _onControllerChanged() {
    final newMode = widget.controller.mode;
    if (newMode != _lastMode) {
      _lastMode = newMode;
      _animateToTarget(_modeToTarget(newMode));
    } else {
      setState(() {});
    }
  }

  double _modeToTarget(IslandMode mode) {
    switch (mode) {
      case IslandMode.idle:
        return 0.0;
      case IslandMode.compact:
        return 1.0;
      case IslandMode.split:
        return 2.0;
      case IslandMode.expanded:
        return 3.0;
    }
  }

  void _animateToTarget(double target) {
    final current = _springController.value;
    final simulation = SpringSimulation(
      _springDesc,
      current,
      target,
      _springController.velocity,
    );
    _springController.animateWith(simulation);
  }

  @override
  Widget build(BuildContext context) {
    final content = widget.child ?? const SizedBox.shrink();

    return ListenableBuilder(
      listenable: Listenable.merge([widget.controller, _springController]),
      builder: (context, _) {
        final mode = widget.controller.mode;
        final activePayload = widget.controller.activePayload;
        final secondaryPayload = widget.controller.secondaryPayload;

        final isVisible = !(mode == IslandMode.idle && _springController.value <= 0.01);

        final anchor = CutoutAnchorResolver.resolve(context);
        final screenWidth = MediaQuery.of(context).size.width;

        final isPlayer = activePayload?.type == IslandPayloadType.media ||
            activePayload?.type == IslandPayloadType.voiceNotePlayback;
        final isCall = activePayload?.type == IslandPayloadType.incomingCall ||
            activePayload?.type == IslandPayloadType.ongoingCall;

        // Bounded compact dimensions: camera-aware and unobtrusive
        final expandedWidth = isPlayer ? 340.0 : 356.0;
        final compactWidth = isPlayer ? 92.0 : (isCall ? 96.0 : 84.0);

        // Progress between 0.0 (idle), 1.0 (compact), 2.0 (split), 3.0 (expanded)
        final t = _springController.value.clamp(0.0, 3.0);

        double width;
        double radius;
        double opacity;

        if (t <= 1.0) {
          // idle -> compact
          width = lerpDouble(32.0, compactWidth, t) ?? compactWidth;
          radius = lerpDouble(16.0, 999.0, t) ?? 999.0;
          opacity = lerpDouble(0.0, 1.0, t) ?? 1.0;
        } else if (t <= 2.0) {
          // compact -> split
          final splitT = t - 1.0;
          width = lerpDouble(compactWidth, 104.0, splitT) ?? 104.0;
          radius = 999.0;
          opacity = 1.0;
        } else {
          // split / compact -> expanded
          final expandT = (t - 2.0).clamp(0.0, 1.0);
          final startW = (mode == IslandMode.split) ? 104.0 : compactWidth;
          width = lerpDouble(startW, expandedWidth, expandT) ?? expandedWidth;
          radius = lerpDouble(999.0, 28.0, expandT) ?? 28.0;
          opacity = 1.0;
        }

        // Keep width bounded by screen padding
        width = math.min(width, screenWidth - 24.0);

        // Content target width to ensure child layouts are never compressed
        // during spring interpolation
        final targetContentWidth = mode == IslandMode.expanded
            ? expandedWidth
            : (mode == IslandMode.idle ? 32.0 : (mode == IslandMode.split ? 104.0 : compactWidth));

        final isHighContrast = MediaQuery.of(context).highContrast;

        return Stack(
          fit: StackFit.expand,
          children: [
            content,

            if (isVisible) ...[
              // Full-screen outside-tap dismiss barrier when expanded
              if (mode == IslandMode.expanded)
                Positioned.fill(
                  child: GestureDetector(
                    behavior: HitTestBehavior.translucent,
                    onTap: () {
                      widget.controller.collapse();
                    },
                  ),
                ),

              // Floating Dynamic Island capsule
            Positioned(
              top: anchor.topOffset,
              left: 0,
              right: 0,
              child: ConstrainedBox(
                constraints: BoxConstraints(
                  maxHeight: math.max(100.0, MediaQuery.of(context).size.height - anchor.topOffset),
                ),
                child: Align(
                  alignment: anchor.alignment,
                  child: Opacity(
                    opacity: opacity.clamp(0.0, 1.0),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                      // Primary capsule
                      GestureDetector(
                        onTap: () {
                          if (mode == IslandMode.compact || mode == IslandMode.split) {
                            widget.controller.expand();
                          } else if (mode == IslandMode.expanded) {
                            widget.controller.collapse();
                          }
                        },
                        onLongPress: () {
                          HapticFeedback.mediumImpact();
                          widget.controller.expand();
                        },
                        onVerticalDragEnd: (details) {
                          if (details.primaryVelocity != null &&
                              details.primaryVelocity! < -100) {
                            // Swiped up -> collapse
                            widget.controller.collapse();
                          }
                        },
                        onHorizontalDragEnd: (details) {
                          if (details.primaryVelocity != null &&
                              details.primaryVelocity!.abs() > 140) {
                            if (mode == IslandMode.expanded && widget.controller.queue.length > 1) {
                              // In expanded mode: horizontal swipe navigates Multi-Task Carousel (OriginOS feature)
                              HapticFeedback.lightImpact();
                              if (details.primaryVelocity! < 0) {
                                widget.controller.nextCarouselItem();
                              } else {
                                widget.controller.prevCarouselItem();
                              }
                            } else {
                              // Swiped left/right in compact -> dismiss active item
                              HapticFeedback.lightImpact();
                              widget.controller.dismissActive();
                            }
                          }
                        },
                        child: ClipRRect(
                          borderRadius: BorderRadius.circular(radius),
                          child: BackdropFilter(
                            filter: widget.enableBlur
                                ? ImageFilter.blur(sigmaX: 20, sigmaY: 20)
                                : ImageFilter.blur(sigmaX: 0, sigmaY: 0),
                            child: Container(
                              width: width,
                              constraints: const BoxConstraints(minHeight: 36.0),
                              decoration: BoxDecoration(
                                color: const Color(0xF2000000), // black/95
                                borderRadius: BorderRadius.circular(radius),
                                border: Border.all(
                                  color: isHighContrast
                                      ? Colors.white
                                      : (widget.controller.isDragTargetActive
                                          ? const Color(0xFF00F0FF)
                                          : Colors.white.withValues(alpha: 0.08)),
                                  width: isHighContrast ? 2.0 : (widget.controller.isDragTargetActive ? 2.0 : 1.0),
                                ),
                                boxShadow: [
                                  BoxShadow(
                                    color: Colors.black.withValues(alpha: 0.6),
                                    blurRadius: 40,
                                    offset: const Offset(0, 8),
                                  ),
                                ],
                              ),
                              child: ClipRect(
                                child: OverflowBox(
                                  alignment: Alignment.topCenter,
                                  minWidth: 0,
                                  maxWidth: math.max(width, targetContentWidth),
                                  child: SizedBox(
                                    width: targetContentWidth,
                                    child: BlurFadeSwitcher(
                                      enableBlur: widget.enableBlur,
                                      child: _buildIslandContent(mode, activePayload),
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),

                      // Secondary bubble in split mode
                      if (mode == IslandMode.split && secondaryPayload != null) ...[
                        const SizedBox(width: 8),
                        Semantics(
                          button: true,
                          label: 'Secondary activity: ${secondaryPayload.title}',
                          child: GestureDetector(
                            onTap: () {
                              HapticFeedback.lightImpact();
                              widget.controller.expand(subView: 'notifications');
                            },
                            onLongPress: () {
                              HapticFeedback.mediumImpact();
                              widget.controller.expand(subView: 'notifications');
                            },
                            child: ClipRRect(
                              borderRadius: BorderRadius.circular(999),
                              child: BackdropFilter(
                                filter: widget.enableBlur
                                    ? ImageFilter.blur(sigmaX: 20, sigmaY: 20)
                                    : ImageFilter.blur(sigmaX: 0, sigmaY: 0),
                                child: Container(
                                  width: 42,
                                  height: 42,
                                  decoration: BoxDecoration(
                                    color: const Color(0xF2000000),
                                    shape: BoxShape.circle,
                                    border: Border.all(
                                      color: isHighContrast
                                          ? Colors.white
                                          : Colors.white.withValues(alpha: 0.08),
                                      width: isHighContrast ? 2.0 : 1.0,
                                    ),
                                    boxShadow: [
                                      BoxShadow(
                                        color: Colors.black.withValues(alpha: 0.6),
                                        blurRadius: 20,
                                        offset: const Offset(0, 4),
                                      ),
                                    ],
                                  ),
                                  child: Center(
                                    child: Icon(
                                      secondaryPayload.iconData ?? Icons.notifications_rounded,
                                      color: secondaryPayload.accentColor,
                                      size: 16,
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
            ),
          ),
        ],
      ],
    );
      },
    );
  }

  Widget _buildIslandContent(IslandMode mode, DynamicIslandPayload? payload) {
    if (mode == IslandMode.compact || mode == IslandMode.split) {
      return CollapsedIslandView(
        key: const ValueKey('collapsed-view'),
        payload: payload,
        notifCount: widget.controller.queue.length,
        onTap: () => widget.controller.expand(),
      );
    }

    if (mode == IslandMode.expanded) {
      if (widget.controller.activeSubView == 'notifications') {
        return ExpandedNotificationsListView(
          key: const ValueKey('expanded-notifications-view'),
          controller: widget.controller,
          showBack: payload?.isOngoingActivity == true,
        );
      }

      if (payload == null) {
        return const SizedBox(height: 42);
      }

      switch (payload.type) {
        case IslandPayloadType.media:
        case IslandPayloadType.voiceNotePlayback:
          return ExpandedMediaView(
            key: ValueKey('expanded-media-${payload.id}'),
            payload: payload,
            controller: widget.controller,
          );
        case IslandPayloadType.incomingCall:
        case IslandPayloadType.ongoingCall:
          return ExpandedCallView(
            key: ValueKey('expanded-call-${payload.id}'),
            payload: payload,
            controller: widget.controller,
          );
        case IslandPayloadType.clipboardIntent:
          return ExpandedClipboardIntentView(
            key: ValueKey('expanded-clipboard-${payload.id}'),
            payload: payload,
            controller: widget.controller,
          );
        case IslandPayloadType.dragAndGoTarget:
          return ExpandedDragTargetView(
            key: ValueKey('expanded-drag-${payload.id}'),
            payload: payload,
            controller: widget.controller,
          );
        case IslandPayloadType.chatMessage:
        case IslandPayloadType.systemAlert:
        case IslandPayloadType.contactOnline:
        case IslandPayloadType.ghostModeStatus:
          return ExpandedChatMessageView(
            key: ValueKey('expanded-chat-${payload.id}'),
            payload: payload,
            controller: widget.controller,
          );
      }
    }

    // Idle dot fallback
    return const SizedBox(width: 38, height: 32);
  }
}
