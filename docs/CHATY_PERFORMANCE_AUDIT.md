# Chaty performance audit

Coding-stage report, 2026-11-04. No current baseline trace or before/after runtime measurement has been supplied to this source writer. No app-wide speed, jank, refresh-rate or memory improvement is claimed.

## Measurement gate

Record build SHA, effective overlay composition, device/API, refresh rate, thermal/battery state, dataset size, network and session fixture. Capture cold/warm startup, home/templates, 1k+ timeline scroll, emoji picker, attachments, dialpad/search, answer/decline, active control transitions, background/foreground and PiP. Keep raw traces and frame percentiles, not only averages. Compare same-device/same-fixture baseline and candidate. Physical audio/media/network tests are separate from emulator UI proof.

## Source-proven changes, not measured optimizations

- Preference initialization serialized before new writes to prevent late restore overwriting a selection.
- Samsung incoming drag offsets synchronously accumulated/reset to avoid residual gesture progress.
- Compact call action layout avoids a 144dp row in a 120dp card.
- Local preview drag bounds remain valid in small viewports.
- Call panels consume live system reduced-motion setting.

Each requires final combined build/regression/device verification. These fix concrete correctness/layout risks, not an established cross-app performance root cause. Filtering and animation searches remain investigative leads.

## Open work

Macrobenchmark/Perfetto evidence, startup/scroll/frame/memory results, heap/resource lifetime checks, before/after comparisons and measurable regression budgets. Do not mark phase complete until these exist.
