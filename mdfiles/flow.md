# Chaty End-to-End Flow Specification

## 1. Global rendering flow

```mermaid
flowchart LR
    U[User interaction] --> E[Semantic event]
    E --> C[Existing controller/service]
    C --> S[Semantic state]
    S --> R[Variant resolver]
    R --> V[Selected renderer]
    V --> T[Theme tokens]
    T --> M[Motion profile]
    M --> P[Paint / layout]
```

## 2. App startup → customized UI

```mermaid
flowchart TD
    A[App launch] --> B[Load secure/session state]
    B --> C[Initialize ThemeController]
    C --> D[Load Preferences]
    D --> E[Resolve customization snapshot]
    E --> F[Resolve accessibility/window capabilities]
    F --> G[Build MainNavigationShell]
    G --> H[Resolve nav + header + list variants]
    H --> I[Render screen]
```

## 3. Bottom navigation flow

```mermaid
flowchart TD
    A[Tap destination] --> B[Pressed state]
    B --> C[Haptic if enabled]
    C --> D[Emit destination intent]
    D --> E[Navigation controller/state]
    E --> F{Same destination?}
    F -->|Yes| G[Scroll to top / existing behavior]
    F -->|No| H[Route/state switch]
    H --> I[Animate active indicator]
    I --> J[Persist no navigation style change]
```

## 4. Header flow

```mermaid
flowchart TD
    A[Header action] --> B{Action}
    B -->|Back| C[Pop current route]
    B -->|Title| D[Open profile/info]
    B -->|Call| E[Existing call coordinator]
    B -->|Search| F[Open search state]
    B -->|Overflow| G[Open anchored context menu]
```

## 5. Chat composer flow

```mermaid
flowchart TD
    A[Composer idle] --> B{Input}
    B -->|Text tap| C[Focus field]
    C --> D[Keyboard opens]
    D --> E[Text changes]
    E --> F{Mode}
    F -->|Normal| G[Show Send]
    F -->|Reply| H[Keep reply context]
    F -->|Edit| I[Keep edit context]
    F -->|Attachment draft| J[Show media draft tray]
    B -->|Mic press| K[Permission check]
    K -->|Allowed| L[Recording]
    K -->|Denied| M[Permission guidance]
    G --> N[Send event]
    N --> O[Existing FIFO queue]
    O --> P[MLS encrypt]
    P --> Q[Network dispatch]
    Q --> R[Message status flow]
```

## 6. Message bubble flow

```mermaid
flowchart TD
    A[ChatMessage] --> B[Resolve message type]
    B --> C[Resolve semantic status/reply/reaction]
    C --> D[Resolve bubble variant]
    D --> E[Measure content]
    E --> F[Paint bubble geometry]
    F --> G[Render content]
    G --> H[Render metadata/ticks]
    H --> I[Render reactions]
```

## 7. Message long-press flow

```mermaid
flowchart TD
    A[Long press message] --> B[Enter context state]
    B --> C[Open context menu/action surface]
    C --> D[Build action list from message registry]
    D --> E{Action}
    E -->|Reply| F[Set reply context]
    E -->|Copy| G[Clipboard action]
    E -->|Forward| H[Forward flow]
    E -->|Star| I[Star flow]
    E -->|Edit| J[Edit flow]
    E -->|Delete| K[Delete flow]
    E -->|Pin| L[Pin flow]
    E -->|Info| M[Message info]
```

## 8. Reaction flow

```mermaid
flowchart TD
    A[Tap/double tap/long press] --> B{Gesture}
    B -->|Quick| C[Default reaction]
    B -->|Long press| D[Reaction rail]
    D --> E[Select reaction]
    C --> E
    E --> F[Optimistic local state]
    F --> G[Idempotent reaction event]
    G --> H[Remote sync]
    H --> I[Animated confirmation]
```

## 9. Attachment flow

```mermaid
flowchart TD
    A[Attachment trigger] --> B[Resolve context]
    B --> C[Open attachment tray variant]
    C --> D{Action}
    D -->|Gallery| E[Media picker]
    D -->|Camera| F[Camera capture]
    D -->|Document| G[File picker]
    D -->|Audio| H[Audio selection]
    D -->|Location| I[Location picker]
    D -->|Poll| J[Poll composer]
    D -->|Contact| K[Contact picker]
    E --> L[Media draft tray]
    F --> L
    G --> L
    H --> L
    L --> M[Edit/crop/annotate]
    M --> N[Preview]
    N --> O[Send event]
```

## 10. Modal flow

```mermaid
flowchart TD
    A[Trigger action] --> B[OverlayManager.open]
    B --> C[Compute available bounds/insets]
    C --> D[Choose overlay family]
    D --> E[Mount scrim/surface]
    E --> F[Move focus inward]
    F --> G[Run enter motion]
    G --> H[User interacts]
    H --> I{Close reason}
    I -->|Confirm| J[Commit action]
    I -->|Cancel| K[Discard transient input]
    I -->|Outside| K
    I -->|Back/Escape| K
    J --> L[Close]
    K --> L
    L --> M[Restore focus]
```

