/// Privacy Preferences Model
class PrivacyPreferences {
  final bool freezeLastSeen;
  final String frozenLastSeenTime;
  final String
  hideLastSeenAudience; // 'Everyone', 'My Contacts', 'My Contacts Except...', 'Nobody'
  final String hideOnlineAudience; // 'Everyone', 'Same as Last Seen'
  final bool antiViewOnce;
  final bool disableForwardedLabel;
  final bool readReceipts;
  final bool typingIndicators;
  final bool recordingIndicators;
  final String
  whoCanCallMe; // 'Everyone', 'My Contacts', 'My Contacts Except...', 'Nobody'
  final List<String>
  whoCanCallMeExceptions; // user IDs excluded when 'My Contacts Except...'
  final bool hidePrivacyOption;
  final bool hideViewStatus;
  final bool antiDeleteStatus;
  final bool statusRevocationAlert;
  final bool showEditedMessage;
  final bool antiDeleteMessages;
  final bool messageRevokeAlert;
  final bool showBlueTicksAfterReply;
  final bool hideUpdateOption;
  final bool disableChannels;
  final bool hideFirstMessage;
  final bool antiDisappearingMessages;
  final bool deletionOfEveryone;
  final bool deletedMediaTime;
  final bool unviewedChats;

  const PrivacyPreferences({
    this.freezeLastSeen = false,
    this.frozenLastSeenTime = '',
    this.hideLastSeenAudience = 'My Contacts',
    this.hideOnlineAudience = 'Everyone',
    this.antiViewOnce = true,
    this.disableForwardedLabel = false,
    this.readReceipts = true,
    this.typingIndicators = true,
    this.recordingIndicators = true,
    this.whoCanCallMe = 'Everyone',
    this.whoCanCallMeExceptions = const <String>[],
    this.hidePrivacyOption = false,
    this.hideViewStatus = false,
    this.antiDeleteStatus = true,
    this.statusRevocationAlert = true,
    this.showEditedMessage = true,
    this.antiDeleteMessages = true,
    this.messageRevokeAlert = true,
    this.showBlueTicksAfterReply = false,
    this.hideUpdateOption = false,
    this.disableChannels = false,
    this.hideFirstMessage = false,
    this.antiDisappearingMessages = true,
    this.deletionOfEveryone = true,
    this.deletedMediaTime = false,
    this.unviewedChats = false,
  });

  PrivacyPreferences copyWith({
    bool? freezeLastSeen,
    String? frozenLastSeenTime,
    String? hideLastSeenAudience,
    String? hideOnlineAudience,
    bool? antiViewOnce,
    bool? disableForwardedLabel,
    bool? readReceipts,
    bool? typingIndicators,
    bool? recordingIndicators,
    String? whoCanCallMe,
    List<String>? whoCanCallMeExceptions,
    bool? hidePrivacyOption,
    bool? hideViewStatus,
    bool? antiDeleteStatus,
    bool? statusRevocationAlert,
    bool? showEditedMessage,
    bool? antiDeleteMessages,
    bool? messageRevokeAlert,
    bool? showBlueTicksAfterReply,
    bool? hideUpdateOption,
    bool? disableChannels,
    bool? hideFirstMessage,
    bool? antiDisappearingMessages,
    bool? deletionOfEveryone,
    bool? deletedMediaTime,
    bool? unviewedChats,
  }) {
    return PrivacyPreferences(
      freezeLastSeen: freezeLastSeen ?? this.freezeLastSeen,
      frozenLastSeenTime: frozenLastSeenTime ?? this.frozenLastSeenTime,
      hideLastSeenAudience: hideLastSeenAudience ?? this.hideLastSeenAudience,
      hideOnlineAudience: hideOnlineAudience ?? this.hideOnlineAudience,
      antiViewOnce: antiViewOnce ?? this.antiViewOnce,
      disableForwardedLabel:
          disableForwardedLabel ?? this.disableForwardedLabel,
      readReceipts: readReceipts ?? this.readReceipts,
      typingIndicators: typingIndicators ?? this.typingIndicators,
      recordingIndicators: recordingIndicators ?? this.recordingIndicators,
      whoCanCallMe: whoCanCallMe ?? this.whoCanCallMe,
      whoCanCallMeExceptions:
          whoCanCallMeExceptions ?? this.whoCanCallMeExceptions,
      hidePrivacyOption: hidePrivacyOption ?? this.hidePrivacyOption,
      hideViewStatus: hideViewStatus ?? this.hideViewStatus,
      antiDeleteStatus: antiDeleteStatus ?? this.antiDeleteStatus,
      statusRevocationAlert:
          statusRevocationAlert ?? this.statusRevocationAlert,
      showEditedMessage: showEditedMessage ?? this.showEditedMessage,
      antiDeleteMessages: antiDeleteMessages ?? this.antiDeleteMessages,
      messageRevokeAlert: messageRevokeAlert ?? this.messageRevokeAlert,
      showBlueTicksAfterReply:
          showBlueTicksAfterReply ?? this.showBlueTicksAfterReply,
      hideUpdateOption: hideUpdateOption ?? this.hideUpdateOption,
      disableChannels: disableChannels ?? this.disableChannels,
      hideFirstMessage: hideFirstMessage ?? this.hideFirstMessage,
      antiDisappearingMessages:
          antiDisappearingMessages ?? this.antiDisappearingMessages,
      deletionOfEveryone: deletionOfEveryone ?? this.deletionOfEveryone,
      deletedMediaTime: deletedMediaTime ?? this.deletedMediaTime,
      unviewedChats: unviewedChats ?? this.unviewedChats,
    );
  }

  Map<String, dynamic> toMap() => {
    'freezeLastSeen': freezeLastSeen,
    'frozenLastSeenTime': frozenLastSeenTime,
    'hideLastSeenAudience': hideLastSeenAudience,
    'hideOnlineAudience': hideOnlineAudience,
    'antiViewOnce': antiViewOnce,
    'disableForwardedLabel': disableForwardedLabel,
    'readReceipts': readReceipts,
    'typingIndicators': typingIndicators,
    'recordingIndicators': recordingIndicators,
    'whoCanCallMe': whoCanCallMe,
    'whoCanCallMeExceptions': whoCanCallMeExceptions,
    'hidePrivacyOption': hidePrivacyOption,
    'hideViewStatus': hideViewStatus,
    'antiDeleteStatus': antiDeleteStatus,
    'statusRevocationAlert': statusRevocationAlert,
    'showEditedMessage': showEditedMessage,
    'antiDeleteMessages': antiDeleteMessages,
    'messageRevokeAlert': messageRevokeAlert,
    'showBlueTicksAfterReply': showBlueTicksAfterReply,
    'hideUpdateOption': hideUpdateOption,
    'disableChannels': disableChannels,
    'hideFirstMessage': hideFirstMessage,
    'antiDisappearingMessages': antiDisappearingMessages,
    'deletionOfEveryone': deletionOfEveryone,
    'deletedMediaTime': deletedMediaTime,
    'unviewedChats': unviewedChats,
  };

  factory PrivacyPreferences.fromMap(Map<String, dynamic> map) =>
      PrivacyPreferences(
        freezeLastSeen: map['freezeLastSeen'] ?? false,
        frozenLastSeenTime: map['frozenLastSeenTime'] ?? '',
        hideLastSeenAudience: map['hideLastSeenAudience'] ?? 'My Contacts',
        hideOnlineAudience: map['hideOnlineAudience'] ?? 'Everyone',
        antiViewOnce: map['antiViewOnce'] ?? true,
        disableForwardedLabel: map['disableForwardedLabel'] ?? false,
        readReceipts: map['readReceipts'] ?? true,
        typingIndicators: map['typingIndicators'] ?? true,
        recordingIndicators: map['recordingIndicators'] ?? true,
        whoCanCallMe: map['whoCanCallMe'] ?? 'Everyone',
        whoCanCallMeExceptions:
            (map['whoCanCallMeExceptions'] as List<dynamic>? ??
                    const <dynamic>[])
                .map((item) => item.toString())
                .toList(growable: false),
        hidePrivacyOption: map['hidePrivacyOption'] ?? false,
        hideViewStatus: map['hideViewStatus'] ?? false,
        antiDeleteStatus: map['antiDeleteStatus'] ?? true,
        statusRevocationAlert: map['statusRevocationAlert'] ?? true,
        showEditedMessage: map['showEditedMessage'] ?? true,
        antiDeleteMessages: map['antiDeleteMessages'] ?? true,
        messageRevokeAlert: map['messageRevokeAlert'] ?? true,
        showBlueTicksAfterReply: map['showBlueTicksAfterReply'] ?? false,
        hideUpdateOption: map['hideUpdateOption'] ?? false,
        disableChannels: map['disableChannels'] ?? false,
        hideFirstMessage: map['hideFirstMessage'] ?? false,
        antiDisappearingMessages: map['antiDisappearingMessages'] ?? true,
        deletionOfEveryone: map['deletionOfEveryone'] ?? true,
        deletedMediaTime: map['deletedMediaTime'] ?? false,
        unviewedChats: map['unviewedChats'] ?? false,
      );
}

/// Security Preferences & App Lock Model
///
/// SECURITY: This model is serialized into local storage and synced to the
/// backend. It therefore holds only non-secret configuration. The actual
/// unlock credentials (PIN / pattern / password) are never stored here — they
/// live exclusively as salted PBKDF2 hashes in platform secure storage via
/// `LocalLockService`. Any legacy plaintext credential fields are purged by
/// [PreferencesMigrator] on load.
class SecurityPreferences {
  final bool isAppLockEnabled;
  final String
  lockMethod; // 'Biometric', 'PIN', 'Pattern', 'Password', 'Device Credential'
  final bool makePatternInvisible;
  final bool disablePatternVibration;
  final String
  autoLockTimeout; // 'Immediately', '15s', '30s', '1m', '5m', '15m'
  final bool hideLockNotificationContent;
  final List<String> lockedConversationIds;
  final List<String> hiddenConversationIds;
  final bool hideLockedChats;
  final bool entryByAppTitle;
  final bool entryBySecretPhrase;
  final bool protectFromScreenshots;

  const SecurityPreferences({
    this.isAppLockEnabled = false,
    this.lockMethod = 'PIN',
    this.makePatternInvisible = false,
    this.disablePatternVibration = false,
    this.autoLockTimeout = '1m',
    this.hideLockNotificationContent = true,
    this.lockedConversationIds = const [],
    this.hiddenConversationIds = const [],
    this.hideLockedChats = false,
    this.entryByAppTitle = true,
    this.entryBySecretPhrase = false,
    this.protectFromScreenshots = false,
  });

  SecurityPreferences copyWith({
    bool? isAppLockEnabled,
    String? lockMethod,
    bool? makePatternInvisible,
    bool? disablePatternVibration,
    String? autoLockTimeout,
    bool? hideLockNotificationContent,
    List<String>? lockedConversationIds,
    List<String>? hiddenConversationIds,
    bool? hideLockedChats,
    bool? entryByAppTitle,
    bool? entryBySecretPhrase,
    bool? protectFromScreenshots,
  }) {
    return SecurityPreferences(
      isAppLockEnabled: isAppLockEnabled ?? this.isAppLockEnabled,
      lockMethod: lockMethod ?? this.lockMethod,
      makePatternInvisible: makePatternInvisible ?? this.makePatternInvisible,
      disablePatternVibration:
          disablePatternVibration ?? this.disablePatternVibration,
      autoLockTimeout: autoLockTimeout ?? this.autoLockTimeout,
      hideLockNotificationContent:
          hideLockNotificationContent ?? this.hideLockNotificationContent,
      lockedConversationIds:
          lockedConversationIds ?? this.lockedConversationIds,
      hiddenConversationIds:
          hiddenConversationIds ?? this.hiddenConversationIds,
      hideLockedChats: hideLockedChats ?? this.hideLockedChats,
      entryByAppTitle: entryByAppTitle ?? this.entryByAppTitle,
      entryBySecretPhrase: entryBySecretPhrase ?? this.entryBySecretPhrase,
      protectFromScreenshots:
          protectFromScreenshots ?? this.protectFromScreenshots,
    );
  }

  Map<String, dynamic> toMap() => {
    'isAppLockEnabled': isAppLockEnabled,
    'lockMethod': lockMethod,
    'makePatternInvisible': makePatternInvisible,
    'disablePatternVibration': disablePatternVibration,
    'autoLockTimeout': autoLockTimeout,
    'hideLockNotificationContent': hideLockNotificationContent,
    'lockedConversationIds': lockedConversationIds,
    'hiddenConversationIds': hiddenConversationIds,
    'hideLockedChats': hideLockedChats,
    'entryByAppTitle': entryByAppTitle,
    'entryBySecretPhrase': entryBySecretPhrase,
    'protectFromScreenshots': protectFromScreenshots,
  };

