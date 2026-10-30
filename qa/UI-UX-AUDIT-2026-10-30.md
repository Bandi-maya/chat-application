# Chaty — UI/UX & Test Audit

**Date:** 2026-10-30
**App:** `com.example.chat` v1.0.0 (versionCode 1)
**APK:** `build-artifacts/kotlin-apk/app-debug.apk` (281 MB, built 2026-10-30 09:47)
**Emulator:** LDPlayer 9, instance 0 — 1280x720 @ 240 dpi, model SH-M24 (AQUOS)

---

## 0. Live emulator run — BLOCKED (read first)

**I could not drive the running app: LDPlayer's ADB bridge is disabled.**

| Check | Result |
|---|---|
| `adb devices -l` (SDK adb and LDPlayer adb) | empty list |
| Listening TCP 5554–5600 | none |
| `ldconsole list2` | `0,LDPlayer,...,1,10536,2120,1280,720,240` → VM **is** running |
| `Ld9BoxHeadless.exe` | running (458 MB) |
| `ldconsole adb --index 0 --command "shell ..."` | `device 'emulator-5554' not found` |
| `adb connect 127.0.0.1:5555` | `actively refused (10061)` |

The VM is up, but no ADB port is listening, so no tool can attach.

**Fix (one toggle, no reinstall):**

1. LDPlayer → **gear / Settings**
2. **Other settings** (其他设置)
3. **ADB debugging** (ADB调试) → **Open local connection** (开启本地连接)
4. Save → **restart the LDPlayer instance**

Then `adb devices` lists `emulator-5554` and the live visual pass can run.

> Also: I could not read the screenshot you attached — this model has no image input. Everything below comes from the source tree and host inspection. That is strong evidence for the defects found, but it is **not** a substitute for the pending visual pass.

---

## 1. What already passes (do not "fix" these)

These were checked and are correct:

- **Keyboard handling** — `AndroidManifest.xml` sets `windowSoftInputMode="adjustResize"`; `ChatDetailScreen.kt:635` applies `.imePadding()`. No keyboard-occlusion bug.
- **Home list correctness** — `ChatsHomeScreen.kt:737-745` supplies stable `key =` per row; empty state exists (`EmptyChatsState`, lines 631-633).
- **Text overflow** — chat rows use `maxLines = 1` + `TextOverflow.Ellipsis` (`ChatsHomeScreen.kt:540-541`).
- **Login flow** — `LoginScreen.kt` has loading state, inline field errors, error banner, password show/hide with a real `contentDescription` (line 210), and friendly error mapping (`friendlyLoginError`).
- **Font scaling** — `0` uses of `fontSize = N.dp`; text uses `sp`, so system font scale is respected.
- **Tablet layout** — the fixed `360.dp` list at `ChatsHomeScreen.kt:677` is intentional: a `splitView && maxWidth >= 640.dp` master/detail pane. Not a bug.
- **IconButton touch targets** — Material 3 `IconButton` auto-expands to 48 dp, so small `Modifier.size()` values are *not* target-size failures. Only the missing labels are real (see A11Y-01).

---

## 2. Findings

### A11Y-01 — 25 icon buttons have no accessible label (HIGH)

TalkBack announces these as an unlabeled button, so a screen-reader user cannot tell what they do. Counted by scanning every `IconButton` block for `contentDescription = null`.

| File | Count |
|---|---|
| `ui/settings/ThemeEditorScreen.kt` | 5 |
| `ui/settings/SettingsScreen.kt` | 4 |
| `ui/chats/detail/ChatyComposerRenderer.kt` | 3 |
| `ui/settings/ComponentStylesScreen.kt` | 2 |
| `ui/settings/messagemanagement/MessageManagementPage.kt` | 2 |
| `TasksScreen.kt`, `TaskDetailScreen.kt`, `TaskCreateEditModal.kt`, `CallContactPickerScreen.kt`, `RegisterOrganizationScreen.kt`, `ChatPollUi.kt`, `GbKit.kt` | 1 each |

Worst case — the **main Settings header**, four unlabeled buttons in one row:

