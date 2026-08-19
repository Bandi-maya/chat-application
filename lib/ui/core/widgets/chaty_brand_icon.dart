import 'dart:io';

import 'package:flutter/material.dart';

import '../controllers/app_icon_controller.dart';

class LauncherIconPreview extends StatelessWidget {
  final LauncherIconVariant variant;
  final double size;
  final double borderRadius;

  const LauncherIconPreview({
    super.key,
    required this.variant,
    this.size = 48,
    this.borderRadius = 14,
  });

  @override
  Widget build(BuildContext context) {
    final palette = _paletteFor(variant);
    return ClipRRect(
      borderRadius: BorderRadius.circular(borderRadius),
      child: SizedBox(
        width: size,
        height: size,
        child: DecoratedBox(
          decoration: BoxDecoration(color: palette.$1),
          child: Center(
            child: Icon(
              palette.$3,
              size: size * 0.54,
              color: palette.$2,
            ),
          ),
        ),
      ),
    );
  }

  static (Color, Color, IconData) _paletteFor(LauncherIconVariant variant) {
    switch (variant) {
      case LauncherIconVariant.original:
        return (const Color(0xFF13B982), Colors.white, Icons.chat_bubble_rounded);
      case LauncherIconVariant.minimal:
        return (const Color(0xFF17212B), const Color(0xFFEAF2F5), Icons.chat_bubble_outline_rounded);
      case LauncherIconVariant.bubble:
        return (const Color(0xFF006D5B), const Color(0xFFE8FFF7), Icons.forum_rounded);
      case LauncherIconVariant.midnight:
        return (const Color(0xFF0B1220), const Color(0xFF5EEAD4), Icons.mode_comment_rounded);
      case LauncherIconVariant.ocean:
        return (const Color(0xFF075985), const Color(0xFFE0F2FE), Icons.chat_rounded);
      case LauncherIconVariant.violet:
        return (const Color(0xFF5B21B6), const Color(0xFFF3E8FF), Icons.sms_rounded);
    }
  }
}

class ChatyBrandIcon extends StatelessWidget {
  final AppIconController controller;
  final double size;
  final double borderRadius;

  const ChatyBrandIcon({
    super.key,
    required this.controller,
    this.size = 48,
    this.borderRadius = 14,
  });

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: controller,
      builder: (context, _) {
        Widget image;
        final customPath = controller.customBrandIconPath;
        if (controller.brandIconSource == BrandIconSource.custom &&
            customPath != null &&
            customPath.isNotEmpty &&
            File(customPath).existsSync()) {
          image = ClipRRect(
            borderRadius: BorderRadius.circular(borderRadius),
            child: Image.file(
              File(customPath),
              width: size,
              height: size,
              fit: BoxFit.cover,
              gaplessPlayback: true,
              errorBuilder: (_, _, _) => LauncherIconPreview(
                variant: controller.launcherIcon,
                size: size,
                borderRadius: borderRadius,
              ),
            ),
          );
        } else {
          image = LauncherIconPreview(
            variant: controller.launcherIcon,
            size: size,
            borderRadius: borderRadius,
          );
        }

        return Semantics(
          image: true,
          label: 'Chaty app icon',
          child: SizedBox(width: size, height: size, child: image),
        );
      },
    );
  }
}
