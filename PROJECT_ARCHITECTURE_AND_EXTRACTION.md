# Chaty — Comprehensive Architectural Extraction & Technical Specification

> **Repository:** `chat-application`  
> **Application Name:** Chaty (Private, Customizable Messaging & Tasks)  
> **Document Status:** Complete Exhaustive Extraction  
> **Target SDK:** Flutter `^3.12.2` (Dart 3.x) | Supabase Backend | OpenMLS RFC 9420 | WebRTC  

---

## Table of Contents
1. [Executive Summary: What is Chaty?](#1-executive-summary-what-is-chaty)
2. [Product Scope: What We Built & Are Building](#2-product-scope-what-we-built--are-building)
3. [System Architecture & Data Flow](#3-system-architecture--data-flow)
4. [Component Display System & Design System Architecture](#4-component-display-system--design-system-architecture)
5. [Core Logic, Subsystems & State Machines](#5-core-logic-subsystems--state-machines)
   - [5.1 Cryptographic & E2EE Subsystem (RFC 9420 MLS)](#51-cryptographic--e2ee-subsystem-rfc-9420-mls)
   - [5.2 Outgoing Message Queue & Delivery State Machine](#52-outgoing-message-queue--delivery-state-machine)
   - [5.3 WebRTC Voice & Video Calling Subsystem](#53-webrtc-voice--video-calling-subsystem)
   - [5.4 Collaborative Tasks & Workflow Synchronization](#54-collaborative-tasks--workflow-synchronization)
   - [5.5 Status & Stories Subsystem (24h Ephemeral Updates)](#55-status--stories-subsystem-24h-ephemeral-updates)
   - [5.6 Settings, Preferences & GB Customization Engine](#56-settings-preferences--gb-customization-engine)
   - [5.7 Security, Biometrics & Local Lock Layer](#57-security-biometrics--local-lock-layer)
   - [5.8 Camera, Lenses & Augmented Reality Effects](#58-camera-lenses--augmented-reality-effects)
6. [Complete Project Extraction: File-by-File Inventory](#6-complete-project-extraction-file-by-file-inventory)
   - [6.1 Application Entry & Dependency Injection (`lib/`)](#61-application-entry--dependency-injection-lib)
   - [6.2 Core Domain Models (`lib/domain/models/`)](#62-core-domain-models-libdomainmodels)
   - [6.3 Data Access & Service Layer (`lib/data/`)](#63-data-access--service-layer-libdata)
   - [6.4 Presentation Design System & Tokens (`lib/ui/core/`)](#64-presentation-design-system--tokens-libuicore)
   - [6.5 Feature Modules (`lib/features/`)](#65-feature-modules-libfeatures)
   - [6.6 Supabase Infrastructure, Database Migrations & RPCs (`supabase/`)](#66-supabase-infrastructure-database-migrations--rpcs-supabase)
   - [6.7 Platform Native & Third-Party Dependencies](#67-platform-native--third-party-dependencies)
7. [Database Schema & Backend Contracts](#7-database-schema--backend-contracts)
8. [Summary & Key Architectural Principles](#8-summary--key-architectural-principles)

---

## 1. Executive Summary: What is Chaty?

**Chaty** is a production-grade, privacy-first, hyper-customizable communication and task collaboration application. It blends the enterprise-grade end-to-end encryption standards of modern secure messaging (specifically the **IETF RFC 9420 Messaging Layer Security / MLS** protocol) with the deep visual customization popularized by WhatsApp mods (such as GBWhatsApp), paired with real-time peer-to-peer VoIP/video calling and embedded task management.

### Key Tenets
1. **Zero-Trust Client Cryptography**: Private signing keys, database encryption keys, and ratcheting state never leave the device. Supabase and Postgres store only opaque ciphertexts, public key material, and ephemeral signaling envelopes.
2. **Deep Aesthetic Sovereignty**: Unlike rigid chat applications, Chaty gives users complete control over design tokens: 8 distinct Home Screen layout variants, customizable chat bubble geometries, custom delivery tick packs, animated emoji rendering, particle click overlays, dynamic wallpaper engines, and customizable app icon theming.
3. **Integrated Collaboration**: Beyond personal chats, Chaty treats conversations as collaborative workspaces with fully synchronized, encrypted tasks governed by a multi-state workflow engine (Todo → In Progress → In Review → Testing → Done / Reopened / Cancelled).
4. **Resilient Offline-First Architecture**: Powered by a local SQLite/SQLCipher and in-memory presentation cache backed by Supabase with Row Level Security (RLS). Strict FIFO outbox queuing guarantees message idempotency and automated background retry with exponential backoff and jitter.

---

## 2. Product Scope: What We Built & Are Building

| Feature Domain | Capabilities Implemented & Designed |
|---|---|
| **E2EE Messaging** | Client-side MLS ratcheting (OpenMLS Rust bridge + SQLCipher), 1-on-1 and Group chats, text, rich markdown, media (images, videos, documents, audio, contacts, location, polls), view-once media, message editing, reactions, pinned messages, starred messages, scheduled messages. |
| **Voice & Video Calling** | WebRTC audio and video calling, Supabase PostgreSQL RLS-backed signaling (`call_sessions` & `call_ice_candidates`), STUN/TURN NAT traversal, floating in-app Picture-in-Picture (PiP), Android Foreground Service lifecycle management, speaker/earpiece/Bluetooth routing. |
| **Collaborative Tasks** | Tasks embedded in chat threads, Kanban-style status workflow, task assignment, priority tagging, due dates, milestone tracking, and bijective synchronization between local and remote state. |
| **Status / Stories Updates** | 24-hour ephemeral text and media stories, per-viewer privacy filtering (My Contacts, Except..., Only Share With...), view tracking, anti-delete status retention, and automatic client/server expiry cleanup. |
| **GB Customization Engine** | 8 Home Screen UI layouts, custom bubble styles (iOS, Modern, Minimal, Rounded, Terminal, etc.), delivery tick styles (Double Check, Blue, Minimal, Circle, Flag), Ghost Mode (hidden presence), Airplane Mode simulator, Freeze Last Seen, custom display names, and theme presets. |
| **Security & Privacy Center** | App Lock via Biometrics (`local_auth`), PIN, or Pattern (`PatternLockPad`), auto-lock background timers, screenshot protection, secret-phrase-protected Hidden Locked Chats, anti-revoke message retention, anti-view-once bypass. |
| **Camera & Lens Studio** | In-app Camera capture (`camera`), real-time face tracking (`FaceTracker`), lens overlay engine (`LensOverlay`), AR filters (Beauty, Neon, Cyberpunk, Film, Vignette), and media cropping/editing dialogs. |

---

## 3. System Architecture & Data Flow

Chaty is structured using **Clean Architecture** with a strict unidirectional data flow and clean separation of concerns:

```
┌────────────────────────────────────────────────────────────────────────┐
│                          PRESENTATION LAYER                            │
│  MainNavigationShell ─── ChatsHomeScreen ─── ChatDetailScreen          │
│  CallsScreen ────────── OngoingCallScreen ── InAppCallPip              │
│  TasksScreen ────────── UpdatesScreen ────── SettingsRootScreen        │
│  Design System: ChatyKit, GbDesignSystem, ChatyModal, AppComponents    │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                        Observes / Listens to
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                       CONTROLLER & STATE LAYER                         │
│  ThemeController ────────────── ChatyPreferencesController             │
│  AppearanceVariantController ── TemplateController                     │
│  CallPresentationController ── RichChatRealtimeService                 │
│  ConnectionHealthService ────── LocalLockService                       │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                Invokes
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        DATA & SERVICE LAYER                            │
│  ChatyBackendService ────────── OutgoingMessageQueueEngine             │
│  MlsE2eeService (OpenMLS/Rust)  CallSignalingService (WebRTC)          │
│  ChatyDataStore (Local Cache)   StatusService                          │
│  ContactRelationshipService ─── NotificationService                    │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                          Network / Persistence
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                         INFRASTRUCTURE LAYER                           │
│  Platform Secure Storage ─── SQLCipher Database ─── SharedPreferences  │
│  Supabase Auth ───────────── Supabase Realtime ──── Supabase Storage   │
│  PostgreSQL Database with Row Level Security (RLS) & RPC Functions     │
└────────────────────────────────────────────────────────────────────────┘
```

### Dependency Injection (`lib/injection/locator.dart`)
All long-lived services and controllers are registered as singletons via `GetIt`:
- `ThemeController`: Global theme tokens, typography, dynamic palette derivation.
- `ChatyPreferencesController`: Multi-tier preferences (Privacy, Security, Home, Conversation, Notifications, Media, GB).
- `AppearanceVariantController` & `TemplateController`: Dynamic component override switching.
- `ChatyBackendService`: Central facade for Supabase Auth, profiles, contacts, and conversations.
- `MlsE2eeService`: Cryptographic engine managing RFC 9420 ratchets and KeyPackage pooling.
- `OutgoingMessageQueueEngine`: FIFO message spooler handling backoff, encryption, and dispatch.
- `CallSignalingService`: WebRTC session coordinator and PostgreSQL signaling layer.
- `CallLifecycleCoordinator` & `CallForegroundService`: Android background and notification persistence.
- `ConnectionHealthService`: Real-time network and latency classifier.
- `RichChatRealtimeService`: Supabase presence channels (typing, recording, online status).
- `StatusService`: Ephemeral story lifecycle and view metrics.
- `LocalLockService`: Biometric, PIN, and pattern verification.

---

## 4. Component Display System & Design System Architecture

### 4.1 Design Philosophy: The Chaty Design Standard
Chaty does not rely on ad-hoc styling. Every UI element adheres to an explicit design token hierarchy defined in `lib/ui/core/design_system/tokens/app_tokens.dart` and `lib/ui/core/design_system/gb_design_system.dart`:
- **Standard Modal Radius**: Mandated 10px (`kChatyModalRadius = 10.0`) for all cards, dialogs, and popups.
- **Micro-Interactions**: Haptic feedback on all taps (`ChatyHaptics`), smooth scale/fade page transitions (`ChatyPageTransitions`), and custom spring physics (`ChatyMotion`).
- **Responsive Scaffolding**: `ChatyScaffold` and `AdaptiveSplitView` provide fluid layouts across mobile, tablet, and desktop viewports.

### 4.2 Component Catalog & Usage

#### 1. Core Scaffolding & Atoms (`app_components.dart`, `tokens/app_tokens.dart`)
- **`ChatyScaffold`**: Wraps screens with safe-area bounds, scaffold background tokens, and app bar integration.
- **`ChatyAppBar`**: Custom header supporting leading back chevrons, interactive action items, and subtitle caption rows.
- **`ChatyBackButton`**: Apple HIG-styled back button with touch haptics and spring animation.
- **`ChatyCard`**: Standard card container with 10px corner radius, customizable border strokes, and surface elevation.
- **`ChatyButton`**: Primary, secondary, tonal, and outline button styles with built-in loading spinners and disabled states.
- **`ChatyTextField`**: Input field featuring floating labels, clear buttons, focus borders, and error validation states.
- **`ChatyAvatar`**: Adaptive user avatar supporting network image caching, gradient fallbacks, status indicator dots, and shapes (Circle, Squircle, Rounded Square).

#### 2. Modal Framework (`chaty_modal.dart`)
- **Structure**: A fixed header, scrollable body (`Flexible` + `SingleChildScrollView`), and fixed footer row.
- **Transitions**: Presented via `ChatyModal.show(...)` with a smooth 220ms scale-and-fade curve (`Curves.easeOutCubic`) over a darkened scrim (`Colors.black54`) with optional backdrop blur.
- **Specialized Modals**:
  - `SingleChoiceModal`: Selectable radio lists for theme and settings pickers.
  - `LockCredentialSetupModal`: Setup flow for PIN, Pattern, or Biometrics.
  - `TaskCreateEditModal`: Modal sheet for composing and modifying tasks.

#### 3. Shared Kits & Feedback (`chaty_kit.dart`)
- **`ChatyConfirmDialog`**: Unified destructive and confirmation alert dialogs.
- **`ChatyToast`**: Standardized snackbar notifications with floating pills.
- **`ChatyEmptyState`**: Empty container placeholder with icon, headline, and subtitle.
- **`ChatyBottomSheet`**: Sliding bottom sheets with grabber handles and rounded top corners.
- **`ChatyActionSheet`**: iOS-style action list with cancel button separation.

#### 4. WhatsApp / GB Modded Components (`gb_design_system.dart`)
- **`GbCustomSwitch`**: Custom pill switch featuring a minus (`-`) icon when disabled and a checkmark (`✓`) when active, dynamically colored by the accent theme.
- **`GbSectionCard`**: Settings card container grouping related options with dividers.
- **`GbSliderTile`**: Linear slider for fine adjustments (bubble radius, font scale, playback speed).
- **`GbRadioGroup`**: Styled single-selection option groups.
- **`GbColorTile`**: Interactive color preview tile launching the HSV color picker (`ChatyColorPicker`).

#### 5. Chat Bubbles & Delivery Ticks (`bubbles/`, `ticks/`)
- **`BubblePainter` & `BubbleGeometry`**: Custom painter rendering speech bubbles with customizable tails (right, left, none), rounded corners, and distinct styles:
  - `BubbleStyleId.ios`: Sleek, rounded corner bubbles with subtle tails.
  - `BubbleStyleId.modern`: Material 3 expressive bubbles.
  - `BubbleStyleId.minimal`: Sharp corners without tails.
  - `BubbleStyleId.curved`: Soft organic curves.
- **`DeliveryStatusIconPainter`**: Custom painter rendering tick states:
  - `DeliveryIconStyle.classic`: Standard double checks.
  - `DeliveryIconStyle.blueTicks`: Blue checkmark on delivery/read.
  - `DeliveryIconStyle.minimal`: Single/double clean hairline checks.
  - `DeliveryIconStyle.circles`: Circular filled indicators.

#### 6. Dynamic Effects & Overlays (`widgets/`)
- **`ClickParticleOverlay`**: Spawns subtle celebratory particles on touch interactions.
- **`FallingParticlesOverlay`**: Ambient background particle simulation (snow, stars, embers).
- **`EventToastOverlay`**: System-wide toast notification overlay displaying in-app alerts without interrupting conversation flow.

---

## 5. Core Logic, Subsystems & State Machines

### 5.1 Cryptographic & E2EE Subsystem (RFC 9420 MLS)
Chaty is built on **IETF RFC 9420 Messaging Layer Security (MLS)** using a custom client-side integration of `OpenMLS` in Rust via `flutter_rust_bridge` and `MlsE2eeService`:

```
               ┌──────────────────────────────────────┐
               │         Client Device Setup          │
               │  - Deterministic device ID           │
               │  - Ed25519 Signing Key (SecureStore) │
               │  - SQLCipher Ratchet DB (AES-256)    │
               └──────────────────┬───────────────────┘
                                  │
                                  ▼
               ┌──────────────────────────────────────┐
               │       KeyPackage Pool Manager        │
               │  - Target pool size: 10              │
               │  - Replenishment threshold: 5        │
               │  - Published to `e2ee_device_prekeys`│
               └──────────────────┬───────────────────┘
                                  │
         ┌────────────────────────┴────────────────────────┐
         │                                                 │
         ▼                                                 ▼
┌─────────────────────────────────┐       ┌─────────────────────────────────┐
│     1-on-1 Outgoing Session     │       │       Incoming Message Flow     │
│ 1. Claim recipient KeyPackage   │       │ 1. Receive MLS ciphertext       │
│ 2. Create MLS Group & Welcome   │       │ 2. Advance local ratchet        │
│ 3. Encrypt payload to ciphertext│       │ 3. Decrypt in SQLCipher         │
│ 4. Send via Supabase RPC        │       │ 4. Emit to ChatyBackendService  │
└─────────────────────────────────┘       └─────────────────────────────────┘
```

#### Security Boundaries
1. **Zero Plaintext Fallback**: If recipient devices are not yet registered, messages are stored in `PendingSecureSendStore` with `SecureSendPendingException` and retried automatically. **They are never sent in plaintext.**
2. **Key Storage**: Private keys are maintained in `FlutterSecureStorage` (Android Keystore / iOS Keychain).
3. **Ratchet Persistence**: Forward secrecy and post-compromise security states are stored solely in OpenMLS' encrypted SQLCipher database.

---

### 5.2 Outgoing Message Queue & Delivery State Machine
The `OutgoingMessageQueueEngine` provides a strictly ordered, resilient message outbox:

```
┌──────────────┐
│ Local Draft  │ ──> [Send Button Pressed]
└──────┬───────┘
       │
       ▼
┌──────────────┐   (Timer / Clock Icon)
│    Queued    │ ──> Placed in FIFO memory & persistent pending store
└──────┬───────┘
       │
       ├──> [MLS Encryption Completed]
       ├──> [Network Request Dispatched to Supabase]
       │
       ▼
┌──────────────┐   (Single Grey Tick)
│     Sent     │ ──> Server DB acknowledged ciphertext insertion
└──────┬───────┘
       │
       ├──> [Recipient Device receives & decrypts message]
       │
       ▼
┌──────────────┐   (Double Grey Ticks)
│  Delivered   │ ──> Delivery receipt ACK transmitted to sender
└──────┬───────┘
       │
       ├──> [Recipient opens conversation / Read receipt enabled]
       │
       ▼
┌──────────────┐   (Double Blue / Colored Ticks)
│     Read     │ ──> Read receipt ACK transmitted to sender
└──────────────┘

* On Network / Encryption Failure:
┌──────────────┐   (Red Exclamation Icon)
│    Failed    │ ──> Retried automatically with exponential backoff + jitter
└──────────────┘
```

- **Idempotency**: Every message is assigned a client-generated UUID (`clientMessageId`). The server RPC verifies this key, preventing duplicate messages on retries.

---

### 5.3 WebRTC Voice & Video Calling Subsystem
Calling is divided between two coordinated layers:
1. **Signaling Layer (`CallSignalingService`)**: Supabase PostgreSQL tables `call_sessions` and `call_ice_candidates` governed by RLS.
2. **Media Layer (`RTCPeerConnection`)**: WebRTC audio and video streaming using Google STUN/TURN servers.

```
Caller                                    Supabase (Signaling)                              Callee
  │                                                │                                          │
  │─── 1. Create Call Session (RPC) ──────────────>│                                          │
  │    Status: 'ringing'                           │─── 2. Realtime Push (New Session) ──────>│
  │                                                │                                          │ (Ringing Screen / Vibration)
  │─── 3. Publish SDP Offer ──────────────────────>│                                          │
  │                                                │<── 4. Accept Call (RPC) ─────────────────│
  │                                                │<── 5. Publish SDP Answer ────────────────│
  │<── 6. Receive SDP Answer ──────────────────────│                                          │
  │                                                │                                          │
  │<══════════════ 7. Exchange ICE Candidates via Realtime Channel ══════════════════════════>│
  │                                                │                                          │
  │<═══════════════════════ 8. PeerConnection Connected (P2P Media) ═════════════════════════>│
  │                                                │                                          │
  │ (Call Timer Starts)                            │                                          │ (Call Timer Starts)
```

#### Calling Features
- **In-App Picture-in-Picture (`InAppCallPip`, `ChatyCallIsland`)**: When the user navigates away from `OngoingCallScreen`, an interactive floating island appears on top of the app, displaying call duration, mute status, and expand controls.
- **Android Foreground Service (`CallForegroundService`)**: Keeps audio running when the app is backgrounded or the screen is turned off.
- **Call Presentation Controller (`CallPresentationController`)**: Coordinates audio routing between Speaker, Earpiece, and Bluetooth devices.

---

### 5.4 Collaborative Tasks & Workflow Synchronization
Tasks in Chaty are first-class collaboration entities embedded directly into conversations:
- **Model (`ChatTask`)**:
  - `id`, `conversationId`, `title`, `description`, `assigneeId`, `creatorId`
  - `priority`: Low, Medium, High, Urgent
  - `status`: Managed by a 7-stage state machine:
    $$\text{Todo} \longrightarrow \text{In Process} \longrightarrow \text{In Review} \longrightarrow \text{Testing} \longrightarrow \text{Done}$$
    *(Optional transitions: Cancelled, Reopened)*
- **Data Integrity**: Bijective mapping ensures local task models and Supabase PostgreSQL enum values match exactly without losing custom task properties during E2EE transport.

---

### 5.5 Status & Stories Subsystem (24h Ephemeral Updates)
`StatusService` implements a 24-hour disappearing story feed:
- **Media Types**: Text statuses with customizable gradient backgrounds, or full-screen photos and short videos.
- **Privacy Policy**: Granular audience controls:
  - *My Contacts*: Shared with reciprocal contacts.
  - *My Contacts Except...*: Block specific users from seeing the status.
  - *Only Share With...*: Whitelist specific contacts.
- **Anti-Revoke Retention**: Under GB privacy preferences, statuses deleted by contacts remain visible to the user with a "Deleted by sender" indicator.

---

### 5.6 Settings, Preferences & GB Customization Engine
The configuration engine is anchored in `ChatyPreferencesController` and backed by `PreferencesStorage`:
- **Namespaced Per User**: Preferences are stored in keys prefixed with `user_{userId}_*`, eliminating cross-account leakage.
- **Migration Engine (`PreferencesMigrator`)**: Safely upgrades schema versions (v1 → v2), removing legacy plaintext credentials into platform-secure storage.
- **Canonical Registry (`SettingsRegistry`)**: Every setting has exactly one canonical definition and route:
  - `cluster_account`: Profile details, username, avatar.
  - `cluster_privacy_presence`: Last seen, online status, read receipts, anti-delete, ghost mode.
  - `cluster_security`: Biometrics, PIN, pattern lock, hidden chats.
  - `cluster_home_customization`: Home screen styles, story layouts, floating action buttons.
  - `cluster_conversation`: Bubble shapes, delivery tick icons, font sizes, wallpapers.
  - `cluster_notifications`: Channel priorities, custom ringtones, vibrate patterns.

---

### 5.7 Security, Biometrics & Local Lock Layer
`LocalLockService` and `AppLockOverlay` protect sensitive messages:
- **Biometric Authentication**: Fingerprint / Face ID via `local_auth`.
- **PIN & Pattern Pad**: Implemented using `PatternLockPad` with custom dot animations and vibration haptics.
- **Auto-Lock Timer**: Configurable re-lock timeout (Immediately, 1 min, 5 min, 15 min, 30 min) triggered whenever the app transitions to `AppLifecycleState.paused`.
- **Hidden Locked Chats**: Users can move confidential conversations into a protected vault (`LockedChatsScreen`) that can be hidden from the home screen entirely and revealed only by entering a secret search code.

---

### 5.8 Camera, Lenses & Augmented Reality Effects
`CameraCaptureScreen` provides custom media creation capabilities:
- **Hardware Integration**: High-definition camera stream capture (`camera`) with flash modes, front/rear camera flip, and video recording.
- **Lens & Effect Engine (`EffectEngine`, `FaceTracker`, `LensOverlay`)**:
  - Live face detection landmarks.
  - Real-time shader overlays: Vintage Film, Cyberpunk Neon, Beauty Smoothing, Dynamic Vignette, B&W High Contrast.
- **Editing Pipeline**: Post-capture cropping, rotation, text annotation, and drawing before sending into the message draft tray.

---

## 6. Complete Project Extraction: File-by-File Inventory

### 6.1 Application Entry & Dependency Injection (`lib/`)

| File Path | Lines / Size | Core Purpose & Architecture |
|---|---|---|
| `lib/main.dart` | 822 lines / 31.4 KB | Application entry point (`main()`). Initializes Flutter bindings, Supabase client, GetIt service locator, and theme/template controllers. Manages root lifecycle observer (`WidgetsBindingObserver`), global app lock barrier (`AppLockOverlay`), floating call overlay host, and deferred platform service bootstrap. |
| `lib/injection/locator.dart` | 106 lines / 4.8 KB | Central GetIt configuration registering all singletons and factories across theme, backend, E2EE, WebRTC, calling, storage, notifications, and controllers. |

---

### 6.2 Core Domain Models (`lib/domain/models/`)

| File Path | Core Models & Responsibilities |
|---|---|
| `lib/domain/models/chat_message.dart` | `ChatMessage`, `MessageStatus` (`queued`, `sent`, `delivered`, `read`, `failed`), `MessageType` (`text`, `image`, `video`, `audio`, `document`, `location`, `contact`, `poll`), `MessageReaction`. Handles JSON serialization, MLS ciphertext encapsulation, and reply metadata. |
| `lib/domain/models/chat_task.dart` | `ChatTask`, `TaskPriority` (`low`, `medium`, `high`, `urgent`), `TaskStatus`. Encapsulates structured task data linked to conversation threads. |
| `lib/domain/models/call_state.dart` | `ChatyCallSession`, `CallType` (`voice`, `video`), `CallStatus` (`ringing`, `connecting`, `connected`, `ended`, `declined`, `missed`, `busy`), `CallDirection` (`incoming`, `outgoing`). |
| `lib/domain/models/connection_health.dart` | `ConnectionHealth`, `ConnectionStatus` (`connected`, `reconnecting`, `offline`, `degraded`), latency metrics, packet loss estimation. |
| `lib/domain/models/contact_relationship.dart` | `ContactRelationship`, relationship state (`none`, `pendingSent`, `pendingReceived`, `accepted`, `blocked`), mutual contact verification. |
| `lib/domain/models/conversation.dart` | `Conversation`, `ConversationType` (`direct`, `group`, `broadcast`), unread counts, pinned state, mute settings, draft message retention. |
| `lib/domain/models/other_models.dart` | Ancillary domain entities: `PollOption`, `ScheduledMessage`, `MediaAttachment`, `ChatReport`. |
| `lib/domain/models/preferences.dart` | Root user preferences container holding `PrivacyPreferences`, `SecurityPreferences`, `HomePreferences`, `ConversationPreferences`, `NotificationPreferences`, and `MediaPreferences`. |
| `lib/domain/models/privacy_publication_policy.dart` | Models governing audience projection for last-seen, online presence, status stories, and avatar visibility. |
| `lib/domain/models/task_workflow.dart` | `TaskWorkflow`, state transition rules, milestone mappings, and workflow validation logic. |
| `lib/domain/models/user_profile.dart` | `UserProfile`: User ID, username, display name, bio, avatar URL, phone number, public keys, and last active timestamp. |

---

### 6.3 Data Access & Service Layer (`lib/data/`)

#### Repositories
- `lib/data/repositories/chaty_data_store.dart`: In-memory reactive cache layer maintaining active conversations, message streams, contacts, and tasks. Acts as the intermediate data bridge between UI controllers and remote services.

#### Services
- `lib/data/services/backend_service.dart` (2,711 lines / 93.4 KB):
  The core backend gateway. Manages Supabase Auth sessions, login via username/email, password reset, user profile synchronization, contact querying, conversation loading, message transmission via Supabase RPCs, and group administration.
- `lib/data/services/mls_e2ee_service.dart` (1,140 lines / 39.1 KB):
  RFC 9420 Messaging Layer Security coordinator. Drives the OpenMLS Rust engine via `flutter_rust_bridge`. Manages device registration, Ed25519 key pairs, SQLCipher ratchet databases, one-time KeyPackage pools, MLS Welcome/Commit message parsing, and payload decryption.
- `lib/data/services/call_signaling_service.dart` (1,114 lines / 37.9 KB):
  WebRTC coordinator. Handles `RTCPeerConnection` configuration, local/remote `MediaStream` acquisition, SDP Offer/Answer negotiation, ICE candidate publication via Supabase Realtime, and call duration tracking.
- `lib/data/services/call_foreground_service.dart`:
  Binds Flutter calling logic to native Android Foreground Services via `flutter_foreground_task`, ensuring background audio resilience.
- `lib/data/services/call_history_service.dart`:
  Persists and queries call logs (incoming, outgoing, missed, duration) with search and filtering.
- `lib/data/services/call_lifecycle_coordinator.dart`:
  Listens to system interruptions (incoming phone calls, app minimize, network disconnect) and coordinates teardown or reconnect.
- `lib/data/services/chat_media_service.dart`:
  Handles media compression (`flutter_image_compress`), file type detection (`mime`), local caching, and encrypted upload to Supabase Storage.
- `lib/data/services/chaty_share_service.dart`:
  Handles system intent sharing (`share_plus`) allowing external content to be shared into Chaty chats.
- `lib/data/services/connection_health_classifier.dart` & `connection_health_service.dart`:
  Monitors network ping, Supabase socket connection, and WebRTC RTT to classify connection health (`healthy`, `degraded`, `offline`).
- `lib/data/services/contact_relationship_service.dart`:
  Manages contact list syncing with device address book (`flutter_contacts`), contact request workflows, and blocklists.
- `lib/data/services/gb_feature_backend_service.dart`:
  Handles server-side feature toggles and preferences for extended GB customization features.
- `lib/data/services/local_lock_service.dart`:
  Wraps `local_auth` biometrics, secure storage credential verification, and pattern hash checks using PBKDF2 with SHA-256 (120,000 iterations).
- `lib/data/services/local_snapshot_cache_service.dart` & `snapshot_codec.dart`:
  Encrypted disk snapshot caching for offline-first conversation and message loading.
- `lib/data/services/message_automation_service.dart`:
  Executes auto-reply rules and scheduled message dispatches.
- `lib/data/services/message_transport_compatibility_service.dart`:
  Detects client version differences and negotiates compatible message envelope formats.
- `lib/data/services/native_call_pip_service.dart`:
  Interacts with native platform Picture-in-Picture APIs on Android.
- `lib/data/services/notification_channel_manager.dart` & `notification_service.dart`:
  Configures Android Notification Channels, high-priority heads-up alerts, sound overrides, and in-app banner toasts.
- `lib/data/services/outgoing_message_queue_engine.dart`:
  Strict FIFO outbox message queue with exponential backoff, jitter, and automatic retry.
- `lib/data/services/pending_secure_send_store.dart`:
  Local SQLite persistent buffer holding messages awaiting recipient MLS KeyPackage registration.
- `lib/data/services/profile_media_service.dart`:
  Avatar image uploading, thumbnail generation, and profile asset caching.
- `lib/data/services/protected_resource_gate.dart`:
  Security gate verifying PIN/biometrics before opening locked chats or export files.
- `lib/data/services/push_token_service.dart`:
  Registers FCM / APNS push tokens with Supabase for background call and message notifications.
- `lib/data/services/rich_chat_realtime_service.dart`:
  Manages Supabase presence channels for typing indicators, recording state, and user online status.
- `lib/data/services/status_service.dart`:
  Handles uploading, viewing, audience filtering, and deletion of 24h status stories.
- `lib/data/services/username_login_service.dart`:
  Resolves `@username` handles to user IDs and emails via secure Supabase RPCs.
- `lib/data/services/voice_note_service.dart`:
  Handles high-fidelity audio recording (`record`) and variable-speed playback (`just_audio`).

---

### 6.4 Presentation Design System & Tokens (`lib/ui/core/`)

#### Design System & Primitives
- `lib/ui/core/design_system/tokens/app_tokens.dart`: Core tokens (spacing, radii, typography, elevations, transitions).
- `lib/ui/core/design_system/gb_design_system.dart` (1,967 lines / 66.5 KB): Complete GB customization component suite (`GbColors`, `GbCustomSwitch`, `GbSliderTile`, `GbRadioGroup`, `GbColorTile`, `GbSectionCard`).
- `lib/ui/core/design_system/settings_primitives.dart` (917 lines / 25.2 KB): Standardized settings page builder (`ChatySettingsPage`, `SettingsGroupCard`, `SettingsTile`, `SettingsSwitchTile`).
- `lib/ui/core/design_system/chaty_adaptive.dart`: Screen-size breakpoints and adaptive builders for mobile/tablet.
- `lib/ui/core/design_system/chaty_haptics.dart`: Standardized vibration haptics for buttons, toggles, errors, and success.
- `lib/ui/core/design_system/chaty_motion.dart`: Custom animation durations and cubic curves.
- `lib/ui/core/design_system/color_picker.dart`: HSV color picker dialog for custom UI styling.

#### Design System Components (`lib/ui/core/design_system/components/`)
- `chaty_modal.dart` (642 lines / 19.6 KB): Standard 10px-radius modal container with backdrop blur, scale/fade transitions, and fixed header/footer.
- `chaty_kit.dart` (942 lines / 29.6 KB): Reusable app dialogs (`ChatyConfirmDialog`, `ChatyToast`, `ChatyEmptyState`, `ChatyBottomSheet`).
- `app_components.dart` (983 lines / 30.9 KB): Core scaffolding (`ChatyScaffold`, `ChatyAppBar`, `ChatyBackButton`, `ChatyButton`, `ChatyTextField`).
- `messaging_components.dart` (30.4 KB): Message thread widgets, date separators, unread counters, and typing indicators.
- `signature_components.dart` (21.9 KB): Interactive digital signature drawing pads and verification widgets.
- `social_components.dart` (12.7 KB): Social media profile cards, bio widgets, and contact badges.
- `composer_components.dart`: Chat input composer, send button, attachment tray trigger, mic button.
- `call_activity_capsule.dart`: Floating active call status pill.
- `global_activity_host.dart`: Overlay container hosting global alerts, PiP views, and call bars.
- `media_draft_tray.dart`: Attachment preview tray displayed above the chat composer before sending.
- `adaptive_selection_panel.dart` & `adaptive_split_view.dart`: Split-view layout for large screen displays.
- `single_choice_modal.dart`: Radio-selection dialog modal.

#### Theming, Styles & Formatting
- `theme/theme_config.dart` (684 lines / 25.2 KB): 27-property theme configuration object supporting custom colors, layout modes, bubble styles, and font scaling.
- `theme/theme_controller.dart`: Dynamic theme manager publishing theme state changes to the app tree.
- `theme/theme_presets.dart` (18.2 KB): Pre-built theme styles (Emerald WhatsApp, Midnight OLED, Cyberpunk, Rose, Ocean, Minimal Light).
- `theme/semantic_colors.dart`: Derives WCAG-compliant accessible contrast colors for dark and light modes.
- `theme/chaty_page_transitions.dart`: Custom page route transitions (Cupertino push, Fade Through, Shared Axis, Scale).
- `bubbles/bubble_painter.dart`, `bubble_geometry.dart`, `bubble_style_registry.dart`: Custom chat bubble rendering engines.
- `ticks/delivery_status_icon_painter.dart`, `delivery_status_icon.dart`, `delivery_icon_style.dart`: Custom delivery tick mark rendering engines.
- `formatting/chat_formatters.dart`: Timestamps, file size formatting, duration strings, phone number maskers.

#### Global Controllers & Overlays
- `controllers/preferences_controller.dart` (28.9 KB): Manages user preferences state and persistence.
- `controllers/appearance_variant_controller.dart`: Controls layout mode variants (Bottom nav, Top bar, Drawer, Rail).
- `controllers/app_icon_controller.dart` (17.2 KB): Dynamic native app icon switching via Android activity aliases and iOS alternate icons.
- `widgets/click_particle_overlay.dart` & `falling_particles_overlay.dart`: Interactive and ambient particle engines.
- `widgets/event_toast_overlay.dart` (12.3 KB): High-priority system alert banner host.
- `widgets/chat_wallpaper.dart`: Renders solid, gradient, patterned, and custom image chat wallpapers.
- `widgets/app_avatar.dart`: High-performance cached user avatar with fallback badges.
- `menu/app_context_menu.dart` & `context_surface_engine.dart`: iOS-style long-press context menus with frosted glass backdrops.

---

### 6.5 Feature Modules (`lib/features/`)

#### 1. Authentication & Onboarding (`lib/features/auth/`, `onboarding/`)
- `welcome_screen.dart`: First-launch landing screen introducing privacy features.
- `login_screen.dart`: Authentication screen supporting username, email, and password.
- `register_screen.dart`: New account creation with real-time username availability checks.
- `forgot_password_screen.dart` & `create_new_password_screen.dart`: Password recovery flow.
- `otp_verification_screen.dart`: Multi-factor and phone OTP entry.
- `profile_setup_screen.dart`: Initial display name and avatar selection.
- `permission_onboarding_screen.dart`: Permission onboarding (Camera, Microphone, Contacts, Notifications).

#### 2. Chats & Conversations (`lib/features/chats/`)
- `main_navigation_shell.dart` (1,850 lines / 68.7 KB): Root bottom navigation shell hosting Chats, Updates, Calls, Tasks, and Settings.
- `chats_home_screen.dart` (2,375 lines / 99.6 KB): Main conversations list. Supports 8 layout styles, search, story carousels, unread badges, swipe actions, and floating action menus.
- `chat_detail_screen.dart` (3,148 lines / 119.4 KB): Full conversation screen. Manages message streaming, message composer, audio recording, media previews, task creation, reactions, and reply chains.
- `contact_info_screen.dart` & `group_info_screen.dart`: Participant profiles, shared media grids, encryption verification fingerprints, and mute toggles.
- `new_chat_screen.dart`: Contact directory picker for starting 1-on-1 or group conversations.
- `locked_chats_screen.dart`: Protected folder for hidden conversations accessible via PIN/biometrics.
- `linked_devices_qr_screen.dart`: QR code scanner/display for pairing desktop companion clients.

#### 3. Messages & Media Viewers (`lib/features/messages/`)
- `message_bubble.dart` (2,086 lines / 94.6 KB): Individual chat message renderer. Displays text, animated emojis, audio player with speed toggles, map snippets, attachments, tasks, reaction badges, and delivery ticks.
- `message_action_sheet.dart` & `message_action_registry.dart`: Long-press action sheet (Reply, Copy, Forward, Star, Edit, Delete, Pin, Info).
- `attachment_sheet.dart` & `chat_attachment_actions.dart`: Media selection sheet (Gallery, Camera, Document, Audio, Location, Poll, Contact).
- `emoji_picker_modal.dart` & `emoji_only.dart`: Categorized emoji picker with animated emoji rendering.
- `media_viewer_screen.dart`: Full-screen interactive image and video viewer with pinch-to-zoom and save actions.

#### 4. Voice & Video Calling (`lib/features/calls/`)
- `calls_screen.dart`: Call log history with tabs for All and Missed calls.
- `incoming_call_screen.dart`: Full-screen incoming call alert with Accept and Decline buttons and ringtone audio.
- `ongoing_call_screen.dart`: In-call interface featuring video feeds, mute, camera flip, speakerphone toggle, and minimize to PiP.
- `call_presentation_controller.dart`: Coordinates media routing and call screen visibility.
- `widgets/chaty_call_island.dart` & `in_app_call_pip.dart`: Draggable floating in-app Picture-in-Picture widgets.

#### 5. Collaborative Tasks (`lib/features/tasks/`)
- `tasks_screen.dart`: Consolidated task management dashboard. Filter by conversation, status, priority, and assignee.
- `task_detail_screen.dart`: Detailed task view with status update controls, history logs, and linked chat shortcuts.
- `task_create_edit_modal.dart`: Dialog for composing new tasks with title, description, priority, due date, and assignee.

#### 6. Ephemeral Status Updates (`lib/features/updates/`)
- `updates_screen.dart`: Stories and status feed screen displaying My Status and contact story circles.
- `status_view_session.dart`: Full-screen story viewer with auto-advancing progress bars, gesture pauses, and reply input.

#### 7. Camera & Lens Studio (`lib/features/camera/`)
- `camera_capture_screen.dart`: Custom camera capture viewport.
- `effects/effect_engine.dart`, `effect_registry.dart`, `effect_picker_sheet.dart`: Shader and color filter selection.
- `lens/face_tracker.dart`, `lens_overlay.dart`, `lens_carousel.dart`: Real-time AR face filter tracking and rendering.

#### 8. Settings & Customization Suite (`lib/features/settings/`)
- `settings_root_screen.dart` & `settings_screen.dart`: Master settings index.
- `theme_editor_screen.dart`: Interactive theme builder adjusting colors, corner radii, and density.
- `privacy/privacy_center_screen.dart`: Privacy controls (Last seen, Read receipts, Anti-Delete, Ghost Mode).
- `security/security_center_screen.dart`, `app_lock_overlay.dart`, `pattern_lock_pad.dart`: Lock configuration and biometric setup.
- `gb_features/gb_feature_center_screen.dart` & `gb_settings_screen.dart`: Full GB customization suite.
- `appearance/app_icon_settings_screen.dart` & `universal_appearance_screen.dart`: App icon changer and UI variants.
- `home/home_screen_settings_page.dart`: Home layout styles, story strip toggles, header configurations.
- `conversation/conversation_settings_page.dart`, `conversation_bubble_and_ticks_screen.dart`: Bubble shape and tick selectors.
- `calls/call_settings_screen.dart`: Call privacy, ringing preferences, and data saver toggles.
- `templates/templates_settings_screen.dart` & `component_override_screen.dart`: Live template and component override testing.

---

### 6.6 Supabase Infrastructure, Database Migrations & RPCs (`supabase/`)

The backend is fully defined in 47 SQL migration files under `supabase/migrations/`:
- `20260819064501_chaty_core.sql`: Core schema defining `users`, `conversations`, `conversation_members`, `messages`, and `tasks`.
- `20260819075231_chaty_production_backend.sql` & `20260819075312_lock_production_rpc_to_authenticated.sql`: Production RLS policies locking tables to authenticated users.
- `20260819082138_chat_media_storage.sql`: Storage bucket policies for encrypted chat media.
- `20260819082948_chat_polls.sql`: Poll schema and vote tracking.
- `20260819131133_add_realtime_status_updates.sql` & `20260819140654_status_updates_and_media_v2.sql`: Status story tables and automated 24-hour cleanup triggers.
- `20260819160140_gbwa_feature_engine.sql` & `20260819161121_gbwa_privacy_presence_enforcement.sql`: GB feature flags, presence masking, and privacy RPCs.
- `20260819191802_phase10_call_sessions.sql`: WebRTC signaling schema: `call_sessions` and `call_ice_candidates`.
- `20260819210935_secure_username_login_resolution.sql`: Secure RPC resolving username handles without revealing user email addresses.
- `20260822123756_e2ee_device_ciphertext_foundation.sql`: E2EE device registration and prekey storage.
- `20260822151854_mls_rfc9420_transport_foundation.sql` & `20260822152157_index_mls_transport_foreign_keys.sql`: RFC 9420 MLS protocol support, KeyPackage pooling, and Welcome/Commit transport.

---

### 6.7 Platform Native & Third-Party Dependencies

#### Core Dependencies (`pubspec.yaml`)
- `supabase_flutter: ^2.0.0`: Authentication, PostgreSQL querying, Realtime sockets, and Storage.
- `openmls: ^2.0.1` (with repository override in `third_party/openmls_android`): Rust-based RFC 9420 MLS implementation compiled with `flutter_rust_bridge: 2.12.0`.
- `flutter_webrtc: ^1.6.0`: Real-time audio/video peer connections and media streaming.
- `flutter_secure_storage: ^11.0.0`: Hardware-backed secure storage for private signing and database keys.
- `local_auth: '>=2.3.0 <3.0.0'`: Biometric authentication (Fingerprint, Touch ID, Face ID).
- `flutter_foreground_task: ^11.0.1`: Android Foreground Service manager for ongoing calls.
- `just_audio: ^0.10.5` & `record: ^7.1.1`: Voice note recording and variable-speed audio playback.
- `flutter_map: ^8.3.1` & `latlong2: ^0.10.1`: In-app OpenStreetMap integration for location sharing.
- `camera: ^0.11.0+2`, `image_picker: ^1.1.2`, `file_picker: ^12.0.0`: In-app camera and attachment picking.
- `animated_emoji: ^3.3.0` & `emoji_picker_flutter: ^4.5.3`: Rich animated emoji expressions.
- `get_it: ^9.2.1`: Dependency injection and service locator.

---

## 7. Database Schema & Backend Contracts

### Key Database Tables

```sql
-- Conversations Table
CREATE TABLE public.conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type TEXT NOT NULL CHECK (type IN ('direct', 'group', 'broadcast')),
    title TEXT,
    avatar_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_message_at TIMESTAMPTZ,
    metadata JSONB DEFAULT '{}'::jsonb
);

-- Conversation Members
CREATE TABLE public.conversation_members (
    conversation_id UUID REFERENCES public.conversations(id) ON DELETE CASCADE,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    role TEXT NOT NULL DEFAULT 'member' CHECK (role IN ('owner', 'admin', 'member')),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_read_at TIMESTAMPTZ,
    PRIMARY KEY (conversation_id, user_id)
);

-- Messages Table (E2EE Ciphertext Envelopes)
CREATE TABLE public.messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES auth.users(id),
    client_message_id TEXT NOT NULL UNIQUE,
    ciphertext TEXT NOT NULL,
    protocol_suite TEXT NOT NULL DEFAULT 'mls-rfc9420-v1',
    message_type TEXT NOT NULL DEFAULT 'text',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    edited_at TIMESTAMPTZ,
    reply_to_id UUID REFERENCES public.messages(id),
    metadata JSONB DEFAULT '{}'::jsonb
);

-- Tasks Table
CREATE TABLE public.chat_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
    creator_id UUID NOT NULL REFERENCES auth.users(id),
    assignee_id UUID REFERENCES auth.users(id),
    title TEXT NOT NULL,
    description TEXT,
    priority TEXT NOT NULL DEFAULT 'medium' CHECK (priority IN ('low', 'medium', 'high', 'urgent')),
    status TEXT NOT NULL DEFAULT 'todo' CHECK (status IN ('todo', 'in_progress', 'in_review', 'testing', 'done', 'cancelled', 'reopened')),
    due_date TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- WebRTC Call Sessions
CREATE TABLE public.call_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    caller_id UUID NOT NULL REFERENCES auth.users(id),
    callee_id UUID NOT NULL REFERENCES auth.users(id),
    call_type TEXT NOT NULL CHECK (call_type IN ('voice', 'video')),
    status TEXT NOT NULL DEFAULT 'ringing' CHECK (status IN ('ringing', 'connecting', 'connected', 'ended', 'declined', 'missed', 'busy')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at TIMESTAMPTZ,
    duration_seconds INT DEFAULT 0
);
```

---

## 8. Summary & Key Architectural Principles

1. **Defense in Depth**: Client data integrity is protected by multiple defense layers: device-level secure key storage, SQLCipher database encryption, PBKDF2 hash stretching, and PostgreSQL Row Level Security.
2. **Deterministic UI State**: The entire user interface reacts dynamically to changes merged across controllers (`ThemeController`, `ChatyPreferencesController`, `AppearanceVariantController`, `ChatyBackendService`), guaranteeing instantaneous updates when settings or themes are adjusted.
3. **Fail-Safe Offline Resiliency**: Every user action is written to local storage before network dispatch. Network disconnects transition the app gracefully into offline mode, with pending messages and tasks automatically queued for synchronization upon reconnection.
4. **Modularity & Maintainability**: Clean boundaries between the UI layer, domain models, and service implementations ensure that any component (such as replacing WebRTC signaling or updating MLS ciphersuites) can evolve without impacting unrelated application modules.