  factory SecurityPreferences.fromMap(Map<String, dynamic> map) =>
      SecurityPreferences(
        isAppLockEnabled: map['isAppLockEnabled'] as bool? ?? false,
        lockMethod: map['lockMethod'] as String? ?? 'PIN',
        makePatternInvisible: map['makePatternInvisible'] as bool? ?? false,
        disablePatternVibration:
            map['disablePatternVibration'] as bool? ?? false,
        autoLockTimeout: map['autoLockTimeout'] as String? ?? '1m',
        hideLockNotificationContent:
            map['hideLockNotificationContent'] as bool? ?? true,
        lockedConversationIds:
            (map['lockedConversationIds'] as List?)
                ?.map((e) => e.toString())
                .toList() ??
            const [],
        hiddenConversationIds:
            (map['hiddenConversationIds'] as List?)
                ?.map((e) => e.toString())
                .toList() ??
            const [],
        hideLockedChats: map['hideLockedChats'] as bool? ?? false,
        entryByAppTitle: map['entryByAppTitle'] as bool? ?? true,
        entryBySecretPhrase: map['entryBySecretPhrase'] as bool? ?? false,
        protectFromScreenshots: map['protectFromScreenshots'] as bool? ?? false,
      );
}

/// Home Screen Customization Model
class HomePreferences {
  final String homeStyle;
  final bool enableStoriesStrip;
  final String storiesStyle;
  final bool separateChatsAndGroups;
  final String myNameOverride;
  final String avatarShape;
  final bool ghostMode;
  final bool airplaneModeSimulator;
  final bool showSearchBar;
  final bool showCameraIcon;
  final bool showDesktopIcon;

  final bool carouselView;
  final bool setMyName;
  final bool disableStatusUnderName;
  final bool hideChatSortList;
  final bool disableSearchBar;
  final bool themesEnabled;
  final bool showIconAddAccount;
  final bool hideChatsDivider;
  final bool hideUnsavedNumbers;
  final bool hideFrequentlyContacted;
  final bool hideOtherContacts;
  final bool hideRecentChats;

  // New Image 3 Rows properties
  final double screenTextSize;
  final int? rowTextColor;
  final int? rowContactNameColor;
  final int? unreadCounterColor;
  final int? unreadCounterTextColor;
  final int? contactOnlineColor;
  final int? lastSeenColor;
  final int? mentionIndicatorBgColor;
  final int? mentionIconColor;
  final bool hideArchivedChats;
  final bool archiveChatsOnTop;
  final bool disableContactOnlineLastSeen;
  final bool disableOnlineDot;
  final int? onlineDotColor;
  final bool elapsedTime;

  // New Image 3 FAB properties
  final bool hideFab;
  final int? fabNormalColor;
  final int? fabPressedColor;
  final int? fabIconsColor;
  final bool showMetaAiIcon;
  final bool hideNewMessageFab;
  final bool hideLastSeenFab;
  final bool hideCutEditFab;
  final bool hidePluginsList;
  final bool hideGbwaSettingsFab;
  final String pagerTransition3d;
  final String tabBubbleStyle;

  const HomePreferences({
    this.homeStyle = 'Chaty Default',
    this.enableStoriesStrip = false,
    this.storiesStyle = 'Circular',
    this.separateChatsAndGroups = false,
    this.myNameOverride = 'Alex Rivera',
    this.avatarShape = 'circle',
    this.ghostMode = false,
    this.airplaneModeSimulator = false,
    this.showSearchBar = true,
    this.showCameraIcon = false,
    this.showDesktopIcon = true,
    this.carouselView = false,
    this.setMyName = true,
    this.disableStatusUnderName = false,
    this.hideChatSortList = false,
    this.disableSearchBar = false,
    this.themesEnabled = true,
    this.showIconAddAccount = false,
    this.hideChatsDivider = false,
    this.hideUnsavedNumbers = false,
    this.hideFrequentlyContacted = false,
    this.hideOtherContacts = false,
    this.hideRecentChats = false,
    this.screenTextSize = 17.0,
    this.rowTextColor,
    this.rowContactNameColor,
    this.unreadCounterColor,
    this.unreadCounterTextColor,
    this.contactOnlineColor,
    this.lastSeenColor,
    this.mentionIndicatorBgColor,
    this.mentionIconColor,
    this.hideArchivedChats = false,
    this.archiveChatsOnTop = true,
    this.disableContactOnlineLastSeen = false,
    this.disableOnlineDot = false,
    this.onlineDotColor,
    this.elapsedTime = false,
    this.hideFab = false,
    this.fabNormalColor,
    this.fabPressedColor,
    this.fabIconsColor,
    this.showMetaAiIcon = true,
    this.hideNewMessageFab = false,
    this.hideLastSeenFab = false,
    this.hideCutEditFab = false,
    this.hidePluginsList = false,
    this.hideGbwaSettingsFab = false,
    this.pagerTransition3d = 'Cube 3D',
    this.tabBubbleStyle = 'Capsule Pill',
  });

  HomePreferences copyWith({
    String? pagerTransition3d,
    String? tabBubbleStyle,
    String? homeStyle,
    bool? enableStoriesStrip,
    String? storiesStyle,
    bool? separateChatsAndGroups,
    String? myNameOverride,
    String? avatarShape,
    bool? ghostMode,
    bool? airplaneModeSimulator,
    bool? showSearchBar,
    bool? showCameraIcon,
    bool? showDesktopIcon,
    bool? carouselView,
    bool? setMyName,
    bool? disableStatusUnderName,
    bool? hideChatSortList,
    bool? disableSearchBar,
    bool? themesEnabled,
    bool? showIconAddAccount,
    bool? hideChatsDivider,
    bool? hideUnsavedNumbers,
    bool? hideFrequentlyContacted,
    bool? hideOtherContacts,
    bool? hideRecentChats,
    double? screenTextSize,
    int? rowTextColor,
    int? textColor,
    int? rowContactNameColor,
    int? contactNameColor,
    int? unreadCounterColor,
    int? unreadCounterTextColor,
    int? contactOnlineColor,
    int? lastSeenColor,
    int? mentionIndicatorBgColor,
    int? mentionIconColor,
    bool? hideArchivedChats,
    bool? archiveChatsOnTop,
    bool? disableContactOnlineLastSeen,
    bool? disableOnlineDot,
    int? onlineDotColor,
    bool? elapsedTime,
    bool? hideFab,
    int? fabNormalColor,
    int? fabPressedColor,
    int? fabIconsColor,
    bool? showMetaAiIcon,
    bool? hideNewMessageFab,
    bool? hideLastSeenFab,
    bool? hideCutEditFab,
    bool? hideCutterFab,
    bool? hidePluginsList,
    bool? hideGbwaSettingsFab,
  }) {
    return HomePreferences(
      homeStyle: homeStyle ?? this.homeStyle,
      enableStoriesStrip: enableStoriesStrip ?? this.enableStoriesStrip,
      storiesStyle: storiesStyle ?? this.storiesStyle,
      separateChatsAndGroups:
          separateChatsAndGroups ?? this.separateChatsAndGroups,
      myNameOverride: myNameOverride ?? this.myNameOverride,
      avatarShape: avatarShape ?? this.avatarShape,
      ghostMode: ghostMode ?? this.ghostMode,
      airplaneModeSimulator:
          airplaneModeSimulator ?? this.airplaneModeSimulator,
      showSearchBar: showSearchBar ?? this.showSearchBar,
      showCameraIcon: showCameraIcon ?? this.showCameraIcon,
      showDesktopIcon: showDesktopIcon ?? this.showDesktopIcon,
      carouselView: carouselView ?? this.carouselView,
      setMyName: setMyName ?? this.setMyName,
      disableStatusUnderName:
          disableStatusUnderName ?? this.disableStatusUnderName,
      hideChatSortList: hideChatSortList ?? this.hideChatSortList,
      disableSearchBar: disableSearchBar ?? this.disableSearchBar,
      themesEnabled: themesEnabled ?? this.themesEnabled,
      showIconAddAccount: showIconAddAccount ?? this.showIconAddAccount,
      hideChatsDivider: hideChatsDivider ?? this.hideChatsDivider,
      hideUnsavedNumbers: hideUnsavedNumbers ?? this.hideUnsavedNumbers,
      hideFrequentlyContacted:
          hideFrequentlyContacted ?? this.hideFrequentlyContacted,
      hideOtherContacts: hideOtherContacts ?? this.hideOtherContacts,
      hideRecentChats: hideRecentChats ?? this.hideRecentChats,
      screenTextSize: screenTextSize ?? this.screenTextSize,
      rowTextColor: textColor ?? rowTextColor ?? this.rowTextColor,
      rowContactNameColor:
          contactNameColor ?? rowContactNameColor ?? this.rowContactNameColor,
      unreadCounterColor: unreadCounterColor ?? this.unreadCounterColor,
      unreadCounterTextColor:
          unreadCounterTextColor ?? this.unreadCounterTextColor,
      contactOnlineColor: contactOnlineColor ?? this.contactOnlineColor,
      lastSeenColor: lastSeenColor ?? this.lastSeenColor,
      mentionIndicatorBgColor:
          mentionIndicatorBgColor ?? this.mentionIndicatorBgColor,
      mentionIconColor: mentionIconColor ?? this.mentionIconColor,
      hideArchivedChats: hideArchivedChats ?? this.hideArchivedChats,
      archiveChatsOnTop: archiveChatsOnTop ?? this.archiveChatsOnTop,
      disableContactOnlineLastSeen:
          disableContactOnlineLastSeen ?? this.disableContactOnlineLastSeen,
      disableOnlineDot: disableOnlineDot ?? this.disableOnlineDot,
      onlineDotColor: onlineDotColor ?? this.onlineDotColor,
      elapsedTime: elapsedTime ?? this.elapsedTime,
      hideFab: hideFab ?? this.hideFab,
      fabNormalColor: fabNormalColor ?? this.fabNormalColor,
      fabPressedColor: fabPressedColor ?? this.fabPressedColor,
      fabIconsColor: fabIconsColor ?? this.fabIconsColor,
      showMetaAiIcon: showMetaAiIcon ?? this.showMetaAiIcon,
      hideNewMessageFab: hideNewMessageFab ?? this.hideNewMessageFab,
      hideLastSeenFab: hideLastSeenFab ?? this.hideLastSeenFab,
      hideCutEditFab:
          hideCutterFab ?? hideCutEditFab ?? this.hideCutEditFab,
      hidePluginsList: hidePluginsList ?? this.hidePluginsList,
      hideGbwaSettingsFab: hideGbwaSettingsFab ?? this.hideGbwaSettingsFab,
      pagerTransition3d: pagerTransition3d ?? this.pagerTransition3d,
      tabBubbleStyle: tabBubbleStyle ?? this.tabBubbleStyle,
    );
  }

  // Aliases for compatibility
  int? get textColor => rowTextColor;
  int? get contactNameColor => rowContactNameColor;
  bool get hideCutterFab => hideCutEditFab;

  Map<String, dynamic> toMap() => {
    'homeStyle': homeStyle,
    'enableStoriesStrip': enableStoriesStrip,
    'storiesStyle': storiesStyle,
    'separateChatsAndGroups': separateChatsAndGroups,
    'myNameOverride': myNameOverride,
    'avatarShape': avatarShape,
    'ghostMode': ghostMode,
    'airplaneModeSimulator': airplaneModeSimulator,
    'showSearchBar': showSearchBar,
    'showCameraIcon': showCameraIcon,
    'showDesktopIcon': showDesktopIcon,
    'carouselView': carouselView,
    'setMyName': setMyName,
    'disableStatusUnderName': disableStatusUnderName,
    'hideChatSortList': hideChatSortList,
    'disableSearchBar': disableSearchBar,
    'themesEnabled': themesEnabled,
    'showIconAddAccount': showIconAddAccount,
    'hideChatsDivider': hideChatsDivider,
    'hideUnsavedNumbers': hideUnsavedNumbers,
    'hideFrequentlyContacted': hideFrequentlyContacted,
    'hideOtherContacts': hideOtherContacts,
    'hideRecentChats': hideRecentChats,
    'screenTextSize': screenTextSize,
    'rowTextColor': rowTextColor,
    'rowContactNameColor': rowContactNameColor,
    'unreadCounterColor': unreadCounterColor,
    'unreadCounterTextColor': unreadCounterTextColor,
    'contactOnlineColor': contactOnlineColor,
    'lastSeenColor': lastSeenColor,
    'mentionIndicatorBgColor': mentionIndicatorBgColor,
    'mentionIconColor': mentionIconColor,
    'hideArchivedChats': hideArchivedChats,
    'archiveChatsOnTop': archiveChatsOnTop,
    'disableContactOnlineLastSeen': disableContactOnlineLastSeen,
    'disableOnlineDot': disableOnlineDot,
    'onlineDotColor': onlineDotColor,
    'elapsedTime': elapsedTime,
    'hideFab': hideFab,
    'fabNormalColor': fabNormalColor,
    'fabPressedColor': fabPressedColor,
    'fabIconsColor': fabIconsColor,
    'showMetaAiIcon': showMetaAiIcon,
    'hideNewMessageFab': hideNewMessageFab,
    'hideLastSeenFab': hideLastSeenFab,
    'hideCutEditFab': hideCutEditFab,
    'hidePluginsList': hidePluginsList,
    'hideGbwaSettingsFab': hideGbwaSettingsFab,
    'pagerTransition3d': pagerTransition3d,
    'tabBubbleStyle': tabBubbleStyle,
  };

