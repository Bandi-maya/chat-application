# SOURCE REGISTRY & EVIDENCE AUDIT

## Executive Summary
This registry tracks all primary, secondary, and platform sources researched during the intelligence phase, noting validation date, accessibility, verified status, and relevance to the Flutter/Android codebase.

---

## 1. Primary Platform Authorities

| Source ID | Organization / Author | Topic / Destination | URL | Verified Status |
| :--- | :--- | :--- | :--- | :--- |
| **SR-01** | Apple Inc. | Human Interface Guidelines | https://developer.apple.com/design/human-interface-guidelines/ | Confirmed Active |
| **SR-02** | Apple Inc. | Live Activities & Dynamic Island | https://developer.apple.com/design/human-interface-guidelines/live-activities | Confirmed Active |
| **SR-03** | Apple Inc. | Dynamic Type & Typography | https://developer.apple.com/design/human-interface-guidelines/typography | Confirmed Active |
| **SR-04** | Google LLC | Material Design 3 Specification | https://m3.material.io/ | Confirmed Active |
| **SR-05** | Google LLC / Android | Android Edge-to-Edge Windows | https://developer.android.com/develop/ui/views/layout/edge-to-edge | Confirmed Active |
| **SR-06** | Google LLC / Android | Android App Quality Core Criteria | https://developer.android.com/quality | Confirmed Active |
| **SR-07** | Flutter Team | Flutter Performance & Impeller | https://docs.flutter.dev/perf | Confirmed Active |
| **SR-08** | W3C | Web Content Accessibility Guidelines 2.2 | https://www.w3.org/TR/WCAG22/ | Confirmed Active |
| **SR-09** | Nielsen Norman Group | Mobile Usability Research | https://www.nngroup.com/ | Confirmed Active |
| **SR-10** | Jon Yablonski | Laws of UX | https://lawsofux.com/ | Confirmed Active |

---

## 2. Component System Benchmark Sources (28 Required + Sample Verification)

| Source ID | Name | Verified URL | Primary Extractable Value |
| :--- | :--- | :--- | :--- |
| **SR-11** | 21st.dev | https://21st.dev/ | Micro-interaction components and layout hierarchy |
| **SR-12** | React Bits | https://reactbits.dev/ | Physics-based magnetic hover/touch reactions |
| **SR-13** | Magic UI | https://magicui.design/ | Animated gradient borders, beam effects |
| **SR-14** | shadcn/ui | https://ui.shadcn.com/ | Clean design tokens, unopinionated composability |
| **SR-15** | Ant Design | https://ant.design/ | Structured information density and badges |
| **SR-16** | Material UI | https://mui.com/ | Elevation levels and color system mapping |
| **SR-17** | Origin UI | https://originui.com/ | Form validation and multi-digit authentication inputs |
| **SR-18** | COSS UI | https://coss.com/ | Sleek dark mode visual balance |
| **SR-19** | Skiper UI | https://skiper-ui.com/ | Scrubbing sliders and tactile dials |
| **SR-20** | ThreeUI | https://threeui.com/ | Layered perspective depth cards |
| **SR-21** | Scrolltide | https://scrolltide.com/ | Sticky section headers and list scroll effects |
| **SR-22** | Animista | https://animista.net/ | Easing curves and motion timing |
| **SR-23** | Motion | https://motion.dev/ | Gesture dragging and velocity-based release |
| **SR-24** | Aceternity UI | https://ui.aceternity.com/ | Spotlight highlighting and subtle borders |
| **SR-25** | Radix UI | https://www.radix-ui.com/ | Dialog, sheet, and popover focus and semantic rules |
| **SR-26** | Base UI | https://base-ui.com/ | Unstyled foundational inputs and positioning |
| **SR-27** | Kibo UI | https://kibo-ui.com/ | Vector badges and status markers |
| **SR-28** | Chakra UI | https://chakra-ui.com/ | Modular 4dp tokenized spacing system |
| **SR-29** | Cult UI | https://cult-ui.com/ | Spring physics interactive elements |
| **SR-30** | React Aria | https://react-spectrum.adobe.com/react-aria/ | Screen reader accessibility and announcement models |
| **SR-31** | Headless UI | https://headlessui.com/ | Transition lifecycles and backdrop management |
| **SR-32** | Tailwind UI | https://tailwindcss.com/plus | Mobile list layouts and grouped setting rows |
| **SR-33** | Park UI | https://park-ui.com/ | Theming architecture and token persistence |
| **SR-34** | Mantine | https://mantine.dev/ | Toast notifications and transient alerts |
| **SR-35** | Tremor | https://www.tremor.so/ | Clean metrics visualizers and progress gauges |
| **SR-36** | Motion Primitives | https://motion-primitives.com/ | Expandable pill shapes and floating overlays |
| **SR-37** | SmoothUI | https://smoothui.dev/ | Micro-switch toggles and fluid state changes |
| **SR-38** | OriginKit | https://originkit.com/ | Cohesive application navigation shells |

---

## 3. Evidence Quality & Feasibility Assessment
* **Zero Dependency Installation Constraint**: All adopted patterns are translated directly into pure Flutter Dart code (`CustomPainter`, `AnimationController`, `CurvedAnimation`, `Transform`, `BackdropFilter`, and standard Flutter SDK primitives) and Kotlin Android platform channels.
* **No external third-party UI/icon/animation packages** are added to `pubspec.yaml`, ensuring 100% adherence to repository safety and performance limits.