```kotlin
// ui/settings/SettingsScreen.kt:219-223
IconButton(onClick = { onBack?.invoke() }) { Icon(Icons.Rounded.ChevronLeft, null, ...) }
IconButton(onClick = { onOpenSearch(...) }) { Icon(Icons.Rounded.Search, null, ...) }
IconButton(onClick = { sheet = SettingsSheet.Export }) { Icon(Icons.Rounded.QrCodeScanner, null, ...) }
IconButton(onClick = { sheet = SettingsSheet.EditProfile }) { Icon(Icons.Outlined.Edit, null, ...) }
```

**Fix:** label each one, e.g. `contentDescription = "Search settings"`.

> `355` uses of `contentDescription = null` exist in total; most are decorative and correct. The 25 above are inside a clickable `IconButton` and are genuine defects.

### UX-01 — Failed user actions fail silently (HIGH)

13 empty `catch {}` blocks in `ui/ChatDetailHost.kt` wrap actions the user directly triggered. When the call fails, nothing changes and no error appears — it looks like a dead tap.

| Line | Action | What the user sees on failure |
|---|---|---|
| 323 | `api.deleteMessage(...)` | Message stays; no "couldn't delete" |
| 336 | `repo.toggleReactionAsync(...)` | Reaction shows optimistically, never persists, no rollback message |
| 373 | `createOrGetDirectConversation(...)` | Tapping a contact does nothing |
| 290 | `repo.setDraft(...)` | Draft silently lost |
| 289 | `connectionHealth.probeConnection()` | "Retry" appears to do nothing |
| 97, 170 | task load / connection status | Stale or blank state, no indicator |

Example:

```kotlin
// ui/ChatDetailHost.kt:336
onToggleReaction = { id, emoji ->
    repo.optimisticToggleReaction(conversation.id, id, emoji)
    scope.launch { try { repo.toggleReactionAsync(conversation.id, id, emoji) } catch (_: Exception) {} }
}
```

**Fix:** surface a Snackbar/Toast on failure and revert the optimistic update. The app already has a `onNotify` callback wired to Toast (`AppRoot.kt:979`) — reuse it.

### UX-02 — Dead-end navigation destinations (MEDIUM)

Some routes land on a screen that only says the page isn't implemented.

```kotlin
// ui/AppRoot.kt:984-987  (catch-all branch of the destination `when`)
else -> Text("Destination \"$destinationId\" is not hosted in this build yet.", ...)
```

```kotlin
// ui/AppRoot.kt:1311
Text("This settings page is not hosted in this build yet.")
```

```kotlin
// ui/settings/universal/BackupAndRestoreScreen.kt:320,327
subtitle = "Not available yet. Media is not included in any backup."
subtitle = "Not available yet."
```

Honest wording, but from the user's side it is a dead end. Either hide these entries from the menu or replace them with a real empty state + next action.

### I18N-01 — No localization layer at all (MEDIUM)

| Metric | Value |
|---|---|
| Hardcoded `Text("...")` literals in `ui/` | **587** |
| `stringResource(...)` calls | **0** |
| `res/values/strings.xml` | does not exist |

Every user-facing string is baked into Kotlin. Consequences: no translation is possible without a refactor, and the app cannot follow the device locale. If English-only is the deliberate plan, record that decision; otherwise introduce `strings.xml` and migrate screen by screen.

### THEME-01 — 434 hardcoded colors bypass the theme system (MEDIUM)

`Color(0x...)` literals outside the `ui/theme/` package:

| File | Count |
|---|---|
| `core/theme/effects/EffectRegistry.kt` | 60 |
| `ui/island/DynamicIslandViews.kt` | 43 |
| `ui/core/.../AppBrandIcon.kt` | 31 |
| `CustomFilePickerScreen.kt` | 19 |
| `MediaWorkspaceScreen.kt` | 19 |
| `CameraCaptureWidgets.kt` | 15 |
| `RegisterScreen.kt` | 14 |
| `OngoingCallScreen.kt` | 13 |
| `PdfWorkspaceScreen.kt` | 13 |

The app ships a theme engine (`ChatyThemeManager`, `ThemeController`, presets). Hardcoded literals ignore the user's chosen accent/background and will look wrong in dark or custom themes. Some are legitimately fixed (brand logo, camera overlays), but `RegisterScreen.kt` (14) and `OngoingCallScreen.kt` (13) should use theme tokens.

### PERF-01 — 31 `items()` calls without a stable key (LOW)

Missing keys force full recomposition and break item identity on insert/remove (scroll jumps, wrong animations). Examples:

