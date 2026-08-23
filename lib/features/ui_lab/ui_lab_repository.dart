import 'ui_lab_models.dart';

/// Deterministic UI-only fixtures for new/unapproved frontend concepts.
///
/// Production messaging must never depend on this repository. Components leave
/// the UI Lab only after their interaction contracts are accepted and mapped to
/// a real feature repository.
class UiLabRepository {
  const UiLabRepository();

  List<UiLabConversationFixture> conversations(UiLabScenario scenario) {
    if (scenario == UiLabScenario.empty) return const [];
    final dense = scenario == UiLabScenario.dense;
    final base = <UiLabConversationFixture>[
      const UiLabConversationFixture(
        id: 'maya',
        title: 'Maya',
        preview: 'Voice note · 0:18',
        timeLabel: 'Now',
        unreadCount: 3,
        isPinned: true,
        isMuted: false,
        isOnline: true,
      ),
      const UiLabConversationFixture(
        id: 'design',
        title: 'Design crew',
        preview: 'Task moved to Review',
        timeLabel: '2m',
        unreadCount: 0,
        isPinned: false,
        isMuted: true,
        isOnline: false,
      ),
      const UiLabConversationFixture(
        id: 'pavan',
        title: 'Pavan',
        preview: '📷 4 photos',
        timeLabel: '18m',
        unreadCount: 1,
        isPinned: false,
        isMuted: false,
        isOnline: false,
      ),
    ];
    if (!dense) return base;
    return <UiLabConversationFixture>[
      ...base,
      for (var i = 0; i < 14; i++)
        UiLabConversationFixture(
          id: 'dense_$i',
          title: 'Conversation ${i + 4}',
          preview: i.isEven ? 'Draft · finish the proposal' : 'See you at 6:30',
          timeLabel: '${i + 1}h',
          unreadCount: i % 4,
          isPinned: false,
          isMuted: i % 3 == 0,
          isOnline: i % 5 == 0,
        ),
    ];
  }

  List<UiLabMessageFixture> messages(UiLabScenario scenario) {
    if (scenario == UiLabScenario.empty) return const [];
    return const <UiLabMessageFixture>[
      UiLabMessageFixture(
        id: '1',
        text: 'The new composer feels much faster.',
        isMine: false,
        timeLabel: '10:28',
        delivery: 'read',
      ),
      UiLabMessageFixture(
        id: '2',
        text: 'I converted the feedback into a task.',
        isMine: true,
        timeLabel: '10:29',
        delivery: 'read',
        replyTo: 'The new composer feels much faster.',
        reaction: '🔥',
      ),
      UiLabMessageFixture(
        id: '3',
        text: 'Try long-pressing this message for the context halo.',
        isMine: false,
        timeLabel: '10:30',
        delivery: 'delivered',
      ),
    ];
  }
}
