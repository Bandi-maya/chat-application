import 'package:flutter/widgets.dart';

enum ChatyWindowClass { compact, medium, expanded, large }

enum ChatyPaneMode { single, supporting, dual }

abstract final class ChatyAdaptive {
  static ChatyWindowClass windowClassForWidth(double width) {
    if (width < 600) return ChatyWindowClass.compact;
    if (width < 840) return ChatyWindowClass.medium;
    if (width < 1200) return ChatyWindowClass.expanded;
    return ChatyWindowClass.large;
  }

  static ChatyWindowClass of(BuildContext context) {
    return windowClassForWidth(MediaQuery.sizeOf(context).width);
  }

  static ChatyPaneMode paneModeForWidth(double width) {
    return switch (windowClassForWidth(width)) {
      ChatyWindowClass.compact => ChatyPaneMode.single,
      ChatyWindowClass.medium => ChatyPaneMode.supporting,
      ChatyWindowClass.expanded => ChatyPaneMode.dual,
      ChatyWindowClass.large => ChatyPaneMode.dual,
    };
  }

  static ChatyPaneMode paneMode(BuildContext context) {
    return paneModeForWidth(MediaQuery.sizeOf(context).width);
  }

  static bool prefersRail(BuildContext context) {
    final value = of(context);
    return value == ChatyWindowClass.expanded ||
        value == ChatyWindowClass.large;
  }

  static bool prefersTwoPane(BuildContext context) {
    return paneMode(context) == ChatyPaneMode.dual;
  }

  static double navigationWidth(BuildContext context) {
    return switch (of(context)) {
      ChatyWindowClass.compact => 0,
      ChatyWindowClass.medium => 72,
      ChatyWindowClass.expanded => 80,
      ChatyWindowClass.large => 88,
    };
  }

  static double conversationListWidth(BuildContext context) {
    final width = MediaQuery.sizeOf(context).width;
    return switch (of(context)) {
      ChatyWindowClass.compact => width,
      ChatyWindowClass.medium => width,
      ChatyWindowClass.expanded => (width * .36).clamp(320, 420),
      ChatyWindowClass.large => (width * .32).clamp(360, 460),
    };
  }

  static EdgeInsets pageInsets(BuildContext context) {
    return switch (of(context)) {
      ChatyWindowClass.compact => const EdgeInsets.symmetric(horizontal: 16),
      ChatyWindowClass.medium => const EdgeInsets.symmetric(horizontal: 20),
      ChatyWindowClass.expanded => const EdgeInsets.symmetric(horizontal: 24),
      ChatyWindowClass.large => const EdgeInsets.symmetric(horizontal: 32),
    };
  }

  static double contentMaxWidth(BuildContext context) {
    return switch (of(context)) {
      ChatyWindowClass.compact => double.infinity,
      ChatyWindowClass.medium => 720,
      ChatyWindowClass.expanded => 900,
      ChatyWindowClass.large => 1080,
    };
  }

  static double effectiveTextScale(BuildContext context) {
    return MediaQuery.textScalerOf(context).scale(1).clamp(1.0, 2.0);
  }

  static bool hasLargeText(BuildContext context) {
    return effectiveTextScale(context) >= 1.5;
  }

  static bool shouldStackActions(BuildContext context) {
    final width = MediaQuery.sizeOf(context).width;
    return width < 360 || hasLargeText(context);
  }
}
