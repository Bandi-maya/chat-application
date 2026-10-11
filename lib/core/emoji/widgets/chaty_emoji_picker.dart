import 'package:emoji_picker_flutter/emoji_picker_flutter.dart';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../emoji_registry.dart';
import '../services/emoji_recent_cache.dart';
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
    Color? headerColor,
    Color? headerIconsColor,
    Color? backgroundColor,
  }) {
    return showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      enableDrag: false,
      showDragHandle: false,
      // Open at approximately keyboard height; the user can drag up for more.
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
      clipBehavior: Clip.hardEdge,
      backgroundColor: backgroundColor ?? Theme.of(context).colorScheme.surface,
      builder: (_) => _ChatyEmojiPickerSheet(
        reactionMode: reactionMode,
        headerColor: headerColor,
        headerIconsColor: headerIconsColor,
        backgroundColor: backgroundColor,
      ),
    );
  }
}

class _ChatyEmojiPickerSheet extends StatefulWidget {
  final bool reactionMode;
  final Color? headerColor;
  final Color? headerIconsColor;
  final Color? backgroundColor;
  const _ChatyEmojiPickerSheet({
    required this.reactionMode,
    this.headerColor,
    this.headerIconsColor,
    this.backgroundColor,
  });

  @override
  State<_ChatyEmojiPickerSheet> createState() => _ChatyEmojiPickerSheetState();
}

