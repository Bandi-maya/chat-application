/// Privacy Preferences Model
class PrivacyPreferences {
  final bool freezeLastSeen;
  final String frozenLastSeenTime;
  final String hideLastSeenAudience; // 'Everyone', 'My Contacts', 'My Contacts Except...', 'Nobody'
  final String hideOnlineAudience; // 'Everyone', 'Same as Last Seen'
  final bool antiViewOnce;
  final bool disableForwardedLabel;
  final bool readReceipts;
  final bool typingIndicators;
  final bool recordingIndicators;
  final bool linkPreviews;
  final String whoCanCallMe; // 'Everyone', 'My Contacts', 'My Contacts Except...', 'Nobody'
  final bool hidePrivacyOption;
  final bool hideUpdateOption;
  final bool disableChannels;
  final bool hideViewStatus;
  final bool antiDeleteStatus;
  final bool statusRevocationAlert;
  final bool antiDisappearingMessages;
  final bool showEditedMessage;
  final bool antiDeleteMessages;
  final bool messageRevokeAlert;
  final bool showBlueTicksAfterReply;

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
    this.linkPreviews = true,
    this.whoCanCallMe = 'Everyone',
    this.hidePrivacyOption = false,
    this.hideUpdateOption = false,
    this.disableChannels = false,
    this.hideViewStatus = false,
    this.antiDeleteStatus = true,
    this.statusRevocationAlert = true,
    this.antiDisappearingMessages = true,
    this.showEditedMessage = true,
    this.antiDeleteMessages = true,
    this.messageRevokeAlert = true,
    this.showBlueTicksAfterReply = false,
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
    bool? linkPreviews,
    String? whoCanCallMe,
    bool? hidePrivacyOption,
    bool? hideUpdateOption,
    bool? disableChannels,
    bool? hideViewStatus,
    bool? antiDeleteStatus,
    bool? statusRevocationAlert,
    bool? antiDisappearingMessages,
    bool? showEditedMessage,
    bool? antiDeleteMessages,
    bool? messageRevokeAlert,
    bool? showBlueTicksAfterReply,
  }) {
    return PrivacyPreferences(
      freezeLastSeen: freezeLastSeen ?? this.freezeLastSeen,
      frozenLastSeenTime: frozenLastSeenTime ?? this.frozenLastSeenTime,
      hideLastSeenAudience: hideLastSeenAudience ?? this.hideLastSeenAudience,
      hideOnlineAudience: hideOnlineAudience ?? this.hideOnlineAudience,
      antiViewOnce: antiViewOnce ?? this.antiViewOnce,
      disableForwardedLabel: disableForwardedLabel ?? this.disableForwardedLabel,
      readReceipts: readReceipts ?? this.readReceipts,
      typingIndicators: typingIndicators ?? this.typingIndicators,
      recordingIndicators: recordingIndicators ?? this.recordingIndicators,
      linkPreviews: linkPreviews ?? this.linkPreviews,
      whoCanCallMe: whoCanCallMe ?? this.whoCanCallMe,
      hidePrivacyOption: hidePrivacyOption ?? this.hidePrivacyOption,
      hideUpdateOption: hideUpdateOption ?? this.hideUpdateOption,
      disableChannels: disableChannels ?? this.disableChannels,
      hideViewStatus: hideViewStatus ?? this.hideViewStatus,
      antiDeleteStatus: antiDeleteStatus ?? this.antiDeleteStatus,
      statusRevocationAlert: statusRevocationAlert ?? this.statusRevocationAlert,
      antiDisappearingMessages: antiDisappearingMessages ?? this.antiDisappearingMessages,
      showEditedMessage: showEditedMessage ?? this.showEditedMessage,
      antiDeleteMessages: antiDeleteMessages ?? this.antiDeleteMessages,
      messageRevokeAlert: messageRevokeAlert ?? this.messageRevokeAlert,
      showBlueTicksAfterReply: showBlueTicksAfterReply ?? this.showBlueTicksAfterReply,
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
        'linkPreviews': linkPreviews,
        'whoCanCallMe': whoCanCallMe,
        'hidePrivacyOption': hidePrivacyOption,
        'hideUpdateOption': hideUpdateOption,
        'disableChannels': disableChannels,
        'hideViewStatus': hideViewStatus,
        'antiDeleteStatus': antiDeleteStatus,
        'statusRevocationAlert': statusRevocationAlert,
        'antiDisappearingMessages': antiDisappearingMessages,
        'showEditedMessage': showEditedMessage,
        'antiDeleteMessages': antiDeleteMessages,
        'messageRevokeAlert': messageRevokeAlert,
        'showBlueTicksAfterReply': showBlueTicksAfterReply,
      };

  factory PrivacyPreferences.fromMap(Map<String, dynamic> map) => PrivacyPreferences(
        freezeLastSeen: map['freezeLastSeen'] ?? false,
        frozenLastSeenTime: map['frozenLastSeenTime'] ?? '',
        hideLastSeenAudience: map['hideLastSeenAudience'] ?? 'My Contacts',
        hideOnlineAudience: map['hideOnlineAudience'] ?? 'Everyone',
        antiViewOnce: map['antiViewOnce'] ?? true,
        disableForwardedLabel: map['disableForwardedLabel'] ?? false,
        readReceipts: map['readReceipts'] ?? true,
        typingIndicators: map['typingIndicators'] ?? true,
        recordingIndicators: map['recordingIndicators'] ?? true,
        linkPreviews: map['linkPreviews'] ?? true,
        whoCanCallMe: map['whoCanCallMe'] ?? 'Everyone',
        hidePrivacyOption: map['hidePrivacyOption'] ?? false,
        hideUpdateOption: map['hideUpdateOption'] ?? false,
        disableChannels: map['disableChannels'] ?? false,
        hideViewStatus: map['hideViewStatus'] ?? false,
        antiDeleteStatus: map['antiDeleteStatus'] ?? true,
        statusRevocationAlert: map['statusRevocationAlert'] ?? true,
        antiDisappearingMessages: map['antiDisappearingMessages'] ?? true,
        showEditedMessage: map['showEditedMessage'] ?? true,
        antiDeleteMessages: map['antiDeleteMessages'] ?? true,
        messageRevokeAlert: map['messageRevokeAlert'] ?? true,
        showBlueTicksAfterReply: map['showBlueTicksAfterReply'] ?? false,
      );
}

