# Instructions for the Coding Agent — Chaty Template and Feature Program

## Start here
Read, in order:
1. `00_MASTER_EXECUTION_INDEX.md`
2. `GBWHATSAPP_APK_SCREEN_AND_FEATURE_INVENTORY.md`
3. `CHATY_GLOBAL_CUSTOMIZATION_PRD.md`
4. `CHATY_CUSTOMIZATION_ARCHITECTURE.md`
5. `CHATY_DESIGN_SYSTEM_AND_UX_RULES.md`
6. `CHATY_FEATURE_FLOWS.md`
7. `CHATY_FUNCTIONAL_REQUIREMENTS_TRACEABILITY.md`
8. `CHATY_PHASED_IMPLEMENTATION_PLAN.md`
9. `CHATY_BUGHUNTER_PLAN.md`
10. `CHATY_QA_TEST_PLAN.md`
11. `CHATY_SECURITY_PRIVACY_REQUIREMENTS.md`
12. `CHATY_MIGRATION_ROLLOUT.md`

## Before editing
- Inspect the current repository and current branch; check git status and don't discard user changes.
- Read actual implementations, tests, package versions, migration schema and RLS policies.
- Build a map of each existing settings option to its controller, persistence key and consuming widget.
- Find the real status/call/device services before changing facade methods.
- Confirm current MLS payload model and RPC parameter names.
- Record baseline commands and current failures.

## Implementation loop
For each vertical slice:
1. Write/extend a test that captures expected behavior.
2. Implement the smallest safe change.
3. Run formatter, targeted tests, full analyze and full tests.
4. Review the diff for unrelated UI or behavior changes.
5. Update traceability and implementation report.
6. Commit to feature branch; don't push directly to main.
7. If any check cannot run, explicitly report it as blocked rather than passed.

## Prohibitions
- Do not rewrite all screens at once.
- Do not replace real services with mocks in production.
- Do not add inert settings or fake success.
- Do not use plaintext updates for MLS-encrypted messages.
- Do not weaken RLS, Auth preflight, device authorization or call security to make tests pass.
- Do not copy APK source/assets/branding.
- Do not claim 100% parity without complete evidence and tests.
- Do not add unsupported iOS controls just because an Android resource exists.
- Do not export private data through templates.

## First task
Execute Phase 0 discovery and Phase 1 P0 correctness/security work from `CHATY_PHASED_IMPLEMENTATION_PLAN.md`. Report the exact source files, test output and blockers before moving to template foundation. Do not begin broad UI redesign until the current behavior and critical security defects are covered.
