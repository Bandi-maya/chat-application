# Chaty Customization QA & Release Matrix

## 1. Variant coverage

Every component variant must be tested in:

- light theme
- dark theme
- AMOLED/dark high contrast where supported
- large text
- reduced motion
- compact width
- medium width
- expanded width
- touch input
- keyboard/pointer input where supported
- RTL where supported

## 2. Navigation tests

| Test | Expected |
|---|---|
| tap active destination | existing active-destination behavior remains |
| tap new destination | existing route/state behavior remains |
| badge 0→1 | badge appears without shifting icon anchor |
| badge 99→100 | caps/format remains stable |
| long label | no overlap; accessible name preserved |
| narrow width | intentional compact layout |
| wide width | rail/dock transformation if selected |
| reduced motion | no decorative bounce |

## 3. Header tests

- back route works
- title opens existing info route where configured
- call/video actions use existing coordinators
- overflow menu anchors correctly
- selection mode shows correct count
- keyboard focus order is logical

## 4. Composer tests

- typing changes send/mic state correctly
- attachment tray opens
- camera opens
- emoji opens
- reply context is preserved
- edit context is preserved
- recording permission denial is handled
- record/lock/cancel works
- draft survives expected lifecycle events
- send uses existing queue
- failed message exposes retry
- keyboard insets do not cover composer

## 5. Bubble/tick tests

For each bubble/tick variant:

- short text
- long text
- multiline text
- emoji-only
- media
- voice
- reply
- reaction
- edited
- pinned/starred
- queued
- sent
- delivered
- read
- failed
- selected

## 6. Overlay tests

- focus enters modal
- focus returns on close
- Escape/back behavior correct
- outside tap behavior matches policy
- nested popover/menu works
- dialog inside sheet works
- long content scrolls
- header/footer remain fixed when required
- safe-area insets preserved
- collision with bottom/top edges is handled

## 7. Performance tests

Measure in Flutter profile mode.

Watch for:

- frame time above target during message scrolling
- dropped frames on theme switching
- excessive rebuild count
- image decoding stalls
- blur/saveLayer spikes
- large repaint regions
- continuous animation CPU/GPU usage

Reference Flutter performance guidance notes that opacity, clipping with saveLayer and image filters can be expensive, and advises careful use of `AnimatedBuilder` subtrees and lazy lists.

## 8. Accessibility tests

- semantic labels exist
- focus is visible
- target sizes are adequate
- color is not the sole state indicator
- contrast is acceptable
- text scaling does not clip critical content
- reduced motion disables nonessential animation
- screen reader order follows visual intent

## 9. Regression rule

Before release, compare the default snapshot against a baseline build/screenshot set.

The zero-customization path should be visually and behaviorally stable except for explicitly accepted bug fixes.
