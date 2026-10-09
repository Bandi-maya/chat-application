# Chaty Security and Privacy Requirements

## 1. Principles
- Server-side authorization is authoritative. Hiding a button is not access control.
- MLS encrypted conversations fail closed if group/device/epoch state is invalid.
- Never store MLS plaintext in the backend for encrypted messages.
- Never claim a privacy effect unless it is implemented and verified.
- Logs, crash reports, screenshots and template files must not contain credentials, tokens, private keys or message bodies.
- Treat third-party APK behavior as a reference, not as proof that a feature is safe or suitable to replicate.

## 2. MLS message handling
- All new/edit operations for MLS rows must use the MLS payload format and approved RPCs.
- Edit payload must be encrypted for the current conversation group and correct epoch/device.
- Remove direct table update fallback that can write plaintext for MLS rows.
- Check sender ownership and group/device/epoch authorization on server.
- On any mismatch or RPC error, retain old server content and roll back optimistic UI.
- Add tests that inspect server records for ciphertext-only content and verify recipient decryption.

## 3. Authentication/session lifecycle
- Scope async work, caches, Realtime subscriptions and repositories to the authenticated user.
- Clear/detach user-specific state on sign-out and account switch.
- Apply rate limiting and abuse protections to username/login endpoints where relevant.
- Avoid wildcard CORS for sensitive endpoints unless explicitly justified by the endpoint's threat model.
- Secrets live in secret storage/CI secret manager, never in templates or committed config.

## 4. RLS and migrations
- Every table and RPC must have explicit authorization and grants.
- Security-definer functions require safe search_path, validation, ownership checks and least privilege.
- Test unauthorized cross-user reads/writes and removed-device access.
- Review migration order, idempotency and rollback strategy.
- Do not bypass a failed hosted Auth preflight to produce a release build.

## 5. Privacy feature mapping
- For every privacy control document: user-facing promise, client behavior, server enforcement, supported platforms, limitations, test.
- Do not fake read receipts or presence.
- Do not add stealth interception, unauthorized content recovery, or controls that defeat another user's access/privacy decisions.
- Local hiding is not equivalent to server deletion or access revocation; labels must explain the difference.
- Status audience, expiry and view tracking must be backend-backed if presented as guarantees.

## 6. Device revocation
- Server confirms revocation before UI success.
- Define whether revocation removes MLS membership, invalidates credentials, or requires re-linking.
- Ensure revoked devices cannot continue sending as active members.
- Test stale clients, offline devices and cross-device updates.

## 7. Template import/export
- Strict schema allowlist and maximum file size.
- Reject unexpected fields, URLs or payloads that could cause unsafe resource loading.
- Do not import executable code or arbitrary filesystem paths.
- Never export identity, account/session data, private keys, message history or media.
- Atomic apply and rollback on validation/persistence failure.

## 8. Observability
Use non-sensitive event identifiers and redacted errors. Never log message plaintext, auth tokens, encryption material or private media URLs. Audit analytics and crash reporting against this rule.
