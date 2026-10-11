# Chaty Component Behavior Matrix — Interaction-Level Contract

This file is the agent-facing behavior specification for the component layer. Visual variants may change presentation, but not the semantic command emitted by a component.

## 1. Core components

| Component | Primary states | Required inputs | Required outputs | Failure behavior | Accessibility behavior |
|---|---|---|---|---|---|
| `ChatyScaffold` | normal/loading/locked/offline | content, safe-area policy, background | layout frame | safe fallback | preserves reading order |
| `ChatyAppBar` | normal/search/selection/call | title, subtitle, actions | action callback | action disabled/hidden by capability | semantic labels on all actions |
| `ChatyBackButton` | idle/pressed/disabled | route context | back command | no-op at root or delegates to route policy | descriptive label |
| `ChatyCard` | normal/pressed/selected/disabled | child, surface config | optional tap/selection | visual fallback | role communicated when interactive |
| `ChatyButton` | idle/pressed/loading/disabled/success/error | label/icon, command | command/result | loading prevents duplicate tap | state announcement |
| `ChatyTextField` | idle/focused/filled/error/disabled | controller, validation policy | text/focus/submit | retains input; shows error | label/hint/error semantics |
| `ChatyAvatar` | loading/loaded/fallback/status | identity/image/status/shape | tap context | fallback initials/asset | alt/identity label |
| `GbCustomSwitch` | off/on/disabled/pressed | bool + command | bool change | revert if persistence fails | value + label |
| `GbSliderTile` | idle/dragging/disabled | min/max/value/divisions | value change | clamp to range | value readable and adjustable |
| `GbRadioGroup` | none/selected/disabled | options/value | selection | no invalid commit | selected state announced |
| `GbColorTile` | current/hover/focused | semantic color | picker/open/change | revert previous valid color | announces color/value |

## 2. Overlay components

| Component | Open trigger | Dismiss rules | Confirm rules | Focus rules |
|---|---|---|---|---|
| `ChatyModal` | explicit command | close, scrim where allowed, Escape/back | configurable | first actionable target; restore opener |
| `ChatyConfirmDialog` | destructive/risky action | cancel or explicit choice | required for destructive mutations | destructive action is not accidental default |
| `ChatyBottomSheet` | scoped action | drag/down/back/close policy | only if mutation is consequential | trap while modal |
| `ChatyActionSheet` | contextual action | selection/cancel/back | only when needed | primary choices first |
| `adaptive_selection_panel` | select/filter command | selection or close | immediate for reversible choice | list focus |
| `SingleChoiceModal` | setting/picker | select/back | selection may commit immediately when reversible | selected option visible |
| `LockCredentialSetupModal` | security setup | cancel only if setup can safely abort | explicit completion | keyboard/secure input focus |
| `TaskCreateEditModal` | task create/edit | save/cancel/back policy | validate before commit | first invalid field receives focus |
| context menu | long press / secondary click | outside/Escape/back | destructive items open confirm | focused item highlight |
| `ChatyToast` | event | auto-dismiss or action | never blocks unless converted to dialog | live-region semantics where supported |

## 3. Messaging components

### Composer

| State | Visible primary command | Secondary controls | Allowed transition |
|---|---|---|---|
| Empty | Mic | emoji, camera, attachment, more | focus / record / attach |
| Typing | Send | emoji, camera, attachment, more | send / edit / reply / attach |
| Reply | Send | dismiss reply, media controls | send / dismiss |
| Edit | Save | dismiss edit | save / dismiss |
| Recording | Stop | cancel, lock, timer/waveform | stop / cancel / lock |
| Media draft | Send | edit, remove, add-more | send / edit / remove |
| Offline | Queue/send action reflects offline policy | attach remains available if local-only | queue / draft |
| Failed | Retry | edit/delete if allowed | retry / recover |

### Composer interaction rules

1. Send never duplicates a message when tapped repeatedly; UI locks against concurrent send and relies on idempotent message identity.
2. Holding the mic enters recording only after the configured press threshold; a cancelled gesture produces no message.
3. Locking recording changes the control mode from hold-to-record to explicit stop/cancel.
4. Draft content persists according to existing preference/draft storage rules.
5. The composer must not expand indefinitely; long text scrolls internally after a configured maximum.
6. Keyboard resize must not obscure the send control or reply strip.

### Message bubble

