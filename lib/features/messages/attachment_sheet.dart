import 'package:flutter/material.dart';

import '../../../ui/core/theme/theme_config.dart';
import '../../ui/core/design_system/design_system.dart';

class AttachmentSheet extends StatelessWidget {
  final ThemeConfig theme;
  final ValueChanged<String> onMediaRequested;
  final VoidCallback onLocationRequested;
  final VoidCallback onContactRequested;
  final VoidCallback onPollRequested;
  final VoidCallback onTaskOption;

  const AttachmentSheet({
    super.key,
    required this.theme,
    required this.onMediaRequested,
    required this.onLocationRequested,
    required this.onContactRequested,
    required this.onPollRequested,
    required this.onTaskOption,
  });

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;

    return Container(
      padding: const EdgeInsets.fromLTRB(
        ChatySpacing.lg,
        ChatySpacing.md,
        ChatySpacing.lg,
        ChatySpacing.xl,
      ),
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: const BorderRadius.vertical(
          top: Radius.circular(ChatyRadius.xl),
        ),
      ),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 38,
              height: 4.5,
              decoration: BoxDecoration(
                color: colors.foregroundSecondary.withValues(alpha: 0.2),
                borderRadius: BorderRadius.circular(ChatyRadius.full),
              ),
            ),
            const SizedBox(height: ChatySpacing.lg),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _option(
                  context: context,
                  icon: Icons.photo_library_rounded,
                  label: 'Gallery',
                  color: colors.accent,
                  onTap: () =>
                      _closeAnd(context, () => onMediaRequested('image')),
                ),
                _option(
                  context: context,
                  icon: Icons.videocam_rounded,
                  label: 'Video',
                  color: colors.accent,
                  onTap: () =>
                      _closeAnd(context, () => onMediaRequested('video')),
                ),
                _option(
                  context: context,
                  icon: Icons.insert_drive_file_rounded,
                  label: 'Document',
                  color: colors.primary,
                  onTap: () =>
                      _closeAnd(context, () => onMediaRequested('document')),
                ),
                _option(
                  context: context,
                  icon: Icons.headphones_rounded,
                  label: 'Audio',
                  color: colors.warning,
                  onTap: () =>
                      _closeAnd(context, () => onMediaRequested('audio')),
                ),
              ],
            ),
            const SizedBox(height: ChatySpacing.base),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _option(
                  context: context,
                  icon: Icons.location_on_rounded,
                  label: 'Location',
                  color: colors.success,
                  onTap: () => _closeAnd(context, onLocationRequested),
                ),
                _option(
                  context: context,
                  icon: Icons.person_rounded,
                  label: 'Contact',
                  color: colors.info,
                  onTap: () => _closeAnd(context, onContactRequested),
                ),
                _option(
                  context: context,
                  icon: Icons.poll_rounded,
                  label: 'Poll',
                  color: colors.primary,
                  onTap: () => _closeAnd(context, onPollRequested),
                ),
                _option(
                  context: context,
                  icon: Icons.task_alt_rounded,
                  label: 'Task',
                  color: colors.error,
                  onTap: () => _closeAnd(context, onTaskOption),
                ),
              ],
            ),
            const SizedBox(height: ChatySpacing.lg),
            Text(
              'Files and attachments are end-to-end encrypted before storage.',
              textAlign: TextAlign.center,
              style: ChatyTypography.caption(
                colors.foregroundSecondary,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _option({
    required BuildContext context,
    required IconData icon,
    required String label,
    required Color color,
    required VoidCallback onTap,
  }) {
    final colors = context.colors;
    return InkWell(
      borderRadius: BorderRadius.circular(ChatyRadius.md),
      onTap: onTap,
      child: SizedBox(
        width: 72,
        child: Column(
          children: [
            Container(
              width: 52,
              height: 52,
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(ChatyRadius.md),
              ),
              child: Icon(icon, color: color, size: 24),
            ),
            const SizedBox(height: ChatySpacing.xs),
            Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                color: colors.foreground,
                fontSize: 12,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _closeAnd(BuildContext context, VoidCallback action) {
    Navigator.of(context).pop();
    WidgetsBinding.instance.addPostFrameCallback((_) => action());
  }
}