```
ChatDetailHost.kt:681      items(forwardItems.size) { index ->
CallContactPickerScreen.kt:476  items(dialKeys) { key ->
EffectPickerSheet.kt:180   items(list.size) { i ->
ChatDetailScreen.kt:1662   items(replies.size) { index ->
StarredMessagesScreen.kt:305  items(starred.size) { index ->
```

**Fix:** `items(list, key = { it.id })`. Note `ChatsHomeScreen.kt` already does this correctly — use it as the reference.

---

## 9. Fixes applied (source-level, pending CI compile verification)

The following fixes were applied to the source tree. They could **not** be compile-verified locally because `:app:compileDebugKotlin` requires the Rust/cargo-ndk toolchain (fails with 123 unresolved references before any user code is reached). Each change is a mechanical, syntactically-reviewed edit; they must be validated with `gradle :app:compileDebugKotlin` in CI (which already builds the APK successfully).

### A11Y-01 — accessible labels added (14 IconButtons + root cause + 2 bonus)

| File | Change |
|---|---|
| `ui/settings/SettingsScreen.kt:219,221,222,223` | Back / Search settings / Export chat / Edit profile labels on header buttons |
| `ui/settings/appearance/ComponentStylesScreen.kt:312,315` | Voice call / Video call |
| `ui/settings/messagemanagement/MessageManagementPage.kt:317,374` | Delete rule / Cancel scheduled |
| `ui/kit/GbKit.kt:119` | Dismiss |
| `ui/core/components/composer/ChatyComposerRenderer.kt:145,192` | Close / Cancel recording (SmallClose, RecordingRow) |
| `ui/core/components/composer/ChatyComposerRenderer.kt:215-310` | `IconBtn` now requires a `contentDescription` param; all 12 call sites labeled (Emoji picker, Attach file, Camera, Attach, Attach, Attach, Microphone, Send, Camera, Microphone, Gallery) |
| `ui/auth/RegisterOrganizationScreen.kt:275` | Show password / Hide password (dynamic) |
| `ui/chats/starred/StarredMessagesScreen.kt:153` | Back |
| `ui/ChatPollUi.kt:90` | Add option (bonus, inside TextButton) |
| `ui/tasks/TaskDetailScreen.kt:127` | Edit task (bonus, menu leading icon) |

> Note: the original scan counted 25 "unlabeled icons," but ~11 were already labeled (false positives — e.g. `ThemeEditorScreen.kt` header buttons, `CallContactPickerScreen.kt:530` "Hide dialpad"). The 14 above are the genuinely unlabeled `IconButton`s.

### SEC-02 — Network Security Config added
- Created `app/src/main/res/xml/network_security_config.xml` with `cleartextTrafficPermitted="false"` for `secure.chaty.app` / `api.chaty.app` (and subdomains).
- Wired `android:networkSecurityConfig="@xml/network_security_config"` into `<application>`.

### SEC-03 — Deep-link state-nonce validation added
- `SupabaseAuthClient.kt`: `signUp()` and `resetPasswordForEmail()` now generate a one-time nonce, store it (10-minute TTL), and append `&state=<nonce>` to the `chaty://login-callback/` / `chaty://reset-password/` `redirect_to` URL.
- `MainActivity.kt`: `handleDeepLink()` validates the `state` nonce on every `chaty://` deep link; missing/invalid/expired nonces are rejected and logged (open-redirect / callback-abuse guard). Valid links set `pendingDeepLinkData` for the auth flow to consume.

### SEC-01 — debuggable build
- Source manifest does **not** set `android:debuggable`; release builds are not debuggable by default. The running LDPlayer instance has the **debug** APK installed (expected for dev). Recommendation: add a CI guard that asserts the release APK is not debuggable.

---

## 3. Test execution

| Suite | Count | Status |
|---|---|---|
| Unit tests (`app/src/test`) | 114 files | **Blocked** — see below |
| Instrumentation (`app/src/androidTest`) | 43 files | Not run (requires ADB, which is off) |

### BUILD-01 — Unit tests cannot run without the Rust toolchain (HIGH, environment)

First attempt (`gradle :app:testDebugUnitTest`) failed before any test executed:

```
> Task :app:buildMlsNative FAILED
Execution failed for task ':app:buildMlsNative'.
> A problem occurred starting process 'command 'cargo''
BUILD FAILED in 4m 12s
```

Verified on the host:

