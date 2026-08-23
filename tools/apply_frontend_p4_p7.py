#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding='utf-8')


# ---------------------------------------------------------------------------
# P4/P7 — native-looking poll summary card for the conversation timeline.
# The server-backed poll viewer remains authoritative when tapped.
# ---------------------------------------------------------------------------
path = 'lib/ui/core/design_system/components/messaging_components.dart'
text = read(path)
if 'class ChatyPollSummaryCard extends StatelessWidget' not in text:
    insertion = r'''

class ChatyPollSummaryCard extends StatelessWidget {
  const ChatyPollSummaryCard({
    super.key,
    required this.question,
    required this.onTap,
    this.timeLabel,
    this.isMine = false,
  });

  final String question;
  final VoidCallback onTap;
  final String? timeLabel;
  final bool isMine;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final background = isMine
        ? scheme.primaryContainer
        : scheme.surfaceContainerHighest;
    return Semantics(
      button: true,
      label: 'Poll. $question. Tap to vote or view results.',
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(20),
        child: Container(
          constraints: const BoxConstraints(minWidth: 220, maxWidth: 340),
          padding: const EdgeInsets.fromLTRB(13, 12, 13, 9),
          decoration: BoxDecoration(
            color: background,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
              color: scheme.primary.withValues(alpha: .18),
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    width: 34,
                    height: 34,
                    decoration: BoxDecoration(
                      color: scheme.primary.withValues(alpha: .13),
                      borderRadius: BorderRadius.circular(11),
                    ),
                    child: Icon(
                      Icons.poll_rounded,
                      color: scheme.primary,
                      size: 19,
                    ),
                  ),
                  const SizedBox(width: 9),
                  Expanded(
                    child: Text(
                      question.trim().isEmpty ? 'Poll' : question.trim(),
                      maxLines: 3,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: scheme.onSurface,
                        fontSize: 14,
                        fontWeight: FontWeight.w800,
                        height: 1.25,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              Row(
                children: [
                  Icon(
                    Icons.touch_app_rounded,
                    size: 14,
                    color: scheme.primary,
                  ),
                  const SizedBox(width: 5),
                  Text(
                    'Tap to vote or view results',
                    style: TextStyle(
                      color: scheme.primary,
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const Spacer(),
                  if (timeLabel != null)
                    Text(
                      timeLabel!,
                      style: TextStyle(
                        color: scheme.onSurfaceVariant,
                        fontSize: 10.5,
                      ),
                    ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
'''
    text += insertion
write(path, text)


# ---------------------------------------------------------------------------
# P6/P7 — attachment actions: no raw native/Postgres exception strings in UI,
# robust poll marker parsing, and permission recovery language.
# ---------------------------------------------------------------------------
path = 'lib/features/messages/chat_attachment_actions.dart'
text = read(path)
if "components/signature_components.dart" not in text:
    marker = "import '../../ui/core/controllers/preferences_controller.dart';"
    text = text.replace(
        marker,
        marker + "\nimport '../../ui/core/design_system/components/signature_components.dart';",
        1,
    )

old = """  static bool isPollMessage(ChatMessage message) =>
      message.text.startsWith('[POLL] ');
"""
new = """  static bool isPollMessage(ChatMessage message) =>
      message.text.trimLeft().startsWith('[POLL]');

  static String pollQuestion(ChatMessage message) {
    var value = message.text.trim();
    // Older builds could accidentally prepend the marker more than once.
    // Strip all transport markers before presenting user-visible content.
    while (value.startsWith('[POLL]')) {
      value = value.substring('[POLL]'.length).trimLeft();
    }
    return value.trim();
  }
"""
if old in text:
    text = text.replace(old, new, 1)
elif 'static String pollQuestion(ChatMessage message)' not in text:
    raise SystemExit('P4-P7 poll helper marker missing')

