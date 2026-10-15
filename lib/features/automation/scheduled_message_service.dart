import 'dart:async';
import 'package:flutter/foundation.dart';
import '../../data/services/gb_feature_backend_service.dart';

/// Service managing scheduled message queues and background dispatch.
class ScheduledMessageService extends ChangeNotifier {
  ScheduledMessageService({GbFeatureBackendService? backendService})
      : _backend = backendService ?? GbFeatureBackendService();

  final GbFeatureBackendService _backend;
  List<ServerScheduledMessage> _messages = <ServerScheduledMessage>[];
  bool _isLoading = false;
  String? _lastError;

  List<ServerScheduledMessage> get messages => List.unmodifiable(_messages);
  bool get isLoading => _isLoading;
  String? get lastError => _lastError;

  /// Refreshes the list of scheduled messages from the backend.
  Future<List<ServerScheduledMessage>> refresh() async {
    _isLoading = true;
    _lastError = null;
    notifyListeners();

    try {
      final results = await _backend.getScheduledMessages();
      _messages = results;
      return _messages;
    } catch (e) {
      _lastError = e.toString();
      debugPrint('ScheduledMessageService refresh note: $e');
      return _messages;
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  /// Schedules a message for future delivery at [scheduledAt].
  Future<String> scheduleMessage({
    required String conversationId,
    required String body,
    required DateTime scheduledAt,
  }) async {
    final id = await _backend.scheduleMessage(
      conversationId: conversationId,
      body: body,
      scheduledAt: scheduledAt,
    );
    unawaited(refresh());
    return id;
  }

  /// Cancels a pending scheduled message by [id].
  Future<void> cancelScheduledMessage(String id) async {
    await _backend.cancelScheduledMessage(id);
    _messages.removeWhere((m) => m.id == id);
    notifyListeners();
    unawaited(refresh());
  }
}
