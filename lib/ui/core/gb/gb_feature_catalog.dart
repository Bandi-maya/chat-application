enum GbFeatureKind { toggle, choice, slider, color, action }

class GbFeatureDefinition {
  final String key;
  final String title;
  final String category;
  final GbFeatureKind kind;
  final Object? defaultValue;
  final String description;
  final double min;
  final double max;
  final List<String> options;
  const GbFeatureDefinition({required this.key, required this.title, required this.category, required this.kind, required this.defaultValue, required this.description, this.min=0, this.max=100, this.options=const <String>[]});
}

class GbFeatureCatalog {
  const GbFeatureCatalog._();

  static const Map<String, String> _categoryKeys = <String, String>{
    'Home FAB & shortcuts': 'hide_fab,ModFabNormalColor,ModFabPressedColor,ModFabTextColor,MetaAI,hide_action_m,hide_action_b,hide_action_s,hide_action_mas,hide_action_a',
    'Navigation & touch effects': 'key_pager_animation,key_chats_listanimation,key_chat_animation,tap_effect_enabled,tap_emoji,tap_density,tap_size,tap_duration,fall_effect_enabled,fall_emoji,fall_density,fall_size,fall_speed',
    'Advanced messaging': 'key_send_hidden_msg,key_edit_hidden_msg,key_all_white,key_notsend_msg,key_notreceive_msg,key_noreceive_img,abosaleh_mention_member,key_reply_mention,key_method_endgp,key_hide_unsaved_num',
    'Chat list rows': 'main_text,ModConTextColor,ModContactNameColor,HomeCounterBK,HomeCounterText,ModOnlineColor,ModlastseenColor,ModHomeMentionIndBackground,ModHomeMentionIconColor,key_mas_hide_archive_home,arch_chats_top,onlinechat,onlineDotchat,onlineDotchatColor,elapsed_time',
    'Presence & activity alerts': 'abu_saleh_toast_online,abu_saleh_toast_online_Override,abu_saleh_toast_online_view,abu_saleh_toast_online_cs,abu_saleh_toast_online_el,abu_saleh_toast_online_rd,abu_saleh_toast_online_rt,abu_saleh_toast_online_gv,abu_saleh_toast_online_bc,abu_saleh_toast_online_tc,abu_saleh_toast_status,yo_toast_status_override,abu_saleh_toast_status_view,abu_saleh_toast_status_cs,abu_saleh_toast_status_el,abu_saleh_toast_status_rd,abu_saleh_toast_status_rt,abu_saleh_toast_status_gv,abu_saleh_toast_status_bc,abu_saleh_toast_status_tc,abu_saleh_toast_profile,abu_saleh_toast_profile_override,abu_saleh_toast_profile_view,abu_saleh_toast_profile_cs,abu_saleh_toast_profile_el,abu_saleh_toast_profile_rd,abu_saleh_toast_profile_rt,abu_saleh_toast_profile_gv,abu_saleh_toast_profile_bc,abu_saleh_toast_profile_tc,abu_saleh_toast_typing,abu_saleh_toast_typing_override,abu_saleh_toast_typing_view,abu_saleh_toast_typing_cs,abu_saleh_toast_typing_el,abu_saleh_toast_typing_rd,abu_saleh_toast_typing_rt,abu_saleh_toast_typing_gv,abu_saleh_toast_typing_bc,abu_saleh_toast_typing_tc',
    'Home list visibility': 'Hide_div,key_hide_unsaved_num,Hide_contact_picker_section_frequent_chats,Hide_contact_picker_section_other_contacts,Hide_contact_picker_section_recent_chats',
    'Home header': 'ui_home_styleV3,home_stories_key,key_carousel_view,home_stories_style,enable_grp_separationV2,my_name,my_statusd,abu_saleh_hide_list,abu_saleh_research,yo_multi_account_menu,yo_want_ghostmode,yo_want_toolbar_cam,yo_want_nightmode,yo_want_airplanemode,PicHead,disable_hiddenchat_access,ModConColor,pagetitle_picker,pagetitle_sel_picker,tabindicator,tabadgeBKColor,tabadgeTextColor',
    'Block list maintenance': 'mas_key_cleanlog_blocklist',
    'Fonts & icons': 'yo_nicon_color,yo_nicons,font,load_customfont',
    'Privacy': 'yoHideSeen,anti_vw_once,yoDisableFwd,yoCallsPrivacy,yoCustomPrivList,Saleh_HidePrivacy,Saleh_HideCUpdates,stat_cat,abu_saleh_channels,yoHideStatViewV2,yoAntiRevokeStatus,AntiRevokeStatusNotif,Abu_Saleh_Hide_message,disappearing_message_key,key_chat_editview,yoAntiRevoke,AntiRevokeMsgNotif,masdeletionofeveryone,key_show_deltime,abu_saleh_unreaded_chat,yoBlueOnReply,cat_pc,cat_pg,cat_pb',
    'Status & stories': 'home_stories_key,key_carousel_view,home_stories_style,key_status_ui,statuses_bar_bg_picker,statuses_bar_text_picker,key_name_stories,SeenColor,UnSeenColor,key_counter_bg,key_counter_tx,abu_saleh_status_and_photo,enable_statuspage_extras,key_with_thumb,abu9aleh_status_audio,status_wantsendconfirmation,enable_fivminstatus',
    'Snow & particles': 'key_snow_home,hrotation,animation_drawable,intensitas,speed,key_snow_chats,crotation,animation_drawableshatssss,intensitasshatrs,speedshats',
    'Chat bubbles & ticks': 'tick_style,bubble_style,text_size_pick,ConvoBack,ModChatRightBubble,ModChatBubbleText,date_right_color,ModChatLeftBubble,ModChatBubbleTextLeft,date_left_color,rvkdmsg_icon_color,quoted_divider_picker,quoted_name_picker,quoted_text_picker,quoted_bg_picker,TxtSelect,read_more',
    'Avatars in chat': 'pic_inside,chat_contactpicV2,chat_mypicV2,pic_chat_size_pickerV2',
    'Storage & cleanup': 'clear_logs',
    'Conversation behavior': 'direkt_link_degistir,stkr_wantsendconfirmation,yo_hideinfo,admin_abosaleh,key_admicon_list,key_quick_view,key_quick_position,quickBK,quickText,abu_saleh_key_fab_cahtV2,key_hide_scrollup,key_hide_scrolldown,key_custom_iconup,key_custom_icondown,key_updown_bgcolor,key_updown_iconcolor,MasOption,bubble_more_options_dlg,disableDTTL,inconvo_trans_option,hide_translation_icon,custom_wall,custom_wall_profilepic,Audio_sensor,Audio_ears,abu_saleh_play_voice_note,abu_saleh_forward_tovoice,hazar_bozkurt_sesler_gelenmesaj,hazar_bozkurt_sesler_gidenmesaj',
    'Universal behavior': 'Language,inconvo_trans_option,trans_def_to,multiChats,Pop_Heds,disable_bcounter,disable_audioheads,fwd_lim_incr,disable_chatswipeV2,always_online,tenor_giphy,clear_logs,cat_img,Img_highres_seek',
    'Conversation colors': 'emojipopup_header,emojipopup_icons,emojipopup_body,ModChatBubbleHyperlinks,date_divider_color_picker,date_bubble_color_picker,participant_name_color_picker,seekbar_color_chat_picker,btn_voice_color_chat_picker',
    'Calls appearance': 'ModCallsBackground,ModCallsTextColor,ModCallsIconColors',
    'Media visibility': 'yohide_inimages,yohide_invideos,yohide_ingifs,yohide_mediashow',
    'Conversation header': 'ModChatColor,PicProf,NameProf,Conv_call_btn,statuschat,ModChatGStatusB,ModChatGStatusT',
    'Quick contact': 'abu_saleh_quickcontact,key_mas_setBackground_quick,mas_latarkontak2,key_mas_icon_quickcontact,key_mas_border_avatar_quick_contact,mas_lingkaran_kontak2,mas_lingkaran_kontak3,key_mas_setText_contact_name',
    'Universal colors': 'ModConPickColor,HomeBarText,ModConBackColor,list_bg_color,ModDarkConPickColor,ModDarkConPickColorNav',
    'Media limits & quality': 'abu9aleh_media_video_player,Abu_Saleh_Previewbtn,abusaleh_media,abu_saleh_image_quality,Up_size_limit,abo_saleh_audio_limit_check,Img_share_limit,key_more_docs_send,enable_fivminstatus',
    'Widget appearance': 'ModWdgBKColor,ModWdgTitleColor,ModWdgStatusColor',
    'Composer appearance': 'BGColor,ModChatBtnColor,ModChatEmojiColor,ModChaSendColor,ModChaSendBKColor,ModChatEntry,ModChatTextColor',
  };

