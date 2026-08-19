import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../../../ui/core/controllers/chaty_preferences_controller.dart';
import '../../../ui/core/gb/gb_feature_catalog.dart';

class GbFeatureCenterScreen extends StatefulWidget {
  final ChatyPreferencesController preferencesController;

  const GbFeatureCenterScreen({super.key, required this.preferencesController});

  @override
  State<GbFeatureCenterScreen> createState() => _GbFeatureCenterScreenState();
}

class _GbFeatureCenterScreenState extends State<GbFeatureCenterScreen> {
  final TextEditingController _searchController = TextEditingController();
  String _query = '';
  String _category = 'All';

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.preferencesController,
      builder: (context, _) {
        final scheme = Theme.of(context).colorScheme;
        final definitions = GbFeatureCatalog.all.where((item) {
          final categoryMatch = _category == 'All' || item.category == _category;
          if (!categoryMatch) return false;
          if (_query.isEmpty) return true;
          final q = _query.toLowerCase();
          return item.title.toLowerCase().contains(q) ||
              item.key.toLowerCase().contains(q) ||
              item.description.toLowerCase().contains(q) ||
              item.category.toLowerCase().contains(q);
        }).toList(growable: false);

        return Scaffold(
          appBar: AppBar(
            leading: IconButton(
              tooltip: 'Back',
              onPressed: () => Navigator.of(context).pop(),
              icon: const Icon(Icons.chevron_left_rounded),
            ),
            title: const Text('GB Feature Center'),
            actions: [
              IconButton(
                tooltip: 'Reset extracted features',
                onPressed: _confirmReset,
                icon: const Icon(Icons.restart_alt_rounded),
              ),
            ],
          ),
          body: SafeArea(
            top: false,
            child: Column(
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 10),
                  child: _SummaryCard(
                    enabled: widget.preferencesController.gbFeatures.values.whereType<bool>().where((value) => value).length,
                    total: GbFeatureCatalog.all.length,
                    onGhostMode: () => _applyBundle(<String, Object?>{
                      'yo_want_ghostmode': true,
                      'yoHideSeen': true,
                      'yoHideStatViewV2': true,
                      'abu_saleh_toast_typing': false,
                      'abu_saleh_toast_online': false,
                      'always_online': false,
                    }, 'Stealth privacy bundle'),
                    onStandardMode: () => _applyBundle(<String, Object?>{
                      'yo_want_ghostmode': false,
                      'yo_want_airplanemode': false,
                      'yoHideSeen': false,
                      'yoHideStatViewV2': false,
                      'always_online': false,
                    }, 'Standard connectivity bundle'),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: TextField(
                    controller: _searchController,
                    onChanged: (value) => setState(() => _query = value.trim()),
                    decoration: InputDecoration(
                      hintText: 'Search all ${GbFeatureCatalog.all.length} extracted features',
                      prefixIcon: const Icon(Icons.search_rounded),
                      suffixIcon: _query.isEmpty
                          ? null
                          : IconButton(
                              onPressed: () {
                                _searchController.clear();
                                setState(() => _query = '');
                              },
                              icon: const Icon(Icons.close_rounded),
                            ),
                      filled: true,
                      fillColor: scheme.surfaceContainerLow,
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: BorderSide.none),
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                SizedBox(
                  height: 40,
                  child: ListView.separated(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    scrollDirection: Axis.horizontal,
                    itemCount: GbFeatureCatalog.categories.length + 1,
                    separatorBuilder: (_, _) => const SizedBox(width: 8),
                    itemBuilder: (context, index) {
                      final category = index == 0 ? 'All' : GbFeatureCatalog.categories[index - 1];
                      return ChoiceChip(
                        label: Text(category),
                        selected: _category == category,
                        onSelected: (_) => setState(() => _category = category),
                      );
                    },
                  ),
                ),
                const SizedBox(height: 8),
                Expanded(
                  child: definitions.isEmpty
                      ? const Center(child: Text('No matching features'))
                      : ListView.builder(
                          padding: const EdgeInsets.fromLTRB(12, 0, 12, 30),
                          itemCount: definitions.length,
                          itemBuilder: (context, index) {
                            final item = definitions[index];
                            return _FeatureTile(
                              definition: item,
                              controller: widget.preferencesController,
                              onColor: () => _editColor(item),
                              onAction: () => _runAction(item),
                            );
                          },
                        ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  void _applyBundle(Map<String, Object?> values, String title) {
    widget.preferencesController.updateGbFeatures(values, logTitle: title);
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text('$title applied')));
  }

  Future<void> _editColor(GbFeatureDefinition item) async {
    final current = widget.preferencesController.gbInt(item.key);
    final initial = current == 0 ? '' : '#${current.toRadixString(16).padLeft(8, '0').toUpperCase()}';
    final controller = TextEditingController(text: initial);
    final presets = <int>[
      0xFF000000, 0xFFFFFFFF, 0xFF2563EB, 0xFF7C3AED, 0xFFDB2777, 0xFFDC2626,
      0xFFEA580C, 0xFFCA8A04, 0xFF16A34A, 0xFF0891B2, 0xFF475569, 0xFF18181B,
    ];
    final selected = await showDialog<int?>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(item.title),
        content: SizedBox(
          width: 360,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(item.description),
              const SizedBox(height: 16),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  for (final color in presets)
                    InkWell(
                      borderRadius: BorderRadius.circular(20),
                      onTap: () => Navigator.of(dialogContext).pop(color),
                      child: Container(
                        width: 34,
                        height: 34,
                        decoration: BoxDecoration(
                          color: Color(color),
                          shape: BoxShape.circle,
                          border: Border.all(color: Theme.of(context).colorScheme.outlineVariant),
                        ),
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 16),
              TextField(
                controller: controller,
                decoration: const InputDecoration(labelText: 'ARGB hex', hintText: '#FF6366F1', border: OutlineInputBorder()),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(dialogContext).pop(0), child: const Text('Theme default')),
          TextButton(onPressed: () => Navigator.of(dialogContext).pop(), child: const Text('Cancel')),
          FilledButton(
            onPressed: () {
              var text = controller.text.trim().replaceFirst('#', '').replaceFirst('0x', '');
              if (text.length == 6) text = 'FF$text';
              final value = int.tryParse(text, radix: 16);
              if (value != null) Navigator.of(dialogContext).pop(value);
            },
            child: const Text('Apply'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (selected != null) widget.preferencesController.updateGbFeature(item.key, selected);
  }

  Future<void> _runAction(GbFeatureDefinition item) async {
    if (item.key == 'clear_logs') {
      widget.preferencesController.clearPreferenceHistory();
      _toast('Local preference history cleared.');
      return;
    }
    if (item.key == 'mas_key_cleanlog_blocklist') {
      final user = Supabase.instance.client.auth.currentUser;
      if (user == null) return;
      try {
        await Supabase.instance.client.from('blocked_users').delete().eq('blocker_id', user.id);
        _toast('Your block list was cleared.');
      } catch (error) {
        _toast('Unable to clear block list: $error');
      }
      return;
    }

    final result = await showDialog<String>(
      context: context,
      builder: (dialogContext) {
        final input = TextEditingController(text: widget.preferencesController.gbString(item.key));
        return AlertDialog(
          title: Text(item.title),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(item.description),
              const SizedBox(height: 14),
              TextField(controller: input, decoration: const InputDecoration(labelText: 'Configuration value', border: OutlineInputBorder())),
            ],
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(dialogContext), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(dialogContext, input.text.trim()), child: const Text('Save')),
          ],
        );
      },
    );
    if (result != null) widget.preferencesController.updateGbFeature(item.key, result);
  }

  Future<void> _confirmReset() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Reset extracted features?'),
        content: const Text('This resets the compatibility controls to Chaty defaults. Existing chats and server data are not deleted.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Reset')),
        ],
      ),
    );
    if (confirmed == true) widget.preferencesController.resetGbFeatures();
  }

  void _toast(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }
}

class _SummaryCard extends StatelessWidget {
  final int enabled;
  final int total;
  final VoidCallback onGhostMode;
  final VoidCallback onStandardMode;

