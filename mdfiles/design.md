# Chaty Visual Design Specification

## 1. Design character

Chaty should feel like a real product with an authored visual grammar, not a gallery of disconnected skins. The system therefore uses **one behavioral language + many visual dialects**.

The product can support expressive, minimal, cyber, editorial, dark/AMOLED and soft themes, but every theme must preserve hierarchy, legibility and touchability.

## 2. Baseline visual constraints

- Keep current 10px modal radius as the compatibility default.
- Preserve existing semantic color derivation and accessibility logic.
- Use the current spacing/radius/type token hierarchy instead of screen-local magic numbers.
- No component may invent its own shadow language.
- No random gradient is allowed merely to make a component look premium.

## 3. Token layers

```text
Primitive tokens
  color.blue.500
  gray.100
  radius.10
  space.4
  duration.120

Semantic tokens
  surface.base
  surface.elevated
  text.primary
  text.secondary
  accent.primary
  state.success
  state.warning
  state.error
  message.outgoing
  message.incoming

Component tokens
  composer.background
  composer.radius
  bubble.outgoing
  bubble.incoming
  tick.read
  navigation.activeIndicator
  modal.surface
  modal.scrim

Scope tokens
  global
  screen
  conversation
```

## 4. Typography

Typography is content hierarchy, not decoration.

Rules:

- Prefer a small, intentional type scale.
- Avoid using multiple expressive typefaces in one theme.
- Preserve platform legibility at large text settings.
- Never shrink body text just to preserve a pretty fixed-height component.
- Numeric/status metadata should remain readable at 200% text scaling where feasible.

## 5. Color extraction method

For any visual reference image:

1. crop out logos/brand marks where they are not part of the intended visual grammar
2. sample dominant surface, background, accent and text regions
3. group near-identical colors
4. normalize to a small palette
5. derive semantic roles
6. run contrast checks
7. test light/dark/large-text states
8. manually tune by eye so the result is not a literal screenshot palette copy

Suggested palette roles:

`background / surface / surfaceVariant / textPrimary / textSecondary / accent / accentOn / success / warning / error / divider / messageIncoming / messageOutgoing / overlayScrim`

### Reference examples

WhatsApp's current chat themes demonstrate preset themes plus mix-and-match bubble/background colors and per-chat versus global theme application. Telegram demonstrates chat-specific themes and highly customized visual surfaces. Viber added user-created chat themes in its 2026 feature update. Use these as **behavioral models**, not direct palettes.

## 6. Surface language

Use three depth levels:

- base canvas
- content surface
- transient/elevated surface

Depth should come from contrast, spacing and shape first; blur/shadow second.

## 7. Responsive composition

### Compact

- bottom navigation
- single-column chat
- short header action set
- composer optimized for thumb reach
- sheets over centered dialogs for larger forms

### Medium

- larger message max width
- optional two-pane chat/contact layouts
- side-mounted contextual controls where useful

### Expanded

- navigation rail or side pane where selected style supports it
- conversation column with readable max width
- secondary info/task/media pane
- dialog/sheet width limited to a comfortable reading measure

Never merely scale up a compact UI.

## 8. Chat list design

Information order:

1. identity
2. unread/priority signal
3. last meaningful message preview
4. time
5. secondary metadata

Pinned conversations should be visually distinct without looking like a separate product.

Unread states should use both shape/weight and color so they remain visible in grayscale/high contrast.

## 9. Header design

The identity block is the anchor. Avoid putting more than two high-priority actions in the compact trailing group; secondary actions belong in overflow.

For scrolling lists, title collapse must preserve navigation continuity and avoid sudden vertical jumps.

## 10. Composer design

The composer is the most frequently touched component in the app and therefore deserves the strongest Fitts/Hick optimization.

Primary action order by state:

`type → send`
`empty → record`
`reply → send/cancel reply`
`media draft → edit/send`
`recording → stop/cancel`

Do not force the user to search for send after typing.

## 11. Bubble design

The bubble must read as content first. Shape is a secondary identity signal.

- Keep line length comfortable.
- Avoid large internal padding on long-form text.
- Keep metadata visually subordinate.
- Do not let reactions cover text or timestamps.
- Preserve alignment under text scaling.

## 12. Motion language

Every motion has one reason:

- **enter:** establish location/context
- **exit:** explain where it went
- **state change:** confirm success/progress
- **gesture response:** show direct manipulation
- **continuity:** maintain spatial relationship between views

### Motion profiles

`Subtle`
- 80–180ms micro transitions
- minimal scale
- no decorative looping effects

`Expressive`
- 120–260ms
- spring-like selection movement
- reaction particles or glyph morphing where semantic

`Reduced`
- cross-fade/instant state changes where possible
- preserve essential feedback without decorative movement

## 13. Anti-generic / anti-AI-slop rules

Do not default to:

- purple-to-blue gradients
- glassmorphism on every surface
- six identical cards in a grid
- identical 16px/20px/24px spacing everywhere
- one generic font everywhere without typographic reasoning
- neon glow on unrelated controls
- pills for every button
- icons used as decoration rather than communication
- bounce animation on every click
- generic placeholder copy

Instead:

- establish a visual point of view per theme
- vary density intentionally
- use asymmetric composition where it improves hierarchy
- use whitespace to group meaning
- reserve blur for surfaces that genuinely benefit from depth
- use motion as feedback, not spectacle
- use content-driven layouts rather than fixed template blocks
- test the result with real message lengths, not idealized short copy

## 14. Iconography

All production icons are custom SVG/painter assets.

Requirements:

- 1 consistent optical stroke family per theme
- visual weight must match adjacent text
- state variants are redrawn, not recolored blindly
- touch target is larger than visible glyph
- icon-only controls need semantic labels
- no proprietary social-media marks inside Chaty UI

## 15. Component-specific design rules

### Bottom navigation

Active state should be visually obvious without animation dependency. Label can collapse on compact widths only if discoverability remains intact.

### Header

Use title + identity before decorative treatment. Overflow must be anchored to the header action.

### Composer

The keyboard and composer form one spatial system. Insets, typing state, recording state and media tray must move together.

### Modal

The modal body scrolls independently from the fixed header/footer contract already present in Chaty. Avoid giant titles. Destructive actions must not be visually easier to hit than safe cancel actions.

### Toast

Low interruption. Avoid covering the composer or critical call controls.

### Bottom sheet

Use a grabber only when the surface supports drag interaction. Do not imply drag if it is not draggable.

### Context menu

Anchor to source content. Never jump to a distant location on compact screens unless collision handling requires it.

## 16. Accessibility baseline

- minimum 24x24 CSS px target for applicable pointer controls as a WCAG AA baseline; use larger targets for key mobile interactions
- iOS visible/control sizing should follow platform guidance; Apple's current HIG describes 44x44 pt default control size and a 28x28 pt minimum for iOS/iPadOS
- visible focus on keyboard-capable surfaces
- labels/descriptions on semantic controls
- contrast checked for text and essential indicators
- reduced motion supported
- large text tested
- state never conveyed by color alone
