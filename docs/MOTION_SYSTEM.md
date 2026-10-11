# MOTION SYSTEM & SPRING PHYSICS SPECIFICATION

## 1. Core Motion Principles

### 1.1 Tactile Spring Physics (Emil Kowalski & Apple HIG Benchmark)
* Motion in Chaty is never linear or artificially delayed. Transitions use physical spring dynamics where mass, stiffness, and damping ratio prevent unnatural bouncing while preserving snappy, organic responsiveness.
* **Standard Interactive Spring Curve**:
  * Cubic Bezier: `Cubic(0.16, 1.0, 0.3, 1.0)`
  * Duration: `240ms` to `280ms`
  * Use Case: Button tap releases, sheet slide-ups, card expansions.

---

## 2. Interaction Timing & Easing Catalog

| Interaction Family | Curve / Simulation | Duration | Target Property | Tactile / Haptic Pairing |
| :--- | :--- | :--- | :--- | :--- |
| **Button Tap Press** | `Curves.easeOutCubic` | `80ms` | Scale down to `0.97` | `HapticFeedback.lightImpact()` |
| **Button Tap Release** | `Cubic(0.16, 1.0, 0.3, 1.0)` | `180ms` | Scale up to `1.0` | None (instantaneous release) |
| **Bottom Sheet Slide** | `Cubic(0.2, 1.0, 0.25, 1.0)` | `280ms` | Translation Y + Fade | `HapticFeedback.selectionClick()` |
| **Dynamic Island Expand** | Custom Spring Simulation | `260ms` | Width, Height, Radius | `HapticFeedback.lightImpact()` |
| **Dynamic Island Collapse** | `Cubic(0.25, 1.0, 0.5, 1.0)` | `200ms` | Morph back to pill | None |
| **Message Bubble Arrival** | `Cubic(0.16, 1.0, 0.3, 1.0)` | `220ms` | Scale from `0.92` + Opacity | `HapticFeedback.lightImpact()` |
| **Reaction Popover** | Elastic / Springsettle | `240ms` | Scale from `0.6` with overshoot | `HapticFeedback.selectionClick()` |
| **Toast Alert Entrance** | `Curves.easeOutQuart` | `260ms` | Translation Y from `-40dp` | `HapticFeedback.mediumImpact()` |

---

## 3. Reduced Motion Support (`prefers-reduced-motion`)
* The motion system checks both system-wide accessibility settings and in-app motion preferences.
* When active:
  * Scale transforms are disabled (`scale: 1.0`).
  * Slide transitions convert to immediate opacity fades ($\le 80\text{ ms}$) or `Duration.zero`.
  * Physics simulations complete in 1 frame.