## 11. Customization live preview flow

```mermaid
flowchart TD
    A[Open customization] --> B[Load current snapshot]
    B --> C[Render component gallery]
    C --> D[Select variant]
    D --> E[Create preview override]
    E --> F[Render live sample]
    F --> G{Apply?}
    G -->|No| H[Revert preview]
    G -->|Yes| I[Persist snapshot]
    I --> J[ThemeController update]
    J --> K[All subscribed components rebuild narrowly]
    K --> L[Confirmation]
```

## 12. Chat-specific theme flow

```mermaid
flowchart TD
    A[Open chat customization] --> B[Load global theme]
    B --> C[Load conversation override]
    C --> D[Merge fields]
    D --> E[Preview]
    E --> F[Save conversation override]
    F --> G[Persist namespaced key]
    G --> H[Open chat uses override]
    H --> I[Other chats remain unchanged]
```

## 13. Recording flow

```mermaid
flowchart TD
    A[Mic press] --> B[Permission]
    B -->|Allowed| C[Start recording]
    C --> D[Live waveform]
    D --> E{Gesture}
    E -->|Release| F[Stop]
    E -->|Lock| G[Continue hands-free]
    E -->|Cancel| H[Discard]
    F --> I[Preview optional]
    I --> J[Send/Discard]
    J --> K[Existing queue]
```

## 14. Media viewer flow

```mermaid
flowchart TD
    A[Tap media] --> B[Open full-screen viewer]
    B --> C[Resolve media type]
    C --> D[Decode/cached asset]
    D --> E[Display immersive content]
    E --> F{Gesture/action}
    F -->|Swipe back| G[Close]
    F -->|Pinch| H[Zoom]
    F -->|Save/share| I[Existing media/share service]
    F -->|More| J[Context actions]
```

## 15. Call PiP flow

```mermaid
flowchart TD
    A[Connected call] --> B[Call presentation controller]
    B --> C{Navigate away?}
    C -->|No| D[Ongoing call screen]
    C -->|Yes| E[Create in-app PiP/island]
    E --> F[Drag/expand/mute]
    F -->|Expand| D
    F -->|End| G[Call lifecycle teardown]
```

## 16. Stories/status flow

```mermaid
flowchart TD
    A[Updates screen] --> B[Load story list]
    B --> C[Resolve privacy-filtered set]
    C --> D[Render story circles]
    D --> E[Open viewer]
    E --> F[Progress timer]
    F --> G{Gesture}
    G -->|Tap next| H[Next story]
    G -->|Hold| I[Pause]
    G -->|Reply| J[Reply composer]
    G -->|Back| K[Close]
    H --> F
    I --> F
```

## 17. Settings control flow

```mermaid
flowchart TD
    A[Settings tile] --> B[Read current semantic value]
    B --> C[Render control variant]
    C --> D[User changes value]
    D --> E[Validate constraints]
    E -->|Invalid| F[Inline error]
    E -->|Valid| G[Update controller]
    G --> H[Persist namespaced preference]
    H --> I[Publish state]
    I --> J[Dependent components re-resolve]
```

## 18. Generic component behavior template

```mermaid
flowchart LR
    INPUT[User input] --> STATE[Semantic state]
    STATE --> CAP[Capability check]
    CAP --> VAR[Variant resolve]
    VAR --> LAYOUT[Measure/layout]
    LAYOUT --> PAINT[Render]
    PAINT --> MOTION[State transition motion]
    PAINT --> A11Y[Accessibility semantics]
    PAINT --> PERF[Performance budget]
```

## 19. State flow for all interactive controls

```mermaid
stateDiagram-v2
    [*] --> Idle
    Idle --> Focused: focus/tap
    Idle --> Pressed: pointer down
    Focused --> Pressed: activate
    Pressed --> Focused: release
    Focused --> Disabled: external constraint
    Pressed --> Loading: async action
    Loading --> Success: complete
    Loading --> Error: fail
    Error --> Focused: retry/correct
    Success --> Idle: settle
    Disabled --> Idle: constraint clears
```

# 19. Primitive component micro-flows

## ChatyButton
```mermaid
flowchart TD
A[Idle] --> B[Pointer/touch down]
B --> C[Pressed visual + optional haptic]
C --> D{Enabled?}
D -->|No| A
D -->|Yes| E[Invoke command once]
E --> F{Async?}
F -->|No| A
F -->|Yes| G[Loading/disabled duplicate tap]
G --> H[Success or failure state]
H --> A
```

