# GBWhatsApp Reference APK — Screen and Feature Inventory

## Scope and confidence

Reference file supplied in this conversation: `GBWhatsApp_P_com.gbwhatsapp_V_v2.26.28.78.apk`.

This is a **static APK inventory**, not a device-driven walkthrough. The archive contains 12 DEX files, 5,678 layout resources, 8,927 XML resources, 10,161 drawable resources, and 273 asset files. These are resource counts, not screen counts. A layout resource may be a row, dialog, widget, or partial screen. Some labels and feature paths are inferred from resource/code strings. Exact settings labels, visual layouts, toggles, and navigation order still need confirmation by running the APK on a test device and recording every screen. Do not treat every row below as verified enabled behavior.

The objective is to reproduce legitimate user-facing feature categories in Chaty with original UI/components and safe backend implementations—not copy WhatsApp/GBWhatsApp proprietary assets, branding, private code, or security bypasses.

## Screen and feature inventory

| # | Screen / area | Features and flows to inspect | Settings/template surfaces | Chaty implementation target | Evidence / validation |
|---:|---|---|---|---|---|
| 1 | Launch / splash | Cold launch, initialization, restore previous session, loading/error/retry | Splash branding, supported motion | App bootstrap and route guard | Device capture needed |
| 2 | Registration / sign-in | Phone/account onboarding, verification, session restore, sign out | Account/security preferences | Existing Supabase auth flow; don't copy WhatsApp auth backend | Device capture + backend contract |
| 3 | Main home / tab shell | Main tabs, tab switching, unread counts, overflow/actions, back behavior | Tab order/style, labels/icons, header, density | Global shell driven by template profile | Layout/resource evidence; runtime confirmation |
| 4 | Chats list | Search, unread/read state, last message, timestamp, pin, mute, archive, swipe/context actions | List density, avatar, badge, preview style | Shared chat list item + repository-backed actions | DEX identifiers include thread/unseen-message data; test actions |
| 5 | Individual chat | Message list, date dividers, scroll-to-latest, unread marker, pagination | Wallpaper, bubble shape/color, spacing, timestamp/tick styles | Shared message timeline | Message resources/DB strings; device walkthrough |
| 6 | Composer / keyboard | Text, emoji, send, multiline, attachment/camera/mic actions | Composer layout, icon/shape, toolbar arrangement | Shared composer component | Device walkthrough |
| 7 | Message context menu | Reply, forward, copy, edit, delete, star/pin, reactions, select/multi-select | Context menu density/shape where configurable | Action policy + message repository | Validate each action against Chaty backend support |
| 8 | Reply / quoted message | Quote preview, jump to source, threaded replies where available | Quote bar, typography, colors | Reusable reply preview | Runtime confirmation |
| 9 | Media and documents | Image/video/document send, preview, download/open/share, progress/failure | Gallery grid, media preview, document row | Attachment service + platform pickers | Resource evidence; test permissions and failures |
| 10 | Voice messages | Record, cancel, send, playback, seek, speed/waveform where present | Recording strip, waveform and controls | Native recording/player lifecycle | Device walkthrough on Android and iOS |
| 11 | Camera / gallery picker | Capture/select media, preview, confirm/cancel | Picker preview only where supported | Platform picker abstraction | Device walkthrough; permissions denied/retry |
| 12 | Contact / profile details | Avatar, status/about, shared media, search, mute, block, report, contact actions | Profile header, avatar and action layout | Profile screen + authorization-backed actions | Runtime confirmation |
| 13 | Group creation / details | Create group, choose participants, title/photo, members, admins, invite/link, description | Group header, member list rows | Group repository/RLS and role rules | Verify every action and permission |
| 14 | Calls tab / history | Incoming/outgoing/missed records, voice/video distinction, return call | Call row and icon/badge style | Existing call history service | DEX contains call-log schema references; runtime confirmation |
| 15 | Voice call | Ringing, connect, mute, speaker/audio route, end, reconnect/error | Call screen controls and layout | WebRTC/call signaling lifecycle | Must test real devices and permissions |
| 16 | Video call | Camera permission, preview, switch camera, mute, end, background/PiP where supported | Call controls and overlays | WebRTC + native lifecycle | Runtime and device test |
| 17 | Status / updates list | Recent/viewed status rings, mute/hide, create status | Ring color/style, card density | Status service and status list | Resource/feature inference; verify exact app behavior |
| 18 | Status viewer / composer | View photos/videos/text, progress, reply/react, post, expiry, audience | Viewer controls, status rings, composer | Server-backed status lifecycle | Verify upload/view tracking and privacy |
| 19 | Broadcasts / channels / communities | Discover/create/manage where present in this build | List/card templates | Add only if actual app scope and backend contracts support it | DEX contains broadcast/channel-related terms; exact screens unverified |
| 20 | Search | Global and in-chat search, results, filters, jump to message | Result row and highlighting | Indexed/query-backed search | Runtime confirmation |
| 21 | Archived chats | View, restore/unarchive, preserve unread state | List density and row style | Chat state persisted by repository | Runtime confirmation |
| 22 | Starred messages | List, jump to source, unstar | Starred row template | Message state + repository | Runtime confirmation |
| 23 | Pinned chats/messages | Pin/unpin, limits and ordering | Pin marker and row/bubble states | Server-backed pin state where supported | Runtime confirmation |
| 24 | Privacy settings | Last seen/online, read receipts, profile/status audience, blocked users, app lock if present | Privacy controls and explanations | Only real platform/backend-supported controls | Do not infer behavior from switch labels alone |
| 25 | Security / chat lock | App lock, biometric/PIN prompts, locked-chat entry if present | Lock entry presentation | Platform secure storage + authentication | Runtime confirmation; never fake protection |
| 26 | Notifications | Message/group/call notifications, mute, tones/vibration where supported | In-app notification style and supported badges | OS notification channels and preferences | Android/iOS behavior differs; verify separately |
| 27 | Chat settings | Media visibility, enter-to-send, wallpaper, backup-related options if present | Chat-specific global defaults | Persistent settings model + migration | Runtime confirmation |
| 28 | Appearance / themes | Theme mode, colors, wallpaper, fonts, icon/navigation options if present | Global and per-chat appearance | Chaty template editor with preview/apply/reset | Must distinguish actual APK options from Chaty-only enhancements |
| 29 | Storage / network / data | Storage usage, media auto-download, network usage and data saver if present | Media/download behavior | Download policy and cache controls | Runtime confirmation |
| 30 | Account / devices | Profile, linked devices, account changes, backup/restore options if present | Security/session UI | Existing auth + device enrollment/revocation | Never implement UI-only revocation |
| 31 | Help / about | Help, contact, terms, privacy policy, version | About rows | Chaty help/about screens with correct destinations | Runtime confirmation |
| 32 | Dialogs / sheets / pickers | Confirmations, overflow menus, attachment sheet, emoji/sticker/GIF pickers | Radius, elevation, typography, spacing | Shared modal/sheet primitives | Layout resources are not equivalent to unique screens |
| 33 | Error / empty / offline states | Empty lists, no connection, failed upload, retry, blocked permission, expired session | Consistent error and skeleton treatments | Shared state components and recovery logic | Must be intentionally tested; not proven by resource existence |
| 34 | Accessibility / lifecycle | Large text, screen reader, keyboard, rotate, background/foreground, process death | Text scaling, reduced motion, tap targets | Responsive Flutter layouts and lifecycle-safe controllers | Android and iOS test matrix required |