/// Security Preferences & App Lock Model
class SecurityPreferences {
  final bool isAppLockEnabled;
  final String lockMethod; // 'Biometric', 'PIN', 'Pattern', 'Password'
  final String? pinCode;
  final String? password;
  final String? patternCode;
  final String? recoveryQuestion;
  final String? recoveryAnswer;
  final bool makePatternInvisible;
  final bool disablePatternVibration;
  final String autoLockTimeout; // 'Immediately', '15s', '30s', '1m', '5m', '15m'
  final bool hideLockNotificationContent;
  final List<String> lockedConversationIds;

  const SecurityPreferences({
    this.isAppLockEnabled = false,
    this.lockMethod = 'PIN',
    this.pinCode,
    this.password,
    this.patternCode,
    this.recoveryQuestion,
    this.recoveryAnswer,
    this.makePatternInvisible = false,
    this.disablePatternVibration = false,
    this.autoLockTimeout = '1m',
    this.hideLockNotificationContent = true,
    this.lockedConversationIds = const [],
  });

  SecurityPreferences copyWith({
    bool? isAppLockEnabled,
    String? lockMethod,
    String? pinCode,
    String? password,
    String? patternCode,
    String? recoveryQuestion,
    String? recoveryAnswer,
    bool? makePatternInvisible,
    bool? disablePatternVibration,
    String? autoLockTimeout,
    bool? hideLockNotificationContent,
    List<String>? lockedConversationIds,
  }) {
    return SecurityPreferences(
      isAppLockEnabled: isAppLockEnabled ?? this.isAppLockEnabled,
      lockMethod: lockMethod ?? this.lockMethod,
      pinCode: pinCode ?? this.pinCode,
      password: password ?? this.password,
      patternCode: patternCode ?? this.patternCode,
      recoveryQuestion: recoveryQuestion ?? this.recoveryQuestion,
      recoveryAnswer: recoveryAnswer ?? this.recoveryAnswer,
      makePatternInvisible: makePatternInvisible ?? this.makePatternInvisible,
      disablePatternVibration: disablePatternVibration ?? this.disablePatternVibration,
      autoLockTimeout: autoLockTimeout ?? this.autoLockTimeout,
      hideLockNotificationContent: hideLockNotificationContent ?? this.hideLockNotificationContent,
      lockedConversationIds: lockedConversationIds ?? this.lockedConversationIds,
    );
  }

