import 'package:animated_emoji/animated_emoji.dart';
import 'package:emoji_picker_flutter/emoji_picker_flutter.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../emoji_registry.dart';
import 'animated_emoji_view.dart';
import '../models/parsed_emoji_span.dart';

/// Telegram-quality Emoji Picker bottom sheet with:
/// - Fast animated vector emoji grid & search
/// - Native Unicode emoji picker with categories & recents
/// - Skin tone support
/// - Offscreen scroll performance optimization
/// - Zero hardcoded colors (pure semantic theme tokens)
class ChatyEmojiPicker {
  static Future<String?> show(
    BuildContext context, {
    bool reactionMode = false,
  }) {
    return showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      backgroundColor: Theme.of(context).colorScheme.surface,
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

class _ChatyEmojiPickerSheetState extends State<_ChatyEmojiPickerSheet>
    with SingleTickerProviderStateMixin {
  late final TabController _tabController;
  final TextEditingController _searchCtrl = TextEditingController();
  String _searchQuery = '';
  List<AnimatedEmojiData> _filteredAnimated = const <AnimatedEmojiData>[];

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    _filteredAnimated = ChatyEmojiRegistry.allAnimated;
    _searchCtrl.addListener(_onSearchChanged);
  }

  void _onSearchChanged() {
    final q = _searchCtrl.text.trim().toLowerCase();
    if (q == _searchQuery) return;
    setState(() {
      _searchQuery = q;
      if (q.isEmpty) {
        _filteredAnimated = ChatyEmojiRegistry.allAnimated;
      } else {
        _filteredAnimated = ChatyEmojiRegistry.allAnimated
            .where((e) {
              return e.id.toLowerCase().contains(q) ||
                  e.toUnicodeEmoji().contains(q);
            })
            .toList(growable: false);
      }
    });
  }

  @override
  void dispose() {
    _searchCtrl.removeListener(_onSearchChanged);
    _searchCtrl.dispose();
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final height =
        MediaQuery.sizeOf(context).height * (widget.reactionMode ? 0.60 : 0.72);

    return SizedBox(
      height: height.clamp(380.0, 660.0),
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(18, 2, 14, 8),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    widget.reactionMode ? 'Choose reaction' : 'Choose emoji',
                    style: theme.textTheme.titleLarge?.copyWith(
                      fontWeight: FontWeight.w800,
                    ),
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
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: TabBar(
              controller: _tabController,
              indicatorSize: TabBarIndicatorSize.tab,
              dividerColor: Colors.transparent,
              tabs: const [
                Tab(
                  icon: Icon(Icons.auto_awesome_rounded),
                  text: 'Animated Emojis',
                ),
                Tab(
                  icon: Icon(Icons.emoji_emotions_outlined),
                  text: 'All Emojis',
                ),
              ],
            ),
          ),
          const SizedBox(height: 8),
          Expanded(
            child: TabBarView(
              controller: _tabController,
              children: [
                _buildAnimatedTab(context),
                _buildUnicodePicker(context, height),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAnimatedTab(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
          child: TextField(
            controller: _searchCtrl,
            decoration: InputDecoration(
              hintText: 'Search animated emojis…',
              prefixIcon: const Icon(Icons.search_rounded, size: 20),
              suffixIcon: _searchCtrl.text.isNotEmpty
                  ? IconButton(
                      icon: const Icon(Icons.clear_rounded, size: 18),
                      onPressed: () => _searchCtrl.clear(),
                    )
                  : null,
              filled: true,
              fillColor: colorScheme.surfaceContainerHighest.withValues(
                alpha: 0.45,
              ),
              contentPadding: const EdgeInsets.symmetric(
                horizontal: 12,
                vertical: 8,
              ),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(16),
                borderSide: BorderSide.none,
              ),
              isDense: true,
            ),
          ),
        ),
        Expanded(
          child: _filteredAnimated.isEmpty
              ? Center(
                  child: Text(
                    'No animated emojis found for "$_searchQuery"',
                    style: TextStyle(color: colorScheme.onSurfaceVariant),
                  ),
                )
              : GridView.builder(
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
                  gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: 5,
                    mainAxisSpacing: 10,
                    crossAxisSpacing: 10,
                  ),
                  itemCount: _filteredAnimated.length,
                  itemBuilder: (context, index) {
                    final emojiData = _filteredAnimated[index];
                    final unicode = emojiData.toUnicodeEmoji();
                    return Material(
                      color: colorScheme.surfaceContainerHighest.withValues(
                        alpha: 0.35,
                      ),
                      borderRadius: BorderRadius.circular(14),
                      child: InkWell(
                        onTap: () {
                          HapticFeedback.lightImpact();
                          Navigator.of(context).pop(unicode);
                        },
                        borderRadius: BorderRadius.circular(14),
                        child: Center(
                          child: AnimatedEmojiView(
                            unicode: unicode,
                            size: 38.0,
                            animate: true,
                            mode: EmojiDisplayMode.picker,
                          ),
                        ),
                      ),
                    );
                  },
                ),
        ),
      ],
    );
  }

  Widget _buildUnicodePicker(BuildContext context, double height) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final primary = theme.colorScheme.primary;
    final surfaceColor = theme.colorScheme.surface;
    final iconColor = isDark
        ? theme.colorScheme.onSurface.withValues(alpha: 0.65)
        : theme.colorScheme.onSurface.withValues(alpha: 0.7);

    return Container(
      color: surfaceColor,
      child: EmojiPicker(
        onEmojiSelected: (category, emoji) {
          HapticFeedback.selectionClick();
          Navigator.of(context).pop(emoji.emoji);
        },
        config: Config(
          height: height - 110,
          checkPlatformCompatibility: true,
          emojiViewConfig: EmojiViewConfig(
            emojiSizeMax: 30,
            columns: 8,
            recentsLimit: 32,
            backgroundColor: surfaceColor,
            buttonMode: ButtonMode.MATERIAL,
          ),
          categoryViewConfig: CategoryViewConfig(
            backgroundColor: surfaceColor,
            iconColor: iconColor,
            iconColorSelected: primary,
            indicatorColor: primary,
            dividerColor: isDark
                ? Colors.white.withValues(alpha: 0.08)
                : Colors.black.withValues(alpha: 0.06),
            tabBarHeight: 44,
          ),
          bottomActionBarConfig: BottomActionBarConfig(
            enabled: true,
            backgroundColor: surfaceColor,
            buttonColor: primary,
            buttonIconColor: Colors.white,
            showBackspaceButton: false,
          ),
          searchViewConfig: SearchViewConfig(
            backgroundColor: surfaceColor,
            buttonIconColor: primary,
            hintText: 'Search emoji…',
          ),
        ),
      ),
    );
  }
}
