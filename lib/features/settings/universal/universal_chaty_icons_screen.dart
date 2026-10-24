import 'package:flutter/material.dart';

import '../../../injection/locator.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart';
import '../../../ui/core/icons/chaty_icon_registry.dart';
import '../../../ui/core/icons/chaty_icon_theme.dart';

class UniversalChatyIconsScreen extends StatefulWidget {
  final ChatyPreferencesController preferencesController;

  const UniversalChatyIconsScreen({
    super.key,
    required this.preferencesController,
  });

  @override
  State<UniversalChatyIconsScreen> createState() =>
      _UniversalChatyIconsScreenState();
}

class _UniversalChatyIconsScreenState extends State<UniversalChatyIconsScreen> {
  final TextEditingController _searchCtrl = TextEditingController();
  String _selectedCategory = 'All';
  bool _previewIsDark = true;
  bool _microSizePreview = false;

  static const List<String> _categories = [
    'All',
    'Navigation',
    'Messaging',
    'Calls',
    'Camera',
    'Status',
    'Settings',
  ];

  static const List<ChatyIconId> _coreContactIcons = [
    ChatyIconId.search,
    ChatyIconId.back,
    ChatyIconId.more,
    ChatyIconId.camera,
    ChatyIconId.cameraFlip,
    ChatyIconId.cameraShutter,
    ChatyIconId.callAudio,
    ChatyIconId.callVideo,
    ChatyIconId.send,
    ChatyIconId.attachment,
    ChatyIconId.microphone,
    ChatyIconId.favourite,
    ChatyIconId.settings,
    ChatyIconId.messageCheckDouble,
  ];

