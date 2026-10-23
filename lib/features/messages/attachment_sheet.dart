import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../ui/core/design_system/design_system.dart';

/// Mixed create-dock attachment sheet with an instant Demo Sandbox suite.
///
/// All actions share one predictable grid so the user can scan the complete
/// set without stepping through artificial categories. The demo sandbox allows
/// 1-tap testing of documents, music/audio, call cards, and location previews.
class AttachmentSheet extends StatelessWidget {
  final ThemeConfig theme;
  final ValueChanged<String> onMediaRequested;
  final VoidCallback onLocationRequested;
  final VoidCallback onContactRequested;
  final VoidCallback onPollRequested;
  final VoidCallback onTaskOption;
  final VoidCallback? onDemoDocument;
  final VoidCallback? onDemoAudio;
  final VoidCallback? onDemoCall;
  final VoidCallback? onDemoTask;
  final VoidCallback? onDemoLocation;

  const AttachmentSheet({
    super.key,
    required this.theme,
    required this.onMediaRequested,
    required this.onLocationRequested,
    required this.onContactRequested,
    required this.onPollRequested,
    required this.onTaskOption,
    this.onDemoDocument,
    this.onDemoAudio,
    this.onDemoCall,
    this.onDemoTask,
    this.onDemoLocation,
  });

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;

    final actions = <_AttachmentItem>[
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
        child: SingleChildScrollView(
          physics: const BouncingScrollPhysics(),
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
              const SizedBox(height: ChatySpacing.sm),

              // Demo Sandbox / Instant Test dock
              Container(
                margin: const EdgeInsets.only(bottom: ChatySpacing.sm),
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: colors.primary.withValues(alpha: 0.08),
                  borderRadius: BorderRadius.circular(18),
                  border: Border.all(
                    color: colors.primary.withValues(alpha: 0.18),
                    width: 1,
                  ),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 3,
                          ),
                          decoration: BoxDecoration(
                            gradient: const LinearGradient(
                              colors: [Color(0xFF6366F1), Color(0xFF8B5CF6)],
                            ),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: const Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(
                                Icons.science_rounded,
                                color: Colors.white,
                                size: 12,
                              ),
                              SizedBox(width: 4),
                              Text(
                                'DEMO SANDBOX',
                                style: TextStyle(
                                  color: Colors.white,
                                  fontSize: 10,
                                  fontWeight: FontWeight.w800,
                                  letterSpacing: 0.5,
                                ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            '1-Tap test chat components & island hooks',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: colors.foregroundSecondary,
                              fontSize: 11,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),
                    SingleChildScrollView(
                      scrollDirection: Axis.horizontal,
                      physics: const BouncingScrollPhysics(),
                      child: Row(
                        children: [
                          _DemoTestPill(
                            icon: Icons.description_rounded,
                            label: 'Test Doc (PDF)',
                            gradient: const [
                              Color(0xFF7F66FF),
                              Color(0xFF5E35B1),
                            ],
                            onTap: () => _closeAnd(
                              context,
                              onDemoDocument ??
                                  () => onMediaRequested('document'),
                            ),
                          ),
                          const SizedBox(width: 8),
                          _DemoTestPill(
                            icon: Icons.graphic_eq_rounded,
                            label: 'Test Music (MP3)',
                            gradient: const [
                              Color(0xFFFF9800),
                              Color(0xFFF57C00),
                            ],
                            onTap: () => _closeAnd(
                              context,
                              onDemoAudio ?? () => onMediaRequested('audio'),
                            ),
                          ),
                          const SizedBox(width: 8),
                          _DemoTestPill(
                            icon: Icons.call_end_rounded,
                            label: 'Test Call Card',
                            gradient: const [
                              Color(0xFF00BFA5),
                              Color(0xFF00897B),
                            ],
                            onTap: () =>
                                _closeAnd(context, onDemoCall ?? () {}),
                          ),
                          const SizedBox(width: 8),
                          _DemoTestPill(
                            icon: Icons.task_alt_rounded,
                            label: 'Test Task Card',
                            gradient: const [
                              Color(0xFF3B82F6),
                              Color(0xFF1D4ED8),
                            ],
                            onTap: () => _closeAnd(
                              context,
                              onDemoTask ?? onTaskOption,
                            ),
                          ),
                          const SizedBox(width: 8),
                          _DemoTestPill(
                            icon: Icons.pin_drop_rounded,
                            label: 'Test Location',
                            gradient: const [
                              Color(0xFF10B981),
                              Color(0xFF059669),
                            ],
                            onTap: () => _closeAnd(
                              context,
                              onDemoLocation ?? onLocationRequested,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: ChatySpacing.xs),
              LayoutBuilder(
                builder: (context, constraints) {
                  final textScale = MediaQuery.textScalerOf(context).scale(1);
                  final columns =
                      constraints.maxWidth >= 220 && textScale <= 1.6
                      ? 3
                      : 2;
                  const spacing = 8.0;
                  final itemWidth =
                      (constraints.maxWidth - (columns - 1) * spacing) /
                      columns;

                  return Wrap(
                    spacing: spacing,
                    runSpacing: 12,
                    children: actions
                        .map(
                          (item) => SizedBox(
                            width: itemWidth,
                            child: _AttachmentGridTile(
                              item: item,
                              onTap: () => _closeAnd(context, item.onTap),
                            ),
                          ),
                        )
                        .toList(growable: false),
                  );
                },
              ),
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

void _closeAnd(BuildContext context, VoidCallback action) {
  HapticFeedback.selectionClick();
  Navigator.of(context).pop();
  WidgetsBinding.instance.addPostFrameCallback((_) => action());
}

class _DemoTestPill extends StatelessWidget {
  const _DemoTestPill({
    required this.icon,
    required this.label,
    required this.gradient,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final List<Color> gradient;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(14),
        child: Container(
          constraints: const BoxConstraints(minHeight: 48),
          alignment: Alignment.center,
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          decoration: BoxDecoration(
            color: gradient.first.withValues(alpha: 0.12),
            borderRadius: BorderRadius.circular(14),
            border: Border.all(
              color: gradient.first.withValues(alpha: 0.3),
              width: 1,
            ),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 26,
                height: 26,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: LinearGradient(colors: gradient),
                ),
                child: Center(
                  child: Icon(icon, color: Colors.white, size: 14),
                ),
              ),
              const SizedBox(width: 8),
              Text(
                label,
                style: TextStyle(
                  color: theme.colorScheme.onSurface,
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                  letterSpacing: -0.1,
                ),
              ),
            ],
          ),
        ),
      ),
    );
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
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 76),
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
                      color: colors.primary.withValues(alpha: 0.12),
                    ),
                    child: Center(
                      child: Icon(item.icon, color: colors.primary, size: 28),
                    ),
                  ),
                  const SizedBox(height: 7),
                  Text(
                    item.label,
                    textAlign: TextAlign.center,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color: colors.foreground,
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      letterSpacing: -0.1,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
