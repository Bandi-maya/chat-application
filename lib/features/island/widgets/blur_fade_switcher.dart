import 'dart:ui';
import 'package:flutter/material.dart';

/// Replicates the Framer Motion optical blur-fade enter/exit transition:
/// Combines ImageFilter.blur (up to 8px) with opacity and scale (0.92 -> 1.0).
class BlurFadeSwitcher extends StatelessWidget {
  final Widget child;
  final Duration duration;
  final bool enableBlur;

  const BlurFadeSwitcher({
    super.key,
    required this.child,
    this.duration = const Duration(milliseconds: 240),
    this.enableBlur = true,
  });

  @override
  Widget build(BuildContext context) {
    return AnimatedSwitcher(
      duration: duration,
      switchInCurve: Curves.easeOutCubic,
      switchOutCurve: Curves.easeInCubic,
      layoutBuilder: (currentChild, previousChildren) {
        return Stack(
          alignment: Alignment.center,
          children: [
            ...previousChildren,
            if (currentChild != null) currentChild,
          ],
        );
      },
      transitionBuilder: (child, animation) {
        final scaleAnimation = Tween<double>(begin: 0.92, end: 1.0).animate(
          CurvedAnimation(parent: animation, curve: Curves.easeOutBack),
        );
        final opacityAnimation = Tween<double>(begin: 0.0, end: 1.0).animate(
          CurvedAnimation(parent: animation, curve: Curves.easeOut),
        );

        return FadeTransition(
          opacity: opacityAnimation,
          child: ScaleTransition(
            scale: scaleAnimation,
            child: enableBlur
                ? AnimatedBuilder(
                    animation: animation,
                    builder: (context, w) {
                      final sigma = (1.0 - animation.value) * 6.0;
                      if (sigma <= 0.1) return w!;
                      return ImageFiltered(
                        imageFilter: ImageFilter.blur(sigmaX: sigma, sigmaY: sigma),
                        child: w,
                      );
                    },
                    child: child,
                  )
                : child,
          ),
        );
      },
      child: child,
    );
  }
}
