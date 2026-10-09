import 'package:flutter/material.dart';
import '../../design_system/components/chaty_modal.dart';
import '../../design_system/components/chaty_kit.dart';
import '../../theme/app_theme.dart';

class ChatyOverlayRenderer {
  ChatyOverlayRenderer._();

  /// Center Modal Dialog (10px default radius per Chaty specification)
  static Future<T?> showCenterModal<T>({
    required BuildContext context,
    Widget? header,
    required Widget content,
    Widget? footer,
    double maxWidth = 420.0,
    bool barrierDismissible = true,
  }) {
    return ChatyModal.show<T>(
      context: context,
      header: header,
      content: content,
      footer: footer,
      maxWidth: maxWidth,
      barrierDismissible: barrierDismissible,
    );
  }

  /// Adaptive Bottom Sheet
  static Future<T?> showAdaptiveSheet<T>({
    required BuildContext context,
    required Widget child,
    bool isDismissible = true,
    bool enableDrag = true,
    bool showGrabber = true,
  }) {
    final colors = context.colors;
    final isCompact = MediaQuery.sizeOf(context).width < 600.0;

    if (!isCompact) {
      // On wide screens, present as centered modal
      return showCenterModal<T>(
        context: context,
        content: child,
        barrierDismissible: isDismissible,
      );
    }

    return showModalBottomSheet<T>(
      context: context,
      isDismissible: isDismissible,
      enableDrag: enableDrag,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (sheetContext) {
        return Container(
          decoration: BoxDecoration(
            color: colors.surface,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
          ),
          child: SafeArea(
            top: false,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (showGrabber)
                  Center(
                    child: Container(
                      width: 36,
                      height: 4,
                      margin: const EdgeInsets.symmetric(vertical: 10),
                      decoration: BoxDecoration(
                        color: colors.border,
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                  ),
                Flexible(child: child),
              ],
            ),
          ),
        );
      },
    );
  }

  /// Action Sheet with iOS-like styled items
  static Future<void> showActionSheet({
    required BuildContext context,
    required String title,
    required List<ChatyMenuItem> items,
  }) {
    return ChatyMenuSheet.show(
      context,
      title: title,
      items: items,
    );
  }

  /// Alert confirmation dialog where safe exit is visually prominent
  static Future<bool> showConfirmDialog(
    BuildContext context, {
    required String title,
    required String message,
    String confirmLabel = 'Confirm',
    String cancelLabel = 'Cancel',
    bool destructive = false,
  }) {
    return ChatyConfirmDialog.show(
      context,
      title: title,
      message: message,
      confirmLabel: confirmLabel,
      cancelLabel: cancelLabel,
      destructive: destructive,
    );
  }

  /// Toast notification
  static void showToast(
    BuildContext context,
    String message, {
    Color? background,
  }) {
    ChatyToast.show(context, message, background: background);
  }
}
