import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Placement of the context surface relative to the anchor rectangle.
enum ContextSurfacePlacement {
  aboveLeft,
  aboveRight,
  belowLeft,
  belowRight,
  sideLeft,
  sideRight,
}

/// A request to calculate positioning and layout for a floating contextual surface.
class ContextSurfaceRequest {
  final Rect anchorRect;
  final Size preferredSize;
  final EdgeInsets safeInsets;
  final EdgeInsets keyboardInsets;
  final Size screenSize;

  const ContextSurfaceRequest({
    required this.anchorRect,
    required this.preferredSize,
    required this.safeInsets,
    this.keyboardInsets = EdgeInsets.zero,
    required this.screenSize,
  });

  /// Factory helper from BuildContext and anchor Rect
  factory ContextSurfaceRequest.fromContext({
    required BuildContext context,
    required Rect anchorRect,
    required Size preferredSize,
  }) {
    final mediaQuery = MediaQuery.of(context);
    return ContextSurfaceRequest(
      anchorRect: anchorRect,
      preferredSize: preferredSize,
      safeInsets: mediaQuery.padding,
      keyboardInsets: mediaQuery.viewInsets,
      screenSize: mediaQuery.size,
    );
  }
}

/// Result of resolving surface placement and coordinates.
class ContextSurfaceResolution {
  final Rect targetRect;
  final ContextSurfacePlacement placement;
  final Alignment transformOrigin;

  const ContextSurfaceResolution({
    required this.targetRect,
    required this.placement,
    required this.transformOrigin,
  });
}

/// Resolves spatial placement, collision avoidance, and clamping for context surfaces.
class ContextSurfaceResolver {
  const ContextSurfaceResolver._();

  static ContextSurfaceResolution resolve(ContextSurfaceRequest request) {
    final screen = request.screenSize;
    final padding = request.safeInsets;
    final keyboard = request.keyboardInsets;
    final anchor = request.anchorRect;
    final pref = request.preferredSize;

    final effectiveBottom = screen.height - padding.bottom - keyboard.bottom;
    final effectiveTop = padding.top;
    final effectiveLeft = padding.left + 8.0;
    final effectiveRight = screen.width - padding.right - 8.0;

    final spaceBelow = effectiveBottom - anchor.bottom;
    final spaceAbove = anchor.top - effectiveTop;

    final showBelow = spaceBelow >= pref.height || spaceBelow >= spaceAbove;

    // Horizontal placement: check if anchor is on the right or left half of screen
    final anchorCenterX = anchor.center.dx;
    final isRightSide = anchorCenterX > (screen.width / 2);

    final ContextSurfacePlacement placement;
    if (showBelow) {
      placement = isRightSide
          ? ContextSurfacePlacement.belowRight
          : ContextSurfacePlacement.belowLeft;
    } else {
      placement = isRightSide
          ? ContextSurfacePlacement.aboveRight
          : ContextSurfacePlacement.aboveLeft;
    }

    // Determine target width
    final double targetWidth = pref.width.clamp(180.0, screen.width - 24.0);
    final double targetHeight = pref.height;

    // Calculate X coordinate
    double left;
    if (isRightSide) {
      left = anchor.right - targetWidth;
    } else {
      left = anchor.left;
    }

    // Clamp horizontally within screen
    if (left + targetWidth > effectiveRight) {
      left = effectiveRight - targetWidth;
    }
    if (left < effectiveLeft) {
      left = effectiveLeft;
    }

    // Calculate Y coordinate
    double top;
    final Alignment transformOrigin;
    if (showBelow) {
      top = anchor.bottom + 6.0;
      if (top + targetHeight > effectiveBottom) {
        top = (effectiveBottom - targetHeight).clamp(effectiveTop, effectiveBottom);
      }
      transformOrigin = isRightSide ? Alignment.topRight : Alignment.topLeft;
    } else {
      top = anchor.top - targetHeight - 6.0;
      if (top < effectiveTop) {
        top = effectiveTop;
      }
      transformOrigin = isRightSide ? Alignment.bottomRight : Alignment.bottomLeft;
    }

    return ContextSurfaceResolution(
      targetRect: Rect.fromLTWH(left, top, targetWidth, targetHeight),
      placement: placement,
      transformOrigin: transformOrigin,
    );
  }
}

/// Central controller and presentation engine for anchored context surfaces.
class ContextSurfaceController {
  ContextSurfaceController._();

  /// Presents an anchored surface overlay using precise physics, transform origins, and dismissal logic.
  static Future<T?> showSurface<T>({
    required BuildContext context,
    required Rect anchorRect,
    required Widget Function(BuildContext dialogContext, ContextSurfaceResolution resolution) builder,
    Size preferredSize = const Size(240, 280),
    Color barrierColor = const Color(0x33000000),
    Duration duration = const Duration(milliseconds: 170),
  }) {
    HapticFeedback.lightImpact();

    final resolution = ContextSurfaceResolver.resolve(
      ContextSurfaceRequest.fromContext(
        context: context,
        anchorRect: anchorRect,
        preferredSize: preferredSize,
      ),
    );

    return showGeneralDialog<T>(
      context: context,
      barrierDismissible: true,
      barrierLabel: 'Dismiss Context Surface',
      barrierColor: barrierColor,
      transitionDuration: duration,
      pageBuilder: (dialogContext, anim1, anim2) {
        return Stack(
          children: [
            Positioned.fill(
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: () => Navigator.of(dialogContext).pop(),
                child: const SizedBox.expand(),
              ),
            ),
            Positioned(
              left: resolution.targetRect.left,
              top: resolution.targetRect.top,
              width: resolution.targetRect.width,
              child: Material(
                color: Colors.transparent,
                child: builder(dialogContext, resolution),
              ),
            ),
          ],
        );
      },
      transitionBuilder: (context, animation, secondaryAnimation, child) {
        final curved = CurvedAnimation(
          parent: animation,
          curve: Curves.easeOutCubic,
          reverseCurve: Curves.easeInCubic,
        );
        return FadeTransition(
          opacity: curved,
          child: ScaleTransition(
            alignment: resolution.transformOrigin,
            scale: Tween<double>(begin: 0.92, end: 1.0).animate(curved),
            child: child,
          ),
        );
      },
    );
  }
}
