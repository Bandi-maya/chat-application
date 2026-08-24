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

    final actions = <_AttachmentItem>[
      _AttachmentItem(
        icon: Icons.insert_drive_file_rounded,
        label: 'document',
        gradient: const [Color(0xFF7F66FF), Color(0xFF5E35B1)],
        onTap: () => onMediaRequested('document'),
      ),
      _AttachmentItem(
        icon: Icons.photo_library_rounded,
        label: 'gallery',
        gradient: const [Color(0xFFE91E63), Color(0xFFC2185B)],
        onTap: () => onMediaRequested('image'),
      ),
      _AttachmentItem(
        icon: Icons.videocam_rounded,
        label: 'video',
        gradient: const [Color(0xFFFF5722), Color(0xFFE64A19)],
        onTap: () => onMediaRequested('video'),
      ),
      _AttachmentItem(
        icon: Icons.graphic_eq_rounded,
        label: 'audio',
        gradient: const [Color(0xFFFF9800), Color(0xFFF57C00)],
        onTap: () => onMediaRequested('audio'),
      ),
      _AttachmentItem(
        icon: Icons.location_on_rounded,
        label: 'location',
        gradient: const [Color(0xFF2E7D32), Color(0xFF1B5E20)],
        onTap: onLocationRequested,
      ),
      _AttachmentItem(
        icon: Icons.person_rounded,
        label: 'contact',
        gradient: const [Color(0xFF0097A7), Color(0xFF006064)],
        onTap: onContactRequested,
      ),
      _AttachmentItem(
        icon: Icons.poll_rounded,
        label: 'poll',
        gradient: const [Color(0xFF0288D1), Color(0xFF01579B)],
        onTap: onPollRequested,
      ),
      _AttachmentItem(
        icon: Icons.task_alt_rounded,
        label: 'task',
        gradient: const [Color(0xFF00BFA5), Color(0xFF00897B)],
        onTap: onTaskOption,
      ),
    ];

    return Container(
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.12),
            blurRadius: 20,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(
            ChatySpacing.md,
            ChatySpacing.sm,
            ChatySpacing.md,
            ChatySpacing.lg,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Center(
                child: Container(
                  width: 36,
                  height: 4,
                  decoration: BoxDecoration(
                    color: colors.foregroundSecondary.withValues(alpha: 0.25),
                    borderRadius: BorderRadius.circular(ChatyRadius.full),
                  ),
                ),
              ),
              const SizedBox(height: ChatySpacing.md),
              LayoutBuilder(
                builder: (context, constraints) {
                  final crossAxisCount = constraints.maxWidth > 420 ? 4 : 4;
                  final itemWidth = (constraints.maxWidth - (crossAxisCount - 1) * 12) / crossAxisCount;

                  return Wrap(
                    spacing: 12,
                    runSpacing: 18,
                    alignment: WrapAlignment.start,
                    children: actions.map((item) {
                      return SizedBox(
                        width: itemWidth,
                        child: _AttachmentGridTile(
                          item: item,
                          onTap: () => _closeAnd(context, item.onTap),
                        ),
                      );
                    }).toList(growable: false),
                  );
                },
              ),
              const SizedBox(height: ChatySpacing.sm),
            ],
          ),
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

class _AttachmentItem {
  const _AttachmentItem({
    required this.icon,
    required this.label,
    required this.gradient,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final List<Color> gradient;
  final VoidCallback onTap;
}

class _AttachmentGridTile extends StatelessWidget {
  const _AttachmentGridTile({
    required this.item,
    required this.onTap,
  });

  final _AttachmentItem item;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;

    return Semantics(
      button: true,
      label: item.label,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(16),
          onTap: onTap,
          splashColor: item.gradient.first.withValues(alpha: 0.2),
          highlightColor: item.gradient.first.withValues(alpha: 0.1),
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 6),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                Container(
                  width: 54,
                  height: 54,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: LinearGradient(
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                      colors: item.gradient,
                    ),
                    boxShadow: [
                      BoxShadow(
                        color: item.gradient.last.withValues(alpha: 0.3),
                        blurRadius: 10,
                        offset: const Offset(0, 4),
                      ),
                    ],
                  ),
                  child: Center(
                    child: Icon(
                      item.icon,
                      color: Colors.white,
                      size: 26,
                    ),
                  ),
                ),
                const SizedBox(height: 7),
                Text(
                  item.label,
                  textAlign: TextAlign.center,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: colors.foreground.withValues(alpha: 0.85),
                    fontSize: 12,
                    fontWeight: FontWeight.w500,
                    letterSpacing: -0.1,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
