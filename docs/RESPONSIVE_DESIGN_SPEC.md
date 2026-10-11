# RESPONSIVE DESIGN & WINDOW INSETS SPECIFICATION

## 1. Multi-Device & Density Matrix
Chaty is engineered to run flawlessly across the entire spectrum of mobile form factors:
1. **Compact Phones** (width 320dp - 360dp, e.g. small Android devices).
2. **Standard Modern Smartphones** (width 390dp - 430dp, tall 19.5:9 / 20:9 aspect ratios).
3. **Foldables & Large Displays** (width $\ge 600\text{ dp}$, dual-column / master-detail layouts in landscape).

---

## 2. Android 14/15 Edge-to-Edge & System Insets

### 2.1 The Edge-to-Edge Contract
* Content renders behind the transparent system status bar and navigation bar.
* All interactive controls, floating bars, and inputs must use dynamic insets:
  * Top offset: `MediaQuery.paddingOf(context).top`
  * Bottom offset: `MediaQuery.paddingOf(context).bottom` (accommodates 3-button navigation and gesture pill)
  * Display Cutout / Punch hole: Respect horizontal safe areas on landscape orientation.

### 2.2 Software Keyboard Handling
* Message input fields and form screens must never be covered by the virtual keyboard (`IME`).
* Use `Scaffold.resizeToAvoidBottomInset: true` combined with smooth animated inset transitions (`MediaQuery.viewInsetsOf(context).bottom`) so input bars ride the keyboard smoothly without jumping or flickering.

---

## 3. Responsive Rules & Anti-Patterns

| Component / Area | Required Responsive Behavior | Prohibited Anti-Pattern |
| :--- | :--- | :--- |
| **Top App Bar** | Flexible header with title truncation / marquee if contact name is long. | Fixed width overflow or text clipping. |
| **Chat Bubble Timeline** | Max bubble width constrained to `75%` of screen width; wraps text cleanly. | Hardcoded width like `width: 320` causing overflow on narrow screens. |
| **Dynamic Island Overlay** | Top position calculated from `MediaQuery.paddingOf(context).top + 4dp`. | Hardcoded `top: 40dp` colliding with punch hole on high-density devices. |
| **Bottom Navigation Bar** | Icons scale cleanly; labels hide or collapse gracefully on narrow widths. | Fixed 400dp width bottom bar clipping at screen edges. |
| **Settings Grid/List** | Grouped cards scale to 100% width with 16dp margins. | Fixed card dimensions. |
