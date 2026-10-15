import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import '../models/dynamic_island_payload.dart';

/// Controller orchestrating the 4-state Dynamic Island state machine,
/// priority queue, auto-collapse timers, and bidirectional native sync.
class DynamicIslandController extends ChangeNotifier {
  static const MethodChannel _channel = MethodChannel('chaty/dynamic_island');

  IslandMode _mode = IslandMode.idle;
  IslandMode get mode => _mode;

  // Active stack of payloads sorted by priority, then timestamp descending
  final List<DynamicIslandPayload> _queue = [];
  List<DynamicIslandPayload> get queue => List.unmodifiable(_queue);

  DynamicIslandPayload? get activePayload => _queue.isNotEmpty ? _queue.first : null;
  DynamicIslandPayload? get secondaryPayload => _queue.length > 1 ? _queue[1] : null;

  // Sub-view toggle when expanded (matching reference repo: 'player' vs 'notifications')
  String _activeSubView = 'primary';
  String get activeSubView => _activeSubView;

  Timer? _collapseTimer;
  Timer? _dismissTimer;
  bool _isPinned = false;
  bool get isPinned => _isPinned;

  // Auto-collapse duration constants
  static const Duration expandedAutoCollapseDuration = Duration(milliseconds: 4500);
  static const Duration compactIdleTimeout = Duration(milliseconds: 7000);

  DynamicIslandController() {
    _initChannel();
  }

  void _initChannel() {
    _channel.setMethodCallHandler((call) async {
      switch (call.method) {
        case 'onNativeNotificationPosted':
          final args = call.arguments as Map<dynamic, dynamic>?;
          if (args != null) {
            postPayload(DynamicIslandPayload(
              id: args['key']?.toString() ?? UniqueKey().toString(),
              type: (args['isCall'] == true)
                  ? IslandPayloadType.incomingCall
                  : IslandPayloadType.chatMessage,
              title: args['title']?.toString() ?? 'Notification',
              message: args['body']?.toString() ?? '',
              timestamp: DateTime.now(),
              iconEmoji: '💬',
            ));
          }
          break;
        case 'onNativeNotificationRemoved':
          final key = call.arguments?.toString();
          if (key != null) {
            removePayload(key);
          }
          break;
        case 'onClearAll':
          clearAll();
          break;
      }
    });
  }

  /// Posts or updates a payload in the priority queue.
  void postPayload(DynamicIslandPayload payload, {bool autoExpand = true}) {
    final existingIndex = _queue.indexOfFirst((p) => p.id == payload.id);
    if (existingIndex >= 0) {
      _queue[existingIndex] = payload;
    } else {
      _queue.add(payload);
    }

    _sortQueue();

    // Determine state transition
    if (_queue.length >= 2 &&
        _queue[0].isOngoingActivity &&
        !_queue[1].isOngoingActivity) {
      _mode = IslandMode.split;
    } else if (autoExpand) {
      _mode = IslandMode.expanded;
      _scheduleAutoCollapse();
    } else if (_mode == IslandMode.idle) {
      _mode = IslandMode.compact;
      _scheduleDismissTimer();
    }

    notifyListeners();
  }

  /// Removes a payload by its ID.
  void removePayload(String id) {
    _queue.removeWhere((p) => p.id == id);
    _reconcileStateAfterRemoval();
    notifyListeners();
  }

  /// Clears all payloads and transitions to idle.
  void clearAll() {
    _queue.clear();
    _mode = IslandMode.idle;
    _cancelTimers();
    notifyListeners();
    _channel.invokeMethod('clearAll').catchError((_) {});
  }

  /// Transitions to expanded state.
  void expand({String? subView}) {
    if (_queue.isEmpty) return;
    _mode = IslandMode.expanded;
    if (subView != null) {
      _activeSubView = subView;
    } else {
      _activeSubView = activePayload?.type == IslandPayloadType.media ||
              activePayload?.type == IslandPayloadType.voiceNotePlayback
          ? 'player'
          : 'primary';
    }
    _cancelTimers();
    if (!_isPinned) {
      _scheduleAutoCollapse();
    }
    notifyListeners();
  }

