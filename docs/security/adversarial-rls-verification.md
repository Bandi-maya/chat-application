# Chaty Adversarial RLS Verification

Date: 2026-08-22

Scope: live Supabase production schema backing the `production-hardening-2026-08-22` branch.

## Method

The checks in this document were executed against the live project using the Postgres `authenticated` role with an explicit JWT subject (`request.jwt.claim.sub`). Test identities are intentionally not recorded in source control.

All mutation probes were wrapped in a database transaction and ended with `ROLLBACK`. Temporary call rows, ICE candidates, privacy overrides, block rows, test public keys, E2EE bundles, one-time prekeys, and test messages therefore did not persist.

Positive controls were included so a negative result could not be mistaken for a broken fixture or a blanket permission failure.

## Result

**38 / 38 assertions passed.**

### Identity and canonical profile isolation — 3 / 3

- simulated JWT resolves to the intended `auth.uid()`
- signed-in user can read their own canonical profile row
- signed-in user cannot directly read another user's canonical profile row

### Status privacy and expiry — 2 / 2

- an unexpired status belonging to a user who shares a conversation is visible to the intended peer
- an expired status is not returned through RLS

### Call sessions and ICE — 9 / 9

- caller can read their call session
- callee can read their call session
- third authenticated user cannot read the call session
- caller can read authorized ICE candidates
- callee can read authorized ICE candidates
- third authenticated user cannot read ICE candidates
- caller identity forgery on INSERT is rejected
- creating a call directly in an already-accepted/connected state is rejected
- ICE sender identity forgery is rejected

### Privacy, blocking, and legacy public-key ownership — 9 / 9

- owner can create/read their own contact privacy override
- another user cannot read that private override
- forging another user's privacy-override ownership is rejected
- blocker can create/read their own block row
- another user cannot read that private block row
- forging another user's blocker identity is rejected
- user can register their own legacy E2EE public key
- authenticated peers can read legacy public keys as intentionally designed
- registering a legacy public key under another user's identity is rejected

### Per-device E2EE bundle and prekey ownership — 6 / 6

Fixtures used real, active linked-device foreign keys so an ownership denial could not be confused with an invalid device reference.

- user can register a bundle for their own active linked device
- conversation peer can read the active public device bundle
- peer cannot enumerate another user's one-time prekeys directly
- user can register their own one-time prekey
- forging another user's device bundle is rejected
- forging another user's one-time prekey is rejected

### Conversation and message BOLA/IDOR — 9 / 9

A direct conversation with two members and a third authenticated non-member was used.

- member can read the conversation
- member can read conversation membership
- member can read a message in the conversation
- member `send_message` RPC positive control succeeds
- non-member cannot read the conversation
- non-member cannot enumerate conversation membership
- non-member cannot read the message
- non-member `send_message` RPC is rejected
- non-member `get_conversation_messages` RPC is rejected

## Security conclusions

The tested high-risk ownership boundaries currently enforce the intended model for canonical profiles, statuses, direct conversations/messages, call sessions, ICE candidates, contact privacy, blocking, legacy E2EE public keys, and the new per-device E2EE bundle/prekey tables.

This verification does **not** prove the application is fully secure or fully E2EE. In particular:

- the client Signal-style ratchet/session implementation is still not selected/integrated;
- encrypted attachment key wrapping is still incomplete;
- physical-device WebRTC/TURN relay behavior must still be tested across different networks;
- project-level Auth controls such as leaked-password protection require separate verification/configuration;
- new tables/RPCs must receive the same adversarial treatment when introduced.

## Regression rule

Any migration that changes RLS, a `SECURITY DEFINER` RPC, conversation membership semantics, call authorization, status visibility, linked-device ownership, or encryption key/ciphertext access must re-run the applicable positive and negative controls from this matrix before release.
