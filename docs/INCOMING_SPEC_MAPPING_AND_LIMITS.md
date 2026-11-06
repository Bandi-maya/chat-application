# Incoming UI specification, bounded implementation

Source baseline: effective0de33ce snapshot after stages5-11. Publisher reports A+B+F-map and4JVM fixes later; none expected in incoming file. Before-hash is required: merge any mismatch, never overwrite newer code.

Mapping from supplied call-stage5-sheet.jpg, row-major5+4:
- Tile01: needs mapping. Visually identical to02; do not guess stable ID after stage10name swap.
- Tile02: needs mapping, same reason. Both Chaty Default(apple_ios) and Apple iOS Call Experience(chaty_default) layouts/interactions retained. Locked slider unchanged.
- Tile03: samsung_one_ui, source-specific dual halos and Tap Answer or Decline text.
- Tile04: google_phone_latest, horizontal Decline/puck/Answer. Existing horizontal gesture retained, not changed to tap-only based on unsupported spec assumption about center action.
- Tile05: google_phone_classic, classic header and swipe-up accept.
- Tile06: vivo_iqoo, badge/camera/name/dual actions. Neutral INCOMING CALL/VIDEO retained; no ORIGIN OS CALL or HD guarantee.
- Tile07: nextcloud_talk, header INCOMING VIDEO plus lower swipe+actions. Existing tap-or-up accept retained.
- Tile08: azure_ring_vertical, ring+dotted vertical destination track; up decline/down answer.
- Tile09: mono_chevron_dark, red upper/green lower chevrons; up decline/down answer.

Changes: shared single safeDrawing owner, centered480dp maximum surface, mapped layout compact margins,24sp caller/2line ellipsis, baseline camera/ring tile sizes, less bottom decoration space, explicit48dpaction minimum,96dpupward header gestures,112dptwo-way gestures and diagonal rejection, corrected Google classic TalkBack up-answer/down-no-action. No service, signaling, call-state, permission, backend, notification, registry IDs/names or audio primary control changes.

Not exact: spec admits raster dimensions are a chosen393x852baseline, not recovered original dp. Company reference links are labels, not actual URLs, so no external OEM/version claim added. Apple font/art/system lock/status/Telecom behaviors not reproduced. Existing icon family reused, no proprietary assets. New full entrance/press/chevron animation choreography,velocity1000dp/s completion, spring-return timing and all listed baseline coordinates are NOT implemented by this conservative pass. Existing animations remain and reduced-motion behavior preserved. Scene not pixel verified; no144Hz or WCAG contrast certification. Caller may ellipsize after2lines rather than expand unbounded. Full 640dp/2x layout and gesture collision coverage remains publisher gate.

Verification:2new source contract tests PASS, preserving nine stable routes/Apple slider, token checks/bounds/threshold/a11y guards. Source checks are not Android compile/gestures/pixels. Existing ongoing/audio Hang up source untouched byte-for-byte and current matrix tests must still pass at220dp/large font. Current direct actual Android render is unavailable in this writer, so visual readiness unverified, not complete.