  factory HomePreferences.fromMap(Map<String, dynamic> map) => HomePreferences(
    homeStyle: map['homeStyle'] ?? 'Chaty Default',
    enableStoriesStrip: map['enableStoriesStrip'] ?? false,
    storiesStyle: map['storiesStyle'] ?? 'Circular',
    separateChatsAndGroups: map['separateChatsAndGroups'] ?? false,
    myNameOverride: map['myNameOverride'] ?? 'Alex Rivera',
    avatarShape: map['avatarShape'] ?? 'circle',
    ghostMode: map['ghostMode'] ?? false,
    airplaneModeSimulator: map['airplaneModeSimulator'] ?? false,
    showSearchBar: map['showSearchBar'] ?? true,
    showCameraIcon: map['showCameraIcon'] ?? false,
    showDesktopIcon: map['showDesktopIcon'] ?? true,
    carouselView: map['carouselView'] ?? false,
    setMyName: map['setMyName'] ?? true,
    disableStatusUnderName: map['disableStatusUnderName'] ?? false,
    hideChatSortList: map['hideChatSortList'] ?? false,
    disableSearchBar: map['disableSearchBar'] ?? false,
    themesEnabled: map['themesEnabled'] ?? true,
    showIconAddAccount: map['showIconAddAccount'] ?? false,
    hideChatsDivider: map['hideChatsDivider'] ?? false,
    hideUnsavedNumbers: map['hideUnsavedNumbers'] ?? false,
    hideFrequentlyContacted: map['hideFrequentlyContacted'] ?? false,
    hideOtherContacts: map['hideOtherContacts'] ?? false,
    hideRecentChats: map['hideRecentChats'] ?? false,
    screenTextSize: (map['screenTextSize'] as num?)?.toDouble() ?? 17.0,
    rowTextColor: map['rowTextColor'] as int?,
    rowContactNameColor: map['rowContactNameColor'] as int?,
    unreadCounterColor: map['unreadCounterColor'] as int?,
    unreadCounterTextColor: map['unreadCounterTextColor'] as int?,
    contactOnlineColor: map['contactOnlineColor'] as int?,
    lastSeenColor: map['lastSeenColor'] as int?,
    mentionIndicatorBgColor: map['mentionIndicatorBgColor'] as int?,
    mentionIconColor: map['mentionIconColor'] as int?,
    hideArchivedChats: map['hideArchivedChats'] ?? false,
    archiveChatsOnTop: map['archiveChatsOnTop'] ?? true,
    disableContactOnlineLastSeen:
        map['disableContactOnlineLastSeen'] ?? false,
    disableOnlineDot: map['disableOnlineDot'] ?? false,
    onlineDotColor: map['onlineDotColor'] as int?,
    elapsedTime: map['elapsedTime'] ?? false,
    hideFab: map['hideFab'] ?? false,
    fabNormalColor: map['fabNormalColor'] as int?,
    fabPressedColor: map['fabPressedColor'] as int?,
    fabIconsColor: map['fabIconsColor'] as int?,
    showMetaAiIcon: map['showMetaAiIcon'] ?? true,
    hideNewMessageFab: map['hideNewMessageFab'] ?? false,
    hideLastSeenFab: map['hideLastSeenFab'] ?? false,
    hideCutEditFab: map['hideCutEditFab'] ?? false,
    hidePluginsList: map['hidePluginsList'] ?? false,
    hideGbwaSettingsFab: map['hideGbwaSettingsFab'] ?? false,
    pagerTransition3d: map['pagerTransition3d'] ?? 'Cube 3D',
    tabBubbleStyle: map['tabBubbleStyle'] ?? 'Capsule Pill',
  );
}

/// Conversation Screen Customization Model
class ConversationPreferences {
  final String bubbleStyle;
  final String tickStyle;
  final bool enableQuickContactSidebar;
  final String sidebarPosition;
  final double sidebarOpacity;
  final bool iosStylePopupMenu;
  final String doubleTapReactionEmoji;
  final String wallpaperType;
  final double voicePlaybackSpeed;
  final String wallpaperPath;
  final bool enableAnimatedEmojis;
  final bool switchDirectContactLink;
  final bool confirmBeforeSendingSticker;
  final bool newAttachmentPickerUi;
  final bool hideDateAndName;
  final bool hideAdminNameIcon;
  final String groupAdminIcon;
  final String quickContactSidebarPosition;
  final int quickContactBgColor;
  final int quickContactTextColor;
  final bool hideChatFab;
  final bool disableMoreOptionsFromBubble;
  final bool disableDoubleTapReaction;
  final String translateOptionSettings;
  final bool hideMessageTranslationIcon;
  final bool customWallpaperPerContact;
  final bool profilePicWallpaper;
  final bool enableProximitySensor;
  final bool disableOutputSwitching;
  final bool playVoiceNotes;
  final bool forwardAsVoiceNote;
  final String incomingMessageRingtone;
  final String sendMessageRingtone;

  // New Image 4 Action Bar properties
  final int? actionBarColor;
  final bool hideProfilePicture;
  final bool hideContactName;
  final bool hideCallButton;
  final bool disableContactStatus;
  final int? contactStatusBgColor;
  final int? contactStatusTextColor;

  // New Image 4 Bubble & Ticks properties
  final double messageTextSize;
  final int? convBackgroundColor;
  final int? rightBubbleColor;
  final int? rightChatBubbleTextColor;
  final int? rightBubbleTimeColor;
  final int? leftBubbleColor;
  final int? leftChatBubbleTextColor;
  final int? leftBubbleTimeColor;
  final int? deletedMessageIconColor;
  final int? quotedDividerColor;
  final int? quotedNameColor;
  final int? quotedMessageColor;
  final int? quotedBackgroundColor;
  final bool makeTextSelectable;
  final bool removeReadMore;

  // New Image 4 Conversation Entry Style properties
  final String entryStyle;
  final int? uiEntryBackgroundColor;
  final int? uiButtonsColor;
  final int? emojiButtonColor;
  final int? sendButtonColor;
  final int? micSendBgCircleColor;
  final int? textEntryBackgroundColor;
  final int? textEntryColor;

  // New Image 4 More Options properties
  final int? emojiHeaderColor;
  final int? emojiHeaderIconsColor;
  final int? emojiPickerBgColor;
  final int? hyperlinksColor;
  final int? infoBalloonsTextColor;
  final int? infoBalloonsBgColor;
  final int? groupParticipantNameColor;
  final int? voiceNotePlayingBarColor;
  final int? voiceNotePlayButtonColor;

  const ConversationPreferences({
    this.bubbleStyle = 'Stock',
    this.tickStyle = 'RC iOS 11',
    this.enableQuickContactSidebar = false,
    this.sidebarPosition = 'Right',
    this.sidebarOpacity = 0.9,
    this.iosStylePopupMenu = true,
    this.doubleTapReactionEmoji = '❤️',
    this.wallpaperType = 'Pattern',
    this.voicePlaybackSpeed = 1.0,
    this.wallpaperPath = '',
    this.enableAnimatedEmojis = true,
    this.switchDirectContactLink = false,
    this.confirmBeforeSendingSticker = true,
    this.newAttachmentPickerUi = false,
    this.hideDateAndName = true,
    this.hideAdminNameIcon = false,
    this.groupAdminIcon = 'Default',
    this.quickContactSidebarPosition = 'Top',
    this.quickContactBgColor = 0xFF000000,
    this.quickContactTextColor = 0xFF000000,
    this.hideChatFab = true,
    this.disableMoreOptionsFromBubble = false,
    this.disableDoubleTapReaction = false,
    this.translateOptionSettings = 'Server',
    this.hideMessageTranslationIcon = false,
    this.customWallpaperPerContact = false,
    this.profilePicWallpaper = false,
    this.enableProximitySensor = true,
    this.disableOutputSwitching = false,
    this.playVoiceNotes = false,
    this.forwardAsVoiceNote = false,
    this.incomingMessageRingtone = 'Default',
    this.sendMessageRingtone = 'Default',
    this.actionBarColor,
    this.hideProfilePicture = false,
    this.hideContactName = false,
    this.hideCallButton = false,
    this.disableContactStatus = false,
    this.contactStatusBgColor,
    this.contactStatusTextColor,
    this.messageTextSize = 16.0,
    this.convBackgroundColor,
    this.rightBubbleColor,
    this.rightChatBubbleTextColor,
    this.rightBubbleTimeColor,
    this.leftBubbleColor,
    this.leftChatBubbleTextColor,
    this.leftBubbleTimeColor,
    this.deletedMessageIconColor,
    this.quotedDividerColor,
    this.quotedNameColor,
    this.quotedMessageColor,
    this.quotedBackgroundColor,
    this.makeTextSelectable = true,
    this.removeReadMore = false,
    this.entryStyle = 'Stock',
    this.uiEntryBackgroundColor,
    this.uiButtonsColor,
    this.emojiButtonColor,
    this.sendButtonColor,
    this.micSendBgCircleColor,
    this.textEntryBackgroundColor,
    this.textEntryColor,
    this.emojiHeaderColor,
    this.emojiHeaderIconsColor,
    this.emojiPickerBgColor,
    this.hyperlinksColor,
    this.infoBalloonsTextColor,
    this.infoBalloonsBgColor,
    this.groupParticipantNameColor,
    this.voiceNotePlayingBarColor,
    this.voiceNotePlayButtonColor,
  });

  String get bubbleShape => bubbleStyle;
  double get bubbleRadius => 16.0;

