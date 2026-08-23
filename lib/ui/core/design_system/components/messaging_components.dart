import 'package:flutter/material.dart';

import '../chaty_haptics.dart';
import '../chaty_motion.dart';
import '../component_state.dart';

class ChatyConversationTile extends StatelessWidget {
  const ChatyConversationTile({
    super.key,
    required this.title,
    required this.preview,
    required this.timeLabel,
    required this.avatar,
    required this.onTap,
    this.onLongPress,
    this.unreadCount = 0,
    this.isPinned = false,
    this.isMuted = false,
    this.presence = ChatyPresenceState.offline,
    this.deliveryState,
    this.draft = false,
  });

  final String title;
  final String preview;
  final String timeLabel;
  final Widget avatar;
  final VoidCallback onTap;
  final VoidCallback? onLongPress;
  final int unreadCount;
  final bool isPinned;
  final bool isMuted;
  final ChatyPresenceState presence;
  final ChatyDeliveryState? deliveryState;
  final bool draft;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final hasUnread = unreadCount > 0;
    return Semantics(
      button: true,
      label:
          '$title. $preview. $timeLabel${hasUnread ? '. $unreadCount unread' : ''}',
      child: InkWell(
        onTap: onTap,
        onLongPress: onLongPress == null
            ? null
            : () {
                ChatyHaptics.threshold();
                onLongPress!();
              },
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 11),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Stack(
                clipBehavior: Clip.none,
                children: [
                  SizedBox.square(dimension: 52, child: avatar),
                  if (presence != ChatyPresenceState.offline)
                    Positioned(
                      right: -1,
                      bottom: 0,
                      child: _PresenceDot(presence: presence),
                    ),
                ],
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            title,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: scheme.onSurface,
                              fontSize: 16,
                              fontWeight: hasUnread
                                  ? FontWeight.w800
                                  : FontWeight.w700,
                              letterSpacing: -0.2,
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Text(
                          timeLabel,
                          style: TextStyle(
                            color: hasUnread
                                ? scheme.primary
                                : scheme.onSurfaceVariant,
                            fontSize: 11.5,
                            fontWeight: hasUnread
                                ? FontWeight.w700
                                : FontWeight.w500,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Row(
                      children: [
                        if (draft) ...[
                          Text(
                            'Draft',
                            style: TextStyle(
                              color: scheme.error,
                              fontWeight: FontWeight.w700,
                              fontSize: 12.5,
                            ),
                          ),
                          const SizedBox(width: 5),
                        ],
                        if (deliveryState != null) ...[
                          _DeliveryGlyph(state: deliveryState!),
                          const SizedBox(width: 5),
                        ],
                        Expanded(
                          child: Text(
                            preview,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: hasUnread
                                  ? scheme.onSurface
                                  : scheme.onSurfaceVariant,
                              fontSize: 13.5,
                              fontWeight: hasUnread
                                  ? FontWeight.w600
                                  : FontWeight.w400,
                            ),
                          ),
                        ),
                        if (isPinned) ...[
                          const SizedBox(width: 6),
                          Icon(
                            Icons.push_pin_rounded,
                            size: 15,
                            color: scheme.onSurfaceVariant,
                          ),
                        ],
                        if (isMuted) ...[
                          const SizedBox(width: 6),
                          Icon(
                            Icons.notifications_off_rounded,
                            size: 15,
                            color: scheme.onSurfaceVariant,
                          ),
                        ],
                        if (hasUnread) ...[
                          const SizedBox(width: 8),
                          Container(
                            constraints: const BoxConstraints(minWidth: 22),
                            height: 22,
                            padding: const EdgeInsets.symmetric(horizontal: 6),
                            alignment: Alignment.center,
                            decoration: BoxDecoration(
                              color: scheme.primary,
                              borderRadius: BorderRadius.circular(999),
                            ),
                            child: Text(
                              unreadCount > 99 ? '99+' : '$unreadCount',
                              style: TextStyle(
                                color: scheme.onPrimary,
                                fontSize: 10.5,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          ),
                        ],
                      ],
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
}

class ChatyReplyPreview extends StatelessWidget {
  const ChatyReplyPreview({
    super.key,
    required this.author,
    required this.preview,
    this.icon,
    this.onDismiss,
  });

  final String author;
  final String preview;
  final IconData? icon;
  final VoidCallback? onDismiss;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      constraints: const BoxConstraints(minHeight: 48),
      padding: const EdgeInsets.fromLTRB(10, 7, 6, 7),
      decoration: BoxDecoration(
        color: scheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(14),
      ),
      child: Row(
        children: [
          Container(
            width: 3,
            height: 34,
            decoration: BoxDecoration(
              color: scheme.primary,
              borderRadius: BorderRadius.circular(999),
            ),
          ),
          const SizedBox(width: 8),
          if (icon != null) ...[
            Icon(icon, size: 18, color: scheme.primary),
            const SizedBox(width: 7),
          ],
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  author,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: scheme.primary,
                    fontSize: 11.5,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 1),
                Text(
                  preview,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: scheme.onSurfaceVariant,
                    fontSize: 12.5,
                  ),
                ),
              ],
            ),
          ),
          if (onDismiss != null)
            Semantics(
              button: true,
              label: 'Cancel reply',
              child: IconButton(
                tooltip: 'Cancel reply',
                constraints: const BoxConstraints.tightFor(
                  width: 42,
                  height: 42,
                ),
                onPressed: onDismiss,
                icon: const Icon(Icons.close_rounded, size: 19),
              ),
            ),
        ],
      ),
    );
  }
}

