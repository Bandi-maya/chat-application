import '../../data/services/status_service.dart';

/// Prevents repeated widget opens and rebuilds from publishing duplicate views.
class StatusViewSession {
  final Set<String> _recorded = <String>{};

  String _key(StatusRecord status, String viewerId) => '${status.id}:$viewerId';

  bool tryBegin(StatusRecord status, String viewerId, {DateTime? now}) {
    final effectiveNow = now ?? DateTime.now();
    if (status.id.isEmpty ||
        viewerId.isEmpty ||
        viewerId == status.userId ||
        status.isDeleted ||
        !status.expiresAt.isAfter(effectiveNow)) {
      return false;
    }
    return _recorded.add(_key(status, viewerId));
  }

  void rollback(StatusRecord status, String viewerId) {
    _recorded.remove(_key(status, viewerId));
  }
}
