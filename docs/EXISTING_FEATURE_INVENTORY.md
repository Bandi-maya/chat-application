# EXISTING FEATURE INVENTORY & SCREEN ARCHITECTURE

## Executive Summary
This document indexes the complete application screen catalog (70 distinct screens and subpages across 13 feature domains), mapping entry routes, state owners, and underlying system integrations.

---

## 1. Complete Screen & Route Inventory

### 1.1 Authentication & Onboarding
1. **Splash Screen** (`lib/features/auth/splash_screen.dart`): Initializes services, checks session tokens, routes to Welcome or Home.
2. **Welcome Screen** (`lib/features/auth/welcome_screen.dart`): Initial branding, terms agreement, login/register triggers.
3. **Login Screen** (`lib/features/auth/login_screen.dart`): Phone/email credential input, authentication request.
4. **Register Screen** (`lib/features/auth/register_screen.dart`): Account registration form.
5. **OTP Verification Screen** (`lib/features/auth/otp_verification_screen.dart`): Multi-digit pin input, SMS/email verification, countdown timer.
6. **Profile Setup Screen** (`lib/features/auth/profile_setup_screen.dart`): Avatar selection, display name, initial bio.
7. **Forgot Password Screen** (`lib/features/auth/forgot_password_screen.dart`): Password recovery request.
8. **Create New Password Screen** (`lib/features/auth/create_new_password_screen.dart`): Password reset confirmation.
9. **Permission Onboarding Screen** (`lib/features/onboarding/permission_onboarding_screen.dart`): Multi-permission grant wizard (Camera, Mic, Contacts, Notifications).

### 1.2 Main Shell & Core Messaging
10. **Chats Home Screen** (`lib/features/chats/chats_home_screen.dart`): Main inbox, pinned chats, unread counters, search integration, floating new chat action.
11. **Chat Detail Screen** (`lib/features/chats/chat_detail_screen.dart`): Full messaging timeline, bubble rendering, media preview, input bar, voice notes, stickers, reactions, app bar call triggers.
12. **New Chat Screen** (`lib/features/chats/new_chat_screen.dart`): Contact selector, create group trigger, QR scan shortcut.
13. **Contact Info Screen** (`lib/features/chats/contact_info_screen.dart`): Contact details, media gallery, encryption verification, mute options.
14. **Contact Privacy Screen** (`lib/features/chats/contact_privacy_screen.dart`): Per-contact privacy toggles (hide read receipts, custom notifications).
15. **Group Info Screen** (`lib/features/chats/group_info_screen.dart`): Group participants, admin roles, group settings, invite links.
16. **Locked Chats Screen** (`lib/features/chats/locked_chats_screen.dart`): Biometric/PIN-protected chat vault.
17. **Linked Devices QR Screen** (`lib/features/chats/linked_devices_qr_screen.dart`): QR code presentation/scanner for web/desktop pairing.
18. **Global Search Screen** (`lib/features/search/global_search_screen.dart`): Multi-scope search (Chats, Messages, Contacts, Media).

### 1.3 Calling & WebRTC Engine
19. **Calls Screen** (`lib/features/calls/calls_screen.dart`): Call log history, incoming/outgoing/missed icons, new call launcher.
20. **Call Contact Picker Screen** (`lib/features/calls/call_contact_picker_screen.dart`): Searchable contacts with dual-SIM or audio/video launch buttons.
21. **Incoming Call Screen** (`lib/features/calls/incoming_call_screen.dart`): Full-screen incoming call UI with slide-to-answer/decline.
22. **Ongoing Call Screen** (`lib/features/calls/ongoing_call_screen.dart`): Active call grid, audio mute, video toggle, speakerphone, bluetooth switch, screen sharing, call duration ticker.

### 1.4 Camera & Media Capture
23. **Camera Capture Screen** (`lib/features/camera/camera_capture_screen.dart`): Full camera viewfinder, flash toggle, lens flip, shutter button, video recording mode, AR face mesh filter integration.
24. **Media Viewer Screen** (`lib/features/messages/media_viewer_screen.dart`): Fullscreen pinch-to-zoom photo and video player with caption overlay.
25. **Starred Messages Screen** (`lib/features/messages/starred_messages_screen.dart`): Filtered list of bookmarked/starred messages with jump-to-chat.

### 1.5 Tasks & Productivity
26. **Tasks Screen** (`lib/features/tasks/tasks_screen.dart`): Productivity task list, completion checkboxes, filtering.
27. **Task Detail Screen** (`lib/features/tasks/task_detail_screen.dart`): Task editing, due date picker, priority selection.

### 1.6 Profile & Social Updates
28. **Profile Screen** (`lib/features/profile/profile_screen.dart`): User profile summary, phone number, bio, status QR.
29. **Profile Edit Screen** (`lib/features/profile/profile_edit_screen.dart`): Edit display name, about status, avatar crop.
30. **Updates Screen** (`lib/features/updates/updates_screen.dart`): Status stories, text status updates, channel feeds.

