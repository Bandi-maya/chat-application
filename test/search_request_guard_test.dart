import 'package:chat/features/search/search_request_guard.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('SearchRequestGuard', () {
    test('invalidates an older request whenever a new query begins', () {
      final guard = SearchRequestGuard();
      final first = guard.begin();
      final second = guard.begin();

      expect(guard.isCurrent(first), isFalse);
      expect(guard.isCurrent(second), isTrue);
    });

    test('deduplicates canonical records while preserving result order', () {
      final guard = SearchRequestGuard();
      final results = guard.deduplicate(
        const [
          (id: 'user-1', name: 'First'),
          (id: 'user-2', name: 'Second'),
          (id: 'user-1', name: 'Duplicate'),
        ],
        (value) => value.id,
      );

      expect(results.map((value) => value.name), ['First', 'Second']);
    });
  });
}
