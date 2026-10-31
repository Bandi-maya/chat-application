#!/usr/bin/env python3
"""Disjoint source-discovered Android class partitions; no test is silently dropped."""
import pathlib, re, sys
shards = {"core-ui": [], "media": [], "media-emoji-perf": [], "emoji-real": [], "emoji-loop": [], "maps": [], "calls-camera": []}
for path in sorted(p for p in pathlib.Path("app/src/androidTest").rglob("*") if p.suffix in (".kt", ".java")):
    text = path.read_text()
    if not re.search(r"@Test\b", text):
        continue
    package = re.search(r"^package\s+([\w.]+)", text, re.M)
    klass = re.search(r"^\s*(?:(?:public|internal|open|abstract)\s+)*class\s+(\w+)", text, re.M)
    if not package or not klass:
        raise SystemExit(f"Cannot identify test class: {path}")
    name = package[1] + "." + klass[1]
    if any(key in name.lower() for key in ("camera", "call", "inlinedocument", "island")):
        shard = "calls-camera"
    elif name.rsplit(".", 1)[-1] == "RealEmojiPackProofTest":
        shard = "emoji-real"
    elif name.rsplit(".", 1)[-1] == "SingleEmojiLoopProofTest":
        # Own shard = own emulator session: an emulator loss during the real-Noto class cannot hide this class.
        shard = "emoji-loop"
    elif any(key in name.lower() for key in ("location", "geolibre")):
        shard = "maps"
    elif name.rsplit(".", 1)[-1] == "EmojiPackAssetsAndPerfTest":
        # Isolated emulator session: a mid-class emulator loss in the grid perf/memory
        # class must not suppress the remaining media shard's evidence.
        shard = "media-emoji-perf"
    elif any(key in name.lower() for key in ("media", "image", "attachment", "emoji", "bubble")):
        shard = "media"
    else:
        shard = "core-ui"
    shards[shard].append(name)
flat = [name for names in shards.values() for name in names]
assert len(flat) == len(set(flat)), "Duplicate instrumentation class"
assert flat and all(shards.values()), "An instrumentation shard is empty"
choice = sys.argv[1] if len(sys.argv) > 1 else "all"
if choice == "all":
    print(",".join(flat))
elif choice in shards:
    print(",".join(shards[choice]))
else:
    raise SystemExit(f"Unknown shard {choice}")