## What static extraction can and cannot prove

- **Observed archive facts:** 12 DEX files and the resource counts stated above; DEX strings contain database/query identifiers for message threads, unread counts, status, broadcast, and call-log data.
- **Not yet verified:** exact screen count, every visible setting label, exact order of settings, which GBWhatsApp-specific toggles are active, and whether each option works on the supplied APK version.
- A definitive visual inventory requires installing this APK only in an isolated test environment, navigating each screen, recording screenshots and taps, and noting permission/network requirements. No WhatsApp account, personal messages, or production credentials should be used for this inspection.

## Required next-step workflow

1. **Capture and label the APK screens** in a test device/emulator. Record each screen, entry path, controls, states, dialogs, and back behavior. Keep screenshots as reference artifacts with numbered IDs.
2. **Build a control-by-control matrix**: screen, control, default value, action, persisted key, backend/API/RPC, permission, failure state, test case, and Chaty equivalent.
3. **Map each flow to existing Chaty code** before implementation. Reuse current repositories/services and the existing MLS/WebRTC design; do not create duplicate data sources.
4. **Classify parity items** as existing-and-working, exists-but-incomplete, missing, unsupported by platform, or intentionally excluded for privacy/security.
5. **Implement in vertical slices** (UI + state + backend + tests), starting with global template architecture and known correctness/security gaps.
6. **Verify Android and iOS** for all changed flows, run format/analyze/tests, validate Supabase migrations/RLS and production security preflight, and record the results.
7. **Do not declare complete** based on a screen rendering alone. Each item must pass persistence, backend authorization, offline/error, lifecycle, accessibility, and regression tests where applicable.


---

# Detailed APK resource-derived settings and component inventory

## Evidence labels

- **Resource-backed** means a named resource or preference key was found in the supplied APK archive. It proves the resource exists, not that a toggle is enabled, visible in this exact build, or works.
- **Flow to verify** means the expected user interaction must be confirmed by launching the APK and tapping through it.
- **Chaty target** means the equivalent should be implemented only after checking existing Chaty code and backend contracts.
- The APK contains explicit GB/YO customization resources such as `yo_home_header.xml`, `yo_home_row.xml`, `yo_home_status.xml`, `yo_convo_actionbar.xml`, `yo_convo_bubbleticks.xml`, `yo_convo_entry.xml`, `yo_convo_more.xml`, `yo_convo_picsinchat.xml`, `yo_privacy.xml`, `yo_universal_colors.xml`, `yo_universal_style.xml`, `yo_media.xml`, and `yo_widget_style.xml`. These names are stronger evidence of customization categories than generic layout counts.

## A. GB/YO custom-settings navigation map

