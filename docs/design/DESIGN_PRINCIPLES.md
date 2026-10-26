# Design Principles

Initial source review: 27 Oct 2026, 01:43 IST. Build launch update: 01:49; runtime compile failure update: 01:54. Source review and unit results are not emulator acceptance.

1. Truthful connected/saved/applied state before polish.
2. Preserve existing components/business logic; progressive disclosure, not feature deletion.
3. Apple-inspired clarity with Android platform behavior.
4.48dp touch regions; measure small text4.5:1 and larger text3:1 per Android guidance; large text/TalkBack/reduced motion.
5. Shared tokens/IDs/semantics; preview and production share models.
6. Fitts/Hick/chunking are heuristics, not correctness proof.
7. Preserve drafts, explicit Apply/Cancel, no accidental send.
8. Keep animations opt-in as confirmed and honor reduced motion.
9. Security boundaries never bypassed for demos/screenshots.
10. Separate source, unit, instrumentation, pixels, real device/network evidence.