  Map<String, dynamic> toMap() => {
    'isAppLockEnabled': isAppLockEnabled,
    'lockMethod': lockMethod,
    'pinCode': pinCode,
    'password': password,
    'patternCode': patternCode,
    'recoveryQuestion': recoveryQuestion,
    'recoveryAnswer': recoveryAnswer,
    'makePatternInvisible': makePatternInvisible,
    'disablePatternVibration': disablePatternVibration,
    'autoLockTimeout': autoLockTimeout,
    'hideLockNotificationContent': hideLockNotificationContent,
    'lockedConversationIds': lockedConversationIds,
  };

  factory SecurityPreferences.fromMap(Map<String, dynamic> map) => SecurityPreferences(
    isAppLockEnabled: map['isAppLockEnabled'] as bool? ?? false,
    lockMethod: map['lockMethod'] as String? ?? 'PIN',
    pinCode: map['pinCode'] is String ? map['pinCode'] : null,
    password: map['password'] is String ? map['password'] : null,
    patternCode: map['patternCode'] is String ? map['patternCode'] : null,
    recoveryQuestion: map['recoveryQuestion'] is String ? map['recoveryQuestion'] : null,
    recoveryAnswer: map['recoveryAnswer'] is String ? map['recoveryAnswer'] : null,
    makePatternInvisible: map['makePatternInvisible'] as bool? ?? false,
    disablePatternVibration: map['disablePatternVibration'] as bool? ?? false,
    autoLockTimeout: map['autoLockTimeout'] as String? ?? '1m',
    hideLockNotificationContent: map['hideLockNotificationContent'] as bool? ?? true,
    lockedConversationIds: (map['lockedConversationIds'] as List?)?.map((e) => e.toString()).toList() ?? const [],
  );
}

/// Home Screen Customization Model
class HomePreferences {
  final String homeStyle; // 'Chaty Default', 'Classic', 'Compact', 'Expressive', 'Minimal', 'Stories First', 'Productivity', 'Tablet Split View'
  final bool enableStoriesStrip;
  final String storiesStyle; // 'Circular', 'Squircle', 'Card', 'Minimal', 'Compact'
  final bool separateChatsAndGroups;
  final String myNameOverride;
  final bool disableStatusUnderName;
  final bool hideHomeProfilePic;
  final String avatarShape; // 'circle', 'squircle', 'roundedSquare'
  final bool ghostMode;
  final bool airplaneModeSimulator;
  final bool showSearchBar;
  final bool showCameraIcon;
  final bool showAddAccount;
  final int headerBackgroundColorHex;
  final int headerTextColorHex;
  final double headerHeight;

  const HomePreferences({
    this.homeStyle = 'Chaty Default',
    this.enableStoriesStrip = false,
    this.storiesStyle = 'Circular',
    this.separateChatsAndGroups = false,
    this.myNameOverride = 'Alex Rivera',
    this.disableStatusUnderName = false,
    this.hideHomeProfilePic = false,
    this.avatarShape = 'circle',
    this.ghostMode = false,
    this.airplaneModeSimulator = false,
    this.showSearchBar = true,
    this.showCameraIcon = false,
    this.showAddAccount = false,
    this.headerBackgroundColorHex = 0x00000000,
    this.headerTextColorHex = 0x00000000,
    this.headerHeight = 56.0,
  });

