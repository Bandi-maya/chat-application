# Chaty Global Customization and Feature-Parity Program

## Mission
Evolve the existing Chaty Flutter + Supabase application so users can customize the app's supported visual components from Chaty Settings using consistent, reusable templates, while preserving existing routes, data, MLS end-to-end encryption, WebRTC, authentication, permissions, and working features.

Use the GBWhatsApp APK strictly as a user-facing reference for feature categories and interaction patterns. Do not copy its proprietary code/assets/branding, and do not implement privacy/security bypasses. "Parity" means legitimate equivalent user workflows where technically and legally appropriate—not identical internals or unsafe mod behavior.

## Source documents
1. [APK screen and settings inventory](GBWHATSAPP_APK_SCREEN_AND_FEATURE_INVENTORY.md)
2. [Product requirements](CHATY_GLOBAL_CUSTOMIZATION_PRD.md)
3. [Architecture](CHATY_CUSTOMIZATION_ARCHITECTURE.md)
4. [Design system and UX laws](CHATY_DESIGN_SYSTEM_AND_UX_RULES.md)
5. [Feature flows](CHATY_FEATURE_FLOWS.md)
6. [Functional requirements and traceability](CHATY_FUNCTIONAL_REQUIREMENTS_TRACEABILITY.md)
7. [Phase implementation plan](CHATY_PHASED_IMPLEMENTATION_PLAN.md)
8. [Bug hunter audit plan](CHATY_BUGHUNTER_PLAN.md)
9. [QA and test plan](CHATY_QA_TEST_PLAN.md)
10. [Security and privacy requirements](CHATY_SECURITY_PRIVACY_REQUIREMENTS.md)
11. [Migration, rollout and recovery](CHATY_MIGRATION_ROLLOUT.md)
12. [Implementation report template](CHATY_IMPLEMENTATION_REPORT_TEMPLATE.md)
13. [Current implementation progress and remaining gaps](CHATY_IMPLEMENTATION_PROGRESS.md)
14. [UI reference research catalog and design decisions](UI_REFERENCE_RESEARCH_CATALOG.md)

## Non-negotiable engineering rules
- Read the actual current source before editing; do not assume file names or contracts from documentation alone.
- Preserve existing behavior by default. First capture baselines and add regression tests.
- Implement one vertical slice at a time: UI + state/controller + persistence/backend + tests + docs.
- No decorative settings: every exposed setting must affect a real supported component and persist correctly.
- No fake feature success, silent no-op callbacks, or mock data in production paths.
- No plaintext fallback for MLS-encrypted messages.
- Never expose secrets in exported templates, logs, screenshots, diagnostics, or test fixtures.
- Use server-side RLS/RPC authorization; client-side visibility is not authorization.
- Android/iOS platform differences must be documented and tested.
- Never state "100% complete" unless all acceptance criteria and supported-platform checks pass.

## Phase gates
| Phase | Outcome | Exit gate |
|---|---|---|
| 0 | Baseline and code map | Existing routes/services/tests/CI status documented |
| 1 | Correctness/security defects | MLS edit fixed safely; incomplete facade methods audited; security CI gate green |
| 2 | Theme/template core | Typed versioned profile, migration, persistence and preview tests |
| 3 | Global component adoption | Shared shell/header/list/bubble/composer/media/dialog components consume profile |
| 4 | Settings UX | Searchable categories, live preview, reset, template gallery and validated import/export |
| 5 | Feature gap slices | Each APK-derived legitimate workflow mapped to a Chaty service and acceptance test |
| 6 | QA and release hardening | Android/iOS checks, accessibility, performance, security and migration gates pass |

## Definition of done
A feature is done only when the intended UI, state transitions, persistence, backend authorization, encryption (where applicable), loading/empty/error/offline recovery, accessibility, tests and documentation are all complete. A rendered screen alone is not sufficient.

## Design references
- Flutter accessibility and UI design: https://docs.flutter.dev/ui/accessibility/ui-design-and-styling
- Flutter accessibility release checklist: https://docs.flutter.dev/ui/accessibility
- Flutter Material widgets: https://docs.flutter.dev/ui/widgets/material
- Apple accessibility guidance: https://developer.apple.com/design/human-interface-guidelines/accessibility
- Apple motion guidance: https://developer.apple.com/design/human-interface-guidelines/motion
