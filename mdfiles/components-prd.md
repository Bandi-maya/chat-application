# Chaty Components PRD — Complete Variant Catalog

## 0. Current baseline inventory

The existing project already contains the following important custom component families:

| Family | Current baseline |
|---|---|
| Scaffold | `ChatyScaffold`, `AdaptiveSplitView` |
| Header | `ChatyAppBar`, `ChatyBackButton` |
| Surface | `ChatyCard` |
| Controls | `ChatyButton`, `ChatyTextField`, `GbCustomSwitch`, `GbSliderTile`, `GbRadioGroup`, `GbColorTile` |
| Identity | `ChatyAvatar`, `app_avatar` |
| Modal | `ChatyModal`, `SingleChoiceModal`, `LockCredentialSetupModal`, `TaskCreateEditModal` |
| Feedback | `ChatyConfirmDialog`, `ChatyToast`, `ChatyEmptyState`, `EventToastOverlay` |
| Sheets | `ChatyBottomSheet`, `ChatyActionSheet`, `adaptive_selection_panel` |
| Messaging | `message_bubble`, `messaging_components`, composer, media draft tray |
| Bubble | `BubblePainter`, `BubbleGeometry`, bubble style registry |
| Ticks | delivery status icon painter + style registry |
| Effects | click particles, falling particles, wallpapers |
| Menu | context menu + context surface engine |
| Theme | theme config/controller/presets/semantic colors |
| Navigation | appearance variant controller; bottom/top/drawer/rail concepts |
| Call surfaces | call activity capsule, call island, in-app PiP |
| Camera | effect engine, effect registry, face tracker, lens overlay/carousel |

## 1. Navigation variants

| ID | Name | Visual idea | Best use | Interaction |
|---|---|---|---|---|
| NAV-01 | Classic Label Bar | Familiar icon + label row | Default | Tap destination; badge; long label truncates to accessible tooltip |
| NAV-02 | Floating Dock | Elevated compact dock | Expressive themes | Tap; dock lifts 1–2dp on press |
| NAV-03 | Capsule Rail | Selected item sits in a contained capsule | Minimal themes | Selection morphs, no bounce |
| NAV-04 | Split Segments | Even segments with strong active contrast | Productivity mode | Fast scanning, no hidden actions |
| NAV-05 | Glass Dock | Translucent surface over content | Visual themes | Blur is bounded; disabled when performance profile disallows |
| NAV-06 | Compact Icon Dock | Icon-first, labels on accessibility/long press | Dense screens | Tap + tooltip |
| NAV-07 | Orbit | Active destination has a radial indicator around icon | Experimental | Small motion only; no travel distance jump |
| NAV-08 | Elevated Pill | One continuous surface with animated active cutout | Bold themes | Active cutout slides between items |
| NAV-09 | Line Marker | Minimal underline/side marker | Editorial themes | Marker moves with spring |
| NAV-10 | Adaptive Rail | Same visual language becomes vertical rail at wide widths | Tablets/desktop | Keyboard and pointer-friendly |

### Navigation button contract

`onTap(destination)` must call the same route/state transition regardless of skin.

Pressed state:
- visual scale 0.97–1.0 or bounded offset
- haptic only where enabled
- active indicator animation under 220ms by default
- no repeating motion after selection

Badge rules:
- unread count visible when >0
- `99+` cap for compact displays
- badge never shifts icon anchor between states
- badge announcement available to assistive technologies

## 2. Header variants

| ID | Name | Distinctive layout | Use |
|---|---|---|---|
| HDR-01 | Standard Identity | Back + avatar + title + status + actions | Default |
| HDR-02 | Large Context | Larger identity cluster, collapses while scrolling | Chat details/profile |
| HDR-03 | Compact | Small title and dense actions | Media/settings |
| HDR-04 | Search-First | Search is the primary trailing action or inline field | Lists |
| HDR-05 | Glass Strip | Translucent with bounded blur | Theme presets |
| HDR-06 | Profile-Led | Avatar dominates, status wraps beneath | Social-like profile chats |
| HDR-07 | Selection Header | Count + contextual actions | Multi-select |
| HDR-08 | Call Context | Identity + call status + elapsed state | Ongoing conversations |

### Header button semantics

- Back: return one navigation level; never used to dismiss a modal if a dedicated close/cancel affordance is expected.
- Overflow: opens contextual menu anchored to the header.
- Search: enters search without losing current route context.
- Call/video: invokes existing call coordinator.
- Title tap: opens existing profile/info route.

## 3. Composer variants

| ID | Name | Inspiration family | Unique treatment |
|---|---|---|---|
| CMP-01 | Chaty Capsule | Existing Chaty baseline + WhatsApp familiarity | Balanced attachment/emoji/send/mic |
| CMP-02 | Telegram Dense | Telegram | Dense action spacing; attachment first |
| CMP-03 | iMessage Expressive | Apple Messages | App/action rail and rich extensions |
| CMP-04 | Messenger Action Row | Messenger | Strong action strip around text input |
| CMP-05 | Signal Quiet | Signal | Calm privacy-first input, limited decoration |
| CMP-06 | Instagram Media-First | Instagram | Media/sticker/GIF priority |
| CMP-07 | Lens-First | Snapchat | Camera/lens affordance takes primary shortcut |
| CMP-08 | Command Composer | Chaty-original | Context-aware action prioritization and keyboard command support |

