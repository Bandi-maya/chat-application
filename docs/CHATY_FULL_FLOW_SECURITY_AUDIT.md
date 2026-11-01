# Chaty Full-Flow, Validation & Security Boundary Audit

**Branch:** `kotlin`  
**Audit baseline:** current `kotlin` tree at commit `999c6395409e6ff28fd19295f4a7e9a1143fe789` (the audit report itself is committed immediately afterward).  
**Scope:** Android/Kotlin app, native MLS/OpenMLS layer, custom API client, legacy Supabase-backed services, media/file paths, calls, permissions, local storage, and CI/release configuration.

> This is a source-level audit, not a claim that every runtime path has been executed. Findings below are grounded in the files and code paths listed. Device tests, backend source review, dependency/SBOM scans, APK static/dynamic analysis, and independent penetration testing are still required. “No bugs” cannot be guaranteed.

## 1. Executive risk register

| ID | Severity | Finding | Evidence | Required action |
|---|---|---|---|---|
| SEC-001 | CRITICAL | Auth tokens are persisted without app-layer encryption in two auth planes. | `data/auth/SessionStore.kt` stores access/refresh tokens in Preferences DataStore. `data/api/ApiBackendService.kt` stores `api_hub_token` in ordinary SharedPreferences. | Migrate both to one Keystore-backed encrypted token store; versioned migration, atomic writes, logout/rotation cleanup, backup exclusion and tests. Never log tokens. |
| SEC-002 | CRITICAL | The custom API auth plane and legacy Supabase auth/data plane coexist; not all features use the declared custom backend. | `ServiceLocator.kt` constructs SupabaseAuthClient, PostgrestQueryClient, PostgrestRpcClient, SupabaseRealtimeChannel, SupabaseStorageClient and SupabaseRemoteSettingsStore alongside ApiBackendService. `MlsE2eeService` takes `SupabaseRpc`; ServiceLocator passes `rpcClient`. | Establish one authoritative backend contract per feature; migrate or explicitly document each remaining Supabase dependency. Do not state the whole app is custom-backend-only. |
| SEC-003 | CRITICAL | Attachment key derivation exists, but no verified end-to-end media encryption pipeline is demonstrated by the audited upload binding. | `MlsE2eeService.exportAttachmentSecret()` is documented as phase 2; `ChatAttachmentsBinding.kt` reads media into plaintext bytes and passes them to `ChatMediaService.uploadBytes`; custom API multipart upload is used. | Encrypt each media stream client-side with a unique random DEK; wrap DEK with MLS exporter-derived key; authenticate conversation/message/attachment metadata as AAD; upload ciphertext only; decrypt only after authenticated membership; include encrypted thumbnails and metadata. |
| SEC-004 | CRITICAL | Video and file sends read entire selected files into RAM. This prevents reliable very-large video support and creates memory/DoS risk. | `ui/ChatAttachmentsBinding.kt`: `read(uri)` calls `InputStream.readBytes()`; upload adapter falls back to file `readBytes()` and `uploadBytes`. | Replace with URI-backed streaming source, bounded buffers, streaming multipart, hard byte/time limits, cancellation, resumable chunk uploads and persistent transfer journal. Never allocate a full video ByteArray. |
| SEC-005 | HIGH | Document picker copies full selected files to cache and stores raw paths; temporary files are not tied to a clear lifecycle in this flow. | `ui/media/CustomFilePickerScreen.kt`: OpenDocument(s) copies each URI stream into `cacheDir`; descriptor path is that copy. | Retain content URI + persistable grant where supported; avoid copy for send; for transformations use bounded temp files and deterministic cleanup on cancel/send/logout/startup. |
| SEC-006 | HIGH | Native media library scans app files/cache and reads headers synchronously in `refreshLibrary()`; this can block UI and expose internal cache/temp files as user media. | `ui/media/MediaLibraryService.kt`; wired with `filesDir` and `cacheDir` in `ChatAttachmentsBinding.kt`. | Move scanning to IO, use explicit app-owned media directories, exclude internal caches/partial/temp/encrypted blobs, use paging and cancellation. |
| SEC-007 | HIGH | Screen sharing has a consent UI path but the complete Android 14+ MediaProjection lifecycle/foreground-service contract needs verification. | `WebRtcCallMediaController.kt` owns `ScreenCapturerAndroid`; manifest declares camera/microphone/remoteMessaging FGS permissions, but the audited manifest does not establish a dedicated mediaProjection FGS service/type. | Add a dedicated, user-visible projection foreground-service lifecycle where required by target SDK; declare correct permission/type; stop on projection callback, call end, account switch and process teardown; test Android 14–16. |
| SEC-008 | HIGH | Sensitive permissions are broad and include location and overlay access in the base manifest. | `app/src/main/AndroidManifest.xml`: SYSTEM_ALERT_WINDOW, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, READ_CONTACTS, READ_MEDIA_* and full-screen intent. | Create a permission-to-feature inventory; request just-in-time; use Photo Picker/SAF instead of broad media access; location only for explicit share-location; overlay/full-screen intent only when justified and policy-compliant. |
| SEC-009 | HIGH | MLS group/device enrollment still calls Supabase RPC, so E2EE setup cannot be considered migrated to the custom backend. | `MlsE2eeService.kt` constructor depends on `SupabaseRpc`; `ServiceLocator.kt` injects `rpcClient`. | Define authenticated custom API endpoints for device registration, key-package claim/replenishment, group state, commit/welcome exchange, membership and epoch reconciliation; validate user/device/conversation authorization server-side. |
| SEC-010 | HIGH | Remote media path can return a signed HTTPS URL for restricted/view-once media. This is transport TLS, not MLS application-layer E2EE. | `AttachmentSourceResolver.resolveInner()` returns `ResolvedSource.Remote(url)` for restricted/view-once attachments. | If strict E2EE is required for all media, return ciphertext object references only and decrypt in client memory; never hand a plaintext CDN URL to the player. Enforce short TTL and no-store as defense in depth. |
| SEC-011 | HIGH | Exported/decrypted media must be treated as a deliberate plaintext boundary. | `AttachmentFileCache` decrypts to leases/exports and dedicated provider; external apps can retain bytes already read even after URI grant revocation. | Keep explicit user confirmation, shortest lifetime, URI grants, secure cleanup, no backup, no implicit share; clearly explain that external apps can copy exported media. |
| SEC-012 | HIGH | Custom API profile/session JSON and access token are persisted together in ordinary SharedPreferences. | `ApiBackendService.kt` local `ApiSessionStore` implementation. | Store token separately in Keystore-encrypted storage; keep only non-secret profile cache in ordinary preferences; validate JWT expiry/audience/issuer server-side. |
| SEC-013 | MEDIUM | Lock secret phrase verification does not use the failed-attempt cooldown path used by PIN/pattern/password verification. | `LocalLockService.verifySecretPhrase()` does not call `getRemainingCooldownSeconds` or `recordFailedAttempt`. | Apply shared attempt throttling and lockout to every local secret verifier; protect counters against reset/tampering; use device credential/biometric for high-risk actions. |
| SEC-014 | MEDIUM | General protected action gate allows access when no lock credential is configured. This may be intentional UX, but is unsafe if a feature assumes the gate itself always authenticates. | `ProtectedResourceGate.authorizeGeneralAction()` returns true when `isMethodConfigured` is false. | Make callers pass an explicit policy; when app lock is enabled, fail closed or require credential setup before protected actions. Test all call sites. |
| SEC-015 | MEDIUM | Screen capture resource ownership is incomplete: MediaProjection callback only has a comment; screen helper/source/track/permission intent cleanup and restoration paths need a single owner. | `WebRtcCallMediaController.startScreenShare()/stopScreenShare()/disposeInternal()`. | Retain and dispose screen VideoTrack, VideoSource, SurfaceTextureHelper; restore camera on projection `onStop`; clear permission Intent; rollback if sender replacement fails. |
| SEC-016 | MEDIUM | Debug fixtures are now gated by BuildConfig.DEBUG, but demo mode, debug manifest overlays and build variants still need a complete release-only audit. | `MediaLibraryService.kt`, `app/src/debug/AndroidManifest.xml`, `ReleaseFeatures.kt`, CI overlays. | Add release artifact tests asserting no demo users/messages/media, debug activities, debug endpoints, test keys or debug-only exported components. |
| SEC-017 | MEDIUM | Auth tokens and app-local lock secrets have different protection requirements; do not treat app PIN hash as a substitute for OS device authentication. | `LocalLockService.kt`, `SecureKeyValueStoreImpl.kt`, session stores. | Use Android Keystore non-exportable keys and BiometricPrompt/device credential for key-unwrapping/high-risk operations; use app lock only as an additional UI barrier. |
| SEC-018 | MEDIUM | Call/recording/ICE custom-backend server handlers are not included in the supplied backend source. | `backend/routes/chaty-webrtc-ice.js` exists; README documents mount and socket event contract; actual server app is absent. | Review real backend auth middleware, room membership, rate limits, origin/TLS, payload schemas, replay protection, TURN secrets, recording event and message idempotency. |

