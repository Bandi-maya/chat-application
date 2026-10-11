# Chaty Research Index

## 1. Executive synthesis

The current Chaty system is already unusually componentized. The research gap is not a missing widget library; it is a **variant orchestration system** that lets one behavior render through many visual grammars while keeping the same domain events and persistence.

The strongest cross-source patterns are:

- **Composable primitives:** Radix, Base UI, React Aria, Headless UI and similar systems separate interaction state from visual treatment.
- **Registry/source ownership:** 21st.dev demonstrates a copy-owned registry model rather than a single fixed aesthetic.
- **Responsive layout selection:** Flutter and Android guidance favor window-size/adaptive decisions over device-type checks.
- **Chat personalization:** WhatsApp, Telegram, Instagram, Signal, Google Messages and Viber increasingly expose themes, chat-specific styling or expressive interaction controls.
- **Expressive micro-interaction:** Telegram reaction animations, Google Messages reaction effects, and Snapchat lens/chat expression demonstrate that motion can communicate meaning when it remains scoped and fast.
- **Accessibility-first overlays:** WAI-ARIA, Apple HIG and mature headless primitives emphasize managed focus, escape handling, outside-dismiss rules, clear labels, and sufficient targets.
- **Anti-generic design:** 2026 discussions of AI-slop converge on the same failure pattern: generic gradients, interchangeable cards, weak content hierarchy, missing states and unreviewed interaction details.

## 2. Component-library reference matrix

> The matrix focuses on component families relevant to Chaty: navigation, input/composer, overlays, menus, lists, cards, avatars, feedback, animation and theme/control primitives. It is deliberately deduplicated by capability.

