import 'dart:ui';

import 'package:flutter/material.dart';

/// Signature Chaty surfaces. These are intentionally brand-specific rather
/// than imitations of another messenger's chrome.

class ChatyActivityIsland extends StatelessWidget {
  const ChatyActivityIsland({
    super.key,
    required this.icon,
    required this.title,
    this.subtitle,
    this.progress,
    this.onTap,
  });

  final IconData icon;
  final String title;
  final String? subtitle;
  final double? progress;
  final VoidCallback? onTap;

  static void show(
    BuildContext context, {
    required IconData icon,
    required String title,
    String? subtitle,
  }) {
    final messenger = ScaffoldMessenger.of(context);
    messenger
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          elevation: 0,
          backgroundColor: Colors.transparent,
          behavior: SnackBarBehavior.floating,
          margin: const EdgeInsets.fromLTRB(16, 0, 16, 16),
          padding: EdgeInsets.zero,
          duration: const Duration(seconds: 3),
          content: ChatyActivityIsland(
            icon: icon,
            title: title,
            subtitle: subtitle,
          ),
        ),
      );
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final surface = Color.alphaBlend(
      scheme.primary.withValues(alpha: 0.10),
      scheme.surface,
    );
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(22),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(22),
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 18, sigmaY: 18),
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: surface.withValues(alpha: 0.94),
                borderRadius: BorderRadius.circular(22),
                border: Border.all(
                  color: scheme.primary.withValues(alpha: 0.20),
                ),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.14),
                    blurRadius: 24,
                    offset: const Offset(0, 9),
                  ),
                ],
              ),
              child: Padding(
                padding: const EdgeInsets.fromLTRB(14, 11, 14, 11),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Row(
                      children: [
                        Container(
                          width: 38,
                          height: 38,
                          decoration: BoxDecoration(
                            color: scheme.primary.withValues(alpha: 0.14),
                            borderRadius: BorderRadius.circular(13),
                          ),
                          child: Icon(icon, color: scheme.primary, size: 21),
                        ),
                        const SizedBox(width: 11),
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
                                  fontSize: 14,
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                              if (subtitle != null) ...[
                                const SizedBox(height: 2),
                                Text(
                                  subtitle!,
                                  maxLines: 2,
                                  overflow: TextOverflow.ellipsis,
                                  style: TextStyle(
                                    color: scheme.onSurfaceVariant,
                                    fontSize: 11.5,
                                    height: 1.25,
                                  ),
                                ),
                              ],
                            ],
                          ),
                        ),
                      ],
                    ),
                    if (progress != null) ...[
                      const SizedBox(height: 9),
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
            ),
          ),
        ),
      ),
    );
  }
}

class ChatyGlassSheet {
  static Future<T?> show<T>(
    BuildContext context, {
    required Widget child,
  }) {
    return showModalBottomSheet<T>(
      context: context,
      useSafeArea: true,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      barrierColor: Colors.black.withValues(alpha: 0.40),
      builder: (context) => ClipRRect(
        borderRadius: const BorderRadius.vertical(top: Radius.circular(30)),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 22, sigmaY: 22),
          child: Container(
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.surface.withValues(alpha: 0.96),
              borderRadius: const BorderRadius.vertical(top: Radius.circular(30)),
              border: Border(
                top: BorderSide(
                  color: Theme.of(context).colorScheme.primary.withValues(alpha: 0.18),
                ),
              ),
            ),
            child: child,
          ),
        ),
      ),
    );
  }
}

class ChatyPresenceAvatar extends StatelessWidget {
  const ChatyPresenceAvatar({
    super.key,
    required this.child,
    required this.size,
    this.online = false,
    this.typing = false,
    this.recording = false,
    this.hasUpdate = false,
  });

