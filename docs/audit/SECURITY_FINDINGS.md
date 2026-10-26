# Security Findings

Initial source review: 27 Oct 2026, 01:43 IST. Build launch update: 01:49; runtime compile failure update: 01:54. Source review and unit results are not emulator acceptance.

ISLAND-01 destructive original-notification behavior current-source confirmed; preserve-original source check passes, Android proof owed. Production manifest has allowBackup=false/fullBackupContent=false; inspect exported components and runtime grants rather than infer blanket safety.

Review of the660-file Next5 source confirms: plaintext token stores and exported deep-link filters with no handler remain source-confirmed. Device-global lock scope remains unresolved; no exploit/runtime claim. Token migration HOLD until atomicity/crash/account-switch proven. Global FLAG_SECURE not approved, would block requested screenshot audit and user bug reports; scoped recents/sensitive-screen behavior needs decision and testing.

Use MASVS groups storage/crypto/auth/network/platform/code/resilience/privacy. Verify no secret/private logs, strict TLS, scoped bridge and permissions, locked/view-once overlay redaction, logout/account cache/lease revocation, invalid backup/import handling. Never weaken policy to make media/overlay work. Keep existing backend/native crypto. A checklist or unit pass is not certification. https://mas.owasp.org/MASVS/


## Reconciled current-source findings
Review of the660-file Next5 source confirms: token material remains in plaintext SharedPreferences/DataStore; no migrating token store is wired. Exported login/reset deep-link filters remain without corresponding handler. Supabase-related remote preferences/realtime are still wired in ServiceLocator; runtime activation unknown. These are source facts, not proof of exploit or active traffic. Preserve custom backend; do not silently remove legacy wiring without reachability/behavior proof. Atomic migration and scoped screenshot/recents policy remain HOLD.
