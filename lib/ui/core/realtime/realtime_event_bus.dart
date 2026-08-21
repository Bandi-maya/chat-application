import 'dart:async';

enum RealtimeEventType {
  messageCreated,
  messageUpdated,
  messageDeleted,
  messageDelivered,
  messageRead,
  reactionCreated,
  reactionRemoved,
  typingStarted,
  typingStopped,
  presenceUpdated,
  conversationUpdated,
  memberJoined,
  memberLeft,
  callRinging,
  callAccepted,
  callDeclined,
  callEnded,
}

class RealtimeEvent {
  final RealtimeEventType type;
  final String? conversationId;
  final String? userId;
  final Map<String, dynamic> payload;
  final DateTime timestamp;

  RealtimeEvent({
    required this.type,
    this.conversationId,
    this.userId,
    this.payload = const {},
    DateTime? timestamp,
  }) : timestamp = timestamp ?? DateTime.now();
}

class RealtimeEventBus {
  static final RealtimeEventBus _instance = RealtimeEventBus._internal();
  factory RealtimeEventBus() => _instance;
  RealtimeEventBus._internal();

  final StreamController<RealtimeEvent> _eventController =
      StreamController<RealtimeEvent>.broadcast();

  Stream<RealtimeEvent> get events => _eventController.stream;

  Stream<RealtimeEvent> on(RealtimeEventType type) {
    return _eventController.stream.where((event) => event.type == type);
  }

  Stream<RealtimeEvent> forConversation(String conversationId) {
    return _eventController.stream.where(
      (event) => event.conversationId == conversationId,
    );
  }

  void publish(RealtimeEvent event) {
    if (!_eventController.isClosed) {
      _eventController.add(event);
    }
  }

  void emitTyping(String conversationId, String userId, bool isTyping) {
    publish(
      RealtimeEvent(
        type: isTyping
            ? RealtimeEventType.typingStarted
            : RealtimeEventType.typingStopped,
        conversationId: conversationId,
        userId: userId,
        payload: {'isTyping': isTyping},
      ),
    );
  }

  void emitPresence(String userId, String presence) {
    publish(
      RealtimeEvent(
        type: RealtimeEventType.presenceUpdated,
        userId: userId,
        payload: {'presence': presence},
      ),
    );
  }

  void emitMessageDelivered(String conversationId, String messageId) {
    publish(
      RealtimeEvent(
        type: RealtimeEventType.messageDelivered,
        conversationId: conversationId,
        payload: {'messageId': messageId},
      ),
    );
  }

  void emitMessageRead(String conversationId, String messageId) {
    publish(
      RealtimeEvent(
        type: RealtimeEventType.messageRead,
        conversationId: conversationId,
        payload: {'messageId': messageId},
      ),
    );
  }
}
