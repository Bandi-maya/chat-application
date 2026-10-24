import 'dart:io';
import 'dart:math' as math;

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/design_system/design_system.dart';
import '../../domain/models/chat_message.dart';
import '../../injection/locator.dart';
import '../../ui/core/controllers/preferences_controller.dart';
import '../../ui/core/theme/app_theme.dart';
import 'appearance/component_styles_screen.dart';
import '../../ui/core/templates/template_controller.dart';
import '../../ui/core/theme/chaty_theme_manager.dart';
import '../../ui/core/templates/applied_pair_preview.dart';
import '../../ui/core/ticks/delivery_icon_style.dart';
import '../../ui/core/ticks/delivery_status_icon.dart';

/// Next-Generation 21st.dev + Kombai Theme Studio & Visual Customization Suite.
/// Provides live interactive preview, bubble gradient editor, 3D page transitions,
/// ambient canvas particles, custom tick icons, and theme JSON import/export.
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

class _ThemeStudioScreenState extends State<ThemeStudioScreen>
    with SingleTickerProviderStateMixin {
  late ThemeController _themeCtrl;
  late ChatyPreferencesController _prefsCtrl;
  late ThemeConfig _current;

  late AnimationController _particleAnimController;
  final List<_ParticleItem> _particles = <_ParticleItem>[];
  final math.Random _random = math.Random();

  int _selectedTab = 0;
  String _activeParticleEffect = 'Neon Embers';
  String _active3DTransition = 'Cube 3D';
  double _particleSpeed = 1.0;
  double _particleDensity = 25.0;

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

    _particleAnimController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 10),
    )..addListener(_tickParticles);

    _initParticles();
    _particleAnimController.repeat();
  }

  @override
  void dispose() {
    _particleAnimController.dispose();
    super.dispose();
  }

  void _initParticles() {
    _particles.clear();
    final count = _particleDensity.toInt();
    for (int i = 0; i < count; i++) {
      _particles.add(
        _ParticleItem(
          x: _random.nextDouble(),
          y: _random.nextDouble(),
          size: 3.0 + _random.nextDouble() * 5.0,
          speed: 0.15 + _random.nextDouble() * 0.45,
          opacity: 0.2 + _random.nextDouble() * 0.7,
          color: _getParticleColor(i),
        ),
      );
    }
  }

  Color _getParticleColor(int index) {
    switch (_activeParticleEffect) {
      case 'Neon Embers':
        return index % 2 == 0 ? const Color(0xFFFF5E36) : const Color(0xFFFFB300);
      case 'Sakura':
        return index % 2 == 0 ? const Color(0xFFFFB7B2) : const Color(0xFFFF69B4);
      case 'Snow':
        return Colors.white70;
      case 'Cyber Matrix':
        return index % 2 == 0 ? const Color(0xFF00FF66) : const Color(0xFF00E5FF);
      case 'Hearts':
        return index % 2 == 0 ? const Color(0xFFFF2D55) : const Color(0xFFFF375F);
      default:
        return Colors.transparent;
    }
  }

  void _tickParticles() {
    if (_activeParticleEffect == 'None') return;
    for (final p in _particles) {
      if (_activeParticleEffect == 'Neon Embers' ||
          _activeParticleEffect == 'Hearts') {
        p.y -= (p.speed * 0.005) * _particleSpeed;
        if (p.y < -0.05) {
          p.y = 1.05;
          p.x = _random.nextDouble();
        }
      } else {
        p.y += (p.speed * 0.005) * _particleSpeed;
        p.x += math.sin(p.y * 10) * 0.001;
        if (p.y > 1.05) {
          p.y = -0.05;
          p.x = _random.nextDouble();
        }
      }
    }
    setState(() {});
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
        enableFallingParticles: _activeParticleEffect != 'None',
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
            // Top Live Interactive Preview
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: _buildLiveInteractivePreview(),
            ),

            // Morphing Segmented Control Tab Bar
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: MorphingSegmentedControl<int>(
                selectedValue: _selectedTab,
                items: const [
                  MorphingSegmentItem(value: 0, label: 'Bubbles', icon: Icons.chat_bubble_outline),
                  MorphingSegmentItem(value: 1, label: '3D Motion', icon: Icons.view_in_ar_rounded),
                  MorphingSegmentItem(value: 2, label: 'Particles', icon: Icons.auto_awesome),
                  MorphingSegmentItem(value: 3, label: 'Ticks', icon: Icons.done_all_rounded),
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
            'Home and conversation preview',
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
            'Your current colours on your current layout, including component overrides. Wallpaper and the finer Home UI styling are not drawn.',
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

  Widget _buildLiveInteractivePreview() {
    return SpecularGlassCard(
      padding: EdgeInsets.zero,
      borderRadius: 20.0,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(20),
        child: Container(
          // Grows with large text instead of overflowing.
          constraints: const BoxConstraints(minHeight: 185),
          color: _current.backgroundColor,
          child: Stack(
            children: [
              // Canvas particle layer
              if (_activeParticleEffect != 'None')
                Positioned.fill(
                  child: CustomPaint(
                    painter: _LiveParticleCanvasPainter(
                      particles: _particles,
                      effect: _activeParticleEffect,
                    ),
                  ),
                ),

              // Chat Bubble Content Simulation
              Padding(
                padding: const EdgeInsets.all(14),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    // Incoming Bubble
                    Align(
                      alignment: Alignment.centerLeft,
                      child: Container(
                        constraints: BoxConstraints(
                          maxWidth: MediaQuery.of(context).size.width * 0.72,
                        ),
                        padding: const EdgeInsets.symmetric(
                          horizontal: 14,
                          vertical: 9,
                        ),
                        decoration: BoxDecoration(
                          color: _current.incomingBubbleColor,
                          borderRadius: BorderRadius.only(
                            topLeft: Radius.circular(_current.cornerRadius),
                            topRight: Radius.circular(_current.cornerRadius),
                            bottomRight: Radius.circular(_current.cornerRadius),
                            bottomLeft: const Radius.circular(4),
                          ),
                          border: Border.all(
                            color: Colors.white.withValues(alpha: 0.12),
                            width: 1,
                          ),
                        ),
                        child: Text(
                          'Hey! Check out this real-time 3D theme preview ✨',
                          style: TextStyle(
                            color: _current.incomingTextColor,
                            fontSize: 13 * _current.fontScale,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 10),

                    // Outgoing Bubble with custom gradient & selected ticks
                    Align(
                      alignment: Alignment.centerRight,
                      child: Container(
                        constraints: BoxConstraints(
                          maxWidth: MediaQuery.of(context).size.width * 0.75,
                        ),
                        padding: const EdgeInsets.symmetric(
                          horizontal: 14,
                          vertical: 9,
                        ),
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            colors: [_gradientStart, _gradientEnd],
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                          ),
                          borderRadius: BorderRadius.only(
                            topLeft: Radius.circular(_current.cornerRadius),
                            topRight: Radius.circular(_current.cornerRadius),
                            bottomLeft: Radius.circular(_current.cornerRadius),
                            bottomRight: const Radius.circular(4),
                          ),
                          boxShadow: [
                            BoxShadow(
                              color: _gradientStart.withValues(alpha: 0.35),
                              blurRadius: 10,
                              offset: const Offset(0, 4),
                            ),
                          ],
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: [
                            Flexible(
                              child: Text(
                                'Specular glass + live canvas particles active!',
                                style: TextStyle(
                                  color: _current.outgoingTextColor,
                                  fontSize: 13 * _current.fontScale,
                                  fontWeight: FontWeight.w500,
                                ),
                              ),
                            ),
                            const SizedBox(width: 8),
                            DeliveryStatusIcon(
                              style: _current.deliveryTickStyle,
                              state: DeliveryState.read,
                              unreadColor: Colors.white70,
                              readColor: Colors.white,
                              size: 15,
                            ),
                          ],
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

  Widget _buildActiveTabContent() {
    switch (_selectedTab) {
      case 0:
        return _buildBubblesTab();
      case 1:
        return _build3DMotionTab();
      case 2:
        return _buildParticlesTab();
      case 3:
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

  Widget _build3DMotionTab() {
    final transitions = [
      {'name': 'Cube 3D', 'desc': 'Perspective rotation along 3D cube edges'},
      {'name': 'Accordion', 'desc': 'Fold & unfold accordion sheet depth'},
      {'name': 'Card Flip', 'desc': 'Full Y-axis 3D spatial flip animation'},
      {'name': 'Zoom Out', 'desc': 'Cinematic scale-down perspective depth'},
      {'name': 'Shared Axis', 'desc': 'Smooth Material Motion Z-axis glide'},
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          '3D Spatial Page Transitions',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
        ),
        const SizedBox(height: 10),
        ...transitions.map((t) {
          final isSelected = _active3DTransition == t['name'];
          return Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: SpringInteractiveScale(
              onTap: () {
                setState(() => _active3DTransition = t['name']!);
              },
              child: BentoCard(
                title: t['name']!,
                subtitle: t['desc']!,
                headerLeading: Icon(
                  isSelected
                      ? Icons.check_circle_rounded
                      : Icons.view_in_ar_outlined,
                  color: isSelected ? context.colors.accent : context.colors.icon,
                ),
                child: isSelected
                    ? Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 10,
                          vertical: 6,
                        ),
                        decoration: BoxDecoration(
                          color: context.colors.accent.withValues(alpha: 0.15),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: const Text(
                          'Active 3D Transition Engine',
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      )
                    : const SizedBox.shrink(),
              ),
            ),
          );
        }),
      ],
    );
  }

  Widget _buildParticlesTab() {
    final particleTypes = [
      'Neon Embers',
      'Sakura',
      'Snow',
      'Cyber Matrix',
      'Hearts',
      'None',
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Ambient Canvas Particles',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
        ),
        const SizedBox(height: 10),
        Wrap(
          spacing: 10,
          runSpacing: 10,
          children: particleTypes.map((type) {
            final isSelected = _activeParticleEffect == type;
            return SpringInteractiveScale(
              onTap: () {
                setState(() {
                  _activeParticleEffect = type;
                  _initParticles();
                });
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
        const SizedBox(height: 20),

        BentoCard(
          title: 'Particle Velocity & Density',
          subtitle: 'Control real-time canvas animation frequency',
          headerLeading: const Icon(Icons.speed_rounded),
          child: Column(
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text('Speed Multiplier', style: TextStyle(fontSize: 13)),
                  Text('${_particleSpeed.toStringAsFixed(1)}x',
                      style: const TextStyle(fontWeight: FontWeight.bold)),
                ],
              ),
              Slider(
                value: _particleSpeed,
                min: 0.5,
                max: 3.0,
                divisions: 10,
                activeColor: context.colors.accent,
                onChanged: (v) {
                  setState(() => _particleSpeed = v);
                },
              ),
              const SizedBox(height: 8),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text('Particle Count', style: TextStyle(fontSize: 13)),
                  Text('${_particleDensity.toInt()}',
                      style: const TextStyle(fontWeight: FontWeight.bold)),
                ],
              ),
              Slider(
                value: _particleDensity,
                min: 10.0,
                max: 60.0,
                divisions: 10,
                activeColor: context.colors.accent,
                onChanged: (v) {
                  setState(() {
                    _particleDensity = v;
                    _initParticles();
                  });
                },
              ),
            ],
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

class _ParticleItem {
  double x;
  double y;
  double size;
  double speed;
  double opacity;
  Color color;

  _ParticleItem({
    required this.x,
    required this.y,
    required this.size,
    required this.speed,
    required this.opacity,
    required this.color,
  });
}

class _LiveParticleCanvasPainter extends CustomPainter {
  final List<_ParticleItem> particles;
  final String effect;

  _LiveParticleCanvasPainter({
    required this.particles,
    required this.effect,
  });

  @override
  void paint(Canvas canvas, Size size) {
    if (effect == 'None') return;

    for (final p in particles) {
      final paint = Paint()
        ..color = p.color.withValues(alpha: p.opacity)
        ..style = PaintingStyle.fill;

      final dx = p.x * size.width;
      final dy = p.y * size.height;

      if (effect == 'Hearts') {
        _drawMiniHeart(canvas, Offset(dx, dy), p.size, paint);
      } else if (effect == 'Sakura') {
        canvas.drawOval(
          Rect.fromCenter(
            center: Offset(dx, dy),
            width: p.size * 1.5,
            height: p.size,
          ),
          paint,
        );
      } else {
        canvas.drawCircle(Offset(dx, dy), p.size / 2, paint);
      }
    }
  }

  void _drawMiniHeart(Canvas canvas, Offset offset, double size, Paint paint) {
    final path = Path();
    final s = size * 0.8;
    path.moveTo(offset.dx, offset.dy + s / 4);
    path.cubicTo(
      offset.dx,
      offset.dy,
      offset.dx - s / 2,
      offset.dy,
      offset.dx - s / 2,
      offset.dy + s / 4,
    );
    path.cubicTo(
      offset.dx - s / 2,
      offset.dy + s / 2,
      offset.dx,
      offset.dy + s * 0.8,
      offset.dx,
      offset.dy + s,
    );
    path.cubicTo(
      offset.dx,
      offset.dy + s * 0.8,
      offset.dx + s / 2,
      offset.dy + s / 2,
      offset.dx + s / 2,
      offset.dy + s / 4,
    );
    path.cubicTo(
      offset.dx + s / 2,
      offset.dy,
      offset.dx,
      offset.dy,
      offset.dx,
      offset.dy + s / 4,
    );
    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(covariant _LiveParticleCanvasPainter oldDelegate) => true;
}
