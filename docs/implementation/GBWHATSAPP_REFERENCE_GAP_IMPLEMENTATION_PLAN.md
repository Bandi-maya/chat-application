# GBWhatsApp Reference APK — Chaty Gap Audit and Implementation Plan

**Prepared:** 2026-10-29  
**Reference build:** com.gbwhatsapp, version 2.26.39.80  
**Reference source:** GBWhatsApp-full-app-analysis-final-558d512c(1).pdf supplied for this task  
**Target branch:** feat/gbwhatsapp-reference-gap-audit-20261029  
**Status:** Code review started; one misleading security UI has been corrected in this slice. Automated/device verification remains pending until the relevant CI/device run is observed.

## 1. Goal and guardrails

Use the APK analysis to map legitimate user-facing workflows to Chaty's own Flutter components and services. Reuse Chaty's current controllers, design tokens, Supabase authorization, MLS E2EE and WebRTC paths. Do not copy APK code, branding, proprietary assets or private APIs.

A setting is done only when it has a real consumer, changes the intended screen, survives persistence/restart, handles failure without false success, and has regression coverage. A privacy/security label is not proof of behavior. A local chat lock is a presentation/access gate, not message encryption.

## 2. Evidence quality — what this APK report does and does not establish

The supplied analysis identifies build 2.26.39.80 and is explicitly static-only (PDF pp. 1–2, 20). The APK was not installed or run, no network traffic was captured, no account/chat was touched, native function bodies were not analyzed, and only about 95 classes were decompiled in full. Much of the screen inventory is manifest/signature-level. Consequently:

- Treat class names, resource labels and decompiled branches as leads for our runtime walkthrough, not proof that a feature is reachable, rendered as expected or works correctly.
- The report says exact message tap/long-press gestures and action lists are not established (PDF p. 8).
- Media quality/limit labels are present, but their effects on the send path were not established (PDF p. 17).
- The mod's scheduled/automated send helper dep.sendAMsg has an empty decompiled body; whether it reaches the same encrypted send path was not established (PDF pp. 9–10).
- Its read/played-receipt hook points are visible in code, but end-to-end behavior was not runtime-tested (PDF pp. 14–16).
- The APK was not compared class-by-class with an official APK. Do not state that it is an unmodified WhatsApp release or copy its internals.

The older GBWHATSAPP_APK_SCREEN_AND_FEATURE_INVENTORY.md references build 2.26.28.78. Keep that historical inventory distinct; this plan records evidence from the newer supplied build rather than silently merging the two builds' resource counts.

## 3. Current Chaty baseline observed in source

The repository already contains a global customization/template system and an extensive implementation program. Do not recreate it in parallel.

- TemplateController, ChatyTemplateRegistry, TemplatesSettingsScreen and ComponentOverrideScreen provide base templates and per-component overrides.
- CustomizationCenterScreen offers appearance controls for navigation, header, bubbles, ticks, composer, overlays, motion and density.
- The current implementation-progress document says real consumers exist for navigation, chat rows, composer, Updates layout, profile header, call controls, selected template previews and starred-message navigation. Each claim still needs the regression/runtime verification specified there.
- LocalLockService stores PIN/pattern/password verifiers in FlutterSecureStorage, derives hashes with PBKDF2-HMAC-SHA256 (120,000 iterations, 16-byte random salt), and compares derived bytes in constant time. This is materially safer than the reference mod's Base64-only PIN/recovery storage (PDF pp. 10–11); preserve this implementation.
- Chaty documents an MLS-based E2EE path, server-authorized services, and guardrails against plaintext message fallbacks. Keep all message sending, editing, status, device and call mutations routed through authoritative services.

**Confirmed defect found in this review:** lib/features/settings/security/security_center_screen.dart showed a hard-coded example number, a “Verified” button and an “ENCRYPTED / Active & Verified” label under “Demo Security Model.” The content was not backed by a contact-specific cryptographic fingerprint flow. This falsely implied successful identity verification. This branch removes the fabricated number and verification claim and replaces them with an explicit, honest disclosure. The new widget regression test must pass in CI before the code fix is considered verified.

