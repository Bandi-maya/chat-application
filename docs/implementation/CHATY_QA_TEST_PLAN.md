# Chaty QA and Test Plan

## 1. Test layers
| Layer | Scope | Evidence |
|---|---|---|
| Unit | Profile model, validator, migrations, reducers, state machines | Automated test output |
| Widget | Settings controls, templates, shared components, accessibility semantics | Widget test results |
| Golden | Theme/profile variants across shared widgets and screens | Reviewed golden diffs |
| Integration | Settings persistence, message actions, status/call flows, account lifecycle | Integration test report |
| Backend | RPC auth, RLS, migrations, idempotency, MLS metadata invariants | SQL/security test output |
| Device | Android/iOS permissions, audio, camera, safe areas, notifications, lifecycle | Device/OS/build evidence |
| Performance | Long chat lists, media rendering, theme change, animations | Profile traces and comparison |
| Release | CI security preflight, analyze, tests, Android/iOS build | Required workflow checks |

## 2. Template matrix
Test every supported built-in template plus one custom profile with:
- light, dark and system mode;
- short/long message, incoming/outgoing, media, quote, reaction, deleted message and receipt state;
- compact and comfortable density;
- default and large system text;
- reduced motion enabled;
- narrow/large screen, keyboard visible, notch/status bar and bottom gesture inset;
- settings change while on each major tab;
- app restart and account switch.

## 3. Required regression scenarios
| ID | Scenario | Expected result |
|---|---|---|
| QA-001 | Migrate existing appearance keys | Existing selected styles preserved |
| QA-002 | Apply a template | All consumers update consistently |
| QA-003 | Cancel preview | Applied profile unchanged |
| QA-004 | Persistence write fails | Old profile remains active and error is visible |
| QA-005 | Reset one category | Other categories unchanged |
| QA-006 | Import malformed/oversized/unknown schema | Rejected safely, current profile unchanged |
| QA-007 | Export profile | Contains no secrets/account data |
| QA-008 | Edit MLS message | Ciphertext stored; authorized recipients decrypt new text |
| QA-009 | MLS edit RPC failure | No plaintext fallback; local optimistic state rolls back |
| QA-010 | Status publish/view | Server state updates and permissions are enforced |
| QA-011 | Call ends in multiple racing callbacks | One terminal call record and complete resource cleanup |
| QA-012 | Revoke linked device | Server reports revoked state; client updates only after confirmation |
| QA-013 | Sign out during network request | Stale result does not render in next session |
| QA-014 | Deny mic/camera permission | Clear explanation and retry path; no crash |
| QA-015 | Record while app backgrounds/interruption occurs | Recording state is handled and resources cleaned up |
| QA-016 | Open modal with keyboard/small screen | Internal scroll works; no horizontal overflow |
| QA-017 | Large text and screen reader | Content remains readable and controls announced |
| QA-018 | Offline send/reconnect | Draft retained, retry idempotent, no duplicates |
| QA-019 | Large message history | Scroll remains responsive; pagination correct |
| QA-020 | Android release workflow | Auth security preflight and build pass |
| QA-021 | iOS build/test | Required checks pass on macOS runner |
| QA-022 | Color contrast | Meets thresholds or documented justified exception |
| QA-023 | Motion preference | Reduce-motion honored and no essential state depends on animation |
| QA-024 | Navigation style changes | Every tab and deep link remains reachable |
| QA-025 | Theme changes during active call | Call controls remain visible and functional |

## 4. Automated commands
Confirm actual Flutter SDK/project scripts before using; typical baseline:
```bash
dart format --output=none --set-exit-if-changed lib test
flutter analyze
flutter test
flutter build apk --debug
```
Run migration/security scripts documented by the repository. iOS build/test requires macOS/Xcode. Do not invent successful outputs.

## 5. Release gate
- No unresolved P0 bugs.
- P1 bugs fixed or explicitly accepted by release owner with documented workaround/risk.
- Required CI security checks green.
- Migration/RLS review complete.
- Android build and core device tests pass.
- iOS build/device tests pass where supported; otherwise clearly state the verification limitation.
- QA report includes commit, environment, commands, pass/fail/blocked counts, artifacts, known limitations and sign-off.

## 6. Report template
For each run, record: commit SHA, branch, date, OS/device, Flutter/Dart versions, test command, pass/fail/skipped counts, links to CI, bug IDs, screenshots/logs with sensitive data redacted, and release recommendation.
