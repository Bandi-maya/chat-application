# Chaty Production Audit

Branch: `production-hardening-2026-08-22`

This audit records verified production defects. Findings are evidence-driven from the repository and the connected Supabase project. Cosmetic redesign is intentionally lower priority than correctness, security, data integrity, and transport behavior.

## AUTH-001

### Severity
Critical

### Area
Authentication

### File(s)
- `lib/main.dart`
- `lib/data/services/backend_service.dart`

### Current behavior
Supabase is initialized before `runApp`, but `ChatyBackendService.initialize()` is not awaited before the root route is selected. `MaterialApp.home` chooses between the authenticated shell and `WelcomeScreen` from `_backend.isAuthenticated`, while that property also requires a hydrated `_currentUser`.

### Root cause
Authentication session restoration and profile hydration are coupled to presentation routing, but backend bootstrap is not part of the startup critical path.

### User impact
A valid persisted Supabase session can be routed to Welcome during cold start/process restoration even though the refresh session still exists.

### Security/business impact
Creates an apparent logout/authentication loop and encourages unnecessary reauthentication.

### Proposed repair
Introduce an explicit auth bootstrap gate/state machine and await backend session restoration before rendering authenticated/unauthenticated routes. Treat profile hydration failures separately from authentication loss.

### Dependencies
None.

### Verification
1. Login on a physical Android device.
2. Kill the process without logging out.
3. Relaunch offline and online.
4. Confirm no Welcome/login flash and no sign-out event.
5. Repeat after token refresh and app update.

---

## SEC-001

### Severity
Critical

### Area
Encryption / RLS

### File(s)
- live `public.conversation_key_envelopes` policies
- `supabase/migrations/202608220001_rls_hardening.sql`

### Current behavior
The previous envelope INSERT policy contained tautologies equivalent to `cm.user_id = cm.user_id` and `k.user_id = k.user_id`, so recipient membership and key ownership were not bound to the envelope recipient.

### Root cause
Alias/self-comparison errors in RLS predicates.

### User impact
A conversation member could attempt to create malformed/envelopes targeting unrelated key ownership combinations.

### Security/business impact
Key-distribution authorization failure undermining the E2EE trust boundary.

### Proposed repair
Bind envelope `user_id` to an actual current conversation member and require `recipient_key_id` to belong to that same user and be non-revoked. Restrict envelope reads to the intended user/key owner.

### Dependencies
Existing `conversation_key_versions`, `conversation_members`, `user_e2ee_keys`, and `is_conversation_member`.

### Verification
- unrelated user cannot read an envelope;
- unrelated user cannot insert an envelope;
- member cannot bind an envelope to another user's key under a mismatched `user_id`;
- revoked recipient key is rejected for new envelope insertion.

### Status
Fixed in live Supabase and mirrored to the hardening branch.

---

## SEC-002

### Severity
High

### Area
Privacy / RLS / Updates

### File(s)
- live `public.status_updates` policies
- `supabase/migrations/202608220001_rls_hardening.sql`

### Current behavior
A permissive `status_updates_select_authenticated` policy allowed every authenticated user to read every unexpired status. A second participant-scoped SELECT policy could not restrict it because permissive RLS policies are ORed.

### Root cause
Overlapping permissive SELECT policies with one broad authenticated rule.

### User impact
Users outside any shared conversation could receive status/update rows.

### Security/business impact
Cross-user privacy leak.

### Proposed repair
Remove the broad policy and retain one participant/owner-scoped policy.

### Dependencies
`conversation_members`.

### Verification
Test owner, shared-conversation user, unrelated authenticated user, and expired status.

### Status
Fixed in live Supabase and mirrored to the hardening branch.

---

## SEC-003

### Severity
Critical

### Area
Authentication / Database RPC

### File(s)
- live function `public.resolve_login_email(text,text)`
- `supabase/migrations/202608220002_access_and_index_hardening.sql`
- `supabase/functions/username-login/index.ts`
- `lib/data/services/username_login_service.dart`

### Current behavior
A legacy `SECURITY DEFINER` RPC that accepts a login identifier and password was executable by anonymous and authenticated roles even though the current Flutter login path uses the `username-login` Edge Function.

### Root cause
Obsolete RPC grants remained after the login architecture moved to an Edge Function.

### User impact
No legitimate current-client benefit.

### Security/business impact
Unnecessary privileged attack surface around auth data.

### Proposed repair
Revoke public/anon/authenticated execution. Retain only privileged internal access until the obsolete function is safely removed in a later migration after usage verification.

### Verification
Security advisor no longer reports anonymous/authenticated execution of `resolve_login_email`; current username login continues through the Edge Function.