## 4. APK findings mapped to Chaty work

| Reference finding | Existing Chaty area | Implementation / audit action | Acceptance evidence | Priority |
|---|---|---|---|---|
| GB settings hierarchy covers Home, Conversation, Universal, Themes, Privacy, media, backup/restore and About (PDF pp. 6–7) | Settings registry, Settings screens, Template Studio, Customization Center | Keep one canonical route and one persisted state source. Map every visible row to a controller key and a real consumer. Remove dead/duplicate routes and inert controls; don't add a second preferences store. | Settings wiring matrix + route/widget tests; no unexplained duplicate setting keys. | P1 |
| Bubble, tick, entry-bar, background, button and wallpaper customization (PDF pp. 6–7) | ConversationTemplate, bubble/tick style IDs, theme controller, customization controller | Audit each listed option end-to-end: selection state, apply, restart, chat-list/chat-screen consistency, reset isolation and unknown-value fallback. Do not add a live preview canvas to component-specific settings in this parity pass; use selected-state indicators and only add a static preview if the reference APK's runtime flow confirms it. Only expose styles Chaty actually renders. | Widget/golden matrix for incoming/outgoing text, media, quotes, reactions, ticks; persistence tests for every exposed field. | P1 |
| Custom attachment dialog, emoji picker, message-entry controls (PDF pp. 6–7) | Composer renderer, attachment flows, voice-note controls | Audit camera/gallery/document selection, cancellation, permission denial, upload progress, retry, size/type validation, keyboard insets and voice-note lifecycle. Do not bypass safe upload limits solely to match a label. | Android/iOS permission and lifecycle cases; no stuck recording/busy state or false upload success. | P1 |
| Selection dropdown and raised, paged bottom action menu; a short scale animation is listed (PDF p. 8) | Chat detail/message action UI | Verify tap, long-press, multi-select, reply, copy, forward, edit, delete, star/pin and reactions against actual supported actions. Use accessible original components; treat the APK's precise gesture split/action inventory as unverified until device capture. | Widget tests for each message type and action; focus/large-text/reduced-motion coverage. | P1 |
| Per-chat lock and app lock are separate from message encryption (PDF pp. 7, 9–10, 16) | LocalLockService, ProtectedResourceGate, security center, locked chats | Keep local lock as an access gate. Audit first entry, return from background, repeated auth prompts, chat-switch races, lock disable/change flows, logout/account-switch cleanup and secure phrase recovery. Do not claim the lock encrypts chats. | Credential unit tests, app/chat lock widget tests, lifecycle/device tests. | P1 |
| Privacy toggles and receipt hooks are present, but runtime behavior is not proved (PDF pp. 10, 14–16) | Privacy center, preferences, backend capabilities | For each toggle, document promise, exact consumer, backend/platform enforcement and limitation. Remove or relabel unsupported controls instead of implying ghost mode/read-receipt effects that cannot be verified. Never bypass another user's privacy or forge delivery/read state. | Toggle-to-service matrix, backend authorization tests and truthful UX copy. | P0/P1 |
| Quick replies, auto reply, scheduler and mass sender appear in the mod (PDF pp. 7, 9–10, 17) | Message Management, scheduler/automation preferences, outgoing queue | Verify automated and scheduled messages go through the same authenticated MLS-capable sending path as manual messages, with idempotency, retries and cancellation. Review message text at rest and notification contents. Do not implement mass unsolicited sending. | Queue/MLS integration tests, offline/retry/duplicate tests, local-storage privacy review. | P0/P1 |
| Media preview, HD/large-file and status-duration labels appear (PDF pp. 4–5, 17) | Media picker/composer/upload service, status service | Trace actual upload path and enforce encryption, MIME/size validation, cancellation and server authorization. Do not infer that a “disable limit” label is safe or effective. | Valid/invalid file, interrupted upload, retry, save/open and recipient-decrypt tests. | P1 |
| Status, profile, groups, channels and call screen families are inventoried (PDF pp. 3–5) | Status service, profile/group flows, WebRTC and call history services | Continue one vertical slice at a time. Status publish/view/expiry must be server-backed; group mutations must enforce roles; call history must be authoritative and de-duplicated. Add only flows that Chaty's product/backend actually supports. | Integration tests and Android/iOS walkthrough, with screenshots and device/OS/build recorded. | P1/P2 |
| Full backup, media backup/restore, updater and external translation components are present; encryption/trust behavior is uncertain (PDF pp. 10–12, 17–18) | Backup/export, template import/export, network and external integrations | Do not copy the mod's unverified ZIP backup/update flows, reversible backup key, permissive TLS reporter, user-CA pin override or cleartext defaults. External translation must disclose that text leaves the device and require explicit consent; never send messages to a provider silently. Use allowlisted HTTPS release metadata and validated schemas. | Security review, hostile-input tests, TLS/config checks, consent tests, export inspection showing no secrets. | P0/P1 |
| The APK has 871 activities and 122 aliases, but distinct visible screen count is unknown (PDF pp. 2–3, 20) | Full Chaty routes/screens and emulator QA | Build a route-driven walk-through checklist instead of treating the APK class count as a screen count. Mark each parity item Implemented / Partial / Missing / Unsupported / Needs Runtime Evidence. | Recorded device walkthrough; no item marked “verified” from static strings alone. | P0 discovery |

