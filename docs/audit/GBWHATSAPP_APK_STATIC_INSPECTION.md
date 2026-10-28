# GB WhatsApp APK — Initial Static Inspection

**Reference file:** `GBWhatsApp_P_com.gbwhatsapp_V_v2.26.39.80.apk`  
**Reported version in filename:** `2.26.39.80` (not yet independently decoded from manifest)  
**SHA-256:** `eeb78f640319f171dc7dffd0371acb1e258687d92b7910cc4cef0653170abfd4`  
**File size:** 139,162,654 bytes (~132.7 MiB)  
**ZIP entries:** 17,684  
**DEX files:** 13  
**Native libraries:** 14  
**Inspection type:** Static archive and string inspection only. No APK execution or emulator interaction was performed.

## Confirmed observations

- The archive contains a binary Android manifest and multiple DEX files.
- Manifest string extraction includes package identifier `com.gbwhatsapp`, a launcher category, WhatsApp activity names, and GB WhatsApp-specific components.
- DEX string extraction includes GB/YoMods customization identifiers and settings resource names such as:
  - `yo_settings_home`
  - `yo_settings_convo`
  - `yo_settings_privsec`
  - `yo_settings_universal`
  - `yo_settings_universalstyles`
  - `yo_settings_yothemes`
  - `yo_settings_backuprestore`
  - `yo_settings_homeheader`
  - `yo_settings_homefab`
  - `yo_settings_homerows`
  - `yo_settings_convoactionbar`
  - `yo_settings_convoentry`
  - `yo_settings_convobubbleticks`
- Extracted strings include references to chat lock, privacy settings, status privacy, conversation style variants, emoji entry, microphone entry, media attachment entry, and theme preview/gallery components.
- The archive contains native libraries for both `armeabi-v7a` and `arm64-v8a`.

These are static string/resource indicators. They do **not** prove that every named feature is enabled, functional, visible in this particular build, or equivalent to a Chaty feature.

## Security and handling notes

- The APK is a third-party modified messaging client. Treat it as untrusted.
- Do not install it on a primary phone or sign into a personal account during evaluation. Use a disposable, isolated emulator and test account only.
- Do not publish embedded API keys, tokens, private endpoints, account data, or extracted protected assets.
- Do not bypass login, encryption, anti-abuse controls, or other security boundaries.
- The SHA-256 above identifies this exact uploaded file and should be recorded in any subsequent test report.

## Not yet verified

- Exact manifest version name/code, SDK levels, launcher entry point, and complete component inventory via a proper Android binary-XML decoder.
- Runtime screen-by-screen inventory, actual settings interactions, persistence behavior, permissions, network behavior, and crashes.
- Decompilation/deobfuscation of relevant GB/YoMods code.
- Visual comparison against Chaty's Kotlin/Compose screens.
- Any Chaty source-code implementation, build, emulator test, or regression check resulting from this APK.

## Next steps

1. Decode the binary manifest with Android SDK build tools or a suitable APK analysis tool.
2. Review DEX structure and relevant mod-specific code in an isolated analysis environment.
3. Install and explore only in a disposable emulator if the environment supports it.
4. Build an evidence-backed screen/interaction matrix and map each observed behavior to existing Kotlin components.
5. Implement only the appropriate independent equivalent behavior in Chaty, preserving existing encryption, active backend, data, package identity, and security behavior.
6. Run unit tests, instrumented tests, emulator interactions, restart/persistence checks, and regression tests. Record actual outputs; do not infer success.

## Status

**Static archive inspection: PARTIAL.**  
**Dynamic APK audit: NOT STARTED.**  
**Chaty implementation based on APK: NOT STARTED.**  
**Reason:** This environment currently has no `adb`, `apktool`, `aapt`, `apkanalyzer`, or `jadx` executable available. A proper decoder/emulator toolchain is required before claiming dynamic observations or implementing verified parity.