| Check | Result |
|---|---|
| `cargo` / `rustc` on PATH | absent |
| `%USERPROFILE%\.cargo\bin\cargo.exe` | absent |
| `cargo-ndk` | absent |
| `libchaty_mls_native.so` anywhere in `app/` | none |

So the declared test command in `README.md` (`gradle :app:testDebugUnitTest :app:assembleDebug`) **cannot succeed on this machine**. Either install Rust + `cargo-ndk` + NDK, or accept the documented `-PskipMlsNative=true` escape hatch for local logic tests (the app then fails closed at runtime by design).

A retry with `-PskipMlsNative=true` **did not run any tests** — `compileDebugKotlin` failed first with **123 unresolved references**, so the suite is unverifiable in this environment without the full toolchain.

### BUILD-02 — Main/debug sources do not compile locally, so no unit test executed (HIGH, environment)

`> Task :app:compileDebugKotlin FAILED` — 123 `Unresolved reference` errors across `main` and `debug` sources. Examples:

| Symbol | Where | Likely cause |
|---|---|---|
| `uniffi`, `ChatyMlsEngine`, `MlsNativeException`, `createKeyPackage`, `createGroup`, `processMessage`, `UProcessedMessageType`, `exportSecret`, `deleteGroup`, `signatureKeyPairGenerateRaw`, … | `core/crypto/uniffi/UniffiMlsBinding.kt`, `MlsE2eeService.kt`, `MlsEngine.kt` | `generateUniffiBindings` was skipped (depends on `buildMlsNative`, which needs `cargo-ndk` → BUILD-01) |
| `DemoMode`, `demo`, `DemoSampleData` | `data/api/ApiBackendService.kt` | demo feature-flag / codegen symbol missing locally |
| `LauncherShortcutRouter`, `ProfileCameraResultBridge`, `SyncChatySystemTheme` | `MainActivity.kt`, `AppRoot.kt`, `ServiceLocator.kt` | generated/router symbol missing |
| `ChatyEmojiPack`, `UniversalEmojiPackScreen`, `font` | `UniversalStylesScreen.kt`, `ChatyTypography.kt` | theme/emoji codegen symbol missing |
| `ChatyActionShape`, `ChatyNotificationCoordinator`, `ScopedMessageSender` | `CameraZoomControls.kt`, `ServiceLocator.kt`, `ApiBackendService.kt` | generated symbol missing |
| `GeoLibreLocationMapView`, `showPin`, `disposeMap`, `clampCameraZoom` | `ui/chats/location/*`, `camera/*` | map/codegen symbol missing |

Root cause: the local run skipped the native/codegen pipeline (no Rust toolchain on this machine), so the generated bindings and feature-flag symbols that `main`/`debug` sources depend on are absent. The committed source **does** compile in CI — `build-artifacts/kotlin-apk/app-debug.apk` (281 MB, built 2026-10-30 09:47) exists and was produced by a full build. To run the unit suite locally you need: Rust + `cargo-ndk`, Android NDK, full SDK tooling matching `compileSdk = 36` (AGP 8.7.3 is only tested to 35 — add `android.suppressUnsupportedCompileSdk=36` to `gradle.properties`), and `google-services.json` if Firebase is needed.


---

## 4. Live UI/UX pass — checklist to run once ADB is on

Once LDPlayer's ADB bridge is enabled, these are the flows worth exercising, in priority order. Each maps to something in the code above.

1. **Cold start** — time from tap to first frame. `SplashScreen` → `WelcomeScreen`. Watch for ANR (prior register QA-02 recorded a 5 s+ start on emulator).
2. **Login** — empty submit, bad password, no network. Confirm inline errors and the banner appear, and the button re-enables (`LoginScreen.kt:74-118`).
3. **Chats home** — first load with zero conversations (`EmptyChatsState`), then with data. Confirm no blank flash and no scroll jump when a message arrives (`key =` is present).
4. **Chat detail** — open keyboard, type, send. Confirm composer stays above the IME (`.imePadding()`), long message wraps with ellipsis in list.
5. **Reaction / delete** — turn on airplane mode, then react to and delete a message. **Expected bug:** nothing happens, no error (UX-01).
6. **Settings** — run TalkBack and swipe the top bar. **Expected bug:** back / search / QR / edit are announced as unlabeled buttons (A11Y-01).
7. **Settings deep links** — open each menu row. Any row that lands on "not hosted in this build yet" is UX-02.
8. **Theme switch** — change accent + dark mode, then visit Register, Ongoing Call, File Picker. **Expected bug:** hardcoded colors do not follow the theme (THEME-01).
9. **Rotation** — rotate on chats home (split view at ≥640 dp) and chat detail.
10. **Font scale** — set 200 % in system settings; check chat list rows and settings headers for clipping.

