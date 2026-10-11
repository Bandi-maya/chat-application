# MASTER COMPONENT REFERENCE CATALOG & DESIGN SYSTEM BENCHMARK

## Executive Summary
This catalog documents 28 foundational component libraries and implementation references alongside 50 verified authoritative design systems and interaction sources, establishing concrete architectural patterns for Chaty's Flutter implementation.

---

## 1. Required Component Libraries & Implementation References (28 Sources)

| ID | Library / System | URL | Core Capabilities & Patterns | Chaty Flutter Equivalent / Target | Decision |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **CR-01** | 21st.dev | https://21st.dev/ | Micro-components, polished card shaders, badge aesthetics | `lib/ui/core/design_system/components/` | Adapt principles |
| **CR-02** | React Bits | https://reactbits.dev/ | Animated text reveals, magnetic buttons, subtle background gradients | `lib/ui/core/widgets/` & button tactile physics | Adapt principles |
| **CR-03** | Magic UI | https://magicui.design/ | Animated beam, shimmer badges, dock interactions | Animated dock navigation, active call indicator | Adapt principles |
| **CR-04** | shadcn/ui | https://ui.shadcn.com/ | Composable atomic primitives, minimalist tokens, high keyboard a11y | `lib/ui/core/design_system/tokens/` | Adopt token hierarchy |
| **CR-05** | Ant Design | https://ant.design/ | Enterprise data density, table grids, badge counters | Chat list badges, settings detail views | Adapt data layout |
| **CR-06** | Material UI | https://mui.com/ | Material 3 component specifications, ripple physics, elevation tokens | Core Flutter Material 3 baseline integration | Adopt system tokens |
| **CR-07** | Origin UI | https://originui.com/ | Nuanced input states, multi-step forms, verification code inputs | OTP input, phone number verification screens | Adopt input patterns |
| **CR-08** | COSS UI | https://coss.com/ | High contrast dark mode widgets, compact action menus | Action sheet menus, context popups | Adapt aesthetics |
| **CR-09** | Skiper UI | https://skiper-ui.com/ | Kinetic text inputs, interactive sliders | Custom audio player progress slider, media scrubbing | Adapt interaction |
| **CR-10** | ThreeUI | https://threeui.com/ | 3D depth layering, perspective transforms | Subtle card tilt on press in media previews | Adapt restrained 2D tilt |
| **CR-11** | Scrolltide | https://scrolltide.com/ | Scroll-linked header collapses, sticky section delimiters | Chat conversation date sticky headers, contact indexer | Adopt sticky headers |
| **CR-12** | Animista | https://animista.net/ | CSS easing timing curves, entrance/exit keyframes | Flutter `CurvedAnimation` timing definitions | Adopt bezier curves |
| **CR-13** | Motion | https://motion.dev/ | Physics-based gestures, drag dismiss, layout morphing | Chat message swipe-to-reply, dismissible overlays | Adopt gesture models |
| **CR-14** | Aceternity UI | https://ui.aceternity.com/ | Spotlight cards, glowing border shaders, tracing beams | Status update ring animations, encrypted badge shimmer | Adapt restrained borders |
| **CR-15** | Radix UI | https://www.radix-ui.com/ | Accessible primitives, focus traps, screen-reader announcements | Custom bottom sheet and dialog semantics | Adopt a11y patterns |
| **CR-16** | Base UI | https://base-ui.com/ | Unstyled robust form primitives, popover positioning | Custom contextual popovers and message reaction bars | Adopt positioning |
| **CR-17** | Kibo UI | https://kibo-ui.com/ | Clean iconography, structured badge elements, status chips | User online status indicator, encryption tags | Adopt status badges |
| **CR-18** | Chakra UI | https://chakra-ui.com/ | Modular spacing scale (4dp grid), semantic color mapping | `lib/ui/core/design_system/tokens/` spacing scale | Adopt spacing tokens |
| **CR-19** | Cult UI | https://cult-ui.com/ | Experimental tactile interactions, spring physics cards | Reaction picker popovers, haptic gesture tiles | Adapt spring dynamics |
| **CR-20** | React Aria | https://react-spectrum.adobe.com/react-aria/ | Bulletproof accessibility, keyboard navigation, focus management | Accessible form fields and talkback semantic labels | Adopt WCAG guidelines |
| **CR-21** | Headless UI | https://headlessui.com/ | Transition lifecycles, modal backdrop layering | Modal backdrop transition controllers | Adopt lifecycle states |
| **CR-22** | Tailwind UI | https://tailwindcss.com/plus | Production application layouts, grouped list settings | Settings screen list rows, toggle item primitives | Adopt grouped rows |
| **CR-23** | Park UI | https://park-ui.com/ | Multi-theme support, token sync, panda CSS foundation | Dark/Light/OLED theme token orchestration | Adopt theme switching |
| **CR-24** | Mantine | https://mantine.dev/ | Rich notification toast systems, alert banners | `lib/ui/core/widgets/event_toast_overlay.dart` | Adopt toast stacking |
| **CR-25** | Tremor | https://www.tremor.so/ | Clean metric cards, storage and bandwidth visualizers | Settings > Storage & Data usage breakdown bars | Adopt metric graphs |
| **CR-26** | Motion Primitives | https://motion-primitives.com/ | Morphing dialogs, expandable cards, floating dynamic bars | Dynamic Island pill expansion and collapse | Adopt pill morphing |
| **CR-27** | SmoothUI | https://smoothui.dev/ | Micro-interaction button states, bouncy toggles | Custom switches and radio buttons | Adopt micro-motion |
| **CR-28** | OriginKit | https://originkit.com/ | Cohesive application shells, mobile bottom navigation bars | App shell bottom navigation bar & badge sync | Adopt shell structure |

