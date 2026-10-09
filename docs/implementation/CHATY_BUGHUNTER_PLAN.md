# Chaty Bug Hunter Plan

## Mission
Systematically find and fix regressions across current Chaty flows and the new template system. This is a repeatable audit procedure, not a claim that bugs listed here are confirmed.

## Known issues to verify from current source
| ID | Candidate issue | Verification | Required outcome |
|---|---|---|---|
| BH-001 | Message edit uses legacy `edit_chat_message` plus direct update fallback while MLS edit RPC exists | Trace all edit callers, payload schema, migration RPC and recipient decrypt flow | MLS rows use encrypted edit RPC only; failure restores old local content |
| BH-002 | `addStory` facade throws UnsupportedError | Trace callers and existing status service | UI either uses implemented status service or clearly disables unsupported action |
| BH-003 | `markStoryViewed` facade is empty | Trace status viewer and server contract | View state persisted once via authoritative service |
| BH-004 | `logCall` facade is empty | Trace call termination and call history service | Exactly one server-authoritative terminal call record |
| BH-005 | Linked-device revocation may be local-only in facade | Trace all callers, RPC, device table and MLS enrollment | Server confirms revocation before UI reports success |
| BH-006 | Appearance overview has hardcoded labels such as Type/In/Out | Inspect current file and intended selected values | Display selected values or remove misleading chips |
| BH-007 | Android security preflight previously failed | Inspect exact workflow log and hosted Auth configuration | Fix actual secret/config cause; no bypass |
| BH-008 | Generated Gradle report may be tracked | Inspect current Git tree and ignore rules | Remove generated artifacts if safe and update ignore rules |
| BH-009 | Very large chat screens are hard to test | Identify safe extraction seams with tests | Incremental component extraction without output/functionality regression |

## Bug hunting dimensions
- Navigation: back stack, duplicate routes, deep links, dialogs, tab changes.
- Layout: safe area, keyboard inset, notch/status bar, bottom gesture area, internal modal scroll, horizontal overflow.
- State: rapid taps, stale async results, optimistic rollback, duplicate submissions, account switch.
- Persistence: restart, app update, malformed preferences, migration failure, reset isolation.
- Networking: offline startup, timeout, retry, duplicate RPC, stale Realtime event, reconnect.
- Security: RLS, user/tenant ownership, MLS plaintext leakage, stale device enrollment, sensitive logging.
- Media/audio: permission denial, picker cancellation, upload failure, interrupted recording, audio focus.
- Calls: ringing timeout, accept/decline race, busy, ICE restart, network switch, background/foreground, double hang-up.
- Settings: no-op controls, controls with wrong consumers, invalid values, import/export, preview cancellation.
- Accessibility: large text, TalkBack/VoiceOver, focus order, contrast, reduced motion.
- Performance: long histories, image-heavy rows, rapid theme changes, animations, memory cleanup.

## Required bug record
Each issue must include ID, severity, reproducible steps, expected/actual result, device/OS, build/commit, evidence, root cause, fix, regression test, and verification status.

## Severity
- P0: data loss, auth bypass, E2EE violation, crash on core path, release/security gate bypass.
- P1: core flow broken, calls/recording unusable, settings corrupt user state, major layout unusable.
- P2: feature degradation with workaround, noncritical UI inconsistency.
- P3: cosmetic polish or low-impact edge case.

## Closure rule
Never mark fixed based only on a code change. Reproduce, add a regression test, run it, inspect the diff and validate on affected platform. If hardware/CI access is unavailable, mark pending verification.