  ConversationPreferences copyWith({
    String? bubbleStyle,
    String? bubbleShape,
    String? tickStyle,
    bool? enableQuickContactSidebar,
    String? sidebarPosition,
    double? sidebarOpacity,
    bool? iosStylePopupMenu,
    String? doubleTapReactionEmoji,
    String? wallpaperType,
    double? voicePlaybackSpeed,
    String? wallpaperPath,
    bool? enableAnimatedEmojis,
    bool? switchDirectContactLink,
    bool? confirmBeforeSendingSticker,
    bool? newAttachmentPickerUi,
    bool? hideDateAndName,
    bool? hideAdminNameIcon,
    String? groupAdminIcon,
    String? quickContactSidebarPosition,
    int? quickContactBgColor,
    int? quickContactTextColor,
    bool? hideChatFab,
    bool? disableMoreOptionsFromBubble,
    bool? disableDoubleTapReaction,
    String? translateOptionSettings,
    bool? hideMessageTranslationIcon,
    bool? customWallpaperPerContact,
    bool? profilePicWallpaper,
    bool? enableProximitySensor,
    bool? disableOutputSwitching,
    bool? playVoiceNotes,
    bool? forwardAsVoiceNote,
    String? incomingMessageRingtone,
    String? sendMessageRingtone,
    int? actionBarColor,
    bool? hideProfilePicture,
    bool? hideContactName,
    bool? hideCallButton,
    bool? disableContactStatus,
    int? contactStatusBgColor,
    int? contactStatusBackgroundColor,
    int? contactStatusTextColor,
    double? messageTextSize,
    int? convBackgroundColor,
    int? conversationBackgroundColor,
    int? rightBubbleColor,
    int? rightChatBubbleTextColor,
    int? rightBubbleTimeColor,
    int? leftBubbleColor,
    int? leftChatBubbleTextColor,
    int? leftBubbleTimeColor,
    int? deletedMessageIconColor,
    int? quotedDividerColor,
    int? quotedNameColor,
    int? quotedMessageColor,
    int? quotedBackgroundColor,
    bool? makeTextSelectable,
    bool? removeReadMore,
    String? entryStyle,
    int? uiEntryBackgroundColor,
    int? entryUiBackgroundColor,
    int? uiButtonsColor,
    int? entryUiButtonsColor,
    int? emojiButtonColor,
    int? entryEmojiButtonColor,
    int? sendButtonColor,
    int? entrySendButtonColor,
    int? micSendBgCircleColor,
    int? entryMicSendBackgroundCircle,
    int? textEntryBackgroundColor,
    int? entryTextBackground,
    int? textEntryColor,
    int? entryTextColor,
    int? emojiHeaderColor,
    int? emojiHeaderIconsColor,
    int? emojiPickerBgColor,
    int? hyperlinksColor,
    int? infoBalloonsTextColor,
    int? infoBalloonsBgColor,
    int? infoBalloonsBackgroundColor,
    int? groupParticipantNameColor,
    int? voiceNotePlayingBarColor,
    int? voiceNotePlayingBar,
    int? voiceNotePlayButtonColor,
    int? voiceNotePlayButton,
    bool clearActionBarColor = false,
    bool clearContactStatusBgColor = false,
    bool clearContactStatusTextColor = false,
    bool clearConvBackgroundColor = false,
    bool clearRightBubbleColor = false,
    bool clearRightChatBubbleTextColor = false,
    bool clearRightBubbleTimeColor = false,
    bool clearLeftBubbleColor = false,
    bool clearLeftChatBubbleTextColor = false,
    bool clearLeftBubbleTimeColor = false,
    bool clearDeletedMessageIconColor = false,
    bool clearQuotedDividerColor = false,
    bool clearQuotedNameColor = false,
    bool clearQuotedMessageColor = false,
    bool clearQuotedBackgroundColor = false,
    bool clearUiEntryBackgroundColor = false,
    bool clearUiButtonsColor = false,
    bool clearEmojiButtonColor = false,
    bool clearSendButtonColor = false,
    bool clearMicSendBgCircleColor = false,
    bool clearTextEntryBackgroundColor = false,
    bool clearTextEntryColor = false,
    bool clearEmojiHeaderColor = false,
    bool clearEmojiHeaderIconsColor = false,
    bool clearEmojiPickerBgColor = false,
    bool clearHyperlinksColor = false,
    bool clearInfoBalloonsTextColor = false,
    bool clearInfoBalloonsBgColor = false,
    bool clearGroupParticipantNameColor = false,
    bool clearVoiceNotePlayingBarColor = false,
    bool clearVoiceNotePlayButtonColor = false,
  }) {
    return ConversationPreferences(
      bubbleStyle: bubbleStyle ?? bubbleShape ?? this.bubbleStyle,
      tickStyle: tickStyle ?? this.tickStyle,
      enableQuickContactSidebar:
          enableQuickContactSidebar ?? this.enableQuickContactSidebar,
      sidebarPosition: sidebarPosition ?? this.sidebarPosition,
      sidebarOpacity: (sidebarOpacity ?? this.sidebarOpacity).clamp(0.1, 1.0),
      iosStylePopupMenu: iosStylePopupMenu ?? this.iosStylePopupMenu,
      doubleTapReactionEmoji:
          doubleTapReactionEmoji ?? this.doubleTapReactionEmoji,
      wallpaperType: wallpaperType ?? this.wallpaperType,
      voicePlaybackSpeed: (voicePlaybackSpeed ?? this.voicePlaybackSpeed).clamp(0.5, 3.0),
      wallpaperPath: wallpaperPath ?? this.wallpaperPath,
      enableAnimatedEmojis: enableAnimatedEmojis ?? this.enableAnimatedEmojis,
      switchDirectContactLink:
          switchDirectContactLink ?? this.switchDirectContactLink,
      confirmBeforeSendingSticker:
          confirmBeforeSendingSticker ?? this.confirmBeforeSendingSticker,
      newAttachmentPickerUi:
          newAttachmentPickerUi ?? this.newAttachmentPickerUi,
      hideDateAndName: hideDateAndName ?? this.hideDateAndName,
      hideAdminNameIcon: hideAdminNameIcon ?? this.hideAdminNameIcon,
      groupAdminIcon: groupAdminIcon ?? this.groupAdminIcon,
      quickContactSidebarPosition:
          quickContactSidebarPosition ?? this.quickContactSidebarPosition,
      quickContactBgColor: quickContactBgColor ?? this.quickContactBgColor,
      quickContactTextColor:
          quickContactTextColor ?? this.quickContactTextColor,
      hideChatFab: hideChatFab ?? this.hideChatFab,
      disableMoreOptionsFromBubble:
          disableMoreOptionsFromBubble ?? this.disableMoreOptionsFromBubble,
      disableDoubleTapReaction:
          disableDoubleTapReaction ?? this.disableDoubleTapReaction,
      translateOptionSettings:
          translateOptionSettings ?? this.translateOptionSettings,
      hideMessageTranslationIcon:
          hideMessageTranslationIcon ?? this.hideMessageTranslationIcon,
      customWallpaperPerContact:
          customWallpaperPerContact ?? this.customWallpaperPerContact,
      profilePicWallpaper: profilePicWallpaper ?? this.profilePicWallpaper,
      enableProximitySensor:
          enableProximitySensor ?? this.enableProximitySensor,
      disableOutputSwitching:
          disableOutputSwitching ?? this.disableOutputSwitching,
      playVoiceNotes: playVoiceNotes ?? this.playVoiceNotes,
      forwardAsVoiceNote: forwardAsVoiceNote ?? this.forwardAsVoiceNote,
      incomingMessageRingtone:
          incomingMessageRingtone ?? this.incomingMessageRingtone,
      sendMessageRingtone: sendMessageRingtone ?? this.sendMessageRingtone,
      actionBarColor: clearActionBarColor
          ? null
          : (actionBarColor ?? this.actionBarColor),
      hideProfilePicture: hideProfilePicture ?? this.hideProfilePicture,
      hideContactName: hideContactName ?? this.hideContactName,
      hideCallButton: hideCallButton ?? this.hideCallButton,
      disableContactStatus:
          disableContactStatus ?? this.disableContactStatus,
      contactStatusBgColor: clearContactStatusBgColor
          ? null
          : (contactStatusBackgroundColor ??
              contactStatusBgColor ??
              this.contactStatusBgColor),
      contactStatusTextColor: clearContactStatusTextColor
          ? null
          : (contactStatusTextColor ?? this.contactStatusTextColor),
      messageTextSize: (messageTextSize ?? this.messageTextSize).clamp(10.0, 30.0),
      convBackgroundColor: clearConvBackgroundColor
          ? null
          : (conversationBackgroundColor ??
              convBackgroundColor ??
              this.convBackgroundColor),
      rightBubbleColor: clearRightBubbleColor
          ? null
          : (rightBubbleColor ?? this.rightBubbleColor),
      rightChatBubbleTextColor: clearRightChatBubbleTextColor
          ? null
          : (rightChatBubbleTextColor ?? this.rightChatBubbleTextColor),
      rightBubbleTimeColor: clearRightBubbleTimeColor
          ? null
          : (rightBubbleTimeColor ?? this.rightBubbleTimeColor),
      leftBubbleColor: clearLeftBubbleColor
          ? null
          : (leftBubbleColor ?? this.leftBubbleColor),
      leftChatBubbleTextColor: clearLeftChatBubbleTextColor
          ? null
          : (leftChatBubbleTextColor ?? this.leftChatBubbleTextColor),
      leftBubbleTimeColor: clearLeftBubbleTimeColor
          ? null
          : (leftBubbleTimeColor ?? this.leftBubbleTimeColor),
      deletedMessageIconColor: clearDeletedMessageIconColor
          ? null
          : (deletedMessageIconColor ?? this.deletedMessageIconColor),
      quotedDividerColor: clearQuotedDividerColor
          ? null
          : (quotedDividerColor ?? this.quotedDividerColor),
      quotedNameColor: clearQuotedNameColor
          ? null
          : (quotedNameColor ?? this.quotedNameColor),
      quotedMessageColor: clearQuotedMessageColor
          ? null
          : (quotedMessageColor ?? this.quotedMessageColor),
      quotedBackgroundColor: clearQuotedBackgroundColor
          ? null
          : (quotedBackgroundColor ?? this.quotedBackgroundColor),
      makeTextSelectable: makeTextSelectable ?? this.makeTextSelectable,
      removeReadMore: removeReadMore ?? this.removeReadMore,
      entryStyle: entryStyle ?? this.entryStyle,
      uiEntryBackgroundColor: clearUiEntryBackgroundColor
          ? null
          : (entryUiBackgroundColor ??
              uiEntryBackgroundColor ??
              this.uiEntryBackgroundColor),
      uiButtonsColor: clearUiButtonsColor
          ? null
          : (entryUiButtonsColor ?? uiButtonsColor ?? this.uiButtonsColor),
      emojiButtonColor: clearEmojiButtonColor
          ? null
          : (entryEmojiButtonColor ?? emojiButtonColor ?? this.emojiButtonColor),
      sendButtonColor: clearSendButtonColor
          ? null
          : (entrySendButtonColor ?? sendButtonColor ?? this.sendButtonColor),
      micSendBgCircleColor: clearMicSendBgCircleColor
          ? null
          : (entryMicSendBackgroundCircle ??
              micSendBgCircleColor ??
              this.micSendBgCircleColor),
      textEntryBackgroundColor: clearTextEntryBackgroundColor
          ? null
          : (entryTextBackground ??
              textEntryBackgroundColor ??
              this.textEntryBackgroundColor),
      textEntryColor: clearTextEntryColor
          ? null
          : (entryTextColor ?? textEntryColor ?? this.textEntryColor),
      emojiHeaderColor: clearEmojiHeaderColor
          ? null
          : (emojiHeaderColor ?? this.emojiHeaderColor),
      emojiHeaderIconsColor: clearEmojiHeaderIconsColor
          ? null
          : (emojiHeaderIconsColor ?? this.emojiHeaderIconsColor),
      emojiPickerBgColor: clearEmojiPickerBgColor
          ? null
          : (emojiPickerBgColor ?? this.emojiPickerBgColor),
      hyperlinksColor: clearHyperlinksColor
          ? null
          : (hyperlinksColor ?? this.hyperlinksColor),
      infoBalloonsTextColor: clearInfoBalloonsTextColor
          ? null
          : (infoBalloonsTextColor ?? this.infoBalloonsTextColor),
      infoBalloonsBgColor: clearInfoBalloonsBgColor
          ? null
          : (infoBalloonsBackgroundColor ??
              infoBalloonsBgColor ??
              this.infoBalloonsBgColor),
      groupParticipantNameColor: clearGroupParticipantNameColor
          ? null
          : (groupParticipantNameColor ?? this.groupParticipantNameColor),
      voiceNotePlayingBarColor: clearVoiceNotePlayingBarColor
          ? null
          : (voiceNotePlayingBar ??
              voiceNotePlayingBarColor ??
              this.voiceNotePlayingBarColor),
      voiceNotePlayButtonColor: clearVoiceNotePlayButtonColor
          ? null
          : (voiceNotePlayButton ??
              voiceNotePlayButtonColor ??
              this.voiceNotePlayButtonColor),
    );
  }

  // Aliases for compatibility
  int? get infoBalloonsBackgroundColor => infoBalloonsBgColor;
  int? get voiceNotePlayingBar => voiceNotePlayingBarColor;
  int? get voiceNotePlayButton => voiceNotePlayButtonColor;
  int? get entryUiBackgroundColor => uiEntryBackgroundColor;
  int? get entryUiButtonsColor => uiButtonsColor;
  int? get entryEmojiButtonColor => emojiButtonColor;
  int? get entrySendButtonColor => sendButtonColor;
  int? get entryMicSendBackgroundCircle => micSendBgCircleColor;
  int? get entryTextBackground => textEntryBackgroundColor;
  int? get entryTextColor => textEntryColor;
  int? get conversationBackgroundColor => convBackgroundColor;
  int? get contactStatusBackgroundColor => contactStatusBgColor;

  Map<String, dynamic> toMap() => {
    'bubbleStyle': bubbleStyle,
    'tickStyle': tickStyle,
    'enableQuickContactSidebar': enableQuickContactSidebar,
    'sidebarPosition': sidebarPosition,
    'sidebarOpacity': sidebarOpacity,
    'iosStylePopupMenu': iosStylePopupMenu,
    'doubleTapReactionEmoji': doubleTapReactionEmoji,
    'wallpaperType': wallpaperType,
    'voicePlaybackSpeed': voicePlaybackSpeed,
    'wallpaperPath': wallpaperPath,
    'enableAnimatedEmojis': enableAnimatedEmojis,
    'switchDirectContactLink': switchDirectContactLink,
    'confirmBeforeSendingSticker': confirmBeforeSendingSticker,
    'newAttachmentPickerUi': newAttachmentPickerUi,
    'hideDateAndName': hideDateAndName,
    'hideAdminNameIcon': hideAdminNameIcon,
    'groupAdminIcon': groupAdminIcon,
    'quickContactSidebarPosition': quickContactSidebarPosition,
    'quickContactBgColor': quickContactBgColor,
    'quickContactTextColor': quickContactTextColor,
    'hideChatFab': hideChatFab,
    'disableMoreOptionsFromBubble': disableMoreOptionsFromBubble,
    'disableDoubleTapReaction': disableDoubleTapReaction,
    'translateOptionSettings': translateOptionSettings,
    'hideMessageTranslationIcon': hideMessageTranslationIcon,
    'customWallpaperPerContact': customWallpaperPerContact,
    'profilePicWallpaper': profilePicWallpaper,
    'enableProximitySensor': enableProximitySensor,
    'disableOutputSwitching': disableOutputSwitching,
    'playVoiceNotes': playVoiceNotes,
    'forwardAsVoiceNote': forwardAsVoiceNote,
    'incomingMessageRingtone': incomingMessageRingtone,
    'sendMessageRingtone': sendMessageRingtone,
    'actionBarColor': actionBarColor,
    'hideProfilePicture': hideProfilePicture,
    'hideContactName': hideContactName,
    'hideCallButton': hideCallButton,
    'disableContactStatus': disableContactStatus,
    'contactStatusBgColor': contactStatusBgColor,
    'contactStatusTextColor': contactStatusTextColor,
    'messageTextSize': messageTextSize,
    'convBackgroundColor': convBackgroundColor,
    'rightBubbleColor': rightBubbleColor,
    'rightChatBubbleTextColor': rightChatBubbleTextColor,
    'rightBubbleTimeColor': rightBubbleTimeColor,
    'leftBubbleColor': leftBubbleColor,
    'leftChatBubbleTextColor': leftChatBubbleTextColor,
    'leftBubbleTimeColor': leftBubbleTimeColor,
    'deletedMessageIconColor': deletedMessageIconColor,
    'quotedDividerColor': quotedDividerColor,
    'quotedNameColor': quotedNameColor,
    'quotedMessageColor': quotedMessageColor,
    'quotedBackgroundColor': quotedBackgroundColor,
    'makeTextSelectable': makeTextSelectable,
    'removeReadMore': removeReadMore,
    'entryStyle': entryStyle,
    'uiEntryBackgroundColor': uiEntryBackgroundColor,
    'uiButtonsColor': uiButtonsColor,
    'emojiButtonColor': emojiButtonColor,
    'sendButtonColor': sendButtonColor,
    'micSendBgCircleColor': micSendBgCircleColor,
    'textEntryBackgroundColor': textEntryBackgroundColor,
    'textEntryColor': textEntryColor,
    'emojiHeaderColor': emojiHeaderColor,
    'emojiHeaderIconsColor': emojiHeaderIconsColor,
    'emojiPickerBgColor': emojiPickerBgColor,
    'hyperlinksColor': hyperlinksColor,
    'infoBalloonsTextColor': infoBalloonsTextColor,
    'infoBalloonsBgColor': infoBalloonsBgColor,
    'groupParticipantNameColor': groupParticipantNameColor,
    'voiceNotePlayingBarColor': voiceNotePlayingBarColor,
    'voiceNotePlayButtonColor': voiceNotePlayButtonColor,
  };

