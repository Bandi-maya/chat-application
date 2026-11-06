# Call redesign stage 3: implementation status, not release approval

Base: supplied effective 8cba2a7. This is a source delta, not a branch replacement.

## Newest default instruction

CHATY_DEFAULT incoming audio/video now presents Apple-inspired placement. Existing explicit other bundle IDs are not changed. Apple uses actual Android keyguard state: locked slider plus an accessible decline control, unlocked separate left decline/right answer. No official artwork, sample person, logo, photo or brand asset was added. This supersedes PDF v3's earlier recommendation to keep the old Chaty incoming identity. Ongoing default identity remains unchanged.

## Bundle status

All rows are partial and device/pixel-unverified. The source contains each bundle's existing routing/geometry/gesture rather than manufacturing absent vendor states.

| Bundle | Implemented source changes | Reference boundary / remaining |
|---|---|---|
| Chaty Default | Incoming now Apple-inspired, per newest owner choice; local video preview; theme roles | Validate locked/unlocked, banner and narrow/large text. Ongoing default still original geometry |
| Apple iOS | Real locked/unlocked branching instead of permanently simulated locked slider; separate accessible decline; scroll-safe caller/controls | V3 full screen image is archived iOS13, not a claim of latest iOS. No Message/Remind actions without a real feature |
| Samsung One UI | Existing tap halos retained, theme roles and local preview applied | Exact OS version unpinned. Optional tap/swipe preference not implemented |
| Google Phone Latest | Existing horizontal puck/single-fire gestures retained; theme roles, local preview, round audio controls | No claim all 2026 states match Pixel10 image. Strict gesture tests need rerun |
| Google Phone Classic | Existing central swipe control retained; theme roles and local preview | Locked/unlocked variations still need review/captures |
| vivo/iQOO | Existing layout retained with theme roles/local preview/shared audio controls | Exact official call-screen image absent; locked/unlocked/popup variants not implemented |
| Nextcloud Talk | Existing answer controls/header retained with theme roles/local preview | Official image in V3 is web, not Android parity proof |
| Azure Ring Vertical | Existing vertical ring gestures retained, theme roles and local preview | App-defined, external original absent. Reduced-motion/device captures pending |
| Mono Chevron Dark | Existing chevron up-decline/down-answer retained with theme roles/local preview | App-defined, external original absent. Does not force black/white contrary to user theme |

## Shared changes and proof

- Incoming preview is CameraX Preview only. No recorder, microphone or WebRTC path. Requires already-granted camera permission, resumed lifecycle and unlocked keyguard; unavailable/off uses theme fallback. Release on background/screen-off/accept/reject/minimize/dispose and notification answer. STREAMING state, not bind success, determines readiness.
- Existing bundle surfaces are transparent over a theme-owned scrim only for incoming video. A separate top toolbar prevents overlaying caller identity. Small status button says local preview on/off/unavailable. Readability and overlay placement still need actual pixels.
- Audio exactly four round controls: Mute, Speaker, Video, Hang up. Audio controls do not auto-hide. Current Speaker action is speaker/earpiece only, not a supported-device selector. Labels wrap. TalkBack mute/speaker states added.
- Theme helpers remove fixed palettes from incoming/ongoing; InAppCallPip now also consumes semantic roles; secondary call files remain to audit. All visual assertions need production render proof. Source assertions expecting old fixed palette must be revised narrowly, never delete runtime gates.
- Upgrade client coordinator/service compile with actual model/codec; nine pure JVM state/coordinator tests pass. WebRTC file type-checks against actual WebRTC jar using minimal Android permission/foreground/projection stubs, NOT full Android compile. No transport runtime proof. Both production capability gates remain false.
- Client coordinator checks account/call/peer identity through the captured service guard, rejects old request replay, serializes SDP/media work, invalidates awaiting work on cancellation, and keeps audio media provider alive. Distinct signals do not touch original call-offer duplicate logic.
- Media draft adds disabled video sender only after consent, starts camera at commit, removes/disposes upgrade video on cancellation/return to audio and attempts SDP rollback. ICE sequencing, rollback failure, cancellation at every await, repeated upgrade and two-real-endpoint tests remain blocking before enabling.

## Outstanding release gates

No complete CALL-01..06 claim. Need actual Android/Compose compile; strict suite including corrected Direct1x/2x; all nine production screenshots at 220/280/360dp,font1/1.5/2,light/dark/theme,landscape/insets,long names,TalkBack/reduced motion; camera denied/busy/locked/background/terminal cleanup; supported-device routing; real two-endpoint upgrade tests. Publisher applies on live combined candidate, preserving Bandi commits and CI preflight repair. No main merge or APK until green and owner approval.
