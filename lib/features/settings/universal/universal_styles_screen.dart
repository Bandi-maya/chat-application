import 'dart:async';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';

import '../../../injection/locator.dart';
import '../../../ui/core/controllers/app_icon_controller.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/design_system/design_system.dart';
import '../../../ui/core/design_system/gb_design_system.dart';
import '../../../ui/core/widgets/app_brand_icon.dart';

class UniversalStylesScreen extends StatefulWidget {
  final ChatyPreferencesController preferencesController;

  const UniversalStylesScreen({
    super.key,
    required this.preferencesController,
  });

  @override
  State<UniversalStylesScreen> createState() => _UniversalStylesScreenState();
}

class _UniversalStylesScreenState extends State<UniversalStylesScreen> {
  bool _showAllLauncherIcons = false;

  @override
  void initState() {
    super.initState();
    if (locator.isRegistered<AppIconController>()) {
      unawaited(locator<AppIconController>().initialize());
    }
  }

  // Each visible option maps to one distinct Android launcher alias.
  // Do not map multiple labels to a shared alias: it makes the UI claim a
  // different icon was selected even though Android displays the same artwork.
  final List<Map<String, dynamic>> _launcherIcons = [
    {
      'name': 'Swift Flight',
      'variant': LauncherIconVariant.bird,
      'color': Color(0xFF25D366),
      'icon': Icons.air_rounded,
    },
    {
      'name': 'Warm Signature',
      'variant': LauncherIconVariant.warm,
      'color': Color(0xFFB18B67),
      'icon': Icons.chat_bubble_rounded,
    },
    {
      'name': 'Warm Outline',
      'variant': LauncherIconVariant.outline,
      'color': Color(0xFFD8C7B2),
      'icon': Icons.chat_bubble_outline_rounded,
    },
    {
      'name': 'Obsidian',
      'variant': LauncherIconVariant.obsidian,
      'color': Color(0xFF202124),
      'icon': Icons.mode_comment_rounded,
    },
    {
      'name': 'Spatial Glass',
      'variant': LauncherIconVariant.glass,
      'color': Color(0xFF75D5D9),
      'icon': Icons.bubble_chart_rounded,
    },
    {
      'name': 'Signal',
      'variant': LauncherIconVariant.signal,
      'color': Color(0xFF18B5D1),
      'icon': Icons.graphic_eq_rounded,
    },
    {
      'name': 'Fold',
      'variant': LauncherIconVariant.fold,
      'color': Color(0xFFEF5AB7),
      'icon': Icons.layers_rounded,
    },
  ];

  final List<String> _emojiVariants = [
    'WhatsApp',
    'iOS NEW 2025',
    'One',
    'Facebook',
    'Android O',
    'System Emoji (BETA)',
  ];

  final List<Map<String, dynamic>> _notificationIcons = [
    // Brand-adjacent notification marks: all remain legible at status-bar size.
    {'name': 'White', 'color': Colors.white, 'icon': Icons.chat_bubble_outline_rounded},
    {'name': 'Black', 'color': Colors.black87, 'icon': Icons.chat_bubble_rounded},
    {'name': 'Blue', 'color': Colors.blue, 'icon': Icons.forum_rounded},
    {'name': 'Red', 'color': Colors.redAccent, 'icon': Icons.mode_comment_rounded},
    {'name': 'Green', 'color': Colors.green, 'icon': Icons.sms_rounded},
    {'name': 'Yellow', 'color': Colors.amber, 'icon': Icons.chat_rounded},
    {'name': 'Orange', 'color': Colors.deepOrange, 'icon': Icons.mark_chat_unread_rounded},
    {'name': 'Gold', 'color': Color(0xFFFFD700), 'icon': Icons.message_rounded},
    {'name': 'Cyan', 'color': Colors.cyan, 'icon': Icons.bubble_chart_rounded},
    {'name': 'Pink', 'color': Colors.pinkAccent, 'icon': Icons.chat_bubble_rounded},
    {'name': 'Magenta', 'color': Colors.purpleAccent, 'icon': Icons.chat_bubble_outline_rounded},
    {'name': 'Purple', 'color': Colors.deepPurple, 'icon': Icons.forum_outlined},
    {'name': 'Notifybar 12', 'color': Colors.grey, 'icon': Icons.mode_comment_outlined},
    {'name': 'Notifybar 13', 'color': Colors.pink, 'icon': Icons.chat_bubble_outline_rounded},
    {'name': 'Notifybar 14', 'color': Colors.teal, 'icon': Icons.forum_rounded},
    {'name': 'Notifybar 15', 'color': Colors.deepPurpleAccent, 'icon': Icons.message_outlined},
    {'name': 'Notifybar 16', 'color': Colors.orangeAccent, 'icon': Icons.chat_rounded},
    {'name': 'Notifybar 17', 'color': Colors.amberAccent, 'icon': Icons.mark_chat_unread_rounded},
    {'name': 'Notifybar 18', 'color': Colors.lightGreen, 'icon': Icons.chat_bubble_rounded},
    {'name': 'Notifybar 19', 'color': Colors.red, 'icon': Icons.sms_outlined},
    {'name': 'Notifybar 20', 'color': Colors.pink, 'icon': Icons.mode_comment_rounded},
    {'name': 'Notifybar 21', 'color': Colors.grey, 'icon': Icons.forum_outlined},
    {'name': 'Notifybar 22', 'color': Colors.redAccent, 'icon': Icons.chat_bubble_outline_rounded},
    {'name': 'Notifybar 23', 'color': Colors.greenAccent, 'icon': Icons.mark_chat_unread_rounded},
    {'name': 'Notifybar 24', 'color': Colors.pinkAccent, 'icon': Icons.message_rounded},
    {'name': 'Notifybar 25', 'color': Colors.amber, 'icon': Icons.forum_rounded},
    {'name': 'Notifybar 26', 'color': Colors.lightBlueAccent, 'icon': Icons.chat_bubble_rounded},
    {'name': 'Notifybar 27', 'color': Colors.brown, 'icon': Icons.chat_rounded},
  ];

