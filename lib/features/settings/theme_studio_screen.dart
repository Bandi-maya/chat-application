import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/design_system/design_system.dart';
import '../../injection/locator.dart';
import '../../ui/core/controllers/preferences_controller.dart';
import '../../ui/core/theme/app_theme.dart';
import 'appearance/component_styles_screen.dart';
import '../../ui/core/theme/chaty_theme_manager.dart';
import '../../ui/core/templates/applied_pair_preview.dart';
import '../../ui/core/ticks/delivery_icon_style.dart';
import '../../ui/core/ticks/delivery_status_icon.dart';

/// Theme editor for bubble gradients, supported falling-particle effects, custom
/// tick icons, and theme JSON import/export. Edits are staged until Save; this
/// screen intentionally does not render an animated live-chat preview.
class ThemeStudioScreen extends StatefulWidget {
  final ThemeController? themeController;
  final ChatyPreferencesController? preferencesController;

  const ThemeStudioScreen({
    super.key,
    this.themeController,
    this.preferencesController,
  });

  @override
  State<ThemeStudioScreen> createState() => _ThemeStudioScreenState();
}

class _ThemeStudioScreenState extends State<ThemeStudioScreen> {
  late ThemeController _themeCtrl;
  late ChatyPreferencesController _prefsCtrl;
  late ThemeConfig _current;

  int _selectedTab = 0;
  late String _activeParticleEffect;
  late bool _enableFallingParticles;

  // Gradient presets
  static const List<_GradientPreset> _gradientPresets = [
    _GradientPreset(
      name: 'Neon Horizon',
      start: Color(0xFF00F2FE),
      end: Color(0xFF4FACFE),
    ),
    _GradientPreset(
      name: 'Cyber Matrix',
      start: Color(0xFF00F5A0),
      end: Color(0xFF00D9F5),
    ),
    _GradientPreset(
      name: 'Aurora Violet',
      start: Color(0xFF7F00FF),
      end: Color(0xFFE100FF),
    ),
    _GradientPreset(
      name: 'Sunset Ember',
      start: Color(0xFFFA709A),
      end: Color(0xFFFEE140),
    ),
    _GradientPreset(
      name: 'Midnight Obsidian',
      start: Color(0xFF232526),
      end: Color(0xFF414345),
    ),
    _GradientPreset(
      name: 'Emerald Lux',
      start: Color(0xFF11998E),
      end: Color(0xFF38EF7D),
    ),
  ];

  late Color _gradientStart;
  late Color _gradientEnd;
  bool _tickChanged = false;

  @override
  void initState() {
    super.initState();
    _themeCtrl = widget.themeController ?? locator<ThemeController>();
    _prefsCtrl =
        widget.preferencesController ?? locator<ChatyPreferencesController>();
    _current = _themeCtrl.globalTheme;

    _gradientStart = _current.outgoingBubbleColor;
    _gradientEnd = _current.accentColor;

    final savedEffects = _prefsCtrl.effects;
    const supportedParticleEffects = [
      'Stars',
      'Hearts',
      'Snowflakes',
      'Leaves',
    ];
    _activeParticleEffect =
        supportedParticleEffects.contains(savedEffects.fallingParticleObject)
        ? savedEffects.fallingParticleObject
        : 'Stars';
    _enableFallingParticles = savedEffects.enableFallingParticles;
  }

