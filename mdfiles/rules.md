# Chaty UI/UX Rules

## 1. Hard rules

1. Behavior is immutable unless the user explicitly requests a functional change.
2. Styling is replaceable through a registry.
3. Every component owns all of its states before implementation begins.
4. Every interactive surface must have keyboard, touch and semantic behavior appropriate to the platform.
5. Use window size for adaptive composition; never assume phone/tablet/desktop from hardware alone.
6. No icon-only button without an accessible label.
7. No destructive primary action gets stronger visual prominence than the safe exit/cancel action by accident.
8. No overlay may trap the user without an escape/back path unless the task is an intentionally blocking security operation.
9. Never put important controls underneath system gesture insets.
10. Every visual effect has a documented performance cost.

## 2. Nielsen heuristics applied to Chaty

| Heuristic | Chaty application |
|---|---|
| Visibility of system status | queued/sent/delivered/read/failed states; upload progress; call connection state; saving indicator in customization |
| Match with real world | familiar composer, swipe, long-press, navigation and task metaphors |
| User control/freedom | undo, cancel, clear draft carefully, dismiss overlays, revert customization |
| Consistency/standards | same message actions, same overlay dismissal, same button state logic |
| Error prevention | validate permissions, confirm irreversible actions, preserve drafts |
| Recognition over recall | visible selected variant, contextual actions, previews |
| Flexibility/efficiency | gestures, quick reactions, shortcuts, per-chat customization |
| Aesthetic/minimalist | remove decorative UI that competes with message content |
| Error recovery | clear errors + retry action + preserved local state |
| Help/documentation | contextual descriptions in advanced customization rather than long tutorials |

## 3. Laws of UX / behavioral principles

The implementation should consider, when relevant:

- Fitts's Law
- Hick's Law / Choice Overload
- Jakob's Law
- Miller's Law / Chunking
- Von Restorff Effect
- Zeigarnik Effect
- Goal-Gradient Effect
- Doherty Threshold
- Peak-End Rule
- Serial Position Effect
- Aesthetic-Usability Effect
- Tesler's Law
- Pareto Principle
- Postel's Law
- Prägnanz
- Proximity
- Similarity
- Common Region
- Figure-Ground
- Continuity
- Common Fate
- Uniform Connectedness
- Cognitive Load
- Flow

### Application examples

**Fitts:** keep send/record near the thumb and stable in position across composer states.

**Hick:** do not show every attachment action as equal. Put the most frequent actions first; put the rest in a secondary surface.

**Jakob:** retain familiar chat interaction patterns even when the visual skin changes.

**Miller/chunking:** group settings into meaningful cards and avoid giant flat option lists.

**Von Restorff:** use a unique treatment only for the one thing that truly needs attention.

**Zeigarnik:** preserve visible progress in multi-step customization and show what remains to be configured.

**Goal gradient:** live preview and completion indicators can encourage finishing setup.

**Doherty:** local preview updates should feel immediate; do not wait on network operations for a visual selection.

**Peak-end:** customization application should end with a satisfying but short confirmation, not a long animation.

**Tesler:** advanced complexity belongs behind advanced controls; do not push every option onto the first screen.

## 4. Accessibility rules

### Touch targets

WCAG 2.2 SC 2.5.8 requires pointer targets to be at least 24×24 CSS px or sufficiently spaced, with exceptions. Chaty should exceed the minimum for high-frequency mobile actions where practical.

### Apple

Apple's HIG currently recommends 44×44 pt default control size on iOS/iPadOS and a 28×28 pt minimum, plus spacing between controls. Preserve platform expectations even when rendering custom components.

### Focus

Dialog focus must move into the dialog and return to the triggering control when the dialog closes where appropriate.

### Color

Do not encode message state, unread status or selection only by color.

### Motion

Respect reduced-motion preferences. Essential state feedback remains available without decorative animation.

## 5. Overlay laws

### Dialog

- use for scoped decisions or focused information
- outside content becomes inert when modal
- Escape/back dismisses unless intentionally blocked
- focus moves into dialog
- focus returns to origin
- fixed header/footer when content is long

### Alert dialog

Use for important or irreversible consequences. Do not use for routine information.

### Bottom sheet

Use for short scoped tasks closely related to the current context. On complex long tasks, use a full screen or dedicated route.

### Menu

- order items by frequency/importance
- group related commands
- allow keyboard arrow navigation on desktop/web-capable surfaces
- preserve visible selected state when applicable
- handle collisions with window edges

### Popover

- anchor to trigger/content
- close on outside interaction where appropriate
- restore focus
- support collision-aware placement

## 6. Gesture rules

### Tap

Use for primary action.

### Long press

Use for contextual actions such as message selection.

### Swipe

Use only when the hidden action is discoverable and safe. Do not require swipe for a primary action.

### Drag

Provide a visible affordance only when the object is actually draggable.

## 7. Content rules

- Use real labels, not placeholder gibberish.
- Keep error text specific and actionable.
- Avoid technical codes in user-facing errors.
- Keep system status concise.
- Preserve user-authored message text exactly.

## 8. Responsive rules

- compact, medium and expanded layouts must be consciously designed
- never rely on a fixed width that can clip under large text
- dialog width should be limited for readability
- list rows adapt their metadata instead of merely shrinking text
- composer and keyboard are one layout unit
- system bar/gesture insets are respected
- foldable/multi-window cases are treated as app-window problems, not phone/tablet labels

## 9. Performance rules

### 144Hz target

A 144Hz display has a theoretical frame budget of about 6.94ms. Treat this as an optimization target, not a guarantee on all devices.

### Avoid

- large `ImageFilter` blur surfaces on scrolling content
- unnecessary `saveLayer`/opacity/clipping during animation
- rebuilding the entire chat list because one message state changed
- animated `Column` stacks of long lists
- repeated heavy image decoding in scrolling cells
- continuous decorative animations when idle

### Prefer

- `ListView.builder`/lazy structures
- narrow rebuild scopes
- `AnimatedBuilder` with stable child subtrees
- `RepaintBoundary` around independently expensive regions
- pre-sized image thumbnails
- cached parsed message metadata
- custom painters for small repeated vectors
- profile-mode performance testing

## 10. Anti-AI-slop quality gate

Reject a variant when at least two of the following are true without product justification:

- generic purple/indigo gradient
- excessive glass/blur
- identical card grid repeated across unrelated surfaces
- pills used for every action
- no clear primary action
- no meaningful empty/loading/error states
- arbitrary shadows
- generic icons
- decorative animation on every interaction
- content density that feels templated rather than task-driven

## 11. Theme safety

A user-selected theme may alter:

- colors
- surfaces
- typography scale/family where supported
- radius within safe bounds
- wallpaper
- bubble family
- tick family
- navigation family
- header family
- composer family
- motion profile

A theme may not alter:

- encryption semantics
- message state semantics
- authorization
- data ownership
- security gates
- privacy visibility rules
- call lifecycle semantics
- persistence identifiers

## 12. Icon rules

Custom SVG only.

Each icon needs:

- default
- active
- disabled
- pressed/focused
- accessibility label
- optical-size check at 16/20/24/32 px equivalent sizes

## 13. Test rules

No component is complete until all states and at least three widths have been visually checked.