| ID | Settings screen / category | Resource evidence | Subscreens / controls evidenced by resource names | Flow to capture in running APK | Chaty target / state owner |
|---|---|---|---|---|---|
| GB-001 | Main GB/YO settings hub | `yo_settings.xml`, `yo_settings_prefsview.xml` | Category list, preference rows, navigation into customization areas | Open app overflow/settings and identify exact entry label, icon, order, scroll and all destinations | Settings index; typed settings routes |
| GB-002 | Universal settings | `yo_universal_settings.xml` | Checkbox/list preferences; `disable_chatswipeV2`, `multiChats` | Capture every row, default, explanation, dependent controls and persistence | Global behavior preferences; only supported controls |
| GB-003 | Universal colors | `yo_universal_colors.xml` | `HomeBarText`, `ModConBackColor`, `ModConPickColor`, `ModDarkConPickColor`, `ModDarkConPickColorNav`, `list_bg_color` | Open each color picker; record light/dark scope, alpha, reset and preview behavior | Semantic theme tokens with light/dark palettes |
| GB-004 | Universal style | `yo_universal_style.xml` | `font`, `load_customfont`, `yo_nicon_color`, `yo_nicons`; image/font list preferences | Capture font list, icon style, custom font import validation and fallback | Typography/icon profile; safe font allowlist/import |
| GB-005 | Home header | `yo_home_header.xml`, `yo_settings_homeheader.xml` | `ui_home_styleV3`, `home_stories_style`, `home_stories_key`, `key_carousel_view`, `tabadgeBKColor`, `tabadgeTextColor`, `yo_multi_account_menu`, `yo_want_toolbar_cam`, night/ghost/airplane toggles, hidden-chat access key | Record style selector, header elements, tab/badge controls, switches and any permission/security implications | Header template, tab shell, badge token; privacy/security toggles reviewed separately |
| GB-006 | Home rows / chat list | `yo_home_row.xml`, `yo_settings_homerows.xml` | `HomeCounterBK`, `HomeCounterText`, `ModConTextColor`, `ModContactNameColor`, `ModHomeMentionIconColor`, `ModHomeMentionIndBackground`, `ModOnlineColor`, `ModlastseenColor`, `arch_chats_top`, `key_mas_hide_archive_home`, `onlineDotchat`, `onlineDotchatColor`, `onlinechat` | Capture row preview, avatar/presence indicators, archive position/visibility and counter colors | Chat row template and presence/unread states |
| GB-007 | Home FAB | `yo_home_fab.xml`, `yo_settings_homefab.xml` | `ModFabNormalColor`, `ModFabPressedColor`, `ModFabTextColor` | Identify which floating action is affected, icon/action, shape, pressed state | Floating action style, but actions remain route-driven |
| GB-008 | Home status tabs / status row | `yo_home_status.xml`, `yo_settings_homestatus.xml` | `SeenColor`, `UnSeenColor`, `home_stories_style`, `key_status_ui`, `key_name_stories`, `key_with_thumb`, `status_reaction`, `status_wantsendconfirmation`, `enable_statuspage_extras`, status bar background/text, status audio/media keys | Capture status tabs, seen/unseen rings, reaction and confirmation controls, layout styles | Status list/viewer template; status actions backed by status service |
| GB-009 | Call list appearance | `yo_home_calls.xml` | `ModCallsBackground`, `ModCallsTextColor`, `ModCallsIconColors` | Capture call-row color options and preview | Call-history row theme |
| GB-010 | Conversation action bar | `yo_convo_actionbar.xml`, `yo_settings_convoactionbar.xml` | `Conv_call_btn`, `ModChatColor`, `ModChatGStatusB`, `ModChatGStatusT`, `statuschat` | Capture one-to-one/group header, call/video buttons, title/status styling | Conversation header template |
| GB-011 | Conversation bubbles and ticks | `yo_convo_bubbleticks.xml`, `yo_settings_convobubbleticks.xml` | `bubble_style`, `ModChatBubbleText`, `ModChatBubbleTextLeft`, `ModChatLeftBubble`, `ModChatRightBubble`, `date_left_color`, `date_right_color`, `tick_style`, `rvkdmsg_icon_color` | Capture outgoing/incoming bubble variants, color/shape controls, timestamp/date colors, tick previews | Message bubble and delivery/read-state templates; preserve true receipt semantics |
| GB-012 | Conversation composer / entry | `yo_convo_entry.xml`, `yo_settings_convoentry.xml` | `BGColor`, `ConvoEntry`, `ModChaSendBKColor`, `ModChaSendColor`, `ModChaSendColor`, `ModChatBtnColor`, `ModChatEmojiColor`, `ModChatEntry`, `ModChatTextColor` | Capture composer background, text, emoji, send button, multiline/attachment behavior | Shared composer template; sending state remains controller-owned |
| GB-013 | Conversation additional options | `yo_convo_more.xml` | `emojipopup_header`, `ModChatBubbleHyperlinks`, `date_divider_color_picker`, `date_bubble_color_picker`, `participant_name_color_picker`, `seekbar_color_chat_picker`, `btn_voice_color_chat_picker` | Capture each preview/control and which UI component it affects | Shared date divider, link style, group participant name, audio controls |
| GB-014 | Pictures in chat | `yo_convo_picsinchat.xml`, `yo_settings_convo.xml` | `chat_contactpicV2`, `chat_mypicV2`, `pic_chat_size_pickerV2` | Capture show/hide contact/self images, sizing and group vs direct-chat behavior | Avatar visibility and size preference |
| GB-015 | Media preferences | `yo_media.xml`, `yo_settings_convo.xml` | `yohide_ingifs`, `yohide_inimages`, `yohide_invideos`, `yohide_mediashow` | Determine whether controls hide media in UI, prevent download, or change visibility only; test each independently | Separate display visibility from download/storage policy |
| GB-016 | Privacy / security options | `yo_privacy.xml`, `yo_settings_secprivacy.xml`, `activity_yocalls_privacy.xml` | `yoHideSeen`, `yoHideStatViewV2`, `yoBlueOnReply`, `yoAntiRevoke`, `yoAntiRevokeStatus`, `yoCallsPrivacy`, `yoCustomPrivList`, `disappearing_message_key`, `key_chat_editview`, `key_show_deltime`, `masdeletionofeveryone`, `Saleh_HidePrivacy` | Record each label and explain what the APK claims it changes; do not assume server-side behavior from switch state | Only implement supported privacy controls with explicit server/platform semantics; no fake privacy guarantees |
| GB-017 | Widget style | `yo_widget_style.xml`, `yo_settings_yowawidget.xml` | `ModWdgBKColor`, `ModWdgTitleColor`, `ModWdgStatusColor` | Capture widget preview and Android launcher widget behavior | Optional Android widget styling; iOS equivalent may not exist |
| GB-018 | Themes / theme import area | `yo_settings_yothemes.xml` | Theme-related settings screen; exact import/export subcontrols need runtime inspection | Capture theme list, preview/apply, import/export, reset and invalid-file feedback | Chaty template gallery and validated JSON import/export |
| GB-019 | Backup and restore | `yo_settings_backuprestore.xml` | Backup/restore screen resource exists; exact operations not established from resource name alone | Capture destination, encryption, overwrite confirmation, failure/recovery behavior | Implement only with an explicit safe backup design; never export E2EE private keys casually |
| GB-020 | Updates | `yo_settings_updates.xml`, `yo_update_headerview.xml` | Update-related screen/header resources | Record update check, changelog, download/install and error paths | App version/update information only; platform installation constraints apply |
| GB-021 | About | `yo_settings_about.xml` | About screen layout | Capture version, links, author/support/legal links | Chaty about screen and real destinations |
| GB-022 | Support | `yo_settings_support.xml`, `abu_saleh_settings_support.xml` | Support page layouts | Capture help/contact actions and destinations | Support center and privacy-safe diagnostics |
| GB-023 | Notification customization | `Abo_Saleh_Not.xml` | Ringtone preference, background/color preferences, multiple toast/status notification keys | Capture each notification type and sound/vibration behavior; identify Android channel limitations | Notification preferences by category, using platform APIs |
| GB-024 | Visual effects / animations | `Abo_Saleh_Effects.xml` | `key_chat_animation`, `key_chats_listanimation`, `key_pager_animation`, image/list/seekbar preferences | Capture animation choices, duration/intensity, reduced-motion option and performance | Shared motion profile with reduced-motion override |
| GB-025 | Snow/effects overlay | `Abo_Saleh_snow_effects.xml` | `key_snow_chats`, `key_snow_home` | Determine overlay scope, CPU/battery impact and disable behavior | Optional effects only; default off, reduced-motion aware |
| GB-026 | Extra feature flags | `Abo_Saleh_Features.xml` | `key_all_white`, `key_edit_hidden_msg`, `key_hide_unsaved_num`, `key_method_endgp`, `key_noreceive_img`, `key_notreceive_msg`, `key_notsend_msg`, `key_reply_mention`, `key_send_hidden_msg` | Inspect exact labels, applicability, account/server effects, and possible abuse implications before mapping | Implement ordinary supported functionality only; exclude unsafe or deceptive message interception/hidden-send semantics |
| GB-027 | Media extra options | `Abo_Saleh_media.xml` | `abu9aleh_media_video_player`, `abusaleh_media`, `enable_fivminstatus`, `key_more_docs_send` | Verify video player selection, media options, status duration and document limits as displayed | Use platform-supported media players, upload limits and transparent constraints |
| GB-028 | Clear/reset tools | `Abo_Saleh_Clear.xml`, `Abo_Saleh_Clear.xml` | Clear-related preference screen | Record what is cleared, confirmation wording, irreversible actions and rollback | Explicit destructive-action dialogs; scoped cache/settings reset |
| GB-029 | Quick contact appearance | `abu_saleh_quick_contact_settings.xml` | `key_mas_setBackground_quick`, `key_mas_icon_quickcontact`, `key_mas_border_avatar_quick_contact`, `key_mas_setText_contact_name` | Capture quick-contact cards/icons/avatar border/text styling | Profile/contact-card template |
| GB-030 | Blocklist cleanup | `mas_unblocked_settings.xml` | `mas_key_cleanlog_blocklist` | Verify whether this is a cleanup utility and its effect on actual block state | Block/unblock must remain server-authoritative and auditable |