---

## 2. Additional 50 Authoritative Resources Inspected & Mapped

### Design Systems & Enterprise Foundations (15 Sources)
1. **Material Design 3 (Google)** — https://m3.material.io/ (Token roles, dynamic color tinting, shape scales)
2. **Apple Human Interface Guidelines** — https://developer.apple.com/design/ (Spatial depth, typography, haptics)
3. **IBM Carbon Design System** — https://carbondesignsystem.com/ (Strict accessibility, spacing tokens, enterprise grid)
4. **Shopify Polaris** — https://polaris.shopify.com/ (Content clarity, action feedback, accessible tables)
5. **Atlassian Design System** — https://atlassian.design/ (Modal dialog conventions, confirmation workflows)
6. **GitHub Primer** — https://primer.style/ (High contrast mode, color schemes, responsive navigation)
7. **Microsoft Fluent 2** — https://fluent2.microsoft.design/ (Lightweight shadows, acrylic materials, cohesive iconography)
8. **Adobe Spectrum** — https://spectrum.adobe.com/ (Scale-independent touch targets, color contrast verification)
9. **Salesforce Lightning** — https://www.lightningdesignsystem.com/ (State representation, badge hierarchies)
10. **SAP Fiori** — https://experience.sap.com/fiori-design/ (Dense data presentation, mobile master-detail views)
11. **US Web Design System (USWDS)** — https://designsystem.digital.gov/ (Government-grade WCAG 2.2 AAA accessibility)
12. **GOV.UK Design System** — https://design-system.service.gov.uk/ (Radical simplicity, error prevention patterns)
13. **Zendesk Garden** — https://garden.zendesk.com/ (Customer communication UI, ticket lists, message bubbles)
14. **Elastic UI** — https://elastic.github.io/eui/ (Search bar indexing, filter chips, tag systems)
15. **PatternFly** — https://www.patternfly.org/ (Drawer navigation, sticky filter bars, empty state layouts)