### Composer states

`idle → focused → typing → reply/edit → media-draft → recording → sending → send-complete`

Edge states:

`offline`, `queued`, `failed`, `permission-denied`, `locked`, `scheduled`, `view-once`, `disappearing-mode`.

### Composer button matrix

| Control | Idle | Typing | Reply/Edit | Recording | Media draft |
|---|---|---|---|---|---|
| Attachment | enabled | enabled | enabled | hidden/disabled | hidden/secondary |
| Camera | enabled | enabled | enabled | hidden | preview/editor action |
| Emoji | enabled | enabled | enabled | hidden | optional |
| Send | hidden if empty | primary | primary | hidden | primary |
| Mic | primary if empty | secondary | secondary | cancel/lock/stop |
| More | optional | optional | optional | hidden | editor menu |

## 4. Bubble families

### Existing

- iOS-like
- Modern
- Minimal
- Curved

### New

- `BUB-05 Angular Tail` — restrained geometric tail
- `BUB-06 Threaded` — reply-aware visual spine
- `BUB-07 Fold` — asymmetric corner that subtly encodes direction
- `BUB-08 Soft Block` — no tail, strong spacing and metadata separation
- `BUB-09 Editorial` — text-first, near-zero chrome
- `BUB-10 Pixel Grid` — experimental, limited to custom themes

### Bubble rules

Every bubble gets:

- max width based on usable chat width, not screen width
- intrinsic height
- timestamp anchored without covering text
- delivery tick space reserved before paint
- reaction tray that never changes line wrapping after it appears
- selectable semantic region

## 5. Tick packs

### Existing

- Classic double check
- Blue ticks
- Minimal hairline
- Circle indicators

### New

- `TICK-05 Split Arc` — sent/delivered/read through arc segments
- `TICK-06 Dot Pair` — compact two-dot progression
- `TICK-07 Diamond` — geometric read-state indicator
- `TICK-08 Pulse Ring` — animated only at state transition
- `TICK-09 Bar` — low-noise linear status
- `TICK-10 Glyph` — tiny custom glyph states

### Tick behavior

State source remains `MessageStatus`.

`queued` → queued indicator
`sent` → sent indicator
`delivered` → delivered indicator
`read` → read indicator
`failed` → failure indicator + retry affordance

The selected tick skin may change geometry/color/animation only. It may not reinterpret message status.

## 6. Popup and modal families

| ID | Type | When |
|---|---|---|
| OVL-01 | Center dialog | Small decision |
| OVL-02 | Alert dialog | Critical/destructive decision |
| OVL-03 | Bottom sheet | Scoped short task |
| OVL-04 | Full-height sheet | Rich but temporary task |
| OVL-05 | Side sheet | Expanded windows |
| OVL-06 | Anchored popover | Contextual details |
| OVL-07 | Context menu | Long press / contextual actions |
| OVL-08 | Action sheet | Multiple explicit actions |
| OVL-09 | Command palette | Searchable command set |
| OVL-10 | Toast/banner | Non-blocking status |

All overlays use a centralized overlay manager and focus/dismissal contract.

## 7. List row variants

- Dense avatar-first
- Spacious content-first
- Time-aligned
- Unread-emphasis
- Pinned-grouped
- Story-overlay row
- Presence-rich row
- Minimal line row
- Two-line message-preview row
- Media-preview row

Row click = open destination.
Row swipe = contextual actions only.
Long press = selection/context menu according to screen capability.

## 8. Attachment tray variants

1. Grid of labeled actions.
2. Camera-first carousel.
3. Wheel/radial tray (experimental).
4. Adaptive two-row quick actions + scrollable second row.

Actions remain canonical:
Gallery, Camera, Document, Audio, Location, Poll, Contact.

## 9. Reactions

Reference behavior:

- quick reaction via double-tap where the product context allows
- long-press opens full reaction set
- reaction selection is visually distinct from message selection
- each reaction may have a short entrance animation
- changing/removing own reaction must be idempotent

Chaty should use a custom reaction rail with:

- 6–8 fastest reactions
- full palette access
- recent reactions
- per-chat quick reaction preference
- reduced-motion fallback

## 10. Settings primitives

`GbSectionCard`, `GbSliderTile`, `GbRadioGroup`, `GbColorTile`, `GbCustomSwitch` remain the baseline semantic components.

All setting tiles must expose:

- title
- supporting description when needed
- current state/value
- tap target
- disabled explanation
- persistence result
- optional reset control

## 11. Feedback components

### Toast
Use for fast confirmation or reversible background status. Never for critical errors that require user action.

### Confirm dialog
Use for irreversible or high-risk destructive actions.

### Empty state
Must explain what is absent and give one useful next action when one exists.

### Loading
Prefer skeletons shaped like final content. Avoid spinners for predictable short local operations.

## 12. Component interface pattern

Each visual component implementation should follow this shape conceptually:

```text
SemanticState → ComponentSpec → VariantResolver → VariantRenderer → ThemeTokens → MotionProfile
```

The semantic state must not import the chosen renderer.
