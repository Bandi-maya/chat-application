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
    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        _ComposerCircleButton(
          theme: theme,
          tooltip: 'Add to conversation',
          icon: Icons.add_rounded,
          iconColor: theme.accentColor,
          onTap: () {
            HapticFeedback.selectionClick();
            widget.onAttach();
          },
        ),
        Expanded(
          child: ChatyComposerShell(
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
                fontSize: 15.5 * theme.fontScale,
                height: 1.25,
              ),
              decoration: InputDecoration(
                hintText: 'Message',
                hintStyle: TextStyle(color: theme.secondaryTextColor),
                filled: false,
                prefixIcon: IconButton(
                  tooltip: 'Emoji',
                  onPressed: widget.onEmoji,
                  icon: Icon(
                    Icons.emoji_emotions_outlined,
                    color: theme.secondaryTextColor,
                  ),
                ),
                suffixIcon: IconButton(
                  tooltip: 'Commands',
                  onPressed: () async {
                    final command = await ChatyCommandPalette.show(context);
                    if (!mounted || command == null) return;
                    widget.controller
                      ..text = '$command '
                      ..selection = TextSelection.collapsed(
                        offset: command.length + 1,
                      );
                    widget.onChanged(widget.controller.text);
                  },
                  icon: Icon(
                    Icons.bolt_rounded,
                    color: theme.secondaryTextColor,
                  ),
                ),
                border: InputBorder.none,
                isDense: true,
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 4,
                  vertical: 11,
                ),
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
                icon: Icons.arrow_upward_rounded,
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
              icon: Icons.mic_none_rounded,
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
    );
  }
'''

text = text[:start] + replacement + text[end:]
path.write_text(text, encoding='utf-8')
print('Chaty unique UX round 2 applied.')
