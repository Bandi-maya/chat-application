# FEATURE PRESERVATION LEDGER & INTEGRATION CONTRACTS

## Executive Summary
This ledger guarantees that every functional capability, system integration, persistence mechanism, and user customization in Chaty is preserved with zero feature regression.

---

## 1. Feature Preservation Matrix

| Feature ID | Functional Capability | Core Files / Handlers | Persistence & State | Risk Level | Preservation Guarantee & Acceptance Criteria |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **FP-01** | E2EE Messaging | `lib/features/chats/chat_detail_screen.dart`, `openmls` | Local SQLite + OpenMLS KeyStore | Critical | Messages encrypt and decrypt cleanly without plaintext leakage; MLS groups function without state loss. |
| **FP-02** | WebRTC Voice/Video Calling | `lib/features/calls/ongoing_call_screen.dart`, `flutter_webrtc` | Active Call Session State | Critical | Call connection, audio routing (speaker/earpiece/bluetooth), camera toggle, and mute function with zero audio interruption. |
| **FP-03** | Dynamic Island Notification Overlay | `DynamicIslandNotificationListener.kt`, `event_toast_overlay.dart` | `SharedPreferences` toggle | High | Inset-relative positioning (`MediaQuery.padding.top`), smooth expansion on tap, no layout jitter, stable collapsed state. |
| **FP-04** | Theme & Token Customization | `gb_design_system.dart`, `theme_editor_screen.dart` | `SharedPreferences` key-value pairs | High | All user color overrides, corner radii, bubble styles, and tick styles load instantly on app restart. |
| **FP-05** | Biometric App Lock & Vault | `locked_chats_screen.dart`, `local_auth` | `flutter_secure_storage` | High | Biometric prompt triggers appropriately on resume; locked chats remain hidden until authentication succeeds. |
| **FP-06** | Contact Picking & Dual-SIM | `call_contact_picker_screen.dart`, `flutter_contacts` | System Contacts Permission | Medium | Search filtering remains instantaneous; SIM selection sheet displays available phone accounts without crashing. |
| **FP-07** | Media Viewer & Playback | `media_viewer_screen.dart`, `video_player`, `just_audio` | Device Storage Cache | Medium | Fullscreen zoom, scrub bar, voice note waveform playback, and audio speed toggle work smoothly. |
| **FP-08** | Camera & Face Mesh AR | `camera_capture_screen.dart`, `mediapipe_face_mesh` | Camera Controller Stream | Medium | Viewfinder maintains correct aspect ratio without distortion; shutter captures full-resolution photo. |
| **FP-09** | Cloud Sync & Supabase Backend | `supabase_flutter`, `auth/login_screen.dart` | Remote Supabase Auth Session | High | Authentication state persists; real-time channels receive message events without dropped connections. |
| **FP-10** | Event Toast Notifications | `lib/ui/core/widgets/event_toast_overlay.dart` | Global Overlay Entry | Low | Transient in-app alerts stack cleanly, animate in/out without blocking underlying touch gestures when dismissed. |

---

## 2. Safety Invariants & Non-Negotiables
1. **No Code Deletion**: No existing screens, routes, or settings are deleted or replaced with static mockups.
2. **No Dependency Additions**: Zero new packages in `pubspec.yaml`; only native Flutter/Dart and project libraries used.
3. **Preserve User Settings**: Existing theme keys and user preferences stored in `SharedPreferences` remain 100% backward-compatible.
4. **Edge-to-Edge Geometry**: All screen insets conform to Android 14/15 window insets without clipping content behind navigation bars or cutouts.