  factory ConversationPreferences.fromMap(Map<String, dynamic> map) =>
      ConversationPreferences(
        bubbleStyle: map['bubbleStyle'] ?? map['bubbleShape'] ?? 'Stock',
        tickStyle: map['tickStyle'] ?? 'RC iOS 11',
        enableQuickContactSidebar: map['enableQuickContactSidebar'] ?? false,
        sidebarPosition: map['sidebarPosition'] ?? 'Right',
        sidebarOpacity: (map['sidebarOpacity'] as num?)?.toDouble() ?? 0.9,
        iosStylePopupMenu: map['iosStylePopupMenu'] ?? true,
        doubleTapReactionEmoji: map['doubleTapReactionEmoji'] ?? '❤️',
        wallpaperType: map['wallpaperType'] ?? 'Pattern',
        voicePlaybackSpeed:
            (map['voicePlaybackSpeed'] as num?)?.toDouble() ?? 1.0,
        wallpaperPath: map['wallpaperPath'] as String? ?? '',
        enableAnimatedEmojis: map['enableAnimatedEmojis'] ?? true,
        switchDirectContactLink: map['switchDirectContactLink'] ?? false,
        confirmBeforeSendingSticker:
            map['confirmBeforeSendingSticker'] ?? true,
        newAttachmentPickerUi: map['newAttachmentPickerUi'] ?? false,
        hideDateAndName: map['hideDateAndName'] ?? true,
        hideAdminNameIcon: map['hideAdminNameIcon'] ?? false,
        groupAdminIcon: map['groupAdminIcon'] ?? 'Default',
        quickContactSidebarPosition:
            map['quickContactSidebarPosition'] ?? 'Top',
        quickContactBgColor: map['quickContactBgColor'] ?? 0xFF000000,
        quickContactTextColor: map['quickContactTextColor'] ?? 0xFF000000,
        hideChatFab: map['hideChatFab'] ?? true,
        disableMoreOptionsFromBubble:
            map['disableMoreOptionsFromBubble'] ?? false,
        disableDoubleTapReaction: map['disableDoubleTapReaction'] ?? false,
        translateOptionSettings:
            map['translateOptionSettings'] ?? 'Server',
        hideMessageTranslationIcon:
            map['hideMessageTranslationIcon'] ?? false,
        customWallpaperPerContact:
            map['customWallpaperPerContact'] ?? false,
        profilePicWallpaper: map['profilePicWallpaper'] ?? false,
        enableProximitySensor: map['enableProximitySensor'] ?? true,
        disableOutputSwitching: map['disableOutputSwitching'] ?? false,
        playVoiceNotes: map['playVoiceNotes'] ?? false,
        forwardAsVoiceNote: map['forwardAsVoiceNote'] ?? false,
        incomingMessageRingtone:
            map['incomingMessageRingtone'] ?? 'Default',
        sendMessageRingtone: map['sendMessageRingtone'] ?? 'Default',
        actionBarColor: map['actionBarColor'] as int?,
        hideProfilePicture: map['hideProfilePicture'] ?? false,
        hideContactName: map['hideContactName'] ?? false,
        hideCallButton: map['hideCallButton'] ?? false,
        disableContactStatus: map['disableContactStatus'] ?? false,
        contactStatusBgColor: (map['contactStatusBgColor'] ?? map['contactStatusBackgroundColor']) as int?,
        contactStatusTextColor: map['contactStatusTextColor'] as int?,
        messageTextSize:
            ((map['messageTextSize'] as num?)?.toDouble() ?? 16.0).clamp(10.0, 30.0),
        convBackgroundColor: (map['convBackgroundColor'] ?? map['conversationBackgroundColor']) as int?,
        rightBubbleColor: map['rightBubbleColor'] as int?,
        rightChatBubbleTextColor: map['rightChatBubbleTextColor'] as int?,
        rightBubbleTimeColor: map['rightBubbleTimeColor'] as int?,
        leftBubbleColor: map['leftBubbleColor'] as int?,
        leftChatBubbleTextColor: map['leftChatBubbleTextColor'] as int?,
        leftBubbleTimeColor: map['leftBubbleTimeColor'] as int?,
        deletedMessageIconColor: map['deletedMessageIconColor'] as int?,
        quotedDividerColor: map['quotedDividerColor'] as int?,
        quotedNameColor: map['quotedNameColor'] as int?,
        quotedMessageColor: map['quotedMessageColor'] as int?,
        quotedBackgroundColor: map['quotedBackgroundColor'] as int?,
        makeTextSelectable: map['makeTextSelectable'] ?? true,
        removeReadMore: map['removeReadMore'] ?? false,
        entryStyle: map['entryStyle'] ?? 'Stock',
        uiEntryBackgroundColor: (map['uiEntryBackgroundColor'] ?? map['entryUiBackgroundColor']) as int?,
        uiButtonsColor: (map['uiButtonsColor'] ?? map['entryUiButtonsColor']) as int?,
        emojiButtonColor: (map['emojiButtonColor'] ?? map['entryEmojiButtonColor']) as int?,
        sendButtonColor: (map['sendButtonColor'] ?? map['entrySendButtonColor']) as int?,
        micSendBgCircleColor: (map['micSendBgCircleColor'] ?? map['entryMicSendBackgroundCircle']) as int?,
        textEntryBackgroundColor: (map['textEntryBackgroundColor'] ?? map['entryTextBackground']) as int?,
        textEntryColor: (map['textEntryColor'] ?? map['entryTextColor']) as int?,
        emojiHeaderColor: map['emojiHeaderColor'] as int?,
        emojiHeaderIconsColor: map['emojiHeaderIconsColor'] as int?,
        emojiPickerBgColor: map['emojiPickerBgColor'] as int?,
        hyperlinksColor: map['hyperlinksColor'] as int?,
        infoBalloonsTextColor: map['infoBalloonsTextColor'] as int?,
        infoBalloonsBgColor: (map['infoBalloonsBgColor'] ?? map['infoBalloonsBackgroundColor']) as int?,
        groupParticipantNameColor: map['groupParticipantNameColor'] as int?,
        voiceNotePlayingBarColor: (map['voiceNotePlayingBarColor'] ?? map['voiceNotePlayingBar']) as int?,
        voiceNotePlayButtonColor: (map['voiceNotePlayButtonColor'] ?? map['voiceNotePlayButton']) as int?,
      );
}

/// Notification Preferences Model
class NotificationPreferences {
  final bool enableGlobalNotifications;
  final bool showSenderAvatar;
  final bool showSenderName;
  final bool showMessagePreview;
  final bool notifyContactOnline;
  final bool notifyStatusViewed;
  final bool notifyTypingStarted;
  final bool notifyMessageDeleted;
  final bool notifyStatusDeleted;

  const NotificationPreferences({
    this.enableGlobalNotifications = true,
    this.showSenderAvatar = true,
    this.showSenderName = true,
    this.showMessagePreview = true,
    this.notifyContactOnline = true,
    this.notifyStatusViewed = true,
    this.notifyTypingStarted = false,
    this.notifyMessageDeleted = true,
    this.notifyStatusDeleted = true,
  });

  NotificationPreferences copyWith({
    bool? enableGlobalNotifications,
    bool? showSenderAvatar,
    bool? showSenderName,
    bool? showMessagePreview,
    bool? notifyContactOnline,
    bool? notifyStatusViewed,
    bool? notifyTypingStarted,
    bool? notifyMessageDeleted,
    bool? notifyStatusDeleted,
  }) {
    return NotificationPreferences(
      enableGlobalNotifications:
          enableGlobalNotifications ?? this.enableGlobalNotifications,
      showSenderAvatar: showSenderAvatar ?? this.showSenderAvatar,
      showSenderName: showSenderName ?? this.showSenderName,
      showMessagePreview: showMessagePreview ?? this.showMessagePreview,
      notifyContactOnline: notifyContactOnline ?? this.notifyContactOnline,
      notifyStatusViewed: notifyStatusViewed ?? this.notifyStatusViewed,
      notifyTypingStarted: notifyTypingStarted ?? this.notifyTypingStarted,
      notifyMessageDeleted: notifyMessageDeleted ?? this.notifyMessageDeleted,
      notifyStatusDeleted: notifyStatusDeleted ?? this.notifyStatusDeleted,
    );
  }

  Map<String, dynamic> toMap() => {
    'enableGlobalNotifications': enableGlobalNotifications,
    'showSenderAvatar': showSenderAvatar,
    'showSenderName': showSenderName,
    'showMessagePreview': showMessagePreview,
    'notifyContactOnline': notifyContactOnline,
    'notifyStatusViewed': notifyStatusViewed,
    'notifyTypingStarted': notifyTypingStarted,
    'notifyMessageDeleted': notifyMessageDeleted,
    'notifyStatusDeleted': notifyStatusDeleted,
  };

  factory NotificationPreferences.fromMap(Map<String, dynamic> map) =>
      NotificationPreferences(
        enableGlobalNotifications: map['enableGlobalNotifications'] ?? true,
        showSenderAvatar: map['showSenderAvatar'] ?? true,
        showSenderName: map['showSenderName'] ?? true,
        showMessagePreview: map['showMessagePreview'] ?? true,
        notifyContactOnline: map['notifyContactOnline'] ?? true,
        notifyStatusViewed: map['notifyStatusViewed'] ?? true,
        notifyTypingStarted: map['notifyTypingStarted'] ?? false,
        notifyMessageDeleted: map['notifyMessageDeleted'] ?? true,
        notifyStatusDeleted: map['notifyStatusDeleted'] ?? true,
      );
}

/// Message Automation & Quick Reply Model
class AutoReplyRule {
  final String id;
  final bool enabled;
  final String keyword;
  final String responseMessage;
  final String recipientFilter; // 'All', 'Contacts', 'Groups'

  const AutoReplyRule({
    required this.id,
    this.enabled = true,
    required this.keyword,
    required this.responseMessage,
    this.recipientFilter = 'All',
  });

  Map<String, dynamic> toMap() => {
    'id': id,
    'enabled': enabled,
    'keyword': keyword,
    'responseMessage': responseMessage,
    'recipientFilter': recipientFilter,
  };

  factory AutoReplyRule.fromMap(Map<String, dynamic> map) => AutoReplyRule(
    id: map['id'],
    enabled: map['enabled'] ?? true,
    keyword: map['keyword'] ?? '',
    responseMessage: map['responseMessage'] ?? '',
    recipientFilter: map['recipientFilter'] ?? 'All',
  );
}

class ScheduledMessageEntry {
  final String id;
  final String recipientId;
  final String recipientName;
  final String text;
  final DateTime scheduledAt;
  final bool isExecuted;

  const ScheduledMessageEntry({
    required this.id,
    required this.recipientId,
    required this.recipientName,
    required this.text,
    required this.scheduledAt,
    this.isExecuted = false,
  });

  ScheduledMessageEntry copyWith({bool? isExecuted}) => ScheduledMessageEntry(
    id: id,
    recipientId: recipientId,
    recipientName: recipientName,
    text: text,
    scheduledAt: scheduledAt,
    isExecuted: isExecuted ?? this.isExecuted,
  );

  Map<String, dynamic> toMap() => {
    'id': id,
    'recipientId': recipientId,
    'recipientName': recipientName,
    'text': text,
    'scheduledAt': scheduledAt.millisecondsSinceEpoch,
    'isExecuted': isExecuted,
  };

  factory ScheduledMessageEntry.fromMap(Map<String, dynamic> map) =>
      ScheduledMessageEntry(
        id: map['id'],
        recipientId: map['recipientId'] ?? '',
        recipientName: map['recipientName'] ?? '',
        text: map['text'] ?? '',
        scheduledAt: DateTime.fromMillisecondsSinceEpoch(
          map['scheduledAt'] ?? DateTime.now().millisecondsSinceEpoch,
        ),
        isExecuted: map['isExecuted'] ?? false,
      );
}

