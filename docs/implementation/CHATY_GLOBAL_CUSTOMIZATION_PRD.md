# Chaty Global Customization — Product Requirements Document

## 1. Product vision
Give each Chaty user one coherent Settings workspace where supported aspects of the app can be customized using ready-made templates or detailed controls. Settings should personalize the same shared components across chats, home, status, calls, profiles and dialogs without fragmenting product behavior or weakening security.

## 2. Problem statement
Customization options can become misleading when settings only update a preview, persist inconsistently, or affect one screen but not another. Duplicated styles create inconsistent layouts, while feature requests inspired by third-party clients can lead to unsupported privacy claims or unstable backend logic. Chaty needs a single source of truth for visual preferences and a traceable feature implementation plan.

## 3. Users and jobs
- Personalizer: chooses a preset, changes colors/bubbles/navigation and sees a live preview.
- Accessibility-focused user: adjusts text scale, contrast, tap targets and motion.
- Power user: customizes granular components and saves several profiles.
- User switching devices: expects account preferences to sync if cloud sync is enabled, with understandable conflict behavior.
- Maintainer/QA: needs each option linked to a consumer widget and automated tests.

## 4. Goals
- Versioned typed appearance profile and safe migration from current preference keys.
- One global token/component system with local overrides only where explicitly supported.
- Built-in templates: Default, Classic, Minimal, Compact, High Contrast, and user-created templates.
- Live preview, apply, category reset, full reset, validated import/export.
- Consistent appearance across supported screens and Android/iOS.
- Preserve current navigation and domain logic.
- Trace APK-inspired features to existing/missing Chaty functionality and tests.

## 5. Non-goals
- Copying proprietary APK code, assets, branding, or private APIs.
- Replicating unsafe "anti-revoke", stealth, interception, fake read receipts, or privacy-bypass behaviors.
- Rewriting all backend services at once.
- Promising identical OS behavior on Android and iOS.
- Treating a resource identifier as proof a feature works.
- Exporting private keys, access tokens, session cookies, message content, or account secrets with templates.

## 6. Product surfaces
| Surface | Requirements |
|---|---|
| Settings home | Clear categories, search, descriptions, current template summary, reset and preview |
| Template gallery | Preset cards, selected state, preview, apply, duplicate/save custom |
| Theme | Light/dark/system, accent/semantic colors, surfaces, wallpaper and contrast validation |
| Navigation | Bottom bar/rail styles, supported tab labels/order/visibility, header variants |
| Chat list | Density, avatar size/shape, badges, presence display, archive position |
| Conversation | Incoming/outgoing bubbles, spacing, timestamp/date dividers, receipt icon style, quote/reaction presentation |
| Composer | Shape, background, action icons, attachment tray, voice recording affordance |
| Media | Image/video/document rows, gallery layout, audio controls and progress styling |
| Status | Ring style/colors, list layout and viewer chrome; status semantics remain backend-owned |
| Calls | Call-history rows and supported call-screen layout variants |
| Profile/dialogs | Avatar, profile header, sheets, confirmation dialogs and contextual menus |
| Accessibility | System font scaling, high contrast, reduced motion, large tap targets and screen reader labels |
| Export/import | Versioned JSON of appearance only; strict schema and size limits |

## 7. Functional requirements
- FR-001: Existing appearance settings migrate without losing their values.
- FR-002: Profile changes validate before becoming active.
- FR-003: Every exposed control updates at least one real component and persists.
- FR-004: Preview does not mutate persistent settings until Apply, unless the control is explicitly immediate with Undo.
- FR-005: Category reset changes only that category.
- FR-006: Full reset asks for confirmation and restores versioned defaults.
- FR-007: Imported profiles reject unknown unsafe fields, oversized input, invalid colors/enums and unsupported schema versions.
- FR-008: Export excludes account data, backend configuration, credentials and cryptographic material.
- FR-009: Theme updates propagate without unnecessary app restart.
- FR-010: User choices remain stable across app restart and supported account/device changes.
- FR-011: OS accessibility settings take precedence over cosmetic settings when needed for readability and reduced motion.
- FR-012: No security/privacy option is exposed unless the underlying behavior is genuinely supported.
- FR-013: Domain actions continue using existing repositories/services and server authorization.
- FR-014: All new and migrated behavior has tests and traceability.

## 8. Success metrics
- 100% of visible settings have a mapped consumer, persistence rule and test.
- Zero settings that silently do nothing.
- Zero loss of existing appearance preferences during migration.
- Zero plaintext fallback for MLS-encrypted message edits.
- No increase in crash rate or materially degraded scrolling/call performance in regression testing.
- All critical acceptance tests pass on Android; iOS tests pass where a macOS runner/device is available.
- Accessibility audit meets project thresholds and has documented exceptions.

## 9. User experience principles
Predictability, consistency, visibility of state, reversible actions, useful defaults, progressive disclosure, direct manipulation through preview, platform familiarity, accessibility, and truthful feedback. See CHATY_DESIGN_SYSTEM_AND_UX_RULES.md.

## 10. Acceptance criteria
A reviewer can change a template, navigate all major tabs, inspect chat list/conversation/composer/media/call/status surfaces, restart the app, and observe consistent persistent styling. Resetting one category preserves unrelated settings. Importing invalid data shows a useful error without altering the current profile. Existing chat send/receive, MLS security, calls, status and authentication behavior remains intact.