## 2. Three-layer security architecture

This is a separation of data and authority, not three UI screens.

### Layer A — User-visible layer
- Profile, contacts the user chose to share, conversations, visible attachment metadata, preferences and on-device decrypted content.
- The user controls what is shared, who receives it, visibility, retention and export.
- UI must not expose raw access tokens, MLS private keys, local lock hashes, SQLCipher keys, signed storage URLs, internal device identifiers or diagnostic secrets.
- Sensitive actions require contextual confirmation and clear permission purpose.

### Layer B — Chaty application-private layer
- App-private process, repositories, services, encrypted local databases, encrypted media cache, outbox, transfer journal and per-account state.
- Apply authorization in service/repository boundaries, not only Compose buttons.
- Enforce tenant + user + auth generation + conversation membership on every read/write, retry, export, background job and notification action.
- Persist only what is needed; encrypt at rest; keep decrypted temporary media short-lived; exclude private data from logs, crash breadcrumbs and backups.
- Backend can see account/routing data and ciphertext transport metadata unless protocol hides it; developers must not have a hidden decryption capability.

### Layer C — Android OS / hardware-backed security
- Android Keystore keys, StrongBox when available, hardware-backed authentication, BiometricPrompt/device credential, app sandbox, scoped storage, permission manager, SELinux and verified boot.
- Private key material must be non-exportable where possible; use a Keystore AES-GCM wrapping key to encrypt tokens and local database keys.
- OpenMLS signing/ratchet secrets remain inside the app's crypto boundary, protected by SQLCipher + a Keystore-wrapped database key. Keystore protects wrapping keys; it does not automatically make all app memory hardware-isolated.
- Android does not provide a general-purpose “OS-only private folder” for arbitrary app content. Use Keystore and app-private storage; do not attempt hidden storage, root-only tricks or Play Protect evasion.
- If the device lacks StrongBox, use Android Keystore software-backed fallback with a clear security posture; do not fail into plaintext.