  final List<Map<String, dynamic>> _fontStyles = [
    {'name': 'Default', 'style': TextStyle(fontWeight: FontWeight.normal)},
    {'name': 'Roboto-Light', 'style': TextStyle(fontWeight: FontWeight.w300)},
    {'name': 'Roboto-Medium', 'style': TextStyle(fontWeight: FontWeight.w600)},
    {'name': 'ProductSans', 'style': TextStyle(fontFamily: 'sans-serif', fontWeight: FontWeight.w500, letterSpacing: 0.2)},
    {'name': 'FrutigerLTStdRoman', 'style': TextStyle(fontFamily: 'sans-serif', fontWeight: FontWeight.w400)},
    {'name': 'Bariol', 'style': TextStyle(fontFamily: 'sans-serif-condensed', fontWeight: FontWeight.w400)},
    {'name': 'ComicSans', 'style': TextStyle(fontFamily: 'cursive', fontWeight: FontWeight.w500)},
    {'name': 'BEBASNEUE', 'style': TextStyle(fontWeight: FontWeight.w900, letterSpacing: 1.5)},
    {'name': 'Comfortaa', 'style': TextStyle(fontFamily: 'sans-serif-rounded', fontWeight: FontWeight.w600)},
    {'name': 'TRANSFORMERS', 'style': TextStyle(fontFamily: 'monospace', fontWeight: FontWeight.w800, letterSpacing: 1.2)},
    {'name': 'HappyGiraffe', 'style': TextStyle(fontStyle: FontStyle.italic, fontWeight: FontWeight.w500)},
  ];

  void _showNotificationIconModal(ThemeData gbTheme) {
    final prefs = widget.preferencesController.universal;
    GbModalSheet.show(
      context: context,
      title: 'Change Notification Icon',
      child: ListView.separated(
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        itemCount: _notificationIcons.length,
        separatorBuilder: (_, __) => const Divider(height: 1, indent: 56),
        itemBuilder: (ctx, i) {
          final item = _notificationIcons[i];
          final isSelected = prefs.notificationIcon == item['name'];
          return ListTile(
            leading: Container(
              width: 38,
              height: 38,
              decoration: BoxDecoration(
                color: (item['color'] as Color).withValues(alpha: 0.15),
                shape: BoxShape.circle,
              ),
              child: Icon(item['icon'] as IconData, color: item['color'] as Color, size: 20),
            ),
            title: Text(
              item['name'] as String,
              style: TextStyle(
                fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                color: isSelected ? gbTheme.colorScheme.primary : null,
              ),
            ),
            trailing: Radio<String>(
              value: item['name'] as String,
              groupValue: prefs.notificationIcon,
              activeColor: gbTheme.colorScheme.primary,
              onChanged: (val) {
                if (val != null) {
                  widget.preferencesController.updateUniversal(
                    prefs.copyWith(notificationIcon: val),
                  );
                  Navigator.pop(ctx);
                }
              },
            ),
            onTap: () {
              widget.preferencesController.updateUniversal(
                prefs.copyWith(notificationIcon: item['name'] as String),
              );
              Navigator.pop(ctx);
            },
          );
        },
      ),
    );
  }