### Status
Fixed in live Supabase and mirrored to the hardening branch.

---

## SEC-004

### Severity
High

### Area
Database / Automation / RLS

### File(s)
- live `public.auto_reply_log`
- `supabase/migrations/202608220001_rls_hardening.sql`

### Current behavior
RLS was enabled but no policy existed.

### Root cause
Automation log table was created without a user ownership policy.

### User impact
Depending on the write path, legitimate user-scoped automation access could fail while the table's intended authorization model remained undocumented.

### Security/business impact
Ambiguous production authorization and advisor failure.

### Proposed repair
Allow access only when the related auto-reply rule belongs to the authenticated user and the user is a member of the referenced conversation.

### Verification
Owner/member succeeds; unrelated user fails; advisor warning disappears.

### Status
Fixed in live Supabase and mirrored to the hardening branch.

---

## CALL-001

### Severity
Blocker

### Area
Calls / WebRTC

### File(s)
- `lib/data/services/call_signaling_service.dart`
- `lib/data/services/rich_chat_realtime_service.dart`
- `lib/features/calls/ongoing_call_screen.dart`
- `lib/features/calls/calls_screen.dart`
- `pubspec.yaml`

### Current behavior
The call UI can transition to a connected state from signaling state alone. Video surfaces are icon/placeholders; mute/camera/speaker controls mutate local booleans; there is no `RTCPeerConnection` media path and no `flutter_webrtc` dependency.

### Root cause
Signaling/UI prototype logic was promoted into the production path without a real media transport.

### User impact
The UI can imply a working call even though no audio/video transport exists.

### Security/business impact
False product behavior and failed release gate.

### Proposed repair
Consolidate onto one call-session controller backed by `call_sessions`/`call_ice_candidates`, real WebRTC SDP/ICE/media streams, permission handling, STUN/TURN credentials, transport-derived connection state, and deterministic teardown. Hide preview/mock actions from release until the transport is complete.

### Dependencies
TURN credential architecture, WebRTC package, notification/background incoming-call design.

### Verification
Two physical clients establish real audio/video over direct and TURN-relayed paths; UI connected state is emitted only after peer connection reaches a connected transport state.

---

## CALL-002

### Severity
High

### Area
Calls / Realtime Authorization

### File(s)
- `lib/data/services/call_signaling_service.dart`
- `lib/data/services/rich_chat_realtime_service.dart`

### Current behavior
Two independent call-signaling implementations use user-addressed Realtime Broadcast channels such as `chaty_calls_v1_<userId>`. The privacy gate is partly client-side.

### Root cause
Duplicated signaling implementations and client-addressed broadcast topology.

### User impact
Divergent call state and duplicate invite behavior are possible.

### Security/business impact
Authorization is harder to reason about and cannot be accepted as the production call trust boundary.

### Proposed repair
Remove duplicated broadcast signaling from release flow. Use server-authorized `call_sessions` and `call_ice_candidates` with participant RLS/RPC validation; use private Realtime only as a transport optimization where needed.

### Verification
Unrelated authenticated user cannot create/read/update/subscribe to another call session or ICE candidates.

---

## ARCH-001

### Severity
High

### Area
Architecture / State

### File(s)
- `lib/data/repositories/mock_data_store.dart`
- `lib/injection/locator.dart`
- multiple feature screens

### Current behavior
A production compatibility facade still named `MockDataStore` is registered globally. It creates its own `ChatyBackendService` instead of consuming the locator singleton and includes fallback guest-profile behavior.

### Root cause
Incremental migration preserved the historical mock adapter name and internal ownership model.

### User impact
Different screens/services can observe different backend state and fallback data.

### Security/business impact
Split state ownership can mask auth/profile failures and violates the no-production-mock-path release rule.

### Proposed repair
Create a production-named data facade/repository that receives the singleton backend through dependency injection; migrate call sites incrementally; remove guest fallback from authenticated production paths; retain mocks only under test fixtures.

### Verification
One backend instance owns session/profile/realtime state; production dependency graph has no `MockDataStore` imports.

---

## MSG-001

### Severity
Blocker

### Area
Messaging / Encryption

### File(s)
- `lib/data/services/backend_service.dart`
- live `public.messages`
- live message RPCs

### Current behavior
The client currently sends plaintext message bodies to `send_message`; server RPCs and message reads operate on plaintext `body` fields.

### Root cause
Existing E2EE key tables are not integrated with an audited per-device ratchet/session message transport.

### User impact
Private message confidentiality depends on server/database access controls rather than E2EE.

### Security/business impact
Fails the master prompt's E2EE production acceptance gate.

### Proposed repair
Implement an audited device/session protocol, migrate message storage to ciphertext/envelope metadata, encrypt attachments locally, migrate client read/write paths, and only then remove plaintext fields after backfill/compatibility validation.

