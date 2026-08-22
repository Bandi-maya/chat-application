import 'package:flutter/material.dart';
import '../../ui/core/design_system/components/app_components.dart';
import '../../ui/core/theme/app_theme.dart';

class SettingsSearchResult {
  final String title;
  final String category;
  final String description;
  final IconData icon;
  final Widget destination;
  final List<String> keywords;

  const SettingsSearchResult({
    required this.title,
    required this.category,
    required this.description,
    required this.icon,
    required this.destination,
    this.keywords = const [],
  });
}

class SettingsSearchDelegate extends SearchDelegate<SettingsSearchResult?> {
  final List<SettingsSearchResult> allSettings;

  SettingsSearchDelegate({required this.allSettings});

  @override
  List<Widget>? buildActions(BuildContext context) {
    return [
      if (query.isNotEmpty)
        IconButton(
          icon: const Icon(Icons.clear_rounded),
          onPressed: () => query = '',
        ),
    ];
  }

  @override
  Widget? buildLeading(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(8.0),
      child: ChatyBackButton(onPressed: () => close(context, null)),
    );
  }

  @override
  Widget buildResults(BuildContext context) => _buildSearchResults(context);

  @override
  Widget buildSuggestions(BuildContext context) => _buildSearchResults(context);

  Widget _buildSearchResults(BuildContext context) {
    final clean = query.trim().toLowerCase();
    final matches = clean.isEmpty
        ? allSettings
        : allSettings.where((item) {
            return item.title.toLowerCase().contains(clean) ||
                item.category.toLowerCase().contains(clean) ||
                item.description.toLowerCase().contains(clean) ||
                item.keywords.any((k) => k.toLowerCase().contains(clean));
          }).toList();

    if (matches.isEmpty) {
      final colors = context.colors;
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.search_off_rounded,
              size: 48,
              color: colors.foregroundTertiary,
            ),
            const SizedBox(height: 12),
            Text(
              'No settings found for "$query"',
              style: TextStyle(color: colors.foregroundTertiary),
            ),
          ],
        ),
      );
    }

    return ListView.builder(
      itemCount: matches.length,
      itemBuilder: (ctx, idx) {
        final item = matches[idx];
        return ListTile(
          leading: Icon(
            item.icon,
            color: Theme.of(context).colorScheme.primary,
          ),
          title: Text(
            item.title,
            style: const TextStyle(fontWeight: FontWeight.w600),
          ),
          subtitle: Text('${item.category} • ${item.description}'),
          trailing: const Icon(Icons.chevron_right_rounded),
          onTap: () {
            close(context, item);
            Navigator.of(
              context,
            ).push(MaterialPageRoute(builder: (_) => item.destination));
          },
        );
      },
    );
  }
}
