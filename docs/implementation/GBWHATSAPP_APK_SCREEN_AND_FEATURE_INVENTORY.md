# GBWhatsApp Reference APK — Screen and Feature Inventory

## Scope and confidence

Reference file supplied in this conversation: `GBWhatsApp_P_com.gbwhatsapp_V_v2.26.28.78.apk`.

This is a **static APK inventory**, not a device-driven walkthrough. The archive contains 12 DEX files, 5,678 layout resources, 8,927 XML resources, 10,161 drawable resources, and 273 asset files. These are resource counts, not screen counts. A layout resource may be a row, dialog, widget, or partial screen. Some labels and feature paths are inferred from resource/code strings. Exact settings labels, visual layouts, toggles, and navigation order still need confirmation by running the APK on a test device and recording every screen. Do not treat every row below as verified enabled behavior.

The objective is to reproduce legitimate user-facing feature categories in Chaty with original UI/components and safe backend implementations—not copy WhatsApp/GBWhatsApp proprietary assets, branding, private code, or security bypasses.

## Screen and feature inventory

| # | Screen / area | Features and flows to inspect | Settings/template surfaces | Chaty implementation target | Evidence / validation |
|---:|---|---|---|---|---|
| 1 | Launch / splash | Cold launch, initialization, restore previous session, loading/error/retry | Splash branding, supported motion | App bootstrap and route guard | Device capture needed |
| 2 | Registration / sign-in | Phone/account onboarding, verification, session restore, sign out | Account/security preferences | Existing Supabase auth flow; don't copy WhatsApp auth backend | Device capture + backend contract |
| 3 | Main home / tab shell | Main tabs, tab switching, unread counts, overflow/actions, back behavior | Tab order/style, labels/icons, header, density | Global shell driven by template profile | Layout/resource evidence; runtime confirmation |
| 4 | Chats list | Search, unread/read state, last message, timestamp, pin, mute, archive, swipe/context actions | List density, avatar, badge, preview style | Shared chat list item + repository-backed actions | DEX identifiers include thread/unseen-message data; test actions |
| 5 | Individual chat | Message list, date dividers, scroll-to-latest, unread marker, pagination | Wallpaper, bubble shape/color, spacing, timestamp/tick styles | Shared message timeline | Message resources/DB strings; device walkthrough |
| 6 | Composer / keyboard | Text, emoji, send, multiline, attachment/camera/mic actions | Composer layout, icon/shape, toolbar arrangement | Shared composer component | Device walkthrough |
| 7 | Message context menu | Reply, forward, copy, edit, delete, star/pin, reactions, select/multi-select | Context menu density/shape where configurable | Action policy + message repository | Validate each action against Chaty backend support |
| 8 | Reply / quoted message | Quote preview, jump to source, threaded replies where available | Quote bar, typography, colors | Reusable reply preview | Runtime confirmation |
| 9 | Media and documents | Image/video/document send, preview, download/open/share, progress/failure | Gallery grid, media preview, document row | Attachment service + platform pickers | Resource evidence; test permissions and failures |
| 10 | Voice messages | Record, cancel, send, playback, seek, speed/waveform where present | Recording strip, waveform and controls | Native recording/player lifecycle | Device walkthrough on Android and iOS |
| 11 | Camera / gallery picker | Capture/select media, preview, confirm/cancel | Picker preview only where supported | Platform picker abstraction | Device walkthrough; permissions denied/retry |
| 12 | Contact / profile details | Avatar, status/about, shared media, search, mute, block, report, contact actions | Profile header, avatar and action layout | Profile screen + authorization-backed actions | Runtime confirmation |
| 13 | Group creation / details | Create group, choose participants, title/photo, members, admins, invite/link, description | Group header, member list rows | Group repository/RLS and role rules | Verify every action and permission |
| 14 | Calls tab / history | Incoming/outgoing/missed records, voice/video distinction, return call | Call row and icon/badge style | Existing call history service | DEX contains call-log schema references; runtime confirmation |
| 15 | Voice call | Ringing, connect, mute, speaker/audio route, end, reconnect/error | Call screen controls and layout | WebRTC/call signaling lifecycle | Must test real devices and permissions |
| 16 | Video call | Camera permission, preview, switch camera, mute, end, background/PiP where supported | Call controls and overlays | WebRTC + native lifecycle | Runtime and device test |
| 17 | Status / updates list | Recent/viewed status rings, mute/hide, create status | Ring color/style, card density | Status service and status list | Resource/feature inference; verify exact app behavior |
| 18 | Status viewer / composer | View photos/videos/text, progress, reply/react, post, expiry, audience | Viewer controls, status rings, composer | Server-backed status lifecycle | Verify upload/view tracking and privacy |
| 19 | Broadcasts / channels / communities | Discover/create/manage where present in this build | List/card templates | Add only if actual app scope and backend contracts support it | DEX contains broadcast/channel-related terms; exact screens unverified |
| 20 | Search | Global and in-chat search, results, filters, jump to message | Result row and highlighting | Indexed/query-backed search | Runtime confirmation |
| 21 | Archived chats | View, restore/unarchive, preserve unread state | List density and row style | Chat state persisted by repository | Runtime confirmation |
| 22 | Starred messages | List, jump to source, unstar | Starred row template | Message state + repository | Runtime confirmation |
| 23 | Pinned chats/messages | Pin/unpin, limits and ordering | Pin marker and row/bubble states | Server-backed pin state where supported | Runtime confirmation |
| 24 | Privacy settings | Last seen/online, read receipts, profile/status audience, blocked users, app lock if present | Privacy controls and explanations | Only real platform/backend-supported controls | Do not infer behavior from switch labels alone |
| 25 | Security / chat lock | App lock, biometric/PIN prompts, locked-chat entry if present | Lock entry presentation | Platform secure storage + authentication | Runtime confirmation; never fake protection |
| 26 | Notifications | Message/group/call notifications, mute, tones/vibration where supported | In-app notification style and supported badges | OS notification channels and preferences | Android/iOS behavior differs; verify separately |
| 27 | Chat settings | Media visibility, enter-to-send, wallpaper, backup-related options if present | Chat-specific global defaults | Persistent settings model + migration | Runtime confirmation |
| 28 | Appearance / themes | Theme mode, colors, wallpaper, fonts, icon/navigation options if present | Global and per-chat appearance | Chaty template editor with preview/apply/reset | Must distinguish actual APK options from Chaty-only enhancements |
| 29 | Storage / network / data | Storage usage, media auto-download, network usage and data saver if present | Media/download behavior | Download policy and cache controls | Runtime confirmation |
| 30 | Account / devices | Profile, linked devices, account changes, backup/restore options if present | Security/session UI | Existing auth + device enrollment/revocation | Never implement UI-only revocation |
| 31 | Help / about | Help, contact, terms, privacy policy, version | About rows | Chaty help/about screens with correct destinations | Runtime confirmation |
| 32 | Dialogs / sheets / pickers | Confirmations, overflow menus, attachment sheet, emoji/sticker/GIF pickers | Radius, elevation, typography, spacing | Shared modal/sheet primitives | Layout resources are not equivalent to unique screens |
| 33 | Error / empty / offline states | Empty lists, no connection, failed upload, retry, blocked permission, expired session | Consistent error and skeleton treatments | Shared state components and recovery logic | Must be intentionally tested; not proven by resource existence |
| 34 | Accessibility / lifecycle | Large text, screen reader, keyboard, rotate, background/foreground, process death | Text scaling, reduced motion, tap targets | Responsive Flutter layouts and lifecycle-safe controllers | Android and iOS test matrix required |

