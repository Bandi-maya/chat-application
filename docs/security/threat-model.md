# Chaty Security Threat Model

Audit date: 2026-08-22

## Security objectives

Chaty must preserve confidentiality, integrity, authorization boundaries, availability, and device/session authenticity for private messaging, media, calls, tasks, status updates, contacts, and account data. UI visibility is never an authorization control; sensitive access must be enforced by RLS, validated RPCs, Storage policy, cryptographic protocol state, or trusted server execution.

## Protected assets

- Supabase refresh/access sessions
- user profile/private preference data
- conversation membership
- private message content and metadata
- E2EE identity/session keys and key envelopes
- private attachments and thumbnails
- delivery/read state
- status/update content and viewer lists
- task/poll data
- contact/privacy relationships
- call SDP, ICE candidates, media streams and call history
- linked-device identity and revocation state
- local app-lock credentials and decrypted cache
- server secrets including service-role credentials and TURN shared secret

## Trust boundaries

1. **Flutter client ↔ Supabase Auth** — client is untrusted; authentication establishes identity, not authorization.
2. **Flutter client ↔ PostgREST/RPC** — every object identifier is attacker controlled until validated by RLS/RPC membership checks.
3. **Flutter client ↔ Realtime** — subscriptions/events are sensitive data access and require the same participant authorization as ordinary reads.
4. **Flutter client ↔ Storage** — private object paths and signed URLs must not be globally readable.
5. **Flutter client ↔ Edge Functions** — functions must authenticate callers and must not expose privileged secrets.
6. **Device secure storage ↔ application memory** — decrypted secrets may exist in process memory only as long as needed.
7. **WebRTC signaling ↔ media plane** — SDP/ICE signaling does not prove an active call; only peer-connection transport state does.
8. **Linked devices ↔ account identity** — each device requires explicit identity, revocation and future-key cutoff.

## Primary adversaries

### Unauthenticated network client
Attempts account enumeration, anonymous RPC abuse, auth-flow abuse and direct public API access.

### Malicious authenticated user
Attempts BOLA/IDOR against conversations, messages, envelopes, media, calls, tasks, updates, receipts, devices and profiles by substituting UUIDs.

### Removed/blocked participant
Attempts to retain access after conversation removal, blocking, device revocation or key rotation.

### Stolen device/session
Attempts to reuse a refresh token, bypass local app lock, read decrypted cache or receive private notification content.

### Compromised backend credential
Service-role/TURN/backend secrets must never ship in Flutter or repository history. Exposure is treated as a server credential incident.

### Curious infrastructure operator/database reader
E2EE private-message payloads and private attachments should remain unintelligible after the E2EE migration is complete.

## Verified threats and controls

| Threat | Previous/Current Risk | Required Control | Current Status |
|---|---|---|---|
| E2EE envelope recipient/key substitution | Previous INSERT RLS used tautological self-comparisons | Bind target user to current conversation membership and target key ownership/revocation | Fixed 2026-08-22 |
| Global status/update visibility | Broad authenticated SELECT policy defeated narrower permissive policy | Owner/shared-conversation RLS only | Fixed 2026-08-22 |
| Automation log ambiguity | RLS enabled with no policy | Rule owner + conversation-member policy | Fixed 2026-08-22 |
| Obsolete privileged auth RPC | `resolve_login_email` callable by anon/authenticated | Revoke client execution; current login stays on Edge Function | Fixed 2026-08-22 |
| Fake call connection state | UI/signaling could mark connected without media transport | WebRTC peer connection; timer/connected UI only from transport-connected callback | Code migrated on hardening branch; device verification pending |
| TURN secret disclosure | Long-lived client credential would be extractable | JWT-protected Edge Function generating short-lived HMAC credentials | Function deployed; TURN server secrets/config still required |
| Plaintext private messages server-side | Existing RPC/storage uses plaintext body | Audited device/session E2EE + ciphertext schema + client-side attachment encryption | Blocker; not yet migrated |
| Broad realtime invalidation | Large refetches and state races | Event-specific deltas, cursors, dedupe and reconnect backfill | High; not yet migrated |
| Leaked-password reuse | Supabase leaked-password protection disabled | Enable Auth leaked-password protection | High; project configuration pending |
| Public/private SECURITY DEFINER RPC surface | Advisor warns on intentional client RPCs | Per-function caller/object validation, safe search_path, narrow grants, documented reason | Review in progress; do not mass-convert |

