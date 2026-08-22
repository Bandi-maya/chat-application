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
        protectFromScreenshots:
            map['protectFromScreenshots'] as bool? ?? false,
      );
}

/// Home Screen Customization Model
class HomePreferences {
  final String
  homeStyle; // 'Chaty Default', 'Classic', 'Compact', 'Expressive', 'Minimal', 'Stories First', 'Productivity', 'Tablet Split View'
  final bool enableStoriesStrip;
  final String
  storiesStyle; // 'Circular', 'Squircle', 'Card', 'Minimal', 'Compact'
  final bool separateChatsAndGroups;
  final String myNameOverride;
  final String avatarShape; // 'circle', 'squircle', 'roundedSquare'
  final bool ghostMode;
  final bool airplaneModeSimulator;
  final bool showSearchBar;
  final bool showCameraIcon;

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
  });

  HomePreferences copyWith({
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
    );
  }

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
  );
}

/// Conversation Screen Customization Model
class ConversationPreferences {
  final String
  bubbleShape; // 'Rounded', 'Compact', 'Classic', 'Tail', 'Tail-less', 'Squircle', 'Minimal', 'Card'
  final double bubbleRadius;
  final String
  tickStyle; // 'Default', 'Double Check', 'iOS Style', 'Minimal', 'Neon'
  final bool enableQuickContactSidebar;
  final String sidebarPosition; // 'Left', 'Right'
  final double sidebarOpacity;
  final bool iosStylePopupMenu;
  final String doubleTapReactionEmoji;
  final String
  wallpaperType; // 'Solid', 'Gradient', 'Pattern', 'Image', 'ProfileBlur'
  final double voicePlaybackSpeed;
  final String
  wallpaperPath; // '' = none; local copy of a user-picked background image
  final bool enableAnimatedEmojis;

  const ConversationPreferences({
    this.bubbleShape = 'Rounded',
    this.bubbleRadius = 16.0,
    this.tickStyle = 'Default',
    this.enableQuickContactSidebar = false,
    this.sidebarPosition = 'Right',
    this.sidebarOpacity = 0.9,
    this.iosStylePopupMenu = true,
    this.doubleTapReactionEmoji = '❤️',
    this.wallpaperType = 'Pattern',
    this.voicePlaybackSpeed = 1.0,
    this.wallpaperPath = '',
    this.enableAnimatedEmojis = true,
  });

  ConversationPreferences copyWith({
    String? bubbleShape,
    double? bubbleRadius,
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
  }) {
    return ConversationPreferences(
      bubbleShape: bubbleShape ?? this.bubbleShape,
      bubbleRadius: bubbleRadius ?? this.bubbleRadius,
      tickStyle: tickStyle ?? this.tickStyle,
      enableQuickContactSidebar:
          enableQuickContactSidebar ?? this.enableQuickContactSidebar,
      sidebarPosition: sidebarPosition ?? this.sidebarPosition,
      sidebarOpacity: sidebarOpacity ?? this.sidebarOpacity,
      iosStylePopupMenu: iosStylePopupMenu ?? this.iosStylePopupMenu,
      doubleTapReactionEmoji:
          doubleTapReactionEmoji ?? this.doubleTapReactionEmoji,
      wallpaperType: wallpaperType ?? this.wallpaperType,
      voicePlaybackSpeed: voicePlaybackSpeed ?? this.voicePlaybackSpeed,
      wallpaperPath: wallpaperPath ?? this.wallpaperPath,
      enableAnimatedEmojis: enableAnimatedEmojis ?? this.enableAnimatedEmojis,
    );
  }

  Map<String, dynamic> toMap() => {
    'bubbleShape': bubbleShape,
    'bubbleRadius': bubbleRadius,
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
  };

  factory ConversationPreferences.fromMap(Map<String, dynamic> map) =>
      ConversationPreferences(
        bubbleShape: map['bubbleShape'] ?? 'Rounded',
        bubbleRadius: (map['bubbleRadius'] as num?)?.toDouble() ?? 16.0,
        tickStyle: map['tickStyle'] ?? 'Default',
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
class NavigationEffectPreferences { // 'Fade', 'Slide', 'Grow', 'Scale', 'Shared Axis', 'Fade Through', 'Cupertino', 'None'
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
