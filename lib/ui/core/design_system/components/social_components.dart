import 'package:flutter/material.dart';

import '../chaty_motion.dart';

class ChatyMomentRing extends StatelessWidget {
  const ChatyMomentRing({
    super.key,
    required this.avatar,
    required this.label,
    required this.onTap,
    this.seen = false,
    this.isMine = false,
  });

  final Widget avatar;
  final String label;
  final VoidCallback onTap;
  final bool seen;
  final bool isMine;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final ringColor = seen ? scheme.outlineVariant : scheme.primary;
    return Semantics(
      button: true,
      label: '${isMine ? 'Your' : label} moment${seen ? ', viewed' : ', new'}',
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: onTap,
        child: SizedBox(
          width: 74,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Stack(
                clipBehavior: Clip.none,
                children: [
                  AnimatedContainer(
                    duration: ChatyMotion.base,
                    width: 60,
                    height: 60,
                    padding: const EdgeInsets.all(2.5),
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      border: Border.all(color: ringColor, width: 2.2),
                    ),
                    child: ClipOval(child: avatar),
                  ),
                  if (isMine)
                    Positioned(
                      right: -1,
                      bottom: 0,
                      child: Container(
                        width: 20,
                        height: 20,
                        decoration: BoxDecoration(
                          color: scheme.primary,
                          shape: BoxShape.circle,
                          border: Border.all(color: scheme.surface, width: 2),
                        ),
                        child: Icon(
                          Icons.add_rounded,
                          color: scheme.onPrimary,
                          size: 14,
                        ),
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 5),
              Text(
                isMine ? 'Your moment' : label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: scheme.onSurface,
                  fontSize: 10.5,
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

class ChatyMomentProgress extends StatelessWidget {
  const ChatyMomentProgress({
    super.key,
    required this.count,
    required this.index,
    required this.progress,
  });

  final int count;
  final int index;
  final double progress;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        for (var i = 0; i < count; i++) ...[
          Expanded(
            child: ClipRRect(
              borderRadius: BorderRadius.circular(999),
              child: LinearProgressIndicator(
                value: i < index
                    ? 1
                    : i == index
                    ? progress.clamp(0, 1)
                    : 0,
                minHeight: 2.5,
                backgroundColor: Colors.white.withValues(alpha: .32),
                valueColor: const AlwaysStoppedAnimation<Color>(Colors.white),
              ),
            ),
          ),
          if (i != count - 1) const SizedBox(width: 3),
        ],
      ],
    );
  }
}

class ChatyStreamCard extends StatelessWidget {
  const ChatyStreamCard({
    super.key,
    required this.avatar,
    required this.title,
    required this.preview,
    required this.timeLabel,
    required this.onTap,
    this.unreadCount = 0,
    this.verified = false,
  });

  final Widget avatar;
  final String title;
  final String preview;
  final String timeLabel;
  final VoidCallback onTap;
  final int unreadCount;
  final bool verified;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 11),
        child: Row(
          children: [
            SizedBox.square(dimension: 46, child: avatar),
            const SizedBox(width: 11),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Flexible(
                        child: Text(
                          title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            color: scheme.onSurface,
                            fontSize: 14,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ),
                      if (verified) ...[
                        const SizedBox(width: 4),
                        Icon(
                          Icons.verified_rounded,
                          size: 15,
                          color: scheme.primary,
                        ),
                      ],
                      const Spacer(),
                      Text(
                        timeLabel,
                        style: TextStyle(
                          color: scheme.onSurfaceVariant,
                          fontSize: 10.5,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 3),
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          preview,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            color: scheme.onSurfaceVariant,
                            fontSize: 12.5,
                          ),
                        ),
                      ),
                      if (unreadCount > 0)
                        Container(
                          margin: const EdgeInsets.only(left: 8),
                          padding: const EdgeInsets.symmetric(
                            horizontal: 6,
                            vertical: 3,
                          ),
                          decoration: BoxDecoration(
                            color: scheme.primary,
                            borderRadius: BorderRadius.circular(999),
                          ),
                          child: Text(
                            unreadCount > 99 ? '99+' : '$unreadCount',
                            style: TextStyle(
                              color: scheme.onPrimary,
                              fontSize: 9.5,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class ChatyProfileCanvas extends StatelessWidget {
  const ChatyProfileCanvas({
    super.key,
    required this.banner,
    required this.avatar,
    required this.title,
    required this.subtitle,
    this.actions = const [],
    this.badges = const [],
  });

  final Widget banner;
  final Widget avatar;
  final String title;
  final String subtitle;
  final List<Widget> actions;
  final List<Widget> badges;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Stack(
          clipBehavior: Clip.none,
          children: [
            SizedBox(height: 178, width: double.infinity, child: banner),
            Positioned(
              left: 18,
              bottom: -38,
              child: Container(
                width: 86,
                height: 86,
                padding: const EdgeInsets.all(3),
                decoration: BoxDecoration(
                  color: scheme.surface,
                  shape: BoxShape.circle,
                ),
                child: ClipOval(child: avatar),
              ),
            ),
          ],
        ),
        const SizedBox(height: 48),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 18),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: TextStyle(
                        color: scheme.onSurface,
                        fontSize: 22,
                        fontWeight: FontWeight.w900,
                        letterSpacing: -.4,
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      subtitle,
                      style: TextStyle(
                        color: scheme.onSurfaceVariant,
                        fontSize: 12.5,
                      ),
                    ),
                    if (badges.isNotEmpty) ...[
                      const SizedBox(height: 9),
                      Wrap(spacing: 6, runSpacing: 6, children: badges),
                    ],
                  ],
                ),
              ),
              if (actions.isNotEmpty) Wrap(spacing: 4, children: actions),
            ],
          ),
        ),
      ],
    );
  }
}

class ChatyViewsSheet extends StatelessWidget {
  const ChatyViewsSheet({super.key, required this.items});

  final List<ChatyViewItem> items;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return SafeArea(
      top: false,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const SizedBox(height: 8),
          Container(
            width: 38,
            height: 4,
            decoration: BoxDecoration(
              color: scheme.outlineVariant,
              borderRadius: BorderRadius.circular(999),
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(18, 14, 18, 8),
            child: Row(
              children: [
                Text(
                  'Views',
                  style: TextStyle(
                    color: scheme.onSurface,
                    fontSize: 18,
                    fontWeight: FontWeight.w900,
                  ),
                ),
                const Spacer(),
                Text(
                  '${items.length}',
                  style: TextStyle(color: scheme.onSurfaceVariant),
                ),
              ],
            ),
          ),
          Flexible(
            child: ListView.builder(
              shrinkWrap: true,
              itemCount: items.length,
              itemBuilder: (context, index) {
                final item = items[index];
                return ListTile(
                  leading: item.avatar,
                  title: Text(item.name),
                  subtitle: Text(item.timeLabel),
                  trailing: item.reaction == null
                      ? null
                      : Text(
                          item.reaction!,
                          style: const TextStyle(fontSize: 22),
                        ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class ChatyViewItem {
  const ChatyViewItem({
    required this.avatar,
    required this.name,
    required this.timeLabel,
    this.reaction,
  });

  final Widget avatar;
  final String name;
  final String timeLabel;
  final String? reaction;
}
