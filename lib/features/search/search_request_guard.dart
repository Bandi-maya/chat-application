/// Tracks monotonically increasing search requests so asynchronous results can
/// never replace results for a newer query.
class SearchRequestGuard {
  int _requestId = 0;

  int begin() => ++_requestId;

  bool isCurrent(int requestId) => requestId == _requestId;

  /// Preserves backend ordering while removing repeated canonical records.
  List<T> deduplicate<T>(Iterable<T> values, String Function(T value) idOf) {
    final seen = <String>{};
    return values
        .where((value) => seen.add(idOf(value)))
        .toList(growable: false);
  }
}