## B. Fine-grained setting/control inventory

Each row is a separate control or behavior to look for during the device walkthrough. A key/resource name is an internal identifier, not a guaranteed user-visible label.

| # | Control family | Resource key / evidence | User-visible behavior to verify | Expected persistence / logic | Chaty mapping |
|---:|---|---|---|---|---|
| 1 | Home layout | `ui_home_styleV3` | Selectable home/tab layout variants | Persist selected enum; invalid value falls back | `ChatyAppearanceProfile.navigationStyle` |
| 2 | Story tab layout | `home_stories_style` | Select status/story presentation style | Persist and redraw home/status tabs | `statusListStyle` |
| 3 | Story visibility | `home_stories_key` | Enable/disable story area if shown | Confirm actual scope; do not suppress backend events | `showUpdatesTab` only if product supports it |
| 4 | Carousel | `key_carousel_view` | Change carousel layout/presentation | Persist layout variant | `homeHeaderLayout` |
| 5 | Badge background | `tabadgeBKColor` | Unread badge background color | Semantic color token | `unreadBadgeBackground` |
| 6 | Badge text | `tabadgeTextColor` | Unread badge text color | Semantic color token with contrast check | `unreadBadgeForeground` |
| 7 | Multi-account entry | `yo_multi_account_menu` | Show account switch entry if supported | Account isolation, independent session state | Explicit account switcher only if multi-account auth is supported |
| 8 | Camera shortcut | `yo_want_toolbar_cam` | Toggle home toolbar camera shortcut | Persist visibility; route to authorized picker/camera | `showCameraShortcut` |
| 9 | Night mode | `yo_want_nightmode` | Enable app night/dark appearance | Respect system mode and screen-level overrides | `themeMode` |
| 10 | Airplane/ghost mode | `yo_want_airplanemode`, `yo_want_ghostmode` | Determine exact claimed behavior; may be mod-specific | Never fake network state or misrepresent delivery/privacy | No direct parity toggle without a safe, truthful definition |
| 11 | Hidden chat access | `disable_hiddenchat_access` | Determine access affordance and lock flow | Must use real authentication and secure state | Secure hidden/locked chats only if correctly designed |
| 12 | Hide archive | `key_mas_hide_archive_home` | Hide or reposition archive row | Preserve archived conversation data and access | `archiveVisibility` |
| 13 | Archive top | `arch_chats_top` | Move archive row to top | Persist list ordering | `archivePosition` |
| 14 | Online dot | `onlineDotchat` | Show presence indicator | Driven by actual presence data | `showPresenceDot` |
| 15 | Online dot color | `onlineDotchatColor` | Change presence dot color | Theme token | `presenceColor` |
| 16 | Online text | `onlinechat` | Show online text indicator | Must use privacy-permitted live presence | `showOnlineLabel` |
| 17 | Last-seen color | `ModlastseenColor` | Change last-seen text color | Theme token; data visibility is separate | `lastSeenColor` |
| 18 | Contact name color | `ModContactNameColor` | Change contact-name color | Theme token | `contactNameColor` |
| 19 | Mention icon | `ModHomeMentionIconColor` | Change mention indicator color | Theme token | `mentionIconColor` |
| 20 | Mention background | `ModHomeMentionIndBackground` | Change mention badge background | Theme token and contrast validation | `mentionBadgeBackground` |
| 21 | Unread counter background | `HomeCounterBK` | Change unread counter background | Theme token | `unreadCounterBackground` |
| 22 | Unread counter text | `HomeCounterText` | Change unread counter text | Theme token | `unreadCounterForeground` |
| 23 | FAB normal color | `ModFabNormalColor` | Change floating button color | Theme token | `fabBackground` |
| 24 | FAB pressed color | `ModFabPressedColor` | Change pressed state | Theme token/state style | `fabPressedBackground` |
| 25 | FAB label/icon | `ModFabTextColor` | Change floating action label/icon color | Theme token | `fabForeground` |
| 26 | Seen status color | `SeenColor` | Change seen-status ring color | Theme token only | `statusSeenColor` |
| 27 | Unseen status color | `UnSeenColor` | Change unseen-status ring color | Theme token only | `statusUnseenColor` |
| 28 | Status UI | `key_status_ui` | Select status-list layout | Persist layout enum | `statusLayoutStyle` |
| 29 | Status name | `key_name_stories` | Show/hide or style status names | Confirm exact semantics | `showStatusNames` |
| 30 | Status thumbnail | `key_with_thumb` | Show/hide thumbnail | Display setting only; doesn't delete media | `showStatusThumbnail` |
| 31 | Status reaction | `status_reaction` | Enable/display status reaction action | Backend must store reaction if supported | `statusReactionsEnabled` |
| 32 | Status send confirmation | `status_wantsendconfirmation` | Confirm before posting status | Persist and show confirmation | `confirmStatusPublish` |
| 33 | Status extras | `enable_statuspage_extras` | Show extra status actions/controls | Each action must have real implementation | Status viewer actions |
| 34 | Status audio | `abu9aleh_status_audio` | Status audio/media options | Platform playback lifecycle | Status audio controls |
| 35 | Status media | `abu_saleh_status_and_photo` | Status photo/media behavior | Media service and validation | Status composer media options |
| 36 | Five-minute status | `enable_fivminstatus` | Exact time/length semantics to verify | Respect service/platform limits; never claim longer expiry if backend doesn't support it | Status duration policy only after backend support |
| 37 | Status bar background | `statuses_bar_bg_picker` | Change status bar background | Theme token | `statusBarBackground` |
| 38 | Status bar text | `statuses_bar_text_picker` | Change status bar text color | Contrast validation | `statusBarForeground` |
| 39 | Call background | `ModCallsBackground` | Change call row background | Theme token | `callRowBackground` |
| 40 | Call text | `ModCallsTextColor` | Change call row text | Theme token | `callRowForeground` |
| 41 | Call icon | `ModCallsIconColors` | Change call icons | Theme token; icon semantics unchanged | `callIconColor` |
| 42 | Conversation header color | `ModChatColor` | Change conversation toolbar background | Theme token | `conversationHeaderBackground` |
| 43 | Group status bar | `ModChatGStatusB` | Group conversation status/header background | Theme token | `groupHeaderStatusBackground` |
| 44 | Group status text | `ModChatGStatusT` | Group status/header text | Theme token | `groupHeaderStatusForeground` |
| 45 | Call button | `Conv_call_btn` | Show/style call button in conversation header | Must route to valid voice/video call flow | `showConversationCallAction` |
| 46 | Bubble style | `bubble_style` | Choose bubble variant | Persist style enum | `messageBubbleStyle` |
| 47 | Incoming bubble | `ModChatLeftBubble` | Change incoming bubble appearance | Theme tokens and shape | `incomingBubble` |
| 48 | Outgoing bubble | `ModChatRightBubble` | Change outgoing bubble appearance | Theme tokens and shape | `outgoingBubble` |
| 49 | Incoming bubble text | `ModChatBubbleTextLeft` | Change incoming text color | Contrast validation | `incomingTextColor` |
| 50 | Outgoing bubble text | `ModChatBubbleText` | Change outgoing text color | Contrast validation | `outgoingTextColor` |
| 51 | Date left color | `date_left_color` | Change date/divider color for one side if applicable | Theme token | Date-divider style |
| 52 | Date right color | `date_right_color` | Change date/divider color for other side if applicable | Theme token | Date-divider style |
| 53 | Tick style | `tick_style` | Select sent/delivered/read tick appearance | Style may change; actual state must not be fabricated | `receiptIconStyle` |
| 54 | Revoked/deleted icon color | `rvkdmsg_icon_color` | Style deleted/revoked message marker | Theme token | `deletedMessageIndicatorColor` |
| 55 | Composer background | `BGColor`, `ModChatEntry` | Change entry field background | Theme token | `composerBackground` |
| 56 | Composer text | `ModChatTextColor` | Change typed text color | Theme token | `composerForeground` |
| 57 | Send button background | `ModChaSendBKColor` | Change send button background | Theme token | `sendButtonBackground` |
| 58 | Send button foreground | `ModChaSendColor` | Change send icon/text color | Theme token | `sendButtonForeground` |
| 59 | Composer action button | `ModChatBtnColor` | Change action button color | Theme token | `composerActionColor` |
| 60 | Emoji button | `ModChatEmojiColor` | Change emoji icon color | Theme token | `emojiButtonColor` |
| 61 | Emoji popup header | `emojipopup_header` | Style emoji popup header | Shared popup theme | `emojiPickerHeader` |
| 62 | Hyperlink color | `ModChatBubbleHyperlinks` | Change link color in bubbles | Theme token with contrast/underline accessibility | `messageLinkStyle` |
| 63 | Date divider | `date_divider_color_picker` | Change divider text/background | Theme tokens | `dateDividerStyle` |
| 64 | Date bubble | `date_bubble_color_picker` | Change date bubble appearance | Theme tokens | `datePillStyle` |
| 65 | Participant name | `participant_name_color_picker` | Change group sender name color | Theme token; maintain identity distinction | `participantNamePalette` |
| 66 | Audio seek bar | `seekbar_color_chat_picker` | Change voice/audio progress color | Theme token | `audioProgressColor` |
| 67 | Voice button | `btn_voice_color_chat_picker` | Change voice recording button color | Theme token; recording state remains real | `voiceButtonStyle` |
| 68 | Contact photo in chat | `chat_contactpicV2` | Show/hide other person's avatar | Display preference | `showContactAvatarInChat` |
| 69 | Own photo in chat | `chat_mypicV2` | Show/hide own avatar | Display preference | `showOwnAvatarInChat` |
| 70 | Avatar size | `pic_chat_size_pickerV2` | Adjust avatar size | Bounded numeric setting | `chatAvatarSize` |
| 71 | GIF visibility | `yohide_ingifs` | Hide/show GIFs in conversation UI | Distinguish hide from block download | `showGifsInChat` |
| 72 | Image visibility | `yohide_inimages` | Hide/show images in conversation UI | Do not delete message/media data | `showImagesInChat` |
| 73 | Video visibility | `yohide_invideos` | Hide/show videos in conversation UI | Do not conflate with auto-download | `showVideosInChat` |
| 74 | Media show | `yohide_mediashow` | Exact effect on gallery/media list to verify | UI visibility vs indexing policy separated | `showChatMediaInGallery` |
| 75 | Hide read/seen | `yoHideSeen` | Exact receipt/seen behavior claimed | Must obey server privacy and transport constraints; no false receipt state | No toggle until semantics are defined and supported |
| 76 | Hide status view | `yoHideStatViewV2` | Exact status-view receipt behavior | Must be truthful and server-supported | No fake viewer privacy switch |
| 77 | Blue tick on reply | `yoBlueOnReply` | Exact tick display semantics | UI display is separate from actual read receipt | Receipt display option only if truthful |
| 78 | Anti-revoke message | `yoAntiRevoke` | Exact deleted-message visibility behavior | Respect other users' privacy and policy; avoid misleading claims | Consider only a local tombstone/history policy with clear disclosure |
| 79 | Anti-revoke status | `yoAntiRevokeStatus` | Exact expired/deleted status behavior | Must not bypass access controls | No bypass implementation |
| 80 | Calls privacy | `yoCallsPrivacy` | Inspect call-related privacy submenu | Server permissions and call signaling rules | Call privacy settings backed by server |
| 81 | Custom privacy list | `yoCustomPrivList` | Select people excluded/included in privacy scope | Persist membership safely and enforce on backend | Per-contact privacy rules only if backend supports |
| 82 | Disappearing messages | `disappearing_message_key` | Select message expiry duration | Backend deletion/expiry job and client cache semantics | `disappearingMessageDuration` with server support |
| 83 | Chat edit view | `key_chat_editview` | Exact edit-message UI/feature | MLS-encrypted edits; no plaintext fallback | Existing MLS edit flow to repair |
| 84 | Show delete time | `key_show_deltime` | Display deleted message timing | UI metadata only if stored truthfully | `showDeletionTimestamp` |
| 85 | Delete for everyone | `masdeletionofeveryone` | Exact delete-for-everyone behavior | Authorization, time/window policy and server state | Existing delete RPC and policy tests |
| 86 | Chat animation | `key_chat_animation` | Conversation transition animation selector | Persist enum and honor reduce-motion | `chatEntryAnimation` |
| 87 | Chat-list animation | `key_chats_listanimation` | Chat list item entrance/transition | Persist enum; avoid jank on large lists | `chatListAnimation` |
| 88 | Pager animation | `key_pager_animation` | Tab/page transition style | Persist enum | `pageTransitionStyle` |
| 89 | Snow on chats | `key_snow_chats` | Enable/disable overlay in conversation | Low-frequency optional effect, lifecycle-safe | Optional overlay setting |
| 90 | Snow on home | `key_snow_home` | Enable/disable overlay on home | Low-frequency optional effect, lifecycle-safe | Optional overlay setting |
| 91 | Font choice | `font` | Select bundled font | Validate font resource and fallback | `fontFamily` |
| 92 | Custom font | `load_customfont` | Import/select custom font file | File size/type validation; never execute content | Optional user font import |
| 93 | Notification toast | `abu_saleh_toast_status` and related keys | Customize status/notification toast appearance/content | Verify each individual key; avoid conflating in-app toast with OS notification | In-app toast style vs OS notification preferences |
| 94 | Quick-contact background | `key_mas_setBackground_quick` | Change quick-contact card background | Theme token | `quickContactBackground` |
| 95 | Quick-contact icon | `key_mas_icon_quickcontact` | Change quick-contact icon style | Icon pack/style enum | `quickContactIconStyle` |
| 96 | Quick-contact avatar border | `key_mas_border_avatar_quick_contact` | Enable/style avatar border | Theme token/shape | `quickContactAvatarBorder` |
| 97 | Quick-contact name | `key_mas_setText_contact_name` | Change contact name style | Theme token/typography | `quickContactNameStyle` |
| 98 | Widget background | `ModWdgBKColor` | Change widget background | Android launcher widget only | Android widget theme if supported |
| 99 | Widget title | `ModWdgTitleColor` | Change widget title color | Theme token | Widget title token |
| 100 | Widget status | `ModWdgStatusColor` | Change widget status color | Theme token | Widget status token |
| 101 | Clear settings/data | `Abo_Saleh_Clear.xml` | Identify exact clear operations | Confirmation, scope, irreversible-data warning | Separate reset appearance, clear cache, delete data |
| 102 | Backup/restore | `yo_settings_backuprestore.xml` | Identify backup destination and restore behavior | Integrity check, versioning, encryption, conflict policy | Secure backup feature only after threat model |
| 103 | Theme manager | `yo_settings_yothemes.xml` | Theme preview/apply/import/export/delete | Schema validation and safe rollback | Chaty template repository |
| 104 | Updates | `yo_settings_updates.xml` | Check/download changelog | Platform-safe update flow | About/update screen |
| 105 | Support | `yo_settings_support.xml` | Open support/contact | Safe destination and optional diagnostics consent | Help center |
| 106 | About | `yo_settings_about.xml` | Version/build information | Correct app version and legal links | Chaty About |
| 107 | Media player | `abu9aleh_media_video_player` | Choose or configure player | Handle unsupported codecs and external intents | Platform media player abstraction |
| 108 | Document send limit | `key_more_docs_send` | Determine allowed file types/size | Server-side validation and progress/error UI | Attachment policy |
| 109 | Hide unsaved numbers | `key_hide_unsaved_num` | Determine where unknown numbers are hidden | UI filtering only; don't corrupt chat history | Contact/chat list filter if requested |
| 110 | Reply mention | `key_reply_mention` | Determine mention behavior in replies | Validate group mention authorization and notifications | Reply/mention composer logic |
| 111 | Blocklist cleanup | `mas_key_cleanlog_blocklist` | Exact cleanup effect | No accidental unblock/data deletion | Explicit block-list maintenance only |

