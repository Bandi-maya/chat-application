# UI Reference Research Catalog and Chaty Design Decisions

**Research update:** 9 October 2026  
**Target codebase:** Chaty Flutter + Supabase  
**Implementation constraint:** Study public patterns only. Do not install React/website libraries in the Flutter app, copy proprietary code/assets, or replace current working behavior.

## Scope and honesty

This is a curated research catalogue built from publicly available design-system documentation, component galleries, UX-pattern collections, and platform guidance relevant to mobile messaging, settings, menus, customization, responsive layout and motion. It is not a claim that every page on the entire internet—or every individual component on every listed site—was exhaustively inspected. The list is intended to be expandable and gives a specific research entry point plus the distinct design question it can inform.

Research was applied to Chaty's existing code rather than treated as a request to port React components. The implementation must use the app's current Flutter APIs and existing dependencies. **No external dependency is added by this research.**

## Part A — Component and motion reference sites

| # | Reference | Useful research entry | Distinct capability/pattern to study for Chaty |
|---:|---|---|---|
| 1 | [21st.dev](https://21st.dev/) | [Dropdown/menu research](https://docs.21st.dev/blog/react-dropdown-menu-components) | Choose the correct interaction contract: action menu vs single-value select vs searchable combobox vs navigation menu. |
| 2 | [React Bits](https://reactbits.dev/) | Components | Source-owned animated primitives, with motion treated as a small component rather than a page effect. |
| 3 | [Magic UI](https://magicui.design/docs/components) | [Animated list](https://magicui.design/docs/components/animated-list) | Staggered list entry for events; only use when it does not delay reading or cause unnecessary animation. |
| 4 | [shadcn/ui](https://ui.shadcn.com/docs/components) | [Component documentation](https://ui.shadcn.com/docs/components/dropdown-menu) | Copy-owned component architecture, explicit variants and theme-token integration. |
| 5 | [Ant Design](https://ant.design/components/menu/) | Menu | Predictable grouping and hierarchy for dense settings/navigation. |
| 6 | [MUI](https://mui.com/material-ui/react-menu/) | Menu | Anchored menu behavior, dismiss interactions and positioning. |
| 7 | [Origin UI](https://originui.com/) | Components | Practical form/control variations and composed settings surfaces. |
| 8 | [COSS UI](https://coss.com/ui/docs/components/menu) | [Menu](https://coss.com/ui/docs/components/menu) | Grouped actions and responsive menu-to-drawer composition. |
| 9 | [Skiper UI](https://skiper-ui.com/) | Component catalogue | Scroll-linked transitions and active-section feedback; adapt cautiously to app navigation. |
| 10 | [ThreeUI](https://threeui.com/browse) | Browse | 3D/shader visual treatment as optional inspiration, not a default for high-frequency chat screens. |
| 11 | [Scrolltide](https://www.scrolltide.co/components) | Components | Motion principles for focus reveals and 3D carousel interactions; generally not used in the live chat timeline. |
| 12 | [Animista](https://animista.net/) | Animation catalogue | Name and tune simple CSS motion families; translate only appropriate timing/curves to Flutter. |
| 13 | [Motion](https://motion.dev/) | Docs | State-driven enter/exit, gestures, and reduced-motion-aware transitions. |
| 14 | [Aceternity UI](https://ui.aceternity.com/components) | Components | Hover-responsive docks and expandable navigation as references for large displays. |
| 15 | [Radix Primitives](https://www.radix-ui.com/primitives/docs/components/dropdown-menu) | [Dropdown menu](https://www.radix-ui.com/primitives/docs/components/dropdown-menu) | Checkable items, focus management, typeahead, submenu dismissal and collision-aware positioning. |
| 16 | [Base UI](https://base-ui.com/react/components/menu) | [Menu](https://base-ui.com/react/components/menu) | Separate menu-action and select-value semantics, plus accessible item states. |
| 17 | [Kibo UI](https://www.kibo-ui.com/docs) | Docs | Functional composable primitives such as color pickers, media dropzones and chat-oriented components. |
| 18 | [Chakra UI](https://chakra-ui.com/docs/components/menu/usage) | Menu | Clear component state APIs and consistent spacing/variants. |
| 19 | [React Aria / React Spectrum](https://react-spectrum.adobe.com/Menu) | Menu | Screen-reader announcement, menu item labels, selection state and restrictions on interactive controls inside menu rows. |
| 20 | [Headless UI](https://headlessui.com/react/menu) | Menu | Interaction behavior independent of visual styling. |
| 21 | [Tailwind Plus](https://tailwindcss.com/plus/ui-blocks/application-ui/navigation) | Application navigation | Information architecture patterns for responsive app shells. |
| 22 | [Park UI](https://park-ui.com/docs/components/menu) | Menu | Token-driven component composition and predictable menu states. |
| 23 | [Mantine](https://mantine.dev/core/menu/) | Menu | Placement, offsets and submenu behavior for action surfaces. |
| 24 | [Tremor](https://www.tremor.so/) | Components | Dense information presentation and status/data hierarchy for admin-style areas. |
| 25 | [Motion Primitives](https://motion-primitives.com/) | Components | Reusable motion pieces with tunable duration and interactive states. |
| 26 | [OriginKit](https://www.originkit.dev/docs/components) | Components | Component-first live previews and editing controls where a preview maps directly to the chosen output. |
| 27 | [Cult UI](https://www.cult-ui.com/docs) | Docs | Motion-rich niche components; inspiration only, with effects reduced for accessibility and battery. |
| 28 | [Radix Themes](https://www.radix-ui.com/themes/docs/components/context-menu) | Context menu | Long-press/right-click selection and context-sensitive commands. |
| 29 | [Flutter Material widgets](https://docs.flutter.dev/ui/widgets/material) | Material components | Flutter-native patterns and semantics; use existing framework components rather than porting React APIs. |
| 30 | [Flutter performance best practices](https://docs.flutter.dev/perf/best-practices) | Performance | Avoid unnecessary intrinsic layout work and keep work bounded in frequently rebuilt views. |

## Part B — Real-product UI and flow research

Each source has a different research role; use screenshots and flow recordings as evidence of the user problem and sequence, not as a source of assets to copy.

| # | Reference | Research entry | Distinct design question |
|---:|---|---|---|
| 31 | [Mobbin](https://mobbin.com/) | Mobile/web app screens | How do real shipped apps organize their settings, navigation, status, messaging and profile screens? |
| 32 | [SaaSFrame](https://www.saasframe.io/) | SaaS screens | How are dense preferences grouped without making every section look like a separate card? |
| 33 | [Curated](https://www.curated.design/) | Design references | Which information hierarchy is clear without decorative noise? |
| 34 | [Recent.design](https://recent.design/) | Recent design work | Which contemporary visual patterns remain readable instead of following short-lived trends? |
| 35 | [Awwwards](https://www.awwwards.com/) | Interaction inspiration | What motion conveys state rather than merely decorating the page? |
| 36 | [Lapa Ninja](https://www.lapa.ninja/) | Web galleries | How is product branding established through typography, spacing and restraint? |
| 37 | [Land-book](https://land-book.com/) | Web gallery | How are large design systems visually consistent from header through sections? |
| 38 | [Dribbble](https://dribbble.com/) | UI exploration | How might a composition be explored; verify every interaction against a real flow before adopting it. |
| 39 | [Behance](https://www.behance.net/) | Case studies | How is a design rationale tied to user and brand requirements? |
| 40 | [Godly](https://godly.website/) | Web experiences | How do transitions and visual contrast direct attention; avoid bringing marketing-page spectacle to messaging. |
| 41 | [Page Flows](https://pageflows.com/) | Recorded product flows | What exact screen sequence, cancel behavior and success/error state does a workflow require? |
| 42 | [Refero](https://refero.design/) | [UI/UX research](https://refero.design/) | Real personalization, chatting, editing, deletion, search, skeleton and account-setting patterns. |
| 43 | [Screenlane](https://screenlane.com/) | Mobile UI patterns | What layout behavior survives real phone screen constraints? |
| 44 | [UI Garage](https://uigarage.net/) | UI examples | What component boundaries can be reused without blending unrelated patterns? |
| 45 | [Collect UI](https://collectui.com/) | Component examples | Alternative control compositions for comparison, not direct copying. |
| 46 | [Really Good UX](https://www.reallygoodux.io/) | UX examples | What copy and feedback reduce uncertainty at decision points? |
| 47 | [Growth.Design](https://growth.design/) | UX psychology case studies | Which user bias is involved and how to avoid exploiting it in privacy-sensitive UI? |
| 48 | [Pttrns](https://pttrns.com/) | Mobile patterns | How familiar mobile navigation conventions are composed into complete flows. |
| 49 | [One Page Love](https://onepagelove.com/) | Page collection | How to keep a single-purpose screen clear without importing web-only layout assumptions. |
| 50 | [Muzli](https://muz.li/) | Design discovery | Broader visual references for typography, density and product tone. |
| 51 | [UXArchive](https://uxarchive.com/) | Mobile flows | What happens before and after a screen; check the full journey, not an isolated mockup. |
| 52 | [SiteInspire](https://www.siteinspire.com/) | Web design gallery | How a design system sustains consistent visual grammar across pages. |
| 53 | [Httpster](https://httpster.net/) | Web showcase | Unusual layouts that may inform low-frequency promotional/empty states, not the core chat flow. |
| 54 | [Minimal Gallery](https://minimal.gallery/) | Minimal interfaces | How reduction can preserve clarity without hiding essential actions. |
| 55 | [Commerce Cream](https://commercecream.com/) | Commerce interfaces | Product/media hierarchy and stateful actions around purchase-like flows. |
| 56 | [Best Website Gallery](https://bestwebsite.gallery/) | Website gallery | Broad visual alternatives and layout compositions. |
| 57 | [The FWA](https://thefwa.com/) | Interactive experiences | Experimental motion boundaries; keep them out of critical security and message actions. |
| 58 | [CSS Design Awards](https://www.cssdesignawards.com/) | Interaction design | Attention focus and transition pacing, adapted conservatively for mobile performance. |
| 59 | [Designspiration](https://www.designspiration.com/) | Visual discovery | Shape/color mood boards, converted into design tokens rather than copied palettes. |
| 60 | [Pinterest](https://www.pinterest.com/) | Visual collections | Exploratory collections; validate any reference using a real product or design-system source. |

## Part C — Platform standards, accessibility and UX principles

| # | Reference | Research entry | Distinct requirement applied to Chaty |
|---:|---|---|---|
| 61 | [Apple Human Interface Guidelines — Menus](https://developer.apple.com/design/human-interface-guidelines/menus) | Menus | Familiar commands, high-priority actions first, and short submenus; use toggles where a single stateful option is clearer. |
| 62 | [Apple Human Interface Guidelines — Accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility) | Accessibility | Support assistive technologies and platform-consistent interaction. |
| 63 | [Apple Human Interface Guidelines — Motion](https://developer.apple.com/design/human-interface-guidelines/motion) | Motion | Motion communicates cause/effect and must respect reduced-motion preferences. |
| 64 | [Android adaptive layout/navigation guidance](https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-and-nav-patterns) | Adaptive navigation | Compact widths use reachable bottom navigation; wider windows can move to a rail/drawer; do not stretch a phone layout across a tablet. |
| 65 | [Material Design 3](https://m3.material.io/) | Components and layout | Consistent component state, selected/unselected treatment, adaptive navigation and tokens. |
| 66 | [Flutter UI design and styling](https://docs.flutter.dev/ui/accessibility/ui-design-and-styling) | Flutter accessibility | System text scaling, contrast and touch-target checks. |
| 67 | [Flutter accessibility checklist](https://docs.flutter.dev/ui/accessibility) | Accessibility | Screen-reader semantics and testable accessibility acceptance criteria. |
| 68 | [Flutter performance](https://docs.flutter.dev/perf/best-practices) | Performance | Profile real frames; remove layout/build bottlenecks instead of adding more animation. |
| 69 | [Flutter DevTools Performance View](https://docs.flutter.dev/tools/devtools/performance) | Frame timing | Diagnose UI-thread and raster-thread stalls on the target device. |
| 70 | [W3C WCAG 2.2 — Target Size Minimum](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html) | Touch targets | Avoid tightly packed controls; preserve accessible minimum targets with exceptions checked deliberately. |
| 71 | [Nielsen Norman Group](https://www.nngroup.com/articles/ten-usability-heuristics/) | Usability heuristics | Visibility of system status, error prevention, consistency and user control. |
| 72 | [Laws of UX](https://lawsofux.com/) | UX laws | Use Hick-Hyman to group choices, Fitts to size/place targets, Jakob to preserve familiar behavior, Miller as a warning against overlong ungrouped lists, and Von Restorff selectively for one primary action. |
| 73 | [Baymard Institute](https://baymard.com/blog) | UX research | Reduce settings friction, keep feedback close to the action, and avoid unnecessary decisions. |
| 74 | [Smashing Magazine — UX](https://www.smashingmagazine.com/category/user-experience/) | UX research | Practical responsive, content-first and accessibility guidance. |
| 75 | [A List Apart](https://alistapart.com/) | Design engineering | Progressive enhancement and content-before-chrome. |
| 76 | [UX Matters](https://uxmatters.com/) | UX practice | Research methods, form design and decision quality. |

## Consolidated feature inventory — unique requirements, no duplicate work items

These are the distinct patterns selected for implementation and QA; repeated ideas from separate sites are intentionally collapsed into one requirement.

### Navigation and overflow
- On compact phones, an overflow action opens a safe-area-aware, scrollable bottom sheet with grouped actions and concise descriptions; on wider layouts, use an anchored popup menu.
- Every action has one stable key and one handler, so desktop popup and mobile sheet invoke the same route/action.
- Keep common commands near the top. Separate creation/device commands from appearance configuration and full settings.
- Theme presets, full templates, component overrides and navigation destinations are separate concepts; their labels must state which scope is changed.
- Stable destination IDs keep screens reachable even after reordering. Validate unique primary/More placement and retain all supported destinations.
- Use window constraints to switch bottom navigation, rail/drawer and content arrangement, not just to stretch the same component.

### Templates and settings
- Each template preview corresponds to the template that its Apply action selects.
- Per-component override changes only that component and survives scrolling/navigation; reset reverts that component to the active base.
- Global reset is explicitly confirmed; import is validated before applying; export contains visual configuration only.
- The current active state is shown with a selected border/status label and real field values, not empty placeholder chips.
- Settings are searchable, categorized, and use standard switches/selectors. No control is exposed unless it has a real state/persistence consumer or is clearly labeled as unavailable.
- Preview controls may stage changes; Cancel discards and Apply commits if the relevant screen uses staged selection.

### Messaging and communication
- Chat-list layout variants alter actual density, avatar silhouette, online-state visibility, divider and unread presentation.
- Conversation styles control bubble geometry, message ticks, wallpaper and group sender avatar visibility without changing stored messages.
- Composer variants include integrated actions, split actions and power-row layout; camera shortcut visibility, voice lock affordance and send/voice morph are independent.
- Chat and message 3-dot menus show context-appropriate actions and preserve current authorization checks.
- Media, voice notes, message edits and calls use existing services; errors must surface and failed optimistic actions roll back.
- MLS messages never fall back to plaintext table writes.

### Accessibility, resilience and performance
- Preserve safe-area insets, gesture/back behavior, text scaling and semantic labels.
- Prefer 48dp minimum touch targets on Android-compatible surfaces, strong contrast and visible focus.
- Every choice menu supports scrolling/keyboard traversal where applicable; do not place nested controls inside a menu row.
- Honor system reduced-motion settings. Avoid unbounded shimmer/particle loops in the messaging timeline.
- Target under 16 ms per frame at 60 Hz and under 8 ms at 120 Hz as measurement goals; 144 Hz is not promised without profile-mode evidence on supported hardware.
- Test low-end devices and worst-case lists, large accessibility text, split screen, rotate, reconnect, offline/loading/empty/error states and reduced motion.

## Chaty-specific decision log

1. **No dependency installation.** Use Flutter's existing navigation, popup/sheet, animation and accessibility primitives.
2. **No copied artwork or proprietary code.** Recreate structural ideas with Chaty's tokens, spacing, custom component behavior and original icon/illustration work.
3. **No change to security semantics.** RLS, MLS encryption, call authorization and local protected-storage behavior are release gates, not appearance options.
4. **No generic AI visual formula.** Avoid uniform card grids for every setting, arbitrary glow/blur, continuous idle motion and too many accent colors. Give pages a clear task hierarchy and content-appropriate density.
5. **Measure instead of promise.** Every responsiveness/performance claim must point to device coverage or a CI/profile result.

## Verified reference anchors

- 21st.dev explains why menu, select, combobox and navigation-panel patterns have different contracts: https://docs.21st.dev/blog/react-dropdown-menu-components
- Radix documents keyboard/focus/typeahead and collision-aware dropdown behavior: https://www.radix-ui.com/primitives/docs/components/dropdown-menu
- Apple says overflow menus should prioritize important/frequent actions and keep a consistent item treatment: https://developer.apple.com/design/human-interface-guidelines/menus
- Android recommends adaptive navigation instead of using the same bottom bar at all window sizes: https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-and-nav-patterns
- Flutter documents large text, contrast and minimum tap-target checks: https://docs.flutter.dev/ui/accessibility/ui-design-and-styling
- Flutter's performance docs explicitly recommend profiling actual frame timing, including an 8 ms target on 120 Hz devices: https://docs.flutter.dev/perf/best-practices and https://docs.flutter.dev/tools/devtools/performance
