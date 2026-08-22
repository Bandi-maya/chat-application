# Chaty Database Access Matrix

Audit date: 2026-08-22

Source of truth: connected production Supabase project `dntnxeanubswyswahdnj`. Every current `public` table has RLS enabled. This matrix documents the intended caller boundary and the current direct-table policy surface; mutation RPCs still require independent function-by-function review.

| Table | Access class | Direct SELECT | Direct INSERT | Direct UPDATE | Direct DELETE | Primary authorization |
|---|---|---|---|---|---|---|
| `auto_reply_log` | own automation + participant | owner/member | owner/member | owner/member | owner/member | related rule belongs to `auth.uid()` + conversation membership |
| `auto_reply_rules` | own row | own | own | own | own | `user_id = auth.uid()` |
| `blocked_users` | own row | own | own | own | own | blocker is `auth.uid()` |
| `call_ice_candidates` | call participants | participant | sender + participant | none direct | none direct | caller/callee of referenced call; sender is `auth.uid()` |
| `call_sessions` | call participants | caller/callee | caller only | caller/callee | none direct | mutually accepted contact + conversation membership on creation; guarded state transitions |
| `contact_connections` | relationship participants | participant | RPC/internal | RPC/internal | RPC/internal | either side of relationship |
| `contact_presence_visibility` | intended viewer projection | viewer | internal | internal | internal | viewer-scoped projection |
| `contact_privacy_overrides` | own row | own | own | own | own | owner identity |
| `conversation_key_envelopes` | intended E2EE recipient | recipient | conversation member for valid recipient/key | none direct | none direct | current conversation membership + recipient key ownership/non-revocation |
| `conversation_key_versions` | conversation participants | member | member/creator | none direct | none direct | conversation membership; creator is `auth.uid()` |
| `conversation_members` | conversation participants | member | RPC/internal | RPC/internal | RPC/internal | current membership |
| `conversations` | conversation participants | member | RPC/internal | RPC/internal | RPC/internal | current membership |
| `linked_devices` | own device rows | own | own | own | own | account owner |
| `mass_message_collections` | own row | own | own | own | own | `user_id = auth.uid()` |
| `message_edit_history` | conversation participants | member | RPC/internal | none direct | none direct | message conversation membership |
| `message_reactions` | conversation participants | member | self + member | none direct | self | message conversation membership + reacting user identity |
| `message_receipts` | conversation participants | member | self | self | none direct | message conversation membership + receipt owner identity |
| `message_user_state` | own per-message state | own | self + member | self | none direct | `user_id = auth.uid()` + message conversation membership |
| `messages` | conversation participants | member | RPC only | RPC only | RPC only | conversation membership; mutations through validated functions |
| `poll_options` | conversation participants | member | RPC/internal | RPC/internal | RPC/internal | parent poll/message conversation membership |
| `poll_votes` | conversation participants | member | RPC | RPC | RPC | parent poll/message conversation membership + voter rules |
| `polls` | conversation participants | member | RPC | RPC | RPC | conversation membership |
| `profiles` | own direct row; discovery via constrained RPC | self | provisioning/internal | self | internal/account deletion flow | profile owner; discovery must use narrow capability rather than broad table read |
| `quick_reply_templates` | own row | own | own | own | own | `user_id = auth.uid()` |
| `reports` | reporter/member write | no direct client read | self + conversation member | none direct | none direct | reporter identity + conversation membership |
| `scheduled_messages` | own row | own | own | own | own | `user_id = auth.uid()`; server execution path separately controlled |
| `status_updates` | owner/shared-conversation viewers | scoped viewer | owner | owner | owner | owner or shared-conversation visibility; expiry enforced |
| `status_views` | viewer + status owner | related user | viewer if status visible | none direct | none direct | viewer identity + current status visibility |
| `task_activity` | conversation participants | member | RPC/internal | none direct | none direct | task conversation membership |
| `task_assignees` | conversation participants | member | RPC/internal | RPC/internal | RPC/internal | task conversation membership |
| `tasks` | conversation participants | member | RPC | RPC | RPC | conversation membership + task mutation validation |
| `typing_states` | conversation participants | member | self + member | self + member | self | ephemeral user identity + conversation membership |
| `user_e2ee_keys` | authenticated public-key discovery; own mutation | authenticated key discovery | own | own revocation | none direct | public key material readable as protocol requires; only owner can create/revoke |
| `user_feature_settings` | own row | own | own | own | own | `user_id = auth.uid()` |