  void _showFontStyleModal(ThemeData gbTheme) {
    final prefs = widget.preferencesController.universal;
    GbModalSheet.show(
      context: context,
      title: 'Font Style',
      child: ListView.separated(
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        itemCount: _fontStyles.length,
        separatorBuilder: (_, __) => const Divider(height: 1),
        itemBuilder: (ctx, i) {
          final item = _fontStyles[i];
          final isSelected = prefs.fontStyle == item['name'];
          return ListTile(
            contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
            title: Text(
              item['name'] as String,
              style: TextStyle(
                fontWeight: FontWeight.w700,
                fontSize: 15,
                color: isSelected ? gbTheme.colorScheme.primary : null,
              ),
            ),
            subtitle: Text(
              'Impossible can be achieved',
              style: (item['style'] as TextStyle).copyWith(
                fontSize: 13,
                color: isSelected
                    ? gbTheme.colorScheme.primary.withValues(alpha: 0.8)
                    : Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
            trailing: isSelected
                ? Icon(Icons.check_circle_rounded, color: gbTheme.colorScheme.primary)
                : null,
            onTap: () {
              widget.preferencesController.updateUniversal(
                prefs.copyWith(fontStyle: item['name'] as String),
              );
              Navigator.pop(ctx);
            },
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final themeController = locator<ThemeController>();
    final appIconController = locator.isRegistered<AppIconController>()
        ? locator<AppIconController>()
        : null;
    final signals = <Listenable>[
      widget.preferencesController,
      themeController,
      if (appIconController != null) appIconController,
    ];
    return ListenableBuilder(
      listenable: Listenable.merge(signals),
      builder: (context, _) {
        final theme = themeController.globalTheme;
        final colors = context.colors;
        final prefs = widget.preferencesController.universal;
        final gbTheme = Theme.of(context);

        final displayedLauncherIcons = _showAllLauncherIcons
            ? _launcherIcons
            : _launcherIcons.take(6).toList();

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
              'Styles (Look and feel)',
              style: TextStyle(
                color: theme.primaryTextColor,
                fontSize: 19 * theme.fontScale,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
          body: SafeArea(
            top: false,
            child: ListView(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
              children: [
                // SECTION 1: LAUNCHER ICONS
                GbCardContainer(
                  headerText: 'LAUNCHER ICONS',
                  children: [
                    Padding(
                      padding: const EdgeInsets.all(12),
                      child: Column(
                        children: [
                          GridView.builder(
                            shrinkWrap: true,
                            physics: const NeverScrollableScrollPhysics(),
                            itemCount: displayedLauncherIcons.length,
                            gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                              crossAxisCount:
                                  MediaQuery.sizeOf(ctx).width < 380 ? 4 : 5,
                              mainAxisSpacing: 10,
                              crossAxisSpacing: 10,
                              childAspectRatio:
                                  MediaQuery.sizeOf(ctx).width < 380 ? 0.62 : 0.78,
                            ),
                            itemBuilder: (ctx, i) {
                              final item = displayedLauncherIcons[i];
                              final variant = item['variant'] as LauncherIconVariant;
                              final isSelected = appIconController != null
                                  ? appIconController.launcherIcon == variant &&
                                      appIconController.brandIconSource == BrandIconSource.bundled
                                  : prefs.launcherIcon == variant.title;
                              return GestureDetector(
                                onTap: () async {
                                  final controller = appIconController;
                                  if (controller == null) {
                                    ScaffoldMessenger.of(context).showSnackBar(
                                      const SnackBar(
                                        content: Text('Launcher icon switching is unavailable on this platform.'),
                                      ),
                                    );
                                    return;
                                  }
                                  final applied = await controller.applyLauncherIcon(variant);
                                  if (!mounted) return;
                                  if (!applied) {
                                    ScaffoldMessenger.of(context).showSnackBar(
                                      SnackBar(
                                        content: Text(
                                          controller.lastError ??
                                              'The launcher did not confirm the selected icon.',
                                        ),
                                      ),
                                    );
                                    return;
                                  }
                                  widget.preferencesController.updateUniversal(
                                    widget.preferencesController.universal.copyWith(
                                      launcherIcon: variant.title,
                                    ),
                                  );
                                },
                                child: Column(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    Stack(
                                      children: [
                                        LauncherIconPreview(
                                          variant: variant,
                                          size: 44,
                                          borderRadius: 14,
                                        ),
                                        if (isSelected)
                                          Positioned.fill(
                                            child: Container(
                                              decoration: BoxDecoration(
                                                borderRadius: BorderRadius.circular(14),
                                                border: Border.all(
                                                  color: theme.accentColor,
                                                  width: 2.5,
                                                ),
                                              ),
                                            ),
                                          ),
                                      ],
                                    ),
                                    const SizedBox(height: 4),
                                    Text(
                                      variant.title,
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      textAlign: TextAlign.center,
                                      style: TextStyle(
                                        color: isSelected
                                            ? colors.foreground
                                            : colors.foregroundSecondary,
                                        fontSize: 9.5,
                                        fontWeight: isSelected
                                            ? FontWeight.w800
                                            : FontWeight.w500,
                                      ),
                                    ),
                                    const SizedBox(height: 2),
                                    if (isSelected)
                                      Container(
                                        width: 5,
                                        height: 5,
                                        decoration: BoxDecoration(
                                          color: theme.accentColor,
                                          shape: BoxShape.circle,
                                        ),
                                      ),
                                  ],
                                ),
                              );
                            },
                          ),
                          const SizedBox(height: 6),
                          InkWell(
                            borderRadius: BorderRadius.circular(12),
                            onTap: () {
                              setState(() {
                                _showAllLauncherIcons = !_showAllLauncherIcons;
                              });
                            },
                            child: Padding(
                              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
                              child: Text(
                                _showAllLauncherIcons ? '• Less •' : '• More •',
                                style: TextStyle(
                                  color: theme.accentColor,
                                  fontSize: 13,
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),

                // SECTION 2: EMOJI VARIANT
                GbCardContainer(
                  headerText: 'EMOJI VARIANT',
                  children: _emojiVariants.map((variant) {
                    final isSelected = prefs.emojiVariant == variant;
                    return InkWell(
                      onTap: () {
                        widget.preferencesController.updateUniversal(
                          prefs.copyWith(emojiVariant: variant),
                        );
                      },
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                        child: Row(
                          children: [
                            Container(
                              width: 22,
                              height: 22,
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                border: Border.all(
                                  color: isSelected ? theme.accentColor : colors.border,
                                  width: 2,
                                ),
                              ),
                              child: isSelected
                                  ? Center(
                                      child: Container(
                                        width: 12,
                                        height: 12,
                                        decoration: BoxDecoration(
                                          shape: BoxShape.circle,
                                          color: theme.accentColor,
                                        ),
                                      ),
                                    )
                                  : null,
                            ),
                            const SizedBox(width: 14),
                            Expanded(
                              child: Text(
                                variant,
                                style: TextStyle(
                                  color: theme.primaryTextColor,
                                  fontSize: 15,
                                  fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                                ),
                              ),
                            ),
                            if (variant != 'WhatsApp' && variant != 'System Emoji (BETA)')
                              Icon(
                                Icons.download_rounded,
                                color: theme.accentColor,
                                size: 20,
                              ),
                          ],
                        ),
                      ),
                    );
                  }).toList(),
                ),
                const SizedBox(height: 14),

                // SECTION 3: CHANGE NOTIFICATION ICON & FONTS
                GbCardContainer(
                  children: [
                    GbSettingRow(
                      icon: Icons.notifications_active_rounded,
                      title: 'Change Notification Icon',
                      subtitle: 'Selected: ${prefs.notificationIcon}\nThis is the icon that will appear in your status bar',
                      hasSubScreen: true,
                      onTap: () => _showNotificationIconModal(gbTheme),
                    ),
                    GbSettingRow(
                      icon: Icons.font_download_rounded,
                      title: 'Font Style',
                      subtitle: 'Selected: ${prefs.fontStyle}\nChange font style for Chaty',
                      hasSubScreen: true,
                      onTap: () => _showFontStyleModal(gbTheme),
                    ),
                    GbSettingRow(
                      icon: Icons.folder_open_rounded,
                      title: 'Load Font...',
                      subtitle: prefs.loadFontCustom ? 'Custom Font: Loaded & Active' : 'Tap to select .ttf or .otf file',
                      hasSubScreen: true,
                      onTap: () async {
                        if (prefs.loadFontCustom) {
                          widget.preferencesController.updateUniversal(
                            prefs.copyWith(loadFontCustom: false),
                          );
                          if (!context.mounted) return;
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('Custom font disabled; reverted to app default.')),
                          );
                          return;
                        }

                        try {
                          final result = await FilePicker.pickFiles(
                            type: FileType.custom,
                            allowedExtensions: ['ttf', 'otf'],
                          );
                          if (result.isEmpty) return;
                          final picked = result.first;
                          final pickedPath = picked.path;
                          if (pickedPath == null) return;
                          final file = File(pickedPath);
                          if (!await file.exists() || await file.length() == 0) {
                            if (!context.mounted) return;
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(content: Text('Invalid or empty font file.')),
                            );
                            return;
                          }
                          final docsDir = await getApplicationDocumentsDirectory();
                          final fontDest = '${docsDir.path}/custom_font_${picked.name}';
                          await file.copy(fontDest);

                          widget.preferencesController.updateUniversal(
                            prefs.copyWith(loadFontCustom: true),
                          );
                          if (!context.mounted) return;
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text('Custom font "${picked.name}" loaded successfully!')),
                          );
                        } catch (e) {
                          if (!context.mounted) return;
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text('Failed to load font: $e')),
                          );
                        }
                      },
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}