## 3. Complete feature-flow inventory and required validations

For each flow, validate at UI entry, domain/service boundary, backend authorization, persistence, account transition, cancellation, offline/retry, and final user-visible state.

| Feature / flow | Entry points and state transitions | Required validations and failure paths |
|---|---|---|
| Install / first launch | launcher → bootstrap → config → auth restore → home | Verify package signature, release variant, config URL/TLS, migration state, device integrity signals, no demo fixtures in release, no token logs. |
| Registration / login / OTP / reset | auth screens → REST or GoTrue auth → session store → account scope | Normalize and validate email/phone/username; server-side rate limits; password policy; OTP expiry/attempts; JWT issuer/audience/expiry; token rotation; encrypted persistence; session generation bump. |
| Tenant / organization switching | org picker → config update → clients reconnect | Validate tenant membership; atomic scope transition; cancel in-flight work; invalidate caches, call, transfer, pending actions; prevent old tenant responses from mutating new state. |
| Logout / account deletion | settings/profile → remote revoke → local cleanup | Revoke refresh/session tokens; cancel workers/sockets/calls; clear notifications and app switcher snapshots; purge scoped keys/cache/outbox/MLS identity; deletion must be idempotent and report remote deletion separately from local purge. |
| Home / chat list / search | navigation → repositories → paging/search | Validate cursor, query length, sort keys, visibility/membership; avoid unbounded list; offline cache scope; stale response suppression; empty/loading/error states. |
| One-to-one chat | open conversation → membership → history → send | Verify membership for every message; MLS readiness and epoch; message ID idempotency; sender identity; timestamp bounds; deduplicate realtime echo; no plaintext fallback; revoke on membership removal. |
| Group chat / membership | create/join/invite/remove/leave → MLS commit/welcome | Verify creator/admin role; invite expiry; key-package uniqueness; device ownership; epoch monotonicity; remove member cryptographically; reject stale commit/replay; handle concurrent commits and fork recovery. |
| Text/edit/delete/reply/forward/reaction | composer/action menu → outbox → backend → realtime | Length/type/schema validation; authorization against source message; immutable IDs; edit/delete window policy; forward provenance; reaction allowlist; dedupe and conflict resolution; encrypt every payload. |
| Delivery/read/typing/recording/presence | socket publish → server fanout → UI | Authenticated socket identity, conversation membership, per-user privacy preferences, rate limits, payload size/schema, sequence ordering, expiry, no sensitive content in notification previews. |
| Photos / videos / documents / audio | attachment sheet → picker → review → upload → message → receiver playback | Validate content URI, MIME by magic bytes, actual size, duration, file count, path traversal, decompression bombs, cancellation, URI permission lifetime, streaming, encryption, integrity, thumbnail privacy, retry/resume. |
| Long video / 100GB P2P | file URI → manifest → invite → consent → DataChannel → chunk ledger → verify | Long-safe streaming; 64-bit size; quota/free-space; safe filename; chunk bounds; nonce uniqueness; AES-GCM tag per chunk; SHA-256 final verification; expiry; replay/reorder/duplicate chunk rejection; sender/receiver identity; backpressure; durable sender journal; resume after process death; no RAM buffering. |
| View-once / restricted media | message metadata → open → timed display → revoke | Server-trusted policy; one-time open token; no local cache, export, preview, screenshot where OS allows restriction; no notification thumbnail; expiry; race-safe revocation; disclose screenshots cannot be perfectly prevented. |
| Calls / voice / video | incoming/outgoing → answer/decline → ICE → connected → end | Authenticated call identity, valid state machine, permission before capture, duplicate signal dedupe, ICE candidate queueing, TURN expiry, call ID/account scope, timeout, reconnection, no stale callback, audio route, privacy indicator, release tracks. |
| Screen sharing | active accepted video call → OS consent → projection FGS → capture → stop/revoke | Connected video call only; user consent result; Android version FGS type; capture indicator; app/window scope if supported; stop on revocation, call end, account switch, service death; restore camera; clear token and dispose resources. |
| Camera / lenses / stories / status | camera permission → capture/import → preview/edit → publish → viewer | Permission just-in-time; capture lifecycle; metadata/EXIF stripping policy; content moderation if required; upload encryption; audience/privacy; expiry; view count dedupe; no accidental public audience. |
| Contacts / location sharing | user action → permission → OS picker/map → send | Contacts picker first; request broad read only if genuinely required; location only after explicit action; coarse/fine choice; coordinate bounds; accuracy/time; preview recipient; no background tracking by default. |
| Notifications / Dynamic Island / overlays | FCM/socket → notification → tap action / overlay | Notification content redaction while locked; validate pending intent/call ID; immutable PendingIntent; exported receiver checks; overlay permission explanation; no private text in lockscreen; clear on logout. |
| App lock / vault / biometric | foreground/background → lock gate → biometric/PIN/pattern → unlock | Lockout on every secret verifier; secure random salt; constant-time compare; timeout with monotonic clock; invalidate on background/account switch; credential setup cannot silently bypass; biometric/device credential policy. |
| Settings / theme / templates / customization | settings control → state → persistence → consumer | Every visible control maps to real persisted behavior; type/range/enum validation; role-based access; reset/undo; migration; no mock-only actions; account-scoped values; survive process restart. |
| Payments / subscription / organization | plan UI → create order → provider callback → entitlement | Server-authoritative amount/currency/plan; signed callback verification; idempotency; replay protection; tenant/user authorization; no client-side entitlement grants. |
| Exports / backups / sharing | user export → provider URI → external app | Explicit consent; no raw paths; URI grant TTL; MIME validation; no automatic cloud backup of keys/plaintext; revoke future opens; explain external copies cannot be recalled. |
| Background jobs / sync / outbox | app lifecycle/network → retry queue → backend | Idempotency key; bounded exponential backoff + jitter; max retry/expiry; account epoch; cancellation; WorkManager constraints; battery/network quotas; no duplicate send; secure pending payload storage. |
| Debug / logs / diagnostics | debug toggles → logcat/crash analytics | No tokens, message text, keys, media URLs, precise location, contact data or decrypted payloads; debug-only tools not in release; opt-in redacted diagnostics. |
| Upgrade / migration / reinstall | app update → schema migration → key restore | Atomic migration; backup/restore policy; rollback; key invalidation handling; corrupted DB recovery; never silently regenerate keys and lose access to old MLS history. |