class QuickReplyTemplate {
  final String shortcut; // e.g. '#thanks'
  final String title;
  final String content;

  const QuickReplyTemplate({
    required this.shortcut,
    required this.title,
    required this.content,
  });

  Map<String, dynamic> toMap() => {
    'shortcut': shortcut,
    'title': title,
    'content': content,
  };

  factory QuickReplyTemplate.fromMap(Map<String, dynamic> map) =>
      QuickReplyTemplate(
        shortcut: map['shortcut'] ?? '',
        title: map['title'] ?? '',
        content: map['content'] ?? '',
      );
}

class MessageAutomationPreferences {
  final bool enableAutoReply;
  final List<AutoReplyRule> autoReplyRules;
  final List<ScheduledMessageEntry> scheduledMessages;
  final List<QuickReplyTemplate> quickReplies;

  const MessageAutomationPreferences({
    this.enableAutoReply = false,
    this.autoReplyRules = const [
      AutoReplyRule(
        id: 'rule_1',
        enabled: true,
        keyword: 'busy',
        responseMessage:
            "I am currently in focused mode via Chaty. I'll get back to you shortly!",
      ),
    ],
    this.scheduledMessages = const [],
    this.quickReplies = const [
      QuickReplyTemplate(
        shortcut: '#thanks',
        title: 'Thank you',
        content: 'Thank you so much! Really appreciate it.',
      ),
      QuickReplyTemplate(
        shortcut: '#eta',
        title: 'ETA 5 mins',
        content: 'On my way! Be there in 5 minutes.',
      ),
    ],
  });

  MessageAutomationPreferences copyWith({
    bool? enableAutoReply,
    List<AutoReplyRule>? autoReplyRules,
    List<ScheduledMessageEntry>? scheduledMessages,
    List<QuickReplyTemplate>? quickReplies,
  }) {
    return MessageAutomationPreferences(
      enableAutoReply: enableAutoReply ?? this.enableAutoReply,
      autoReplyRules: autoReplyRules ?? this.autoReplyRules,
      scheduledMessages: scheduledMessages ?? this.scheduledMessages,
      quickReplies: quickReplies ?? this.quickReplies,
    );
  }

  Map<String, dynamic> toMap() => {
    'enableAutoReply': enableAutoReply,
    'autoReplyRules': autoReplyRules.map((r) => r.toMap()).toList(),
    'scheduledMessages': scheduledMessages.map((s) => s.toMap()).toList(),
    'quickReplies': quickReplies.map((q) => q.toMap()).toList(),
  };

  factory MessageAutomationPreferences.fromMap(Map<String, dynamic> map) =>
      MessageAutomationPreferences(
        enableAutoReply: map['enableAutoReply'] ?? false,
        autoReplyRules:
            (map['autoReplyRules'] as List?)
                ?.map((r) => AutoReplyRule.fromMap(r))
                .toList() ??
            [],
        scheduledMessages:
            (map['scheduledMessages'] as List?)
                ?.map((s) => ScheduledMessageEntry.fromMap(s))
                .toList() ??
            [],
        quickReplies:
            (map['quickReplies'] as List?)
                ?.map((q) => QuickReplyTemplate.fromMap(q))
                .toList() ??
            [],
      );
}

/// Navigation Effects & Particle Config Model
class NavigationEffectPreferences {
  // 'Fade', 'Slide', 'Grow', 'Scale', 'Shared Axis', 'Fade Through', 'Cupertino', 'None'
  final bool enableClickParticles;
  final String clickParticleSymbol; // '✨', '❤️', '🔥', '⚡', '⭐', '🌸'
  final double clickParticleSpeed;
  final bool enableFallingParticles;
  final String
  fallingParticleObject; // 'Stars', 'Hearts', 'Snowflakes', 'Leaves'
  final String fallingParticleScope; // 'Home only', 'Chat only', 'Both'

  const NavigationEffectPreferences({
    this.enableClickParticles = false,
    this.clickParticleSymbol = '✨',
    this.clickParticleSpeed = 1.0,
    this.enableFallingParticles = false,
    this.fallingParticleObject = 'Stars',
    this.fallingParticleScope = 'Home only',
  });

  NavigationEffectPreferences copyWith({
    bool? enableClickParticles,
    String? clickParticleSymbol,
    double? clickParticleSpeed,
    bool? enableFallingParticles,
    String? fallingParticleObject,
    String? fallingParticleScope,
  }) {
    return NavigationEffectPreferences(
      enableClickParticles: enableClickParticles ?? this.enableClickParticles,
      clickParticleSymbol: clickParticleSymbol ?? this.clickParticleSymbol,
      clickParticleSpeed: clickParticleSpeed ?? this.clickParticleSpeed,
      enableFallingParticles:
          enableFallingParticles ?? this.enableFallingParticles,
      fallingParticleObject:
          fallingParticleObject ?? this.fallingParticleObject,
      fallingParticleScope: fallingParticleScope ?? this.fallingParticleScope,
    );
  }

  Map<String, dynamic> toMap() => {
    'enableClickParticles': enableClickParticles,
    'clickParticleSymbol': clickParticleSymbol,
    'clickParticleSpeed': clickParticleSpeed,
    'enableFallingParticles': enableFallingParticles,
    'fallingParticleObject': fallingParticleObject,
    'fallingParticleScope': fallingParticleScope,
  };

  factory NavigationEffectPreferences.fromMap(Map<String, dynamic> map) =>
      NavigationEffectPreferences(
        enableClickParticles: map['enableClickParticles'] ?? false,
        clickParticleSymbol: map['clickParticleSymbol'] ?? '✨',
        clickParticleSpeed:
            (map['clickParticleSpeed'] as num?)?.toDouble() ?? 1.0,
        enableFallingParticles: map['enableFallingParticles'] ?? false,
        fallingParticleObject: map['fallingParticleObject'] ?? 'Stars',
        fallingParticleScope: map['fallingParticleScope'] ?? 'Home only',
      );
}

/// Universal Preferences Model (Image 2)
class UniversalPreferences {
  final int? universalColor;
  final int? universalActionBarTextColor;
  final int? backgroundColor;
  final int? listBackgroundColor;
  final int? statusBarColor;
  final int? navigationBarColor;

  final String launcherIcon;
  final String emojiVariant;
  final String notificationIcon;
  final String fontStyle;
  final bool loadFontEnabled;
  bool get loadFontCustom => loadFontEnabled;

  final bool hidePhotosFromGallery;
  bool get hideMediaPhotos => hidePhotosFromGallery;

  final bool hideVideosFromGallery;
  bool get hideMediaVideos => hideVideosFromGallery;

  final bool hideGifsFromGallery;
  bool get hideMediaGifs => hideGifsFromGallery;

  final String translateOptionSettings;
  String get translateOption => translateOptionSettings;

  final String defaultTranslationLanguage;
  String get translationLanguage => defaultTranslationLanguage;

  final bool conversationCards;
  final bool disableHeadsUpNotification;
  final bool disableBadgeCounter;
  final bool disableAudioPlayingNotification;
  final bool increaseForwardLimit;

  final bool disableSwipeToExitConversation;
  bool get disableSwipeToExit => disableSwipeToExitConversation;

  final bool enableAlwaysOnline;

  final String tenorGiphyGifProvider;
  String get gifProvider => tenorGiphyGifProvider;

  final double sendImagesInFullResolutionMb;
  int get sendImagesFullResolutionMb => sendImagesInFullResolutionMb.round();

  const UniversalPreferences({
    this.universalColor,
    this.universalActionBarTextColor,
    this.backgroundColor,
    this.listBackgroundColor,
    this.statusBarColor,
    this.navigationBarColor,
    this.launcherIcon = 'Classic',
    this.emojiVariant = 'WhatsApp',
    this.notificationIcon = 'White',
    this.fontStyle = 'Default',
    this.loadFontEnabled = false,
    this.hidePhotosFromGallery = false,
    this.hideVideosFromGallery = false,
    this.hideGifsFromGallery = false,
    this.translateOptionSettings = 'Server + No outside apps',
    this.defaultTranslationLanguage = 'Show All',
    this.conversationCards = true,
    this.disableHeadsUpNotification = false,
    this.disableBadgeCounter = false,
    this.disableAudioPlayingNotification = false,
    this.increaseForwardLimit = false,
    this.disableSwipeToExitConversation = false,
    this.enableAlwaysOnline = true,
    this.tenorGiphyGifProvider = 'Tenor',
    this.sendImagesInFullResolutionMb = 1.0,
  });

  UniversalPreferences copyWith({
    int? universalColor,
    int? universalActionBarTextColor,
    int? backgroundColor,
    int? listBackgroundColor,
    int? statusBarColor,
    int? navigationBarColor,
    String? launcherIcon,
    String? emojiVariant,
    String? notificationIcon,
    String? fontStyle,
    bool? loadFontEnabled,
    bool? loadFontCustom,
    bool? hidePhotosFromGallery,
    bool? hideMediaPhotos,
    bool? hideVideosFromGallery,
    bool? hideMediaVideos,
    bool? hideGifsFromGallery,
    bool? hideMediaGifs,
    String? translateOptionSettings,
    String? translateOption,
    String? defaultTranslationLanguage,
    String? translationLanguage,
    bool? conversationCards,
    bool? disableHeadsUpNotification,
    bool? disableBadgeCounter,
    bool? disableAudioPlayingNotification,
    bool? increaseForwardLimit,
    bool? disableSwipeToExitConversation,
    bool? disableSwipeToExit,
    bool? enableAlwaysOnline,
    String? tenorGiphyGifProvider,
    String? gifProvider,
    double? sendImagesInFullResolutionMb,
    int? sendImagesFullResolutionMb,
  }) {
    return UniversalPreferences(
      universalColor: universalColor ?? this.universalColor,
      universalActionBarTextColor:
          universalActionBarTextColor ?? this.universalActionBarTextColor,
      backgroundColor: backgroundColor ?? this.backgroundColor,
      listBackgroundColor: listBackgroundColor ?? this.listBackgroundColor,
      statusBarColor: statusBarColor ?? this.statusBarColor,
      navigationBarColor: navigationBarColor ?? this.navigationBarColor,
      launcherIcon: launcherIcon ?? this.launcherIcon,
      emojiVariant: emojiVariant ?? this.emojiVariant,
      notificationIcon: notificationIcon ?? this.notificationIcon,
      fontStyle: fontStyle ?? this.fontStyle,
      loadFontEnabled:
          loadFontCustom ?? loadFontEnabled ?? this.loadFontEnabled,
      hidePhotosFromGallery:
          hideMediaPhotos ?? hidePhotosFromGallery ?? this.hidePhotosFromGallery,
      hideVideosFromGallery:
          hideMediaVideos ?? hideVideosFromGallery ?? this.hideVideosFromGallery,
      hideGifsFromGallery:
          hideMediaGifs ?? hideGifsFromGallery ?? this.hideGifsFromGallery,
      translateOptionSettings:
          translateOption ?? translateOptionSettings ?? this.translateOptionSettings,
      defaultTranslationLanguage:
          translationLanguage ?? defaultTranslationLanguage ?? this.defaultTranslationLanguage,
      conversationCards: conversationCards ?? this.conversationCards,
      disableHeadsUpNotification:
          disableHeadsUpNotification ?? this.disableHeadsUpNotification,
      disableBadgeCounter: disableBadgeCounter ?? this.disableBadgeCounter,
      disableAudioPlayingNotification:
          disableAudioPlayingNotification ?? this.disableAudioPlayingNotification,
      increaseForwardLimit: increaseForwardLimit ?? this.increaseForwardLimit,
      disableSwipeToExitConversation:
          disableSwipeToExit ?? disableSwipeToExitConversation ?? this.disableSwipeToExitConversation,
      enableAlwaysOnline: enableAlwaysOnline ?? this.enableAlwaysOnline,
      tenorGiphyGifProvider:
          gifProvider ?? tenorGiphyGifProvider ?? this.tenorGiphyGifProvider,
      sendImagesInFullResolutionMb: sendImagesFullResolutionMb?.toDouble() ??
          sendImagesInFullResolutionMb ??
          this.sendImagesInFullResolutionMb,
    );
  }

  Map<String, dynamic> toMap() => {
    'universalColor': universalColor,
    'universalActionBarTextColor': universalActionBarTextColor,
    'backgroundColor': backgroundColor,
    'listBackgroundColor': listBackgroundColor,
    'statusBarColor': statusBarColor,
    'navigationBarColor': navigationBarColor,
    'launcherIcon': launcherIcon,
    'emojiVariant': emojiVariant,
    'notificationIcon': notificationIcon,
    'fontStyle': fontStyle,
    'loadFontEnabled': loadFontEnabled,
    'hidePhotosFromGallery': hidePhotosFromGallery,
    'hideVideosFromGallery': hideVideosFromGallery,
    'hideGifsFromGallery': hideGifsFromGallery,
    'translateOptionSettings': translateOptionSettings,
    'defaultTranslationLanguage': defaultTranslationLanguage,
    'conversationCards': conversationCards,
    'disableHeadsUpNotification': disableHeadsUpNotification,
    'disableBadgeCounter': disableBadgeCounter,
    'disableAudioPlayingNotification': disableAudioPlayingNotification,
    'increaseForwardLimit': increaseForwardLimit,
    'disableSwipeToExitConversation': disableSwipeToExitConversation,
    'enableAlwaysOnline': enableAlwaysOnline,
    'tenorGiphyGifProvider': tenorGiphyGifProvider,
    'sendImagesInFullResolutionMb': sendImagesInFullResolutionMb,
  };

