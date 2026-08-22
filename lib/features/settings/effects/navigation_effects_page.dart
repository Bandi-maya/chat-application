import 'package:flutter/material.dart';
import '../../../ui/core/design_system/settings_primitives.dart';
import '../../../ui/core/controllers/preferences_controller.dart';
import '../../../ui/core/theme/app_theme.dart';

class NavigationEffectsPage extends StatefulWidget {
  final ChatyPreferencesController preferencesController;

  const NavigationEffectsPage({super.key, required this.preferencesController});

  @override
  State<NavigationEffectsPage> createState() => _NavigationEffectsPageState();
}

class _NavigationEffectsPageState extends State<NavigationEffectsPage> {

  static const List<String> _clickSymbols = ['✨', '❤️', '🔥', '⚡', '⭐', '🌸'];

  static const List<String> _fallingObjects = [
    'Stars',
    'Hearts',
    'Snowflakes',
    'Leaves',
  ];

  static const List<String> _fallingScopes = ['Home only', 'Chat only', 'Both'];

  @override
  Widget build(BuildContext context) {
    final fx = widget.preferencesController.effects;

    return ChatySettingsPage(
      title: 'Navigation & Particle Effects',
      subtitle: 'Page Transitions, Click Particles & Falling Objects',
      children: [
        // Live Preview Card at Top
        ChatyPreviewCard(
          title: 'Live Effects Preview',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Click Particles: ${fx.enableClickParticles ? "${fx.clickParticleSymbol} Active" : "Off"} • Falling: ${fx.enableFallingParticles ? "${fx.fallingParticleObject} Active" : "Off"}',
                style: TextStyle(
                  fontSize: 12,
                  color: Theme.of(context).textTheme.bodySmall?.color,
                ),
              ),
              const SizedBox(height: 8),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Theme.of(context).cardColor,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: Theme.of(
                      context,
                    ).dividerColor.withValues(alpha: 0.2),
                  ),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Row(
                      children: [
                        Text(
                          fx.enableClickParticles
                              ? fx.clickParticleSymbol
                              : '✨',
                          style: const TextStyle(fontSize: 22),
                        ),
                        const SizedBox(width: 10),
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Interactive Particle FX: ${fx.enableClickParticles ? "On" : "Disabled"}',
                              style: const TextStyle(
                                fontWeight: FontWeight.bold,
                                fontSize: 13,
                              ),
                            ),
                            Text(
                              fx.enableClickParticles
                                  ? 'Particles spawn on user interaction'
                                  : 'Tap anywhere to spawn particle effects',
                              style: TextStyle(
                                fontSize: 12,
                                color: Theme.of(
                                  context,
                                ).textTheme.bodySmall?.color,
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                    Icon(
                      Icons.animation_rounded,
                      color: fx.enableClickParticles
                          ? context.colors.accent
                          : context.colors.foregroundTertiary,
                      size: 24,
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),

        // Interactive Click Particles
        ChatySettingsSection(
          title: 'Interactive Tap Particles',
          description:
              'Generates decorative particle splashes whenever you tap the screen.',
          children: [
            ChatySwitchTile(
              icon: Icons.auto_awesome_rounded,
              iconColor: context.colors.accent,
              title: 'Enable Click Particles',
              subtitle: 'Spawn particle splash on screen touch',
              value: fx.enableClickParticles,
              onChanged: (val) {
                widget.preferencesController.updateEffects(
                  fx.copyWith(enableClickParticles: val),
                  logTitle: 'Click Particles',
                );
              },
            ),
            if (fx.enableClickParticles) ...[
              ChatyChoiceTile<String>(
                title: 'Particle Symbol',
                options: _clickSymbols,
                selectedOption: fx.clickParticleSymbol,
                optionLabel: (s) => s,
                onSelected: (sym) {
                  widget.preferencesController.updateEffects(
                    fx.copyWith(clickParticleSymbol: sym),
                    logTitle: 'Particle Symbol',
                  );
                },
              ),
              ChatySliderTile(
                icon: Icons.speed_rounded,
                title: 'Particle Speed',
                value: fx.clickParticleSpeed,
                min: 0.5,
                max: 2.0,
                divisions: 6,
                valueFormatter: (v) => '${v}x',
                onChanged: (v) {
                  widget.preferencesController.updateEffects(
                    fx.copyWith(clickParticleSpeed: v),
                    logTitle: 'Particle Speed',
                  );
                },
              ),
            ],
          ],
        ),

        // Decorative Falling Particles
        ChatySettingsSection(
          title: 'Decorative Falling Particles',
          description:
              'Ambient particles drifting downwards on application screens.',
          children: [
            ChatySwitchTile(
              icon: Icons.ac_unit_rounded,
              iconColor: context.colors.info,
              title: 'Enable Falling Particles',
              subtitle: 'Render drifting ambient background particles',
              value: fx.enableFallingParticles,
              onChanged: (val) {
                widget.preferencesController.updateEffects(
                  fx.copyWith(enableFallingParticles: val),
                  logTitle: 'Falling Particles',
                );
              },
            ),
            if (fx.enableFallingParticles) ...[
              ChatyChoiceTile<String>(
                title: 'Falling Object',
                options: _fallingObjects,
                selectedOption: fx.fallingParticleObject,
                optionLabel: (s) => s,
                onSelected: (obj) {
                  widget.preferencesController.updateEffects(
                    fx.copyWith(fallingParticleObject: obj),
                    logTitle: 'Falling Object',
                  );
                },
              ),
              ChatyChoiceTile<String>(
                title: 'Screen Scope',
                options: _fallingScopes,
                selectedOption: fx.fallingParticleScope,
                optionLabel: (s) => s,
                onSelected: (scope) {
                  widget.preferencesController.updateEffects(
                    fx.copyWith(fallingParticleScope: scope),
                    logTitle: 'Falling Scope',
                  );
                },
              ),
            ],
          ],
        ),
      ],
    );
  }
}