  HomePreferences copyWith({
    String? homeStyle,
    bool? enableStoriesStrip,
    String? storiesStyle,
    bool? separateChatsAndGroups,
    String? myNameOverride,
    bool? disableStatusUnderName,
    bool? hideHomeProfilePic,
    String? avatarShape,
    bool? ghostMode,
    bool? airplaneModeSimulator,
    bool? showSearchBar,
    bool? showCameraIcon,
    bool? showAddAccount,
    int? headerBackgroundColorHex,
    int? headerTextColorHex,
    double? headerHeight,
  }) {
    return HomePreferences(
      homeStyle: homeStyle ?? this.homeStyle,
      enableStoriesStrip: enableStoriesStrip ?? this.enableStoriesStrip,
      storiesStyle: storiesStyle ?? this.storiesStyle,
      separateChatsAndGroups: separateChatsAndGroups ?? this.separateChatsAndGroups,
      myNameOverride: myNameOverride ?? this.myNameOverride,
      disableStatusUnderName: disableStatusUnderName ?? this.disableStatusUnderName,
      hideHomeProfilePic: hideHomeProfilePic ?? this.hideHomeProfilePic,
      avatarShape: avatarShape ?? this.avatarShape,
      ghostMode: ghostMode ?? this.ghostMode,
      airplaneModeSimulator: airplaneModeSimulator ?? this.airplaneModeSimulator,
      showSearchBar: showSearchBar ?? this.showSearchBar,
      showCameraIcon: showCameraIcon ?? this.showCameraIcon,
      showAddAccount: showAddAccount ?? this.showAddAccount,
      headerBackgroundColorHex: headerBackgroundColorHex ?? this.headerBackgroundColorHex,
      headerTextColorHex: headerTextColorHex ?? this.headerTextColorHex,
      headerHeight: headerHeight ?? this.headerHeight,
    );
  }

  Map<String, dynamic> toMap() => {
        'homeStyle': homeStyle,
        'enableStoriesStrip': enableStoriesStrip,
        'storiesStyle': storiesStyle,
        'separateChatsAndGroups': separateChatsAndGroups,
        'myNameOverride': myNameOverride,
        'disableStatusUnderName': disableStatusUnderName,
        'hideHomeProfilePic': hideHomeProfilePic,
        'avatarShape': avatarShape,
        'ghostMode': ghostMode,
        'airplaneModeSimulator': airplaneModeSimulator,
        'showSearchBar': showSearchBar,
        'showCameraIcon': showCameraIcon,
        'showAddAccount': showAddAccount,
        'headerBackgroundColorHex': headerBackgroundColorHex,
        'headerTextColorHex': headerTextColorHex,
        'headerHeight': headerHeight,
      };

  factory HomePreferences.fromMap(Map<String, dynamic> map) => HomePreferences(
        homeStyle: map['homeStyle'] ?? 'Chaty Default',
        enableStoriesStrip: map['enableStoriesStrip'] ?? true,
        storiesStyle: map['storiesStyle'] ?? 'Circular',
        separateChatsAndGroups: map['separateChatsAndGroups'] ?? false,
        myNameOverride: map['myNameOverride'] ?? 'Alex Rivera',
        disableStatusUnderName: map['disableStatusUnderName'] ?? false,
        hideHomeProfilePic: map['hideHomeProfilePic'] ?? false,
        avatarShape: map['avatarShape'] ?? 'circle',
        ghostMode: map['ghostMode'] ?? false,
        airplaneModeSimulator: map['airplaneModeSimulator'] ?? false,
        showSearchBar: map['showSearchBar'] ?? true,
        showCameraIcon: map['showCameraIcon'] ?? true,
        showAddAccount: map['showAddAccount'] ?? false,
        headerBackgroundColorHex: map['headerBackgroundColorHex'] ?? 0x00000000,
        headerTextColorHex: map['headerTextColorHex'] ?? 0x00000000,
        headerHeight: (map['headerHeight'] as num?)?.toDouble() ?? 56.0,
      );
}