  factory UniversalPreferences.fromMap(Map<String, dynamic> map) =>
      UniversalPreferences(
        universalColor: map['universalColor'] as int?,
        universalActionBarTextColor:
            map['universalActionBarTextColor'] as int?,
        backgroundColor: map['backgroundColor'] as int?,
        listBackgroundColor: map['listBackgroundColor'] as int?,
        statusBarColor: map['statusBarColor'] as int?,
        navigationBarColor: map['navigationBarColor'] as int?,
        launcherIcon: map['launcherIcon'] ?? 'Classic',
        emojiVariant: map['emojiVariant'] ?? 'WhatsApp',
        notificationIcon: map['notificationIcon'] ?? 'White',
        fontStyle: map['fontStyle'] ?? 'Default',
        loadFontEnabled: map['loadFontEnabled'] ?? false,
        hidePhotosFromGallery: map['hidePhotosFromGallery'] ?? false,
        hideVideosFromGallery: map['hideVideosFromGallery'] ?? false,
        hideGifsFromGallery: map['hideGifsFromGallery'] ?? false,
        translateOptionSettings:
            map['translateOptionSettings'] ?? 'Server + No outside apps',
        defaultTranslationLanguage:
            map['defaultTranslationLanguage'] ?? 'Show All',
        conversationCards: map['conversationCards'] ?? true,
        disableHeadsUpNotification:
            map['disableHeadsUpNotification'] ?? false,
        disableBadgeCounter: map['disableBadgeCounter'] ?? false,
        disableAudioPlayingNotification:
            map['disableAudioPlayingNotification'] ?? false,
        increaseForwardLimit: map['increaseForwardLimit'] ?? false,
        disableSwipeToExitConversation:
            map['disableSwipeToExitConversation'] ?? false,
        enableAlwaysOnline: map['enableAlwaysOnline'] ?? true,
        tenorGiphyGifProvider: map['tenorGiphyGifProvider'] ?? 'Tenor',
        sendImagesInFullResolutionMb:
            (map['sendImagesInFullResolutionMb'] as num?)?.toDouble() ?? 1.0,
      );
}

/// Status Screen Customization Model (Image 3)
class StatusPreferences {
  final bool enableInstagramStories;
  final bool carouselView;
  final String storiesStyle;
  final String activateNewStatusStyle;
  final String statusReactionEmoji;
  final int? recentUpdatesBarColor;
  final int? recentUpdatesTextColor;
  final int? contactNameColor;
  final int? statusSeenColor;
  final int? statusUnSeenColor;
  final int? counterBackgroundColor;
  final int? counterTextColor;
  final bool statusAroundProfile;
  final bool saveAndMarkSeenOptions;
  final bool changePhotoProfileStatusPreview;
  final bool startStoriesDirectlyWithSound;
  final bool confirmBeforeSendingStatus;
  final bool fiveMinuteStatus;

  const StatusPreferences({
    this.enableInstagramStories = false,
    this.carouselView = false,
    this.storiesStyle = 'Instagram',
    this.activateNewStatusStyle = 'Old Status with Thumbnail',
    this.statusReactionEmoji = '💚',
    this.recentUpdatesBarColor,
    this.recentUpdatesTextColor,
    this.contactNameColor,
    this.statusSeenColor,
    this.statusUnSeenColor,
    this.counterBackgroundColor,
    this.counterTextColor,
    this.statusAroundProfile = false,
    this.saveAndMarkSeenOptions = false,
    this.changePhotoProfileStatusPreview = false,
    this.startStoriesDirectlyWithSound = false,
    this.confirmBeforeSendingStatus = true,
    this.fiveMinuteStatus = false,
  });

  StatusPreferences copyWith({
    bool? enableInstagramStories,
    bool? carouselView,
    String? storiesStyle,
    String? activateNewStatusStyle,
    String? statusReactionEmoji,
    int? recentUpdatesBarColor,
    int? recentUpdatesTextColor,
    int? contactNameColor,
    int? statusSeenColor,
    int? statusUnSeenColor,
    int? statusUnseenColor,
    int? counterBackgroundColor,
    int? counterTextColor,
    bool? statusAroundProfile,
    bool? saveAndMarkSeenOptions,
    bool? changePhotoProfileStatusPreview,
    bool? startStoriesDirectlyWithSound,
    bool? startStoriesWithSound,
    bool? confirmBeforeSendingStatus,
    bool? fiveMinuteStatus,
  }) {
    return StatusPreferences(
      enableInstagramStories:
          enableInstagramStories ?? this.enableInstagramStories,
      carouselView: carouselView ?? this.carouselView,
      storiesStyle: storiesStyle ?? this.storiesStyle,
      activateNewStatusStyle:
          activateNewStatusStyle ?? this.activateNewStatusStyle,
      statusReactionEmoji: statusReactionEmoji ?? this.statusReactionEmoji,
      recentUpdatesBarColor:
          recentUpdatesBarColor ?? this.recentUpdatesBarColor,
      recentUpdatesTextColor:
          recentUpdatesTextColor ?? this.recentUpdatesTextColor,
      contactNameColor: contactNameColor ?? this.contactNameColor,
      statusSeenColor: statusSeenColor ?? this.statusSeenColor,
      statusUnSeenColor:
          statusUnseenColor ?? statusUnSeenColor ?? this.statusUnSeenColor,
      counterBackgroundColor:
          counterBackgroundColor ?? this.counterBackgroundColor,
      counterTextColor: counterTextColor ?? this.counterTextColor,
      statusAroundProfile: statusAroundProfile ?? this.statusAroundProfile,
      saveAndMarkSeenOptions:
          saveAndMarkSeenOptions ?? this.saveAndMarkSeenOptions,
      changePhotoProfileStatusPreview:
          changePhotoProfileStatusPreview ?? this.changePhotoProfileStatusPreview,
      startStoriesDirectlyWithSound:
          startStoriesWithSound ??
          startStoriesDirectlyWithSound ??
          this.startStoriesDirectlyWithSound,
      confirmBeforeSendingStatus:
          confirmBeforeSendingStatus ?? this.confirmBeforeSendingStatus,
      fiveMinuteStatus: fiveMinuteStatus ?? this.fiveMinuteStatus,
    );
  }

  // Aliases for compatibility
  int? get statusUnseenColor => statusUnSeenColor;
  bool get startStoriesWithSound => startStoriesDirectlyWithSound;

  Map<String, dynamic> toMap() => {
    'enableInstagramStories': enableInstagramStories,
    'carouselView': carouselView,
    'storiesStyle': storiesStyle,
    'activateNewStatusStyle': activateNewStatusStyle,
    'statusReactionEmoji': statusReactionEmoji,
    'recentUpdatesBarColor': recentUpdatesBarColor,
    'recentUpdatesTextColor': recentUpdatesTextColor,
    'contactNameColor': contactNameColor,
    'statusSeenColor': statusSeenColor,
    'statusUnSeenColor': statusUnSeenColor,
    'counterBackgroundColor': counterBackgroundColor,
    'counterTextColor': counterTextColor,
    'statusAroundProfile': statusAroundProfile,
    'saveAndMarkSeenOptions': saveAndMarkSeenOptions,
    'changePhotoProfileStatusPreview': changePhotoProfileStatusPreview,
    'startStoriesDirectlyWithSound': startStoriesDirectlyWithSound,
    'confirmBeforeSendingStatus': confirmBeforeSendingStatus,
    'fiveMinuteStatus': fiveMinuteStatus,
  };

  factory StatusPreferences.fromMap(Map<String, dynamic> map) =>
      StatusPreferences(
        enableInstagramStories: map['enableInstagramStories'] ?? false,
        carouselView: map['carouselView'] ?? false,
        storiesStyle: map['storiesStyle'] ?? 'Instagram',
        activateNewStatusStyle:
            map['activateNewStatusStyle'] ?? 'Old Status with Thumbnail',
        statusReactionEmoji: map['statusReactionEmoji'] ?? '💚',
        recentUpdatesBarColor: map['recentUpdatesBarColor'] as int?,
        recentUpdatesTextColor: map['recentUpdatesTextColor'] as int?,
        contactNameColor: map['contactNameColor'] as int?,
        statusSeenColor: map['statusSeenColor'] as int?,
        statusUnSeenColor:
            (map['statusUnSeenColor'] ?? map['statusUnseenColor']) as int?,
        counterBackgroundColor: map['counterBackgroundColor'] as int?,
        counterTextColor: map['counterTextColor'] as int?,
        statusAroundProfile: map['statusAroundProfile'] ?? false,
        saveAndMarkSeenOptions: map['saveAndMarkSeenOptions'] ?? false,
        changePhotoProfileStatusPreview:
            map['changePhotoProfileStatusPreview'] ?? false,
        startStoriesDirectlyWithSound:
            map['startStoriesDirectlyWithSound'] ??
            map['startStoriesWithSound'] ??
            false,
        confirmBeforeSendingStatus:
            map['confirmBeforeSendingStatus'] ?? true,
        fiveMinuteStatus: map['fiveMinuteStatus'] ?? false,
      );
}

/// Notification Toast Customization Model (Image 4)
class NotificationToastPreferences {
  final bool onlineToastEnabled;
  final bool onlineToastInNotificationBar;
  final bool onlineToastWithProfilePicture;
  final bool onlineToastElevation;
  final double onlineToastRadius;
  final String onlineToastRingtone;
  final String onlineToastPosition;
  final int? onlineToastBgColor;
  final int? onlineToastTextColor;

  final bool storyToastEnabled;
  final bool storyToastInNotificationBar;
  final bool storyToastWithProfilePicture;
  final bool storyToastElevation;
  final double storyToastRadius;
  final String storyToastRingtone;
  final String storyToastPosition;
  final int? storyToastBgColor;
  final int? storyToastTextColor;

  final bool profileToastEnabled;
  final bool profileToastInNotificationBar;
  final bool profileToastWithProfilePicture;
  final bool profileToastElevation;
  final double profileToastRadius;
  final String profileToastRingtone;
  final String profileToastPosition;
  final int? profileToastBgColor;
  final int? profileToastTextColor;

  final bool typingToastEnabled;
  final bool typingToastInNotificationBar;
  final bool typingToastWithProfilePicture;
  final bool typingToastElevation;
  final double typingToastRadius;
  final String typingToastRingtone;
  final String typingToastPosition;
  final int? typingToastBgColor;
  final int? typingToastTextColor;

  bool get onlineToast => onlineToastEnabled;
  bool get onlineWithProfilePic => onlineToastWithProfilePicture;
  bool get onlineElevation => onlineToastElevation;
  double get onlineRadius => onlineToastRadius;
  String get onlinePosition => onlineToastPosition;
  int? get onlineBgColor => onlineToastBgColor;
  int? get onlineTextColor => onlineToastTextColor;
  bool get disableContactOnlineToast => !onlineToastEnabled;
  String get onlineRingtone => onlineToastRingtone;
  bool get onlineInNotificationBar => onlineToastInNotificationBar;

  bool get storyToast => storyToastEnabled;
  bool get viewedStoryToast => storyToastEnabled;
  bool get storyInNotificationBar => storyToastInNotificationBar;
  bool get storyWithProfilePic => storyToastWithProfilePicture;
  bool get storyElevation => storyToastElevation;
  double get storyRadius => storyToastRadius;
  String get storyPosition => storyToastPosition;
  int? get storyBgColor => storyToastBgColor;
  int? get storyTextColor => storyToastTextColor;
  String get storyRingtone => storyToastRingtone;

  bool get profileToast => profileToastEnabled;
  bool get profileWithProfilePic => profileToastWithProfilePicture;
  bool get profileElevation => profileToastElevation;
  double get profileRadius => profileToastRadius;
  String get profilePosition => profileToastPosition;
  int? get profileBgColor => profileToastBgColor;
  int? get profileTextColor => profileToastTextColor;
  bool get profileInNotificationBar => profileToastInNotificationBar;
  String get profileRingtone => profileToastRingtone;

  bool get typingToast => typingToastEnabled;
  bool get typingWithProfilePic => typingToastWithProfilePicture;
  bool get typingElevation => typingToastElevation;
  double get typingRadius => typingToastRadius;
  String get typingPosition => typingToastPosition;
  int? get typingBgColor => typingToastBgColor;
  int? get typingTextColor => typingToastTextColor;
  bool get typingInNotificationBar => typingToastInNotificationBar;
  String get typingRingtone => typingToastRingtone;