  static const Map<String, List<ChatyIconId>> _categorizedIcons = {
    'Navigation': [
      ChatyIconId.chats,
      ChatyIconId.updates,
      ChatyIconId.calls,
      ChatyIconId.profile,
      ChatyIconId.tasks,
      ChatyIconId.groups,
      ChatyIconId.desktop,
    ],
    'Messaging': [
      ChatyIconId.send,
      ChatyIconId.emoji,
      ChatyIconId.attachment,
      ChatyIconId.attachmentPlus,
      ChatyIconId.microphone,
      ChatyIconId.pin,
      ChatyIconId.archive,
      ChatyIconId.favourite,
      ChatyIconId.delete,
      ChatyIconId.reply,
      ChatyIconId.forward,
      ChatyIconId.copy,
      ChatyIconId.share,
    ],
    'Calls': [
      ChatyIconId.callAudio,
      ChatyIconId.callVideo,
      ChatyIconId.callEnd,
      ChatyIconId.cameraSwitch,
      ChatyIconId.screenShare,
      ChatyIconId.speaker,
    ],
    'Camera': [
      ChatyIconId.camera,
      ChatyIconId.cameraFlip,
      ChatyIconId.cameraShutter,
      ChatyIconId.cameraFlashOn,
      ChatyIconId.gallery,
    ],
    'Status': [
      ChatyIconId.signal,
      ChatyIconId.messageCheckSingle,
      ChatyIconId.messageCheckDouble,
      ChatyIconId.messageClock,
    ],
    'Settings': [
      ChatyIconId.more,
      ChatyIconId.search,
      ChatyIconId.settings,
      ChatyIconId.tune,
      ChatyIconId.lock,
      ChatyIconId.close,
      ChatyIconId.check,
    ],
  };

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  void _applyTheme(ChatyIconThemeId theme) {
    ChatyHaptics.selection();
    final prefs = widget.preferencesController.universal;
    widget.preferencesController.updateUniversal(
      prefs.copyWith(iconTheme: theme.title),
    );
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          duration: const Duration(seconds: 2),
          content: Text(
            '${theme.title} icon theme applied across Chaty',
          ),
        ),
      );
  }

  void _resetToDefault() {
    ChatyHaptics.selection();
    _applyTheme(ChatyIconThemeId.chatySigil);
  }

  void _showIconDetailModal(ChatyIconId iconId, ThemeData gbTheme) {
    ChatyHaptics.light();
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        return Container(
          decoration: BoxDecoration(
            color: gbTheme.colorScheme.surface,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
          ),
          padding: EdgeInsets.fromLTRB(
            20,
            16,
            20,
            MediaQuery.paddingOf(ctx).bottom + 20,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Center(
                child: Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: Colors.white24,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              const SizedBox(height: 16),
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: gbTheme.colorScheme.primary.withValues(alpha: 0.15),
                      shape: BoxShape.circle,
                    ),
                    child: ChatyIcon(
                      iconId,
                      size: 28,
                      color: gbTheme.colorScheme.primary,
                    ),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          _iconDisplayName(iconId),
                          style: const TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        Text(
                          'Unique ID: ChatyIconId.${iconId.name}',
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            color: gbTheme.colorScheme.primary,
                          ),
                        ),
                        Text(
                          'Side-by-side preview across all 10 distinct icon themes. Applying a theme updates this icon everywhere it is displayed.',
                          style: TextStyle(
                            fontSize: 12.5,
                            color: gbTheme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 20),
              Flexible(
                child: GridView.builder(
                  shrinkWrap: true,
                  itemCount: ChatyIconThemeId.values.length,
                  gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: 5,
                    mainAxisSpacing: 12,
                    crossAxisSpacing: 10,
                    childAspectRatio: 0.85,
                  ),
                  itemBuilder: (context, index) {
                    final theme = ChatyIconThemeId.values[index];
                    return Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Container(
                          width: 48,
                          height: 48,
                          decoration: BoxDecoration(
                            color: gbTheme.colorScheme.surfaceContainerHighest
                                .withValues(alpha: 0.45),
                            borderRadius: BorderRadius.circular(12),
                            border: Border.all(
                              color: Colors.white12,
                            ),
                          ),
                          child: Center(
                            child: ChatyIcon(
                              iconId,
                              size: 24,
                              themeOverride: theme,
                              color: gbTheme.colorScheme.primary,
                            ),
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          theme.title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          textAlign: TextAlign.center,
                          style: const TextStyle(
                            fontSize: 9.5,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ],
                    );
                  },
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  String _iconDisplayName(ChatyIconId icon) {
    return icon.name
        .replaceAllMapped(RegExp(r'([a-z])([A-Z])'), (m) => '${m[1]} ${m[2]}')
        .split(' ')
        .map((w) => '${w[0].toUpperCase()}${w.substring(1)}')
        .join(' ');
  }

  List<ChatyIconId> _filteredIcons() {
    final query = _searchCtrl.text.trim().toLowerCase();
    List<ChatyIconId> baseList;
    if (_selectedCategory == 'All') {
      baseList = ChatyIconId.values;
    } else {
      baseList = _categorizedIcons[_selectedCategory] ?? ChatyIconId.values;
    }
    if (query.isEmpty) return baseList;
    return baseList.where((icon) {
      return icon.name.toLowerCase().contains(query);
    }).toList(growable: false);
  }

  @override
  Widget build(BuildContext context) {
    final themeController = locator<ThemeController>();
    return ListenableBuilder(
      listenable: Listenable.merge(<Listenable>[
        widget.preferencesController,
        themeController,
      ]),
      builder: (context, _) {
        final theme = themeController.globalTheme;
        final gbTheme = Theme.of(context);
        final currentThemeName =
            widget.preferencesController.universal.iconTheme;
        final activeTheme = ChatyIconThemeId.fromString(currentThemeName);
        final filteredList = _filteredIcons();
        final previewSize = _microSizePreview ? 20.0 : 26.0;

        return Scaffold(
          backgroundColor: theme.backgroundColor,
          appBar: AppBar(
            automaticallyImplyLeading: false,
            backgroundColor: theme.surfaceColor,
            elevation: 0,
            leading: const Padding(
              padding: EdgeInsets.all(8.0),
              child: ChatyBackButton(),
            ),
            title: Text(
              'Universal Chaty Icons',
              style: TextStyle(
                color: theme.primaryTextColor,
                fontSize: 19 * theme.fontScale,
                fontWeight: FontWeight.w700,
              ),
            ),
            actions: [
              TextButton.icon(
                onPressed: _resetToDefault,
                icon: const Icon(Icons.refresh_rounded, size: 16),
                label: const Text('Reset', style: TextStyle(fontSize: 12.5)),
                style: TextButton.styleFrom(
                  foregroundColor: theme.accentColor,
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                ),
              ),
            ],
          ),
          body: SafeArea(
            top: false,
            child: ListView(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
              children: [
                // SECTION 1: ACTIVE LIVE PREVIEW WITH LIGHT/DARK & SIZE TOGGLES
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                      colors: [
                        theme.accentColor.withValues(alpha: 0.18),
                        theme.surfaceColor,
                      ],
                    ),
                    borderRadius: BorderRadius.circular(18),
                    border: Border.all(
                      color: theme.accentColor.withValues(alpha: 0.35),
                      width: 1.5,
                    ),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'ACTIVE THEME',
                                style: TextStyle(
                                  color: theme.accentColor,
                                  fontSize: 11,
                                  fontWeight: FontWeight.w800,
                                  letterSpacing: 1.1,
                                ),
                              ),
                              const SizedBox(height: 2),
                              Text(
                                activeTheme.title,
                                style: TextStyle(
                                  color: theme.primaryTextColor,
                                  fontSize: 18,
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                            ],
                          ),
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 10,
                              vertical: 4,
                            ),
                            decoration: BoxDecoration(
                              color: theme.accentColor,
                              borderRadius: BorderRadius.circular(20),
                            ),
                            child: const Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(Icons.check, size: 14, color: Colors.black),
                                SizedBox(width: 4),
                                Text(
                                  'Applied',
                                  style: TextStyle(
                                    color: Colors.black,
                                    fontSize: 11,
                                    fontWeight: FontWeight.w800,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Text(
                        activeTheme.description,
                        style: TextStyle(
                          color: theme.secondaryTextColor,
                          fontSize: 12.5,
                        ),
                      ),
                      const SizedBox(height: 12),
                      // Preview Options (Light/Dark mode & Micro-size)
                      Row(
                        children: [
                          ChoiceChip(
                            label: Text(_previewIsDark ? 'Dark Preview' : 'Light Preview', style: const TextStyle(fontSize: 11)),
                            selected: _previewIsDark,
                            onSelected: (val) => setState(() => _previewIsDark = val),
                            selectedColor: theme.accentColor.withValues(alpha: 0.2),
                            avatar: Icon(_previewIsDark ? Icons.dark_mode : Icons.light_mode, size: 14),
                          ),
                          const SizedBox(width: 8),
                          ChoiceChip(
                            label: Text(_microSizePreview ? '20px Micro' : '26px Standard', style: const TextStyle(fontSize: 11)),
                            selected: _microSizePreview,
                            onSelected: (val) => setState(() => _microSizePreview = val),
                            selectedColor: theme.accentColor.withValues(alpha: 0.2),
                            avatar: const Icon(Icons.photo_size_select_small_rounded, size: 14),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      // Simulated Live Bar Preview
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 12,
                        ),
                        decoration: BoxDecoration(
                          color: _previewIsDark
                              ? Colors.black.withValues(alpha: 0.8)
                              : Colors.white,
                          borderRadius: BorderRadius.circular(14),
                          border: Border.all(
                            color: _previewIsDark ? Colors.white12 : Colors.black12,
                          ),
                        ),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceAround,
                          children: [
                            ChatyIcon(
                              ChatyIconId.chats,
                              size: previewSize,
                              color: theme.accentColor,
                              themeOverride: activeTheme,
                            ),
                            ChatyIcon(
                              ChatyIconId.search,
                              size: previewSize,
                              color: _previewIsDark ? Colors.white : Colors.black87,
                              themeOverride: activeTheme,
                            ),
                            ChatyIcon(
                              ChatyIconId.camera,
                              size: previewSize,
                              color: _previewIsDark ? Colors.white : Colors.black87,
                              themeOverride: activeTheme,
                            ),
                            ChatyIcon(
                              ChatyIconId.calls,
                              size: previewSize,
                              color: _previewIsDark ? Colors.white : Colors.black87,
                              themeOverride: activeTheme,
                            ),
                            ChatyIcon(
                              ChatyIconId.signal,
                              size: previewSize * 0.9,
                              color: const Color(0xFF10B981),
                              themeOverride: activeTheme,
                            ),
                            ChatyIcon(
                              ChatyIconId.more,
                              size: previewSize,
                              color: _previewIsDark ? Colors.white : Colors.black87,
                              themeOverride: activeTheme,
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 20),

                // SECTION 2: 10 DISTINCT THEME SELECTION CARDS
                Text(
                  '10 ICON THEMES (GENUINELY DISTINCT SHAPE GRAMMARS)',
                  style: TextStyle(
                    color: theme.secondaryTextColor,
                    fontSize: 11.5,
                    fontWeight: FontWeight.w800,
                    letterSpacing: 0.8,
                  ),
                ),
                const SizedBox(height: 10),
                ListView.separated(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  itemCount: ChatyIconThemeId.values.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 10),
                  itemBuilder: (context, index) {
                    final itemTheme = ChatyIconThemeId.values[index];
                    final isSelected = activeTheme == itemTheme;
                    return InkWell(
                      borderRadius: BorderRadius.circular(16),
                      onTap: () => _applyTheme(itemTheme),
                      child: Container(
                        padding: const EdgeInsets.all(14),
                        decoration: BoxDecoration(
                          color: isSelected
                              ? theme.accentColor.withValues(alpha: 0.12)
                              : theme.surfaceColor,
                          borderRadius: BorderRadius.circular(16),
                          border: Border.all(
                            color: isSelected
                                ? theme.accentColor
                                : Colors.white10,
                            width: isSelected ? 2.0 : 1.0,
                          ),
                        ),
                        child: Row(
                          children: [
                            // Theme Preview Icon Group (Chats, More, Camera, Search, Heart)
                            Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 10,
                                vertical: 8,
                              ),
                              decoration: BoxDecoration(
                                color: _previewIsDark
                                    ? Colors.black.withValues(alpha: 0.45)
                                    : Colors.white,
                                borderRadius: BorderRadius.circular(12),
                                border: Border.all(
                                  color: _previewIsDark ? Colors.white10 : Colors.black12,
                                ),
                              ),
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  ChatyIcon(
                                    ChatyIconId.chats,
                                    size: previewSize * 0.9,
                                    color: isSelected
                                        ? theme.accentColor
                                        : (_previewIsDark ? Colors.white : Colors.black),
                                    themeOverride: itemTheme,
                                  ),
                                  const SizedBox(width: 8),
                                  ChatyIcon(
                                    ChatyIconId.more,
                                    size: previewSize * 0.9,
                                    color: isSelected
                                        ? theme.accentColor
                                        : (_previewIsDark ? Colors.white : Colors.black),
                                    themeOverride: itemTheme,
                                  ),
                                  const SizedBox(width: 8),
                                  ChatyIcon(
                                    ChatyIconId.camera,
                                    size: previewSize * 0.9,
                                    color: isSelected
                                        ? theme.accentColor
                                        : (_previewIsDark ? Colors.white : Colors.black),
                                    themeOverride: itemTheme,
                                  ),
                                  const SizedBox(width: 8),
                                  ChatyIcon(
                                    ChatyIconId.favourite,
                                    size: previewSize * 0.9,
                                    color: isSelected
                                        ? theme.accentColor
                                        : (_previewIsDark ? Colors.white : Colors.black),
                                    themeOverride: itemTheme,
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(width: 14),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Row(
                                    children: [
                                      Text(
                                        itemTheme.title,
                                        style: TextStyle(
                                          color: theme.primaryTextColor,
                                          fontSize: 15,
                                          fontWeight: isSelected
                                              ? FontWeight.w800
                                              : FontWeight.w600,
                                        ),
                                      ),
                                      if (itemTheme == ChatyIconThemeId.chatySigil) ...[
                                        const SizedBox(width: 6),
                                        Container(
                                          padding: const EdgeInsets.symmetric(
                                            horizontal: 6,
                                            vertical: 2,
                                          ),
                                          decoration: BoxDecoration(
                                            color: Colors.white12,
                                            borderRadius:
                                                BorderRadius.circular(6),
                                          ),
                                          child: const Text(
                                            'Default',
                                            style: TextStyle(
                                              fontSize: 9.5,
                                              color: Colors.white70,
                                              fontWeight: FontWeight.w600,
                                            ),
                                          ),
                                        ),
                                      ],
                                    ],
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    itemTheme.description,
                                    maxLines: 2,
                                    overflow: TextOverflow.ellipsis,
                                    style: TextStyle(
                                      color: theme.secondaryTextColor,
                                      fontSize: 12,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(width: 8),
                            if (isSelected)
                              Icon(
                                Icons.check_circle_rounded,
                                color: theme.accentColor,
                                size: 24,
                              )
                            else
                              Icon(
                                Icons.circle_outlined,
                                color: Colors.white24,
                                size: 24,
                              ),
                          ],
                        ),
                      ),
                    );
                  },
                ),
                const SizedBox(height: 24),

                // SECTION 3: 14 CORE ACTIONS CONTACT SHEET (SIDE-BY-SIDE MATRIX)
                Text(
                  'CORE ACTIONS CONTACT SHEET (DIRECT COMPARISON)',
                  style: TextStyle(
                    color: theme.secondaryTextColor,
                    fontSize: 11.5,
                    fontWeight: FontWeight.w800,
                    letterSpacing: 0.8,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  'Direct side-by-side comparison of the 14 core actions in the active theme.',
                  style: TextStyle(
                    color: theme.secondaryTextColor,
                    fontSize: 12.5,
                  ),
                ),
                const SizedBox(height: 12),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: theme.surfaceColor,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: Colors.white10),
                  ),
                  child: GridView.builder(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    itemCount: _coreContactIcons.length,
                    gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                      crossAxisCount: 7,
                      mainAxisSpacing: 10,
                      crossAxisSpacing: 8,
                      childAspectRatio: 0.8,
                    ),
                    itemBuilder: (context, idx) {
                      final ic = _coreContactIcons[idx];
                      return InkWell(
                        onTap: () => _showIconDetailModal(ic, gbTheme),
                        borderRadius: BorderRadius.circular(10),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Container(
                              width: 38,
                              height: 38,
                              decoration: BoxDecoration(
                                color: _previewIsDark ? Colors.black38 : Colors.white70,
                                borderRadius: BorderRadius.circular(10),
                                border: Border.all(color: Colors.white10),
                              ),
                              child: Center(
                                child: ChatyIcon(
                                  ic,
                                  size: previewSize * 0.85,
                                  color: theme.primaryTextColor,
                                  themeOverride: activeTheme,
                                ),
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              ic.name,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(fontSize: 8.5),
                            ),
                          ],
                        ),
                      );
                    },
                  ),
                ),
                const SizedBox(height: 24),

                // SECTION 4: INDIVIDUAL ICON BROWSER
                Text(
                  'INDIVIDUAL ICON CATALOGUE',
                  style: TextStyle(
                    color: theme.secondaryTextColor,
                    fontSize: 11.5,
                    fontWeight: FontWeight.w800,
                    letterSpacing: 0.8,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  'Search and tap any icon to preview it across all 10 themes in the multi-theme modal.',
                  style: TextStyle(
                    color: theme.secondaryTextColor,
                    fontSize: 12.5,
                  ),
                ),
                const SizedBox(height: 12),
                // Search field
                TextField(
                  controller: _searchCtrl,
                  onChanged: (_) => setState(() {}),
                  style: TextStyle(color: theme.primaryTextColor),
                  decoration: InputDecoration(
                    hintText: 'Search icons by name (e.g. camera, calls)…',
                    hintStyle: TextStyle(color: theme.secondaryTextColor),
                    prefixIcon: Icon(
                      Icons.search_rounded,
                      color: theme.secondaryTextColor,
                    ),
                    filled: true,
                    fillColor: theme.surfaceColor,
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 10,
                    ),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(14),
                      borderSide: const BorderSide(color: Colors.white10),
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                // Category Pills
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: _categories.map((cat) {
                      final isSelected = _selectedCategory == cat;
                      return Padding(
                        padding: const EdgeInsets.only(right: 8.0),
                        child: FilterChip(
                          label: Text(
                            cat,
                            style: TextStyle(
                              color: isSelected
                                  ? Colors.black
                                  : theme.primaryTextColor,
                              fontWeight: isSelected
                                  ? FontWeight.w700
                                  : FontWeight.w500,
                              fontSize: 12.5,
                            ),
                          ),
                          selected: isSelected,
                          selectedColor: theme.accentColor,
                          backgroundColor: theme.surfaceColor,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(20),
                            side: BorderSide(
                              color: isSelected
                                  ? theme.accentColor
                                  : Colors.white12,
                            ),
                          ),
                          onSelected: (_) {
                            ChatyHaptics.light();
                            setState(() => _selectedCategory = cat);
                          },
                        ),
                      );
                    }).toList(),
                  ),
                ),
                const SizedBox(height: 14),

                // Icon Grid
                GridView.builder(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  itemCount: filteredList.length,
                  gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: 4,
                    mainAxisSpacing: 12,
                    crossAxisSpacing: 12,
                    childAspectRatio: 0.95,
                  ),
                  itemBuilder: (context, index) {
                    final iconId = filteredList[index];
                    return InkWell(
                      borderRadius: BorderRadius.circular(14),
                      onTap: () => _showIconDetailModal(iconId, gbTheme),
                      child: Container(
                        decoration: BoxDecoration(
                          color: theme.surfaceColor,
                          borderRadius: BorderRadius.circular(14),
                          border: Border.all(color: Colors.white10),
                        ),
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            ChatyIcon(
                              iconId,
                              size: previewSize,
                              color: theme.primaryTextColor,
                              themeOverride: activeTheme,
                            ),
                            const SizedBox(height: 8),
                            Padding(
                              padding: const EdgeInsets.symmetric(horizontal: 4),
                              child: Text(
                                _iconDisplayName(iconId),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                textAlign: TextAlign.center,
                                style: TextStyle(
                                  color: theme.secondaryTextColor,
                                  fontSize: 10.5,
                                  fontWeight: FontWeight.w500,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    );
                  },
                ),
                const SizedBox(height: 30),
              ],
            ),
          ),
        );
      },
    );
  }
}