class ChatyMessageMeta extends StatelessWidget {
  const ChatyMessageMeta({
    super.key,
    required this.timeLabel,
    required this.deliveryState,
    this.edited = false,
  });

  final String timeLabel;
  final ChatyDeliveryState deliveryState;
  final bool edited;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (edited)
          Text(
            'edited · ',
            style: TextStyle(
              color: scheme.onSurfaceVariant,
              fontSize: 10.5,
            ),
          ),
        Text(
          timeLabel,
          style: TextStyle(color: scheme.onSurfaceVariant, fontSize: 10.5),
        ),
        const SizedBox(width: 4),
        _DeliveryGlyph(state: deliveryState),
      ],
    );
  }
}

class ChatyPinnedRail extends StatelessWidget {
  const ChatyPinnedRail({
    super.key,
    required this.label,
    required this.position,
    required this.total,
    required this.onTap,
    this.onNext,
  });

  final String label;
  final int position;
  final int total;
  final VoidCallback onTap;
  final VoidCallback? onNext;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Material(
      color: scheme.surface,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
          child: Row(
            children: [
              Container(
                width: 3,
                height: 32,
                decoration: BoxDecoration(
                  color: scheme.primary,
                  borderRadius: BorderRadius.circular(999),
                ),
              ),
              const SizedBox(width: 9),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Pinned message',
                      style: TextStyle(
                        color: scheme.primary,
                        fontSize: 11,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    Text(
                      label,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: scheme.onSurface,
                        fontSize: 12.5,
                      ),
                    ),
                  ],
                ),
              ),
              Text(
                '${position.clamp(1, total)}/$total',
                style: TextStyle(
                  color: scheme.onSurfaceVariant,
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                ),
              ),
              if (onNext != null)
                IconButton(
                  tooltip: 'Next pinned message',
                  onPressed: onNext,
                  icon: const Icon(Icons.keyboard_arrow_down_rounded),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class ChatyPollCard extends StatelessWidget {
  const ChatyPollCard({
    super.key,
    required this.question,
    required this.options,
    required this.onVote,
    this.totalVotes = 0,
    this.multiSelect = false,
    this.closed = false,
  });

  final String question;
  final List<ChatyPollOption> options;
  final ValueChanged<int> onVote;
  final int totalVotes;
  final bool multiSelect;
  final bool closed;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      constraints: const BoxConstraints(minWidth: 230, maxWidth: 340),
      padding: const EdgeInsets.all(13),
      decoration: BoxDecoration(
        color: scheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: scheme.outlineVariant.withValues(alpha: .65),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.poll_rounded, color: scheme.primary, size: 20),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  question,
                  style: TextStyle(
                    color: scheme.onSurface,
                    fontSize: 14,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 11),
          for (var i = 0; i < options.length; i++) ...[
            _PollOptionTile(
              option: options[i],
              totalVotes: totalVotes,
              disabled: closed,
              onTap: () {
                ChatyHaptics.selection();
                onVote(i);
              },
            ),
            if (i != options.length - 1) const SizedBox(height: 7),
          ],
          const SizedBox(height: 10),
          Text(
            '${closed ? 'Poll closed' : multiSelect ? 'Select one or more' : 'Select one'} · $totalVotes vote${totalVotes == 1 ? '' : 's'}',
            style: TextStyle(
              color: scheme.onSurfaceVariant,
              fontSize: 10.5,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

class ChatyPollOption {
  const ChatyPollOption({
    required this.label,
    this.votes = 0,
    this.selected = false,
  });

  final String label;
  final int votes;
  final bool selected;
}

class ChatyFileCard extends StatelessWidget {
  const ChatyFileCard({
    super.key,
    required this.name,
    required this.sizeLabel,
    required this.onTap,
    this.extension,
    this.state = ChatyComponentState.ready,
    this.progress,
  });

  final String name;
  final String sizeLabel;
  final String? extension;
  final VoidCallback onTap;
  final ChatyComponentState state;
  final double? progress;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Semantics(
      button: true,
      label: 'File $name, $sizeLabel',
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(17),
        child: Container(
          constraints: const BoxConstraints(minWidth: 220, maxWidth: 340),
          padding: const EdgeInsets.all(11),
          decoration: BoxDecoration(
            color: scheme.surfaceContainerHighest,
            borderRadius: BorderRadius.circular(17),
          ),
          child: Row(
            children: [
              Container(
                width: 46,
                height: 46,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: scheme.primary.withValues(alpha: .12),
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Text(
                  (extension ?? 'FILE').replaceFirst('.', '').toUpperCase(),
                  maxLines: 1,
                  style: TextStyle(
                    color: scheme.primary,
                    fontSize: 10,
                    fontWeight: FontWeight.w900,
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: scheme.onSurface,
                        fontWeight: FontWeight.w700,
                        fontSize: 13,
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      _fileSubtitle(),
                      style: TextStyle(
                        color: scheme.onSurfaceVariant,
                        fontSize: 10.5,
                      ),
                    ),
                    if (progress != null) ...[
                      const SizedBox(height: 6),
                      ClipRRect(
                        borderRadius: BorderRadius.circular(99),
                        child: LinearProgressIndicator(
                          value: progress!.clamp(0, 1),
                          minHeight: 3,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
              const SizedBox(width: 6),
              Icon(_fileIcon(), color: scheme.onSurfaceVariant, size: 20),
            ],
          ),
        ),
      ),
    );
  }

  String _fileSubtitle() {
    return switch (state) {
      ChatyComponentState.uploading => '$sizeLabel · uploading',
      ChatyComponentState.offline => '$sizeLabel · queued offline',
      ChatyComponentState.error => '$sizeLabel · tap to retry',
      ChatyComponentState.locked => '$sizeLabel · protected',
      _ => sizeLabel,
    };
  }

  IconData _fileIcon() {
    return switch (state) {
      ChatyComponentState.uploading => Icons.cloud_upload_rounded,
      ChatyComponentState.offline => Icons.schedule_send_rounded,
      ChatyComponentState.error => Icons.refresh_rounded,
      ChatyComponentState.locked => Icons.lock_rounded,
      _ => Icons.open_in_new_rounded,
    };
  }
}

class ChatyLocationCard extends StatelessWidget {
  const ChatyLocationCard({
    super.key,
    required this.title,
    required this.subtitle,
    required this.onTap,
    this.preview,
    this.live = false,
    this.remainingLabel,
  });

  final String title;
  final String subtitle;
  final VoidCallback onTap;
  final Widget? preview;
  final bool live;
  final String? remainingLabel;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Semantics(
      button: true,
      label: '$title. $subtitle',
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(19),
        child: Container(
          clipBehavior: Clip.antiAlias,
          constraints: const BoxConstraints(minWidth: 230, maxWidth: 350),
          decoration: BoxDecoration(
            color: scheme.surfaceContainerHighest,
            borderRadius: BorderRadius.circular(19),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              SizedBox(
                height: 118,
                child:
                    preview ??
                    DecoratedBox(
                      decoration: BoxDecoration(
                        gradient: LinearGradient(
                          begin: Alignment.topLeft,
                          end: Alignment.bottomRight,
                          colors: [
                            scheme.primary.withValues(alpha: .10),
                            scheme.tertiary.withValues(alpha: .18),
                          ],
                        ),
                      ),
                      child: Center(
                        child: Icon(
                          Icons.location_on_rounded,
                          color: scheme.primary,
                          size: 38,
                        ),
                      ),
                    ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(12, 10, 10, 10),
                child: Row(
                  children: [
                    Icon(
                      live ? Icons.my_location_rounded : Icons.place_rounded,
                      color: live ? scheme.error : scheme.primary,
                      size: 20,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            title,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: scheme.onSurface,
                              fontWeight: FontWeight.w800,
                              fontSize: 13,
                            ),
                          ),
                          Text(
                            remainingLabel == null
                                ? subtitle
                                : '$subtitle · $remainingLabel',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: scheme.onSurfaceVariant,
                              fontSize: 10.5,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const Icon(Icons.open_in_new_rounded, size: 19),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class ChatyMediaGrid extends StatelessWidget {
  const ChatyMediaGrid({super.key, required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    if (children.isEmpty) return const SizedBox.shrink();
    if (children.length == 1) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(18),
        child: AspectRatio(aspectRatio: 4 / 3, child: children.first),
      );
    }
    final count = children.length.clamp(2, 4);
    return ClipRRect(
      borderRadius: BorderRadius.circular(18),
      child: AspectRatio(
        aspectRatio: 1,
        child: GridView.builder(
          physics: const NeverScrollableScrollPhysics(),
          padding: EdgeInsets.zero,
          gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: 2,
            mainAxisSpacing: 2,
            crossAxisSpacing: 2,
          ),
          itemCount: count,
          itemBuilder: (context, index) {
            if (index == 3 && children.length > 4) {
              return Stack(
                fit: StackFit.expand,
                children: [
                  children[index],
                  ColoredBox(
                    color: Colors.black.withValues(alpha: .46),
                    child: Center(
                      child: Text(
                        '+${children.length - 4}',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 24,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ),
                  ),
                ],
              );
            }
            return children[index];
          },
        ),
      ),
    );
  }
}

class _PollOptionTile extends StatelessWidget {
  const _PollOptionTile({
    required this.option,
    required this.totalVotes,
    required this.disabled,
    required this.onTap,
  });

  final ChatyPollOption option;
  final int totalVotes;
  final bool disabled;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final ratio = totalVotes <= 0 ? 0.0 : option.votes / totalVotes;
    return Semantics(
      button: !disabled,
      selected: option.selected,
      label: '${option.label}, ${option.votes} votes',
      child: InkWell(
        onTap: disabled ? null : onTap,
        borderRadius: BorderRadius.circular(13),
        child: Container(
          height: 44,
          clipBehavior: Clip.antiAlias,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(13),
            border: Border.all(
              color: option.selected
                  ? scheme.primary
                  : scheme.outlineVariant.withValues(alpha: .7),
            ),
          ),
          child: Stack(
            fit: StackFit.expand,
            children: [
              Align(
                alignment: Alignment.centerLeft,
                child: AnimatedFractionallySizedBox(
                  duration: ChatyMotion.base,
                  curve: ChatyMotion.enter,
                  widthFactor: ratio.clamp(0, 1),
                  child: ColoredBox(
                    color: scheme.primary.withValues(alpha: .10),
                  ),
                ),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 11),
                child: Row(
                  children: [
                    Icon(
                      option.selected
                          ? Icons.check_circle_rounded
                          : Icons.circle_outlined,
                      color: option.selected
                          ? scheme.primary
                          : scheme.onSurfaceVariant,
                      size: 18,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        option.label,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          color: scheme.onSurface,
                          fontSize: 12.5,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                    if (totalVotes > 0)
                      Text(
                        '${(ratio * 100).round()}%',
                        style: TextStyle(
                          color: scheme.onSurfaceVariant,
                          fontSize: 10.5,
                          fontWeight: FontWeight.w700,
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
}

class _PresenceDot extends StatelessWidget {
  const _PresenceDot({required this.presence});

  final ChatyPresenceState presence;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final color = switch (presence) {
      ChatyPresenceState.online => const Color(0xFF20B98A),
      ChatyPresenceState.typing => scheme.primary,
      ChatyPresenceState.recording => scheme.error,
      ChatyPresenceState.calling => scheme.tertiary,
      ChatyPresenceState.offline => Colors.transparent,
    };
    return Container(
      width: 14,
      height: 14,
      decoration: BoxDecoration(
        color: color,
        shape: BoxShape.circle,
        border: Border.all(color: scheme.surface, width: 2.2),
      ),
    );
  }
}

class _DeliveryGlyph extends StatelessWidget {
  const _DeliveryGlyph({required this.state});

  final ChatyDeliveryState state;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final (icon, color) = switch (state) {
      ChatyDeliveryState.local =>
        (Icons.schedule_rounded, scheme.onSurfaceVariant),
      ChatyDeliveryState.queued =>
        (Icons.schedule_send_rounded, scheme.onSurfaceVariant),
      ChatyDeliveryState.sending => (Icons.sync_rounded, scheme.primary),
      ChatyDeliveryState.sent =>
        (Icons.check_rounded, scheme.onSurfaceVariant),
      ChatyDeliveryState.delivered =>
        (Icons.done_all_rounded, scheme.onSurfaceVariant),
      ChatyDeliveryState.read => (Icons.done_all_rounded, scheme.primary),
      ChatyDeliveryState.failed =>
        (Icons.error_outline_rounded, scheme.error),
    };
    return Icon(icon, size: 15, color: color);
  }
}
