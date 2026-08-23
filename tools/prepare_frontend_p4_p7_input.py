#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / 'lib/features/messages/chat_attachment_actions.dart'
text = path.read_text(encoding='utf-8')

start_marker = '  static void _toast(BuildContext context, String message) {'
class_boundary = '\n}\n\nclass _PollDraft'
start = text.find(start_marker)
if start < 0:
    raise SystemExit('P4-P7 input normalization: _toast start marker missing')

boundary = text.find(class_boundary, start)
if boundary < 0:
    raise SystemExit('P4-P7 input normalization: attachment class boundary missing')

# Find the final method-closing brace immediately before the class boundary.
method_end = text.rfind('\n  }', start, boundary)
if method_end < 0:
    raise SystemExit('P4-P7 input normalization: _toast end marker missing')
method_end += len('\n  }')

canonical_input = '''  static void _toast(BuildContext context, String message) {
    if (!context.mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }'''

text = text[:start] + canonical_input + text[method_end:]
path.write_text(text, encoding='utf-8')
print('Frontend P4-P7 input normalized.')