/// Conversation Screen Customization Model
class ConversationPreferences {
  final String bubbleShape; // 'Rounded', 'Compact', 'Classic', 'Tail', 'Tail-less', 'Squircle', 'Minimal', 'Card'
  final double bubbleRadius;
  final double bubblePadding;
  final String tickStyle; // 'Default', 'Double Check', 'iOS Style', 'Minimal', 'Neon'
  final int customIncomingBubbleHex;
  final int customOutgoingBubbleHex;
  final bool enableQuickContactSidebar;
  final String sidebarPosition; // 'Left', 'Right'
  final double sidebarOpacity;
  final bool iosStylePopupMenu;
  final String doubleTapReactionEmoji;
  final bool enableTranslation;
  final String targetLanguage;
  final String wallpaperType; // 'Solid', 'Gradient', 'Pattern', 'Image', 'ProfileBlur'
  final double voicePlaybackSpeed;
  final String waveformStyle;

  const ConversationPreferences({
    this.bubbleShape = 'Rounded',
    this.bubbleRadius = 16.0,
    this.bubblePadding = 12.0,
    this.tickStyle = 'Default',
    this.customIncomingBubbleHex = 0,
    this.customOutgoingBubbleHex = 0,
    this.enableQuickContactSidebar = false,
    this.sidebarPosition = 'Right',
    this.sidebarOpacity = 0.9,
    this.iosStylePopupMenu = true,
    this.doubleTapReactionEmoji = '❤️',
    this.enableTranslation = true,
    this.targetLanguage = 'English',
    this.wallpaperType = 'Pattern',
    this.voicePlaybackSpeed = 1.0,
    this.waveformStyle = 'Bars',
  });

  ConversationPreferences copyWith({
    String? bubbleShape,
    double? bubbleRadius,
    double? bubblePadding,
    String? tickStyle,
    int? customIncomingBubbleHex,
    int? customOutgoingBubbleHex,
    bool? enableQuickContactSidebar,
    String? sidebarPosition,
    double? sidebarOpacity,
    bool? iosStylePopupMenu,
    String? doubleTapReactionEmoji,
    bool? enableTranslation,
    String? targetLanguage,
    String? wallpaperType,
    double? voicePlaybackSpeed,
    String? waveformStyle,
  }) {
    return ConversationPreferences(
      bubbleShape: bubbleShape ?? this.bubbleShape,
      bubbleRadius: bubbleRadius ?? this.bubbleRadius,
      bubblePadding: bubblePadding ?? this.bubblePadding,
      tickStyle: tickStyle ?? this.tickStyle,
      customIncomingBubbleHex: customIncomingBubbleHex ?? this.customIncomingBubbleHex,
      customOutgoingBubbleHex: customOutgoingBubbleHex ?? this.customOutgoingBubbleHex,
      enableQuickContactSidebar: enableQuickContactSidebar ?? this.enableQuickContactSidebar,
      sidebarPosition: sidebarPosition ?? this.sidebarPosition,
      sidebarOpacity: sidebarOpacity ?? this.sidebarOpacity,
      iosStylePopupMenu: iosStylePopupMenu ?? this.iosStylePopupMenu,
      doubleTapReactionEmoji: doubleTapReactionEmoji ?? this.doubleTapReactionEmoji,
      enableTranslation: enableTranslation ?? this.enableTranslation,
      targetLanguage: targetLanguage ?? this.targetLanguage,
      wallpaperType: wallpaperType ?? this.wallpaperType,
      voicePlaybackSpeed: voicePlaybackSpeed ?? this.voicePlaybackSpeed,
      waveformStyle: waveformStyle ?? this.waveformStyle,
    );
  }

  Map<String, dynamic> toMap() => {
        'bubbleShape': bubbleShape,
        'bubbleRadius': bubbleRadius,
        'bubblePadding': bubblePadding,
        'tickStyle': tickStyle,
        'customIncomingBubbleHex': customIncomingBubbleHex,
        'customOutgoingBubbleHex': customOutgoingBubbleHex,
        'enableQuickContactSidebar': enableQuickContactSidebar,
        'sidebarPosition': sidebarPosition,
        'sidebarOpacity': sidebarOpacity,
        'iosStylePopupMenu': iosStylePopupMenu,
        'doubleTapReactionEmoji': doubleTapReactionEmoji,
        'enableTranslation': enableTranslation,
        'targetLanguage': targetLanguage,
        'wallpaperType': wallpaperType,
        'voicePlaybackSpeed': voicePlaybackSpeed,
        'waveformStyle': waveformStyle,
      };