| Source | Reference | Strongest useful patterns for Chaty | Evidence |
|---|---|---|---|
| 21st | https://21st.dev/ | Large multi-author registry; AI-ready source ownership; navigation; AI chats; widgets; themes | Directly reviewed |
| React Bits | https://reactbits.dev/ | Animated text, loaders, interaction primitives, visual motion references | Research target |
| Magic UI | https://magicui.design/ | Animated components, effects, floating surfaces, expressive motion | Directly reviewed |
| Aceternity UI | https://ui.aceternity.com/ | Spotlight, cards, carousels, gooey interactions, 3D, text effects | Directly reviewed |
| shadcn/ui | https://ui.shadcn.com/docs/components | Dialog, sheet, drawer, command, input, menu, message, navigation, toast | Directly reviewed |
| COSS UI | https://coss.com/ui | Base UI-driven composable, accessible patterns | Directly reviewed |
| Base UI | https://base-ui.com/ | Unstyled primitives and interaction contracts | Directly reviewed |
| Radix | https://www.radix-ui.com/primitives | Dialog, dropdown, popover, navigation menu, tabs, radio, focus/collision handling | Directly reviewed |
| Kibo UI | https://www.kibo-ui.com/ | Composable functional components, color picker, image zoom, chat/AI primitives | Directly reviewed |
| Cult UI | https://www.cult-ui.com/ | Motion-rich niche components, source-owned React/Tailwind patterns | Directly reviewed |
| Skiper UI | https://skiper-ui.com/ | Uncommon UI, motion and composition references | Directly reviewed |
| SmoothUI | https://smoothui.dev/ | Anti-generic guidance plus animated component patterns | Directly reviewed |
| Motion Primitives | https://motion-primitives.com/ | Focused reusable motion primitives | Research target |
| Origin UI / COSS | https://coss.com/ui | Clean enterprise controls, form states, layout primitives | Directly reviewed |
| Chakra UI | https://chakra-ui.com/docs/components/concepts/overview | Dialog, drawer, popover, menu, action bar, field/control patterns | Directly reviewed |
| React Aria | https://react-aria.adobe.com/ | Accessible state and interaction contracts | Directly reviewed |
| Headless UI | https://headlessui.com/react | Dialog, menu, popover, tabs, forms, state attributes | Directly reviewed |
| Tailwind Plus | https://tailwindcss.com/plus/ui-blocks/application-ui | Application shells, lists, forms, navbars, command palettes, overlays | Directly reviewed |
| Park UI | https://park-ui.com/ | Accessible floating overlays, recipes, responsive controls | Directly reviewed |
| Mantine | https://mantine.dev/ | App shell, modal, menu, nav, notifications, input | Directly reviewed |
| Tremor | https://www.tremor.so/ | Dense information, states, dashboard-style primitives | Directly reviewed |
| ThreeUI | https://threeui.com/browse | Visual interaction, motion, immersive effects and 3D references | Directly reviewed |
| Ant Design | https://ant.design/components/ | Modal, dropdown, menu, tabs, message, notification, forms, lists | Research target |
| Material UI | https://mui.com/material-ui/ | Navigation, dialogs, sheets/drawers, fields, snackbars, lists | Research target |
| Uiverse | https://uiverse.io/ | Micro elements, inputs, toggles, tooltip treatments, motion ideas | Directly reviewed |
| HyperUI | https://www.hyperui.dev/ | Application lists, dropdowns, buttons, inputs, responsive forms | Directly reviewed |
| Float UI | https://floatui.com/ | Responsive composition and reusable page controls | Directly reviewed |
| Preline UI | https://preline.co/ | Large collection of pragmatic controls and responsive patterns | Directly reviewed |
| Flowbite | https://flowbite.com/ | Tailwind control patterns, dialogs, menus, nav, forms | Reference |
| DaisyUI | https://daisyui.com/ | Tokenized theme primitives and control states | Directly reviewed |
| HeroUI | https://heroui.com/ | Modern component surfaces, focus states and responsive compositions | Directly reviewed |
| PrimeReact | https://primereact.dev/ | Enterprise control breadth, data-dense overlays | Directly reviewed |
| Blueprint JS | https://blueprintjs.com/ | Desktop-grade menus, dialogs, popovers, controls | Directly reviewed |
| React Bootstrap | https://react-bootstrap.netlify.app/ | Familiar modal/form/list patterns | Directly reviewed |
| Evergreen | https://evergreen.segment.com/ | Enterprise controls, panes, tables, menus | Directly reviewed |
| Grommet | https://v2.grommet.io/ | Responsive accessibility, navigation and layout | Directly reviewed |
| Semantic UI React | https://react.semantic-ui.com/ | Familiar semantic control and list patterns | Directly reviewed |
| Fluent UI | https://fluent2.microsoft.design/ | Cross-platform enterprise interaction patterns | URL reviewed; page structure may vary |
| Carbon | https://www.carbondesignsystem.com/ | Enterprise accessibility, menus, notifications and data display | Directly reviewed |
| Adobe Spectrum | https://spectrum.adobe.com/ | Consistent interaction semantics and accessible controls | Directly reviewed |
| Geist UI | https://geist-ui.dev/ | Minimal visual language reference | Archived source |
| Kokonut UI | https://kokonutui.com/ | Visual details and motion accents | Directly reviewed |
| ReUI | https://reui.io/ | Shadcn-compatible composed UI patterns | Directly reviewed |
| Tailark | https://tailark.com/ | Application sections and responsive layouts | Directly reviewed |
| shadcnblocks | https://www.shadcnblocks.com/ | Composition references and page-level systems | Directly reviewed |
| Ruixen UI | https://21st.dev/ | Components indexed through 21st registry; rich controls | 21st registry |
| Efferd | https://21st.dev/ | Registry examples for animated components | 21st registry |
| UI Triple D | https://21st.dev/ | Interaction/effect references | 21st registry |
| UI Layouts | https://21st.dev/ | Layout composition references | 21st registry |
| Jolly UI | https://21st.dev/ | Accessible/control composition references | 21st registry |
| uimix | https://21st.dev/ | Mixed UI treatments | 21st registry |
| ShadcnSpace | https://21st.dev/ | Shadcn-adjacent patterns | 21st registry |
| shadway | https://21st.dev/ | Component treatment references | 21st registry |
| REAVIZ | https://21st.dev/ | Data/interaction references | 21st registry |
| reuno-ui | https://21st.dev/ | Component treatments | 21st registry |
| Systaliko UI | https://21st.dev/ | Motion/control references | 21st registry |
| lndev-ui | https://21st.dev/ | Animated UI references | 21st registry |
| Fancy Components | https://21st.dev/ | Expressive controls | 21st registry |
| Cnippet | https://21st.dev/ | Source-owned component ideas | 21st registry |
| PrebuiltUI | https://21st.dev/ | Composable UI patterns | 21st registry |
| Circle | https://21st.dev/ | Surface and interaction patterns | 21st registry |
| Spell | https://21st.dev/ | Visual/motion effects | 21st registry |
| Layouts | https://21st.dev/ | Page and layout patterns | 21st registry |
| Interfaces DS | https://21st.dev/ | Design-system patterns | 21st registry |

