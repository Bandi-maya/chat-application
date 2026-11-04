# Release readiness

NOT READY / NOT VERIFIED. This report does not state current Play-policy requirements as facts; official-source/date checks remain pending. No signing/package/backend changes authorized.

## Blockers

- Confirm actual production Play application ID and signing lineage. Source/CI/sample disagree; never guess or generate a replacement key.
- Verify final target/API and current submission rules against dated official docs.
- Inspect every final native library ELF and APK/AAB ZIP alignment, then run 16KB runtime proof. Native MLS/WebRTC/JNA dependencies require artifact evidence.
- Complete permission-feature-runtime-disclosure-Play mapping; FGS/background start, microphone/camera/MediaProjection/Bluetooth, notifications/fullscreen/overlay, URI grants and callback paths.
- Verify owner-operated privacy/Data Safety/UGC/moderation/deletion/retention/terms with actual backend/SDK behavior; client buttons are not proof.
- Verify release variant excludes debug fixtures/endpoints/secrets and has correct signing/version/mappings/native symbols.
- Full QA/build/test/pixel/performance/physical call/staging results pending. No high-refresh/zero-lag/Play-acceptance promise.

See `qa/CLIENT_SAFETY_RELEASE_GAPS.md` and permission census for source-presence observations and limits.