  static const Map<String, List<String>> _choiceOptions = <String, List<String>>{
    'ui_home_styleV3': <String>['Chaty', 'Classic', 'Compact', 'Cards', 'Minimal', 'Stories first'],
    'home_stories_style': <String>['Circular', 'Squircle', 'Cards', 'Compact', 'Minimal'],
    'key_status_ui': <String>['Classic', 'Instagram', 'Cards', 'Minimal', 'Compact'],
    'tick_style': <String>['Default', 'Double check', 'iOS', 'Minimal', 'Neon'],
    'bubble_style': <String>['Rounded', 'Tail', 'Tail-less', 'Compact', 'Squircle', 'Card', 'Pill'],
    'Language': <String>['System', 'English', 'Hindi', 'Telugu', 'Tamil', 'Spanish', 'French', 'German', 'Arabic'],
    'inconvo_trans_option': <String>['Off', 'Inline', 'Action menu', 'Auto-detect'],
    'trans_def_to': <String>['System', 'English', 'Hindi', 'Telugu', 'Tamil', 'Spanish', 'French', 'German', 'Arabic'],
    'abu_saleh_quickcontact': <String>['Off', 'Left', 'Right', 'Floating'],
    'key_pager_animation': <String>['None', 'Fade', 'Slide', 'Scale', 'Shared axis', 'Flip'],
    'key_chats_listanimation': <String>['None', 'Fade', 'Slide', 'Scale', 'Stagger'],
    'key_chat_animation': <String>['None', 'Fade', 'Slide', 'Scale', 'Shared axis'],
    'font': <String>['System', 'Compact', 'Rounded', 'Serif', 'Mono', 'Display'],
    'MasOption': <String>['Default', 'Compact', 'Expanded', 'Bottom sheet'],
    'tenor_giphy': <String>['Tenor', 'Giphy', 'Both', 'Off'],
    'yoCallsPrivacy': <String>['Everyone', 'My contacts', 'My contacts except', 'Nobody'],
    'yoCustomPrivList': <String>['Everyone', 'My contacts', 'My contacts except', 'Nobody'],
    'tap_emoji': <String>['✨', '❤️', '🔥', '⚡', '⭐', '🌸', '💫', '🎉'],
    'fall_emoji': <String>['Stars', 'Hearts', 'Snowflakes', 'Leaves', 'Bubbles', 'Sparks'],
    'quickText': <String>['Compact', 'Normal', 'Large'],
    'key_updown_iconcolor': <String>['Theme', 'Accent', 'White', 'Black'],
    'cat_img': <String>['Default', 'Compact', 'Expanded'],
    'yohide_mediashow': <String>['Manage hidden media'],
    'stat_cat': <String>['Everyone', 'Contacts', 'Selected contacts', 'Nobody'],
    'cat_pc': <String>['Everyone', 'Contacts', 'Selected contacts', 'Nobody'],
    'cat_pg': <String>['Everyone', 'Contacts', 'Selected contacts', 'Nobody'],
    'cat_pb': <String>['Everyone', 'Contacts', 'Selected contacts', 'Nobody'],
  };

