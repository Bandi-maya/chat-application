import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

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
    final actions = <_OrbitAction>[
      _OrbitAction(
        Icons.photo_library_rounded,
        'Gallery',
        'Photos',
        colors.accent,
        () => onMediaRequested('image'),
      ),
      _OrbitAction(
        Icons.videocam_rounded,
        'Video',
        'Clips',
        colors.accent,
        () => onMediaRequested('video'),
      ),
      _OrbitAction(
        Icons.insert_drive_file_rounded,
        'Document',
        'Files',
        colors.primary,
        () => onMediaRequested('document'),
      ),
      _OrbitAction(
        Icons.graphic_eq_rounded,
        'Audio',
        'Sound',
        colors.warning,
        () => onMediaRequested('audio'),
      ),
      _OrbitAction(
        Icons.location_on_rounded,
        'Location',
        'Place',
        colors.success,
        onLocationRequested,
      ),
      _OrbitAction(
        Icons.person_rounded,
        'Contact',
        'Person',
        colors.info,
        onContactRequested,
      ),
      _OrbitAction(
        Icons.poll_rounded,
        'Poll',
        'Ask',
        colors.primary,
        onPollRequested,
      ),
      _OrbitAction(
        Icons.task_alt_rounded,
        'Task',
        'Assign',
        colors.error,
        onTaskOption,
      ),
    ];

    return Container(
      padding: const EdgeInsets.fromLTRB(
        ChatySpacing.base,
        ChatySpacing.sm,
        ChatySpacing.base,
        ChatySpacing.lg,
      ),
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: const BorderRadius.vertical(
          top: Radius.circular(30),
        ),
      ),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Center(
              child: Container(
                width: 38,
                height: 4.5,
                decoration: BoxDecoration(
                  color: colors.foregroundSecondary.withValues(alpha: 0.2),
                  borderRadius: BorderRadius.circular(ChatyRadius.full),
                ),
              ),
            ),
            const SizedBox(height: ChatySpacing.base),
            Row(
              children: [
                Container(
                  width: 42,
                  height: 42,
                  decoration: BoxDecoration(
                    color: colors.primary.withValues(alpha: .12),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Icon(
                    Icons.auto_awesome_rounded,
                    color: colors.primary,
                    size: 21,
                  ),
                ),
                const SizedBox(width: 11),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Send something',
                        style: TextStyle(
                          color: colors.foreground,
                          fontSize: 17,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        'Pick an action — Chaty secures media before upload.',
                        style: ChatyTypography.caption(
                          colors.foregroundSecondary,
                        ),
                      ),
                    ],
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 9,
                    vertical: 6,
                  ),
                  decoration: BoxDecoration(
                    color: colors.success.withValues(alpha: .10),
                    borderRadius: BorderRadius.circular(999),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        Icons.lock_rounded,
                        size: 13,
                        color: colors.success,
                      ),
                      const SizedBox(width: 4),
                      Text(
                        'Secure',
                        style: TextStyle(
                          color: colors.success,
                          fontSize: 10.5,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: ChatySpacing.lg),
            LayoutBuilder(
              builder: (context, constraints) {
                final itemWidth = constraints.maxWidth >= 420
                    ? (constraints.maxWidth - 30) / 4
                    : (constraints.maxWidth - 20) / 3;
                return Wrap(
                  spacing: 10,
                  runSpacing: 12,
                  children: actions
                      .map(
                        (action) => SizedBox(
                          width: itemWidth,
                          child: _OrbitTile(
                            action: action,
                            onTap: () => _closeAnd(context, action.onTap),
                          ),
                        ),
                      )
                      .toList(growable: false),
                );
              },
            ),
            const SizedBox(height: ChatySpacing.base),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
              decoration: BoxDecoration(
                color: colors.surfaceSecondary,
                borderRadius: BorderRadius.circular(16),
              ),
              child: Row(
                children: [
                  Icon(
                    Icons.shield_outlined,
                    size: 17,
                    color: colors.primary,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Attachments are encrypted before they leave this device.',
                      style: ChatyTypography.caption(
                        colors.foregroundSecondary,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _closeAnd(BuildContext context, VoidCallback action) {
    HapticFeedback.selectionClick();
    Navigator.of(context).pop();
    WidgetsBinding.instance.addPostFrameCallback((_) => action());
  }
}

class _OrbitAction {
  const _OrbitAction(
    this.icon,
    this.label,
    this.hint,
    this.color,
    this.onTap,
  );

  final IconData icon;
  final String label;
  final String hint;
  final Color color;
  final VoidCallback onTap;
}

class _OrbitTile extends StatelessWidget {
  const _OrbitTile({required this.action, required this.onTap});

  final _OrbitAction action;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: onTap,
        child: Ink(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 12),
          decoration: BoxDecoration(
            color: Color.alphaBlend(
              action.color.withValues(alpha: .055),
              colors.surfaceSecondary,
            ),
            borderRadius: BorderRadius.circular(18),
            border: Border.all(
              color: action.color.withValues(alpha: .16),
            ),
          ),
          child: Column(
            children: [
              Container(
                width: 46,
                height: 46,
                decoration: BoxDecoration(
                  color: action.color.withValues(alpha: .13),
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Icon(action.icon, color: action.color, size: 23),
              ),
              const SizedBox(height: 8),
              Text(
                action.label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  color: colors.foreground,
                  fontSize: 12,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                action.hint,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  color: colors.foregroundSecondary,
                  fontSize: 10,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