  final Widget child;
  final double size;
  final bool online;
  final bool typing;
  final bool recording;
  final bool hasUpdate;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final ring = recording
        ? scheme.error
        : typing
        ? scheme.tertiary
        : hasUpdate
        ? scheme.primary
        : Colors.transparent;
    return SizedBox(
      width: size,
      height: size,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          AnimatedContainer(
            duration: const Duration(milliseconds: 220),
            padding: EdgeInsets.all(ring == Colors.transparent ? 0 : 2.4),
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              border: ring == Colors.transparent
                  ? null
                  : Border.all(color: ring, width: 2),
            ),
            child: ClipOval(child: child),
          ),
          if (online)
            Positioned(
              right: 0,
              bottom: 0,
              child: Container(
                width: size * .25,
                height: size * .25,
                decoration: BoxDecoration(
                  color: const Color(0xFF21D07A),
                  shape: BoxShape.circle,
                  border: Border.all(color: scheme.surface, width: 2),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class ChatyTaskCard extends StatelessWidget {
  const ChatyTaskCard({
    super.key,
    required this.title,
    required this.onTap,
    this.status = 'Open',
    this.assignee,
  });

  final String title;
  final String status;
  final String? assignee;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(18),
        child: Container(
          constraints: const BoxConstraints(minWidth: 190, maxWidth: 320),
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: Color.alphaBlend(
              scheme.primary.withValues(alpha: .07),
              scheme.surfaceContainerHighest,
            ),
            borderRadius: BorderRadius.circular(18),
            border: Border.all(color: scheme.primary.withValues(alpha: .24)),
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 38,
                height: 38,
                decoration: BoxDecoration(
                  color: scheme.primary.withValues(alpha: .14),
                  borderRadius: BorderRadius.circular(13),
                ),
                child: Icon(Icons.task_alt_rounded, color: scheme.primary),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: scheme.onSurface,
                        fontSize: 13.5,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 5),
                    Wrap(
                      spacing: 6,
                      runSpacing: 4,
                      children: [
                        _MetaPill(label: status, color: scheme.primary),
                        if (assignee != null && assignee!.isNotEmpty)
                          _MetaPill(
                            label: assignee!,
                            color: scheme.tertiary,
                            icon: Icons.person_outline_rounded,
                          ),
                      ],
                    ),
                  ],
                ),
              ),
              Icon(
                Icons.chevron_right_rounded,
                color: scheme.onSurfaceVariant,
                size: 20,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _MetaPill extends StatelessWidget {
  const _MetaPill({required this.label, required this.color, this.icon});
  final String label;
  final Color color;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: .10),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: 11, color: color),
            const SizedBox(width: 3),
          ],
          Text(
            label,
            style: TextStyle(
              color: color,
              fontSize: 10.5,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    );
  }
}

class ChatyReactionBar extends StatelessWidget {
  const ChatyReactionBar({
    super.key,
    required this.onReaction,
    this.reactions = const ['❤️', '😂', '🔥', '👏', '😮', '👍'],
  });

  final ValueChanged<String> onReaction;
  final List<String> reactions;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 7),
      decoration: BoxDecoration(
        color: scheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(999),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: .12),
            blurRadius: 20,
            offset: const Offset(0, 7),
          ),
        ],
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: reactions
            .map(
              (reaction) => InkResponse(
                radius: 22,
                onTap: () => onReaction(reaction),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 6),
                  child: Text(reaction, style: const TextStyle(fontSize: 23)),
                ),
              ),
            )
            .toList(growable: false),
      ),
    );
  }
}

class ChatyActionDock extends StatelessWidget {
  const ChatyActionDock({super.key, required this.actions});
  final List<ChatyDockAction> actions;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.all(7),
      decoration: BoxDecoration(
        color: scheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(22),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: actions
            .map(
              (action) => IconButton(
                tooltip: action.label,
                onPressed: action.onPressed,
                icon: Icon(action.icon),
              ),
            )
            .toList(growable: false),
      ),
    );
  }
}

class ChatyDockAction {
  const ChatyDockAction({
    required this.icon,
    required this.label,
    required this.onPressed,
  });
  final IconData icon;
  final String label;
  final VoidCallback onPressed;
}

class ChatyMiniPlayer extends StatelessWidget {
  const ChatyMiniPlayer({
    super.key,
    required this.title,
    required this.playing,
    required this.onPlayPause,
    required this.onClose,
    this.progress = 0,
  });

