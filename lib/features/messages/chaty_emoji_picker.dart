import 'package:animated_emoji/animated_emoji.dart';
import 'package:emoji_picker_flutter/emoji_picker_flutter.dart';
import 'package:flutter/material.dart';

class ChatyEmojiPicker {
  static Future<String?> show(BuildContext context, {bool reactionMode = false}) {
    return showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      builder: (_) => _ChatyEmojiPickerSheet(reactionMode: reactionMode),
    );
  }
}

class _ChatyEmojiPickerSheet extends StatefulWidget {
  final bool reactionMode;
  const _ChatyEmojiPickerSheet({required this.reactionMode});

  @override
  State<_ChatyEmojiPickerSheet> createState() => _ChatyEmojiPickerSheetState();
}

class _ChatyEmojiPickerSheetState extends State<_ChatyEmojiPickerSheet> with SingleTickerProviderStateMixin {
  late final TabController _tabController;

  static final List<AnimatedEmojiData> _animated = <AnimatedEmojiData>[
    AnimatedEmojis.thumbsUp,
    AnimatedEmojis.redHeart,
    AnimatedEmojis.fire,
    AnimatedEmojis.partyPopper,
    AnimatedEmojis.eyes,
    AnimatedEmojis.rocket,
    AnimatedEmojis.clap,
    AnimatedEmojis.laughing,
    AnimatedEmojis.joy,
    AnimatedEmojis.heartEyes,
    AnimatedEmojis.partyingFace,
  ];

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final height = MediaQuery.sizeOf(context).height * (widget.reactionMode ? 0.58 : 0.68);
    return SizedBox(
      height: height.clamp(360.0, 620.0),
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(18, 0, 18, 8),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    widget.reactionMode ? 'Choose reaction' : 'Choose emoji',
                    style: theme.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800),
                  ),
                ),
                IconButton(
                  tooltip: 'Close',
                  onPressed: () => Navigator.of(context).pop(),
                  icon: const Icon(Icons.close_rounded),
                ),
              ],
            ),
          ),
          TabBar(
            controller: _tabController,
            tabs: const [
              Tab(icon: Icon(Icons.emoji_emotions_outlined), text: 'Emoji'),
              Tab(icon: Icon(Icons.auto_awesome_rounded), text: 'Animated'),
            ],
          ),
          Expanded(
            child: TabBarView(
              controller: _tabController,
              children: [
                EmojiPicker(
                  onEmojiSelected: (category, emoji) => Navigator.of(context).pop(emoji.emoji),
                  config: Config(
                    height: height - 105,
                    checkPlatformCompatibility: true,
                    emojiViewConfig: const EmojiViewConfig(
                      emojiSizeMax: 30,
                      columns: 8,
                      recentsLimit: 32,
                    ),
                    categoryViewConfig: CategoryViewConfig(
                      backgroundColor: theme.colorScheme.surface,
                      iconColor: theme.colorScheme.onSurfaceVariant,
                      iconColorSelected: theme.colorScheme.primary,
                      indicatorColor: theme.colorScheme.primary,
                    ),
                    bottomActionBarConfig: const BottomActionBarConfig(enabled: true),
                    searchViewConfig: SearchViewConfig(
                      backgroundColor: theme.colorScheme.surface,
                      buttonIconColor: theme.colorScheme.primary,
                    ),
                  ),
                ),
                _AnimatedEmojiGrid(
                  values: _animated,
                  onSelected: (emoji) => Navigator.of(context).pop(emoji.toUnicodeEmoji()),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _AnimatedEmojiGrid extends StatelessWidget {
  final List<AnimatedEmojiData> values;
  final ValueChanged<AnimatedEmojiData> onSelected;

  const _AnimatedEmojiGrid({required this.values, required this.onSelected});

  @override
  Widget build(BuildContext context) {
    return GridView.builder(
      padding: const EdgeInsets.all(16),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 5,
        mainAxisSpacing: 12,
        crossAxisSpacing: 12,
      ),
      itemCount: values.length,
      itemBuilder: (context, index) {
        final emoji = values[index];
        return Material(
          color: Theme.of(context).colorScheme.surfaceContainerHighest.withValues(alpha: 0.55),
          borderRadius: BorderRadius.circular(16),
          child: InkWell(
            onTap: () => onSelected(emoji),
            borderRadius: BorderRadius.circular(16),
            child: Center(child: AnimatedEmoji(emoji, size: 42)),
          ),
        );
      },
    );
  }
}

AnimatedEmojiData? chatyAnimatedEmojiForUnicode(String value) {
  for (final emoji in <AnimatedEmojiData>[
    AnimatedEmojis.thumbsUp,
    AnimatedEmojis.redHeart,
    AnimatedEmojis.fire,
    AnimatedEmojis.partyPopper,
    AnimatedEmojis.eyes,
    AnimatedEmojis.rocket,
    AnimatedEmojis.clap,
    AnimatedEmojis.laughing,
    AnimatedEmojis.joy,
    AnimatedEmojis.heartEyes,
    AnimatedEmojis.partyingFace,
  ]) {
    if (emoji.toUnicodeEmoji() == value) return emoji;
  }
  return null;
}
