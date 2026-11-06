# Audio-to-video upgrade: proposed client/server contract

Client-only scope. Server implementation belongs to Bandi. No backend file was changed. This is not evidence of a deployed relay capability or working two-endpoint upgrade.

## Required relay capability

Advertise authenticated `call_video_upgrade_v1: true` only when the server forwards these authenticated `call_signal.data.signal_type` values unchanged to the OTHER participant in the same active call:

- `video_upgrade_request`: `{call_id, request_id, protocol_version:1}`
- `video_upgrade_accept`, `video_upgrade_decline`, `video_upgrade_cancel`: `{call_id, request_id, protocol_version:1}`
- `video_upgrade_offer`, `video_upgrade_answer`: same identity fields plus `sdp` string.
- `video_upgrade_commit`: same identity fields after the answer was successfully applied.
- `video_return_audio`: `{call_id, request_id, protocol_version:1}` to disable video again.

Existing candidate events continue carrying ICE for the same peer connection. Do not create a second call, reset call duration, replace account scope, or hang up audio on an upgrade decline/timeout. A client must not assume arbitrary new types relay merely because ordinary offer/candidate works.

Server derives sender ID from token, rejects self/foreign/nonparticipant/stale/ended calls, and never trusts supplied sender/caller IDs. Request IDs are opaque, bounded and unique per attempt; max SDP size and per-call rate limits apply. Keep a 30-second request lifetime and bounded deduplication; reconnect must not replay expired consent. Expose peer protocol support for the current call. Absent/unknown capability or peer support means backend-pending, no upgrade request emitted. Do not log raw SDP, tokens or media.

## Client implementation sequence still required

The VideoUpgradeState model enforces request/accept matching, deterministic glare, cancellation and timeout. CallVideoUpgradeCoordinator is wired through distinct upgrade signal handlers, not initial offer/answer deduplication. It emits request/accept/decline/cancel/offer/answer/commit and return-to-audio, exposes consent/pending UI, and uses a media upgrade seam. The production relay+peer capability provider defaults false, and WebRtcCallMediaController.supportsVideoUpgrade also stays false until transport gates pass. The Video button therefore remains honestly pending. Do not enable either gate from a server claim alone.

Review/complete transport gates before enabling: the coordinator binds every await/result to account scope, call ID, request ID and exact media instance. Serialize renegotiation. Both people consent before video track allocation; camera capture starts only at matching commit; sender track stays disabled until matching answer and commit, including cancellation races. Add a video transceiver/track to the existing peer connection without recreating audio. Receiver sets offer, creates answer; requester sets answer; commit activates allowed video and updates both session states. Queue ICE during SDP transition. On decline/cancel/timeout/failure release new camera resources and roll back SDP/transceiver state without disposing audio. If rollback cannot be proven safe, do not enable capability. Return-to-audio disables/stops video on both peers and retains audio. Handle simultaneous requests by stable account-ID tie-break, never dual local offers.

Incoming own-camera backdrop is a separate local CameraX Preview with no microphone/recorder/WebRTC track. Release it before accept so the actual call transport can claim camera. Denied permission, lockscreen, paused activity and busy/no front camera use themed fallback. Never request permission covertly or transmit preview frames before acceptance.

## Gates

Compile real Android/WebRTC source; pure state tests are not transport proof. Fake transport tests need stale-account/call, foreign peer, duplicated/late answer, glare, timeout, cancellation at every await, permission failure and rollback preserving audio. Device tests need two endpoints on actual relay: request/accept, decline/cancel/timeout retaining audio, zero outgoing video frames before consent, repeated return-to-audio/upgrade, background/account switch, camera release, ICE reconnect. Emulator callbacks prove only local UI/state unless actual two endpoint traffic is measured. No APK until strict/runtime/pixel gates and owner screenshot approval.
