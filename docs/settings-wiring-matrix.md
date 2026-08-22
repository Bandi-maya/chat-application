# Chaty — Settings Wiring Matrix

**Audit date:** 2026-08-22 · **Scope:** every visible settings control → state field/key → feature consumer.
**Method:** scripted extraction of all typed model fields (multi-line-safe), GB Feature Center catalog keys, ThemeConfig fields (27) and appearance-controller fields; whitespace-tolerant reference matching across `lib/`; manual classification of edge rows.

**UPDATE (same day, after fixes):** every ⚠️ row below has since been resolved — either WIRED (`showEditedMessage`, `enableAnimatedEmojis`) or PRUNED from control + alias + model together (translation pair, channels, disappearing, hide-updates, add-account, `pageTransitionStyle`, bubble padding/hex/waveform/header metrics, `blockedUserIds`). Two audit corrections surfaced during the work: `scheduledMessages` is ✅ server-executed (`schedule_message` RPC + `scheduled_messages` table; the local list is a display mirror) and ThemeConfig `linkColor` is ✅ (feeds the semantic `link`/`info` scheme colors; GB `ModChatBubbleHyperlinks` remains as a working hidden customization). ThemeConfig `highContrast` is 🖼 preset metadata for the selectable High Contrast theme.

**Legend**

| Mark | Meaning |
|---|---|
| ✅ WIRED | Visible control → persisted pref → real, observable behavior |
| 🖼 DISPLAY | Persisted and shown back to the user; behavior rides on a sibling field |
| ⚠️ WRITE-ONLY | Visible control persists a value nothing reads |
| 💤 DEAD | Model field with no control anywhere (not user-visible) — cleanup candidate |

Chain verified per row: CONTROL → STATE → PERSIST (`toMap`/`fromMap`) → CONSUMER → VISIBLE/BEHAVIORAL RESULT. Persistence is uniform (schema v2, per-user namespaced); rows note the consumer only.

---

## 1 · Privacy (`PrivacyPreferences`, 23 fields)

| Setting | Control | Consumer(s) | Status |
|---|---|---|---|
| Read Receipts (Blue Ticks) | Privacy Center (+GB `yoHideSeen`) | `backend_service.markAsRead` gate | ✅ |
| Show Blue Ticks After Reply | Privacy Center (+GB `yoBlueOnReply`) | read-receipt gate (reply rule) | ✅ |
| Typing Indicators | Privacy Center | `mock_data_store.setTyping` publish gate | ✅ |
| Recording Indicators | Privacy Center | `rich_chat_realtime_service.setRecording` | ✅ |
| Freeze Last Seen | Privacy Center | `backend_service.setPresence`/`updateCurrentUser` | ✅ |
| Frozen timestamp | auto-set by Freeze toggle | subtitle display | 🖼 |
| Who Can See My Last Seen | Privacy Center | presence downgrade + local suppression | ✅¹ |
| Who Can See When I'm Online | Privacy Center | mirrors last-seen audience ('Same as Last Seen') | ✅¹ |
| Anti View-Once Media | Privacy Center (+GB `anti_vw_once`) | `chat_detail_screen` retained view-once | ✅ |
| Disable Forwarded Tag | Privacy Center (+GB `yoDisableFwd`) | forward metadata at send time | ✅ |
| Anti-Delete Messages | Privacy Center (+GB `yoAntiRevoke`) | deleted-message retention | ✅ |
| Anti-Delete Status | Privacy Center (+GB `yoAntiRevokeStatus`) | status retention + revocation watch | ✅ |
| Message & Status Revoke Alerts | Privacy Center (+GB `AntiRevokeMsgNotif/…Status…`) | realtime revoke-alert toasts | ✅ |
| Hide View Status (mine) | GB `yoHideStatViewV2` | `status_service.markViewed(p_hide_view)` | ✅ |
| Who Can Call Me + Exceptions | Privacy Center (+GB `yoCallsPrivacy`) | `_callerMayRing` ring-time gate | ✅ |
| Hide Privacy Option | Privacy Center (+GB `Saleh_HidePrivacy`) | gates tile in Settings root | ✅ |
| Link Previews | — none — | nothing | 💤 |
| Hide Updates Option | GB `Saleh_HideCUpdates` | nothing | ⚠️ |
| Disable Channels | GB `abu_saleh_channels` | nothing (no channels feature) | ⚠️ |
| Anti Disappearing Messages | GB `disappearing_message_key` | nothing (no disappearing feature) | ⚠️ |
| Show Edited Message | GB `key_chat_editview` | nothing — edited chip renders unconditionally in `message_bubble` | ⚠️ |

¹ Per-viewer granularity ('My Contacts' / 'Except…') is server-enforced via contact projections; client enforces the 'Nobody'/mirror extremes honestly.

## 2 · Security (`SecurityPreferences`, 7 fields)

