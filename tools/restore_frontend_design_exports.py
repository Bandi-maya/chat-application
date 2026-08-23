#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / 'lib/ui/core/design_system/design_system.dart'
required = [
    "export 'tokens/app_tokens.dart';",
    "export 'components/app_components.dart';",
    "export 'components/chaty_kit.dart';",
    "export 'components/signature_components.dart';",
    "export 'components/call_activity_capsule.dart';",
    "export 'components/messaging_components.dart';",
    "export 'components/social_components.dart';",
    "export 'components/settings_components.dart';",
    "export 'chaty_motion.dart';",
    "export 'chaty_haptics.dart';",
    "export 'chaty_adaptive.dart';",
    "export 'component_state.dart';",
    "export '../theme/app_theme.dart';",
]
current = path.read_text(encoding='utf-8') if path.exists() else ''
lines = [line.strip() for line in current.splitlines() if line.strip()]
for export in required:
    if export not in lines:
        lines.append(export)
path.write_text('\n'.join(lines) + '\n', encoding='utf-8')
print('Canonical frontend design-system exports restored.')