## 3. Design-inspiration reference matrix

| Source | Reference | Best use for Chaty |
|---|---|---|
| Mobbin | https://mobbin.com/ | Real shipped app screens, UI elements, flows and animations |
| Refero | https://refero.design/ | Product flows and UI pattern comparisons |
| SaaSFrame | https://www.saasframe.io/ | Search, tables, comments/threads, file upload, empty states |
| Lapa Ninja | https://www.lapa.ninja/ | Full-page composition, typography, responsive sections |
| Land-book | https://land-book.com/ | Curated responsive layout and style references |
| Godly | https://godly.design/ | High-quality visual and interaction inspiration |
| Awwwards | https://www.awwwards.com/ | Motion, craft, art direction |
| Dribbble | https://dribbble.com/ | Visual exploration and interaction studies |
| Behance | https://www.behance.net/ | Detailed case studies and visual systems |
| Page Flows | https://pageflows.com/ | Flow sequences and interaction patterns |
| Screenlane | https://screenlane.com/ | Mobile UI screens and patterns |
| UI Garage | https://uigarage.net/ | Component and mobile layout references |
| Collect UI | https://collectui.com/ | Daily UI micro-patterns |
| One Page Love | https://onepagelove.com/ | Composition, typography and landing craft |
| Muzli | https://muz.li/ | Design trend discovery |
| Growth.Design | https://growth.design/ | UX psychology and behavioral design |
| Really Good UX | https://reallygoodux.io/ | Interaction examples and critiques |
| Pttrns | https://pttrns.com/ | Mobile UI patterns |
| UIverse | https://uiverse.io/ | Small interaction primitives |
| Curated | https://www.curated.design/ | Contemporary visual references |
| Recent.design | https://www.recent.design/ | Recent product design references |
| Figma Community | https://www.figma.com/community | Design-system/community kit references |
| Kombai | https://www.kombai.com/ | Generated/deconstructed design examples; use as comparative study |
| Designsp | https://designsp.co/ | Product UI inspiration |
| UI Jar | https://uijar.com/ | Interface patterns |
| Land-book Collections | https://land-book.com/ | Style-based collections |
| Godly Sites | https://godly.design/sites/ | Full-page visual references |
| Lapa Inspiration | https://blog.lapa.ninja/ | Design trends and case study cues |

## 4. Social / messaging product evidence

| Product | Reference | Useful interaction evidence |
|---|---|---|
| WhatsApp | https://faq.whatsapp.com/ and https://blog.whatsapp.com/ | Chat themes; per-chat/global theme application; disappearing messages; familiar composer/message actions |
| Telegram | https://telegram.org/blog | Android bottom navigation redesign; custom themes; animated reactions; stories; rich messaging controls |
| Instagram DMs | https://about.fb.com/news/2024/03/instagram-dm-updates/ | Long-press edit, pinned chats, read-receipt control, sticker/GIF/media replies, chat themes |
| Facebook Messenger | https://play.google.com/store/apps/details?id=com.facebook.orca | Themes, animated stickers/GIFs/reactions, stories, notes, calling and expressive chat |
| Signal | https://support.signal.org/hc/en-us/sections/360001602792-Signal-Messenger-Features | Edit/reply/schedule, chat folders, pinned, polls, stories, app lock, theme/wallpaper, call settings |
| Google Messages | https://support.google.com/messages/answer/15685397 | Custom bubbles, typing, reactions, expressive reaction effects, E2EE and profile controls |
| Snapchat | https://www.snapchat.com/topic/chat-features-explained | Typing indicators, saved-chat notifications, animated Bitmoji, chat filters/lenses, expressive camera tools |
| Viber | https://www.viber.com/en/features/ | Voice/video messages, calls, communities, stickers/GIFs, edit/delete, disappearing messages; chat themes in 2026 update |
| LINE | https://www.linecorp.com/pr/news/2026/0330 | 2026 chat page redesign: friend tab, all-albums shortcut, plus menu for common actions |
| Apple Messages | https://support.apple.com/en-in/104982 | Replies, mentions, edit/delete, scheduled send, polls, text effects, audio, Check In, collaborative features |
| Discord | https://support.discord.com/ | Threads, reactions, community/channel structures, voice/video, Stage channels |
| WeChat | https://www.wechat.com/ | Research target for high-density messaging and social-integrated surfaces; direct feature verification pending in this crawl |

