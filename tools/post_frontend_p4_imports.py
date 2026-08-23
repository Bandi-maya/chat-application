#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / 'lib/features/chats/chat_detail_screen.dart'
text = path.read_text(encoding='utf-8')

required = "import '../../ui/core/design_system/components/messaging_components.dart';"
if required not in text:
    lines = text.splitlines()
    insert_at = 0
    for i, line in enumerate(lines):
        if line.startswith('import '):
            insert_at = i + 1
        elif insert_at and line.strip() == '':
            break
    lines.insert(insert_at, required)
    text = '\n'.join(lines) + ('\n' if not text.endswith('\n') else '')

path.write_text(text, encoding='utf-8')

if required not in path.read_text(encoding='utf-8'):
    raise SystemExit('P4 import invariant missing after repair')
print('Frontend P4 import invariant applied.')
