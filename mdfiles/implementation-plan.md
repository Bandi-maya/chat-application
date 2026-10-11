# Chaty Customization System — Implementation Plan

## 0. Mission

Implement a premium, fully custom Chaty visual system on top of the existing application without deleting or changing existing business functionality. The implementation is a **renderer/variant upgrade**, not a product rewrite.

## 1. Baseline lock

The supplied documentation establishes Clean Architecture, deterministic controller-driven UI state, offline-first message queuing, custom bubbles/ticks, a central theme controller, an appearance variant controller, and a modal/design-system layer. Those contracts are locked unless a bug fix is explicitly required.

## 2. Delivery order

### Phase A — Inventory and contracts

- Freeze current component IDs, callbacks, domain models and service entry points.
- Create a canonical `ComponentRegistry`.
- Create `VariantRegistry` for nav, header, composer, bubble, tick, overlay, list, avatar and button families.
- Create `InteractionContract` definitions so a visual variant cannot omit a required action.
- Add snapshot/export support for theme configuration.

### Phase B — Token architecture

Create semantic tokens, not hard-coded colors:

```dart
ChatyTokens {
  colors: {
    canvas, surface, surfaceElevated, textPrimary, textSecondary,
    textMuted, divider, accent, accentStrong, success, warning, danger,
    sent, delivered, read, offline
  },
  shape: { radiusXs, radiusSm, radiusMd, radiusLg, bubbleRadius },
  spacing: { xs, sm, md, lg, xl },
  typography: { title, body, caption, label, mono },
  motion: { press, state, transition, sheet, emphasis },
  elevation: { none, low, medium, high }
}
```

Map the existing theme configuration into semantic roles first. Do not let a new skin bypass the existing `ThemeController`.

### Phase C — Renderers

Implement custom renderers in this order because later families depend on earlier contracts:

1. Scaffold and navigation
2. Header and identity
3. Surface primitives
4. Buttons/fields/selection controls
5. Modal/sheet/context surfaces
6. Chat list rows
7. Composer and media draft tray
8. Bubble and tick renderers
9. Reactions and message action surfaces
10. Calls/PiP
11. Status/stories
12. Camera/lens surfaces
13. Settings/customization editor

### Phase D — Interaction polish

Add one motion recipe per semantic interaction:

- press
- selection
- expand/collapse
- navigation change
- sheet presentation
- dismiss
- success
- retry
- recording start/stop
- reaction placement
- media open/close
- call minimize/restore

Do not add ambient motion to static content merely to make it look animated.

### Phase E — Customization editor

The customization editor should expose:

- Navigation style
- Header style
- Composer style
- Bubble style
- Delivery tick style
- Chat wallpaper
- Font scale / density
- Theme colors
- Motion profile
- Avatar shape
- List density
- Reaction animation profile

Every setting must show a live preview, current selection, reset option and scope.

Scopes:

```text
default
  -> global user theme
      -> per-conversation override
          -> transient preview (not persisted)
```

The most specific committed scope wins.

### Phase F — QA and regression

Use the matrix in `qa-test-matrix.md`. Run the entire app through each major skin at three layout classes:

- Compact mobile
- Expanded mobile/tablet
- Wide desktop/tablet

Also test reduced-motion mode, offline state, slow network, dynamic text scaling and permission denial.

## 3. File-level integration map