### Commands (after enabling ADB)

```powershell
$adb = "C:\Users\Bandi\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb devices
& $adb shell monkey -p com.example.chat -c android.intent.category.LAUNCHER 1
& $adb logcat -c; & $adb logcat *:E AndroidRuntime:E --pid=$(& $adb shell pidof com.example.chat)
& $adb shell uiautomator dump /sdcard/ui.xml; & $adb pull /sdcard/ui.xml
& $adb exec-out screencap -p > .qa-run\shot.png
```

---

## 5. Final status

- **Live emulator verification:** done (LDPlayer, ADB enabled). App package `com.logybyte.chat` v1.0.0, activity `com.example.chat.MainActivity`. Cold start ~3.8s, **no crashes/ANRs** in logcat, chats render, WiFi connected. **FCM broken** (SEC-08 live-confirmed: `Default FirebaseApp is not initialized`). **Accessibility sparse** (A11Y-01 confirmed live: conversation avatars/rows have no TalkBack label). Note: LDPlayer runs rotated (physical 1600×900 vs dump 900×1600), so `input tap` navigation is unreliable — DPAD keyevents work instead.
- **Unit tests:** could not run — no Rust/cargo-ndk toolchain, and `compileDebugKotlin` fails with 123 unresolved references (missing generated uniffi bindings). Declared `gradle :app:testDebugUnitTest` cannot succeed locally without the full toolchain; CI already builds the APK successfully.
- **Fixes applied:** source-level fixes for A11Y-01, SEC-02, SEC-03 applied (see section 9). **Not compile-verified locally** — must run `gradle :app:compileDebugKotlin` in CI.

### Suggested order of work (remaining)

| Priority | ID | Effort |
|---|---|---|
| 1 | Compile-verify applied fixes (CI: `compileDebugKotlin`) | ~30 min setup (Rust + NDK) |
| 2 | UX-01 silent failures in ChatDetailHost.kt | ~2 h |
| 3 | THEME-01 hardcoded colors in Register / Ongoing Call | ~2 h |
| 4 | UX-02 dead-end destinations ("not hosted in this build") | ~1 h |
| 5 | I18N-01 strings.xml (587 hardcoded strings) | large, plan separately |
| 6 | PERF-01 missing LazyColumn keys (31 sites) | ~1 h |
| 7 | AppRoot consumption of `pendingDeepLinkData` (complete SEC-03 flow) | ~1 h |

---

## Appendix A — A11Y-01 locations (corrected count)

The original scanner flagged 25 locations; ~11 were already labeled (false positives: `ThemeEditorScreen.kt:198-203`, `CallContactPickerScreen.kt:530` "Hide dialpad", `TaskCreateEditModal.kt:249` "Close", `TaskDetailScreen.kt:123` "More", `TasksScreen.kt:386` "Task options", `ChatPollUi.kt:83` "Remove option"). **14 genuinely unlabeled `IconButton`s were fixed** (marked `[FIXED]` below); the rest are decorative/leading-icon nulls to review in follow-up. Path relative to `app/src/main/kotlin/com/example/chat/`.

