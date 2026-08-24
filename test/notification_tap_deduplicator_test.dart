import 'package:chaty/data/services/notification_channel_manager.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('suppresses duplicate message and call notification taps', () {
    var now = DateTime.utc(2026, 1, 1);
    final deduplicator = NotificationTapDeduplicator(clock: () => now);

    const message = NotificationPayload(
      conversationId: 'conversation-a',
      messageId: 'message-1',
    );
    const sameMessageDifferentConversation = NotificationPayload(
      conversationId: 'conversation-b',
      messageId: 'message-1',
    );
    const call = NotificationPayload(
      conversationId: 'conversation-a',
      callId: 'call-1',
      isCall: true,
    );

    expect(deduplicator.shouldDispatch(message), isTrue);
    expect(deduplicator.shouldDispatch(message), isFalse);
    expect(
      deduplicator.shouldDispatch(sameMessageDifferentConversation),
      isFalse,
    );
    expect(deduplicator.shouldDispatch(call), isTrue);
    expect(deduplicator.shouldDispatch(call), isFalse);

    now = now.add(const Duration(seconds: 11));
    expect(deduplicator.shouldDispatch(message), isTrue);
  });

  test('rejects malformed payloads and deduplicates ID-less launch intents', () {
    final deduplicator = NotificationTapDeduplicator();

    expect(
      deduplicator.shouldDispatch(
        const NotificationPayload(conversationId: ''),
      ),
      isFalse,
    );
    const payload = NotificationPayload(conversationId: 'conversation-a');
    expect(deduplicator.shouldDispatch(payload), isTrue);
    expect(deduplicator.shouldDispatch(payload), isFalse);
  });
}
