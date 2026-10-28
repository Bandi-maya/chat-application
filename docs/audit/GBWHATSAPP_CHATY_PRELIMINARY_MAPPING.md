# GB WhatsApp → Chaty Kotlin Preliminary Mapping

This is a **static-source mapping**, not a visual/runtime parity verdict. Reference evidence is from APK archive/DEX string inspection; target evidence is from the current `kotlin` branch tree and source file inspection. A match in a source file name does not prove the behavior is equivalent.

| APK string/resource evidence | Existing Kotlin candidate | Preliminary assessment | Required verification |
|---|---|---|---|
| `yo_settings_home`, `yo_settings_homeheader`, `yo_settings_homefab`, `yo_settings_homerows` | `app/src/main/kotlin/com/example/chat/ui/settings/home/HomeScreenSettingsPage.kt`, `HeaderSettingsScreen.kt`, `HomeFabSettingsScreen.kt`, `HomeRowsSettingsScreen.kt` | Existing home customization areas are present in source | Open the matching APK screens, record all controls and preview/apply/reset behavior, compare runtime screenshots and persisted values |
| `yo_settings_convo`, `yo_settings_convoactionbar`, `yo_settings_convoentry`, `yo_settings_convobubbleticks` | `app/src/main/kotlin/com/example/chat/ui/settings/conversation/ConversationSettingsPage.kt`, `ConversationActionBarScreen.kt`, `ConversationEntryStyleScreen.kt`, `ConversationBubbleAndTicksScreen.kt` | Existing conversation customization areas are present in source | Verify actual options, selection state, chat preview, restart persistence, and composer/bubble regressions |
| `yo_settings_universal`, `yo_settings_universalstyles`, `yo_settings_yothemes` | `app/src/main/kotlin/com/example/chat/ui/settings/appearance/UniversalAppearanceScreen.kt`, `app/src/main/kotlin/com/example/chat/ui/core/theme/`, `app/src/main/kotlin/com/example/chat/core/theme/` | Theme/appearance infrastructure exists | Compare palette, font, layout, wallpaper, motion, live preview, apply/cancel/reset behavior and accessibility |
| `YoMods`, `yoMods`, `yomods`, `yo_settings_prefsview` | `app/src/main/kotlin/com/example/chat/ui/settings/gb/GbSettingsScreen.kt`, `GbFeatureCenterScreen.kt`, `GbSettingsLogic.kt`, `GbFeatureCenterLogic.kt` | GB-style feature center already exists in target source | Compare feature grouping, toggle descriptions, disabled/unavailable state, and whether each control is actually wired |
| `yo_settings_privsec`, `yo_settings_secprivacy`, chat-lock/privacy strings | `app/src/main/kotlin/com/example/chat/ui/settings/privacy/PrivacyCenterScreen.kt`, `app/src/main/kotlin/com/example/chat/ui/settings/security/AppLockController.kt`, `AppLockOverlay.kt` | Privacy and app-lock areas exist | Validate real privacy semantics, credential setup/re-entry, cooldown, biometric fallback, process death, and fail-closed behavior |
| Conversation emoji/mic/media entry strings and `yowa_*_conversation_entry` resources | `app/src/main/kotlin/com/example/chat/ui/chats/detail/ChatComposer.kt`, `ChatyEmojiPicker.kt`, `EmojiCatalog.kt`, `MessageBubble.kt` | Composer, emoji picker and bubble components exist | Verify cursor insertion, draft retention, category/search/recent behavior, accessibility labels, emoji rendering, audio recording/playback lifecycle |
| Theme preview/gallery class strings | `app/src/main/kotlin/com/example/chat/ui/core/theme/ThemePreviewCard.kt`, `ThemePairPreview.kt`, `AppliedPairPreview.kt` | Preview-related components exist | Establish whether reference uses live preview or explicit apply; do not add preview behavior without observed evidence |
| APK contains WhatsApp call, media, location and notification component names | Existing call/media/notification components in Kotlin branch (full mapping still required) | Static APK strings include platform feature areas; no feature parity conclusion | Test actual APK workflows and map to exact Kotlin call/media/location/notification implementations, including system permissions and error states |
| `yo_settings_backuprestore` | No target mapping established in this pass | **Unknown / potentially intentionally unsupported** | Inspect APK UI and data flows; do not implement backup/restore without defining data protection and migration requirements |

## Source files inspected

- `GbSettingsScreen.kt`
- `GbFeatureCenterScreen.kt`
- `UniversalAppearanceScreen.kt`
- `ChatComposer.kt`
- `ChatyEmojiPicker.kt`
- `PrivacyCenterScreen.kt`
- `AppLockController.kt`

## Implementation guardrails

1. Do not make the UI a pixel-perfect clone by copying protected code or assets. Use behavior and screen observations to guide independent implementation.
2. Do not expose or copy any embedded API keys, credentials, private endpoints, or protected data from the APK.
3. Preserve existing active backend routing, MLS fail-closed encryption paths, app ID, saved data, and preference compatibility.
4. Keep one message composer and insert selected emoji into the existing text field at the cursor.
5. Every implemented difference must include a test and emulator evidence. A build or unit test alone does not prove visual or real-service parity.
6. Mark inaccessible/account-gated/service-dependent screens as blocked or not observed rather than assuming expected behavior.

## Status

- APK archive/hash inspection: **PARTIAL PASS**
- Static target source mapping: **INITIAL**
- APK install and interactive screen inventory: **NOT STARTED**
- UI comparison and issue prioritization: **NOT VERIFIED**
- Kotlin code modifications based on APK behavior: **NOT STARTED**
- Final emulator and regression verification: **NOT STARTED**
