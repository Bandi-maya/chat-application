# Chaty Design System and UX Rules

## 1. Design direction
Create a premium, calm, recognizable messaging interface with consistent surfaces, clear hierarchy, responsive layouts, purposeful animation and user-controlled personalization. Do not copy GBWhatsApp assets or reproduce visual noise simply because a reference APK contains many toggles.

## 2. UX principles
| Principle | Implementation rule | Testable acceptance |
|---|---|---|
| Consistency | Same header, row, bubble, sheet, input and spacing tokens across screens | A token change affects all intended consumers and no unrelated component |
| Visibility of state | Loading, sent, delivered, read, upload, recording, call and selected-template states are explicit | Each async operation has loading/success/error behavior |
| Feedback | Every actionable control gives immediate truthful feedback | No dead taps or fake success |
| User control | Preview, apply, undo where safe, category reset and full reset | Cancel preview leaves applied profile unchanged |
| Error prevention | Validation before destructive/invalid actions | Invalid template or media shows actionable error |
| Recognition over recall | Clear category names, preview thumbnails, descriptions and search | User can find a setting without knowing internal terms |
| Progressive disclosure | Common presets first; granular controls inside categories | Settings home is not an unstructured wall of switches |
| Safe defaults | Useful default theme, accessible contrast and restrained motion | Fresh install is readable and performant |
| Reversibility | Reset/undo for appearance; confirm destructive data actions | Appearance reset never deletes messages |
| Platform familiarity | Respect Android back/system bars and iOS safe areas/gestures | No content under notch/status/home indicator |
| Responsive layout | SafeArea, keyboard insets, small/large screens and text scaling | No clipping/overlap at supported device sizes |
| Accessibility | Screen-reader labels, scalable text, contrast, focus, target size | TalkBack/VoiceOver and large text tests pass |
| Performance | Avoid unnecessary full-tree rebuilds and heavy animation | Frame-time and scroll regression checks pass |
| Privacy by design | Don't log message content/secrets; explain privacy toggles honestly | Security review and redaction tests pass |

## 3. Component contract
Each shared component has:
- a stable API and explicit state inputs;
- a theme token source;
- semantic accessibility labels/actions;
- loading, disabled, selected and error variants where applicable;
- predictable touch/keyboard behavior;
- widget/golden tests;
- no backend calls hidden in presentation widgets.

## 4. Token groups
- Color: primary/accent, background, surface/elevated surface, outline, text primary/secondary, incoming/outgoing bubble, link, unread badge, presence, status seen/unseen, semantic success/warning/error.
- Typography: display/title/body/label/caption styles, weight, line height, supported family and system scaling.
- Spacing: named 4/8/12/16/20/24/32 logical-unit scale; use consistent values rather than arbitrary per-screen gaps.
- Shape: field, chip, card, bubble, sheet, avatar, badge radii with documented variants.
- Elevation: named surface levels; avoid excessive shadows.
- Motion: duration/curve presets, route transitions, reduced-motion alternative.
- Iconography: consistent stroke/fill, semantic meaning and selected/unselected states.
- Layout: safe areas, breakpoints, minimum targets, keyboard and inset rules.

## 5. Customization UX
1. Settings → Appearance shows current template and a concise preview.
2. Template gallery offers built-in presets and custom templates.
3. Each category shows controls grouped by what they affect, with a live sample.
4. Preview uses the real shared components, not a static screenshot.
5. Apply validates and persists the full profile atomically.
6. Cancel/discard returns to the previously applied profile.
7. Category reset preserves all other categories.
8. Full reset has confirmation and explains what is reset (appearance only).
9. Import displays a preview and validation warnings before applying.
10. Export includes only versioned appearance values and no personal data.

## 6. Layout and interaction rules
- Maintain platform safe areas; never add arbitrary top padding to compensate for incorrect system-bar handling.
- Keyboard opening must resize/inset the composer correctly; message list must remain scrollable and preserve intended anchor.
- Bottom navigation and floating controls must respect system gesture regions and bottom insets.
- Modal content scrolls internally; headers/footers remain visible; no horizontal overflow.
- Inputs share radius, focus, disabled, validation and error styles.
- Destructive actions use explicit labels and confirmation where irreversible.
- Avoid changing screen context while the user is typing.
- All controls remain reachable with keyboard/screen reader where platform supports it.

## 7. Accessibility thresholds
Use current platform and WCAG guidance as project acceptance targets:
- Text contrast at least 4.5:1 for ordinary text and 3:1 for large text, except documented semantic/platform cases.
- Interactive targets at least 48x48 dp on Android and 44x44 pt on iOS where practical.
- Support system font scaling and large text without clipping.
- Respect reduced motion and do not rely on animation or color alone to communicate state.
- Test TalkBack and VoiceOver.
References: https://docs.flutter.dev/ui/accessibility/ui-design-and-styling ; https://docs.flutter.dev/ui/accessibility ; https://developer.apple.com/design/human-interface-guidelines/accessibility ; https://developer.apple.com/design/human-interface-guidelines/motion

## 8. Visual QA matrix
Test default/light/dark/high-contrast templates, small/large screens, large text, keyboard visible, long names, long messages, media-heavy messages, empty lists, loading states, errors, and Android/iOS system insets.
