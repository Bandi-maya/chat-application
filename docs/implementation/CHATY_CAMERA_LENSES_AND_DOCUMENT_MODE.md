# Chaty Camera Lenses and Document Mode — PRD

## Goal

Turn the existing chat camera into a polished capture flow with native-feeling photo/document modes, reusable image looks, and face-anchored AR lenses. Preserve existing chat/story routing, gallery import, flash, camera flip, captions, confirmation, message metadata, and story image baking.

## Research findings

- Snapchat's lens interaction is built around a centered, horizontally scrollable lens carousel and real face/world tracking. A face lens must be attached to landmarks/pose; a screen-fixed sticker is not face tracking.
- Flutter's existing `camera` plugin provides preview, photo capture, and camera image streams.
- The primary face engine is [`mediapipe_face_mesh` 3.2.1](https://pub.dev/packages/mediapipe_face_mesh/versions/3.2.1), an open-source Flutter implementation providing 478 face landmarks, iris points, and multi-face tracking on-device. Its source is BSD-3-Clause; bundled MediaPipe/TensorFlow Lite runtime/model components are separately Apache-2.0. The app calls it through a worker isolate and keeps camera pixels on-device.
- Apple Vision (`VNDetectFaceLandmarksRequest`) and Android's built-in `android.media.FaceDetector` remain fallbacks when the mesh pipeline cannot start. The Android fallback has materially fewer landmarks and is not Snapchat-grade tracking.
- GitHub candidate review: [`cornpip/mediapipe_face_mesh`](https://github.com/cornpip/mediapipe_face_mesh) is the selected, directly reusable Flutter runtime; [`vishnusajimon14-beep/realtime-face-filters`](https://github.com/vishnusajimon14-beep/realtime-face-filters) and [`charlielito/snapchat-filters-opencv`](https://github.com/charlielito/snapchat-filters-opencv) demonstrate landmark-to-accessory placement, but are Python/desktop examples rather than drop-in Flutter libraries. [`ridvanaltun/react-native-deepar`](https://github.com/ridvanaltun/react-native-deepar) wraps a proprietary/paid AR service, so it is not used. No proprietary Snapchat lenses or graphics are copied.
- Google's ML Kit Document Scanner offers edge detection, auto-capture, crop/rotation, cleanup, and PDF/JPEG output, but it is an external SDK. Document mode in this no-new-dependency pass provides a guided framing overlay and a baked high-contrast scan look; perspective correction and automatic edge detection are a separate opt-in dependency decision.

## UX direction

1. Keep the camera immersive and full-screen, dark, and high contrast.
2. Keep existing flash, close, gallery, shutter, and camera-flip actions.
3. Keep a centered, snap-to-selection lens carousel directly above capture controls. Selection, selected label, and rendered lens state must never disagree.
4. Keep color looks available as an expandable carousel without hiding the always-ready face-lens carousel in Photo mode.
5. Add a compact mode selector immediately above the shutter row: **Photo** and **Document**.
6. Photo mode supports face lenses and color looks. Document mode shows a restrained four-corner page guide, applies a high-contrast monochrome scan matrix to preview and captured pixels, and avoids face tracking while scanning.
7. Offer useful tracking feedback when a lens is selected but no face is detected. Never draw a pretend lens at the centre of the screen.
8. Retain layouts at small widths and large system text; carousel items size from the shortest screen edge and animations honor reduced-motion settings.
9. Keep detection local. Downsample the image stream to a small RGB frame before passing it to the platform detector; never upload frames.

## Implementation architecture

- Reuse `LensManager`, `LensCarousel`, `LensOverlay`, and `FaceAnchorSolver`.
- Add a Dart bridge that feeds BGRA/NV21 camera frames to the MediaPipe mesh in a worker isolate, mapping eye corners, nose, mouth, face bounds, and roll into `FaceAnchorSolver`.
- Use Apple Vision and Android's built-in frontal `FaceDetector` as fallback paths. The older Android fallback estimates some anchors from the face box, so those fallback lenses are less precise than the mesh path.
- Throttle frame handling and update the lens layer through a `ValueNotifier`; keep camera preview rebuilds separate from landmark updates.
- If the mesh runtime is unsupported or unavailable, ordinary camera, gallery, and photo/document capture remain available rather than failing the whole camera.

## Acceptance criteria

- Camera opens from existing chat-screen entry points with all prior sending and story behavior intact.
- Lens carousel changes the active lens immediately and renders its label/selection coherently.
- Face-anchored lenses follow eyes/head bounds and roll rather than remaining fixed to the viewfinder.
- Tracking loss fades/removes the lens gracefully; no face means no face-attached graphic.
- Face frames remain on-device and are only generated while a face lens is selected.
- Document mode gives a visible framing guide and applies the monochrome scan look to both preview and resulting file.
- One vetted open-source Flutter package (`mediapipe_face_mesh`) is added for face tracking; no paid AR SDK, additional icon library, or external lens artwork is added.
- Camera modes and carousels stay usable on narrow devices, and camera controls don't block the center of the page.
- Regression tests cover tracking reset/mirroring, mode selection UI, and existing camera/effect contracts.

## Deduplicated pattern inventory and applied UX laws

This is the unique set of camera patterns that fit the current Flutter app. Similar patterns from multiple component libraries are recorded once below; the 70-site index later in this document is a discovery catalogue, not a claim that every dynamic catalogue page was exhaustively crawled or that a web component can be dropped into a native Flutter camera.

| Unique pattern | Behavior / distinctive feature | Primary reference |
|---|---|---|
| Full-screen live viewfinder | Preview is the visual focus; camera actions sit over it rather than inside a dense settings panel | [Flutter camera recipe](https://docs.flutter.dev/cookbook/plugins/picture-using-camera) |
| Center-snapping live lens rail | The nearest lens settles exactly in the center; selected artwork, label, and applied effect stay synchronized | [Snapchat Lens Studio](https://support.lensstudio.snapchat.com/hc/en-us/articles/360051085391-Lens-Studio-Quick-Start), [Lens overview](https://support.lensstudio.snapchat.com/hc/en-us/articles/360031423371-What-is-a-Lens) |
| Face-anchored lens renderer | Attach glasses, ears, nose marks, sparkles, hearts, halo, and crown to face landmarks / pose rather than fixed screen coordinates | [Apple Vision face landmarks](https://developer.apple.com/documentation/vision/vndetectfacelandmarksrequest), [ML Kit face concepts](https://developers.google.com/ml-kit/vision/face-detection/face-detection-concepts) |
| Tracking feedback | Clearly distinguish “Find a face”, “Face locked”, and “Reacquiring” instead of silently showing an inaccurate sticker | [Nielsen Norman Group usability heuristics](https://www.nngroup.com/articles/ten-usability-heuristics/) |
| Color look picker | A secondary horizontal rail keeps the active image look visible and easy to compare without competing with the main face-lens rail | [Snapchat filter and lens help](https://help.snapchat.com/hc/en-us/sections/5686515003156-Filters-and-Lenses), [Mobbin](https://mobbin.com) |
| Photo / Document mode selector | Two explicit modes sit immediately above the shutter; active mode is high contrast and the layout remains narrow-screen safe | [Material Design](https://m3.material.io), [Apple Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines) |
| Document framing guide | Four corner markers establish the page boundary; a high-contrast monochrome treatment is applied to preview and saved output | [ML Kit Document Scanner capability reference](https://developers.google.com/ml-kit/vision/doc-scanner) |
| Capture review / recoverability | Keep the existing confirmation, caption, discard, send/story route, and gallery fallback; processing failure keeps the original photo | [Flutter camera recipe](https://docs.flutter.dev/cookbook/plugins/picture-using-camera) |
| Familiar utility controls | Close, flash, gallery, and camera flip stay in predictable corners and the shutter remains the strongest target | [Apple Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines), [Material Design](https://m3.material.io) |
| Accessible lens interaction | Every lens item exposes a semantic label, selected state, and button role; tap targets are not dependent on a decorative icon | [React Aria accessibility patterns](https://react-spectrum.adobe.com/react-aria), [Radix primitives](https://www.radix-ui.com/primitives) |
| Responsive thumb-zone hierarchy | Rails and swatches scale with the shortest screen dimension; tight layouts reduce spacing instead of scaling the entire screen down | [Flutter UI guidance](https://docs.flutter.dev/ui), [Material Design](https://m3.material.io) |
| Reduced-motion-aware transitions | Selected controls use short, eased transitions and honor system reduced-motion settings where supported | [Motion](https://motion.dev), [Animista](https://animista.net) |
| Lightweight frame delivery | Downsample to a small local thumbnail, throttle detection, and update the overlay separately from the viewfinder | [Flutter camera package](https://pub.dev/packages/camera), [Apple Vision](https://developer.apple.com/documentation/vision) |
| Native-first dependency policy | Study web component catalogs for layout and motion only; use a single open-source native-on-device mesh runtime rather than porting React components or integrating a paid AR SDK | [21st.dev](https://21st.dev), [React Bits](https://reactbits.dev), [Magic UI](https://magicui.design), [MediaPipe face mesh](https://github.com/cornpip/mediapipe_face_mesh) |

### UX laws / heuristics translated into decisions

- **Fitts’s Law:** the shutter is the largest, most central target; frequent gallery and flip actions remain separated and tappable.
- **Hick–Hyman Law:** expose only Photo and Document as top-level modes for now; put detailed color looks in a secondary rail rather than adding a crowded mode grid.
- **Jakob’s Law:** use familiar camera conventions for close, flash, flip, gallery, shutter, and horizontally browsed lenses.
- **Miller’s Law:** keep the high-level choice set small and group detailed looks into one scrollable collection.
- **Von Restorff Effect:** selected lens and active mode have a strong contrast boundary, scale, and clear label; decorative highlights are not used for every control.
- **Zeigarnik / Goal-Gradient effects:** the review stage preserves the captured image and clear next actions, while the document frame communicates the immediate goal. No fake percent progress is shown because this implementation has no asynchronous scanner pipeline.
- **Gestalt proximity / continuity:** lens label sits next to the rail, and mode choices are directly above the shutter they change.
- **Visibility of system status:** display tracking state, camera loading/errors, capture progress, and a recoverable message when effect baking fails.
- **Error prevention and recovery:** unsupported native detectors degrade to normal capture; a failed scan/lens composition keeps the original image instead of losing the capture.
- **Doherty Threshold / performance:** frame detection is throttled and runs natively off the UI thread; the camera preview is not rebuilt for every detection.
- **Peak–End Rule:** preserve a clear captured-image review state and immediate haptic feedback for selection rather than interrupting with unrelated dialogs.
- **WCAG / platform accessibility principles:** semantic labels and selected states are included for lens items, and animations can reduce under the system motion preference.

## References studied

- Snapchat Lens Studio: https://support.lensstudio.snapchat.com/hc/en-us/articles/360051085391-Lens-Studio-Quick-Start
- Snapchat Lenses overview: https://support.lensstudio.snapchat.com/hc/en-us/articles/360031423371-What-is-a-Lens
- Flutter camera recipe: https://docs.flutter.dev/cookbook/plugins/picture-using-camera
- Flutter camera package: https://pub.dev/packages/camera
- MediaPipe Face Mesh for Flutter (BSD-3-Clause source; bundled runtime/model notices included): https://pub.dev/packages/mediapipe_face_mesh/versions/3.2.1
- MediaPipe Flutter source repository: https://github.com/cornpip/mediapipe_face_mesh
- Desktop filter concept reference (not imported into the app): https://github.com/vishnusajimon14-beep/realtime-face-filters
- Desktop OpenCV example (not imported into the app): https://github.com/charlielito/snapchat-filters-opencv
- DeepAR React Native wrapper reviewed but not used due its proprietary service requirement: https://github.com/ridvanaltun/react-native-deepar
- Apple Vision face landmarks: https://developer.apple.com/documentation/vision/vndetectfacelandmarksrequest
- ML Kit face detection concepts: https://developers.google.com/ml-kit/vision/face-detection/face-detection-concepts
- ML Kit document scanner: https://developers.google.com/ml-kit/vision/doc-scanner
- Document scanner capabilities: https://developers.google.com/ml-kit/vision/doc-scanner/android
- Snapchat filter/lens support: https://help.snapchat.com/hc/en-us/sections/5686515003156-Filters-and-Lenses
- Mobile filter carousel reference search: https://mobbin.com
- App flows reference search: https://pageflows.com
- UI patterns reference search: https://refero.design

## Broader design-reference index (discovery targets, not copied implementations)

The following are useful catalogs for future review. Many focus on web components rather than native Flutter camera hardware, so they are used only for interaction ideas and do not justify importing web components into this app.

1. 21st.dev — https://21st.dev
2. React Bits — https://reactbits.dev
3. Magic UI — https://magicui.design
4. shadcn/ui — https://ui.shadcn.com
5. Ant Design — https://ant.design
6. MUI — https://mui.com
7. Origin UI — https://originui.com
8. COSS UI — https://coss.com
9. Skiper UI — https://skiper-ui.com
10. ThreeUI — https://threeui.com
11. Scrolltide — https://scrolltide.com
12. Animista — https://animista.net
13. Motion — https://motion.dev
14. Aceternity UI — https://ui.aceternity.com
15. Radix UI — https://www.radix-ui.com
16. Base UI — https://base-ui.com
17. Kibo UI — https://www.kibo-ui.com
18. Chakra UI — https://chakra-ui.com
19. Cult UI — https://cult-ui.com
20. React Aria — https://react-spectrum.adobe.com/react-aria
21. Headless UI — https://headlessui.com
22. Tailwind UI — https://tailwindui.com
23. Park UI — https://park-ui.com
24. Mantine — https://mantine.dev
25. Tremor — https://tremor.so
26. Motion Primitives — https://motion-primitives.com
27. OriginKit — https://originkit.com
28. SmoothUI — https://smoothui.dev
29. Mobbin — https://mobbin.com
30. SaaSFrame — https://saasframe.io
31. Curated — https://curated.design
32. Recent.design — https://recent.design
33. Awwwards — https://www.awwwards.com
34. Lapa Ninja — https://www.lapa.ninja
35. Land-book — https://land-book.com
36. Dribbble — https://dribbble.com
37. Behance — https://www.behance.net
38. Godly — https://godly.website
39. Page Flows — https://pageflows.com
40. Refero — https://refero.design
41. Screenlane — https://screenlane.com
42. UI Garage — https://uigarage.net
43. Collect UI — https://collectui.com
44. Really Good UX — https://www.reallygoodux.io
45. Growth.Design — https://growth.design
46. Pttrns — https://pttrns.com
47. One Page Love — https://onepagelove.com
48. Muzli — https://muz.li
49. UX Archive — https://uxarchive.com
50. Page Collective — https://pagecollective.com
51. UI Sources — https://uisources.com
52. Nicelydone — https://nicelydone.club
53. ScreenSpace — https://screenspace.design
54. UX Planet — https://uxplanet.org
55. Smashing Magazine — https://www.smashingmagazine.com
56. A List Apart — https://alistapart.com
57. Nielsen Norman Group — https://www.nngroup.com
58. Laws of UX — https://lawsofux.com
59. Material Design — https://m3.material.io
60. Apple Human Interface Guidelines — https://developer.apple.com/design/human-interface-guidelines
61. Flutter Design — https://docs.flutter.dev/ui
62. Figma Community — https://www.figma.com/community
63. CodePen — https://codepen.io
64. GitHub Trending — https://github.com/trending
65. Awesome Flutter — https://github.com/Solido/awesome-flutter
66. Flutter Gems — https://fluttergems.dev
67. Flutter Awesome — https://flutterawesome.com
68. Flutter UI Challenges — https://github.com/lohanidamodar/flutter_ui_challenges
69. Dribbble Mobile Apps — https://dribbble.com/tags/mobile-app
70. Behance Mobile UI — https://www.behance.net/search/projects/mobile%20app%20ui
