# Chaty Direct phase1 acceptance

Base effective tree:4583f81b362ce2ec785fa18a85c64f2141533c03, source pin1b2ade4c51b7163ab6034dc63c24e09c5cf99b1c. Separate from kotlin merge and three strict-proof corrections.

## Scope
- New Chaty Direct attachment action, original image/video only, one-to-one approved contact. Exact source bytes, no compression, no media upload and no relay fallback. Any internet connection allowed, including mobile data.
- Existing Large file remains legacy v1. Existing call transports unchanged. No new call_signal event/key, backend source, database schema, dependency, permission or paid service.
- Hidden MLS-encrypted invitation through existing chat message. Direct flag stays inside the encrypted payload. Original access must be persistable so process-death source recovery is real; unsupported picker providers fail with a useful message.
- Ticket exists before negotiation. Existing offer/answer/ICE/restart/cancel events only. Version2 data-channel label and magic, no downgrade. Old/new mismatches fail; update both clients.
- AES-GCM on chunks with per-chunk SHA-256 inside authenticated ciphertext. AAD binds version, transfer, whole digest and geometry/index. Authenticated directional sequenced controls ensure receiver requests and completion require the shared secret. Final whole-file SHA-256 required before verified success.
- STUN-only ICE settings for Direct sessions, reject relay candidates in trickle and SDP. No network type lock. Failure is honest on restrictive NAT.
- Private partial files; fsync verified bytes before durable bitmap. Sender resume reopens original and verifies whole hash. Encrypted receive ticket cache avoids replaying consumed MLS application messages. Verified received receipt binds account/peer/path/digest and is rechecked before opening. Filename includes transfer id.
- Foreground-first: keep both phones open. No new background service or indefinite background promise. Waiting, percentage, retry/cancel and errors remain in chat card/tap controls. Sender queued bytes cannot show100% before receiver verification.

## Verification actually run by writer
- Kotlin2.0.21/JUnit4 isolated compilation and13 tests pass:7 new Direct tests +6 existing native protocol tests.
- Engine compilation against actual WebRTC150.7871.04 classes succeeds. Android/Keystore/network stand-ins used only in external test harness, not shipped. This is a syntax/API compile check, not an APK build or platform test.
- git diff --check passes. Existing five signalling-event literals unchanged.

## Publisher gate, separate run only
1. Build production and compile Android tests using full effective source/assets.
2. Run ChatyDirectProtocolTest and existing NativeP2pTransferProtocolTest, ledger/private-destination tests and full JVM suite.
3. Run ChatyDirectPhaseOneProofTest (2 tests), inspect actual option screenshot and AtomicFile resume result. Recheck narrow/font2x attachment surface and media bubble states visually.
4. Two real peers: approved contact, image/video exact hashes on receiver; reject unrelated peer/tampered ticket/version mismatch; background/process death on each side then explicit resume; changed source/revoked permission; full disk; cancellation/retry; expired ticket; duplicate/out-of-order chunks; corrupted partial/ledger; no cross-account view.
5. Separate Wi-Fi networks, Wi-Fi/mobile, mobile/mobile, restrictive NAT and VPN. Capture selected candidate pair/network traffic proving no relay/media upload on Direct. Restrictive failure must not upload.
6. Verify ordinary cloud attachment sends, legacy Large file, calls and kotlin strict proofs unchanged.

No two-device runtime, actual APK/Compose compile, Android instrumentation, corrected visual result or independent security audit is claimed yet. Do not merge or describe as audited/production-ready before these gates. Phase2 smaller-copy encoding is excluded.