## C. App architecture and component interaction map

The reference APK's resource names indicate UI preference screens and Android-specific presentation hooks. They do not disclose a complete trustworthy architecture by themselves. The following is the architecture Chaty should use when mapping the verified flows.

| Layer | Components / responsibilities | Data passed to next layer | Required guardrails |
|---|---|---|---|
| Navigation | App router, shell tabs, route guards, modal routes, deep links | Route + typed arguments | Back-stack correctness; auth gate; no duplicate routes |
| Screen/presentation | Chat list, conversation, composer, media viewer, calls, status, profile, settings | User intent + view state | Loading/empty/error/offline states; responsive layout |
| Shared UI primitives | Header, bottom nav, bubble, ticks, avatar, row, badge, sheet, dialog, skeleton, theme preview | Typed theme tokens + domain state | No business logic hidden in widgets |
| Template profile | Theme mode, colors, typography, shell styles, bubbles, composer, media, motion | Validated versioned appearance profile | Defaults, migration, reset, contrast and accessibility |
| Settings controller | Load/edit/preview/apply/reset/import/export | Profile patches and validation results | Avoid partial writes; notify only after state is coherent |
| Local persistence | SharedPreferences/secure storage/cache according to sensitivity | Versioned preferences and safe local state | No tokens/private keys in exported templates |
| Domain controllers | Chat, messages, contacts, groups, status, calls, devices | Commands and observable state | Request cancellation, duplicate prevention, lifecycle cleanup |
| Repository layer | Conversations, messages, media, profiles, statuses, call history, devices | Typed domain models | Single source of truth; pagination; optimistic rollback |
| Supabase services | Auth, RPC, Realtime, Storage, migrations, RLS | Authenticated request/result | Enforce authorization server-side; don't trust client-only checks |
| MLS E2EE service | Device identity, group state, encryption/decryption, epoch synchronization | Opaque ciphertext + MLS metadata | Fail closed; never plaintext fallback for MLS messages |
| WebRTC/native services | Audio/video tracks, ICE, permissions, audio route, lifecycle | Call state/events | Android/iOS lifecycle, permission-denied and reconnect tests |
| OS integration | Notifications, file/camera picker, recording, share sheet, PiP | Platform results and permissions | Feature detection; permission rationale; no unsupported promises |
| Testing/observability | Unit, widget, integration, golden, migration, security and device tests | Test reports and actionable diagnostics | Redact private message content and credentials |