  factory ConversationPreferences.fromMap(Map<String, dynamic> map) => ConversationPreferences(
        bubbleShape: map['bubbleShape'] ?? 'Rounded',
        bubbleRadius: (map['bubbleRadius'] as num?)?.toDouble() ?? 16.0,
        bubblePadding: (map['bubblePadding'] as num?)?.toDouble() ?? 12.0,
        tickStyle: map['tickStyle'] ?? 'Default',
        customIncomingBubbleHex: map['customIncomingBubbleHex'] ?? 0,
        customOutgoingBubbleHex: map['customOutgoingBubbleHex'] ?? 0,
        enableQuickContactSidebar: map['enableQuickContactSidebar'] ?? false,
        sidebarPosition: map['sidebarPosition'] ?? 'Right',
        sidebarOpacity: (map['sidebarOpacity'] as num?)?.toDouble() ?? 0.9,
        iosStylePopupMenu: map['iosStylePopupMenu'] ?? true,
        doubleTapReactionEmoji: map['doubleTapReactionEmoji'] ?? '❤️',
        enableTranslation: map['enableTranslation'] ?? true,
        targetLanguage: map['targetLanguage'] ?? 'English',
        wallpaperType: map['wallpaperType'] ?? 'Pattern',
        voicePlaybackSpeed: (map['voicePlaybackSpeed'] as num?)?.toDouble() ?? 1.0,
        waveformStyle: map['waveformStyle'] ?? 'Bars',
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
      enableGlobalNotifications: enableGlobalNotifications ?? this.enableGlobalNotifications,
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

  factory NotificationPreferences.fromMap(Map<String, dynamic> map) => NotificationPreferences(
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

  factory ScheduledMessageEntry.fromMap(Map<String, dynamic> map) => ScheduledMessageEntry(
        id: map['id'],
        recipientId: map['recipientId'] ?? '',
        recipientName: map['recipientName'] ?? '',
        text: map['text'] ?? '',
        scheduledAt: DateTime.fromMillisecondsSinceEpoch(map['scheduledAt'] ?? DateTime.now().millisecondsSinceEpoch),
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

  factory QuickReplyTemplate.fromMap(Map<String, dynamic> map) => QuickReplyTemplate(
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
  final List<String> blockedUserIds;

  const MessageAutomationPreferences({
    this.enableAutoReply = false,
    this.autoReplyRules = const [
      AutoReplyRule(
        id: 'rule_1',
        enabled: true,
        keyword: 'busy',
        responseMessage: "I am currently in focused mode via Chaty. I'll get back to you shortly!",
      ),
    ],
    this.scheduledMessages = const [],
    this.quickReplies = const [
      QuickReplyTemplate(shortcut: '#thanks', title: 'Thank you', content: 'Thank you so much! Really appreciate it.'),
      QuickReplyTemplate(shortcut: '#eta', title: 'ETA 5 mins', content: 'On my way! Be there in 5 minutes.'),
    ],
    this.blockedUserIds = const [],
  });

  MessageAutomationPreferences copyWith({
    bool? enableAutoReply,
    List<AutoReplyRule>? autoReplyRules,
    List<ScheduledMessageEntry>? scheduledMessages,
    List<QuickReplyTemplate>? quickReplies,
    List<String>? blockedUserIds,
  }) {
    return MessageAutomationPreferences(
      enableAutoReply: enableAutoReply ?? this.enableAutoReply,
      autoReplyRules: autoReplyRules ?? this.autoReplyRules,
      scheduledMessages: scheduledMessages ?? this.scheduledMessages,
      quickReplies: quickReplies ?? this.quickReplies,
      blockedUserIds: blockedUserIds ?? this.blockedUserIds,
    );
  }

  Map<String, dynamic> toMap() => {
        'enableAutoReply': enableAutoReply,
        'autoReplyRules': autoReplyRules.map((r) => r.toMap()).toList(),
        'scheduledMessages': scheduledMessages.map((s) => s.toMap()).toList(),
        'quickReplies': quickReplies.map((q) => q.toMap()).toList(),
        'blockedUserIds': blockedUserIds,
      };

  factory MessageAutomationPreferences.fromMap(Map<String, dynamic> map) => MessageAutomationPreferences(
        enableAutoReply: map['enableAutoReply'] ?? false,
        autoReplyRules: (map['autoReplyRules'] as List?)?.map((r) => AutoReplyRule.fromMap(r)).toList() ?? [],
        scheduledMessages: (map['scheduledMessages'] as List?)?.map((s) => ScheduledMessageEntry.fromMap(s)).toList() ?? [],
        quickReplies: (map['quickReplies'] as List?)?.map((q) => QuickReplyTemplate.fromMap(q)).toList() ?? [],
        blockedUserIds: (map['blockedUserIds'] as List?)?.map((b) => b.toString()).toList() ?? [],
      );
}

/// Navigation Effects & Particle Config Model
class NavigationEffectPreferences {
  final String pageTransitionStyle; // 'Fade', 'Slide', 'Grow', 'Scale', 'Shared Axis', 'Fade Through', 'Cupertino', 'None'
  final bool enableClickParticles;
  final String clickParticleSymbol; // '✨', '❤️', '🔥', '⚡', '⭐', '🌸'
  final double clickParticleSpeed;
  final bool enableFallingParticles;
  final String fallingParticleObject; // 'Stars', 'Hearts', 'Snowflakes', 'Leaves'
  final String fallingParticleScope; // 'Home only', 'Chat only', 'Both'

  const NavigationEffectPreferences({
    this.pageTransitionStyle = 'Fade Through',
    this.enableClickParticles = false,
    this.clickParticleSymbol = '✨',
    this.clickParticleSpeed = 1.0,
    this.enableFallingParticles = false,
    this.fallingParticleObject = 'Stars',
    this.fallingParticleScope = 'Home only',
  });

  NavigationEffectPreferences copyWith({
    String? pageTransitionStyle,
    bool? enableClickParticles,
    String? clickParticleSymbol,
    double? clickParticleSpeed,
    bool? enableFallingParticles,
    String? fallingParticleObject,
    String? fallingParticleScope,
  }) {
    return NavigationEffectPreferences(
      pageTransitionStyle: pageTransitionStyle ?? this.pageTransitionStyle,
      enableClickParticles: enableClickParticles ?? this.enableClickParticles,
      clickParticleSymbol: clickParticleSymbol ?? this.clickParticleSymbol,
      clickParticleSpeed: clickParticleSpeed ?? this.clickParticleSpeed,
      enableFallingParticles: enableFallingParticles ?? this.enableFallingParticles,
      fallingParticleObject: fallingParticleObject ?? this.fallingParticleObject,
      fallingParticleScope: fallingParticleScope ?? this.fallingParticleScope,
    );
  }

  Map<String, dynamic> toMap() => {
        'pageTransitionStyle': pageTransitionStyle,
        'enableClickParticles': enableClickParticles,
        'clickParticleSymbol': clickParticleSymbol,
        'clickParticleSpeed': clickParticleSpeed,
        'enableFallingParticles': enableFallingParticles,
        'fallingParticleObject': fallingParticleObject,
        'fallingParticleScope': fallingParticleScope,
      };

  factory NavigationEffectPreferences.fromMap(Map<String, dynamic> map) => NavigationEffectPreferences(
        pageTransitionStyle: map['pageTransitionStyle'] ?? 'Fade Through',
        enableClickParticles: map['enableClickParticles'] ?? true,
        clickParticleSymbol: map['clickParticleSymbol'] ?? '✨',
        clickParticleSpeed: (map['clickParticleSpeed'] as num?)?.toDouble() ?? 1.0,
        enableFallingParticles: map['enableFallingParticles'] ?? false,
        fallingParticleObject: map['fallingParticleObject'] ?? 'Stars',
        fallingParticleScope: map['fallingParticleScope'] ?? 'Home only',
      );
}