  final String title;
  final bool playing;
  final VoidCallback onPlayPause;
  final VoidCallback onClose;
  final double progress;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      padding: const EdgeInsets.fromLTRB(8, 6, 8, 6),
      decoration: BoxDecoration(
        color: scheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Row(
        children: [
          IconButton.filledTonal(
            onPressed: onPlayPause,
            icon: Icon(playing ? Icons.pause_rounded : Icons.play_arrow_rounded),
          ),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 5),
                ClipRRect(
                  borderRadius: BorderRadius.circular(99),
                  child: LinearProgressIndicator(value: progress.clamp(0, 1)),
                ),
              ],
            ),
          ),
          IconButton(onPressed: onClose, icon: const Icon(Icons.close_rounded)),
        ],
      ),
    );
  }
}

class ChatyMediaCarousel extends StatelessWidget {
  const ChatyMediaCarousel({super.key, required this.children, this.height = 190});
  final List<Widget> children;
  final double height;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: height,
      child: ListView.separated(
        padding: const EdgeInsets.symmetric(horizontal: 12),
        scrollDirection: Axis.horizontal,
        itemCount: children.length,
        separatorBuilder: (_, __) => const SizedBox(width: 10),
        itemBuilder: (_, index) => ClipRRect(
          borderRadius: BorderRadius.circular(18),
          child: children[index],
        ),
      ),
    );
  }
}

class ChatyComposerShell extends StatelessWidget {
  const ChatyComposerShell({super.key, required this.child, this.commandMode = false});
  final Widget child;
  final bool commandMode;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return AnimatedContainer(
      duration: const Duration(milliseconds: 180),
      curve: Curves.easeOutCubic,
      margin: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: scheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(26),
        border: Border.all(
          color: commandMode
              ? scheme.primary.withValues(alpha: .45)
              : scheme.outlineVariant.withValues(alpha: .45),
        ),
        boxShadow: [
          BoxShadow(
            color: scheme.primary.withValues(alpha: commandMode ? .12 : .04),
            blurRadius: commandMode ? 18 : 10,
          ),
        ],
      ),
      child: child,
    );
  }
}

class ChatyQuickPeek extends StatelessWidget {
  const ChatyQuickPeek({super.key, required this.title, required this.child});
  final String title;
  final Widget child;

  static Future<void> show(
    BuildContext context, {
    required String title,
    required Widget child,
  }) => ChatyGlassSheet.show<void>(
    context,
    child: ChatyQuickPeek(title: title, child: child),
  );

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(18, 12, 18, 24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Center(
            child: Container(
              width: 42,
              height: 4,
              decoration: BoxDecoration(
                color: Theme.of(context).colorScheme.outlineVariant,
                borderRadius: BorderRadius.circular(99),
              ),
            ),
          ),
          const SizedBox(height: 16),
          Text(title, style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800)),
          const SizedBox(height: 14),
          child,
        ],
      ),
    );
  }
}

class ChatyCommandPalette {
  static Future<String?> show(BuildContext context) {
    final commands = const <(String, String, IconData)>[
      ('/task', 'Create a task from this conversation', Icons.task_alt_rounded),
      ('/poll', 'Ask everyone a question', Icons.poll_rounded),
      ('/remind', 'Create a reminder', Icons.notifications_active_rounded),
      ('/location', 'Share your current location', Icons.location_on_rounded),
      ('/schedule', 'Schedule an action', Icons.schedule_rounded),
    ];
    return ChatyGlassSheet.show<String>(
      context,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 12, 14, 20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const ListTile(
              title: Text('Chaty Commands', style: TextStyle(fontWeight: FontWeight.w800)),
              subtitle: Text('Turn a message into an action without leaving the chat.'),
            ),
            ...commands.map(
              (command) => ListTile(
                leading: Icon(command.$3),
                title: Text(command.$1, style: const TextStyle(fontWeight: FontWeight.w700)),
                subtitle: Text(command.$2),
                onTap: () => Navigator.pop(context, command.$1),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class ChatySmartSearchField extends StatelessWidget {
  const ChatySmartSearchField({
    super.key,
    required this.controller,
    required this.onChanged,
    this.hint = 'Search people, messages, media, links and tasks',
  });
  final TextEditingController controller;
  final ValueChanged<String> onChanged;
  final String hint;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return TextField(
      controller: controller,
      onChanged: onChanged,
      decoration: InputDecoration(
        hintText: hint,
        prefixIcon: const Icon(Icons.manage_search_rounded),
        filled: true,
        fillColor: scheme.surfaceContainerHighest,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(20),
          borderSide: BorderSide.none,
        ),
      ),
    );
  }
}