  const NotificationToastPreferences({
    this.onlineToastEnabled = true,
    this.onlineToastInNotificationBar = false,
    this.onlineToastWithProfilePicture = true,
    this.onlineToastElevation = true,
    this.onlineToastRadius = 8.0,
    this.onlineToastRingtone = 'Default',
    this.onlineToastPosition = 'Top of the screen',
    this.onlineToastBgColor,
    this.onlineToastTextColor,
    this.storyToastEnabled = false,
    this.storyToastInNotificationBar = false,
    this.storyToastWithProfilePicture = true,
    this.storyToastElevation = true,
    this.storyToastRadius = 8.0,
    this.storyToastRingtone = 'Default',
    this.storyToastPosition = 'Top of the screen',
    this.storyToastBgColor,
    this.storyToastTextColor,
    this.profileToastEnabled = false,
    this.profileToastInNotificationBar = false,
    this.profileToastWithProfilePicture = true,
    this.profileToastElevation = true,
    this.profileToastRadius = 8.0,
    this.profileToastRingtone = 'Default',
    this.profileToastPosition = 'Top of the screen',
    this.profileToastBgColor,
    this.profileToastTextColor,
    this.typingToastEnabled = true,
    this.typingToastInNotificationBar = false,
    this.typingToastWithProfilePicture = true,
    this.typingToastElevation = true,
    this.typingToastRadius = 8.0,
    this.typingToastRingtone = 'Default',
    this.typingToastPosition = 'Top of the screen',
    this.typingToastBgColor,
    this.typingToastTextColor,
  });

  NotificationToastPreferences copyWith({
    bool? onlineToastEnabled,
    bool? onlineToast,
    bool? disableContactOnlineToast,
    bool? onlineToastInNotificationBar,
    bool? onlineInNotificationBar,
    bool? onlineToastWithProfilePicture,
    bool? onlineWithProfilePic,
    bool? onlineToastElevation,
    bool? onlineElevation,
    double? onlineToastRadius,
    double? onlineRadius,
    String? onlineToastRingtone,
    String? onlineRingtone,
    String? onlineToastPosition,
    String? onlinePosition,
    int? onlineToastBgColor,
    int? onlineBgColor,
    int? onlineToastTextColor,
    int? onlineTextColor,
    bool? storyToastEnabled,
    bool? storyToast,
    bool? viewedStoryToast,
    bool? storyToastInNotificationBar,
    bool? storyInNotificationBar,
    bool? storyToastWithProfilePicture,
    bool? storyWithProfilePic,
    bool? storyToastElevation,
    bool? storyElevation,
    double? storyToastRadius,
    double? storyRadius,
    String? storyToastRingtone,
    String? storyRingtone,
    String? storyToastPosition,
    String? storyPosition,
    int? storyToastBgColor,
    int? storyBgColor,
    int? storyToastTextColor,
    int? storyTextColor,
    bool? profileToastEnabled,
    bool? profileToast,
    bool? profileToastInNotificationBar,
    bool? profileInNotificationBar,
    bool? profileToastWithProfilePicture,
    bool? profileWithProfilePic,
    bool? profileToastElevation,
    bool? profileElevation,
    double? profileToastRadius,
    double? profileRadius,
    String? profileToastRingtone,
    String? profileRingtone,
    String? profileToastPosition,
    String? profilePosition,
    int? profileToastBgColor,
    int? profileBgColor,
    int? profileToastTextColor,
    int? profileTextColor,
    bool? typingToastEnabled,
    bool? typingToast,
    bool? typingToastInNotificationBar,
    bool? typingInNotificationBar,
    bool? typingToastWithProfilePicture,
    bool? typingWithProfilePic,
    bool? typingToastElevation,
    bool? typingElevation,
    double? typingToastRadius,
    double? typingRadius,
    String? typingToastRingtone,
    String? typingRingtone,
    String? typingToastPosition,
    String? typingPosition,
    int? typingToastBgColor,
    int? typingBgColor,
    int? typingToastTextColor,
    int? typingTextColor,
  }) {
    return NotificationToastPreferences(
      onlineToastEnabled: (disableContactOnlineToast != null
              ? !disableContactOnlineToast
              : null) ??
          onlineToast ??
          onlineToastEnabled ??
          this.onlineToastEnabled,
      onlineToastInNotificationBar: onlineInNotificationBar ??
          onlineToastInNotificationBar ??
          this.onlineToastInNotificationBar,
      onlineToastWithProfilePicture: onlineWithProfilePic ??
          onlineToastWithProfilePicture ??
          this.onlineToastWithProfilePicture,
      onlineToastElevation:
          onlineElevation ?? onlineToastElevation ?? this.onlineToastElevation,
      onlineToastRadius:
          onlineRadius ?? onlineToastRadius ?? this.onlineToastRadius,
      onlineToastRingtone: onlineRingtone ??
          onlineToastRingtone ??
          this.onlineToastRingtone,
      onlineToastPosition:
          onlinePosition ?? onlineToastPosition ?? this.onlineToastPosition,
      onlineToastBgColor:
          onlineBgColor ?? onlineToastBgColor ?? this.onlineToastBgColor,
      onlineToastTextColor:
          onlineTextColor ?? onlineToastTextColor ?? this.onlineToastTextColor,
      storyToastEnabled: viewedStoryToast ??
          storyToast ??
          storyToastEnabled ??
          this.storyToastEnabled,
      storyToastInNotificationBar: storyInNotificationBar ??
          storyToastInNotificationBar ??
          this.storyToastInNotificationBar,
      storyToastWithProfilePicture: storyWithProfilePic ??
          storyToastWithProfilePicture ??
          this.storyToastWithProfilePicture,
      storyToastElevation:
          storyElevation ?? storyToastElevation ?? this.storyToastElevation,
      storyToastRadius:
          storyRadius ?? storyToastRadius ?? this.storyToastRadius,
      storyToastRingtone: storyRingtone ??
          storyToastRingtone ??
          this.storyToastRingtone,
      storyToastPosition:
          storyPosition ?? storyToastPosition ?? this.storyToastPosition,
      storyToastBgColor:
          storyBgColor ?? storyToastBgColor ?? this.storyToastBgColor,
      storyToastTextColor:
          storyTextColor ?? storyToastTextColor ?? this.storyToastTextColor,
      profileToastEnabled:
          profileToast ?? profileToastEnabled ?? this.profileToastEnabled,
      profileToastInNotificationBar: profileInNotificationBar ??
          profileToastInNotificationBar ??
          this.profileToastInNotificationBar,
      profileToastWithProfilePicture: profileWithProfilePic ??
          profileToastWithProfilePicture ??
          this.profileToastWithProfilePicture,
      profileToastElevation: profileElevation ??
          profileToastElevation ??
          this.profileToastElevation,
      profileToastRadius:
          profileRadius ?? profileToastRadius ?? this.profileToastRadius,
      profileToastRingtone: profileRingtone ??
          profileToastRingtone ??
          this.profileToastRingtone,
      profileToastPosition:
          profilePosition ?? profileToastPosition ?? this.profileToastPosition,
      profileToastBgColor:
          profileBgColor ?? profileToastBgColor ?? this.profileToastBgColor,
      profileToastTextColor: profileTextColor ??
          profileToastTextColor ??
          this.profileToastTextColor,
      typingToastEnabled:
          typingToast ?? typingToastEnabled ?? this.typingToastEnabled,
      typingToastInNotificationBar: typingInNotificationBar ??
          typingToastInNotificationBar ??
          this.typingToastInNotificationBar,
      typingToastWithProfilePicture: typingWithProfilePic ??
          typingToastWithProfilePicture ??
          this.typingToastWithProfilePicture,
      typingToastElevation:
          typingElevation ?? typingToastElevation ?? this.typingToastElevation,
      typingToastRadius:
          typingRadius ?? typingToastRadius ?? this.typingToastRadius,
      typingToastRingtone: typingRingtone ??
          typingToastRingtone ??
          this.typingToastRingtone,
      typingToastPosition:
          typingPosition ?? typingToastPosition ?? this.typingToastPosition,
      typingToastBgColor:
          typingBgColor ?? typingToastBgColor ?? this.typingToastBgColor,
      typingToastTextColor:
          typingTextColor ?? typingToastTextColor ?? this.typingToastTextColor,
    );
  }

  Map<String, dynamic> toMap() => {
    'onlineToastEnabled': onlineToastEnabled,
    'onlineToastInNotificationBar': onlineToastInNotificationBar,
    'onlineToastWithProfilePicture': onlineToastWithProfilePicture,
    'onlineToastElevation': onlineToastElevation,
    'onlineToastRadius': onlineToastRadius,
    'onlineToastRingtone': onlineToastRingtone,
    'onlineToastPosition': onlineToastPosition,
    'onlineToastBgColor': onlineToastBgColor,
    'onlineToastTextColor': onlineToastTextColor,
    'storyToastEnabled': storyToastEnabled,
    'storyToastInNotificationBar': storyToastInNotificationBar,
    'storyToastWithProfilePicture': storyToastWithProfilePicture,
    'storyToastElevation': storyToastElevation,
    'storyToastRadius': storyToastRadius,
    'storyToastRingtone': storyToastRingtone,
    'storyToastPosition': storyToastPosition,
    'storyToastBgColor': storyToastBgColor,
    'storyToastTextColor': storyToastTextColor,
    'profileToastEnabled': profileToastEnabled,
    'profileToastInNotificationBar': profileToastInNotificationBar,
    'profileToastWithProfilePicture': profileToastWithProfilePicture,
    'profileToastElevation': profileToastElevation,
    'profileToastRadius': profileToastRadius,
    'profileToastRingtone': profileToastRingtone,
    'profileToastPosition': profileToastPosition,
    'profileToastBgColor': profileToastBgColor,
    'profileToastTextColor': profileToastTextColor,
    'typingToastEnabled': typingToastEnabled,
    'typingToastInNotificationBar': typingToastInNotificationBar,
    'typingToastWithProfilePicture': typingToastWithProfilePicture,
    'typingToastElevation': typingToastElevation,
    'typingToastRadius': typingToastRadius,
    'typingToastRingtone': typingToastRingtone,
    'typingToastPosition': typingToastPosition,
    'typingToastBgColor': typingToastBgColor,
    'typingToastTextColor': typingToastTextColor,
  };

  factory NotificationToastPreferences.fromMap(Map<String, dynamic> map) =>
      NotificationToastPreferences(
        onlineToastEnabled: map['onlineToastEnabled'] ?? true,
        onlineToastInNotificationBar:
            map['onlineToastInNotificationBar'] ?? false,
        onlineToastWithProfilePicture:
            map['onlineToastWithProfilePicture'] ?? true,
        onlineToastElevation: map['onlineToastElevation'] ?? true,
        onlineToastRadius:
            (map['onlineToastRadius'] as num?)?.toDouble() ?? 8.0,
        onlineToastRingtone: map['onlineToastRingtone'] ?? 'Default',
        onlineToastPosition:
            map['onlineToastPosition'] ?? 'Top of the screen',
        onlineToastBgColor: map['onlineToastBgColor'] as int?,
        onlineToastTextColor: map['onlineToastTextColor'] as int?,
        storyToastEnabled: map['storyToastEnabled'] ?? false,
        storyToastInNotificationBar:
            map['storyToastInNotificationBar'] ?? false,
        storyToastWithProfilePicture:
            map['storyToastWithProfilePicture'] ?? true,
        storyToastElevation: map['storyToastElevation'] ?? true,
        storyToastRadius:
            (map['storyToastRadius'] as num?)?.toDouble() ?? 8.0,
        storyToastRingtone: map['storyToastRingtone'] ?? 'Default',
        storyToastPosition: map['storyToastPosition'] ?? 'Top of the screen',
        storyToastBgColor: map['storyToastBgColor'] as int?,
        storyToastTextColor: map['storyToastTextColor'] as int?,
        profileToastEnabled: map['profileToastEnabled'] ?? false,
        profileToastInNotificationBar:
            map['profileToastInNotificationBar'] ?? false,
        profileToastWithProfilePicture:
            map['profileToastWithProfilePicture'] ?? true,
        profileToastElevation: map['profileToastElevation'] ?? true,
        profileToastRadius:
            (map['profileToastRadius'] as num?)?.toDouble() ?? 8.0,
        profileToastRingtone: map['profileToastRingtone'] ?? 'Default',
        profileToastPosition:
            map['profileToastPosition'] ?? 'Top of the screen',
        profileToastBgColor: map['profileToastBgColor'] as int?,
        profileToastTextColor: map['profileToastTextColor'] as int?,
        typingToastEnabled: map['typingToastEnabled'] ?? true,
        typingToastInNotificationBar:
            map['typingToastInNotificationBar'] ?? false,
        typingToastWithProfilePicture:
            map['typingToastWithProfilePicture'] ?? true,
        typingToastElevation: map['typingToastElevation'] ?? true,
        typingToastRadius:
            (map['typingToastRadius'] as num?)?.toDouble() ?? 8.0,
        typingToastRingtone: map['typingToastRingtone'] ?? 'Default',
        typingToastPosition:
            map['typingToastPosition'] ?? 'Top of the screen',
        typingToastBgColor: map['typingToastBgColor'] as int?,
        typingToastTextColor: map['typingToastTextColor'] as int?,
      );
}