| State | Rendering requirement | Interaction |
|---|---|---|
| incoming | left-side semantics + metadata | tap/long press |
| outgoing | right-side semantics + status | tap/long press |
| selected | selection overlay without changing text geometry | multi-select |
| reaction | reaction chips anchored to message | tap reaction; open reaction panel |
| reply | reply quote with stable height budget | open source message |
| edited | edited marker in metadata region | long press actions |
| failed | failure affordance visible | retry/details |
| media | preview + loading/progress state | tap viewer, long press |
| audio | waveform + duration + play state | play/pause, speed |
| task | status/priority + concise content | open task |
| poll | options + vote/result state | vote, view results |

### Long press message flow

```text
press starts
  -> hold threshold
  -> haptic
  -> select message
  -> reveal compact action surface
  -> actions ordered by context:
       reply > react > copy > forward > star > pin > edit > delete > info
  -> destructive commands require confirmation
  -> dismiss restores message exactly
```

### Double tap reaction flow

```text
double tap within gesture window
  -> resolve quick reaction
  -> optimistic local reaction state
  -> persist/send idempotent reaction command
  -> animate only the changed reaction region
  -> reconcile server response
  -> rollback optimistic state only on authoritative failure
```

## 4. Delivery ticks

| Message semantic state | Canonical status | Renderer input | Allowed transition |
|---|---|---|---|
| queued | `queued` | queued | sent/failed |
| sent | `sent` | sent | delivered/failed |
| delivered | `delivered` | delivered | read |
| read | `read` | read | terminal for delivery state |
| failed | `failed` | failed | retry -> queued/sent |

A tick skin can alter geometry, stroke, fill, animation and size within accessibility bounds. It cannot rename or collapse states.

## 5. Lists and rows

### Conversation row

```text
render row
  -> avatar
  -> identity
  -> preview
  -> timestamp
  -> unread/pinned/mute indicators
  -> presence/typing substitution if active
  -> optional swipe action layer
```

Rules:
- Preview must remain readable and not compete with unread state.
- A new message should update the row through the existing conversation state stream.
- Row selection/open is distinct from swipe action reveal.
- Swipe actions must not activate accidentally during scroll.
- Pinned items remain pinned according to existing ordering semantics regardless of skin.

### Story row

```text
load audience-filtered statuses
  -> partition unseen/seen/own
  -> render story identity
  -> tap => viewer session
  -> viewer advances/pauses
  -> update view state
  -> next story/contact
  -> exit => return to exact scroll position
```

## 6. Settings and customization components

### Setting row

```text
read canonical setting
  -> map value to semantic state
  -> render selected skin
  -> interaction
  -> validate
  -> persist atomically
  -> publish controller state
  -> dependent components rebuild only where needed
```

### Theme editor

```text
open
  -> clone committed snapshot
  -> choose scope
  -> choose category
  -> edit token/variant
  -> live preview
  -> contrast/compatibility validation
  -> Apply / Cancel / Reset
```

### Scope resolution

```text
temporary preview
  > conversation override
      > user global theme
          > app default
```

“`>`” means higher precedence.

## 7. Calls and floating surfaces

| Component | Normal | Connecting | Active | Minimized | Ended |
|---|---|---|---|---|---|
| Call activity capsule | hidden | optional | visible | visible | dismiss/archive |
| In-app PiP | hidden | optional | expand/drag | drag/snap | auto-remove |
| Ongoing call | hidden | handshake | controls active | controller-owned | summary |

PiP movement must use bounded hit targets, snap zones and no motion that causes content loss.

## 8. Media viewer

```text
open asset
  -> immersive mode
  -> load metadata
  -> decode progressively
  -> render
  -> gestures: pinch / pan / dismiss
  -> actions: save/share/details where permitted
  -> exit
  -> restore previous context
```

## 9. Camera and lenses

```text
enter camera
  -> permission check
  -> initialize stream
  -> capture or video record
  -> choose lens
  -> update effect engine
  -> capture
  -> edit
  -> add to draft
  -> send through existing message pipeline
```

The lens renderer cannot directly send messages; it only creates media output for the existing composer.

## 10. Generic component invariant

Every custom visual component must satisfy:

```text
semantic input
  -> capability check
  -> state render
  -> interaction
  -> command
  -> domain/service execution
  -> authoritative state
  -> minimal UI rebuild
```

That invariant prevents visual customization from creating separate business logic branches.