## Direct-table rules

- A missing direct mutation policy is intentional where the product uses a guarded RPC to maintain a multi-row invariant or validate authorization/state transitions.
- `authenticated` is not itself an authorization condition. Each private row is bound to ownership, membership, relationship, recipient identity, or a validated projection.
- Client-supplied UUIDs remain hostile input even when the UI sourced them from a previous response.
- E2EE key material that is intentionally discoverable is limited to public protocol material; private identity/session secrets must never be stored in these public tables.

## Security-definer RPC review classes

The production advisor reports client-executable `SECURITY DEFINER` functions. These are not automatically vulnerabilities: several are intentional capability functions that must bypass restrictive direct-table policies while enforcing their own checks. They are classified for individual review rather than mass conversion.

### Call state capabilities

- `accept_call_session`
- `decline_call_session`
- `end_call_session`

Verified to bind the mutation to the caller/callee identity and valid call state with a safe search path. Retain as intentional capability functions unless later tests contradict the implementation.

### Conversation/group capabilities

- `create_direct_conversation`
- `create_group_conversation`
- `add_group_member`
- `leave_group_conversation`
- `update_group_title`
- `get_my_conversations`
- `get_conversation_members`
- `is_conversation_member`

Must be verified for membership/admin-role checks, self-add/self-remove rules, duplicate/idempotent creation and blocked/contact policy interactions.

### Messaging/state capabilities

- `send_message`
- `get_conversation_messages`
- `edit_chat_message`
- `delete_chat_message`
- `mark_conversation_read`
- `set_conversation_draft`
- `set_conversation_state`
- `set_message_user_state`
- `toggle_message_reaction`

These remain part of the plaintext-era messaging architecture. They require authorization review now and replacement/versioning during the E2EE ciphertext migration.

### Tasks/polls/automation capabilities

- `create_chat_task`
- `update_chat_task`
- `update_task_status`
- `create_poll`
- `get_poll`
- `vote_poll`
- `schedule_message`
- `cancel_scheduled_message`
- `mass_send_message`

Verify conversation membership, assignee membership, option ownership, idempotency, schedule ownership/time bounds and recipient authorization.

### Privacy/discovery capabilities

- `block_user`
- `unblock_user`
- `get_my_blocked_users`
- `search_profiles`
- `is_username_available`
- `mark_status_viewed`
- `delete_status_update`

`is_username_available` is intentionally callable pre-authentication for signup UX only if it returns a minimal boolean and cannot expose profile rows. Its anonymous `SECURITY DEFINER` warning remains documented until a dedicated pre-auth service or equally narrow reviewed function is adopted.

## Adversarial verification matrix

For each object class, run tests as A (owner), B (authorized participant) and C (unrelated authenticated attacker):

- C cannot select/insert/update/delete conversation/message/call/task/status data belonging to A/B.
- B cannot manufacture E2EE envelopes for an unrelated user/key.
- removed members lose future conversation/key access according to product policy.
- revoked device keys cannot receive newly generated envelopes.
- call ICE candidate sender must equal the authenticated participant.
- report/task/poll/message UUID substitution cannot cross conversation boundaries.
- profile discovery returns only the deliberately exposed projection, never private profile/account fields.

## Current advisor state

As of 2026-08-22, the previously reported RLS init-plan warnings, missing foreign-key indexes and exact duplicate-index warnings were repaired. Remaining security advisor warnings are primarily the intentional-but-required `SECURITY DEFINER` review set plus Supabase leaked-password protection being disabled.