## D. End-to-end flow inventory

| Flow ID | User journey | UI steps | Controller/repository work | Backend/platform work | Failure/recovery tests |
|---|---|---|---|---|---|
| F-01 | Launch and restore | Splash → session check → home or sign-in | Bootstrap, session hydration | Supabase auth restore | Expired session, no network, rapid account switch |
| F-02 | Change template | Settings → Appearance → select profile → preview → apply | Validate and persist profile; notify shared theme | Optional cloud sync with conflict handling | Invalid profile, restart, partial write, revert |
| F-03 | Change bubble style | Appearance → Chats → bubble preview → apply | Update incoming/outgoing style tokens | No backend call needed for local styling | Long text, media, reply, dark mode, large text |
| F-04 | Change navigation | Appearance → Navigation → preview tab variants | Update shell configuration | No backend call needed | All routes, keyboard, small device, rotation |
| F-05 | Send text | Open chat → type → send | Optimistic message, outbox/idempotency | MLS encrypt + send RPC + Realtime | Offline, duplicate retry, epoch mismatch |
| F-06 | Edit encrypted message | Own message → edit → save | Encrypt edited payload; reconcile optimistic UI | `edit_mls_message_v1` with current group/device/epoch | Unauthorized edit, stale epoch, RPC failure, no plaintext update |
| F-07 | Delete message | Message menu → delete scope → confirm | Update local timeline | Delete RPC, authorization, recipient state | Wrong owner, offline, repeated delete |
| F-08 | Reply/react/forward | Context menu → action → send/commit | Compose relation or reaction | Existing RPC/permissions | Missing source, stale data, unauthorized operation |
| F-09 | Attach media | Attachment tray → picker/camera → preview → send | Validate type/size, upload progress | Storage authorization and encrypted payload path as supported | Permission denial, cancellation, upload failure |
| F-10 | Voice note | Hold/tap mic → record → preview/cancel → send | Recorder lifecycle, audio focus, file cleanup | Upload/encryption and message send | Interruptions, deny permission, background, Bluetooth |
| F-11 | Status publish | Updates → create → media/text → audience → publish | Validate status model and upload | Status write, audience/RLS, expiry | Network drop, invalid media, unauthorized audience |
| F-12 | Status viewed | Open status → advance/exit | Track view once and render progress | Persist viewer event when supported | Duplicate event, expiry, offline replay |
| F-13 | Voice call | Chat/profile → call → ringing → connected → end | Call state machine and cleanup | Signaling, WebRTC, call history | Busy, timeout, network switch, denied mic |
| F-14 | Video call | Call → grant camera → connect → controls → end | Video tracks, camera switching, lifecycle | Signaling/ICE and call record | Camera denial, rotate/background, PiP unsupported |
| F-15 | Linked device revoke | Settings → devices → choose → confirm | Update device state after server success | Server revocation and key/device policy | Offline, RPC failure, stale device list |
| F-16 | Archive/pin/mute | List row menu/swipe → action | Persist view state and reconcile list | Repository/RPC where server-backed | App restart, multi-device sync |
| F-17 | Privacy change | Settings → privacy → change → explanation | Validate supported option | Backend enforcement or platform API | Other device, unauthorized bypass, stale cache |
| F-18 | Import template | Settings → templates → select file → validate → preview → apply | Schema/version validation and atomic persistence | No private keys or account data | Malformed/oversized file, unknown keys, rollback |
| F-19 | Reset appearance | Settings → reset category/all → confirm | Restore defaults and notify all consumers | Local preference update | Ensure category reset preserves unrelated settings |
| F-20 | Account sign-out/switch | Settings → account → sign out/switch | Cancel requests and clear user-bound in-memory state | Auth sign-out; secure session handling | Old events cannot populate new account's UI |