| Existing area | Upgrade point | Rule |
|---|---|---|
| `theme/theme_config.dart` | semantic token projection + variant IDs | preserve existing serialized fields |
| `theme/theme_controller.dart` | transactional preview/commit/reset | no business-service coupling |
| `controllers/appearance_variant_controller.dart` | central variant selection | only selects renderers |
| `design_system/app_components.dart` | scaffold/header/button/field skins | callbacks preserved |
| `design_system/chaty_modal.dart` | adaptive overlay shells | result semantics preserved |
| `design_system/chaty_kit.dart` | feedback/empty/sheet skins | centralized overlay semantics |
| `bubbles/*` | bubble renderer registry | message model unchanged |
| `ticks/*` | tick renderer registry | status semantics unchanged |
| `components/composer_components.dart` | composer skin adapter | draft/send/record state unchanged |
| `components/messaging_components.dart` | message/list state adapters | repository/service unchanged |
| `widgets/chat_wallpaper.dart` | wallpaper engine | contrast/readability guard |
| `menu/*` | context surface variants | action registry unchanged |
| `features/settings/*` | customization editor | canonical settings registry |
| `features/chats/*` | consume variants | do not fork chat logic per skin |

## 4. Suggested internal structure

```text
lib/ui/core/
  design_system/
    tokens/
    semantic/
    components/
    overlays/
    motion/
    accessibility/
    variants/
      navigation/
      headers/
      composer/
      bubbles/
      ticks/
      lists/
      buttons/
      inputs/
      avatars/
      surfaces/
      feedback/

lib/features/settings/customization/
  customization_screen.dart
  customization_controller.dart
  customization_preview.dart
  customization_scope.dart
  customization_history.dart
  customization_reset.dart

lib/domain/models/
  appearance_variant.dart
  customization_snapshot.dart
  motion_profile.dart
  icon_variant.dart

lib/data/services/
  customization_storage_service.dart
  customization_sync_service.dart
```

## 5. Variant registry contract

```dart
abstract interface class ChatyVariant<TConfig> {
  String get id;
  String get displayName;
  TConfig get defaults;
  bool supports(VariantContext context);
  Widget build(BuildContext context, TConfig config);
}
```

The renderer receives semantic data and configuration. It does not query Supabase, mutate messages, perform authentication, or write preferences directly.

## 6. Preview transaction

```text
open customization
  -> create immutable working snapshot
  -> modify working snapshot
  -> preview renderer tree
  -> user selects Apply
      -> validate snapshot
      -> persist atomically
      -> publish controller state
      -> render app from committed snapshot
  -> user selects Cancel
      -> discard snapshot
```

## 7. Reset transaction

Reset must be scoped:

- reset this component
- reset this category
- reset this conversation
- reset all customizations

A confirmation is required only for destructive broad resets, not for reversible local preview changes.

## 8. Responsive implementation

Do not choose a layout because the device is “phone/tablet/desktop”. Measure available window constraints and adapt composition.

```text
width < 600dp
  -> bottom navigation / compact header / full-width composer
600..839dp
  -> adaptive navigation / wider rows / modal sheets
>= 840dp
  -> rail or split view / side surfaces / desktop keyboard affordances
```

Exact breakpoints are configuration, not hard-coded business rules.

## 9. Performance gates

- Prefer `AnimatedBuilder`/stable children for animation islands.
- Use lazy message/list construction.
- Bound blur/filter usage.
- Avoid unnecessary saveLayer/opacity chains.
- Keep high-frequency animated regions isolated with repaint boundaries when profiling shows benefit.
- Avoid rebuilding the whole chat screen for a single message reaction or typing update.
- Never use animation to compensate for slow state updates.

## 10. Acceptance gates

A build is not complete until:

- Existing functionality behaves identically.
- Every variant has a stable ID and fallback.
- Invalid theme combinations gracefully degrade to semantic defaults.
- Navigation, composer, message and modal actions remain callable in every skin.
- All screens reflow instead of merely stretching.
- Accessibility labels/actions remain correct across skins.
- Reduced-motion mode removes nonessential transition effects.
- Offline/send/retry states remain testable in every bubble/tick skin.
- No external UI component library is added.
- No default Flutter visual component is shipped unskinned where a Chaty component contract exists.

## 11. Definition of done

The customization system is done when a user can change the supported visual families, observe the entire app update consistently, preview before commit, revert safely, use the same message/call/task logic underneath, and move between compact and wide layouts without functional loss.