## 4. Component-level validation contract

Apply these contracts to every interactive component, not only screens:

1. **Input:** type, length, range, format, normalization, allowed characters, MIME/magic, URI scheme, size and nullability.
2. **Identity:** authenticated user, tenant, device, conversation membership, role, account generation and ownership.
3. **Authorization:** feature entitlement, privacy setting, resource policy, view-once/restricted state, lock/biometric requirement.
4. **State machine:** allowed current state → requested next state; reject duplicate, stale, out-of-order and terminal transitions.
5. **Concurrency:** mutex/atomic update, request dedupe, idempotency, cancellation, stale callback suppression and bounded queues.
6. **Network:** TLS, host allowlist, redirect policy, request timeout, response size/schema, retry policy, rate limit, server error mapping.
7. **Storage:** encryption, account namespace, atomic write, integrity/authentication, quota, retention, deletion and backup exclusion.
8. **Lifecycle:** rotation, process death, background/foreground, permission revocation, app update, logout and account switch.
9. **Output:** escape untrusted text, safe URL opening, no path disclosure, no secret logging, truthful progress and accessible error states.
10. **Tests:** valid path, invalid input, unauthorized actor, offline, timeout, duplicate, replay, cancellation, process death, account switch, corrupted storage and accessibility.

## 5. Play Store / Play Protect release checklist

