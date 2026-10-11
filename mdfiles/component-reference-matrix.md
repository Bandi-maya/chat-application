# Chaty Component Reference Matrix — Current vs Research vs Proposed

## Purpose

This matrix converts external research into an implementation decision for Chaty. It does **not** copy another product. Each row keeps the current Chaty behavior as the contract, identifies useful reference patterns, then proposes a distinct Chaty skin/interaction family.

## Legend

- **Current** = already present in the supplied Chaty architecture.
- **Reference** = research-only inspiration; no dependency is to be installed.
- **Proposed** = a custom Chaty implementation/variant.
- **Logic lock** = behavior/domain service must remain unchanged by visual variant.

| Component family | Current Chaty contract | Strong reference families | Proposed best-of-all variants | Logic lock |
|---|---|---|---|---|
| Root scaffold | `ChatyScaffold`, safe-area, app background | Flutter adaptive, Apple HIG, Android adaptive layouts | `ChatyScaffoldV2` with constraint-driven composition | Route/state/lifecycle unchanged |
| Navigation | bottom/top/drawer/rail through appearance controller | Telegram 2026 Android navigation; Tailwind app shells; Mantine AppShell | 10 navigation skins + adaptive rail transformation | Destination semantics unchanged |
| Nav item | icon, label, badge, selected state | M3 navigation, iOS tab conventions, 21st/Aceternity motion | shared `NavItemRenderer` with 10 visual skins | `onTap(destination)` unchanged |
| Header | `ChatyAppBar` with leading/action/subtitle | WhatsApp identity header, Telegram compact header, Apple context preservation | 8 headers with density + collapse modes | Back/search/call/profile actions unchanged |
| Avatar | network/cache/fallback/status/shape | social profile cards, Signal/WhatsApp profile treatment | 6 avatar frames + presence states | User identity source unchanged |
| Text field | focus/clear/error/floating label | React Aria, Headless UI, MUI/Ant patterns | Chaty field with state-driven chrome, no default Flutter look | Validation and text state unchanged |
| Button | primary/secondary/tonal/outline/loading/disabled | Apple controls, Chakra, Radix, Magic UI | 8 button treatments + press physics | Command/action callback unchanged |
| Card/surface | 10px default radius, border/elevation | COSS, shadcn, Carbon, Apple grouped surfaces | surface recipes with density/elevation presets | Content tree unchanged |
| Modal | fixed header/body/footer; 10px radius | Radix/Headless/Chakra dialog, Apple sheets | center, sheet, side, full-height, adaptive variants | Result contract unchanged |
| Bottom sheet | grabber + rounded top | Apple sheets, Chakra drawer/sheet patterns | compact/action/media/task variants | Sheet payload/action contract unchanged |
| Action sheet | iOS-like list with cancel | Apple HIG, Headless menu | context-aware action grouping | Action registry unchanged |
| Context menu | long-press anchored frosted surface | Radix/Headless menus, iOS/Telegram | smart anchor, action priority, collision-aware surface | Message action registry unchanged |
| Toast/banner | `ChatyToast` / event overlay | Chakra/Fluent/Carbon notification patterns | status, undo, sync, permission variants | Notification event remains canonical |
| Empty state | icon + title + subtitle | SaaSFrame/NNG minimalist guidance | contextual empty state with next action | No data vs loading vs error remain distinct |
| Search | existing search affordances | Instagram/Telegram/desktop command patterns | inline search, full search, command search | Search query semantics unchanged |
| Conversation row | avatar/title/preview/time/unread | WhatsApp/Signal/Messenger/Telegram | 10 row density variants | Conversation ordering/read state unchanged |
| Swipe actions | existing chat row actions | iOS list swipe conventions | action colors and progressive reveal | Action callbacks unchanged |
| Stories/status strip | existing story carousel | WhatsApp/Instagram/Messenger/Snapchat | 6 story card layouts + progress treatment | 24h/privacy/view lifecycle unchanged |
| Chat composer | attachment/camera/emoji/send/mic | WhatsApp/Telegram/Signal/Instagram/Messenger/Snapchat/Apple | 8 composer skins + context-aware action rail | Draft/send/record state unchanged |
| Reply/edit strip | reply metadata and edit state | Instagram/Apple/Telegram/WhatsApp | compact context bar with preview and dismiss | Reply/edit payload unchanged |
| Attachment tray | Gallery/Camera/Document/Audio/Location/Poll/Contact | WhatsApp/Messenger/Telegram action sheets | grid, camera-first, adaptive rail, radial experimental | Attachment action registry unchanged |
| Voice recording | hold/lock/cancel/duration/waveform | WhatsApp/Telegram/Signal/Apple voice notes | physics-based press + waveform skins | Recording service contract unchanged |
| Message bubble | painter/geometry with text/media/reaction/ticks | WhatsApp/Telegram/Signal/Apple/Messenger | 10 geometry families | `MessageStatus`, message payload unchanged |
| Message metadata | timestamp/tick/edited/pinned/starred | WhatsApp/Telegram/Apple | reserved metadata gutter, state-aware alignment | Metadata model unchanged |
| Delivery ticks | painter + 4 existing styles | WhatsApp/Telegram/Apple custom status marks | 10 tick packs, transition-only animation | Status mapping unchanged |
| Reactions | reaction badges / long press | Telegram animated reactions; Messenger/Google Messages | quick reaction + expandable reaction rail | Reaction idempotency unchanged |
| Link preview | rich message metadata | Apple/Telegram/Instagram-style previews | compact + media-first preview cards | URL extraction/attachment semantics unchanged |
| Media preview | media draft tray | Apple/Telegram/Messenger preview trays | `MediaDraftTrayV2` with gesture states | Upload/compression/send unchanged |
| Full media viewer | pinch/zoom/save | Apple Photos-style context, Telegram media viewer | focused viewer with bounded chrome | Asset URI/permission/save logic unchanged |
| Poll | message type already supported | Apple/Telegram/WhatsApp-like poll patterns | compact poll card + result transition | Poll vote contract unchanged |
| Task card | embedded collaboration | SaaS/enterprise cards + chat thread patterns | task card with priority rail, status timeline | Task workflow state machine unchanged |
| Task modal | create/edit title/description/priority/due/assignee | Ant/Chakra/Apple form composition | adaptive form surface | Task model and validation unchanged |
| Call activity capsule | active call pill/island | Telegram PiP, Messenger call UX, Android foreground surfaces | 5 call capsule skins | Call coordinator/session unchanged |
| PiP | draggable in-app call | Android PiP, floating media controls | bounded draggable island with snap zones | WebRTC session unchanged |
| Camera | capture/flash/front-back/video | Snapchat lenses, modern camera UX | camera-first surface with custom controls | Camera service unchanged |
| Lens picker | carousel + effects | Snapchat/AR effect trays | horizontal lens rail with preview morphs | Effect registry unchanged |
| Color picker | HSV picker | Kibo/Cult/desktop color systems | Chaty spectrum picker with swatches/history | Theme transaction unchanged |
| Theme preset card | theme presets | WhatsApp themes, Telegram themes | visual preview with semantic swatches | Theme config schema unchanged |
| Customization preview | current theme editor/template overrides | 21st component customization + WhatsApp/Telegram theme editors | live, scoped, reversible preview | Preview must never persist without commit |
| Settings row | `SettingsTile`, switch, grouped cards | Apple settings, Signal, Android settings | grouped rows with clear state hierarchy | Setting registry canonical |
| Switch | `GbCustomSwitch` | iOS/Android/custom controls | 6 switch skins | Boolean preference unchanged |
| Slider | `GbSliderTile` | Material/enterprise slider patterns | labeled slider + value preview + min/max explanation | Numeric preference unchanged |
| Radio/select | `GbRadioGroup`, single-choice modal | Radix/React Aria/COSS | compact list, segmented, cards, adaptive picker | Selected value unchanged |
| Notification permission surface | custom onboarding | Apple permission sequencing, Android permission UX | rationale-first custom screen before OS prompt | OS permission remains source of truth |
| Lock UI | app lock/pattern/pin/biometric | platform security UX | Chaty security surface with recovery/timeout clarity | Credential verification unchanged |
| Wallpaper | solid/gradient/pattern/image | WhatsApp/Telegram themes | layered wallpaper engine with contrast guard | Stored wallpaper identity unchanged |
| Motion system | `ChatyMotion`, page transitions, haptics | Magic UI/Aceternity/Motion Primitives + native platform motion | named motion profiles, reduced-motion mode | State transition timing contract remains deterministic |
| Custom SVG icon system | required future direction | platform icon geometry + custom micro-icons | source-owned SVG set with optical sizing | Icon command IDs unchanged |

