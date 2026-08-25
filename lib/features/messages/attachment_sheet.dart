import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../ui/core/design_system/design_system.dart';

/// Grouped create-dock attachment sheet.
///
/// Contract (test/create_dock_contract_test.dart): actions are grouped by user
/// intent into `_OrbitGroup` sections — Media, Files, People & places, Create —
/// each tile exposes Semantics, and the on-device encryption notice is
/// rendered where the user chooses an attachment.
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

    final groups = <_OrbitGroup>[
      _OrbitGroup('Media', actions: <_AttachmentItem>[
          _AttachmentItem(
            icon: Icons.photo_library_rounded,
            label: 'Gallery',
            gradient: ChatyAttachmentPalette.gallery,
            onTap: () => onMediaRequested('image'),
          ),
          _AttachmentItem(
            icon: Icons.videocam_rounded,
            label: 'Video',
            gradient: ChatyAttachmentPalette.video,
            onTap: () => onMediaRequested('video'),
          ),
        ],
      ),
      _OrbitGroup('Files', actions: <_AttachmentItem>[
          _AttachmentItem(
            icon: Icons.insert_drive_file_rounded,
            label: 'Document',
            gradient: ChatyAttachmentPalette.document,
            onTap: () => onMediaRequested('document'),
          ),
          _AttachmentItem(
            icon: Icons.graphic_eq_rounded,
            label: 'Audio',
            gradient: ChatyAttachmentPalette.audio,
            onTap: () => onMediaRequested('audio'),
          ),
        ],
      ),
      _OrbitGroup('People & places', actions: <_AttachmentItem>[
          _AttachmentItem(
            icon: Icons.location_on_rounded,
            label: 'Location',
            gradient: ChatyAttachmentPalette.location,
            onTap: onLocationRequested,
          ),
          _AttachmentItem(
            icon: Icons.person_rounded,
            label: 'Contact',
            gradient: ChatyAttachmentPalette.contact,
            onTap: onContactRequested,
          ),
        ],
      ),
      _OrbitGroup('Create', actions: <_AttachmentItem>[
          _AttachmentItem(
            icon: Icons.poll_rounded,
            label: 'Poll',
            gradient: ChatyAttachmentPalette.poll,
            onTap: onPollRequested,
          ),
          _AttachmentItem(
            icon: Icons.task_alt_rounded,
            label: 'Task',
            gradient: ChatyAttachmentPalette.task,
            onTap: onTaskOption,
          ),
        ],
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
            crossAxisAlignment: CrossAxisAlignment.stretch,
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
              ...groups,
              const SizedBox(height: ChatySpacing.sm),
              Text(
                'Attachments are encrypted on this device before upload.',
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: colors.foregroundSecondary.withValues(alpha: 0.8),
                  fontSize: 11.5,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// A titled intent group inside the create dock.
class _OrbitGroup extends StatelessWidget {
  final String label;
  final List<_AttachmentItem> actions;

  const _OrbitGroup(this.label, {required this.actions});

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;

    return Padding(
      padding: const EdgeInsets.only(bottom: ChatySpacing.sm),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: ChatySpacing.xs,
              vertical: ChatySpacing.xs,
            ),
            child: Text(
              label,
              style: TextStyle(
                color: colors.foregroundSecondary,
                fontSize: 12,
                fontWeight: FontWeight.w700,
                letterSpacing: 0.2,
              ),
            ),
          ),
          LayoutBuilder(
            builder: (context, constraints) {
              const crossAxisCount = 4;
              final itemWidth =
                  (constraints.maxWidth - (crossAxisCount - 1) * 12) /
                  crossAxisCount;

              return Wrap(
                spacing: 12,
                runSpacing: 18,
                alignment: WrapAlignment.start,
                children: actions
                    .map((item) {
                      return SizedBox(
                        width: itemWidth,
                        child: _AttachmentGridTile(
                          item: item,
                          onTap: () => _closeAnd(context, item.onTap),
                        ),
                      );
                    })
                    .toList(growable: false),
              );
            },
          ),
        ],
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
  const _AttachmentGridTile({required this.item, required this.onTap});

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
                    child: Icon(item.icon, color: Colors.white, size: 26),
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