  static const Map<String, String> _descriptions = <String, String>{
    'yoHideSeen': 'Control read receipts / blue ticks.',
    'anti_vw_once': 'Allow view-once media to remain viewable on this device.',
    'yoDisableFwd': 'Hide forwarded labels on messages you forward.',
    'yoCallsPrivacy': 'Choose who may call you.',
    'yoAntiRevoke': 'Keep a locally visible copy when a sender deletes a message.',
    'yoAntiRevokeStatus': 'Keep a locally visible copy of a status after its owner deletes it until expiry.',
    'disappearing_message_key': 'Retain disappearing messages locally when enabled.',
    'yoHideStatViewV2': 'Do not publish status-view receipts.',
    'yoBlueOnReply': 'Send read receipts only after you reply.',
    'always_online': 'Keep presence online while Chaty maintains an active connection.',
    'fwd_lim_incr': 'Allow forwarding to more conversations per operation.',
    'enable_fivminstatus': 'Allow longer video status uploads up to five minutes.',
    'abu9aleh_status_audio': 'Allow audio status uploads.',
    'status_wantsendconfirmation': 'Ask for confirmation before publishing a status.',
    'stkr_wantsendconfirmation': 'Ask for confirmation before sending a sticker.',
    'Audio_ears': 'Use proximity/earpiece behavior for voice-note playback.',
    'Audio_sensor': 'Enable proximity sensor handling during voice-note playback.',
    'abu_saleh_play_voice_note': 'Use the enhanced voice-note player.',
    'Img_highres_seek': 'Set preferred image quality for uploads.',
    'abu_saleh_image_quality': 'Select image upload quality.',
    'Up_size_limit': 'Configure maximum video/file upload size.',
    'abo_saleh_audio_limit_check': 'Configure maximum audio upload size.',
    'Img_share_limit': 'Configure maximum images selectable in one send.',
    'key_more_docs_send': 'Allow selecting more documents per send.',
    'key_send_hidden_msg': 'Allow sending messages from hidden chats without exposing them in the main list.',
    'key_edit_hidden_msg': 'Allow editing messages in hidden chats.',
    'key_notsend_msg': 'Pause outgoing messages while stealth/airplane mode is active.',
    'key_notreceive_msg': 'Pause realtime incoming message reconciliation while local airplane mode is active.',
    'key_noreceive_img': 'Pause automatic media retrieval while local airplane mode is active.',
    'abu_saleh_unreaded_chat': 'Keep selected chats marked unread until manually cleared.',
    'multiChats': 'Enable multi-select actions on chats.',
    'Pop_Heds': 'Enable heads-up / floating message alerts.',
    'disable_bcounter': 'Hide unread badge counters.',
    'disable_audioheads': 'Disable floating audio heads.',
    'disable_chatswipeV2': 'Disable swipe actions on chat rows.',
    'home_stories_key': 'Show stories/status strip on home.',
    'key_carousel_view': 'Render statuses in carousel mode.',
    'onlinechat': 'Show online state in chat list rows.',
    'onlineDotchat': 'Show an online dot on chat avatars.',
    'elapsed_time': 'Show elapsed time since last activity.',
    'arch_chats_top': 'Place archived chats at the top.',
    'key_mas_hide_archive_home': 'Hide archived-chat shortcut on home.',
    'key_hide_unsaved_num': 'Hide unsaved-number entries where applicable.',
    'yo_want_ghostmode': 'Enable the stealth privacy bundle.',
    'yo_want_airplanemode': 'Pause Chaty network/realtime actions without disabling the phone network.',
    'yo_want_nightmode': 'Show the light/dark quick toggle.',
    'yo_want_toolbar_cam': 'Show the camera shortcut.',
    'yo_multi_account_menu': 'Show multi-account switcher.',
    'disable_hiddenchat_access': 'Hide the hidden-chat entry point.',
    'enable_grp_separationV2': 'Separate direct chats and groups.',
    'pic_inside': 'Show avatars alongside chat bubbles.',
    'chat_contactpicV2': 'Show contact avatars on incoming messages.',
    'chat_mypicV2': 'Show your avatar on outgoing messages.',
    'enable_statuspage_extras': 'Enable status-page extended actions.',
    'key_with_thumb': 'Show status media thumbnails.',
  };

