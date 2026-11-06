# Chaty server requirements: calls and bundle preferences

4 November 2026. Proposed protocol v1, not a statement of deployed server support. Server code is owned by Bandi. Client drafts are partial and feature gates must stay denied until actual capability and transport tests pass.

## Common envelope and authority

Client sends existing authenticated WebSocket envelope:

```json
{"type":"call_signal","target_user_id":"peer-user-id","data":{"signal_type":"video_upgrade_request","call_id":"active-call-id","protocol_version":1,"request_id":"uuid"}}
```

Server derives sender_id from the authenticated connection, never caller-supplied sender/caller IDs. Relay only to another authenticated participant of the same active call and account/tenant. Reject self-target, foreign call, wrong account, ended call, unauthorized participant and unbounded payload. Account changes invalidate subscriptions, queued effects and prior capability snapshots. Never replay stale consent after reconnect. Preserve call_id and existing audio connection/duration during optional feature operations. Error replies must include call_id, feature, request_id when present, and stable code: unsupported, forbidden, stale_call, expired, rate_limited, invalid_payload, peer_unavailable, conflict, failed. No raw SDP, tokens, media or recording bytes in diagnostic logs.

Advertise capabilities through an authenticated server response scoped to call_id and peer membership, with expiry and protocol version. Example shape, not an existing endpoint:

```json
{"call_id":"c1","protocol_version":1,"expires_at":"RFC3339","server":{"video_upgrade_v1":false,"hold_v1":false,"recording_consent_v1":false,"reactions_v1":false,"group_calls_v1":false},"peers":{"peer-user-id":{"video_upgrade_v1":false,"hold_v1":false,"recording_consent_v1":false,"reactions_v1":false,"group_calls_v1":false}}}
```

Unknown, missing, expired or conflicting support means false. The server cannot claim local Android media readiness. Client provider gates are separate. A grant applies only to its feature and current call; ordinary offer/answer relay is not an optional-feature grant.

Proposed bounds: request IDs <=128 UTF-8 characters, opaque unique IDs, <=64 retained deduplication IDs per participant/feature/call. SDP <=256KiB, control payload <=4KiB. Return retry_after_ms on rate limit. Confirm final limits against infrastructure before enabling.

## 1. Audio-to-video upgrade

Capability video_upgrade_v1 required on server and both endpoints. Distinct signal_type avoids the existing initial-offer deduplication path.

| Type | Additional data fields |
|---|---|
| video_upgrade_request | request_id |
| video_upgrade_accept / video_upgrade_decline / video_upgrade_cancel | request_id |
| video_upgrade_offer / video_upgrade_answer | request_id, sdp:string |
| video_upgrade_commit | request_id |
| video_return_audio | request_id |

All include call_id, protocol_version:1; inbound sender_id is server-derived. Existing candidate/ice_candidate continues for the current peer connection. Queue candidates until matching description is applied. Associate any future generation identifier consistently on both endpoints before changing deployed ICE shapes.

Request lifetime30s from server receipt, not reset by duplicates. Rate: at most one pending request per call, no more than4 attempts/minute/participant. Stable account-ID tie-break resolves simultaneous requests; losing attempt is cancelled before SDP allocation. Accept is valid only from the invited peer for that live request. Offer only from winning initiator after accept; answer only from receiver after matching offer; commit only after matching answer. Reject malformed/out-of-order/late/duplicate state effects. Return-to-audio retains voice.

Frontend draft: consent and pending dialogs, cancel/timeout, distinct handlers, disabled video track and rollback/cleanup seam. Relay+peer gate defaults false; production WebRTC upgrade gate also false until camera/ICE/rollback/repeated upgrade/two-endpoint tests pass. No video capture before consent and successful commit. Decline/cancel/failure must not dispose established audio. Do not enable based on UI or fake callbacks.

## 2. Hold and resume

Capability hold_v1. Shape:

```json
{"signal_type":"call_hold","call_id":"c1","protocol_version":1,"held":true,"revision":1}
```

Monotonically increasing revision per participant per call; reject old/equal revisions and nonboolean held. Max4 changes/second, sustained30/minute. State is participant-specific, not carrier hold or conference management. Retain newest state only during the active call; clear at end. Snapshot on reconnect must identify current participant state and revisions; never revive a prior call.

Frontend draft: local hold pauses outgoing microphone and incoming playout while retaining user mute intent. Resume restores according to current mute intent. Without relay support, explicit "On hold locally - peer not notified"; no outgoing hold signal. Peer hold notice appears only with authenticated supported relay. Audio transport remains alive. Local pause is not a claim of a server/carrier hold queue.

## 3. Recording consent, notice and stop

Capability recording_consent_v1 on all participants plus a verified local recorder capable of capturing the required streams. Current client has no verified both-sides call PCM capture. Microphone-only voice recording must not be presented as a call recording.

Proposed signals (not yet live client protocol):

| Type | Fields |
|---|---|
| call_record_request | recording_id, request_id, scope:"local_device", participants:[user IDs], format:"audio", retention:"until_user_deletes" |
| call_record_accept / call_record_decline / call_record_cancel | recording_id, request_id |
| call_record_started | recording_id, request_id |
| call_record_stop | recording_id, reason: user_stop / consent_revoked / participant_changed / call_end / account_change / capture_error / background_restricted |
| call_record_stopped | recording_id, reason |

