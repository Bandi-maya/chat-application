# Chaty Island Research: Atoll Clean-Room Behavioral Analysis

> **Disclaimer & Legal Note**: `Ebullioscopic/Atoll` is licensed under **GPL-3.0**. No source code, snippets, or derivative implementations from Atoll are copied or included into Chaty. This document details high-level behavioral patterns, interaction state machines, and UX heuristics observed in notch/cutout HUD paradigms, mapped strictly to clean-room Android APIs.

---

## 1. Notch & Cutout Geometry & Window Anchoring

### Observed Paradigm
- In macOS desktop notches (e.g. MacBook Pro M1/M2/M3), the display cutout is hardware-fixed at the top edge center.
- Closed state: A black pill physically snug with the notch boundary.
- Expansion state: The pill grows outwards symmetrically or downwards asymmetrically depending on content width/height demand.
- Hit testing: Mouse hover or click triggers expansion. Closed state maintains a tight bounding box to prevent blocking desktop clicks around the notch.

### Android Equivalent Architecture
- **Window Type**: `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`.
- **Cutout Insets**: Android 9+ (`DisplayCutout`), API 30+ (`WindowInsets.getDisplayCutout()`, `boundingRectTop`).
- **Positioning**: Center horizontal, y-offset computed from the exact bounding rectangle top/center rather than static arbitrary status bar multipliers.
- **Window Sizing**: Dynamic window bounds matching the current animated pill bounds, ensuring transparent overlay areas never intercept touches meant for underlying apps.

---

## 2. Priority & State Machine Coalescing

### Observed Priority Model
1. **Critical Real-time Streams**: Live calls, VoIP, emergency audio.
2. **Interactive Alerts**: Direct user communications (instant messaging notifications).
3. **Persistent Media Sessions**: Now Playing audio, track seek, playback state changes.
4. **Hardware/Peripheral Transient HUDs**: Charging connection, battery alerts, accessory pairing.
5. **Passive Notifications**: Low-importance generic notifications.

### Android Mapping
- Lower priority states shrink to a **Minimal Dot** or trailing indicator when a higher-priority activity is active (e.g. active phone call in compact pill with a small album art dot or waveform).
- Once the higher-priority activity ends, the island spring-morphs back to reveal the media or unread notification stack.

---

## 3. Media Controls & Seeking Dynamics

### Observed Paradigm
- Progress scrubber smoothly updates during active playback.
- Swiping horizontally on artwork or controls triggers next/previous track.
- Tap toggles play/pause with instant haptic and morph feedback.

### Android Mapping
- Hook into `MediaSessionManager` and active `MediaController`.
- Progress tracking via `PlaybackState.position + (SystemClock.elapsedRealtime() - lastUpdateTime) * speed`, updated with a Choreographer-aligned ticker only while visible.
- Art extraction with strict memory bounding (decode to ≤256 px).

---

## 4. Spring Physics & Motion Character

### Observed Parameters
- Non-linear spring dynamics (critically damped or slight overshoot for pop-in).
- Blur-scale transitions between dissimilar layouts.
- Squircle / continuous corner curvature scaling smoothly without corner clipping or radius snapping.

### Adopted vs. Rejected Patterns
- **Adopted**: Single unified progress parameter driving width, height, superellipse corner curvature, and content crossfade.
- **Adopted**: Rubber-band interactive drag-down with velocity-based commitment threshold.
- **Rejected**: Constant background polling; instead, completely event-driven via Android broadcasts and system callbacks.
