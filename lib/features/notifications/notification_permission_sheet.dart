import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
import '../../injection/locator.dart';
import '../../ui/core/design_system/design_system.dart';
import '../../data/services/notification_channel_manager.dart';

/// Contextual first-launch notification permission modal sheet.
/// Explains the value of timely message and call alerts before triggering
/// the OS runtime dialog.
class NotificationPermissionSheet extends StatefulWidget {
  final VoidCallback? onCompleted;

  const NotificationPermissionSheet({super.key, this.onCompleted});

  static Future<void> showIfNeeded(BuildContext context) async {
    final manager = locator<NotificationChannelManager>();
    if (manager.educationShown) return;

    final status = await Permission.notification.status;
    if (status.isGranted) {
      await manager.markEducationShown();
      return;
    }

    if (!context.mounted) return;
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => const NotificationPermissionSheet(),
    );
  }

  @override
  State<NotificationPermissionSheet> createState() =>
      _NotificationPermissionSheetState();
}

class _NotificationPermissionSheetState
    extends State<NotificationPermissionSheet> {
  bool _requesting = false;

  Future<void> _handleContinue() async {
    setState(() => _requesting = true);
    final manager = locator<NotificationChannelManager>();
    await manager.markEducationShown();

    final nav = Navigator.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final status = await Permission.notification.request();

    if (mounted) {
      setState(() => _requesting = false);
      nav.pop();
      widget.onCompleted?.call();

      if (status.isPermanentlyDenied) {
        messenger.showSnackBar(
          SnackBar(
            content: const Text(
              'Notifications are disabled in system settings.',
            ),
            action: SnackBarAction(
              label: 'Settings',
              onPressed: () => openAppSettings(),
            ),
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;

    return Container(
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: const BorderRadius.vertical(
          top: Radius.circular(ChatyRadius.xxl),
        ),
        border: Border(top: BorderSide(color: colors.borderSubtle)),
      ),
      padding: const EdgeInsets.fromLTRB(
        ChatySpacing.xl,
        ChatySpacing.lg,
        ChatySpacing.xl,
        ChatySpacing.xxl,
      ),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Container(
              width: 42,
              height: 4,
              decoration: BoxDecoration(
                color: colors.foregroundSecondary.withValues(alpha: 0.25),
                borderRadius: BorderRadius.circular(ChatyRadius.full),
              ),
            ),
            const SizedBox(height: ChatySpacing.xl),
            Container(
              padding: const EdgeInsets.all(ChatySpacing.lg),
              decoration: BoxDecoration(
                color: colors.primary.withValues(alpha: 0.12),
                shape: BoxShape.circle,
              ),
              child: Icon(
                Icons.notifications_active_rounded,
                size: 40,
                color: colors.primary,
              ),
            ),
            const SizedBox(height: ChatySpacing.lg),
            Text(
              'Stay Connected with Chaty',
              textAlign: TextAlign.center,
              style: TextStyle(
                color: colors.foreground,
                fontSize: 21,
                fontWeight: FontWeight.w700,
                letterSpacing: -0.4,
              ),
            ),
            const SizedBox(height: ChatySpacing.sm),
            Text(
              'Receive realtime notifications for private direct messages, group chats, reactions, and incoming voice/video calls.',
              textAlign: TextAlign.center,
              style: TextStyle(
                color: colors.foregroundSecondary,
                fontSize: 14.5,
                height: 1.4,
              ),
            ),
            const SizedBox(height: ChatySpacing.xxl),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      side: BorderSide(color: colors.border),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(ChatyRadius.md),
                      ),
                    ),
                    onPressed: () async {
                      await locator<NotificationChannelManager>()
                          .markEducationShown();
                      if (context.mounted) Navigator.of(context).pop();
                    },
                    child: Text(
                      'Not Now',
                      style: TextStyle(
                        color: colors.foregroundSecondary,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: ChatySpacing.md),
                Expanded(
                  child: FilledButton(
                    style: FilledButton.styleFrom(
                      backgroundColor: colors.primary,
                      foregroundColor: colors.onPrimary,
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(ChatyRadius.md),
                      ),
                    ),
                    onPressed: _requesting ? null : _handleContinue,
                    child: _requesting
                        ? const SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Text(
                            'Enable',
                            style: TextStyle(fontWeight: FontWeight.w700),
                          ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