## 5. Ordered implementation plan

### Phase 0 — Reconcile evidence and baseline
1. Keep the new APK evidence separate from the older resource inventory.
2. Enumerate Chaty routes, settings keys, controllers, consuming widgets, backend/RPC contracts and tests for the reference-derived components.
3. Run the existing required baseline commands and inspect current CI state: formatter, flutter analyze, flutter test, migration/RLS checks, Android debug build, and iOS build where a macOS runner exists.
4. Mark emulator-only scenarios as blocked when no device/emulator is available; do not infer runtime results.

**Exit gate:** a source/test map with unverified behavior clearly labeled.

### Phase 1 — Correct misleading security and correctness claims
1. Remove fabricated safety-number data and “verified/encrypted” badges that are not backed by a current conversation's cryptographic state. **Implemented in this branch; test/CI pending.**
2. Verify that all app-lock/chat-lock credentials remain in secure storage and that wrong credentials, lockouts, cancellation, background/resume and account switching fail safely.
3. Audit privacy controls for false promises and unimplemented callbacks.
4. Keep the previously documented MLS message-edit and server-backed status/call/device work at P0 until their existing regression tests and CI are green.
5. Do not release/build around a failed Supabase Auth or security preflight.

**Exit gate:** all P0 tests and required CI security checks pass; none of the security UI presents simulated data as verified identity proof.

### Phase 2 — Verify the customization wiring
1. Build a settings-to-controller-to-persistence-to-consumer matrix for navigation, theme colors, bubble/tick, composer, home/chat list, profile, Updates, calls and overlays.
2. For every control, test select → selected-state indicator → apply → navigate away/back → restart → reset one section → reset all. Do not introduce a live preview canvas into the component-specific settings; only add a static preview if the reference APK walkthrough confirms that behavior.
3. Ensure two quick independent changes cannot overwrite one another; malformed import and storage failure leave the previous valid profile active.
4. Keep template export appearance-only and validate import size/schema/allowlisted values.

**Exit gate:** each exposed setting maps to a real consumer and passes persistence/failure tests.