## 5. UX and accessibility evidence

| Source | Reference | Key rule family |
|---|---|---|
| Nielsen Norman Group | https://www.nngroup.com/articles/ten-usability-heuristics/ | 10 usability heuristics |
| Laws of UX | https://lawsofux.com/ | Fitts, Hick, Goal Gradient, Aesthetic-Usability, Choice Overload, Chunking, Doherty and more |
| W3C ARIA APG | https://www.w3.org/WAI/ARIA/apg/ | Dialog, menu, combobox, tabs, focus and semantic interaction patterns |
| W3C WCAG 2.2 | https://www.w3.org/WAI/WCAG22/ | Target size, contrast, focus appearance, keyboard/accessibility |
| Apple HIG | https://developer.apple.com/design/human-interface-guidelines/ | Platform-native interaction expectations, controls, sheets, alerts, menus, accessibility |
| Android Developers | https://developer.android.com/design | Adaptive, responsive, system bars and platform UI |
| Android 17 | https://developer.android.com/about/versions/17/summary | Current Android 17 behavior changes and system UI considerations |
| Flutter docs | https://docs.flutter.dev/ui/adaptive-responsive | Window-size based responsive/adaptive layout |
| Flutter performance | https://docs.flutter.dev/perf/best-practices | Rebuild, animation and layer-cost guidance |

## 6. Deduped feature inventory

The following capabilities appeared repeatedly enough to be treated as reusable Chaty primitives rather than one-off screens.

### Navigation

- Bottom navigation with label/icon states
- Floating/elevated navigation dock
- Compact icon-only dock
- Rail navigation for larger widths
- Drawer navigation
- Top tab navigation
- Contextual secondary navigation
- Search-first navigation
- Active indicator animation
- Long-label fallback / tooltip
- Badge/counter state
- Reordering/custom destination visibility

### Header

- Leading back control
- Identity cluster (avatar + title + status)
- Action group
- Search affordance
- Collapse-on-scroll title
- Subtitle/status row
- Overflow menu
- Selection-mode header
- Call actions
- Presence/typing status

### Chat input / composer

- Text field with dynamic height
- Attachment affordance
- Camera shortcut
- Emoji/sticker affordance
- Send affordance replacing mic
- Hold-to-record
- Lock-to-record
- Cancel gesture
- Recording waveform and duration
- Reply/edit context strip
- Draft persistence
- Mention/autocomplete
- URL/file previews
- Scheduled-send state
- View-once / disappearing mode state
- Send-success animation

### Message bubble

- Incoming/outgoing geometry
- Tail or no-tail
- Timestamp
- Delivery/read state
- Reply quote
- Reactions
- Edited state
- Star/pin indicators
- Media previews
- Voice waveforms
- Task cards
- Polls
- Link previews
- Multi-select state
- Failed/retry state
- Selection highlight

### Overlays

- Center dialog
- Alert dialog
- Bottom sheet
- Full-height sheet
- Side sheet on wide screens
- Anchored popover
- Context menu
- Action sheet
- Command palette
- Toast/banner
- Floating activity capsule
- PiP island

### Expression/media

- Emoji picker
- Animated emoji
- Reactions
- Sticker picker
- GIF picker
- Attachment tray
- Camera/lens carousel
- Media editor
- Full-screen media viewer
- Gallery preview strip

### Lists

- Dense list
- Spacious list
- Sectioned list
- Pinned section
- Unread filter
- Swipe actions
- Quick actions
- Empty/loading/error states
- Search/filter state
- Multi-select state

### Settings/customization

- Group cards
- Switches
- Sliders
- Radio groups
- Color picker
- Theme presets
- Variant picker with live preview
- Reset/undo/apply
- Dirty-state indicator
- Scoped settings