  const _SummaryCard({required this.enabled, required this.total, required this.onGhostMode, required this.onStandardMode});

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: scheme.primaryContainer.withValues(alpha: 0.38),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: scheme.outlineVariant),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.tune_rounded, color: scheme.primary),
              const SizedBox(width: 8),
              Expanded(child: Text('$total extracted GB-style controls', style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800))),
              Text('$enabled enabled', style: TextStyle(color: scheme.onSurfaceVariant)),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            'Controls are implemented independently in Chaty and synchronized with your account. Core privacy, messaging, status, presence and automation controls are enforced by runtime/server paths.',
            style: TextStyle(color: scheme.onSurfaceVariant, height: 1.35),
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              FilledButton.tonalIcon(onPressed: onGhostMode, icon: const Icon(Icons.visibility_off_rounded), label: const Text('Stealth bundle')),
              OutlinedButton.icon(onPressed: onStandardMode, icon: const Icon(Icons.wifi_rounded), label: const Text('Standard mode')),
            ],
          ),
        ],
      ),
    );
  }
}

class _FeatureTile extends StatelessWidget {
  final GbFeatureDefinition definition;
  final ChatyPreferencesController controller;
  final VoidCallback onColor;
  final VoidCallback onAction;

  const _FeatureTile({required this.definition, required this.controller, required this.onColor, required this.onAction});

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    Widget trailing;
    VoidCallback? onTap;

