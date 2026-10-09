#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path('lib')
catalog = root / 'ui/core/gb/gb_feature_catalog.dart'
text = catalog.read_text(encoding='utf-8')

# Keys declared in the visible advanced-feature catalog. These must appear in
# at least one production file outside the catalog and the generic feature
# editor, otherwise the setting is a write-only control with no runtime effect.
category_block = re.search(
    r"static const Map<String, String> _categoryKeys = <String, String>\{(.*?)\n  \};",
    text,
    flags=re.S,
)
if not category_block:
    raise SystemExit('Could not parse GbFeatureCatalog._categoryKeys')

keys = set()
for quoted in re.findall(r"'([^']+)'", category_block.group(1)):
    if ',' in quoted:
        keys.update(part.strip() for part in quoted.split(',') if part.strip())

ignored = {
    catalog,
    root / 'features/settings/gb/gb_feature_center_screen.dart',
}
production_sources = [
    path
    for path in root.rglob('*.dart')
    if path not in ignored
]

missing = []
for key in sorted(keys):
    token = re.compile(r"(?<![A-Za-z0-9_])" + re.escape(key) + r"(?![A-Za-z0-9_])")
    if not any(token.search(path.read_text(encoding='utf-8', errors='ignore')) for path in production_sources):
        missing.append(key)

if missing:
    print('Advanced settings with no production runtime consumer:', file=sys.stderr)
    for key in missing:
        print(f'  - {key}', file=sys.stderr)
    sys.exit(1)

print(f'Runtime consumer audit passed for {len(keys)} advanced settings.')