## 7. Most important research conclusions

1. Separate **behavior** from **skin**. This is the single most important architectural conclusion.
2. Build every visual variant from the same semantic state model; never fork business logic.
3. Give customization a clear scope and precedence model: system → global app → screen → conversation → transient session override.
4. Use window constraints rather than device names to decide mobile/tablet/desktop layout.
5. Use custom motion sparingly and purposefully; animation should communicate state or preserve continuity.
6. Treat focus, target size, contrast, empty/loading/error states, and reduced-motion behavior as first-class acceptance criteria.
7. Preserve Chaty's existing 10px modal radius as the compatibility default; variant skins may offer controlled alternatives only where the user explicitly chooses them.
8. Use social products as behavior references, not as pixel-perfect visual targets.

## 7. Additional reference targets requested by the brief

These are included as explicit research targets so future agents do not silently omit them. Where the current crawl did not directly verify the site structure, the row is marked `Research target` rather than represented as established evidence.

| Source | Reference | Intended research |
|---|---|---|
| OriginKit | https://origin-kit.com/ | Component/composition reference; verify current URL/catalog before use |
| Scrolltide | https://scrolltide.com/ | Scroll interaction/motion reference; verify availability before use |
| Animista | https://animista.net/ | CSS motion/timing reference |
| Motion | https://motion.dev/ | Motion principles and interaction patterns |
| Origin UI | https://originui.com/ | Form/control composition reference |
| Aceternity Blocks | https://ui.aceternity.com/ | Page/composite interaction references |
| Magic UI Templates | https://magicui.design/ | Composition and motion references |
| Radix Themes | https://www.radix-ui.com/themes | Semantic component composition reference |
| Park UI | https://park-ui.com/ | Ark/recipe-style accessible controls |
| React Spectrum | https://react-spectrum.adobe.com/ | Accessible behavior reference |
| Spectrum | https://spectrum.adobe.com/ | Design-system semantics and states |
| MUI Joy | https://mui.com/joy-ui/ | Alternative expressive component language |
| MUI X | https://mui.com/x/ | Dense data interactions where Chaty tasks need them |
| Carbon Charts | https://charts.carbondesignsystem.com/ | Data display reference for task analytics |
| Blueprint | https://blueprintjs.com/ | Desktop popover/menu density |
| Grommet | https://v2.grommet.io/ | Responsive/accessibility reference |
| Evergreen | https://evergreen.segment.com/ | Pane/control composition |
| Semantic UI | https://react.semantic-ui.com/ | Familiar semantic component vocabulary |
| PrimeReact | https://primereact.org/ | Control breadth/reference only |
| React Bootstrap | https://react-bootstrap.netlify.app/ | Conventional component fallback reference |
| Flowbite | https://flowbite.com/ | Tailwind interaction reference |
| DaisyUI | https://daisyui.com/ | Theme token reference |
| HeroUI | https://www.heroui.com/ | Modern control treatment reference |
| Kibo | https://www.kibo-ui.com/ | Functional custom primitives |
| Kokonut | https://kokonutui.com/ | Motion details |
| ReUI | https://reui.io/ | Composed UI patterns |
| Uiverse | https://uiverse.io/ | Micro-interaction idea pool |
| HyperUI | https://www.hyperui.dev/ | Practical responsive component layouts |
| Preline | https://preline.co/ | Broad control/state catalog |
| Float UI | https://floatui.com/ | Responsive composition |
| shadcnblocks | https://www.shadcnblocks.com/ | Page-level composition |
| ThreeUI | https://threeui.com/three-js | 3D / immersive interaction references |

## 8. Research limits and non-claims

- A web crawl is not equivalent to exhaustive inspection of every page/component in every listed product. The matrix intentionally records verified patterns and clearly labels research targets where direct verification was not completed.
- Proprietary implementations (for example, GBWhatsApp internals or Snapchat's private AR stack) cannot be treated as source code. The brief uses observed user-facing behavior as inspiration and reimplements semantics independently.
- “Custom SVG only” is a Chaty implementation rule; external icon libraries remain research references and are not dependencies.
- The final implementation must re-check time-sensitive product behavior before shipping because messaging apps and operating-system UI evolve.