### Dependencies
Device identity/key management, secure local storage, linked-device semantics, migration/versioning.

### Verification
Two-user encrypted send/receive succeeds; database inspection shows no private plaintext body/preview; unrelated user cannot obtain envelopes/ciphertext rows outside membership.

---

## PERF-001

### Severity
High

### Area
Messaging / Realtime / Performance

### File(s)
- `lib/data/services/backend_service.dart`

### Current behavior
Broad Postgres change subscriptions trigger a reconciliation path that rehydrates conversations/tasks and refreshes loaded message timelines.

### Root cause
Realtime events are treated as invalidation for large aggregates rather than event-specific deltas.

### User impact
Unnecessary network traffic, rebuilds, latency, battery use, and poor scaling on large histories.

### Security/business impact
Operational scalability risk.

### Proposed repair
Use paginated initial hydration plus event-specific reducers, stable cursors/client IDs, reconnect backfill, targeted receipt/reaction/message mutations, and bounded cache reconciliation.

### Verification
Large-history test demonstrates one incoming message does not refetch every loaded timeline; reconnect backfills only missing deltas.

---

## ERROR-001

### Severity
High

### Area
Reliability / Observability

### File(s)
- `lib/data/services/backend_service.dart`
- `lib/data/services/call_signaling_service.dart`
- `lib/data/services/rich_chat_realtime_service.dart`
- `lib/data/repositories/mock_data_store.dart`

### Current behavior
Several production-critical paths catch and discard exceptions or downgrade them to local fallback state.

### Root cause
Prototype resilience patterns were used instead of typed failure propagation/structured logging.

### User impact
Broken database, signaling, or hydration operations can look like empty/successful UI.

### Security/business impact
Masks incidents and makes release verification unreliable.

### Proposed repair
Introduce typed error categories, safe structured logging, user-visible retry/error states, and explicit nonfatal handling only where intentionally documented.

### Verification
Fault injection for network/database/signaling errors produces deterministic error state and safe logs rather than silent success.

---

## DB-001

### Severity
High

### Area
Database / Release Engineering

### File(s)
- `supabase/migrations/`
- live Supabase migration history

### Current behavior
The repository contains only a small subset of the migrations already applied to the live project.

### Root cause
Historical production schema changes were applied without complete source-controlled migration parity.

### User impact
Fresh/local/staging databases cannot be assumed to reproduce production.

### Security/business impact
Disaster recovery, onboarding, CI database tests, and future migrations are unreliable.

### Proposed repair
Snapshot/reconstruct the live schema and migration history into a clean baseline strategy, then require every future production DDL change to land in source control before/with deployment.

### Verification
A fresh Supabase environment can be built from repository migration artifacts and matches production schema/policies/functions except approved environment configuration.

---

## DB-002

### Severity
Medium

### Area
Database / Performance

### File(s)
- `supabase/migrations/202608220002_access_and_index_hardening.sql`

### Current behavior
Production advisor reported multiple foreign keys without supporting indexes and two exact duplicate indexes.

### Root cause
Incremental migrations added constraints without consistently adding/removing supporting indexes.

### User impact
Potentially slower deletes/joins and unnecessary write/storage overhead.

### Proposed repair
Add missing FK indexes and remove only verified exact duplicates.

### Verification
Foreign-key missing-index and duplicate-index advisor warnings no longer appear.

### Status
Fixed in live Supabase and mirrored to the hardening branch.

---

## SEC-005

### Severity
High

### Area
Authentication

### File(s)
Supabase Auth configuration

### Current behavior
Leaked-password protection is disabled according to the current security advisor.

### Root cause
Project Auth configuration.

### User impact
Users can choose credentials known to appear in breach corpora when other password rules allow them.

### Security/business impact
Avoidable account takeover risk.

### Proposed repair
Enable leaked-password protection in Supabase Auth configuration and test signup/password-change UX.

### Dependencies
Project Auth configuration access; not a SQL migration.

### Verification
Security advisor warning disappears and compromised-password test is rejected according to configured policy.

---

## PERF-002

### Severity
Medium

### Area
Database / RLS Performance

### File(s)
Multiple live RLS policies

### Current behavior
The performance advisor still reports multiple policies that evaluate `auth.uid()` per row.

### Root cause
Older policies predate the optimized `(select auth.uid())` form.

### User impact
Higher policy-evaluation cost as tables grow.

### Proposed repair
Review and migrate policies table-by-table while preserving exact authorization semantics. Do not mass-edit.

### Verification
RLS init-plan warnings are removed for each reviewed table and adversarial authorization tests remain green.
