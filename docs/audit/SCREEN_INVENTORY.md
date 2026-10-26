# Screen Inventory

Initial source review: 27 Oct 2026, 01:43 IST. Build launch update: 01:49; runtime compile failure update: 01:54. Source review and unit results are not emulator acceptance.

Source candidates: 91 functions. Not one-to-one reachable screens or completed tests. Reconcile call sites, root route, nested sheets, all variants and production navigation.

| Function | Source | Runtime |
|---|---|---|
| AccountSettingsScreen | `app/src/main/kotlin/com/example/chat/ui/settings/account/AccountSettingsScreen.kt` | Unverified in this report |
| AppIconSettingsScreen | `app/src/main/kotlin/com/example/chat/ui/settings/appicon/AppIconSettingsScreen.kt` | Unverified in this report |
| ArchivedChatsScreen | `app/src/main/kotlin/com/example/chat/ui/chats/home/ArchivedChatsScreen.kt` | Unverified in this report |
| AudioSettingsScreen | `app/src/main/kotlin/com/example/chat/ui/settings/AudioSettingsScreen.kt` | Unverified in this report |
| AutoReplyRulesScreen | `app/src/main/kotlin/com/example/chat/ui/automation/AutoReplyRulesScreen.kt` | Unverified in this report |
| BackupAndRestoreScreen | `app/src/main/kotlin/com/example/chat/ui/settings/universal/BackupAndRestoreScreen.kt` | Unverified in this report |
| CallContactPickerScreen | `app/src/main/kotlin/com/example/chat/ui/calls/CallContactPickerScreen.kt` | Unverified in this report |
| CallSettingsScreen | `app/src/main/kotlin/com/example/chat/ui/settings/CallSettingsScreen.kt` | Unverified in this report |
| CallsScreen | `app/src/main/kotlin/com/example/chat/ui/calls/CallsScreen.kt` | Unverified in this report |
| ChatDetailScreen | `app/src/main/kotlin/com/example/chat/ui/chats/ChatDetailScreen.kt` | Unverified in this report |
| ChatsHomeScreen | `app/src/main/kotlin/com/example/chat/ui/chats/ChatsHomeScreen.kt` | Unverified in this report |
| ChatyAudioPlayerScreen | `app/src/main/kotlin/com/example/chat/ui/media/ChatyAudioPlayerScreen.kt` | Unverified in this report |
| ChatyCameraCaptureScreen | `app/src/main/kotlin/com/example/chat/ui/camera/CameraCaptureScreen.kt` | Unverified in this report |
| ChatyLicensePage | `app/src/main/kotlin/com/example/chat/ui/kit/ChatyAboutDialog.kt` | Unverified in this report |
| ChatyPushedPage | `app/src/main/kotlin/com/example/chat/ui/AppRoot.kt` | Unverified in this report |
| ChatySettingsPage | `app/src/main/kotlin/com/example/chat/ui/settings/ChatySettingsPrimitives.kt` | Unverified in this report |
| ComponentOverrideScreen | `app/src/main/kotlin/com/example/chat/ui/settings/templates/ComponentOverrideScreen.kt` | Unverified in this report |
| ComponentStylesScreen | `app/src/main/kotlin/com/example/chat/ui/settings/appearance/ComponentStylesScreen.kt` | Unverified in this report |
| ContactInfoScreen | `app/src/main/kotlin/com/example/chat/ui/chats/contact_info/ContactInfoScreen.kt` | Unverified in this report |
| ContactPrivacyScreen | `app/src/main/kotlin/com/example/chat/ui/chats/contact_info/ContactPrivacyScreen.kt` | Unverified in this report |
| ConversationActionBarScreen | `app/src/main/kotlin/com/example/chat/ui/settings/conversation/ConversationActionBarScreen.kt` | Unverified in this report |
| ConversationBubbleAndTicksScreen | `app/src/main/kotlin/com/example/chat/ui/settings/conversation/ConversationBubbleAndTicksScreen.kt` | Unverified in this report |
| ConversationEntryStyleScreen | `app/src/main/kotlin/com/example/chat/ui/settings/conversation/ConversationEntryStyleScreen.kt` | Unverified in this report |
| ConversationMoreOptionsScreen | `app/src/main/kotlin/com/example/chat/ui/settings/conversation/ConversationMoreOptionsScreen.kt` | Unverified in this report |
| ConversationSettingsPage | `app/src/main/kotlin/com/example/chat/ui/settings/conversation/ConversationSettingsPage.kt` | Unverified in this report |
| CreateNewPasswordScreen | `app/src/main/kotlin/com/example/chat/ui/auth/CreateNewPasswordScreen.kt` | Unverified in this report |
| CustomFilePickerScreen | `app/src/main/kotlin/com/example/chat/ui/media/CustomFilePickerScreen.kt` | Unverified in this report |
| DocumentReaderScreen | `app/src/main/kotlin/com/example/chat/ui/media/DocumentReaderScreen.kt` | Unverified in this report |
| ForgotPasswordScreen | `app/src/main/kotlin/com/example/chat/ui/auth/ForgotPasswordScreen.kt` | Unverified in this report |
| GbFeatureCenterScreen | `app/src/main/kotlin/com/example/chat/ui/settings/gb/GbFeatureCenterScreen.kt` | Unverified in this report |
| GbSettingsScreen | `app/src/main/kotlin/com/example/chat/ui/settings/gb/GbSettingsScreen.kt` | Unverified in this report |
| GlobalSearchScreen | `app/src/main/kotlin/com/example/chat/ui/search/GlobalSearchScreen.kt` | Unverified in this report |
| GroupInfoScreen | `app/src/main/kotlin/com/example/chat/ui/chats/group_info/GroupInfoScreen.kt` | Unverified in this report |
| HeaderSettingsScreen | `app/src/main/kotlin/com/example/chat/ui/settings/home/HeaderSettingsScreen.kt` | Unverified in this report |
| HiddenVaultScreen | `app/src/main/kotlin/com/example/chat/ui/vault/HiddenVaultScreen.kt` | Unverified in this report |
| HideMediaScreen | `app/src/main/kotlin/com/example/chat/ui/settings/HideMediaScreen.kt` | Unverified in this report |
| HomeFabSettingsScreen | `app/src/main/kotlin/com/example/chat/ui/settings/home/HomeFabSettingsScreen.kt` | Unverified in this report |
| HomeRowsSettingsScreen | `app/src/main/kotlin/com/example/chat/ui/settings/home/HomeRowsSettingsScreen.kt` | Unverified in this report |
| HomeScreenSettingsPage | `app/src/main/kotlin/com/example/chat/ui/settings/home/HomeScreenSettingsPage.kt` | Unverified in this report |
| ImageEditorScreen | `app/src/main/kotlin/com/example/chat/ui/chats/detail/ImageEditorScreen.kt` | Unverified in this report |
| IncomingCallScreen | `app/src/main/kotlin/com/example/chat/ui/calls/IncomingCallScreen.kt` | Unverified in this report |
| LinkedDevicesQrScreen | `app/src/main/kotlin/com/example/chat/ui/chats/linked_devices/LinkedDevicesQrScreen.kt` | Unverified in this report |
| LockedChatsScreen | `app/src/main/kotlin/com/example/chat/ui/chats/locked_chats/LockedChatsScreen.kt` | Unverified in this report |
| LoginScreen | `app/src/main/kotlin/com/example/chat/ui/auth/LoginScreen.kt` | Unverified in this report |
| MapViewerScreen | `app/src/main/kotlin/com/example/chat/ui/chats/location/MapViewerScreen.kt` | Unverified in this report |
| MediaReviewScreen | `app/src/main/kotlin/com/example/chat/ui/chats/detail/MediaReviewScreen.kt` | Unverified in this report |
| MediaTrimScreen | `app/src/main/kotlin/com/example/chat/ui/chats/detail/MediaTrimScreen.kt` | Unverified in this report |
| MediaViewerScreen | `app/src/main/kotlin/com/example/chat/ui/chats/detail/MediaViewerScreen.kt` | Unverified in this report |
| MediaWorkspaceScreen | `app/src/main/kotlin/com/example/chat/ui/media/MediaWorkspaceScreen.kt` | Unverified in this report |
| MessageManagementPage | `app/src/main/kotlin/com/example/chat/ui/settings/messagemanagement/MessageManagementPage.kt` | Unverified in this report |
| NavigationDestinationsSettingsScreen | `app/src/main/kotlin/com/example/chat/ui/settings/navigation/NavigationDestinationsSettingsScreen.kt` | Unverified in this report |
| NavigationEffectsScreen | `app/src/main/kotlin/com/example/chat/ui/settings/NavigationEffectsScreen.kt` | Unverified in this report |
| NewChatScreen | `app/src/main/kotlin/com/example/chat/ui/chats/NewChatScreen.kt` | Unverified in this report |
| NotificationSettingsPage | `app/src/main/kotlin/com/example/chat/ui/settings/notifications/NotificationSettingsPage.kt` | Unverified in this report |
| NotificationToastSettingsScreen | `app/src/main/kotlin/com/example/chat/ui/settings/notifications/NotificationToastSettingsScreen.kt` | Unverified in this report |
| OngoingCallScreen | `app/src/main/kotlin/com/example/chat/ui/calls/OngoingCallScreen.kt` | Unverified in this report |
| PdfWorkspaceScreen | `app/src/main/kotlin/com/example/chat/ui/media/PdfWorkspaceScreen.kt` | Unverified in this report |
| PermissionOnboardingScreen | `app/src/main/kotlin/com/example/chat/ui/onboarding/PermissionOnboarding.kt` | Unverified in this report |
| PlanSubscriptionScreen | `app/src/main/kotlin/com/example/chat/ui/subscription/PlanSubscriptionScreen.kt` | Unverified in this report |
| PrivacyCenterScreen | `app/src/main/kotlin/com/example/chat/ui/settings/privacy/PrivacyCenterScreen.kt` | Unverified in this report |
| ProPricingScreen | `app/src/main/kotlin/com/example/chat/ui/settings/pro/ProPricingScreen.kt` | Unverified in this report |
| ProfileEditScreen | `app/src/main/kotlin/com/example/chat/ui/profile/ProfileEditScreen.kt` | Unverified in this report |
| ProfileScreen | `app/src/main/kotlin/com/example/chat/ui/profile/ProfileScreen.kt` | Unverified in this report |
| RegisterOrganizationScreen | `app/src/main/kotlin/com/example/chat/ui/auth/RegisterOrganizationScreen.kt` | Unverified in this report |
| RegisterScreen | `app/src/main/kotlin/com/example/chat/ui/auth/RegisterScreen.kt` | Unverified in this report |
| ScheduledMessagesScreen | `app/src/main/kotlin/com/example/chat/ui/automation/ScheduledMessagesScreen.kt` | Unverified in this report |
| SecurityCenterScreen | `app/src/main/kotlin/com/example/chat/ui/settings/security/SecurityCenterScreen.kt` | Unverified in this report |
| SettingsPage | `app/src/main/kotlin/com/example/chat/ui/settings/SettingsComponents.kt` | Unverified in this report |
| SettingsRootScreen | `app/src/main/kotlin/com/example/chat/ui/settings/SettingsRootScreen.kt` | Unverified in this report |
| SettingsScreen | `app/src/main/kotlin/com/example/chat/ui/settings/SettingsScreen.kt` | Unverified in this report |
| SettingsSearchScreen | `app/src/main/kotlin/com/example/chat/ui/settings/navigation/SettingsSearchScreen.kt` | Unverified in this report |
| SplashScreen | `app/src/main/kotlin/com/example/chat/ui/auth/SplashScreen.kt` | Unverified in this report |
| StarredMessagesScreen | `app/src/main/kotlin/com/example/chat/ui/chats/starred/StarredMessagesScreen.kt` | Unverified in this report |
| StatusSettingsScreen | `app/src/main/kotlin/com/example/chat/ui/settings/home/StatusSettingsScreen.kt` | Unverified in this report |
| StorageMediaScreen | `app/src/main/kotlin/com/example/chat/ui/settings/StorageMediaScreen.kt` | Unverified in this report |
| SystemPermissionsScreen | `app/src/main/kotlin/com/example/chat/ui/settings/permissions/SystemPermissionsScreen.kt` | Unverified in this report |
| TaskDetailScreen | `app/src/main/kotlin/com/example/chat/ui/tasks/TaskDetailScreen.kt` | Unverified in this report |
| TasksScreen | `app/src/main/kotlin/com/example/chat/ui/tasks/TasksScreen.kt` | Unverified in this report |
| TemplatesSettingsScreen | `app/src/main/kotlin/com/example/chat/ui/settings/templates/TemplatesSettingsScreen.kt` | Unverified in this report |
| ThemeEditorScreen | `app/src/main/kotlin/com/example/chat/ui/settings/theme/ThemeEditorScreen.kt` | Unverified in this report |
| ThemeStudioScreen | `app/src/main/kotlin/com/example/chat/ui/settings/studio/ThemeStudioScreen.kt` | Unverified in this report |
| UniversalAppearanceScreen | `app/src/main/kotlin/com/example/chat/ui/settings/appearance/UniversalAppearanceScreen.kt` | Unverified in this report |
| UniversalChatyIconsScreen | `app/src/main/kotlin/com/example/chat/ui/settings/universal/UniversalChatyIconsScreen.kt` | Unverified in this report |
| UniversalColorsScreen | `app/src/main/kotlin/com/example/chat/ui/settings/universal/UniversalColorsScreen.kt` | Unverified in this report |
| UniversalGeneralSettingsScreen | `app/src/main/kotlin/com/example/chat/ui/settings/UniversalGeneralSettingsScreen.kt` | Unverified in this report |
| UniversalScreen | `app/src/main/kotlin/com/example/chat/ui/settings/universal/UniversalScreen.kt` | Unverified in this report |
| UniversalStylesScreen | `app/src/main/kotlin/com/example/chat/ui/settings/universal/UniversalStylesScreen.kt` | Unverified in this report |
| UpdatesScreen | `app/src/main/kotlin/com/example/chat/ui/updates/UpdatesScreen.kt` | Unverified in this report |
| WelcomeScreen | `app/src/main/kotlin/com/example/chat/ui/auth/WelcomeScreen.kt` | Unverified in this report |
| expandToFullScreen | `app/src/main/kotlin/com/example/chat/ui/calls/CallPresentation.kt` | Unverified in this report |
| showFullScreen | `app/src/main/kotlin/com/example/chat/ui/calls/CallPresentation.kt` | Unverified in this report |

Each screen: real entry/back, loading/empty/error/content, permissions, theme, narrow/landscape,200% text, keyboard, long/RTL data, setting apply/reopen/recreate/relaunch.
