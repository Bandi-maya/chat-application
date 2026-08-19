import 'package:flutter/material.dart';

import '../../../ui/core/theme/theme_config.dart';

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
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 14, 20, 26),
      decoration: BoxDecoration(
        color: theme.surfaceColor,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(22)),
      ),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 38,
              height: 4,
              decoration: BoxDecoration(
                color: theme.secondaryTextColor.withValues(alpha: 0.3),
                borderRadius: BorderRadius.circular(3),
              ),
            ),
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _option(
                  icon: Icons.photo_library_rounded,
                  label: 'Gallery',
                  color: const Color(0xFF8B5CF6),
                  onTap: () => _closeAnd(context, () => onMediaRequested('image')),
                ),
                _option(
                  icon: Icons.videocam_rounded,
                  label: 'Video',
                  color: const Color(0xFFEC4899),
                  onTap: () => _closeAnd(context, () => onMediaRequested('video')),
                ),
                _option(
                  icon: Icons.insert_drive_file_rounded,
                  label: 'Document',
                  color: const Color(0xFF3B82F6),
                  onTap: () => _closeAnd(context, () => onMediaRequested('document')),
                ),
                _option(
                  icon: Icons.headphones_rounded,
                  label: 'Audio',
                  color: const Color(0xFFF59E0B),
                  onTap: () => _closeAnd(context, () => onMediaRequested('audio')),
                ),
              ],
            ),
            const SizedBox(height: 20),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _option(
                  icon: Icons.location_on_rounded,
                  label: 'Location',
                  color: const Color(0xFF10B981),
                  onTap: () => _closeAnd(context, onLocationRequested),
                ),
                _option(
                  icon: Icons.person_rounded,
                  label: 'Contact',
                  color: const Color(0xFF06B6D4),
                  onTap: () => _closeAnd(context, onContactRequested),
                ),
                _option(
                  icon: Icons.poll_rounded,
                  label: 'Poll',
                  color: const Color(0xFF6366F1),
                  onTap: () => _closeAnd(context, onPollRequested),
                ),
                _option(
                  icon: Icons.task_alt_rounded,
                  label: 'Task',
                  color: const Color(0xFFEF4444),
                  onTap: () => _closeAnd(context, onTaskOption),
                ),
              ],
            ),
            const SizedBox(height: 14),
            Text(
              'Files are selected from the device and uploaded to the private Chaty conversation bucket.',
              textAlign: TextAlign.center,
              style: TextStyle(
                color: theme.secondaryTextColor,
                fontSize: 11.5,
                height: 1.35,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _option({
    required IconData icon,
    required String label,
    required Color color,
    required VoidCallback onTap,
  }) {
    return InkWell(
      borderRadius: BorderRadius.circular(16),
      onTap: onTap,
      child: SizedBox(
        width: 68,
        child: Column(
          children: [
            Container(
              width: 52,
              height: 52,
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.14),
                borderRadius: BorderRadius.circular(16),
              ),
              child: Icon(icon, color: color, size: 25),
            ),
            const SizedBox(height: 7),
            Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                color: theme.primaryTextColor,
                fontSize: 11.5,
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