  /// Collapses to compact or split mode.
  void collapse() {
    if (_queue.isEmpty) {
      _mode = IslandMode.idle;
    } else if (_queue.length >= 2 &&
        _queue[0].isOngoingActivity &&
        !_queue[1].isOngoingActivity) {
      _mode = IslandMode.split;
    } else {
      _mode = IslandMode.compact;
    }
    _cancelTimers();
    _scheduleDismissTimer();
    notifyListeners();
  }

  /// Directly set mode.
  void setMode(IslandMode newMode) {
    if (_mode == newMode) return;
    _mode = newMode;
    if (newMode == IslandMode.expanded) {
      _scheduleAutoCollapse();
    } else if (newMode == IslandMode.compact) {
      _scheduleDismissTimer();
    }
    notifyListeners();
  }

  /// Toggles between expanded and collapsed.
  void toggleExpanded() {
    if (_mode == IslandMode.expanded) {
      collapse();
    } else {
      expand();
    }
  }

  /// Switches active expanded sub-view ('player' or 'notifications').
  void setSubView(String subView) {
    _activeSubView = subView;
    _scheduleAutoCollapse();
    notifyListeners();
  }

  /// Pins the island open (e.g. while user is typing a reply).
  void pinOpen(bool pinned) {
    _isPinned = pinned;
    if (pinned) {
      _cancelTimers();
    } else {
      _scheduleAutoCollapse();
    }
    notifyListeners();
  }

  /// Dismisses the active primary payload and promotes the next in queue.
  void dismissActive() {
    if (_queue.isNotEmpty) {
      final removed = _queue.removeAt(0);
      removed.onDismiss?.call();
      _reconcileStateAfterRemoval();
      notifyListeners();
    }
  }

  void _sortQueue() {
    _queue.sort((a, b) {
      final pComp = a.priority.compareTo(b.priority);
      if (pComp != 0) return pComp;
      return b.timestamp.compareTo(a.timestamp);
    });
  }

  void _reconcileStateAfterRemoval() {
    _cancelTimers();
    if (_queue.isEmpty) {
      _mode = IslandMode.idle;
    } else if (_queue.length >= 2 &&
        _queue[0].isOngoingActivity &&
        !_queue[1].isOngoingActivity) {
      _mode = IslandMode.split;
    } else if (_queue.first.isOngoingActivity) {
      _mode = IslandMode.compact;
    } else {
      _mode = IslandMode.compact;
      _scheduleDismissTimer();
    }
  }

  void _scheduleAutoCollapse() {
    _collapseTimer?.cancel();
    if (activePayload?.isOngoingActivity == true || _isPinned) {
      return; // Ongoing activities don't collapse abruptly
    }
    _collapseTimer = Timer(expandedAutoCollapseDuration, () {
      if (_mode == IslandMode.expanded) {
        collapse();
      }
    });
  }

  void _scheduleDismissTimer() {
    _dismissTimer?.cancel();
    if (activePayload?.isOngoingActivity == true || _isPinned) {
      return; // Ongoing activities stay in compact
    }
    _dismissTimer = Timer(compactIdleTimeout, () {
      if (_queue.isNotEmpty && !_queue.first.isOngoingActivity) {
        _queue.clear();
        _mode = IslandMode.idle;
        notifyListeners();
      }
    });
  }

  void _cancelTimers() {
    _collapseTimer?.cancel();
    _dismissTimer?.cancel();
  }

  @override
  void dispose() {
    _cancelTimers();
    super.dispose();
  }
}

extension _IterableExt<T> on List<T> {
  int indexOfFirst(bool Function(T element) test) {
    for (int i = 0; i < length; i++) {
      if (test(this[i])) return i;
    }
    return -1;
  }
}