    switch (definition.kind) {
      case GbFeatureKind.toggle:
        trailing = Switch.adaptive(
          value: controller.gbBool(definition.key, fallback: definition.defaultValue == true),
          onChanged: (value) => controller.updateGbFeature(definition.key, value),
        );
        break;
      case GbFeatureKind.slider:
        final value = controller.gbDouble(
          definition.key,
          fallback: (definition.defaultValue as num?)?.toDouble() ?? definition.min,
        ).clamp(definition.min, definition.max);
        trailing = SizedBox(
          width: 132,
          child: Row(
            children: [
              Expanded(child: Slider(min: definition.min, max: definition.max, value: value, onChanged: (next) => controller.updateGbFeature(definition.key, next))),
              SizedBox(
                width: 36,
                child: Text(value >= 100 ? value.round().toString() : value.toStringAsFixed(value % 1 == 0 ? 0 : 1), textAlign: TextAlign.end, style: Theme.of(context).textTheme.labelSmall),
              ),
            ],
          ),
        );
        break;
      case GbFeatureKind.choice:
        final current = controller.gbString(definition.key, fallback: definition.defaultValue?.toString() ?? '');
        trailing = ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 130),
          child: Text(current, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(color: scheme.primary, fontWeight: FontWeight.w700)),
        );
        onTap = () => _choose(context, current);
        break;
      case GbFeatureKind.color:
        final color = controller.gbColor(definition.key);
        trailing = Container(
          width: 34,
          height: 34,
          decoration: BoxDecoration(color: color ?? scheme.surfaceContainerHighest, shape: BoxShape.circle, border: Border.all(color: scheme.outlineVariant)),
          child: color == null ? const Icon(Icons.palette_outlined, size: 17) : null,
        );
        onTap = onColor;
        break;
      case GbFeatureKind.action:
        trailing = const Icon(Icons.chevron_right_rounded);
        onTap = onAction;
        break;
    }

    return Card(
      elevation: 0,
      margin: const EdgeInsets.symmetric(vertical: 4),
      color: scheme.surfaceContainerLow,
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 5),
        title: Text(definition.title, style: const TextStyle(fontWeight: FontWeight.w700)),
        subtitle: Padding(
          padding: const EdgeInsets.only(top: 3),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(definition.description, maxLines: 3, overflow: TextOverflow.ellipsis),
              const SizedBox(height: 3),
              Text(definition.key, style: Theme.of(context).textTheme.labelSmall?.copyWith(color: scheme.outline)),
            ],
          ),
        ),
        trailing: trailing,
        onTap: onTap,
      ),
    );
  }

  Future<void> _choose(BuildContext context, String current) async {
    final options = definition.options;
    if (options.isEmpty) return;
    final selected = await showModalBottomSheet<String>(
      context: context,
      useSafeArea: true,
      builder: (context) => ListView(
        shrinkWrap: true,
        padding: const EdgeInsets.symmetric(vertical: 10),
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(18, 8, 18, 10),
            child: Text(definition.title, style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800)),
          ),
          for (final option in options)
            ListTile(
              title: Text(option),
              trailing: option == current ? const Icon(Icons.check_rounded) : const Icon(Icons.chevron_right_rounded),
              onTap: () => Navigator.of(context).pop(option),
            ),
        ],
      ),
    );
    if (selected != null) controller.updateGbFeature(definition.key, selected);
  }
}