### Phase 3 — Conversation and message-action parity
1. Test the entire conversation screen and message types: incoming/outgoing, text, image/video/document, voice, quoted reply, reaction, deleted/edited, starred and system/failed states.
2. Verify selection-menu actions against repository/backend support, permissions and ownership checks.
3. Audit chat-lock entry and return flows separately from cryptographic state.
4. Apply shared styling through existing template tokens; no per-screen duplicate theme logic.

**Exit gate:** action-by-action test matrix; no message action silently succeeds or bypasses MLS/RLS.

### Phase 4 — Media, automation, status and calls
1. Validate media picker, preview, upload/download, cancel/retry and permissions.
2. Verify scheduled/quick/auto replies use the authenticated, encrypted outgoing path and safe local storage; omit unsafe mass-sender behavior.
3. Verify status publication, view tracking, privacy, expiry and call history via authoritative services.
4. Test network loss, reconnect, timeouts, duplicate callbacks and process/app lifecycle.

**Exit gate:** integration tests for each supported vertical slice.

### Phase 5 — Runtime screen-by-screen QA and polish
1. Run the supplied APK in a controlled test device/emulator and capture each reachable screen, menu, setting, confirmation, empty/loading/error state and permission branch.
2. Repeat equivalent Chaty journeys on Android and iOS where supported.
3. Check small screen, landscape, keyboard open, notches/gesture inset, large text, screen readers, contrast, reduced motion and rapid setting changes.
4. Record defects with severity, reproduction, root cause, fix commit, regression test and verification evidence.

**Exit gate:** P0/P1 bugs are closed or explicitly accepted; unresolved platform/device checks remain visibly marked as blocked.

## 6. Regression and acceptance checklist

- [ ] No fabricated safety number, sample QR, or “Verified” state unless derived from and verified against the actual cryptographic identity material for the selected conversation/contact.
- [ ] No privacy setting without a truthful, testable consumer and documented server/platform limitation.
- [ ] PIN/pattern/password verifiers stay in platform secure storage; no reversible Base64 credentials or secrets in exported/synced preferences.
- [ ] Every automated send uses the same authorized encrypted transport as supported manual messages; failures retry safely without duplicates or plaintext fallback.
- [ ] Template selection affects the intended runtime component and survives restart; reset is scoped; import/export contains appearance settings only.
- [ ] All media actions validate type/size, handle cancellation/permission failure and preserve encryption.
- [ ] Chat lock is never presented as encryption; lock prompt behavior survives background/resume and account switches.
- [ ] Settings and message actions have loading/empty/error/offline states, accessible labels and reduced-motion behavior.
- [ ] Automated tests, static analysis, migrations/security checks and platform builds have recorded results. Do not report any of these as passing until an actual run confirms it.
- [ ] APK runtime comparison distinguishes direct code evidence, inference and unverified runtime behavior.

## 7. Current execution status

| Slice | Status |
|---|---|
| Review latest APK report and current Chaty plan/source | Reviewed statically |
| Remove misleading sample safety-number/verified security UI | Implemented in this branch |
| Widget regression test for the disclosure | Added in this branch; pending CI execution |
| Full Flutter formatter/analyzer/test/build run from this environment | Not run by the GitHub connector; must be verified by CI or a local Flutter environment |
| APK + Chaty screen-by-screen emulator comparison | Not run; device/runtime evidence still required |



## Additional CI issues discovered while executing this plan

The first PR validation exposed two unrelated-to-feature but release-blocking repository defects, so this branch also includes narrowly scoped fixes:
- Removed the extra trailing closing brace at the end of `lib/features/auth/login_screen.dart` after the Android analyzer reported `expected_executable` at line 527.
- Converted `.github/workflows/diag.yml`, `.github/workflows/settings-render.yml` and `.github/workflows/build-themes-apk.yml` from single-line JSON to equivalent structured YAML and quoted shell summary markers/paths so workflow lint can parse the actual run scripts.

These are marked **fixed in source, CI re-run pending**, not “verified”; the latest head's analyzer, full test and runtime-audit results are the acceptance evidence.
