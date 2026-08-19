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
  String? _category;

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
        final q = _query.toLowerCase();
        final definitions = GbFeatureCatalog.all.where((item) {
          final categoryMatch = _category == null || item.category == _category;
          if (!categoryMatch) return false;
          if (q.isEmpty) return true;
          return item.title.toLowerCase().contains(q) ||
              item.description.toLowerCase().contains(q) ||
              item.category.toLowerCase().contains(q) ||
              item.key.toLowerCase().contains(q);
        }).toList(growable: false);
        final browsingCategories = _category == null && _query.isEmpty;

        return Scaffold(
          appBar: AppBar(
            leading: IconButton(
              tooltip: _category == null ? 'Back' : 'All categories',
              onPressed: () {
                if (_category != null) {
                  setState(() => _category = null);
                } else {
                  Navigator.of(context).pop();
                }
              },
              icon: const Icon(Icons.chevron_left_rounded),
            ),
            title: Text(_category ?? 'Advanced Features'),
            actions: [
              IconButton(
                tooltip: 'Reset advanced features',
                onPressed: _confirmReset,
                icon: const Icon(Icons.restart_alt_rounded),
              ),
            ],
          ),
          body: SafeArea(
            top: false,
            child: Column(
              children: [
                if (browsingCategories)
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
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 10),
                  child: TextField(
                    controller: _searchController,
                    onChanged: (value) => setState(() => _query = value.trim()),
                    decoration: InputDecoration(
                      hintText: _category == null ? 'Search all advanced settings' : 'Search in $_category',
                      prefixIcon: const Icon(Icons.search_rounded),
                      suffixIcon: _query.isEmpty
                          ? null
                          : IconButton(
                              tooltip: 'Clear search',
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
                Expanded(
                  child: browsingCategories
                      ? _CategoryList(
                          controller: widget.preferencesController,
                          onOpen: (category) => setState(() => _category = category),
                        )
                      : definitions.isEmpty
                          ? const Center(child: Text('No matching settings'))
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
      0xFF000000,
      0xFFFFFFFF,
      0xFF2563EB,
      0xFF7C3AED,
      0xFFDB2777,
      0xFFDC2626,
      0xFFEA580C,
      0xFFCA8A04,
      0xFF16A34A,
      0xFF0891B2,
      0xFF475569,
      0xFF18181B,
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
        title: const Text('Reset advanced features?'),
        content: const Text('This resets advanced controls to Chaty defaults. Existing chats and server data are not deleted.'),
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

class _CategoryList extends StatelessWidget {
  final ChatyPreferencesController controller;
  final ValueChanged<String> onOpen;

  const _CategoryList({required this.controller, required this.onOpen});

  @override
  Widget build(BuildContext context) {
    return ListView.separated(
      padding: const EdgeInsets.fromLTRB(12, 0, 12, 30),
      itemCount: GbFeatureCatalog.categories.length,
      separatorBuilder: (_, _) => const SizedBox(height: 4),
      itemBuilder: (context, index) {
        final category = GbFeatureCatalog.categories[index];
        final items = GbFeatureCatalog.all.where((item) => item.category == category).toList(growable: false);
        final enabled = items.where((item) => item.kind == GbFeatureKind.toggle && controller.gbBool(item.key, fallback: item.defaultValue == true)).length;
        return Card(
          elevation: 0,
          color: Theme.of(context).colorScheme.surfaceContainerLow,
          child: ListTile(
            contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
            leading: Container(
              width: 42,
              height: 42,
              decoration: BoxDecoration(
                color: Theme.of(context).colorScheme.primaryContainer,
                borderRadius: BorderRadius.circular(13),
              ),
              child: Icon(_categoryIcon(category), color: Theme.of(context).colorScheme.onPrimaryContainer, size: 21),
            ),
            title: Text(category, style: const TextStyle(fontWeight: FontWeight.w800)),
            subtitle: Text('$enabled enabled • ${items.length} settings'),
            trailing: const Icon(Icons.chevron_right_rounded),
            onTap: () => onOpen(category),
          ),
        );
      },
    );
  }

  static IconData _categoryIcon(String category) {
    final value = category.toLowerCase();
    if (value.contains('privacy')) return Icons.shield_outlined;
    if (value.contains('call')) return Icons.call_outlined;
    if (value.contains('media')) return Icons.perm_media_outlined;
    if (value.contains('status')) return Icons.auto_stories_outlined;
    if (value.contains('notification') || value.contains('alert')) return Icons.notifications_outlined;
    if (value.contains('font') || value.contains('icon')) return Icons.text_fields_rounded;
    if (value.contains('color') || value.contains('appearance')) return Icons.palette_outlined;
    if (value.contains('composer')) return Icons.edit_note_rounded;
    if (value.contains('bubble') || value.contains('conversation')) return Icons.chat_bubble_outline_rounded;
    if (value.contains('home')) return Icons.home_outlined;
    if (value.contains('navigation')) return Icons.space_dashboard_outlined;
    if (value.contains('storage')) return Icons.storage_outlined;
    return Icons.tune_rounded;
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
              Expanded(child: Text('$total advanced controls', style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800))),
              Text('$enabled enabled', style: TextStyle(color: scheme.onSurfaceVariant)),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            'Settings are grouped by purpose. Open one category at a time instead of scanning one very long list.',
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
          child: Text(definition.description, maxLines: 3, overflow: TextOverflow.ellipsis),
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
