# Chaty Island Research: Apple Human Interface Guidelines & Dynamic Island Reference

## 1. Dimensional Reference & Ratios
- **Compact State**: Approximately 126×36 pt on reference devices (split into Leading ~28 pt and Trailing ~28 pt with camera hole isolation in between).
- **Expanded State**: Full readable card width (screen width minus horizontal margins, capped at ~380–392 dp for thumb reachability and aesthetics), corner radius ~32–36 dp continuous curve (squircle).
- **Minimal State**: Detached dot (~14×14 dp to 24×24 dp) separated across the cutout when two concurrent live activities exist.

## 2. Priority Hierarchy & Interruption Rules
1. **Ongoing Call / Ringing** (Top Priority - never suppressed by transient alerts).
2. **Active Timer / Emergency** (High Priority).
3. **Incoming Communication Event** (Brief Peek / Pop-in, ~3–5 seconds timeout).
4. **Active Media Playback** (Sustained Live Activity in background).
5. **Charging / Hardware HUD** (Transient pop-in ~2.5s).

## 3. Motion & Transition Heuristics
- **Physical Continuity**: The island never teleports or abruptly swaps views. Every size change morphs seamlessly from the camera anchor.
- **Content Staggering**: When expanding, the black pill container expands first (reaching ~60% scale) before inner typography and action buttons fade and slide in.
- **Spring Parameters**:
  - Pop-in: Stiffness ~420, Damping Ratio ~0.72 (vibrant entry).
  - Expansion: Stiffness ~380, Damping Ratio ~0.78 (smooth fluid stretch).
  - Collapse: Stiffness ~520, Damping Ratio ~0.90 (snappy, low bounce).
- **Haptic Tactility**: Crisp, synchronized haptics on expand, action press, and dismiss.

## 4. Architectural Rules for Chaty Island
- Pure black fill (`#000000`) in compact mode to merge with the glass of the camera punch-hole.
- Subtle edge highlight (≤8% white stroke or specular glow) only when expanded.
- Never obstruct native status bar elements (battery / time / signal) when in compact mode.