- Build a release-only APK/AAB from a clean CI runner and compare artifact hashes.
- Use a stable upload/release signing key; verify certificate continuity and Play App Signing setup.
- Generate an SBOM and scan all Gradle, Rust, native, WebRTC, ML, Firebase and transitive dependencies for known vulnerabilities and provenance.
- Review manifest merged permissions and exported components for the release variant; remove permissions not required by an enabled user-facing feature.
- Check cleartext traffic, TLS hostname validation, redirects, WebView origins, deep links and intent filters.
- Verify no dynamic code loading, hidden executable downloads, suspicious native code, unnecessary accessibility/overlay behavior or deceptive permission prompts.
- Run Play Integrity / Play Console policy checks where appropriate; use Google's false-positive review process for a confirmed false positive.
- Never hide the package identity, disable Play Protect, or obfuscate behavior to bypass scanning.

## 6. Test plan and release gates

### P0 — must pass before any production APK
- Encrypt and migrate both auth token stores; test process restart, logout, rotation, account switch and migration rollback.
- Remove full-file ByteArray reads from video/document paths; verify streaming and memory ceiling on large fixtures.
- Decide and implement the custom-backend MLS protocol; no production mixed-plane assumptions.
- Complete E2EE media encryption/decryption with tamper, wrong-recipient, stale-epoch and membership-removal tests.
- Validate MediaProjection foreground service and revocation on supported Android versions.
- Release manifest and signing audit.

### P1 — reliability / privacy
- Add authorization tests for every backend endpoint and websocket event.
- Add fuzz/property tests for JSON envelopes, media manifests, MIME sniffing, file names, URI handling, MLS wire decoding and chunk parser.
- Add lifecycle tests for every screen flow and account/tenant transitions.
- Ensure no debug/demo data in release.
- Verify cache/exports/temp cleanup and view-once policy.

### P2 — performance / UX
- Macrobenchmark cold start, home scroll, long chat, media picker, video playback, transfer, call and settings.
- StrictMode and main-thread IO tests; memory and battery profiles on low/mid-range Android.
- Accessibility scanner, TalkBack, large font, contrast, touch target and reduced motion checks.
- UI state coverage: loading, empty, offline, retry, denied, disabled, progress, success, partial failure and destructive confirmation.

### Required evidence before marking a flow “complete”
- Code path and backend contract reference.
- Unit test + integration/instrumentation test.
- Release build result.
- Device/API-level matrix result.
- Security review result.
- Explicit limitations and rollback/migration behavior.

## 7. Next implementation order

1. Fix auth token storage (SEC-001/012) with encrypted migration and tests.
2. Remove full-file memory reads and cache-copy picker path (SEC-004/005/006).
3. Decide backend ownership and migrate MLS enrollment/group RPCs (SEC-002/009).
4. Finish attachment E2EE (SEC-003/010/011).
5. Complete screen-share foreground-service lifecycle (SEC-007/015).
6. Harden lock, permissions and all component validation contracts (SEC-008/013/014).
7. Build flow-level test suite, release scan and Play Protect evidence pack.

**No destructive cleanup is authorized by this audit.** Preserve existing screens, persisted settings, messages and media. Migrations must be backward-compatible, idempotent and recoverable.
