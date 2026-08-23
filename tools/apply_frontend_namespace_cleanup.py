#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding='utf-8')


# Keep the mature production settings primitive named ChatySettingsSection.
# The new preview/design-system section gets a distinct name so screens that
# import both libraries remain source-compatible.
path = 'lib/ui/core/design_system/components/settings_components.dart'
text = read(path)
text = text.replace('class ChatySettingsSection extends StatelessWidget {',
                    'class ChatySettingsGroup extends StatelessWidget {')
text = text.replace('const ChatySettingsSection({', 'const ChatySettingsGroup({')
write(path, text)

path = 'lib/features/ui_lab/ui_lab_screen.dart'
text = read(path)
text = text.replace('return ChatySettingsSection(', 'return ChatySettingsGroup(')
write(path, text)

# app_tokens.dart already owns the public ChatyMotion namespace used throughout
# the mature UI. The new canonical reduced-motion helper is intentionally named
# ChatyMotionSpec instead of shadowing that public token class.
path = 'lib/ui/core/design_system/chaty_motion.dart'
text = read(path)
text = text.replace('abstract final class ChatyMotion {',
                    'abstract final class ChatyMotionSpec {')
write(path, text)

for path in [
    'lib/ui/core/design_system/components/messaging_components.dart',
    'lib/ui/core/design_system/components/social_components.dart',
    'lib/features/ui_lab/ui_lab_screen.dart',
]:
    text = read(path)
    if "chaty_motion.dart" in text:
        text = text.replace('ChatyMotion.', 'ChatyMotionSpec.')
    write(path, text)

# Clean the imports added by the master pass when the production home screen
# already has the same components through its design-system barrel.
home_path = 'lib/features/chats/chats_home_screen.dart'
home = read(home_path)
lines = home.splitlines()
seen = set()
clean = []
for line in lines:
    stripped = line.strip()
    if stripped.startswith('import '):
        if stripped in seen:
            continue
        seen.add(stripped)
    clean.append(line)
home = '\n'.join(clean) + ('\n' if home.endswith('\n') else '')
for unused in [
    "import '../../ui/core/design_system/components/messaging_components.dart';\n",
    "import '../../ui/core/design_system/component_state.dart';\n",
]:
    home = home.replace(unused, '')
write(home_path, home)

# Invariants: one production Settings section public name and no duplicate
# ChatyMotion export namespace.
settings_component = read(
    'lib/ui/core/design_system/components/settings_components.dart'
)
if 'class ChatySettingsSection ' in settings_component:
    raise SystemExit('New settings component still shadows ChatySettingsSection')
if 'class ChatySettingsGroup ' not in settings_component:
    raise SystemExit('ChatySettingsGroup rename was not applied')

motion = read('lib/ui/core/design_system/chaty_motion.dart')
if 'class ChatyMotion {' in motion or 'class ChatyMotion extends' in motion:
    raise SystemExit('chaty_motion.dart still shadows app-token ChatyMotion')
if 'class ChatyMotionSpec ' not in motion:
    raise SystemExit('ChatyMotionSpec rename was not applied')

lab = read('lib/features/ui_lab/ui_lab_screen.dart')
if 'ChatySettingsGroup(' not in lab:
    raise SystemExit('UI Lab does not use the renamed settings group')

print('Frontend namespace cleanup applied.')