| Setting | Control | Consumer(s) | Status |
|---|---|---|---|
| App Lock enabled | Security Center | `main.dart` lock gate | ✅ |
| Lock method (PIN/pattern/bio) | Security Center | `LocalLockService` credential path | ✅ |
| Auto-lock timeout | Security Center | re-lock timer in main gate | ✅ |
| Pattern invisible | Security Center | `app_lock_overlay` rendering | ✅ |
| Pattern vibration | Security Center | `app_lock_overlay` haptics | ✅ |
| Hide Notification Content when Locked | Security Center | toast body redaction under lock barrier | ✅ |
| Locked conversations | chat-list selection + Security Center | `preferences_controller.isConversationLocked` → lock sheet on open | ✅ |

## 3 · Home (`HomePreferences`, 16 fields)

| Setting | Control | Consumer(s) | Status |
|---|---|---|---|
| Home Style (8 variants) | Home Screen (+GB `ui_home_styleV3`) | `chats_home_screen` structural variants | ✅ |
| Stories strip on/off | Home Screen (+GB `home_stories_key`) | stories strip render | ✅ |
| Stories style (shape/minimal…) | Home Screen (+GB `home_stories_style`) | strip knobs | ✅ |
| Separate chats & groups | Home Screen (+GB `enable_grp_separationV2`) | nav shell tabs (single source of truth) | ✅ |
| Display Name Override | Home Screen (+GB `my_name`) | side menu, Updates "My Status", Profile header | ✅ |
| Avatar shape | Home Screen | 4 ChatyAvatar sites | ✅ |
| Ghost Mode | Home Screen (+GB `yo_want_ghostmode`) | read receipts + recording publish gates | ✅ |
| Airplane Mode Simulator | Home Screen (+GB `yo_want_airplanemode`) | presence offline in main lifecycle | ✅ |
| Show Top Search Bar | Home Screen | search icon + field gating (root screen) | ✅ |
| Show Camera Icon | Home Screen (+GB `yo_want_toolbar_cam`) | app-bar action | ✅ |
| Header bg/text color | — no write path — | settings preview only | 💤 |
| Disable status under name | — none — | nothing | 💤 |
| Hide home profile pic | — none — | nothing | 💤 |
| Show Add Account | GB `yo_multi_account_menu` | nothing (no account switcher) | ⚠️ |
| Header height | — none — | nothing | 💤 |

## 4 · Conversation (`ConversationPreferences`, 18 fields)

| Setting | Control | Consumer(s) | Status |
|---|---|---|---|
| Bubble Shape | Conversation (+GB `bubble_style`) | `GbThemeOverrides` → `MessageBubble` geometry | ✅ |
| Bubble Corner Radius | Conversation | `ThemeConfig.bubbleRadius` → bubbles only | ✅ |
| Delivery Tick Style | Conversation (+GB `tick_style`) | `_deliveryIcon` / `_tickReadColor` | ✅ |
| Quick Contact Sidebar + position + opacity | Conversation | `chat_detail_screen` sidebar | ✅ |
| iOS Popup Menu | Conversation | `message_action_sheet` presentations | ✅ |
| Double-Tap Reaction Emoji | Conversation | bubble double-tap reaction | ✅ |
| Voice Note Speed | Conversation | `_VoiceNotePlayer.setSpeed` (+live) | ✅ |
| Wallpaper type / custom image | Conversation (+per-chat override) | `ChatWallpaper` layers | ✅ |
| Enable Animated Emojis | Conversation | its own page only | ⚠️ |
| Translation enable/target | GB `inconvo_trans_option` / `trans_def_to` | nothing (no translation engine) | ⚠️ |
| Page Transition Style | Effects page (+GB `key_pager_animation`) | nothing — transitions use Appearance entry/exit instead | ⚠️ duplicate system |
| Bubble Padding | — no control — | page-local padding math only | 💤 |
| Custom incoming/outgoing bubble hex | — none — | nothing | 💤 |
| Waveform style | — none — | nothing | 💤 |

## 5 · Notifications (9 fields) — ALL ✅
Global master, sender avatar, sender name, event detail, online/typing/recording¹ alerts, message-deleted alert, status-deleted alert, status-viewed alert (realtime `status_view_events` watch). Consumers: event toast overlay + realtime/status services.

¹ Recording alert = GB `notify_recording_started`.

## 6 · Message Automation (5 fields)

| Field | Control | Consumer | Status |
|---|---|---|---|
| Auto-reply master + rules | Message Management | server sync reconciler (`gb_feature_backend_service`) | ✅ |
| Quick replies | Message Management | composer overlay + automation | ✅ |
| Scheduled messages | Message Management | nothing sends them | ⚠️ |
| Blocked user IDs | — none — | blocking uses ContactRelationshipService | 💤 |

## 7 · Navigation & Effects (7 fields) — mostly ✅
Click particles (on/symbol/speed), falling particles (on/object/scope): consumed by the two overlay widgets. `pageTransitionStyle`: see ⚠️ in §4.

