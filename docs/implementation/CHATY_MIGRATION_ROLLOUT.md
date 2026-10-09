# Chaty Migration, Rollout and Recovery Plan

## 1. Scope
Migrate existing appearance preferences into a typed versioned profile while preserving all unrelated local state, message data, auth sessions, MLS keys and call configuration.

## 2. Migration strategy
1. Inventory all current appearance keys and their valid values from source.
2. Define a canonical schema/defaults and explicit mapping table.
3. Read legacy keys without deleting them.
4. Validate each legacy value; use a documented fallback for invalid/missing values.
5. Persist the complete new profile atomically.
6. Read back and verify the profile.
7. Only after successful verification, write a migration version marker.
8. Keep legacy fallback reads for one controlled compatibility window.
9. Remove legacy writes only after regression tests and release validation.
10. Never reset auth/MLS storage as part of an appearance migration.

## 3. Mapping table template
| Legacy key | Legacy values | New profile field | Conversion | Test fixture | Status |
|---|---|---|---|---|---|
| `appearance.navigation` | Existing `navigationStyles` | `shell.navigationStyle` | Validate enum; fallback default | Existing value/invalid/null | Planned |
| `appearance.bottomBar` | Existing `bottomBarStyles` | `shell.bottomBarStyle` | Validate enum; fallback default | Existing value/invalid/null | Planned |
| `appearance.appIcon` | Existing `appIconStyles` | `brand.appIconStyle` | Validate enum; fallback default | Existing value/invalid/null | Planned |
| `appearance.notificationIcon` | Existing list | `notifications.iconStyle` | Platform capability validation | Existing value/invalid/null | Planned |
| `appearance.typography` | Existing `typographyStyles` | `typography.preset` | Preserve selection and scale behavior | Existing value/invalid/null | Planned |
| `appearance.entryAnimation` | Existing list | `motion.entryStyle` | Validate enum; respect reduce motion | Existing value/invalid/null | Planned |
| `appearance.exitAnimation` | Existing list | `motion.exitStyle` | Validate enum; respect reduce motion | Existing value/invalid/null | Planned |

## 4. Cloud sync
Not part of the initial local template migration. If added:
- user-scoped table with RLS;
- no secrets or personal content in profile;
- explicit last-updated/version fields;
- documented conflict resolution;
- offline queue and idempotent writes;
- sign-out/account-switch isolation;
- delete/reset semantics documented.

## 5. Rollout
- Development: migrate fixtures and test current preferences.
- Internal QA: enable new settings route for test builds; compare old/new values.
- Beta: collect crash/performance diagnostics with sensitive content redacted.
- Release: require security preflight, analyze/tests/build and migration review.
- Post-release: monitor migration failures and crashes; provide safe fallback to last known valid profile.

## 6. Recovery
- Keep previous valid profile snapshot until new profile verifies.
- If decode/validation fails, use previous profile or defaults without touching message/auth/MLS data.
- Import apply must be atomic.
- Provide "Reset appearance" separate from "Clear cache" and "Delete account/data".
- Document rollback compatibility for any persisted schema change.