class _ChatyEmojiPickerSheetState extends State<_ChatyEmojiPickerSheet>
    with SingleTickerProviderStateMixin {
  late final TabController _tabController;
  final TextEditingController _searchCtrl = TextEditingController();
  String _searchQuery = '';
  List<ChatyEmojiEntry> _filteredAnimated = const <ChatyEmojiEntry>[];
  double _heightFactor = 0.42;

  @override
  void initState() {
    super.initState();
    _heightFactor = widget.reactionMode ? 0.32 : 0.42;
    _tabController = TabController(length: 2, vsync: this);
    _filteredAnimated = ChatyEmojiRegistry.entries;
    _searchCtrl.addListener(_onSearchChanged);
    EmojiRecentCache.instance.initialize().then((_) {
      if (mounted) setState(() {});
    });
  }

  void _onSearchChanged() {
    final q = _searchCtrl.text.trim().toLowerCase();
    if (q == _searchQuery) return;
    setState(() {
      _searchQuery = q;
      if (q.isEmpty) {
        _filteredAnimated = ChatyEmojiRegistry.entries;
      } else {
        _filteredAnimated = ChatyEmojiRegistry.entries
            .where((entry) => entry.matches(q))
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
    final availableHeight = MediaQuery.sizeOf(context).height;
    final maxSheetHeight = availableHeight * 0.90;
    final height = (availableHeight * _heightFactor)
        .clamp(math.min(250.0, maxSheetHeight), maxSheetHeight)
        .toDouble();

    final headerColor = widget.headerColor;
    final headerIconsColor = widget.headerIconsColor;
    final backgroundColor = widget.backgroundColor;

    return Container(
      color: backgroundColor,
      child: SizedBox(
        height: height,
        child: Column(
          children: [
            GestureDetector(
              behavior: HitTestBehavior.opaque,
              onVerticalDragUpdate: (details) {
                if (availableHeight <= 0) return;
                setState(() {
                  _heightFactor = (_heightFactor -
                          details.delta.dy / availableHeight)
                      .clamp(0.32, 0.90)
                      .toDouble();
                });
              },
              child: SizedBox(
                height: 22,
                width: double.infinity,
                child: Center(
                  child: Container(
                    width: 42,
                    height: 4,
                    decoration: BoxDecoration(
                      color: theme.colorScheme.onSurface.withValues(alpha: 0.32),
                      borderRadius: BorderRadius.zero,
                    ),
                  ),
                ),
              ),
            ),
            Container(
              color: headerColor,
              padding: const EdgeInsets.fromLTRB(18, 2, 14, 8),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      widget.reactionMode ? 'Choose reaction' : 'Choose emoji',
                      style: theme.textTheme.titleLarge?.copyWith(
                        fontWeight: FontWeight.w800,
                        color: headerIconsColor,
                      ),
                    ),
                  ),
                  IconButton(
                    tooltip: 'Close',
                    onPressed: () => Navigator.of(context).pop(),
                    icon: Icon(Icons.close_rounded, color: headerIconsColor),
                  ),
                ],
              ),
            ),
          const SizedBox(height: 8),
          Expanded(
            child: _buildCombinedEmojiPicker(context, height),
          ),
        ],
      ),
    ),
  );
}

  Widget _buildCombinedEmojiPicker(BuildContext context, double height) {
    // A single, clearly named Emojis tab avoids duplicate choices. Animated
    // emoji rendering remains enabled in messages and reactions as before.
    return _buildUnicodePicker(context, height);
  }

  Widget _buildAnimatedTab(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

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
        if (_searchQuery.isEmpty && EmojiRecentCache.instance.items.isNotEmpty) ...[
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 4),
            child: Row(
              children: [
                Icon(Icons.history_rounded, size: 16, color: colorScheme.primary),
                const SizedBox(width: 6),
                Text(
                  'Recently Used',
                  style: theme.textTheme.labelMedium?.copyWith(
                    fontWeight: FontWeight.w700,
                    color: colorScheme.primary,
                  ),
                ),
              ],
            ),
          ),
          SizedBox(
            height: 48,
            child: ListView.builder(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 14),
              itemCount: EmojiRecentCache.instance.items.length,
              itemBuilder: (context, idx) {
                final recent = EmojiRecentCache.instance.items[idx];
                return Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 3),
                  child: Tooltip(
                    message: recent.label,
                    child: InkWell(
                      borderRadius: BorderRadius.circular(10),
                      onTap: () async {
                        HapticFeedback.lightImpact();
                        await EmojiRecentCache.instance.recordUsage(
                          recent.unicode,
                          label: recent.label,
                        );
                        if (context.mounted) Navigator.of(context).pop(recent.unicode);
                      },
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                        decoration: BoxDecoration(
                          color: colorScheme.surfaceContainerHighest.withValues(alpha: 0.3),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Center(
                          child: Text(
                            recent.unicode,
                            style: const TextStyle(fontSize: 22),
                          ),
                        ),
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
          const SizedBox(height: 4),
        ],
        Expanded(
          child: _filteredAnimated.isEmpty
              ? Center(
                  child: Text(
                    'No animated emojis found for "$_searchQuery"',
                    style: TextStyle(color: colorScheme.onSurfaceVariant),
                  ),
                )
              : LayoutBuilder(
                  builder: (context, constraints) {
                    final textScale = MediaQuery.textScalerOf(context).scale(1);
                    final columns =
                        (constraints.maxWidth / (textScale > 1.3 ? 88 : 72))
                            .floor()
                            .clamp(3, 6);
                    return GridView.builder(
                      padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
                      cacheExtent: 96,
                      addAutomaticKeepAlives: false,
                      addRepaintBoundaries: true,
                      addSemanticIndexes: true,
                      gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                        crossAxisCount: columns,
                        mainAxisSpacing: 10,
                        crossAxisSpacing: 10,
                        childAspectRatio: 0.82,
                      ),
                      itemCount: _filteredAnimated.length,
                      itemBuilder: (context, index) {
                        final entry = _filteredAnimated[index];
                        final unicode = entry.unicode;
                        return Material(
                          color: colorScheme.surfaceContainerHighest.withValues(
                            alpha: 0.35,
                          ),
                          borderRadius: BorderRadius.circular(14),
                          child: InkWell(
                            onTap: () async {
                              HapticFeedback.lightImpact();
                              await EmojiRecentCache.instance.recordUsage(
                                unicode,
                                label: entry.label,
                              );
                              if (context.mounted) Navigator.of(context).pop(unicode);
                            },
                            borderRadius: BorderRadius.circular(14),
                            child: Semantics(
                              button: true,
                              label: entry.label,
                              child: Padding(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 4,
                                  vertical: 6,
                                ),
                                child: Column(
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  children: [
                                    AnimatedEmojiView(
                                      unicode: unicode,
                                      size: 36.0,
                                      animate: !MediaQuery.disableAnimationsOf(
                                        context,
                                      ),
                                      mode: EmojiDisplayMode.picker,
                                    ),
                                    const SizedBox(height: 4),
                                    Text(
                                      entry.label,
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      textAlign: TextAlign.center,
                                      style: Theme.of(
                                        context,
                                      ).textTheme.labelSmall,
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ),
                        );
                      },
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
        onEmojiSelected: (category, emoji) async {
          HapticFeedback.selectionClick();
          await EmojiRecentCache.instance.recordUsage(emoji.emoji, label: emoji.name);
          if (context.mounted) Navigator.of(context).pop(emoji.emoji);
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
