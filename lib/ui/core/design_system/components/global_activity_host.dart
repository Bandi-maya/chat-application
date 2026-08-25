import 'package:flutter/material.dart';

import '../chaty_motion.dart';

/// Canonical host for transient, globally visible Chaty activity surfaces.
///
/// The routed application remains the base child. Ongoing activities such as
/// calls, uploads, recording and reconnecting are rendered above it without
/// requiring each feature screen to own a separate overlay implementation.
class ChatyGlobalActivityHost extends StatelessWidget {
  const ChatyGlobalActivityHost({
    super.key,
    required this.child,
    this.primaryActivity,
    this.secondaryActivity,
    this.bottomActivity,
    this.ignoreActivityPointer = false,
  });

  final Widget child;
  final Widget? primaryActivity;
  final Widget? secondaryActivity;
  final Widget? bottomActivity;
  final bool ignoreActivityPointer;

  @override
  Widget build(BuildContext context) {
    return Stack(
      fit: StackFit.expand,
      children: [
        child,
        Align(
          alignment: Alignment.topCenter,
          child: _AnimatedActivitySlot(
            slotKey: const ValueKey<String>('chaty-primary-activity'),
            activity: primaryActivity,
            ignorePointer: ignoreActivityPointer,
            verticalOffset: -12,
          ),
        ),
        Align(
          alignment: Alignment.center,
          child: _AnimatedActivitySlot(
            slotKey: const ValueKey<String>('chaty-secondary-activity'),
            activity: secondaryActivity,
            ignorePointer: ignoreActivityPointer,
          ),
        ),
        Align(
          alignment: Alignment.bottomCenter,
          child: _AnimatedActivitySlot(
            slotKey: const ValueKey<String>('chaty-bottom-activity'),
            activity: bottomActivity,
            ignorePointer: ignoreActivityPointer,
            verticalOffset: 12,
          ),
        ),
      ],
    );
  }
}

class _AnimatedActivitySlot extends StatelessWidget {
  const _AnimatedActivitySlot({
    required this.slotKey,
    required this.activity,
    required this.ignorePointer,
    this.verticalOffset = 0,
  });

  final Key slotKey;
  final Widget? activity;
  final bool ignorePointer;
  final double verticalOffset;

  @override
  Widget build(BuildContext context) {
    final duration = ChatyMotion.duration(context, preferred: ChatyMotion.base);
    return AnimatedSwitcher(
      duration: duration,
      switchInCurve: ChatyMotion.enter,
      switchOutCurve: ChatyMotion.exit,
      transitionBuilder: (child, animation) {
        final offset = Tween<Offset>(
          begin: Offset(0, verticalOffset == 0 ? 0 : verticalOffset.sign * .08),
          end: Offset.zero,
        ).animate(animation);
        return FadeTransition(
          opacity: animation,
          child: SlideTransition(position: offset, child: child),
        );
      },
      child: activity == null
          ? SizedBox.shrink(key: slotKey)
          : _ActivitySlot(
              key: ValueKey<Object>(activity.runtimeType),
              ignorePointer: ignorePointer,
              child: activity!,
            ),
    );
  }
}

class _ActivitySlot extends StatelessWidget {
  const _ActivitySlot({
    super.key,
    required this.child,
    required this.ignorePointer,
  });

  final Widget child;
  final bool ignorePointer;

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      ignoring: ignorePointer,
      child: RepaintBoundary(
        child: Semantics(container: true, liveRegion: true, child: child),
      ),
    );
  }
}
