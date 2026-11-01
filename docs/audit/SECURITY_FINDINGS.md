# Security Findings

Initial source review: 27 Oct 2026, 01:43 IST. Build launch update: 01:49; runtime compile failure update: 01:54. Source review and unit results are not emulator acceptance.

ISLAND-01 destructive original-notification behavior current-source confirmed; preserve-original source check passes, Android proof owed. Production manifest has allowBackup=false/fullBackupContent=false; inspect exported components and runtime grants rather than infer blanket safety.

Review of the660-file Next5 source confirms: plaintext token stores and exported deep-link filters with no handler remain source-confirmed. Device-global lock scope remains unresolved; no exploit/runtime claim. Token migration HOLD until atomicity/crash/account-switch proven. Global FLAG_SECURE not approved, would block requested screenshot audit and user bug reports; scoped recents/sensitive-screen behavior needs decision and testing.

Use MASVS groups storage/crypto/auth/network/platform/code/resilience/privacy. Verify no secret/private logs, strict TLS, scoped bridge and permissions, locked/view-once overlay redaction, logout/account cache/lease revocation, invalid backup/import handling. Never weaken policy to make media/overlay work. Keep existing backend/native crypto. A checklist or unit pass is not certification. https://mas.owasp.org/MASVS/


## Reconciled current-source findings
Review of the660-file Next5 source confirms: token material remains in plaintext SharedPreferences/DataStore; no migrating token store is wired. Exported login/reset deep-link filters remain without corresponding handler. Supabase-related remote preferences/realtime are still wired in ServiceLocator; runtime activation unknown. These are source facts, not proof of exploit or active traffic. Preserve custom backend; do not silently remove legacy wiring without reachability/behavior proof. Atomic migration and scoped screenshot/recents policy remain HOLD.


## Current source reconciliation — 2026-11-01

The initial 27 Oct review is a historical baseline and its plaintext-token statements are superseded by the current Kotlin source: `EncryptedApiSessionStore` is Keystore-backed and the GoTrue session path uses `EncryptedGoTrueSessionStorage`. A durable GoTrue sign-out tombstone was added on 1 Nov to prevent legacy DataStore session resurrection after interrupted cleanup. This does not remove legacy DataStore migration code; it prevents legacy values from overriding an explicit signed-out state.

Local secret phrase verification currently shares the failed-attempt cooldown and constant-time comparison path. Protected-resource authorization is explicit: general actions without a configured app lock may remain available by design; protected conversations still require setup/authentication. Call media permission grants are rechecked against Android after the permission dialog.

The active REST message route is still not end-to-end encrypted by MLS, and standard cloud attachments remain server-readable. Legacy Supabase auth/data/realtime/RPC components still coexist with the custom API plane. These are material architecture boundaries, not claims of a completed E2EE migration. See `docs/audit/KOTLIN_QUALITY_SCORECARD.md` for current status and evidence limits.
