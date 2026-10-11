# CHATY UNIFIED DESIGN SYSTEM ARCHITECTURE

## 1. Design Tokens & Semantic Foundations

### 1.1 Semantic Color Roles

```dart
abstract final class ChatyColorTokens {
  // Brand & Accent
  static const Color brandPrimary = Color(0xFF0D9488); // Teal-600
  static const Color brandPrimaryLight = Color(0xFF14B8A6); // Teal-500
  static const Color brandPrimaryDark = Color(0xFF0F766E); // Teal-700
  static const Color brandAccent = Color(0xFF6366F1); // Indigo-500

  // Neutral Surfaces (Light)
  static const Color lightBackground = Color(0xFFF8FAFC);
  static const Color lightSurface = Color(0xFFFFFFFF);
  static const Color lightSurfaceSubtle = Color(0xFFF1F5F9);
  static const Color lightBorder = Color(0xFFE2E8F0);
  static const Color lightTextPrimary = Color(0xFF0F172A);
  static const Color lightTextSecondary = Color(0xFF64748B);

  // Neutral Surfaces (Dark / OLED)
  static const Color darkBackground = Color(0xFF090D16);
  static const Color darkSurface = Color(0xFF111827);
  static const Color darkSurfaceSubtle = Color(0xFF1F2937);
  static const Color darkBorder = Color(0xFF374151);
  static const Color darkTextPrimary = Color(0xFFF9FAFB);
  static const Color darkTextSecondary = Color(0xFF9CA3AF);

  // System States
  static const Color success = Color(0xFF10B981);
  static const Color warning = Color(0xFFF59E0B);
  static const Color error = Color(0xFFEF4444);
  static const Color info = Color(0xFF3B82F6);
}
```

### 1.2 Spacing & Layout Scale (4dp Grid)
* `space2`: 2.0 dp (micro spacing, icon offsets)
* `space4`: 4.0 dp (tight gap, badge padding)
* `space8`: 8.0 dp (inner element padding)
* `space12`: 12.0 dp (card item spacing)
* `space16`: 16.0 dp (standard screen horizontal margin)
* `space20`: 20.0 dp (section header margin)
* `space24`: 24.0 dp (screen header padding)
* `space32`: 32.0 dp (major layout separation)

### 1.3 Shape & Corner Radii
* `radiusSm`: 8.0 dp (small chips, tags, status badges)
* `radiusMd`: 12.0 dp (standard input fields, settings items)
* `radiusLg`: 16.0 dp (grouped settings cards, message bubbles)
* `radiusXl`: 24.0 dp (modal bottom sheets, floating cards)
* `radiusFull`: 999.0 dp (circular avatars, floating action buttons, pill badges)

---

## 2. Elevation & Material Layers

1. **Layer 0 (Base)**: Solid background surface (`lightBackground` / `darkBackground`).
2. **Layer 1 (Card/Group)**: Frosted surface container with subtle border (`Color.withValues(alpha: 0.08)`) and minimal ambient shadow (`blurRadius: 8, spreadRadius: 0, color: Color(0x08000000)`).
3. **Layer 2 (Floating/Dropdown)**: Frosted glass material with `BackdropFilter(sigmaX: 16, sigmaY: 16)` and elevation 4.
4. **Layer 3 (Modal/Overlay)**: Full backdrop dimming (`Color(0x4D000000)`) with spring entrance bottom sheet.

---

## 3. Dynamic Customization Preservation
The design system directly integrates with [`GBDesignSystem`](file:///c:/Users/Bandi/Desktop/desktop/chat-application/lib/ui/core/design_system/gb_design_system.dart):
* User-selected primary color dynamically re-tints `brandPrimary`.
* User-selected bubble styles (`round`, `sharp`, `ios_modern`, `telegram_sleek`) apply to message containers.
* Custom tick sets (`default`, `double_check`, `minimal_dot`, `gradient_tick`) render inside message status indicators without losing synchronization.