## E. Screen-by-screen visual capture sheet

Use one row per actual screen instance, not one row per generic category. Duplicate this row for every variant, submenu, dialog, empty/error state, and platform-specific version discovered during the APK walkthrough.

| Capture ID | Parent screen | Exact visible title | Entry action | Visible components in order | Interactive controls | State before action | Action taken | State after action | Dialog/sheet | Persistence key or backend request | Screenshot/reference ID | Chaty file/component mapping | Test ID | Confidence |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| CAP-001 | GB settings hub | To be captured | Open overflow/settings | To be recorded from device | Every row and overflow action | Fresh install / known prefs | Tap each row | Record destination | Record if shown | Identify from code/runtime | Pending | Pending | Pending | Resource-backed only |

## F. Required evidence before claiming "all settings extracted"

| Gate | Required artifact | Pass condition |
|---|---|---|
| G-01 | Full app navigation tree | Every visible destination has an ID, parent, and entry action |
| G-02 | GB/YO settings screenshots | Every settings page and nested preference screen captured |
| G-03 | Control register | Every switch, slider, color picker, list picker, button, import/export and reset action documented |
| G-04 | Before/after evidence | Each mutable control has a recorded effect or a documented no-op/unsupported result |
| G-05 | Persistence evidence | Values survive app restart when expected; defaults and reset behavior recorded |
| G-06 | Permission/network matrix | Permission denial, offline state, timeout and retry behavior tested |
| G-07 | Chaty source map | Each reference behavior mapped to existing/new screen, controller, repository, RPC and migration |
| G-08 | Security review | No insecure privacy bypass, plaintext E2EE downgrade, or UI-only authorization |
| G-09 | Android/iOS compatibility | Platform-specific capabilities and unsupported items explicitly marked |
| G-10 | Automated validation | Tests cover all implemented behavior and regression-critical existing flows |

## G. Interpretation notes

1. This APK is an Android package. Android-specific widgets, launchers, notification channels, and settings do not automatically translate to iOS.
2. GBWhatsApp-specific privacy/mod features may rely on unsupported or unsafe behavior. Feature parity must not imply bypassing encryption, user privacy, platform restrictions, or server authorization.
3. Do not copy APK classes, proprietary artwork, logos, strings wholesale, or decompile/repackage its code into Chaty. Use the APK as a functional/UI reference and implement original Flutter components.
4. The exact on-screen labels and screen ordering must be filled from a running-device walkthrough. Resource IDs alone cannot establish the complete screen inventory.
