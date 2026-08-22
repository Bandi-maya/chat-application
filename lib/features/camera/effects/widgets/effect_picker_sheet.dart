import 'package:flutter/material.dart';
import 'package:chat/injection/locator.dart';
import 'package:chat/ui/core/design_system/design_system.dart';
import '../effect_engine.dart';
import '../effect_model.dart';
import '../effect_registry.dart';

/// Bottom sheet carousel for browsing, selecting, and adjusting 100+ effects.
class EffectPickerSheet extends StatefulWidget {
  const EffectPickerSheet({super.key});

  static Future<void> show(BuildContext context) {
    return showModalBottomSheet<void>(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (_) => const EffectPickerSheet(),
    );
  }

  @override
  State<EffectPickerSheet> createState() => _EffectPickerSheetState();
}

class _EffectPickerSheetState extends State<EffectPickerSheet>
    with SingleTickerProviderStateMixin {
  late final TabController _tabCtrl;
  final EffectEngine _engine = locator<EffectEngine>();

  final List<EffectCategory> _categories = [
    EffectCategory.beauty,
    EffectCategory.cinematic,
    EffectCategory.retro,
    EffectCategory.film,
    EffectCategory.glow,
    EffectCategory.faceAR,
    EffectCategory.celebration,
    EffectCategory.cyber,
    EffectCategory.background,
  ];

  @override
  void initState() {
    super.initState();
    _tabCtrl = TabController(length: _categories.length + 1, vsync: this);
  }

  @override
  void dispose() {
    _tabCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;

    return ListenableBuilder(
      listenable: _engine,
      builder: (context, _) {
        return Container(
          height: 360,
          decoration: BoxDecoration(
            color: colors.surface,
            borderRadius: const BorderRadius.vertical(
              top: Radius.circular(ChatyRadius.xxl),
            ),
            border: Border(top: BorderSide(color: colors.borderSubtle)),
          ),
          child: Column(
            children: [
              const SizedBox(height: ChatySpacing.sm),
              Container(
                width: 38,
                height: 4,
                decoration: BoxDecoration(
                  color: colors.foregroundSecondary.withValues(alpha: 0.3),
                  borderRadius: BorderRadius.circular(ChatyRadius.full),
                ),
              ),
              const SizedBox(height: ChatySpacing.xs),

              // Intensity Slider (if active effect is not none)
              if (_engine.activeEffect.id != 'none')
                Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: ChatySpacing.xl,
                    vertical: 2,
                  ),
                  child: Row(
                    children: [
                      Text(
                        'Intensity',
                        style: TextStyle(
                          color: colors.foregroundSecondary,
                          fontSize: 12,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      Expanded(
                        child: Slider(
                          value: _engine.intensity,
                          activeColor: colors.primary,
                          inactiveColor: colors.primary.withValues(alpha: 0.2),
                          onChanged: _engine.setIntensity,
                        ),
                      ),
                      Text(
                        '${(_engine.intensity * 100).round()}%',
                        style: TextStyle(
                          color: colors.foreground,
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                ),

              // Category Tabs
              TabBar(
                controller: _tabCtrl,
                isScrollable: true,
                tabAlignment: TabAlignment.start,
                labelColor: colors.primary,
                unselectedLabelColor: colors.foregroundSecondary,
                indicatorColor: colors.primary,
                tabs: [
                  const Tab(text: 'All'),
                  ..._categories.map((c) => Tab(text: _categoryLabel(c))),
                ],
              ),

              // Effects Grid / Carousel
              Expanded(
                child: TabBarView(
                  controller: _tabCtrl,
                  children: [
                    _buildGrid(EffectRegistry.allEffects, colors),
                    ..._categories.map(
                      (c) => _buildGrid(EffectRegistry.getByCategory(c), colors),
                    ),
                  ],
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildGrid(List<ChatyCameraEffect> list, AppColors colors) {
    return ListView.builder(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.symmetric(
        horizontal: ChatySpacing.lg,
        vertical: ChatySpacing.md,
      ),
      itemCount: list.length,
      itemBuilder: (context, i) {
        final effect = list[i];
        final isSelected = _engine.activeEffect.id == effect.id;

        return Padding(
          padding: const EdgeInsets.only(right: ChatySpacing.md),
          child: GestureDetector(
            onTap: () => _engine.selectEffect(effect),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 60,
                  height: 60,
                  decoration: BoxDecoration(
                    color: effect.previewColor.withValues(alpha: 0.18),
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: isSelected ? colors.primary : colors.border,
                      width: isSelected ? 3 : 1,
                    ),
                  ),
                  child: Icon(
                    effect.icon,
                    color: isSelected ? colors.primary : effect.previewColor,
                    size: 26,
                  ),
                ),
                const SizedBox(height: 6),
                SizedBox(
                  width: 68,
                  child: Text(
                    effect.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: isSelected ? colors.primary : colors.foreground,
                      fontSize: 11,
                      fontWeight: isSelected ? FontWeight.bold : FontWeight.w500,
                    ),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  String _categoryLabel(EffectCategory category) {
    switch (category) {
      case EffectCategory.beauty:
        return 'Beauty';
      case EffectCategory.cinematic:
        return 'Cinematic';
      case EffectCategory.retro:
        return 'Retro';
      case EffectCategory.film:
        return 'Film';
      case EffectCategory.glow:
        return 'Glow';
      case EffectCategory.faceAR:
        return 'Face AR';
      case EffectCategory.celebration:
        return 'Fun';
      case EffectCategory.cyber:
        return 'Cyber';
      case EffectCategory.background:
        return 'Background';
      case EffectCategory.portrait:
        return 'Portrait';
      case EffectCategory.color:
        return 'Color';
    }
  }
}
