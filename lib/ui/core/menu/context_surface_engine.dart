import 'dart:ui';

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

/// Semantic anchor category for tailored contextual surface behavior.
enum ContextAnchorType {
  overflowButton,
  messageIncoming,
  messageOutgoing,
  chatRow,
  avatar,
  navigationItem,
  generic,
}

/// A request to calculate positioning and layout for a floating contextual surface.
class ContextSurfaceRequest {
  final Rect anchorRect;
  final Size preferredSize;
  final EdgeInsets safeInsets;
  final EdgeInsets keyboardInsets;
  final Size screenSize;
  final ContextAnchorType anchorType;
  final bool contentSized;

  const ContextSurfaceRequest({
    required this.anchorRect,
    required this.preferredSize,
    required this.safeInsets,
    this.keyboardInsets = EdgeInsets.zero,
    required this.screenSize,
    this.anchorType = ContextAnchorType.generic,
    this.contentSized = true,
  });

  /// Factory helper from BuildContext and anchor Rect
  factory ContextSurfaceRequest.fromContext({
    required BuildContext context,
    required Rect anchorRect,
    required Size preferredSize,
    ContextAnchorType anchorType = ContextAnchorType.generic,
    bool contentSized = true,
  }) {
    final mediaQuery = MediaQuery.of(context);
    return ContextSurfaceRequest(
      anchorRect: anchorRect,
      preferredSize: preferredSize,
      safeInsets: mediaQuery.padding,
      keyboardInsets: mediaQuery.viewInsets,
      screenSize: mediaQuery.size,
      anchorType: anchorType,
      contentSized: contentSized,
    );
  }
}

/// Result of resolving surface placement and coordinates.
class ContextSurfaceResolution {
  final Rect targetRect;
  final double maxHeight;
  final ContextSurfacePlacement placement;
  final Alignment transformOrigin;

  const ContextSurfaceResolution({
    required this.targetRect,
    required this.maxHeight,
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

    final effectiveBottom = screen.height - padding.bottom - keyboard.bottom - 8.0;
    final effectiveTop = padding.top + 8.0;
    final effectiveLeft = padding.left + 8.0;
    final effectiveRight = screen.width - padding.right - 8.0;

    final spaceBelow = (effectiveBottom - anchor.bottom).clamp(0.0, screen.height);
    final spaceAbove = (anchor.top - effectiveTop).clamp(0.0, screen.height);

    // Determine vertical placement
    final bool showBelow;
    final double availableHeight;
    if (spaceBelow >= pref.height) {
      showBelow = true;
      availableHeight = spaceBelow;
    } else if (spaceAbove >= pref.height) {
      showBelow = false;
      availableHeight = spaceAbove;
    } else if (spaceAbove > spaceBelow) {
      showBelow = false;
      availableHeight = spaceAbove;
    } else {
      showBelow = true;
      availableHeight = spaceBelow;
    }

    final double targetHeight = pref.height.clamp(0.0, availableHeight);

    // Horizontal placement: check if anchor is on the right or left half of screen
    final bool isRightSide;
    if (request.anchorType == ContextAnchorType.messageOutgoing) {
      isRightSide = true;
    } else if (request.anchorType == ContextAnchorType.messageIncoming) {
      isRightSide = false;
    } else if (request.anchorType == ContextAnchorType.overflowButton) {
      isRightSide = true;
    } else {
      final anchorCenterX = anchor.center.dx;
      isRightSide = anchorCenterX > (screen.width / 2);
    }

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

    // Determine target width (clamped between 150dp and 240dp by default for compact content-sized menus)
    final double targetWidth = pref.width.clamp(150.0, screen.width - 24.0);

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
      maxHeight: availableHeight,
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
                child: BackdropFilter(
                  filter: ImageFilter.blur(sigmaX: 6, sigmaY: 6),
                  child: Container(
                    color: Colors.black.withValues(alpha: 0.18),
                  ),
                ),
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