## Non-duplicate feature synthesis

The research repeatedly surfaces the same underlying capabilities. Chaty should implement each semantic capability **once**, then let skins render it differently.

| Canonical capability | One implementation? | Visual skins?
|---|---|---|
| Navigation destination | Yes | Many |
| Message status | Yes | Many ticks |
| Bubble geometry | Yes renderer contract | Many |
| Composer state | Yes state machine | Many |
| Attachment action | Yes registry | Many trays |
| Modal result | Yes | Many surfaces |
| Reaction add/remove | Yes | Many reaction animations |
| Theme application | Yes transaction | Global/per-chat |
| Settings persistence | Yes registry | Many row styles |
| Presence | Yes service | Several indicator styles |
| Story lifecycle | Yes | Several story cards |
| Call state | Yes | Several presentation skins |

## Component implementation rule

A component must be split into:

1. **Semantic model** — what it represents.
2. **State contract** — idle/focused/loading/error/etc.
3. **Interaction controller** — input and commands.
4. **Layout policy** — responsive composition.
5. **Skin renderer** — visual variant only.
6. **Motion recipe** — transition-only animation.
7. **Accessibility adapter** — labels, actions, focus order.
8. **Telemetry/QA hooks** — optional internal measurement without changing behavior.

Never implement business behavior inside a visual skin.