All include call_id, protocol_version, server-derived sender_id. Consent expires after30s and is per recording attempt, exact membership and purpose. Explicit acceptance from every participant required. No default opt-in, implied consent, silence-as-consent or prior-call consent. Membership change cancels consent/stops capture before a new attempt. Any participant can revoke/stop. Server serializes request/accept/start and denies start without all consent. New capability does not authorize retroactive recording. Rate max2 requests/minute/call, one pending/active recording attempt. Stops must not be throttled away. Keep only necessary consent audit metadata under an agreed retention policy; never store media by default.

Proposed UI: "Ask to record this call? Recording starts only after everyone agrees. Everyone sees a recording indicator and can stop it. Saved privately on this device." Receiver sees recorder identity/purpose/scope with Accept/Decline. Capture zero bytes before valid consent. Persistent visible indicator and stop action in full screen, minimized surface and notifications. Save/discard locally, no auto-upload/share. User-controlled deletion and private storage. Recording jurisdiction requirements remain a product/legal check; this protocol is not legal advice or a guarantee of compliance.

Until consent relay AND both-sides capture AND lifecycle cleanup are verified, Record opens a truthful pending explanation and records nothing. No success toast, fake timer or silent microphone fallback. Proposed protocol must be implemented and tested before advertising support.

## 4. Reactions

Capability reactions_v1. Current draft shape:

```json
{"signal_type":"call_reaction","call_id":"c1","protocol_version":1,"reaction_id":"uuid","emoji":"👍","ttl_ms":4000}
```

Allow only 👍 ❤️ 😂 👏 🎉 👋. TTL1..4000ms, measured at receipt with server-side expiry; do not queue or replay after offline/reconnect. Min1500ms between reactions per sender; cap burst and call total as appropriate. Deduplicate IDs, reject arbitrary markup/text/URLs, wrong peer/call/account and old events. Inbound sender_id is authoritative from server. No persistence into chat history unless separately designed and authorized.

Frontend draft: bounded transient overlay, local-only label when relay unsupported; capability-supported send says "Sent" for enqueued delivery, not "Seen" or "Delivered" without acknowledgements. No read receipt inference. Reduced motion uses a static overlay. At most one current overlay, clear on account/call end. Peer reaction expiry uses validated bounded TTL.

## 5. Add call / group calls

Capability group_calls_v1 is not sufficient by itself: Android must have a real multi-participant media/session implementation. Current single-peer service and contact picker cannot safely add a participant. Do not reuse initiateCall in a way that replaces the existing call.

Proposed authenticated operations, names to finalize with server API:

- group_call_create: parent_call_id, request_id, initial_participant_ids. Returns group_call_id, membership_version, transport mode, media endpoint/short-lived credentials, capabilities.
- group_call_invite: group_call_id, request_id, target_user_id, membership_version. Invite lifetime40s. Decline/cancel/timeout leaves existing call intact.
- group_call_accept / decline / cancel: group_call_id, invite_id, membership_version.
- group_call_membership: group_call_id, membership_version, participants:[{user_id,state}], server-derived event_id.
- group_call_leave / end: explicit actor scope; leaving oneself is distinct from ending for everyone. Only authorized host can remove others/end for all if that product policy exists.

Choose SFU vs mesh explicitly. Supply authenticated TURN/SFU credentials with short expiry, participant/media identity mapping, reconnect semantics and consent/membership notices. Never trust client-supplied role/host. Server verifies invitation rights, blocks/privacy restrictions, capacity, active membership and stale membership_version. Deduplicate request/invite IDs. Proposed invite rate max5/minute/caller, no repeated pending invite to same target, max8 participants until load testing specifies otherwise. Handle busy, blocked, declined, offline, capacity, transport failure and revoked invitation without interrupting existing participants. No autoaccept or unannounced audience expansion.

Frontend until implemented: Add call explains pending multi-call support; current call remains connected. No fake contact picker that silently abandons the current call. Multi-party recording requires fresh consent for new membership; reactions/hold must carry participant identity within group membership scope.

## 6. Calls / Messages / Both bundle application

Local layout selection does NOT require backend. Independent local persisted call_bundle_id and message_bundle_id, same-account scope, atomic Both transaction, rollback on failure. Existing explicitly selected call bundle is preserved when only Messages is chosen. Popup runs for every Apply entry including live preview; cancellation writes nothing. Don't label Messages applied until actual message list/thread/composer/attachment layouts consume the preference.

Optional cross-device sync capability bundle_preferences_v1 (not needed for local-only UI): authenticated account preferences document with version/ETag.

```json
{"version":7,"call_bundle_id":"apple_ios","message_bundle_id":"google_phone_latest"}
```

Update uses expected_version and only the selected fields. Both must be atomic. Server validates IDs against supported allowlist, account ownership and payload bounds. Max10 updates/minute/account; conflict returns current version/document, no silent overwrite. Unknown newer IDs fall back locally without deleting server value. Never sync secrets/private chat content with style preferences. Offline apply can remain local and be labelled not synced; no false cloud success. Account switch restores that account's values rather than carrying another person's styling preference.

## Acceptance gates

Compile actual Android and tests; feature-specific fake transport state tests; camera/mic/route/storage lifecycle and account-change tests; two real endpoints on actual authenticated relay for upgrade, hold notices, reactions and recording consent/revocation. Verify no audio/video recording before consent, no preaccept video transmission, no stale effects after call end/account switch, no accidental participant expansion, no audio disconnect on optional-feature failure. Capture every bundle/state at small widths and large fonts, light/dark/custom theme, TalkBack and reduced motion. Current drafts and synthetic fixtures are not these proofs. No release/APK until green gates and owner approval.
