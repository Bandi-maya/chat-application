#!/usr/bin/env python3
"""Generate a source-derived screen/control inventory for Chaty.

This is a discovery aid, not a runtime pass/fail test. Runtime behavior still
requires Flutter tests, attached-Chrome Playwright, and native device checks.
"""
from __future__ import annotations

import argparse
import re
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FEATURES = ROOT / "lib" / "features"
DART_ROOTS = (ROOT / "lib",)
SCREEN_FILE = re.compile(r"(screen|page|sheet|dialog|modal|panel|overlay|view)", re.I)
WIDGET_CLASS = re.compile(
    r"\bclass\s+([A-Z]\w*)\s+extends\s+"
    r"(?:StatefulWidget|StatelessWidget|ConsumerWidget|HookWidget|ConsumerStatefulWidget)\b"
)
INTERACTION_PATTERNS = {
    "onPressed": re.compile(r"\bonPressed\s*:"),
    "onTap": re.compile(r"\bonTap\s*:"),
    "onLongPress": re.compile(r"\bonLongPress\s*:"),
    "onChanged": re.compile(r"\bonChanged\s*:"),
    "PopupMenuItem": re.compile(r"\bPopupMenuItem\s*<"),
    "MenuItemButton": re.compile(r"\bMenuItemButton\s*\("),
    "showDialog": re.compile(r"\bshowDialog\s*<"),
    "showModalBottomSheet": re.compile(r"\bshowModalBottomSheet\s*<"),
    "Navigator.push": re.compile(r"\bNavigator(?:\.of\([^)]*\))?\.push(?:Replacement)?\s*\("),
}
ROUTE_CONSTRUCTOR = re.compile(
    r"\b(?:MaterialPageRoute|CupertinoPageRoute|PageRouteBuilder)\s*<[^>]*>?"
    r"\s*\([^;]{0,1200}?\bbuilder\s*:\s*\([^)]*\)\s*=>\s*([A-Z]\w*)\s*\(",
    re.S,
)


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="artifacts/source-screen-inventory.md")
    args = parser.parse_args()

    dart_files = sorted(path for root in DART_ROOTS for path in root.rglob("*.dart"))
    if not dart_files:
        raise SystemExit("No Dart source files found; run this from the Chaty repository.")

    candidates: list[dict[str, object]] = []
    routes: Counter[str] = Counter()
    interaction_totals: Counter[str] = Counter()
    references: dict[str, set[str]] = {}

    for path in dart_files:
        source = path.read_text(encoding="utf-8", errors="replace")
        classes = WIDGET_CLASS.findall(source)
        is_feature_file = FEATURES in path.parents
        file_candidate = bool(SCREEN_FILE.search(path.stem))
        if file_candidate or (is_feature_file and classes):
            candidates.append({
                "path": rel(path),
                "classes": classes,
                "lines": source.count("\n") + 1,
                "controls": {name: len(pattern.findall(source)) for name, pattern in INTERACTION_PATTERNS.items()},
            })
        for name, pattern in INTERACTION_PATTERNS.items():
            interaction_totals[name] += len(pattern.findall(source))
        for target in ROUTE_CONSTRUCTOR.findall(source):
            routes[target] += 1
            references.setdefault(target, set()).add(rel(path))

    output = [
        "# Chaty source-derived screen and interaction inventory",
        "",
        f"- Dart files scanned: **{len(dart_files)}**",
        f"- Candidate screen/component files: **{len(candidates)}**",
        f"- Distinct widget classes in candidate files: **{len({name for item in candidates for name in item['classes']})}**",
        f"- Route-constructor references found: **{sum(routes.values())}**",
        "",
        "> Static discovery only. A candidate may be a reusable component rather than a standalone screen. This report does not prove that a screen renders, a control works, or a route is reachable.",
        "",
        "## Interaction source counts",
        "",
        "| Pattern | Occurrences |",
        "|---|---:|",
    ]
    output.extend(f"| {name} | {count} |" for name, count in sorted(interaction_totals.items()))
    output += ["", "## Candidate screens and component files", ""]
    for item in candidates:
        classes = ", ".join(item["classes"]) or "(no directly declared widget class detected)"
        output += [
            f"### {item['path']}",
            "",
            f"- Lines: {item['lines']}",
            f"- Widget classes: {classes}",
            "- Interaction patterns: " + ", ".join(
                f"{name}={count}" for name, count in item["controls"].items() if count
            ) if any(item["controls"].values()) else "- Interaction patterns: none detected by the source heuristic",
            "",
        ]
    output += ["## Route constructor references", ""]
    if routes:
        for target, count in sorted(routes.items()):
            locations = ", ".join(sorted(references.get(target, set())))
            output.append(f"- {target} — {count} reference(s); referenced from: {locations}")
    else:
        output.append("No route constructors were detected by the current heuristic; inspect routing and navigation helpers manually.")

    destination = (ROOT / args.output).resolve()
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text("\n".join(output) + "\n", encoding="utf-8")
    print(f"Scanned {len(dart_files)} Dart files; wrote {destination}")
    print(f"Candidate files: {len(candidates)}; route references: {sum(routes.values())}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