  static final List<GbFeatureDefinition> all = () {
    final seen = <String>{};
    final result = <GbFeatureDefinition>[];
    for (final entry in _categoryKeys.entries) {
      for (final raw in entry.value.split(',')) {
        final key = raw.trim();
        if (key.isEmpty || !seen.add(key)) continue;
        result.add(_definition(entry.key, key));
      }
    }
    return List<GbFeatureDefinition>.unmodifiable(result);
  }();

  static GbFeatureDefinition _definition(String category, String key) {
    final kind = _kindFor(key);
    final options = _choiceOptions[key] ?? const <String>['Default', 'Style 1', 'Style 2', 'Style 3'];
    return GbFeatureDefinition(
      key: key,
      title: _humanize(key),
      category: category,
      kind: kind,
      defaultValue: _defaultFor(key, kind, options),
      description: _descriptions[key] ?? 'Independent Chaty implementation of the extracted compatibility control.',
      max: _maxFor(key),
      options: kind == GbFeatureKind.choice ? options : const <String>[],
    );
  }

  static GbFeatureKind _kindFor(String key) {
    if (_choiceOptions.containsKey(key) || <String>{'yoCallsPrivacy','yoCustomPrivList','key_status_ui','animation_drawable','animation_drawableshatssss','yo_nicons','key_admicon_list','key_quick_position','key_custom_iconup','key_custom_icondown','hazar_bozkurt_sesler_gelenmesaj','hazar_bozkurt_sesler_gidenmesaj'}.contains(key)) return GbFeatureKind.choice;
    if (<String>{'clear_logs','mas_key_cleanlog_blocklist','load_customfont','key_edit_hidden_msg','key_reply_mention','yohide_mediashow'}.contains(key)) return GbFeatureKind.action;
    final k = key.toLowerCase();
    if (<String>{'main_text','img_highres_seek','up_size_limit','abo_saleh_audio_limit_check','img_share_limit','text_size_pick','pic_chat_size_pickerv2'}.contains(k) || k.contains('density') || k.contains('intens') || k.contains('speed') || k.contains('duration') || k.endsWith('_rd')) return GbFeatureKind.slider;
    if (k.contains('color') || k.contains('picker') || k.contains('background') || k.contains('counterbk') || k.contains('countertext') || k.contains('quickbk') || k.contains('quicktext') || k.startsWith('modchat') || k.startsWith('modcon') || k.startsWith('modcalls') || k.startsWith('modwdg') || k.startsWith('modfab') || k.startsWith('emojipopup') || k == 'bgcolor' || k == 'convoback' || k == 'homebartext') return GbFeatureKind.color;
    return GbFeatureKind.toggle;
  }

