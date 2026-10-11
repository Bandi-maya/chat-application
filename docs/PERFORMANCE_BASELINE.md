# PERFORMANCE BASELINE & 120/144 HZ RENDERING SPECIFICATION

## 1. Frame Budget & Target Refresh Rates

| Refresh Rate | Target Frame Budget | Janky Threshold | Measurement Tool |
| :--- | :--- | :--- | :--- |
| **60 Hz** | 16.6 ms per frame | $> 16.6\text{ ms}$ | Flutter DevTools Performance |
| **90 Hz** | 11.1 ms per frame | $> 11.1\text{ ms}$ | Android GPU Profiler |
| **120 Hz** | 8.33 ms per frame | $> 8.33\text{ ms}$ | Perfetto Systrace |
| **144 Hz** | 6.94 ms per frame | $> 6.94\text{ ms}$ | Android Studio Profiler / FrameStats |

---

## 2. Rendering Optimization Rules for Flutter Engine (Impeller)

### 2.1 Minimize Rebuild Scopes
* Extract static widgets and wrap with `const` constructors wherever possible.
* Use localized `ValueListenableBuilder` or scoped listeners instead of calling `setState()` at the parent screen level during typing or animation ticks.

### 2.2 Shader & Clip Optimization
* Restrict `BackdropFilter` and blur shaders to bounded `ClipRRect` regions with explicit dimensions.
* Avoid full-screen blur layers running behind actively scrolling `ListView.builder` timelines.
* Cache complex custom painter paths (`AppIcons` vector painter) to avoid re-generating Path objects on every frame.

### 2.3 List View Virtualization & Image Memory
* Chat timeline messages must use `ListView.builder` with automatic item recycling (`addAutomaticKeepAlives: false` for off-screen non-active items).
* Pre-decode and downsample avatar thumbnails using `ResizeImage` or appropriate cached dimensions to prevent memory spikes on high-resolution displays.