## ChatyTextField
```mermaid
flowchart TD
A[Unfocused] --> B[Focus]
B --> C[Focused]
C --> D[Input]
D --> E{Valid?}
E -->|Yes| F[Normal value state]
E -->|No| G[Inline error]
F --> H[Submit/next]
G --> H
H --> I[Persist/command]
```

## Switch / slider / radio
```mermaid
flowchart TD
A[Current value] --> B[User changes value]
B --> C[Clamp/validate]
C --> D[Optimistic visual update]
D --> E[Persist canonical setting]
E --> F{Success?}
F -->|Yes| G[Publish state]
F -->|No| H[Rollback + feedback]
G --> A
H --> A
```

## Avatar
```mermaid
flowchart TD
A[Identity] --> B{Cached asset?}
B -->|Yes| C[Render cached image]
B -->|No| D[Render placeholder]
D --> E[Fetch asset]
E --> F{Valid?}
F -->|Yes| G[Swap image]
F -->|No| H[Initials/fallback badge]
C --> I[Presence overlay]
G --> I
H --> I
```

## Toast / event banner
```mermaid
flowchart TD
A[Event] --> B[Classify severity]
B --> C{Blocking?}
C -->|Yes| D[Dialog/sheet contract]
C -->|No| E[Toast/banner]
E --> F{Action available?}
F -->|Yes| G[User action]
F -->|No| H[Auto dismiss]
G --> I[Execute command]
```

## Color tile / picker
```mermaid
flowchart TD
A[Current semantic color] --> B[Open picker]
B --> C[Create preview value]
C --> D[Contrast validation]
D --> E{Valid?}
E -->|Yes| F[Live preview]
E -->|No| G[Warn + nearest safe suggestion]
F --> H[Apply or Cancel]
H -->|Apply| I[Persist transaction]
H -->|Cancel| J[Discard preview]
```

## List row
```mermaid
flowchart TD
A[Render row] --> B{Gesture}
B -->|Tap| C[Open destination]
B -->|Swipe| D[Reveal row actions]
B -->|Long press| E[Context/selection mode]
D --> F{Action selected?}
F -->|No| A
F -->|Yes| G[Execute canonical action]
E --> H[Selection + action surface]
```

## Empty state
```mermaid
flowchart TD
A[No visible content] --> B{Loading?}
B -->|Yes| C[Skeleton/loading state]
B -->|No| D{Error?}
D -->|Yes| E[Recovery state]
D -->|No| F[Empty state]
F --> G[Primary next action]
E --> H[Retry]
```

## Context menu
```mermaid
flowchart TD
A[Long press / secondary input] --> B[Compute anchor]
B --> C[Compute available viewport]
C --> D[Choose menu position]
D --> E[Open focus-managed menu]
E --> F[Select action]
F --> G[Command registry]
G --> H{Destructive?}
H -->|Yes| I[Confirm dialog]
H -->|No| J[Execute]
```

## Media draft tray
```mermaid
flowchart TD
A[Attachment selected] --> B[Build local draft]
B --> C[Preview + metadata]
C --> D{Edit?}
D -->|Yes| E[Media editor]
E --> C
D -->|No| F{Remove?}
F -->|Yes| G[Delete draft item]
F -->|No| H[Send through existing composer]
```

## Task card
```mermaid
flowchart TD
A[Task summary] --> B{Tap}
B --> C[Open task detail]
C --> D{Status command}
D --> E[Validate workflow transition]
E --> F{Allowed?}
F -->|Yes| G[Persist transition]
F -->|No| H[Show reason]
G --> I[Update chat/task state]
```

## Call capsule / PiP
```mermaid
flowchart TD
A[Call active] --> B[Render capsule]
B --> C{User taps}
C -->|Expand| D[Restore call screen]
C -->|Drag| E[Constrain + move + snap]
C -->|Action| F[Mute/minimize/etc]
F --> G[Call controller]
```

## Camera lens picker
```mermaid
flowchart TD
A[Camera ready] --> B[Load effect registry]
B --> C[Render preview rail]
C --> D[User selects lens]
D --> E[Update effect engine]
E --> F[Render live preview]
F --> G[Capture]
G --> H[Create media draft]
```

## App lock overlay
```mermaid
flowchart TD
A[Protected transition] --> B[Show lock surface]
B --> C{Credential type}
C -->|Biometric| D[Platform biometric]
C -->|PIN| E[PIN verify]
C -->|Pattern| F[Pattern verify]
D --> G{Valid?}
E --> G
F --> G
G -->|No| H[Retry/backoff]
G -->|Yes| I[Unlock + resume original action]
```

## Search
```mermaid
flowchart TD
A[Open search] --> B[Focus field]
B --> C[Debounced query]
C --> D[Local index/filter]
D --> E{Need remote?}
E -->|Yes| F[Remote search]
E -->|No| G[Local results]
F --> H[Merge authoritative results]
G --> H
H --> I[Select result]
I --> J[Open destination]
```
