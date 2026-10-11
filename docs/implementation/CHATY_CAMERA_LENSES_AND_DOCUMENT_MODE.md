# Chaty Camera Lenses and Document Mode — PRD

## Goal

Turn the existing chat camera into a polished capture flow with native-feeling photo/document modes, reusable image looks, and face-anchored AR lenses. Preserve existing chat/story routing, gallery import, flash, camera flip, captions, confirmation, message metadata, and story image baking.

## Research findings

- Snapchat's lens interaction is built around a centered, horizontally scrollable lens carousel and real face/world tracking. A face lens must be attached to landmarks/pose; a screen-fixed sticker is not face tracking.
- Flutter's existing `camera` plugin exposes a preview, photo capture, and image streams. This project already depends on it, so the feature does not need another Dart camera package.
- Apple Vision's `VNDetectFaceLandmarksRequest` can identify face regions and eye/mouth landmarks on-device.
- Google's ML Kit documents real-time face tracking, landmarks, pose, and multi-frame tracking. However, adding ML Kit as a dependency would violate this project brief's no-new-external-library constraint. This implementation therefore uses built-in platform APIs and the existing pure-Dart `FaceAnchorSolver`.
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
- Add a thin Dart bridge that downsamples/rotates the camera image to a tiny RGB thumbnail and calls a native method channel only while a face lens is active.
- Android: use the built-in `android.media.FaceDetector` as a dependency-free fallback. It is a legacy frontal-face detector and does not provide the contour/expression quality of ML Kit; landmarks other than eyes are estimated from the detected face box.
- iOS: use the built-in Vision face-landmark request for eyes, nose, mouth, face box, and pose.
- Throttle face detections and update only the lens overlay via a `ValueNotifier`; do not rebuild the camera viewfinder for every detection.
- When no native detector exists (desktop/test builds), normal photo capture and gallery fallback continue working.

## Acceptance criteria

- Camera opens from existing chat-screen entry points with all prior sending and story behavior intact.
- Lens carousel changes the active lens immediately and renders its label/selection coherently.
- Face-anchored lenses follow eyes/head bounds and roll rather than remaining fixed to the viewfinder.
- Tracking loss fades/removes the lens gracefully; no face means no face-attached graphic.
- Face frames remain on-device and are only generated while a face lens is selected.
- Document mode gives a visible framing guide and applies the monochrome scan look to both preview and resulting file.
- No new Flutter packages or external assets are added.
- Camera modes and carousels stay usable on narrow devices, and camera controls don't block the center of the page.
- Regression tests cover tracking reset/mirroring, mode selection UI, and existing camera/effect contracts.

## References studied

- Snapchat Lens Studio: https://support.lensstudio.snapchat.com/hc/en-us/articles/360051085391-Lens-Studio-Quick-Start
- Snapchat Lenses overview: https://support.lensstudio.snapchat.com/hc/en-us/articles/360031423371-What-is-a-Lens
- Flutter camera recipe: https://docs.flutter.dev/cookbook/plugins/picture-using-camera
- Flutter camera package: https://pub.dev/packages/camera
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