old_toast = """  static void _toast(BuildContext context, String message) {
    if (!context.mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }
"""
new_toast = """  static void _toast(BuildContext context, String message) {
    if (!context.mounted) return;
    final lower = message.toLowerCase();
    final failed = lower.startsWith('unable') ||
        lower.contains('failed') ||
        lower.contains('denied') ||
        lower.contains('error');
    if (failed) debugPrint('Chaty attachment action failed: $message');

    String title = message;
    String? subtitle;
    if (failed) {
      if (lower.contains('location')) {
        title = 'Couldn’t share the location';
        subtitle = lower.contains('permission') || lower.contains('denied')
            ? 'Allow location access in system settings, then try again.'
            : 'Check location services and your connection, then retry.';
      } else if (lower.contains('voice')) {
        title = 'Voice note is waiting';
        subtitle = 'Chaty will retry when the secure conversation is ready.';
      } else if (lower.contains('poll')) {
        title = 'Couldn’t create the poll';
        subtitle = 'Check the connection and try again.';
      } else {
        title = 'Couldn’t send this item';
        subtitle = 'Check the connection and try again.';
      }
    }
    ChatyActivityIsland.show(
      context,
      icon: failed ? Icons.info_outline_rounded : Icons.check_circle_outline_rounded,
      title: title,
      subtitle: subtitle,
    );
  }
"""
if old_toast in text:
    text = text.replace(old_toast, new_toast, 1)
elif 'Chaty attachment action failed:' not in text:
    raise SystemExit('P4-P7 attachment feedback marker missing')
write(path, text)


# ---------------------------------------------------------------------------
# P4/P7 — render polls as a first-class timeline component instead of exposing
# the transport marker as "[POLL] [POLL] ..." text.
# ---------------------------------------------------------------------------
path = 'lib/features/chats/chat_detail_screen.dart'
text = read(path)
if "components/messaging_components.dart" not in text:
    import_marker = "import '../../ui/core/design_system/design_system.dart';"
    if import_marker in text:
        text = text.replace(
            import_marker,
            import_marker + "\nimport '../../ui/core/design_system/components/messaging_components.dart';",
            1,
        )

old_poll = """          if (!ChatAttachmentActions.isPollMessage(message)) return bubble;
          return GestureDetector(
            behavior: HitTestBehavior.translucent,
            onTap: () => _attachments.openPoll(context, message.id),
            child: bubble,
          );
"""
new_poll = """          if (!ChatAttachmentActions.isPollMessage(message)) return bubble;
          return Padding(
            padding: EdgeInsets.only(
              left: isMine ? 70 : 12,
              right: isMine ? 12 : 70,
              bottom: 4,
            ),
            child: Align(
              alignment: isMine ? Alignment.centerRight : Alignment.centerLeft,
              child: ChatyPollSummaryCard(
                question: ChatAttachmentActions.pollQuestion(message),
                isMine: isMine,
                onTap: () => _attachments.openPoll(context, message.id),
              ),
            ),
          );
"""
if old_poll in text:
    text = text.replace(old_poll, new_poll, 1)
elif 'ChatyPollSummaryCard(' not in text:
    raise SystemExit('P4-P7 chat poll render marker missing')

# Remove duplicate composer documentation created by earlier stacked UX passes.
duplicate = """/// Premium message composer + voice recorder.
///
/// Presentation only: every callback is owned by the screen and the capture
/// pipeline is untouched. While recording, the level meter renders REAL
/// microphone amplitude samples (dBFS from the `record` plugin, polled at
/// ~90ms) — never synthetic bars. With no amplitude provider the meter is
/// simply omitted rather than faked.
/// Premium message composer + voice recorder.
///
/// Presentation only: every callback is owned by the screen and the capture
/// pipeline is untouched. While recording, the level meter renders REAL
/// microphone amplitude samples (dBFS from the `record` plugin, polled at
/// ~90ms) — never synthetic bars. With no amplitude provider the meter is
/// simply omitted rather than faked.
"""
single = """/// Premium message composer + voice recorder.
///
/// Presentation only: every callback is owned by the screen and the capture
/// pipeline is untouched. While recording, the level meter renders REAL
/// microphone amplitude samples (dBFS from the `record` plugin, polled at
/// ~90ms) — never synthetic bars. With no amplitude provider the meter is
/// simply omitted rather than faked.
"""
text = text.replace(duplicate, single, 1)
write(path, text)


# Build-time invariants.
checks = {
    'lib/ui/core/design_system/components/messaging_components.dart': [
        'class ChatyPollSummaryCard',
        'Tap to vote or view results',
    ],
    'lib/features/messages/chat_attachment_actions.dart': [
        'static String pollQuestion',
        'Chaty attachment action failed:',
        'ChatyActivityIsland.show(',
    ],
    'lib/features/chats/chat_detail_screen.dart': [
        'ChatyPollSummaryCard(',
        'ChatAttachmentActions.pollQuestion(message)',
    ],
}
for file_path, needles in checks.items():
    value = read(file_path)
    for needle in needles:
        if needle not in value:
            raise SystemExit(f'P4-P7 invariant missing: {file_path}: {needle}')

print('Frontend P4-P7 integration applied.')