## Authentication/session rules

- Persisted Supabase session is the authentication source of truth at application bootstrap.
- Profile hydration failure must not be interpreted as sign-out.
- Backgrounding/process death must not sign the user out.
- App Lock is local UI protection and must never destroy a valid Supabase session.
- Logout must intentionally revoke/clear session-sensitive state.

## Database authorization rules

For every private object, validate the caller against ownership or current membership. `TO authenticated` alone is insufficient. Client-provided `user_id`, `conversation_id`, `message_id`, `call_id`, `task_id`, `status_id`, `device_id`, and key identifiers are hostile inputs.

`SECURITY DEFINER` is acceptable only where elevated access is necessary to implement a narrow capability and the function:

- sets a safe search path;
- authenticates with `auth.uid()`;
- checks target membership/ownership explicitly;
- validates state transitions;
- exposes only the minimum result;
- has narrow EXECUTE grants;
- has adversarial tests.

## Messaging/E2EE target

Private messages must move from plaintext server bodies to an audited device-oriented protocol with identity keys, signed/one-time prekeys where applicable, asynchronous session establishment, Double Ratchet or equivalent maintained implementation, authenticated encryption, per-device sessions, verification UX, revocation and key rotation. Do not invent primitives.

The server may retain routing metadata required for delivery, but must not require private plaintext. Notifications must use generic content unless a secure local decryption design is implemented.

## Attachment target

Generate a random per-file key, encrypt before upload, store only ciphertext in a private bucket, place the file-key envelope in encrypted message metadata, decrypt locally, and keep decrypted cache controlled/clearable. Unrestricted public URLs are forbidden for private media.

## Call target

- Call invitation/session rows are participant-authorized.
- SDP/ICE are stored/transmitted only for the two authorized call participants.
- Real media uses WebRTC; signaling state alone cannot display Connected.
- STUN-only success is insufficient for production; TURN relay must be available for restrictive NATs.
- TURN shared secret remains server-side; clients receive only short-lived credentials.
- Mute/camera controls operate on actual media tracks.
- Media tracks, peer connection, subscriptions and timers are always torn down on end/failure/logout.

## Local-device controls

- App-lock PIN/password material must use a password KDF and secure platform storage, not plaintext or raw SHA-256.
- Notification previews respect lock/privacy settings.
- Sensitive logs must exclude message plaintext, tokens, passwords, PINs, cryptographic keys and privileged signed URLs.
- Decrypted media should not be placed into globally accessible storage by default.

## Mandatory adversarial regression identities

Use at least three legitimate test accounts:

- **A** — owner/sender/caller
- **B** — intended participant/recipient/callee
- **C** — authenticated unrelated attacker

C must be denied private conversation rows, message/envelope access, private media, call sessions/candidates, update viewer lists, task mutations, device data and profile mutations that do not belong to C.

## Release blockers

1. Complete audited message E2EE migration and encrypted attachments.
2. Configure and verify production TURN relay credentials/URLs through server secrets.
3. Run two-physical-client WebRTC tests including a TURN-relayed network path.
4. Enable leaked-password protection in Supabase Auth.
5. Complete SECURITY DEFINER function-by-function review and adversarial RPC tests.
6. Remove remaining production mock/fallback semantics and silent critical catches.
7. Verify private Storage policies and notification payload privacy end to end.