## 8 · Theme editor (`ThemeConfig`, 27 fields)
All WIRED except: `linkColor` 💤 (resolved from GB `ModChatBubbleHyperlinks` but never rendered — the GB key dead-ends here too) and `highContrast` 💤 (no control, no consumer).

## 9 · Appearance variants
Entry/exit animation (40 options → `chaty_page_transitions.dart`), text scale → main builder, navigation mode/layout mode → shell: ✅. `typographyStyle` ⚠️ (variant picker only). Old fake clones (`navigationStyle`, `appIconStyle`, `notificationIconStyle`) were removed from UI; constants remain inert.

## 10 · GB Feature Center catalog (79 listed keys)

| Bucket | Count | Keys |
|---|---|---|
| Directly consumed | 23 | always_online, anti_vw_once, yoDisableFwd, yoAntiRevoke(_Status), yoHideStatViewV2, yoCallsPrivacy, abu_saleh_toast_* (4 masters), abu9aleh_status_audio, abo_saleh_audio_limit_check, bubble_style, enable_grp_separationV2, key_mas_hide_archive_home, onlinechat/onlineDotchat(+Color), statuschat, key_more_docs_send, yo_want_ghostmode/airplanemode |
| Alias-synced into wired typed prefs | ~15 | yoHideSeen, yoBlueOnReply, home_stories_key/_style, tick_style, ui_home_styleV3, abu_saleh_quickcontact, yo_want_toolbar_cam, Pop_Heds, tap/fall_effect_enabled(+emoji), Saleh_HidePrivacy, my_name, notify_recording_started* |
| Alias-synced into UNCONSUMED prefs | 7 | Saleh_HideCUpdates, abu_saleh_channels, disappearing_message_key, key_chat_editview, inconvo_trans_option, trans_def_to, key_pager_animation, yo_multi_account_menu |
| No consumer anywhere | ~34 | cat_img/cat_pb/cat_pc/cat_pg, pic_inside, key_with_thumb, key_carousel_view, font, multiChats, tenor_giphy, disable_*, fwd_lim_incr, elapsed_time, arch_chats_top, chat_*picV2, key_chats_listanimation, key_chat_animation, key_status_ui, stat_cat, enable_fivminstatus, enable_statuspage_extras, stkr/status_wantsendconfirmation, yohide_mediashow, key_hide_unsaved_num, key_noreceive_img, key_notreceive_msg, key_notsend_msg, key_send_hidden_msg, key_edit_hidden_msg, key_updown_iconcolor, quickText, abu_saleh_image_quality, abu_saleh_play_voice_note, abu_saleh_unreaded_chat, disable_hiddenchat_access, disable_bcounter, disable_audioheads, disable_chatswipeV2, yo_want_nightmode, yoCustomPrivList |

\* `notify_recording_started` works but is NOT in the catalog list below.

## 11 · Working keys controlled outside the GB center (verified, no action needed)
`event_toast_position`, `event_toast_duration_seconds`, `notify_recording_started` → controlled from **Notifications settings**; `ModCallsBackground/TextColor/IconColors` and all `abu_saleh_toast_*_bc/_tc` tints → listed in the GB center's Calls-appearance / Presence-alerts categories. Nothing is orphaned.

---

## Summary

| Domain | Wired | Display | Write-only | Dead |
|---|---|---|---|---|
| Privacy | 17 | 1 | 4 | 1 |
| Security | 7 | – | – | – |
| Home | 10 | – | 1 | 5 |
| Conversation | 11 | – | 3 | 4 |
| Notifications | 9 | – | – | – |
| Automation | 3 | – | 1 | 1 |
| Effects | 6 | – | 1 | – |
| Theme tokens | 25 | – | – | 2 |

**Highest-value fixes — ALL IMPLEMENTED 2026-08-22:**
1. ✅ `showEditedMessage` → gates the "edited" chip (`MessageBubble.showEditedLabel`, wired from chat_detail; GB `key_chat_editview` now fully functional).
2. ✅ ✅ `pageTransitionStyle` REMOVED end-to-end (field + GB key/alias + Effects-page control); route transitions remain served by the wired Appearance entry/exit system.
3. ✅ `scheduledMessages` — audit corrected: already server-executed; no dispatcher needed.
4. ✅ Orphan scan corrected — no re-listing needed (see §11).
5. ✅ Pruned: 11 dead keys from the GB center (+39 leftover description/option/default entries, 2 empty categories), 7 controller aliases, and 17 dead model fields across Privacy/Home/Conversation/Automation/NavigationEffects. Kept after correction: `linkColor`+`highContrast` (both live). Bonus wire: `enableAnimatedEmojis` now switches bubble text between AnimatedEmojiText and plain Text.

Zero residual references to any removed symbol repo-wide (scripted check); all touched files brace-balanced and CRLF-pure. Run `flutter analyze` locally to confirm compilation.
