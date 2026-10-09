import 'customization_snapshot.dart';
import 'component_scope.dart';

class CustomizationHistoryItem {
  final DateTime timestamp;
  final ComponentScope scope;
  final CustomizationSnapshot previous;
  final CustomizationSnapshot next;
  final String description;

  const CustomizationHistoryItem({
    required this.timestamp,
    required this.scope,
    required this.previous,
    required this.next,
    this.description = '',
  });
}

class CustomizationHistory {
  static const int maxCapacity = 10;
  final List<CustomizationHistoryItem> _items = [];

  List<CustomizationHistoryItem> get items => List.unmodifiable(_items);

  bool get canUndo => _items.isNotEmpty;

  void record({
    required ComponentScope scope,
    required CustomizationSnapshot previous,
    required CustomizationSnapshot next,
    String description = '',
  }) {
    if (previous == next) return;
    if (_items.length >= maxCapacity) {
      _items.removeAt(0);
    }
    _items.add(
      CustomizationHistoryItem(
        timestamp: DateTime.now(),
        scope: scope,
        previous: previous,
        next: next,
        description: description,
      ),
    );
  }

  CustomizationHistoryItem? popUndo() {
    if (_items.isEmpty) return null;
    return _items.removeLast();
  }

  void clear() {
    _items.clear();
  }
}