  void _saveAndApplyTheme() {
    HapticFeedback.heavyImpact();
    final updatedTheme = _current.copyWith(
      outgoingBubbleColor: _gradientStart,
      accentColor: _gradientEnd,
    );
    _themeCtrl.updateThemeConfig(updatedTheme);

    // Keep the typed Conversation tick id in step with the chosen theme tick.
    if (_tickChanged &&
        _prefsCtrl.conversation.tickStyle != updatedTheme.tickStyle) {
      _prefsCtrl.updateConversation(
        _prefsCtrl.conversation.copyWith(tickStyle: updatedTheme.tickStyle),
        logTitle: 'Ticks Style',
      );
    }

    final fx = _prefsCtrl.effects;
    _prefsCtrl.updateEffects(
      fx.copyWith(
        enableFallingParticles: _enableFallingParticles,
        fallingParticleObject: _activeParticleEffect,
      ),
      logTitle: 'Theme Studio Particle Engine',
    );

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: const Row(
          children: [
            Icon(Icons.check_circle_rounded, color: Colors.white, size: 20),
            SizedBox(width: 10),
            Text('Theme Studio configuration applied & saved!'),
          ],
        ),
        backgroundColor: context.colors.success,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    );
  }

  Future<void> _exportThemeJson() async {
    HapticFeedback.mediumImpact();
    try {
      final file = await ChatyThemeManager.saveThemeToFile(_current);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Theme exported to: ${file.path.split("/").last}'),
          backgroundColor: context.colors.success,
          behavior: SnackBarBehavior.floating,
        ),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Export failed: $e'),
          backgroundColor: context.colors.error,
          behavior: SnackBarBehavior.floating,
        ),
      );
    }
  }

  Future<void> _importThemeJson() async {
    HapticFeedback.mediumImpact();
    try {
      final result = await FilePicker.pickFile(
        type: FileType.custom,
        allowedExtensions: ['json'],
      );
      if (result == null || result.path == null) return;
      final file = File(result.path!);
      final content = await file.readAsString();
      final theme = ChatyThemeManager.validateAndImportTheme(content);
      setState(() {
        _current = theme;
        _gradientStart = theme.outgoingBubbleColor;
        _gradientEnd = theme.accentColor;
      });
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Successfully loaded theme: ${theme.name}'),
          backgroundColor: context.colors.success,
          behavior: SnackBarBehavior.floating,
        ),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Theme import rejected: $e'),
          backgroundColor: context.colors.error,
          behavior: SnackBarBehavior.floating,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: context.colors.background,
      appBar: AppBar(
        title: const Text(
          'Theme Studio',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
        ),
        centerTitle: true,
        backgroundColor: Colors.transparent,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.file_upload_outlined),
            tooltip: 'Export Theme JSON',
            onPressed: _exportThemeJson,
          ),
          IconButton(
            icon: const Icon(Icons.file_download_outlined),
            tooltip: 'Import Theme JSON',
            onPressed: _importThemeJson,
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            // Morphing Segmented Control Tab Bar
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: MorphingSegmentedControl<int>(
                selectedValue: _selectedTab,
                items: const [
                  MorphingSegmentItem(value: 0, label: 'Bubbles', icon: Icons.chat_bubble_outline),
                  MorphingSegmentItem(value: 1, label: 'Particles', icon: Icons.auto_awesome),
                  MorphingSegmentItem(value: 2, label: 'Ticks', icon: Icons.done_all_rounded),
                ],
                onSelected: (idx) {
                  setState(() => _selectedTab = idx);
                },
              ),
            ),

            // Tab Content
            Expanded(
              child: SingleChildScrollView(
                physics: const BouncingScrollPhysics(),
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    _buildActiveTabContent(),
                    const SizedBox(height: 16),
                    _buildHomePreview(),
                    const SizedBox(height: 16),
                    _buildComponentStylesEntry(),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
      bottomSheet: _buildBottomActionBar(),
    );
  }

  /// Entry to the component style editor (navigation, header, composer,
  /// overlays, motion and density). It replaces the old Customization Center
  /// row in the Settings list.
  /// Representative home and header preview for the active template, so the
  /// theme can be judged on the screen people see most. It is a preview of the
  /// template layout, not a screenshot of the live home screen.
  Widget _buildHomePreview() {
    final colors = context.colors;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(left: 4, bottom: 8),
          child: Text(
            'Current applied appearance',
            style: TextStyle(
              color: colors.foreground,
              fontSize: 15,
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
        AppliedPairPreview(
          palette: _themeCtrl.globalTheme,
          layout: currentSetupLayout(),
          applyTemplateConversation: false,
        ),
        Padding(
          padding: const EdgeInsets.only(top: 6),
          child: Text(
            'Static snapshot of the saved theme and layout. Unsaved Theme Studio changes appear only after Apply & Save Theme.',
            textAlign: TextAlign.center,
            style: TextStyle(color: colors.foregroundSecondary, fontSize: 12),
          ),
        ),
      ],
    );
  }

  Widget _buildComponentStylesEntry() {
    final colors = context.colors;
    return Material(
      color: colors.surfaceSecondary,
      borderRadius: BorderRadius.circular(16),
      child: ListTile(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        minVerticalPadding: 12,
        leading: Icon(Icons.dashboard_customize_outlined, color: colors.primary),
        title: Text(
          'Component styles',
          style: TextStyle(color: colors.foreground, fontWeight: FontWeight.w700),
        ),
        subtitle: Text(
          'Navigation, header, composer, overlays, motion and density',
          style: TextStyle(color: colors.foregroundSecondary, fontSize: 12),
        ),
        trailing: Icon(Icons.chevron_right_rounded, color: colors.foregroundSecondary),
        onTap: () => Navigator.of(context).push(
          MaterialPageRoute<void>(builder: (_) => const ComponentStylesScreen()),
        ),
      ),
    );
  }

  Widget _buildActiveTabContent() {
    switch (_selectedTab) {
      case 0:
        return _buildBubblesTab();
      case 1:
        return _buildParticlesTab();
      case 2:
        return _buildTicksTab();
      default:
        return const SizedBox.shrink();
    }
  }

  Widget _buildBubblesTab() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Gradient Colorways',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
        ),
        const SizedBox(height: 10),
        Wrap(
          spacing: 10,
          runSpacing: 10,
          children: _gradientPresets.map((preset) {
            final isSelected =
                _gradientStart == preset.start && _gradientEnd == preset.end;
            return SpringInteractiveScale(
              onTap: () {
                setState(() {
                  _gradientStart = preset.start;
                  _gradientEnd = preset.end;
                });
              },
              child: Container(
                width: 105,
                padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 8),
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [preset.start, preset.end],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  borderRadius: BorderRadius.circular(12),
                  border: isSelected
                      ? Border.all(color: Colors.white, width: 2)
                      : null,
                  boxShadow: [
                    BoxShadow(
                      color: preset.start.withValues(alpha: 0.3),
                      blurRadius: 6,
                      offset: const Offset(0, 3),
                    ),
                  ],
                ),
                child: Text(
                  preset.name,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.bold,
                    fontSize: 11,
                  ),
                ),
              ),
            );
          }).toList(),
        ),
        const SizedBox(height: 20),

        BentoCard(
          title: 'Bubble Geometry & Font Scale',
          subtitle: 'Adjust radius curvature and message typography scale',
          headerLeading: const Icon(Icons.rounded_corner_rounded),
          child: Column(
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text('Corner Radius', style: TextStyle(fontSize: 13)),
                  Text('${_current.cornerRadius.toInt()}px',
                      style: const TextStyle(fontWeight: FontWeight.bold)),
                ],
              ),
              Slider(
                value: _current.cornerRadius.clamp(6.0, 32.0),
                min: 6.0,
                max: 32.0,
                divisions: 13,
                activeColor: context.colors.primary,
                inactiveColor: context.colors.border,
                onChanged: (v) {
                  setState(() => _current = _current.copyWith(cornerRadius: v));
                },
              ),
              const SizedBox(height: 10),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text('Font Scale Factor', style: TextStyle(fontSize: 13)),
                  Text('${(_current.fontScale * 100).toInt()}%',
                      style: const TextStyle(fontWeight: FontWeight.bold)),
                ],
              ),
              Slider(
                value: _current.fontScale.clamp(0.85, 1.3),
                min: 0.85,
                max: 1.3,
                divisions: 9,
                activeColor: context.colors.primary,
                inactiveColor: context.colors.border,
                onChanged: (v) {
                  setState(() => _current = _current.copyWith(fontScale: v));
                },
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildParticlesTab() {
    const particleTypes = ['Stars', 'Hearts', 'Snowflakes', 'Leaves'];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Falling Particle Effects',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
        ),
        const SizedBox(height: 10),
        SwitchListTile(
          contentPadding: EdgeInsets.zero,
          title: const Text('Enable falling particles'),
          subtitle: const Text(
            'Use the selected object within the saved Home/Chat scope.',
          ),
          value: _enableFallingParticles,
          onChanged: (enabled) {
            setState(() => _enableFallingParticles = enabled);
          },
        ),
        const SizedBox(height: 10),
        Wrap(
          spacing: 10,
          runSpacing: 10,
          children: particleTypes.map((type) {
            final isSelected = _activeParticleEffect == type;
            return SpringInteractiveScale(
              onTap: () {
                setState(() => _activeParticleEffect = type);
              },
              child: Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 10,
                ),
                decoration: BoxDecoration(
                  color: isSelected
                      ? context.colors.accent
                      : context.colors.surface,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: isSelected
                        ? context.colors.accent
                        : context.colors.border,
                  ),
                ),
                child: Text(
                  type,
                  style: TextStyle(
                    color: isSelected
                        ? Colors.white
                        : context.colors.foreground,
                    fontWeight: FontWeight.bold,
                    fontSize: 12,
                  ),
                ),
              ),
            );
          }).toList(),
        ),
        const SizedBox(height: 12),
        Text(
          'Supported Chaty effects are saved when you tap Apply & Save Theme. '
          'No animated live preview is rendered in this screen.',
          style: TextStyle(
            color: context.colors.foregroundSecondary,
            fontSize: 12,
          ),
        ),
      ],
    );
  }

  Widget _buildTicksTab() {
    final styles = [
      DeliveryIconStyle.rcIos11,
      DeliveryIconStyle.material3Rounded,
      DeliveryIconStyle.bbmV2,
      DeliveryIconStyle.cirCheck,
      DeliveryIconStyle.circlePrint,
      DeliveryIconStyle.greenTick,
      DeliveryIconStyle.wings,
      DeliveryIconStyle.batman,
      DeliveryIconStyle.doubleSword,
      DeliveryIconStyle.glowingDots,
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Message Tick Style Catalog',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
        ),
        const SizedBox(height: 10),
        GridView.builder(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: 2,
            mainAxisSpacing: 10,
            crossAxisSpacing: 10,
            childAspectRatio: 2.2,
          ),
          itemCount: styles.length,
          itemBuilder: (context, index) {
            final style = styles[index];
            final isSelected = _current.deliveryTickStyle == style;

            return SpringInteractiveScale(
              onTap: () {
                setState(() {
                  _tickChanged = true;
                  _current = _current.copyWith(
                    deliveryTickStyle: style,
                    tickStyle: style.displayName,
                  );
                });
              },
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                decoration: BoxDecoration(
                  color: isSelected
                      ? context.colors.accent.withValues(alpha: 0.15)
                      : context.colors.surface,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: isSelected
                        ? context.colors.accent
                        : context.colors.border,
                    width: isSelected ? 2 : 1,
                  ),
                ),
                child: Row(
                  children: [
                    DeliveryStatusIcon(
                      style: style,
                      state: DeliveryState.read,
                      unreadColor: context.colors.foregroundTertiary,
                      readColor: context.colors.accent,
                      size: 20,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        style.displayName,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight:
                              isSelected ? FontWeight.bold : FontWeight.normal,
                          color: isSelected
                              ? context.colors.accent
                              : context.colors.foreground,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            );
          },
        ),
      ],
    );
  }

  Widget _buildBottomActionBar() {
    return SpecularGlassCard(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      borderRadius: 20.0,
      child: SafeArea(
        top: false,
        child: Row(
          children: [
            Expanded(
              child: SpringInteractiveScale(
                onTap: _saveAndApplyTheme,
                child: Container(
                  height: 48,
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      colors: [_gradientStart, _gradientEnd],
                    ),
                    borderRadius: BorderRadius.circular(14),
                    boxShadow: [
                      BoxShadow(
                        color: _gradientStart.withValues(alpha: 0.4),
                        blurRadius: 10,
                        offset: const Offset(0, 4),
                      ),
                    ],
                  ),
                  child: const Center(
                    child: Text(
                      'Apply & Save Theme',
                      style: TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.bold,
                        fontSize: 15,
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _GradientPreset {
  final String name;
  final Color start;
  final Color end;

  const _GradientPreset({
    required this.name,
    required this.start,
    required this.end,
  });
}
