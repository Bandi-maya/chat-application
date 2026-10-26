# Issue Register

Initial source review: 27 Oct 2026, 01:43 IST. Build launch update: 01:49; runtime compile failure update: 01:54. Source review and unit results are not emulator acceptance.

Proposed severity; no whole-app acceptance. Initial vs later findings retained; no confirmed introduced regression yet, not proof none exists.

| ID | Severity | Evidence | Issue | Status |
|---|---|---|---|---|
| MAP-01 | P1 | Observed phone + shipped source | Blank pre-map Load gate | Local Next5 no-gate patch; Android route/pixels pending |
| CALL-01 | P0 | User report01:41 | Calls not connecting | Deployed-network root cause unverified |
| CALL-02 | P1 | Current source | Uncaught accept/switch/share failures | Bounded error helper tests reported; Android unverified |
| CALL-03 | P1 | Current source | Screen-share consent setter has no production caller | Projection/FGS flow pending, do not fake success |
| CALL-04 | P1 | Current source | STUN-only default; no relay supplied | Not proof of failure cause; no authorized free TURN verified |
| CALL-05 | P2 | Current source | Call record sink no-op | Durable history incomplete |
| CALL-06 | P1 | Synthetic service reproduction | ICE emitted before offer/answer enqueue | Queue/dedup bounded fix;62 service/codec tests reported with CALL-07, not native media |
| CALL-07 | P1 | Synthetic service reproduction | Incoming ICE queued while ringing cleared on accept replacement | V2 same-call preserve guard; synthetic two-peer state wiring uses fake media callbacks, no actual WebRTC proof |
| TASK-02 | P1 | Current source | Task detail missing child system-Back handler | 2-line editor/detail guard staged; runtime pending |
| EMOJI-02 | P2 | Current source | Picker tiles static Text despite animation request | Visible-item animation proposed; perf/reduced-motion proof owed |
| ISLAND-01 | P1 | Current source | Original shade notification canceled before final lock/enabled checks | Preserve-original patch/source check pass; synthetic Android unrun |
| CAMERA-02 | P2 | Current source | EXIF/allocation failure can retain temporary rasters | Outer cleanup patch + unreadable-input test; Android unrun |
| MEDIA-02 | P1 | Current source | Initial video signedUrl non-null assertion and reader-disposal race | V3 staged with reader capture; URI/codec/epoch tests added, unrun Android |
| MAP-02 | P2 | Exact JS reproduction | Offline recovery leaves stale status | Desktop offline-online/style recovery pass matching asset; not Android proof |
| MAP-03 | P2 | Source gap | Viewport reset across background | Production lifecycle proof needed |
| RADIAL-02 | P2 | Current source | Fixed compact row may clip large text | Min-height/shared-constant patch + helper passes; Android unrun |
| PREVIEW-02 | P2 | Current source | Radial preview old shape/hardcoded list or virtual viewport offset | Pixels needed, not observed visual failure |
| SWIPE-02 | P2 | Source boundary | Below240dp three60%-width cells under48dp | Not claimed normal-phone regression |
| SWIPE-03 | P2 | Hypothesis | Production amber/white action contrast may be low | Measure real skin; no silent shared-theme recolor |
| MEDIA-03 | P2 | Source lead | Restricted viewer OpenSecurely action routes to forbidden handler | Hide forbidden action, never weaken policy; unpatched |
| MEDIA-04 | P2 | Source lead | Edited SHARE token reused for OPEN without purpose check | Cache/security design needed; no authority widening |
| MEDIA-05 | P2 | Source lead | Repeated crop/rotate bitmap memory pressure | Lifecycle fix risk, runtime/perf needed |
| INSTALL-01 | P0 | Verified artifact certificates | Next4 differs from old/Next3 signing | Hold safe-update claim; preserve encrypted local history |
| QA-01 | P0 | Evidence gap | Entire-app/settings audit not completed | 100 declared methods/33 files is not100 tested screens |

## Not promoted
Actual Next5 security review confirms plaintext token stores, absent migrating token store and exported login/reset filters without handlers. Runtime/exploit status remains unverified; device-global lock scope needs resolution. Stale Task detail after edit remains hypothesis. Earlier claim camera controls absent was withdrawn. Old UNCOMPILED/bridge=null comments are not current proof.


## Security reconciliation update
- SEC-01 P1 source-confirmed current Next5: plaintext token persistence and no wired migrating token store. Runtime exposure/exploit unverified; atomic migration HOLD.
- SEC-02 P2 source-confirmed current Next5: exported login/reset filters without handler. Actual routing impact/runtime proof pending.
- SEC-03 source-confirmed legacy Supabase preferences/realtime wiring persists; runtime activation unknown, not a claim user backend was replaced.


## Runtime outcome update,01:54 IST
Next4 runtime job failed during Android test compilation, BEFORE app installation/instrumentation: AttachmentFileCacheTest.kt:295 unresolved EditSession/outputFile reference. No app started, no runtime XML or app visual passes. This is a test-source compile failure, not an observed app crash. Native and main application build and1006 JVM unit passes remain valid. Source: https://github.com/Bandi-maya/chat-application/actions/runs/40389044496/job/121812020284 . A missing EditSession import is the test compile cause; outputFile is a cascade error. No production defect is shown by this compile failure. Next5 launch was recorded at01:49; no completed outcome is recorded in this review.