## What static extraction can and cannot prove

- **Observed archive facts:** 12 DEX files and the resource counts stated above; DEX strings contain database/query identifiers for message threads, unread counts, status, broadcast, and call-log data.
- **Not yet verified:** exact screen count, every visible setting label, exact order of settings, which GBWhatsApp-specific toggles are active, and whether each option works on the supplied APK version.
- A definitive visual inventory requires installing this APK only in an isolated test environment, navigating each screen, recording screenshots and taps, and noting permission/network requirements. No WhatsApp account, personal messages, or production credentials should be used for this inspection.

## Required next-step workflow

1. **Capture and label the APK screens** in a test device/emulator. Record each screen, entry path, controls, states, dialogs, and back behavior. Keep screenshots as reference artifacts with numbered IDs.
2. **Build a control-by-control matrix**: screen, control, default value, action, persisted key, backend/API/RPC, permission, failure state, test case, and Chaty equivalent.
3. **Map each flow to existing Chaty code** before implementation. Reuse current repositories/services and the existing MLS/WebRTC design; do not create duplicate data sources.
4. **Classify parity items** as existing-and-working, exists-but-incomplete, missing, unsupported by platform, or intentionally excluded for privacy/security.
5. **Implement in vertical slices** (UI + state + backend + tests), starting with global template architecture and known correctness/security gaps.
6. **Verify Android and iOS** for all changed flows, run format/analyze/tests, validate Supabase migrations/RLS and production security preflight, and record the results.
7. **Do not declare complete** based on a screen rendering alone. Each item must pass persistence, backend authorization, offline/error, lifecycle, accessibility, and regression tests where applicable.
