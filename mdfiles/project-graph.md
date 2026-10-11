# Chaty Project Graph — System and Component Dependency Map

## 1. Product graph

```mermaid
flowchart TB
  APP[Chaty App] --> AUTH[Auth & Onboarding]
  APP --> NAV[Main Navigation]
  APP --> CHAT[Chats]
  APP --> UPDATES[Updates / Stories]
  APP --> CALLS[Calls]
  APP --> TASKS[Tasks]
  APP --> SETTINGS[Settings / Customization]
  APP --> SECURITY[Security / App Lock]
  APP --> CAMERA[Camera / Lenses]

  NAV --> CHAT
  NAV --> UPDATES
  NAV --> CALLS
  NAV --> TASKS
  NAV --> SETTINGS

  CHAT --> LIST[Conversation List]
  CHAT --> DETAIL[Chat Detail]
  DETAIL --> COMPOSER[Chat Composer]
  DETAIL --> MSG[Message Renderer]
  DETAIL --> MEDIA[Media Viewer / Draft]
  DETAIL --> ACTIONS[Message Actions]
  DETAIL --> TASKCARD[Embedded Task]
  DETAIL --> CALLUI[Call Activity / PiP]

  SETTINGS --> THEME[Theme Editor]
  SETTINGS --> APPEAR[Appearance Variants]
  SETTINGS --> PRIVACY[Privacy]
  SETTINGS --> SECSET[Security]

  CAMERA --> LENS[Lens Engine]
  CAMERA --> DRAFT[Media Draft]
  DRAFT --> COMPOSER

  AUTH --> SERVICES
  CHAT --> SERVICES
  UPDATES --> SERVICES
  CALLS --> SERVICES
  TASKS --> SERVICES
  SETTINGS --> SERVICES
  SECURITY --> SERVICES
  SERVICES[Domain/Data Services] --> LOCAL[Local Cache / SQLCipher]
  SERVICES --> SUPA[Supabase / Realtime / Storage]
  SERVICES --> CRYPTO[MLS E2EE]
  SERVICES --> RTC[WebRTC]
```

## 2. UI architecture graph

```mermaid
flowchart LR
  SCREEN[Feature Screen] --> CTRL[Controller / State]
  CTRL --> SEM[Semantic Component Model]
  SEM --> LAYOUT[Adaptive Layout Policy]
  SEM --> SKIN[Visual Skin Renderer]
  SKIN --> MOTION[Motion Recipe]
  SKIN --> ICON[Custom SVG Icon]
  SKIN --> TOKENS[Semantic Tokens]
  LAYOUT --> TOKENS
  MOTION --> TOKENS
  CTRL --> SERVICE[Domain/Data Service]
  SERVICE --> STATE[Authoritative State]
  STATE --> CTRL
```

## 3. Customization dependency graph

```mermaid
flowchart TD
  DEFAULT[App Default] --> GLOBAL[Global User Theme]
  GLOBAL --> CHAT_THEME[Per Conversation Override]
  CHAT_THEME --> PREVIEW[Transient Preview]
  PREVIEW --> APPLY[Commit]
  PREVIEW --> CANCEL[Discard]
  APPLY --> CONTROLLER[Theme + Appearance Controllers]
  CONTROLLER --> NAV2[Navigation Renderer]
  CONTROLLER --> HDR2[Header Renderer]
  CONTROLLER --> COMP2[Composer Renderer]
  CONTROLLER --> BUB2[Bubble Renderer]
  CONTROLLER --> TICK2[Tick Renderer]
  CONTROLLER --> OVL2[Overlay Renderer]
  CONTROLLER --> WALL[Wallpaper Renderer]
```

## 4. Message lifecycle graph

```mermaid
flowchart LR
  DRAFT[Draft] --> Q[Queued]
  Q --> ENC[MLS Encrypt]
  ENC --> SEND[Dispatch]
  SEND --> SENT[Sent]
  SENT --> DELIV[Delivered]
  DELIV --> READ[Read]
  ENC --> FAIL[Failed]
  SEND --> FAIL
  FAIL --> RETRY[Retry]
  RETRY --> Q
```

## 5. “Single source of truth” graph

```text
User action
   │
   ▼
Visual component
   │  emits semantic command
   ▼
Controller / coordinator
   │
   ├── validation
   ├── permissions/capability
   └── service invocation
          │
          ▼
Authoritative domain state
          │
          ▼
Reactive controller state
          │
          ▼
Only affected renderers rebuild
```

## 6. Variant containment rule

```text
FORBIDDEN:
NavigationSkin -> Supabase
BubbleSkin -> MessageRepository
ModalSkin -> PreferencesStorage
TickSkin -> DeliveryStateMachine

ALLOWED:
NavigationSkin -> semantic navigation item
BubbleSkin -> semantic message
ModalSkin -> semantic modal model
TickSkin -> MessageStatus
```

This graph is the guardrail against creating one business-logic implementation per visual theme.
