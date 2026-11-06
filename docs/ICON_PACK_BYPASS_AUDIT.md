# Icon pack bypass source audit, stage9

Whole app/src/main Kotlin scan:1913candidate lines in181files, including1763direct Material vector references and150custom/resource drawing sites. This is an inventory, not a count of confirmed bugs: imports are excluded but preview code and variable definitions can still be candidates. Runtime code can choose vectors indirectly too, so source scan alone does not prove exhaustive visual coverage. No behavior or branding remap approved by this scan.

## Fixed now
ConnectionUi.kt hand-drawn health bars bypassed the provider. Replaced with ChatyIconId.signal using current global/per-icon pack choice. Health strength is a foreground clipping mask over a dim pack glyph; offline keeps the glyph and slash. Tap target, callback, text and health colors preserved. Banner status glyph now uses check/signal from pack rather than direct Material vectors. Glyph details can vary with pack; verify excellent/weak/poor/offline visually.

## Follow-up migration scope
Direct Icons usages and painterResource/Canvas sites still require semantic review. Do not replace photos, QR data, waveforms, charts, avatars, brand marks, capture overlays, progress/state drawing or icon-pack previews with arbitrary glyphs. Preserve click logic, accessibility, state and tint. 77semantic IDs currently exist; add licensed assets with provenance for missing meanings, do not disguise an unrelated existing ID.

Vector resource inventory: 482XML vectors. XML drawable assignment sites: 0files. These include all bundled icon families, not independent bypass bugs. app/src/main/java contains Kotlin model code and is included in recursive scan. Launcher icons, notifications and platform-system UI are not global Compose pack-provider consumers and should be tracked separately.

Top candidate files:
- kotlin/com/example/chat/ui/camera/EffectRegistry.kt: 100 candidate lines
- kotlin/com/example/chat/ui/settings/SettingsScreen.kt: 52 candidate lines
- kotlin/com/example/chat/ui/calls/bundles/BundleOngoingCallView.kt: 47 candidate lines
- kotlin/com/example/chat/ui/settings/home/GbLiveHeaderPreview.kt: 45 candidate lines
- kotlin/com/example/chat/ui/camera/LensOverlay.kt: 44 candidate lines
- kotlin/com/example/chat/ui/settings/notifications/NotificationSettingsPage.kt: 42 candidate lines
- kotlin/com/example/chat/ui/settings/templates/TemplatePreviewWidget.kt: 41 candidate lines
- kotlin/com/example/chat/ui/settings/navigation/SettingsIcons.kt: 38 candidate lines
- kotlin/com/example/chat/ui/chats/ChatDetailScreen.kt: 36 candidate lines
- kotlin/com/example/chat/ui/settings/SettingsRootScreen.kt: 35 candidate lines
- kotlin/com/example/chat/ui/settings/templates/ComponentOverrideScreen.kt: 34 candidate lines
- kotlin/com/example/chat/ui/chats/home/HomeAppBars.kt: 33 candidate lines
- kotlin/com/example/chat/ui/camera/LensManager.kt: 31 candidate lines
- kotlin/com/example/chat/ui/settings/home/HeaderSettingsScreen.kt: 31 candidate lines
- kotlin/com/example/chat/ui/chats/detail/MessageBubble.kt: 30 candidate lines
- kotlin/com/example/chat/ui/settings/conversation/ConversationSettingsPage.kt: 28 candidate lines
- kotlin/com/example/chat/ui/settings/privacy/PrivacyCenterScreen.kt: 26 candidate lines
- kotlin/com/example/chat/ui/settings/notifications/NotificationToastSettingsScreen.kt: 25 candidate lines
- kotlin/com/example/chat/ui/settings/gb/GbSettingsScreen.kt: 24 candidate lines
- kotlin/com/example/chat/ui/chats/detail/ChatComposer.kt: 23 candidate lines
- kotlin/com/example/chat/ui/settings/permissions/SystemPermissionsScreen.kt: 23 candidate lines
- kotlin/com/example/chat/ui/navigation/NavigationFab.kt: 23 candidate lines
- kotlin/com/example/chat/ui/calls/OngoingCallScreen.kt: 22 candidate lines
- kotlin/com/example/chat/ui/calls/bundles/BundleIncomingCallView.kt: 19 candidate lines
- kotlin/com/example/chat/ui/settings/home/StatusSettingsScreen.kt: 19 candidate lines


Full line-level inventory: docs/icon-pack-bypass-candidates.csv. Android compile, instrumented clicks and actual pixel inspection remain publisher gates. This increment does not claim app-wide migration complete.