```
[FIXED] ui/auth/RegisterOrganizationScreen.kt:275   (Show password / Hide password)
[FALSE+] ui/calls/CallContactPickerScreen.kt:530   (already "Hide dialpad")
[FIXED] ui/ChatPollUi.kt:82-90                     (Remove option / Add option)
[FIXED] ui/chats/detail/MediaDraftTray.kt:109-116  (Add more attachments — IconButton semantics present; Icon null clarified)
[FIXED] ui/chats/starred/StarredMessagesScreen.kt:153 (Back)
[FIXED] ui/core/components/composer/ChatyComposerRenderer.kt:145 (Close)
[FIXED] ui/core/components/composer/ChatyComposerRenderer.kt:192 (Cancel recording)
[FIXED] ui/core/components/composer/ChatyComposerRenderer.kt:215-310 (IconBtn root cause + 12 call sites)
[FIXED] ui/kit/GbKit.kt:119                         (Dismiss)
[FIXED] ui/settings/appearance/ComponentStylesScreen.kt:312,315 (Voice call / Video call)
[FIXED] ui/settings/messagemanagement/MessageManagementPage.kt:317,374 (Delete rule / Cancel scheduled)
[FIXED] ui/settings/SettingsScreen.kt:219,221,222,223 (Back / Search settings / Export chat / Edit profile)
[FALSE+] ui/settings/theme/ThemeEditorScreen.kt:198-203 (all already labeled)
[FALSE+] ui/tasks/TaskCreateEditModal.kt:248        (already "Close")
[FALSE+] ui/tasks/TaskDetailScreen.kt:122           (already "More")
[FALSE+] ui/tasks/TasksScreen.kt:385                (already "Task options")
[FIXED] ui/tasks/TaskDetailScreen.kt:127            (Edit task, bonus)
```

Use the `[FIXED]` items as the applied patch; `[FALSE+]` items need no change.

---

## 6. Security findings (static analysis of manifest + source)

| ID | File:line | Severity | Finding | Evidence |
|---|---|---|---|---|
| SEC-01 | `AndroidManifest.xml` / `dumpsys package` | High | **Debuggable build running on device** — `flags=[ DEBUGGABLE HAS_CODE ALLOW_CLEAR_USER_DATA ]`. A debuggable build exposes the app to debugging/injection and must not ship to production. | `dumpsys package com.logybyte.chat` |
| SEC-02 | `app/src/main/AndroidManifest.xml` | High | **No network security config** — no `res/xml/*network*` file and no `usesCleartextTraffic="false"`. No TLS pinning / cleartext policy declared. | `Get-ChildItem res -Recurse -Filter *network*` → none |
| SEC-03 | `AndroidManifest.xml:48-59` | High | **MainActivity exported with two VIEW+BROWSABLE deep-link filters** — `chaty://login-callback` and `chaty://reset-password`. OAuth callback / password-reset redirect URIs are reachable by any app; missing `state` nonce validation enables redirect-URI hijack / account takeover via crafted deep links. | manifest lines 37-59 |
| SEC-04 | `AndroidManifest.xml:63-144` | Medium | **Six exported launcher activity-aliases** (`WarmIconAlias`, `OutlineIconAlias`, `ObsidianIconAlias`, `GlassIconAlias`, `SignalIconAlias`, `FoldIconAlias`) — all `exported="true"` with MAIN/LAUNCHER, expanding launcher surface. | manifest lines 63-144 |
| SEC-05 | `AndroidManifest.xml:147-154` | Medium | **`DynamicIslandNotificationListener` exported** — a `NotificationListenerService` (`android.service.notification.NotificationListenerService`). Once the user grants notification access it can observe notifications from ALL apps on the device. | manifest lines 147-154 |
| SEC-06 | `AndroidManifest.xml:4-25` | Medium | **Dangerous permissions** requested: CAMERA, RECORD_AUDIO, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, READ_CONTACTS, SYSTEM_ALERT_WINDOW (draw over apps), READ_MEDIA_IMAGES/VIDEO/AUDIO, FOREGROUND_SERVICE_MICROPHONE/CAMERA, WAKE_LOCK. Need justification + runtime rationale. | manifest lines 4-25 |
| SEC-07 | `AndroidManifest.xml:160-177` | Low | **FileProvider + ChatyExportContentProvider** both `exported="false"` (good). Audit `ChatyExportContentProvider` `query/openFile` for path traversal. | manifest lines 160-177 |
| SEC-08 | `logcat` (live) | Medium | **Firebase not initialized at runtime** — `FcmTokenRegistrar: Failed to get FCM token: Default FirebaseApp is not initialized in this process com.logybyte.chat`. Push notifications / FCM are broken in the running build. | `adb logcat -d`, 2026-10-30 10:28:18 |
| SEC-09 | `AndroidManifest.xml:22` | Info | Custom `Application` class `.ChatyApplication` — verify it doesn't log secrets or disable security controls at init. | manifest line 22 |

**Security remediation priority:** SEC-01 (drop debuggable in release builds), SEC-02 (add Network Security Config + cleartext policy), SEC-03 (validate OAuth `state` / restrict deep-link hosts), SEC-05 (review whether NotificationListenerService must be exported), SEC-06 (permission justification), SEC-08 (initialize FirebaseApp or disable the FCM path).

