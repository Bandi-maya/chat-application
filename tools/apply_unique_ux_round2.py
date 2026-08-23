#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

path = ROOT / 'lib/features/chats/chat_detail_screen.dart'
text = path.read_text(encoding='utf-8')

start_marker = '  Widget _buildInputRow(ThemeConfig theme) {'
end_marker = '\n}\n\n/// Real-amplitude level meter'
start = text.find(start_marker)
end = text.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit('composer input function markers not found')

replacement = r'''  Widget _buildInputRow(ThemeConfig theme) {
    Widget shortcut(String label, String value, IconData icon) {
      return ActionChip(
        avatar: Icon(icon, size: 15, color: theme.accentColor),
        label: Text(label),
        visualDensity: VisualDensity.compact,
        side: BorderSide(color: theme.accentColor.withValues(alpha: .18)),
        backgroundColor: theme.accentColor.withValues(alpha: .07),
        labelStyle: TextStyle(
          color: theme.primaryTextColor,
          fontSize: 11.5,
          fontWeight: FontWeight.w700,
        ),
        onPressed: () {
          HapticFeedback.selectionClick();
          widget.controller
            ..text = '$value '
            ..selection = TextSelection.collapsed(offset: value.length + 1);
          widget.onChanged(widget.controller.text);
        },
      );
    }

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        ValueListenableBuilder<TextEditingValue>(
          valueListenable: widget.controller,
          builder: (context, value, _) {
            if (value.text.trim().isNotEmpty) return const SizedBox.shrink();
            return Padding(
              padding: const EdgeInsets.fromLTRB(46, 0, 50, 6),
              child: SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(
                  children: [
                    shortcut('Task', '/task', Icons.task_alt_rounded),
                    const SizedBox(width: 6),
                    shortcut('Poll', '/poll', Icons.poll_rounded),
                    const SizedBox(width: 6),
                    shortcut('Location', '/location', Icons.location_on_rounded),
                  ],
                ),
              ),
            );
          },
        ),
        Row(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            _ComposerCircleButton(
              theme: theme,
              tooltip: 'Attach',
              icon: Icons.add_circle_outline_rounded,
              iconColor: theme.accentColor,
              onTap: widget.onAttach,
            ),
            Expanded(
              child: TextField(
                controller: widget.controller,
                minLines: 1,
                maxLines: 5,
                onChanged: (value) {
                  widget.onChanged(value);
                  if (value.trim() == '/') {
                    Future<void>.microtask(() async {
                      final command = await ChatyCommandPalette.show(context);
                      if (!mounted || command == null) return;
                      widget.controller
                        ..text = '$command '
                        ..selection = TextSelection.collapsed(
                          offset: command.length + 1,
                        );
                      widget.onChanged(widget.controller.text);
                    });
                  }
                },
                style: TextStyle(
                  color: theme.primaryTextColor,
                  fontSize: 14 * theme.fontScale,
                ),
                decoration: InputDecoration(
                  hintText: 'Message…  / for commands',
                  hintStyle: TextStyle(color: theme.secondaryTextColor),
                  filled: true,
                  fillColor: theme.cardColor,
                  prefixIcon: IconButton(
                    tooltip: 'Emoji',
                    onPressed: widget.onEmoji,
                    icon: Icon(
                      Icons.emoji_emotions_outlined,
                      color: theme.secondaryTextColor,
                    ),
                  ),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(22),
                    borderSide: BorderSide.none,
                  ),
                  isDense: true,
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 11,
                  ),
                ),
              ),
            ),
            const SizedBox(width: 6),
            ValueListenableBuilder<TextEditingValue>(
              valueListenable: widget.controller,
              builder: (context, value, _) {
                final hasText = value.text.trim().isNotEmpty;
                if (hasText) {
                  return _ComposerCircleButton(
                    theme: theme,
                    tooltip: 'Send',
                    icon: Icons.send_rounded,
                    fillColor: theme.accentColor,
                    iconColor: theme.onAccentColor,
                    onTap: () {
                      HapticFeedback.lightImpact();
                      widget.onSend();
                    },
                  );
                }
                return _ComposerCircleButton(
                  theme: theme,
                  size: 46,
                  icon: Icons.mic_rounded,
                  fillColor: theme.accentColor,
                  iconColor: theme.onAccentColor,
                  semanticsLabel:
                      'Voice note. Tap to start locked recording, or hold to record and slide.',
                  onTap: widget.onVoiceTap,
                  onLongPressStart: (_) => widget.onVoiceHoldStart(),
                  onLongPressMoveUpdate: widget.onVoiceMove,
                  onLongPressEnd: (_) => widget.onVoiceHoldEnd(),
                );
              },
            ),
          ],
        ),
      ],
    );
  }
'''

text = text[:start] + replacement + text[end:]
path.write_text(text, encoding='utf-8')
print('Chaty unique UX round 2 applied.')
