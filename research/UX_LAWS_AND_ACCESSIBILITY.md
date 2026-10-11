# UX LAWS, ACCESSIBILITY COMPLIANCE & ENGINEERING RULES

## Executive Summary
This document codifies the psychological, ergonomic, and accessibility laws that govern every UI decision in Chaty. Each law is mapped to specific screens and components, complete with measurable engineering rules and verification methods.

---

## 1. Usability & Cognitive Science Laws

### 1.1 Fitts's Law
* **Formal Definition**: The time required to rapidly move to a target area is a function of the ratio between the distance to the target and the width of the target: $T = a + b \log_2(1 + \frac{D}{W})$.
* **Chaty Application**:
  * Primary actions (Send button in chat, Call accept button, Floating Action Buttons) are positioned directly inside the primary thumb reach zone (bottom 35% of the viewport).
  * Interactive target sizes are strictly $\ge 48 \times 48\text{ dp}$ even if visible icons are $24 \times 24\text{ dp}$.
* **Verification**: Touch target automated audit (`flutter test` or accessibility inspector asserting hit test dimensions $\ge 48\text{ dp}$).

### 1.2 Hick-Hyman Law
* **Formal Definition**: The time it takes to make a decision increases logarithmically with the number and complexity of choices: $T = b \log_2(n + 1)$.
* **Chaty Application**:
  * Limit primary navigation tabs to 4 items (Chats, Calls, Tasks, Settings).
  * Action menus and contextual sheets categorize options logically (e.g., Media, Location, Contact) with distinct visual glyphs and max 6 initial actions before secondary expansion.
* **Verification**: Usability benchmark assessing time to initiate audio call from chat header $\le 1.2\text{ seconds}$.

### 1.3 Jakob's Law
* **Formal Definition**: Users spend most of their time on other apps. This means they prefer your app to work the same way as all the other apps they already know.
* **Chaty Application**:
  * Messaging conventions: Incoming messages aligned left, outgoing aligned right, swipe right to reply, tap avatar to view profile.
  * Search bar located at top of list, pulls down to refresh, back arrow at top-left.
* **Verification**: Zero custom proprietary gesture overrides for standard system navigation expectations.

### 1.4 Miller's Law & Cognitive Chunking
* **Formal Definition**: The average person can only keep 7 (plus or minus 2) items in their working memory.
* **Chaty Application**:
  * Grouped settings cards chunk configuration options into cohesive clusters of 3-5 items (e.g., Privacy, Notifications, Storage, Account).
  * Verification phone numbers formatted into chunks (e.g., `+1 (555) 019-2834`).
* **Verification**: Visual audit verifying no flat list in Settings exceeds 7 ungrouped rows.

### 1.5 Doherty Threshold
* **Formal Definition**: Productivity soars when a computer and its users interact at a pace ($\le 400\text{ ms}$) that ensures neither has to wait on the other.
* **Chaty Application**:
  * Immediate optimistic UI updates on sending a message: render message bubble immediately in sending state within 16ms (1 frame); transition to sent upon network receipt.
  * Touch response scale down occurs within 50ms of finger contact.
* **Verification**: Latency measurement from pointer down to visual state change $\le 50\text{ ms}$.

### 1.6 Peak-End Rule
* **Formal Definition**: People judge an experience largely based on how they felt at its peak (intense point) and at its end.
* **Chaty Application**:
  * Call completion screen provides smooth disconnection sound and clear duration badge.
  * Media upload completion concludes with a crisp checkmark micro-animation and haptic pulse.
* **Verification**: Manual QA validation of call end sequence and media upload completion states.

---

## 2. Accessibility Engineering (WCAG 2.2 AA & Android A11y Standards)

### 2.1 Color Contrast
* **Criteria**: Minimum 4.5:1 for normal text ($< 18\text{ pt}$ / $24\text{ dp}$), 3.0:1 for large text and interactive icons.
* **Chaty Tokens**:
  * Primary Text on Surface: `#0F172A` on `#FFFFFF` (21:1 in Light Theme); `#F8FAFC` on `#0A0F1D` (18.5:1 in Dark Theme).
  * Secondary Text on Surface: `#64748B` on `#FFFFFF` (5.2:1 in Light Theme); `#94A3B8` on `#0A0F1D` (6.8:1 in Dark Theme).
* **Verification**: Automated contrast calculator script validating all theme tokens in `tokens/color_tokens.dart`.

### 2.2 Screen Reader (TalkBack) Semantics
* **Criteria**: All interactive controls must provide meaningful `Semantics` widgets with label, hint, and state.
* **Chaty Rules**:
  * Custom vector icons wrapped in `Semantics(label: '...', button: true, child: ...)`.
  * Message status ticks announced as "Sending", "Sent to server", "Delivered to device", or "Read by recipient".
  * Dynamic Island announced as "Active voice call with [Name], duration 2 minutes, double tap to expand".
* **Verification**: Automated semantic tree inspection (`tester.getSemantics()`) for critical components.

### 2.3 Reduced Motion Support
* **Criteria**: Respect system-wide `prefers-reduced-motion` or user animation scale settings.
* **Chaty Implementation**:
  * Query `MediaQuery.disableAnimationsOf(context)` or custom user settings in `lib/ui/core/design_system/chaty_motion.dart`.
  * When animations are disabled, replace durations with `Duration.zero` or instant opacity transitions (duration $\le 50\text{ ms}$).
* **Verification**: Widget tests asserting duration is `Duration.zero` when `disableAnimations: true`.