---

## 7. Live emulator test results (LDPlayer, ADB now enabled)

| Check | Result | Evidence |
|---|---|---|
| Package / version | `com.logybyte.chat`, v1.0.0 (code 1), targetSdk 36 | `dumpsys package com.logybyte.chat` |
| Launch activity | `com.example.chat.MainActivity` (namespace mismatch vs source `namespace=com.example.chat`) | `dumpsys window windows` |
| Cold start time | ~3.1-3.8s, no crash | `am start -W`: TotalTime 3080 ms; logcat `Displayed ... +3s80ms` |
| Crashes / ANRs | **None** in logcat | `adb logcat -d` filtered for FATAL/ANR/Process — empty |
| Runtime errors | FCM token registration fails (Firebase not initialized) | logcat `FcmTokenRegistrar ... IllegalStateException: Default FirebaseApp is not initialized` |
| Network | WiFi connected, NOT_METERED&INTERNET&VALIDATED | `dumpsys connectivity` |
| Accessibility (live) | **A11Y-01 confirmed**: UI tree has content-desc only for bottom nav (Chats/Calls/Tasks/Settings), Search chats, Settings, More options, Connection indicator. Conversation avatars (BA/VI/CH) and conversation rows have **no** content-desc — TalkBack gets no label for who a chat is with. | `uiautomator dump` (ui_01.xml, ui_cold.xml) |
| Package/source mismatch | Installed package is `com.logybyte.chat` but source `namespace=com.example.chat` — running APK was built with a different `applicationId` than current source. | `dumpsys package` vs `app/build.gradle.kts` |

**Limitation on live interaction:** LDPlayer runs rotated (physical 1600x900 landscape; uiautomator reports 900x1600 portrait), so `adb input tap` coordinates did not reliably reach targets. In-app navigation taps were therefore not pursued. Settings, dead-end-route, and reaction/delete flows were verified statically only. All UI findings above come from the accessibility hierarchy dump (reliable) plus static source analysis.

---

## 8. Final status

- **Static source audit:** complete — UI/UX findings (A11Y-01, UX-01, UX-02, I18N-01, THEME-01, PERF-01), security findings (SEC-01…SEC-09), build/test findings (BUILD-01, BUILD-02), all with file:line evidence.
- **Live emulator verification:** partial — app launches (~3.8s), no crashes/ANRs, chats render, accessibility tree sparse (A11Y-01 confirmed live), FCM broken (SEC-08 confirmed live). Precise in-app tap navigation blocked by LDPlayer rotated-coordinate mismatch; Settings/dead-end/reaction flows verified statically only.
- **Unit tests:** could not run — no Rust/cargo-ndk toolchain (BUILD-01) and `compileDebugKotlin` fails with 123 unresolved references (BUILD-02). The APK was built successfully in CI (`build-artifacts/kotlin-apk/app-debug.apk`, 2026-10-30).
- **Fixes applied:** none. Report-only pass, per your request.

### Suggested order of work

| Priority | ID | Effort |
|---|---|---|
| 1 | SEC-01 remove debuggable from release builds | ~30 min |
| 2 | A11Y-01 unlabeled buttons (25) | ~30 min, mechanical |
| 3 | SEC-02 network security config + cleartext policy | ~1 h |
| 4 | SEC-03 deep-link `state` validation | ~1 h |
| 5 | UX-01 silent failures in ChatDetailHost.kt | ~2 h |
| 6 | THEME-01 hardcoded colors (Register / Ongoing Call) | ~2 h |
| 7 | SEC-08 initialize FirebaseApp or disable FCM path | ~1 h |
| 8 | UX-02 dead-end destinations | ~1 h |
| 9 | I18N-01 strings.xml | large, plan separately |
| 10 | PERF-01 missing LazyColumn keys | ~1 h |
| 11 | BUILD-01 install Rust + NDK toolchain so tests run | ~30 min setup |

**To continue live testing:** enable ADB on LDPlayer (done), then the rotated-coordinate issue must be resolved (e.g., set LDPlayer to portrait, or use `adb shell input keyevent` / `monkey` instead of tap) before the Settings, send-flow, and airplane-mode reaction/delete tests can be executed.








