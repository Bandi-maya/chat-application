# MASTER PRODUCT REQUIREMENTS DOCUMENT (PRD) — CHATY UI/UX REDESIGN

## 1. Product & Technical Baseline
* **Application**: Chaty (Private, customizable encrypted messaging and calling).
* **Framework**: Flutter 3.47.5, Dart 3.13.4, Android native Kotlin integration.
* **Architecture**: Clean Architecture (Domain, Data, Features, UI Core) with custom design token engine (`GBDesignSystem`).
* **Objective**: Elevate Chaty into a world-class, premium communication tool with Apple-grade tactile precision, fluid motion, strict Android edge-to-edge compliance, and zero loss of existing functionality.

---

## 2. Personas & Primary User Tasks
* **Privacy-Conscious Communicator**: Needs instant, clear visual confirmation of E2E encryption status; demands fast biometric app locking and intuitive locked chat vaults.
* **Power Customizer**: Loves tailoring their messaging experience (bubble shapes, colors, ticks, top bar headers, navigation icons, transition styles) without encountering UI breakages or lag.
* **Daily Voice & Video Caller**: Requires instant access to recent contacts, zero-friction call launching, and non-intrusive ongoing call management via a fluid Dynamic Island overlay.

---

## 3. UI/UX Transformation Requirements

### 3.1 Visual Hierarchy & Design System Elevation
* **Requirement ID**: `PRD-UI-01`
* **Specification**: Introduce a refined, high-cohesion design token palette that replaces harsh contrasting borders with subtle, frosted glass surfaces (`BackdropFilter` with 16dp sigma blur and 0.08 alpha border strokes).
* **Acceptance Criteria**: All surfaces display consistent elevation hierarchy; light and dark modes pass 4.5:1 contrast requirements.

### 3.2 Spring Motion & Micro-Interaction Engine
* **Requirement ID**: `PRD-UI-02`
* **Specification**: Replace all abrupt linear or standard curve transitions with physically tuned spring damping curves (`cubic-bezier(0.16, 1, 0.3, 1)` and custom spring controllers).
* **Acceptance Criteria**: Buttons depress slightly (`scale: 0.97`) on tap down and bounce back cleanly with light haptic feedback; transitions complete within 280ms.

### 3.3 Dynamic Island Island Overlay Refinement
* **Requirement ID**: `PRD-UI-03`
* **Specification**: Audit and refine the custom Dynamic Island status bar overlay:
  * Strict positioning relative to `MediaQuery.padding.top`.
  * Stable, non-jittering collapsed pill geometry ($120 \times 36\text{ dp}$ base).
  * Expansion on tap into a rich card displaying active call details or notification snippet with spring physics.
  * No infinite blinking or layout recalculation loops.
* **Acceptance Criteria**: Seamless expansion/collapse transitions at 120/144Hz; zero overlap with display punch-holes.

### 3.4 Edge-to-Edge Responsive Layouts
* **Requirement ID**: `PRD-UI-04`
* **Specification**: Eliminate all hardcoded offsets; wrap screens with responsive layout builders and strict `SafeArea` / `WindowInsets` handling.
* **Acceptance Criteria**: No text or interactive buttons clipped behind Android 3-button navigation, gesture pills, or camera cutouts across small (360dp width) to large tablet displays.

---

## 4. Technical & Performance Constraints
1. **Zero New Packages**: No adding third-party UI/icon packages to `pubspec.yaml`.
2. **Preserve All Settings**: All 40 customization screens in `lib/features/settings/` remain fully wired and operational.
3. **120/144Hz Refresh Rate**: Main list scrolling and chat timelines must maintain consistent 8.3ms / 6.9ms frame pacing without shader or GC stalls.
4. **Offline Resilience**: App functions seamlessly offline, gracefully queuing outgoing messages.
