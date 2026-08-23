import 'package:flutter/widgets.dart';

enum ChatyWindowClass { compact, medium, expanded, large }

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

  static bool prefersRail(BuildContext context) {
    final value = of(context);
    return value == ChatyWindowClass.expanded ||
        value == ChatyWindowClass.large;
  }

  static double contentMaxWidth(BuildContext context) {
    return switch (of(context)) {
      ChatyWindowClass.compact => double.infinity,
      ChatyWindowClass.medium => 720,
      ChatyWindowClass.expanded => 900,
      ChatyWindowClass.large => 1080,
    };
  }
}
