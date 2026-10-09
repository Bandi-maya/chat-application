# Chaty Customization Architecture

## 1. Existing system constraints
Chaty is a Flutter application backed by Supabase, with MLS E2EE services and WebRTC call functionality. Existing `AppearanceVariantController` stores style preferences using SharedPreferences. Current code already has navigation style, bottom-bar style, app icon, notification icon, typography and entry/exit animation options. Reuse and migrate these values rather than creating parallel preference stores.

## 2. Target architecture
```text
Settings UI ──> AppearanceSettingsController ──> ProfileValidator
                         │                            │
                         ▼                            ▼
                 Preview State (memory)        Typed Profile
                         │                            │
                         ▼                            ▼
                Shared Component Tokens <── Theme/Template Resolver
                         │
       ┌─────────────────┼──────────────────┐
       ▼                 ▼                  ▼
   App Shell          Chat UI            Status/Calls/Profile
       │                 │                  │
       └─────────────────┼──────────────────┘
                         ▼
              Widget/Golden/Integration Tests

Profile persistence:
  local versioned preferences (primary)
  optional authenticated cloud profile sync (separate, conflict-aware)
  template export/import (appearance only; never secrets)
```

## 3. Proposed modules (adapt paths to actual repository conventions)
- `lib/ui/core/appearance/chaty_appearance_profile.dart`: immutable typed profile, schema version, equality/copy helpers.
- `lib/ui/core/appearance/chaty_appearance_defaults.dart`: canonical defaults and preset definitions.
- `lib/ui/core/appearance/chaty_appearance_validator.dart`: enum/color/range/contrast/schema validation.
- `lib/ui/core/appearance/chaty_theme_resolver.dart`: maps profile tokens to Flutter ThemeData and component styles.
- `lib/ui/core/controllers/appearance_settings_controller.dart`: load, edit preview, apply, reset, import/export and state.
- `lib/data/repositories/appearance_profile_repository.dart`: versioned local persistence and optional sync boundary.
- `lib/features/settings/appearance/`: category pages, template gallery, preview and reset UI.
- Shared widgets should be located according to existing project structure after a codebase survey; do not create duplicate component libraries without checking current widgets.

These paths are proposals, not authorization to create duplicates blindly.

## 4. Profile schema
Schema v1 should contain:
- metadata: schemaVersion, templateId, displayName, updatedAt (not identity tokens).
- appearance: themeMode, accent, semantic colors, surfaces, wallpaper reference, typography family/scale, density, radii/elevation.
- shell: navigation style, bottom bar style, tab labels/order/visibility only for supported routes, header style, motion profile.
- chat list: row style/density, avatar shape/size, unread badges, presence presentation.
- conversation: incoming/outgoing bubble style/colors, spacing, timestamps, date separators, receipt icon style, quote/reaction presentation.
- composer: layout, shape, background, icon colors, attachment tray style, voice affordance.
- media: gallery layout, preview chrome, document row, audio waveform/progress.
- status/calls/profile/dialogs: supported visual tokens and variants.
- accessibility: followSystemTextScale, highContrast, reduceMotion, minimum target policy.
Never include account IDs, messages, encryption keys, auth tokens, backend URLs, device secrets or user media.

## 5. State and persistence rules
- Immutable profile snapshots; use typed enums instead of arbitrary UI labels where practical.
- Defaults are code-defined and schema-versioned.
- Load legacy keys once, validate, persist migrated profile, then retain legacy reads only for a controlled compatibility window.
- Profile apply is atomic: validate complete candidate, persist successfully, then publish active state; if persistence fails, retain previous state and show error.
- Preview state is separate from applied state and must be discardable.
- Category reset patches only that category.
- Full reset confirms and writes a complete default profile.
- Cloud sync is a separate phase: user-scoped RLS, explicit conflict policy, offline queue and last-updated metadata; never let one account's settings leak into another account.
- Export/import must have strict size limits and schema validation.

## 6. Theme resolution
Resolve design tokens once from the active profile, then pass them into shared components. Do not use hard-coded per-screen colors for values controlled by settings. Do not globally override semantic states like failed, warning, incoming/outgoing or read receipts in a way that destroys meaning or contrast.

## 7. Domain boundaries
Appearance settings may change presentation, never domain truth:
- A tick style may change icon shape, not fabricate sent/delivered/read state.
- Hiding a media preview may not delete the message or change server storage.
- A call theme may not alter call signaling.
- A privacy preference must have an actual supported backend/platform contract.
- MLS message edits must encrypt the edited payload and call the MLS-specific RPC; no plaintext fallback.
- Linked-device revocation must be server-backed and confirmed before showing success.

## 8. Performance
Avoid rebuilding the entire application for one color update when component-level dependencies suffice. Use immutable values and stable keys; avoid expensive image decoding in build; cache resolved theme; keep animations brief and interruptible; profile scrolling and message lists with large histories.

## 9. Failure handling
Repository operations return typed success/failure results. UI exposes recoverable errors. Failed persistence restores prior state. Import failures do not partially apply. Async work must be cancellable or scoped to the active account to avoid stale state updates after logout/account switch.

## 10. Architecture decision records required
- ADR-001: appearance profile schema and versioning.
- ADR-002: local-only vs optional cloud sync.
- ADR-003: preview/apply/reset transaction semantics.
- ADR-004: per-chat overrides versus global-only styles.
- ADR-005: migration and compatibility window for legacy keys.