  static Object? _defaultFor(String key, GbFeatureKind kind, List<String> options) {
    if (<String>{'anti_vw_once','yoAntiRevoke','yoAntiRevokeStatus','AntiRevokeMsgNotif','AntiRevokeStatusNotif','disappearing_message_key','key_chat_editview','abusaleh_media','yo_hideinfo'}.contains(key)) return true;
    switch (kind) {
      case GbFeatureKind.toggle: return false;
      case GbFeatureKind.choice: return options.first;
      case GbFeatureKind.slider:
        if (key == 'text_size_pick') return 15.0;
        if (key == 'pic_chat_size_pickerV2') return 36.0;
        if (key == 'Img_highres_seek') return 85.0;
        if (key == 'Up_size_limit') return 100.0;
        if (key == 'abo_saleh_audio_limit_check') return 50.0;
        if (key == 'Img_share_limit') return 30.0;
        return 1.0;
      case GbFeatureKind.color: return 0;
      case GbFeatureKind.action: return '';
    }
  }

  static double _maxFor(String key) {
    if (key == 'text_size_pick') return 30;
    if (key == 'pic_chat_size_pickerV2') return 64;
    if (key == 'Img_highres_seek') return 100;
    if (key == 'Up_size_limit') return 2048;
    if (key == 'abo_saleh_audio_limit_check') return 512;
    if (key == 'Img_share_limit') return 100;
    final k = key.toLowerCase();
    if (k.contains('density') || k.contains('intens') || k.contains('speed')) return 20;
    if (k.contains('duration')) return 5000;
    return 100;
  }

  static String _humanize(String key) {
    var value = key.replaceAll(RegExp(r'[_-]+'), ' ').replaceAllMapped(RegExp(r'([a-z0-9])([A-Z])'), (m) => '${m.group(1)} ${m.group(2)}').replaceAll(RegExp(r'\s+'), ' ').trim();
    const replacements = <String,String>{'yo ':'','key ':'','abu ':'','saleh ':'','BK':'Background','BG':'Background','TX':'Text','Pic':'Picture','Prof':'Profile','Convo':'Conversation','Fwd':'Forward'};
    for (final entry in replacements.entries) { value = value.replaceAll(entry.key, entry.value); }
    if (value.isEmpty) return key;
    return '${value[0].toUpperCase()}${value.substring(1)}';
  }

  static Map<String,Object?> get defaults => <String,Object?>{for (final item in all) item.key:item.defaultValue};
  static List<String> get categories => _categoryKeys.keys.toList(growable:false);
  static List<GbFeatureDefinition> inCategory(String category) => all.where((item)=>item.category==category).toList(growable:false);
  static GbFeatureDefinition? byKey(String key) { for (final item in all) { if (item.key==key) return item; } return null; }
}