### Interaction, Animation & Physics Engines (15 Sources)
16. **Emil Kowalski Animation Design** — https://emilkowal.ski/ (Spring physics, tactile feedback, micro-timing)
17. **Josh W. Comeau Interactive Tutorials** — https://www.joshwcomeau.com/ (Delightful micro-interactions, sound/haptic pairing)
18. **Rive Animation Engine** — https://rive.app/ (State-machine driven interactive vectors)
19. **LottieFiles** — https://lottiefiles.com/ (Micro-vector animation benchmarks for success/failure states)
20. **GSAP (GreenSock)** — https://greensock.com/ (Staggered list animations, timeline sequence chaining)
21. **Cubic-Bezier Easing Resource** — https://cubic-bezier.com/ (Exact timing curve definitions: `cubic-bezier(0.16, 1, 0.3, 1)`)
22. **Val Head Motion Design** — https://valhead.com/ (Functional animation, cognitive orientation in navigation)
23. **W3C Web Animations API Specification** — https://www.w3.org/TR/web-animations-1/ (Frame synchronization standards)
24. **Chrome Developers Rendering Performance** — https://developer.chrome.com/docs/performance/ (60fps/120fps jank elimination)
25. **Framer Motion Patterns** — https://www.framer.com/motion/ (Layout animations, shared-element transitions)
26. **Codrops Interaction Experiments** — https://tympanus.net/codrops/ (Creative navigation transitions and contextual sheets)
27. **CSS-Tricks Animation Architecture** — https://css-tricks.com/ (CSS transforms vs layout invalidation benchmarks)
28. **Frontend Horse Community** — https://frontend.horse/ (Creative coding micro-interactions)
29. **Smashing Magazine UX & Animation** — https://www.smashingmagazine.com/ (Accessible animations with `prefers-reduced-motion`)
30. **Jake Archibald Web Engine Notes** — https://jakearchibald.com/ (Event loops, smooth scrolling vs input latency)

### Usability, Cognitive Science & Ergonomics (10 Sources)
31. **Nielsen Norman Group (NN/g)** — https://www.nngroup.com/ (Mobile usability heuristics, touch target ergonomics)
32. **Laws of UX (Jon Yablonski)** — https://lawsofux.com/ (Fitts's Law, Hick-Hyman, Doherty Threshold, Miller's Law)
33. **W3C WCAG 2.2 Guidelines** — https://www.w3.org/TR/WCAG22/ (Target size minimum, contrast ratio 4.5:1, focus appearance)
34. **WebAIM (Web Accessibility in Mind)** — https://webaim.org/ (Color contrast analysis, screen reader hierarchy)
35. **The A11Y Project** — https://www.a11yproject.com/ (Checklist for mobile screen readers, dynamic scaling)
36. **Baymard Institute Mobile Checkout & Form UX** — https://baymard.com/ (Zero-friction input formatting, inline error feedback)
37. **UX Movement Mobile Touch Targets** — https://uxmovement.com/ (Thumb-zone ergonomic mapping on modern smartphones)
38. **CXL Usability & Behavior Research** — https://cxl.com/ (Cognitive load reduction during high-stress interactions)
39. **Don Norman: The Design of Everyday Things** — https://mitpress.mit.edu/ (Affordances, signifiers, natural mappings)
40. **Interaction Design Foundation** — https://www.interaction-design.org/ (Direct manipulation and human-computer symbiosis)

### Mobile Architecture, Android Quality & Profiling (10 Sources)
41. **Android Developers: Core App Quality** — https://developer.android.com/quality (Android 14/15 modern quality guidelines)
42. **Android Window Insets & Edge-to-Edge** — https://developer.android.com/develop/ui/views/layout/edge-to-edge (Edge-to-edge rendering)
43. **Android Macrobenchmark & Baseline Profiles** — https://developer.android.com/topic/performance/baselineprofiles (Startup latency)
44. **Perfetto Tracing Platform** — https://perfetto.dev/ (System-wide trace analysis, frame drop debugging)
45. **Flutter Engine Performance Guide** — https://docs.flutter.dev/perf (Impeller rendering backend, avoiding shader compilation jank)
46. **Android Accessibility Services Documentation** — https://developer.android.com/guide/topics/ui/accessibility (TalkBack testing)
47. **Android Foreground Tasks & Battery Optimization** — https://developer.android.com/guide/background/persistent (Doze mode compliance)
48. **Firebase Performance Monitoring** — https://firebase.google.com/docs/perf-mon (Real-user monitoring latency distributions)
49. **Android Security & Cryptography Guidelines** — https://developer.android.com/privacy-and-security (Keystore, secure shared storage)
50. **Mobbin Mobile UX Patterns** — https://mobbin.com/ (Production mobile chat, calling, and settings interaction flows)
