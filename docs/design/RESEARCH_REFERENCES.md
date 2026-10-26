# Research References

Initial source review: 27 Oct 2026, 01:43 IST. Build launch update: 01:49; runtime compile failure update: 01:54. Source review and unit results are not emulator acceptance.

## S1 Apple: Live Activities
https://developer.apple.com/design/human-interface-guidelines/live-activities
Primary platform design guidance. Read current page in browser, updated 13 Oct 2026. Glanceable, limited-lifetime state; compact/minimal/expanded presentations; sensitive-data caution; consistent margins and state-driven updates. Android implementation here is an adaptation, not API compatibility.

## S2 Apple: Accessibility
https://developer.apple.com/design/human-interface-guidelines/accessibility
Primary platform guidance. Larger text, contrast in light/dark, assistive technologies and reduced motion. Use Android equivalents rather than Apple-only tooling.

## S3 Apple WWDC: Design dynamic Live Activities
https://developer.apple.com/videos/play/wwdc2023/10194/
Primary design talk + transcript, 2023. Even/concentric inset placement, stable relative layout across presentations, essential controls and glanceability. Older talk; current HIG takes precedence on platform details.

## S4 Android: Make apps more accessible
https://developer.android.com/guide/topics/ui/accessibility/apps
Primary implementation guidance, page updated 23 Oct 2026. 48dp touch regions; text contrast 4.5:1 for smaller text and 3:1 for larger text; purposeful semantics and decorative-icon handling.

## S5 Android: Compose accessibility testing
https://developer.android.com/develop/ui/compose/accessibility/testing
Primary testing guidance, updated 22 Oct 2026. Combine manual TalkBack/Switch Access with semantic assertions and Accessibility Test Framework checks; automated checks alone are incomplete.

## S6 Android: Core-Telecom
https://developer.android.com/develop/connectivity/telecom/voip-app/telecom
Primary integration guide. Being added to platform Telecom is not being active/connected; outgoing setActive follows remote answer. Audio endpoint and background lifecycle guidance. This is a reference, not a diagnosis of Chaty.

## S7 OWASP MASVS
https://mas.owasp.org/MASVS/
Security standard. Defines storage, crypto, authentication, network, platform, code, resilience and privacy groups; MASTG/MASWE supply verification detail. Not evidence Chaty passes.

## S8 Laws of UX
https://lawsofux.com/laws/
Independent practitioner synthesis. Fitts, Hick, chunking, cognitive load and aesthetic-usability effect are design heuristics, not automatic proof or a substitute for tests.

## S9 GitHub: Now in Android
https://github.com/android/nowinandroid
Official Android reference repository; Kotlin/Compose, demo variants, testing and design learning journeys. Apache-2.0 per current README; review specific code licenses before reuse.

## S10 GitHub: architecture learning journey
https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md
Official sample architecture: UI/data/domain separation, unidirectional events/data and Kotlin Flows; supports parallel development and testability. Apply incrementally, not an overnight rewrite.

## S11 GitHub: requested GeoLibre repository
https://github.com/opengeos/GeoLibre
Requested upstream, current README inspected. Full GIS platform built from multiple technologies with multiple rendering engines; inclusion of a headless map package does not mean the full app is embedded. Upstream current features do not prove the pinned shipped package supports them.

## S12 WebRTC peer connections
https://webrtc.org/getting-started/peer-connections
Read official guide: SDP/ICE exchange and connected state distinct from enqueue/ringing.
## S13 TURN
https://webrtc.org/getting-started/turn-server
Relay may be necessary for direct-connection failures; no free service or diagnosed root cause established.
## S14 Android FGS restrictions
https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
While-in-use mic/camera and Android14 startup restrictions require lifecycle testing.
