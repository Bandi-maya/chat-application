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
    final groups = <_OrbitGroup>[
      _OrbitGroup('Media', <_OrbitAction>[
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
      ]),
      _OrbitGroup('Files', <_OrbitAction>[
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
      ]),
      _OrbitGroup('People & places', <_OrbitAction>[
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
      ]),
      _OrbitGroup('Create', <_OrbitAction>[
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
      ]),
    ];

    return Container(
      constraints: BoxConstraints(
        maxHeight: MediaQuery.sizeOf(context).height * .78,
      ),
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(30)),
      ),
      child: SafeArea(
        top: false,
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(
            ChatySpacing.base,
            ChatySpacing.sm,
            ChatySpacing.base,
            ChatySpacing.lg,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Center(
                child: Container(
                  width: 38,
                  height: 4.5,
                  decoration: BoxDecoration(
                    color: colors.foregroundSecondary.withValues(alpha: .2),
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
                      Icons.add_rounded,
                      color: colors.primary,
                      size: 24,
                    ),
                  ),
                  const SizedBox(width: 11),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Create or share',
                          style: TextStyle(
                            color: colors.foreground,
                            fontSize: 17,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          'Choose what you want to add to this conversation.',
                          style: ChatyTypography.caption(
                            colors.foregroundSecondary,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Semantics(
                    label: 'Media encryption enabled',
                    child: Container(
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
                  ),
                ],
              ),
              const SizedBox(height: ChatySpacing.base),
              for (int index = 0; index < groups.length; index++) ...[
                _OrbitSection(
                  group: groups[index],
                  onAction: (action) => _closeAnd(context, action.onTap),
                ),
                if (index != groups.length - 1)
                  const SizedBox(height: ChatySpacing.base),
              ],
              const SizedBox(height: ChatySpacing.base),
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 9,
                ),
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
                        'Attachments are encrypted on this device before upload.',
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
      ),
    );
  }

  void _closeAnd(BuildContext context, VoidCallback action) {
    HapticFeedback.selectionClick();
    Navigator.of(context).pop();
    WidgetsBinding.instance.addPostFrameCallback((_) => action());
  }
}

class _OrbitGroup {
  const _OrbitGroup(this.label, this.actions);

  final String label;
  final List<_OrbitAction> actions;
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

class _OrbitSection extends StatelessWidget {
  const _OrbitSection({required this.group, required this.onAction});

  final _OrbitGroup group;
  final ValueChanged<_OrbitAction> onAction;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(left: 2, bottom: 8),
          child: Text(
            group.label,
            style: TextStyle(
              color: colors.foregroundSecondary,
              fontSize: 11,
              fontWeight: FontWeight.w800,
              letterSpacing: .3,
            ),
          ),
        ),
        LayoutBuilder(
          builder: (context, constraints) {
            final gap = 10.0;
            final itemWidth = (constraints.maxWidth - gap) / 2;
            return Wrap(
              spacing: gap,
              runSpacing: gap,
              children: group.actions
                  .map(
                    (action) => SizedBox(
                      width: itemWidth,
                      child: _OrbitTile(
                        action: action,
                        onTap: () => onAction(action),
                      ),
                    ),
                  )
                  .toList(growable: false),
            );
          },
        ),
      ],
    );
  }
}

class _OrbitTile extends StatelessWidget {
  const _OrbitTile({required this.action, required this.onTap});

  final _OrbitAction action;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    return Semantics(
      button: true,
      label: '${action.label}. ${action.hint}',
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(18),
          onTap: onTap,
          child: Ink(
            height: 72,
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 11),
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
            child: Row(
              children: [
                Container(
                  width: 42,
                  height: 42,
                  decoration: BoxDecoration(
                    color: action.color.withValues(alpha: .13),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Icon(action.icon, color: action.color, size: 22),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        action.label,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          color: colors.foreground,
                          fontSize: 12.5,
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
                          fontSize: 10.5,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
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