### 1.7 Settings & Customization Engine (40 Screens)
31. **Settings Root Screen** (`lib/features/settings/settings_root_screen.dart`) / **Settings Screen** (`lib/features/settings/settings_screen.dart`): Main settings navigation hub.
32. **Account Settings Screen** (`lib/features/settings/account/account_settings_screen.dart`): Security, two-step verification, change number, delete account.
33. **App Icon Settings Screen** (`lib/features/settings/appearance/app_icon_settings_screen.dart`): Dynamic app icon selector.
34. **Universal Appearance Screen** (`lib/features/settings/appearance/universal_appearance_screen.dart`): Theme mode (Light/Dark/System), color palette selector.
35. **Call Settings Screen** (`lib/features/settings/calls/call_settings_screen.dart`): Low data mode for calls, call waiting, silence unknown callers.
36. **Conversation Action Bar Screen** (`lib/features/settings/conversation/conversation_action_bar_screen.dart`): Customization of chat header buttons and actions.
37. **Conversation Bubble & Ticks Screen** (`lib/features/settings/conversation/conversation_bubble_and_ticks_screen.dart`): Custom chat bubble styles and tick icons.
38. **Conversation Entry Style Screen** (`lib/features/settings/conversation/conversation_entry_style_screen.dart`): Input bar shape, button styles, attachment drawer configurations.
39. **Conversation More Options Screen** (`lib/features/settings/conversation/conversation_more_options_screen.dart`): Extended conversation preferences.
40. **Conversation Settings Page** (`lib/features/settings/conversation/conversation_settings_page.dart`): Wallpaper, enter-is-send toggle, media visibility.
41. **Customization Center Screen** (`lib/features/settings/customization/customization_center_screen.dart`): GB/Advanced style tweaks.
42. **GB Feature Center Screen** (`lib/features/settings/gb_features/gb_feature_center_screen.dart`) & **GB Settings Screen**: Advanced privacy toggles (freeze last seen, anti-delete messages, hide view status).
43. **Header Settings Screen** (`lib/features/settings/home/header_settings_screen.dart`): Home app bar title style, icon layout, status tab layout.
44. **Home FAB Settings Screen** (`lib/features/settings/home/home_fab_settings_screen.dart`): Floating action button position and action mapping.
45. **Home Rows Settings Screen** (`lib/features/settings/home/home_rows_settings_screen.dart`): Chat row divider styles, avatar shapes, date formatting.
46. **Home Screen Settings Page** (`lib/features/settings/home/home_screen_settings_page.dart`): General home layout options.
47. **Status Settings Screen** (`lib/features/settings/home/status_settings_screen.dart`): Status privacy, auto-download, mute updates.
48. **Storage & Media Settings Screen** (`lib/features/settings/media/storage_and_media_settings_screen.dart`): Auto-download on Wi-Fi/Cellular, storage usage breakdown.
49. **Notification Toast Settings Screen** (`lib/features/settings/notifications/notification_toast_settings_screen.dart`): Custom in-app event toast overlay options.
50. **Notification Settings Page** (`lib/features/settings/notifications/notification_settings_page.dart`): Sound, vibration, Dynamic Island notification listener toggle, badge previews.
51. **System Permissions Screen** (`lib/features/settings/permissions/system_permissions_screen.dart`): Status of all Android permissions with jump-to-settings.
52. **Privacy Center Screen** (`lib/features/settings/privacy/privacy_center_screen.dart`): Read receipts, last seen, profile photo audience, blocked contacts.
53. **Security Center Screen** (`lib/features/settings/security/security_center_screen.dart`): Biometric lock, app lock timeout, OpenMLS encryption details.
54. **Navigation Destinations Settings Screen** (`lib/features/settings/templates/navigation_destinations_settings_screen.dart`): Reorderable bottom nav items.
55. **Component Override Screen** (`lib/features/settings/templates/component_override_screen.dart`): Granular widget overrides.
56. **Templates Settings Screen** (`lib/features/settings/templates/templates_settings_screen.dart`): Complete preset theme loader.
57. **Backup & Restore Screen** (`lib/features/settings/universal/backup_and_restore_screen.dart`): Local and cloud chat backups.
58. **Hide Media Screen** (`lib/features/settings/universal/hide_media_screen.dart`): Media gallery privacy controls.
59. **Universal Colors Screen** (`lib/features/settings/universal/universal_colors_screen.dart`): Custom hex color overrides for every surface.
60. **Universal General Settings Screen** (`lib/features/settings/universal/universal_general_settings_screen.dart`): Language, font selection, app scale.
61. **Universal Screen** (`lib/features/settings/universal/universal_screen.dart`): Unified appearance hub.
62. **Universal Styles Screen** (`lib/features/settings/universal/universal_styles_screen.dart`): Corner radius, border width, elevation slider.
63. **Navigation Effects Page** (`lib/features/settings/effects/navigation_effects_page.dart`): Page route transitions (slide, fade, zoom, spring).
64. **Message Management Page** (`lib/features/settings/message_management/message_management_page.dart`): Auto-delete expired chats, batch archive.
65. **Pro Pricing Screen** (`lib/features/settings/pro_pricing_screen.dart`): Pro subscription perks and feature comparison.
66. **Theme Editor Screen** (`lib/features/settings/theme_editor_screen.dart`): Live visual theme customizer with real-time preview.
67. **UI Lab Screen** (`lib/features/ui_lab/ui_lab_screen.dart`): Sandbox for testing experimental widgets and animations.

---

## 2. State & Persistence Architecture
* **Theme & Appearance Persistence**: `SharedPreferences` via `lib/ui/core/design_system/gb_design_system.dart` and `settings_primitives.dart`.
* **Database & Chat Storage**: Local SQLite/file storage + Supabase remote backend sync (`supabase_flutter`).
* **End-to-End Cryptography**: OpenMLS Android FFI engine (`third_party/openmls_android`).
* **Native System Channels**:
  * `DynamicIslandNotificationListener.kt`: Listens to incoming system notifications and forwards to Flutter overlay via EventChannel.
  * `MainActivity.kt`: Method channels for haptics, battery optimization, and window flags.
