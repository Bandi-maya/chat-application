package com.example.chat.domain.models

/** Exact ports of the preference group models in
 *  lib/domain/models/preferences.dart (api branch). Field order, defaults,
 *  toMap key sets (including the Dart asymmetries) and fromMap coercion
 *  behavior are preserved 1:1. Kotlin data class copy() stands in for the
 *  Dart copyWith. */

// ── PrivacyPreferences ───────────────────────────────────────────────────

data class PrivacyPreferences(
    val freezeLastSeen: Boolean = false,
    val frozenLastSeenTime: String = "",
    /** 'Everyone', 'My Contacts', 'My Contacts Except...', 'Nobody' */
    val hideLastSeenAudience: String = "My Contacts",
    /** 'Everyone', 'Same as Last Seen' */
    val hideOnlineAudience: String = "Everyone",
    val antiViewOnce: Boolean = true,
    val disableForwardedLabel: Boolean = false,
    val readReceipts: Boolean = true,
    val typingIndicators: Boolean = true,
    val recordingIndicators: Boolean = true,
    /** 'Everyone', 'My Contacts', 'My Contacts Except...', 'Nobody' */
    val whoCanCallMe: String = "Everyone",
    /** user IDs excluded when 'My Contacts Except...' */
    val whoCanCallMeExceptions: List<String> = emptyList(),
    val hidePrivacyOption: Boolean = false,
    val hideViewStatus: Boolean = false,
    val antiDeleteStatus: Boolean = true,
    val statusRevocationAlert: Boolean = true,
    val showEditedMessage: Boolean = true,
    val antiDeleteMessages: Boolean = true,
    val messageRevokeAlert: Boolean = true,
    val showBlueTicksAfterReply: Boolean = false,
    val hideUpdateOption: Boolean = false,
    val disableChannels: Boolean = false,
    val hideFirstMessage: Boolean = false,
    val antiDisappearingMessages: Boolean = true,
    val deletionOfEveryone: Boolean = true,
    val deletedMediaTime: Boolean = false,
    val unviewedChats: Boolean = false,
    val hideOnlineStatus: Boolean = false,
    val hideSecondTick: Boolean = false,
) {
    /** Dart toMap asymmetry preserved: antiDisappearingMessages and
     *  hideOnlineStatus are NOT serialized upstream, so a round trip
     *  restores their fromMap defaults. */
    fun toMap(): Map<String, Any?> = mapOf(
        "freezeLastSeen" to freezeLastSeen,
        "frozenLastSeenTime" to frozenLastSeenTime,
        "hideLastSeenAudience" to hideLastSeenAudience,
        "hideOnlineAudience" to hideOnlineAudience,
        "antiViewOnce" to antiViewOnce,
        "disableForwardedLabel" to disableForwardedLabel,
        "readReceipts" to readReceipts,
        "typingIndicators" to typingIndicators,
        "recordingIndicators" to recordingIndicators,
        "whoCanCallMe" to whoCanCallMe,
        "whoCanCallMeExceptions" to whoCanCallMeExceptions,
        "hidePrivacyOption" to hidePrivacyOption,
        "hideViewStatus" to hideViewStatus,
        "antiDeleteStatus" to antiDeleteStatus,
        "statusRevocationAlert" to statusRevocationAlert,
        "showEditedMessage" to showEditedMessage,
        "antiDeleteMessages" to antiDeleteMessages,
        "messageRevokeAlert" to messageRevokeAlert,
        "showBlueTicksAfterReply" to showBlueTicksAfterReply,
        "hideUpdateOption" to hideUpdateOption,
        "disableChannels" to disableChannels,
        "hideFirstMessage" to hideFirstMessage,
        "deletionOfEveryone" to deletionOfEveryone,
        "deletedMediaTime" to deletedMediaTime,
        "unviewedChats" to unviewedChats,
        "hideSecondTick" to hideSecondTick,
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): PrivacyPreferences = PrivacyPreferences(
            freezeLastSeen = map["freezeLastSeen"] as Boolean? ?: false,
            frozenLastSeenTime = map["frozenLastSeenTime"] as String? ?: "",
            hideLastSeenAudience = map["hideLastSeenAudience"] as String? ?: "My Contacts",
            hideOnlineAudience = map["hideOnlineAudience"] as String? ?: "Everyone",
            antiViewOnce = map["antiViewOnce"] as Boolean? ?: true,
            disableForwardedLabel = map["disableForwardedLabel"] as Boolean? ?: false,
            readReceipts = map["readReceipts"] as Boolean? ?: true,
            typingIndicators = map["typingIndicators"] as Boolean? ?: true,
            recordingIndicators = map["recordingIndicators"] as Boolean? ?: true,
            whoCanCallMe = map["whoCanCallMe"] as String? ?: "Everyone",
            whoCanCallMeExceptions = (map["whoCanCallMeExceptions"] as List<*>?)
                ?.map { it.toString() } ?: emptyList(),
            hidePrivacyOption = map["hidePrivacyOption"] as Boolean? ?: false,
            hideViewStatus = map["hideViewStatus"] as Boolean? ?: false,
            antiDeleteStatus = map["antiDeleteStatus"] as Boolean? ?: true,
            statusRevocationAlert = map["statusRevocationAlert"] as Boolean? ?: true,
            showEditedMessage = map["showEditedMessage"] as Boolean? ?: true,
            antiDeleteMessages = map["antiDeleteMessages"] as Boolean? ?: true,
            messageRevokeAlert = map["messageRevokeAlert"] as Boolean? ?: true,
            showBlueTicksAfterReply = map["showBlueTicksAfterReply"] as Boolean? ?: false,
            hideUpdateOption = map["hideUpdateOption"] as Boolean? ?: false,
            disableChannels = map["disableChannels"] as Boolean? ?: false,
            hideFirstMessage = map["hideFirstMessage"] as Boolean? ?: false,
            antiDisappearingMessages = map["antiDisappearingMessages"] as Boolean? ?: true,
            deletionOfEveryone = map["deletionOfEveryone"] as Boolean? ?: true,
            deletedMediaTime = map["deletedMediaTime"] as Boolean? ?: false,
            unviewedChats = map["unviewedChats"] as Boolean? ?: false,
            hideOnlineStatus = map["hideOnlineStatus"] as Boolean? ?: false,
            hideSecondTick = map["hideSecondTick"] as Boolean? ?: false,
        )
    }
}

// ── SecurityPreferences ──────────────────────────────────────────────────

/** Serialized into local storage and synced to the backend, so it holds only
 *  non-secret configuration. Unlock credentials never live here (they are
 *  salted hashes in platform secure storage via LocalLockService); legacy
 *  plaintext credential fields are purged on load by the migrator. */
data class SecurityPreferences(
    val isAppLockEnabled: Boolean = false,
    /** 'Biometric', 'PIN', 'Pattern', 'Password', 'Device Credential' */
    val lockMethod: String = "PIN",
    val makePatternInvisible: Boolean = false,
    val disablePatternVibration: Boolean = false,
    /** 'Immediately', '15s', '30s', '1m', '5m', '15m' */
    val autoLockTimeout: String = "1m",
    val hideLockNotificationContent: Boolean = true,
    val lockedConversationIds: List<String> = emptyList(),
    val hiddenConversationIds: List<String> = emptyList(),
    val hideLockedChats: Boolean = false,
    val entryByAppTitle: Boolean = true,
    val entryBySecretPhrase: Boolean = false,
    val protectFromScreenshots: Boolean = false,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "isAppLockEnabled" to isAppLockEnabled,
        "lockMethod" to lockMethod,
        "makePatternInvisible" to makePatternInvisible,
        "disablePatternVibration" to disablePatternVibration,
        "autoLockTimeout" to autoLockTimeout,
        "hideLockNotificationContent" to hideLockNotificationContent,
        "lockedConversationIds" to lockedConversationIds,
        "hiddenConversationIds" to hiddenConversationIds,
        "hideLockedChats" to hideLockedChats,
        "entryByAppTitle" to entryByAppTitle,
        "entryBySecretPhrase" to entryBySecretPhrase,
        "protectFromScreenshots" to protectFromScreenshots,
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): SecurityPreferences = SecurityPreferences(
            isAppLockEnabled = map["isAppLockEnabled"] as Boolean? ?: false,
            lockMethod = map["lockMethod"] as String? ?: "PIN",
            makePatternInvisible = map["makePatternInvisible"] as Boolean? ?: false,
            disablePatternVibration = map["disablePatternVibration"] as Boolean? ?: false,
            autoLockTimeout = map["autoLockTimeout"] as String? ?: "1m",
            hideLockNotificationContent = map["hideLockNotificationContent"] as Boolean? ?: true,
            lockedConversationIds = (map["lockedConversationIds"] as List<*>?)
                ?.map { it.toString() } ?: emptyList(),
            hiddenConversationIds = (map["hiddenConversationIds"] as List<*>?)
                ?.map { it.toString() } ?: emptyList(),
            hideLockedChats = map["hideLockedChats"] as Boolean? ?: false,
            entryByAppTitle = map["entryByAppTitle"] as Boolean? ?: true,
            entryBySecretPhrase = map["entryBySecretPhrase"] as Boolean? ?: false,
            protectFromScreenshots = map["protectFromScreenshots"] as Boolean? ?: false,
        )
    }
}

// ── NotificationPreferences ──────────────────────────────────────────────

data class NotificationPreferences(
    val enableGlobalNotifications: Boolean = true,
    val showSenderAvatar: Boolean = true,
    val showSenderName: Boolean = true,
    val showMessagePreview: Boolean = true,
    val notifyContactOnline: Boolean = true,
    val notifyStatusViewed: Boolean = true,
    val notifyTypingStarted: Boolean = false,
    val notifyMessageDeleted: Boolean = true,
    val notifyStatusDeleted: Boolean = true,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "enableGlobalNotifications" to enableGlobalNotifications,
        "showSenderAvatar" to showSenderAvatar,
        "showSenderName" to showSenderName,
        "showMessagePreview" to showMessagePreview,
        "notifyContactOnline" to notifyContactOnline,
        "notifyStatusViewed" to notifyStatusViewed,
        "notifyTypingStarted" to notifyTypingStarted,
        "notifyMessageDeleted" to notifyMessageDeleted,
        "notifyStatusDeleted" to notifyStatusDeleted,
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): NotificationPreferences = NotificationPreferences(
            enableGlobalNotifications = map["enableGlobalNotifications"] as Boolean? ?: true,
            showSenderAvatar = map["showSenderAvatar"] as Boolean? ?: true,
            showSenderName = map["showSenderName"] as Boolean? ?: true,
            showMessagePreview = map["showMessagePreview"] as Boolean? ?: true,
            notifyContactOnline = map["notifyContactOnline"] as Boolean? ?: true,
            notifyStatusViewed = map["notifyStatusViewed"] as Boolean? ?: true,
            notifyTypingStarted = map["notifyTypingStarted"] as Boolean? ?: false,
            notifyMessageDeleted = map["notifyMessageDeleted"] as Boolean? ?: true,
            notifyStatusDeleted = map["notifyStatusDeleted"] as Boolean? ?: true,
        )
    }
}

// ── NavigationEffectPreferences ──────────────────────────────────────────

data class NavigationEffectPreferences(
    val enableClickParticles: Boolean = false,
    /** '✨', '❤️', '🔥', '⚡', '⭐', '🌸' */
    val clickParticleSymbol: String = "✨",
    val clickParticleSpeed: Double = 1.0,
    val enableFallingParticles: Boolean = false,
    /** 'Stars', 'Hearts', 'Snowflakes', 'Leaves' */
    val fallingParticleObject: String = "Stars",
    /** 'Home only', 'Chat only', 'Both' */
    val fallingParticleScope: String = "Home only",
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "enableClickParticles" to enableClickParticles,
        "clickParticleSymbol" to clickParticleSymbol,
        "clickParticleSpeed" to clickParticleSpeed,
        "enableFallingParticles" to enableFallingParticles,
        "fallingParticleObject" to fallingParticleObject,
        "fallingParticleScope" to fallingParticleScope,
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): NavigationEffectPreferences = NavigationEffectPreferences(
            enableClickParticles = map["enableClickParticles"] as Boolean? ?: false,
            clickParticleSymbol = map["clickParticleSymbol"] as String? ?: "✨",
            clickParticleSpeed = (map["clickParticleSpeed"] as Number?)?.toDouble() ?: 1.0,
            enableFallingParticles = map["enableFallingParticles"] as Boolean? ?: false,
            fallingParticleObject = map["fallingParticleObject"] as String? ?: "Stars",
            fallingParticleScope = map["fallingParticleScope"] as String? ?: "Home only",
        )
    }
}

// ── UniversalPreferences ─────────────────────────────────────────────────

/** Alias getters from the Dart model preserved (loadFontCustom,
 *  hideMediaPhotos/Videos/Gifs, translateOption, translationLanguage,
 *  disableSwipeToExit, gifProvider, sendImagesFullResolutionMb). The Dart
 *  copyWith's dual alias parameters collapse into Kotlin's copy(). */
data class UniversalPreferences(
    val universalColor: Long? = null,
    val universalActionBarTextColor: Long? = null,
    val backgroundColor: Long? = null,
    val listBackgroundColor: Long? = null,
    val statusBarColor: Long? = null,
    val navigationBarColor: Long? = null,
    val launcherIcon: String = "Classic",
    val emojiVariant: String = "WhatsApp",
    val notificationIcon: String = "White",
    val fontStyle: String = "Default",
    val iconTheme: String = "Chaty Orbit",
    val emojiStyle: String = "Chaty Motion",
    val loadFontEnabled: Boolean = false,
    val hidePhotosFromGallery: Boolean = false,
    val hideVideosFromGallery: Boolean = false,
    val hideGifsFromGallery: Boolean = false,
    val translateOptionSettings: String = "Server + No outside apps",
    val defaultTranslationLanguage: String = "Show All",
    val conversationCards: Boolean = true,
    val disableHeadsUpNotification: Boolean = false,
    val disableBadgeCounter: Boolean = false,
    val disableAudioPlayingNotification: Boolean = false,
    val increaseForwardLimit: Boolean = false,
    val disableSwipeToExitConversation: Boolean = false,
    val enableAlwaysOnline: Boolean = true,
    val tenorGiphyGifProvider: String = "Tenor",
    val sendImagesInFullResolutionMb: Double = 1.0,
) {
    val loadFontCustom: Boolean get() = loadFontEnabled
    val hideMediaPhotos: Boolean get() = hidePhotosFromGallery
    val hideMediaVideos: Boolean get() = hideVideosFromGallery
    val hideMediaGifs: Boolean get() = hideGifsFromGallery
    val translateOption: String get() = translateOptionSettings
    val translationLanguage: String get() = defaultTranslationLanguage
    val disableSwipeToExit: Boolean get() = disableSwipeToExitConversation
    val gifProvider: String get() = tenorGiphyGifProvider
    /** Dart sendImagesInFullResolutionMb.round(). */
    val sendImagesFullResolutionMb: Int get() = kotlin.math.round(sendImagesInFullResolutionMb).toInt()

    fun toMap(): Map<String, Any?> = mapOf(
        "universalColor" to universalColor,
        "universalActionBarTextColor" to universalActionBarTextColor,
        "backgroundColor" to backgroundColor,
        "listBackgroundColor" to listBackgroundColor,
        "statusBarColor" to statusBarColor,
        "navigationBarColor" to navigationBarColor,
        "launcherIcon" to launcherIcon,
        "emojiVariant" to emojiVariant,
        "notificationIcon" to notificationIcon,
        "fontStyle" to fontStyle,
        "iconTheme" to iconTheme,
        "emojiStyle" to emojiStyle,
        "loadFontEnabled" to loadFontEnabled,
        "hidePhotosFromGallery" to hidePhotosFromGallery,
        "hideVideosFromGallery" to hideVideosFromGallery,
        "hideGifsFromGallery" to hideGifsFromGallery,
        "translateOptionSettings" to translateOptionSettings,
        "defaultTranslationLanguage" to defaultTranslationLanguage,
        "conversationCards" to conversationCards,
        "disableHeadsUpNotification" to disableHeadsUpNotification,
        "disableBadgeCounter" to disableBadgeCounter,
        "disableAudioPlayingNotification" to disableAudioPlayingNotification,
        "increaseForwardLimit" to increaseForwardLimit,
        "disableSwipeToExitConversation" to disableSwipeToExitConversation,
        "enableAlwaysOnline" to enableAlwaysOnline,
        "tenorGiphyGifProvider" to tenorGiphyGifProvider,
        "sendImagesInFullResolutionMb" to sendImagesInFullResolutionMb,
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): UniversalPreferences = UniversalPreferences(
            universalColor = (map["universalColor"] as Number?)?.toLong(),
            universalActionBarTextColor = (map["universalActionBarTextColor"] as Number?)?.toLong(),
            backgroundColor = (map["backgroundColor"] as Number?)?.toLong(),
            listBackgroundColor = (map["listBackgroundColor"] as Number?)?.toLong(),
            statusBarColor = (map["statusBarColor"] as Number?)?.toLong(),
            navigationBarColor = (map["navigationBarColor"] as Number?)?.toLong(),
            launcherIcon = map["launcherIcon"] as String? ?: "Classic",
            emojiVariant = map["emojiVariant"] as String? ?: "WhatsApp",
            notificationIcon = map["notificationIcon"] as String? ?: "White",
            fontStyle = map["fontStyle"] as String? ?: "Default",
            iconTheme = map["iconTheme"] as String? ?: "Chaty Orbit",
            emojiStyle = map["emojiStyle"] as String? ?: "Chaty Motion",
            loadFontEnabled = map["loadFontEnabled"] as Boolean? ?: false,
            hidePhotosFromGallery = map["hidePhotosFromGallery"] as Boolean? ?: false,
            hideVideosFromGallery = map["hideVideosFromGallery"] as Boolean? ?: false,
            hideGifsFromGallery = map["hideGifsFromGallery"] as Boolean? ?: false,
            translateOptionSettings = map["translateOptionSettings"] as String? ?: "Server + No outside apps",
            defaultTranslationLanguage = map["defaultTranslationLanguage"] as String? ?: "Show All",
            conversationCards = map["conversationCards"] as Boolean? ?: true,
            disableHeadsUpNotification = map["disableHeadsUpNotification"] as Boolean? ?: false,
            disableBadgeCounter = map["disableBadgeCounter"] as Boolean? ?: false,
            disableAudioPlayingNotification = map["disableAudioPlayingNotification"] as Boolean? ?: false,
            increaseForwardLimit = map["increaseForwardLimit"] as Boolean? ?: false,
            disableSwipeToExitConversation = map["disableSwipeToExitConversation"] as Boolean? ?: false,
            enableAlwaysOnline = map["enableAlwaysOnline"] as Boolean? ?: true,
            tenorGiphyGifProvider = map["tenorGiphyGifProvider"] as String? ?: "Tenor",
            sendImagesInFullResolutionMb = (map["sendImagesInFullResolutionMb"] as Number?)?.toDouble() ?: 1.0,
        )
    }
}

// ── StatusPreferences ────────────────────────────────────────────────────

data class StatusPreferences(
    val enableInstagramStories: Boolean = false,
    val carouselView: Boolean = false,
    val storiesStyle: String = "Instagram",
    val activateNewStatusStyle: String = "Old Status with Thumbnail",
    val statusReactionEmoji: String = "💚",
    val recentUpdatesBarColor: Long? = null,
    val recentUpdatesTextColor: Long? = null,
    val contactNameColor: Long? = null,
    val statusSeenColor: Long? = null,
    val statusUnSeenColor: Long? = null,
    val counterBackgroundColor: Long? = null,
    val counterTextColor: Long? = null,
    val statusAroundProfile: Boolean = false,
    val saveAndMarkSeenOptions: Boolean = false,
    val changePhotoProfileStatusPreview: Boolean = false,
    val startStoriesDirectlyWithSound: Boolean = false,
    val confirmBeforeSendingStatus: Boolean = true,
    val fiveMinuteStatus: Boolean = false,
) {
    /** Dart compatibility aliases. */
    val statusUnseenColor: Long? get() = statusUnSeenColor
    val startStoriesWithSound: Boolean get() = startStoriesDirectlyWithSound

    fun toMap(): Map<String, Any?> = mapOf(
        "enableInstagramStories" to enableInstagramStories,
        "carouselView" to carouselView,
        "storiesStyle" to storiesStyle,
        "activateNewStatusStyle" to activateNewStatusStyle,
        "statusReactionEmoji" to statusReactionEmoji,
        "recentUpdatesBarColor" to recentUpdatesBarColor,
        "recentUpdatesTextColor" to recentUpdatesTextColor,
        "contactNameColor" to contactNameColor,
        "statusSeenColor" to statusSeenColor,
        "statusUnSeenColor" to statusUnSeenColor,
        "counterBackgroundColor" to counterBackgroundColor,
        "counterTextColor" to counterTextColor,
        "statusAroundProfile" to statusAroundProfile,
        "saveAndMarkSeenOptions" to saveAndMarkSeenOptions,
        "changePhotoProfileStatusPreview" to changePhotoProfileStatusPreview,
        "startStoriesDirectlyWithSound" to startStoriesDirectlyWithSound,
        "confirmBeforeSendingStatus" to confirmBeforeSendingStatus,
        "fiveMinuteStatus" to fiveMinuteStatus,
    )

    companion object {
        /** Dart dual-key fallbacks preserved (statusUnSeenColor ??
         *  statusUnseenColor; startStoriesDirectlyWithSound ??
         *  startStoriesWithSound). */
        fun fromMap(map: Map<String, Any?>): StatusPreferences = StatusPreferences(
            enableInstagramStories = map["enableInstagramStories"] as Boolean? ?: false,
            carouselView = map["carouselView"] as Boolean? ?: false,
            storiesStyle = map["storiesStyle"] as String? ?: "Instagram",
            activateNewStatusStyle = map["activateNewStatusStyle"] as String? ?: "Old Status with Thumbnail",
            statusReactionEmoji = map["statusReactionEmoji"] as String? ?: "💚",
            recentUpdatesBarColor = (map["recentUpdatesBarColor"] as Number?)?.toLong(),
            recentUpdatesTextColor = (map["recentUpdatesTextColor"] as Number?)?.toLong(),
            contactNameColor = (map["contactNameColor"] as Number?)?.toLong(),
            statusSeenColor = (map["statusSeenColor"] as Number?)?.toLong(),
            statusUnSeenColor = ((map["statusUnSeenColor"] ?: map["statusUnseenColor"]) as Number?)?.toLong(),
            counterBackgroundColor = (map["counterBackgroundColor"] as Number?)?.toLong(),
            counterTextColor = (map["counterTextColor"] as Number?)?.toLong(),
            statusAroundProfile = map["statusAroundProfile"] as Boolean? ?: false,
            saveAndMarkSeenOptions = map["saveAndMarkSeenOptions"] as Boolean? ?: false,
            changePhotoProfileStatusPreview = map["changePhotoProfileStatusPreview"] as Boolean? ?: false,
            startStoriesDirectlyWithSound = (map["startStoriesDirectlyWithSound"]
                ?: map["startStoriesWithSound"]) as Boolean? ?: false,
            confirmBeforeSendingStatus = map["confirmBeforeSendingStatus"] as Boolean? ?: true,
            fiveMinuteStatus = map["fiveMinuteStatus"] as Boolean? ?: false,
        )
    }
}

// ── NotificationToastPreferences ─────────────────────────────────────────

/** Exact port including the Dart alias getters the toast UI reads. */
data class NotificationToastPreferences(
    val onlineToastEnabled: Boolean = true,
    val onlineToastInNotificationBar: Boolean = false,
    val onlineToastWithProfilePicture: Boolean = true,
    val onlineToastElevation: Boolean = true,
    val onlineToastRadius: Double = 8.0,
    val onlineToastRingtone: String = "Default",
    val onlineToastPosition: String = "Top of the screen",
    val onlineToastBgColor: Long? = null,
    val onlineToastTextColor: Long? = null,
    val storyToastEnabled: Boolean = false,
    val storyToastInNotificationBar: Boolean = false,
    val storyToastWithProfilePicture: Boolean = true,
    val storyToastElevation: Boolean = true,
    val storyToastRadius: Double = 8.0,
    val storyToastRingtone: String = "Default",
    val storyToastPosition: String = "Top of the screen",
    val storyToastBgColor: Long? = null,
    val storyToastTextColor: Long? = null,
    val profileToastEnabled: Boolean = false,
    val profileToastInNotificationBar: Boolean = false,
    val profileToastWithProfilePicture: Boolean = true,
    val profileToastElevation: Boolean = true,
    val profileToastRadius: Double = 8.0,
    val profileToastRingtone: String = "Default",
    val profileToastPosition: String = "Top of the screen",
    val profileToastBgColor: Long? = null,
    val profileToastTextColor: Long? = null,
    val typingToastEnabled: Boolean = true,
    val typingToastInNotificationBar: Boolean = false,
    val typingToastWithProfilePicture: Boolean = true,
    val typingToastElevation: Boolean = true,
    val typingToastRadius: Double = 8.0,
    val typingToastRingtone: String = "Default",
    val typingToastPosition: String = "Top of the screen",
    val typingToastBgColor: Long? = null,
    val typingToastTextColor: Long? = null,
) {
    val onlineToast: Boolean get() = onlineToastEnabled
    val viewedStoryToast: Boolean get() = storyToastEnabled
    val onlineWithProfilePic: Boolean get() = onlineToastWithProfilePicture
    val onlineElevation: Boolean get() = onlineToastElevation
    val onlineRadius: Double get() = onlineToastRadius
    val onlinePosition: String get() = onlineToastPosition
    val onlineBgColor: Long? get() = onlineToastBgColor
    val onlineTextColor: Long? get() = onlineToastTextColor
    val onlineRingtone: String get() = onlineToastRingtone
    val onlineInNotificationBar: Boolean get() = onlineToastInNotificationBar
    val storyToast: Boolean get() = storyToastEnabled
    val storyWithProfilePic: Boolean get() = storyToastWithProfilePicture
    val storyElevation: Boolean get() = storyToastElevation
    val storyRadius: Double get() = storyToastRadius
    val storyPosition: String get() = storyToastPosition
    val storyBgColor: Long? get() = storyToastBgColor
    val storyTextColor: Long? get() = storyToastTextColor
    val storyRingtone: String get() = storyToastRingtone
    val storyInNotificationBar: Boolean get() = storyToastInNotificationBar
    val profileToast: Boolean get() = profileToastEnabled
    val profileWithProfilePic: Boolean get() = profileToastWithProfilePicture
    val profileElevation: Boolean get() = profileToastElevation
    val profileRadius: Double get() = profileToastRadius
    val profilePosition: String get() = profileToastPosition
    val profileBgColor: Long? get() = profileToastBgColor
    val profileTextColor: Long? get() = profileToastTextColor
    val profileRingtone: String get() = profileToastRingtone
    val profileInNotificationBar: Boolean get() = profileToastInNotificationBar
    val typingToast: Boolean get() = typingToastEnabled
    val typingWithProfilePic: Boolean get() = typingToastWithProfilePicture
    val typingElevation: Boolean get() = typingToastElevation
    val typingRadius: Double get() = typingToastRadius
    val typingPosition: String get() = typingToastPosition
    val typingBgColor: Long? get() = typingToastBgColor
    val typingTextColor: Long? get() = typingToastTextColor
    val typingRingtone: String get() = typingToastRingtone
    val typingInNotificationBar: Boolean get() = typingToastInNotificationBar
    /** Dart disableContactOnlineToast. */
    val disableContactOnlineToast: Boolean get() = !onlineToastEnabled

    fun toMap(): Map<String, Any?> = mapOf(
        "onlineToastEnabled" to onlineToastEnabled,
        "onlineToastInNotificationBar" to onlineToastInNotificationBar,
        "onlineToastWithProfilePicture" to onlineToastWithProfilePicture,
        "onlineToastElevation" to onlineToastElevation,
        "onlineToastRadius" to onlineToastRadius,
        "onlineToastRingtone" to onlineToastRingtone,
        "onlineToastPosition" to onlineToastPosition,
        "onlineToastBgColor" to onlineToastBgColor,
        "onlineToastTextColor" to onlineToastTextColor,
        "storyToastEnabled" to storyToastEnabled,
        "storyToastInNotificationBar" to storyToastInNotificationBar,
        "storyToastWithProfilePicture" to storyToastWithProfilePicture,
        "storyToastElevation" to storyToastElevation,
        "storyToastRadius" to storyToastRadius,
        "storyToastRingtone" to storyToastRingtone,
        "storyToastPosition" to storyToastPosition,
        "storyToastBgColor" to storyToastBgColor,
        "storyToastTextColor" to storyToastTextColor,
        "profileToastEnabled" to profileToastEnabled,
        "profileToastInNotificationBar" to profileToastInNotificationBar,
        "profileToastWithProfilePicture" to profileToastWithProfilePicture,
        "profileToastElevation" to profileToastElevation,
        "profileToastRadius" to profileToastRadius,
        "profileToastRingtone" to profileToastRingtone,
        "profileToastPosition" to profileToastPosition,
        "profileToastBgColor" to profileToastBgColor,
        "profileToastTextColor" to profileToastTextColor,
        "typingToastEnabled" to typingToastEnabled,
        "typingToastInNotificationBar" to typingToastInNotificationBar,
        "typingToastWithProfilePicture" to typingToastWithProfilePicture,
        "typingToastElevation" to typingToastElevation,
        "typingToastRadius" to typingToastRadius,
        "typingToastRingtone" to typingToastRingtone,
        "typingToastPosition" to typingToastPosition,
        "typingToastBgColor" to typingToastBgColor,
        "typingToastTextColor" to typingToastTextColor,
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): NotificationToastPreferences = NotificationToastPreferences(
            onlineToastEnabled = map["onlineToastEnabled"] as Boolean? ?: true,
            onlineToastInNotificationBar = map["onlineToastInNotificationBar"] as Boolean? ?: false,
            onlineToastWithProfilePicture = map["onlineToastWithProfilePicture"] as Boolean? ?: true,
            onlineToastElevation = map["onlineToastElevation"] as Boolean? ?: true,
            onlineToastRadius = (map["onlineToastRadius"] as Number?)?.toDouble() ?: 8.0,
            onlineToastRingtone = map["onlineToastRingtone"] as String? ?: "Default",
            onlineToastPosition = map["onlineToastPosition"] as String? ?: "Top of the screen",
            onlineToastBgColor = (map["onlineToastBgColor"] as Number?)?.toLong(),
            onlineToastTextColor = (map["onlineToastTextColor"] as Number?)?.toLong(),
            storyToastEnabled = map["storyToastEnabled"] as Boolean? ?: false,
            storyToastInNotificationBar = map["storyToastInNotificationBar"] as Boolean? ?: false,
            storyToastWithProfilePicture = map["storyToastWithProfilePicture"] as Boolean? ?: true,
            storyToastElevation = map["storyToastElevation"] as Boolean? ?: true,
            storyToastRadius = (map["storyToastRadius"] as Number?)?.toDouble() ?: 8.0,
            storyToastRingtone = map["storyToastRingtone"] as String? ?: "Default",
            storyToastPosition = map["storyToastPosition"] as String? ?: "Top of the screen",
            storyToastBgColor = (map["storyToastBgColor"] as Number?)?.toLong(),
            storyToastTextColor = (map["storyToastTextColor"] as Number?)?.toLong(),
            profileToastEnabled = map["profileToastEnabled"] as Boolean? ?: false,
            profileToastInNotificationBar = map["profileToastInNotificationBar"] as Boolean? ?: false,
            profileToastWithProfilePicture = map["profileToastWithProfilePicture"] as Boolean? ?: true,
            profileToastElevation = map["profileToastElevation"] as Boolean? ?: true,
            profileToastRadius = (map["profileToastRadius"] as Number?)?.toDouble() ?: 8.0,
            profileToastRingtone = map["profileToastRingtone"] as String? ?: "Default",
            profileToastPosition = map["profileToastPosition"] as String? ?: "Top of the screen",
            profileToastBgColor = (map["profileToastBgColor"] as Number?)?.toLong(),
            profileToastTextColor = (map["profileToastTextColor"] as Number?)?.toLong(),
            typingToastEnabled = map["typingToastEnabled"] as Boolean? ?: true,
            typingToastInNotificationBar = map["typingToastInNotificationBar"] as Boolean? ?: false,
            typingToastWithProfilePicture = map["typingToastWithProfilePicture"] as Boolean? ?: true,
            typingToastElevation = map["typingToastElevation"] as Boolean? ?: true,
            typingToastRadius = (map["typingToastRadius"] as Number?)?.toDouble() ?: 8.0,
            typingToastRingtone = map["typingToastRingtone"] as String? ?: "Default",
            typingToastPosition = map["typingToastPosition"] as String? ?: "Top of the screen",
            typingToastBgColor = (map["typingToastBgColor"] as Number?)?.toLong(),
            typingToastTextColor = (map["typingToastTextColor"] as Number?)?.toLong(),
        )
    }
}

// ── HomePreferences ──────────────────────────────────────────────────────

data class HomePreferences(
    val homeStyle: String = "Chaty Default",
    val enableStoriesStrip: Boolean = false,
    val storiesStyle: String = "Circular",
    val separateChatsAndGroups: Boolean = false,
    val myNameOverride: String = "",
    val avatarShape: String = "circle",
    val ghostMode: Boolean = false,
    val airplaneModeSimulator: Boolean = false,
    val showSearchBar: Boolean = true,
    val searchPlacement: String = "Header action",
    val showCameraIcon: Boolean = false,
    val showGhostPill: Boolean = false,
    val showDesktopIcon: Boolean = false,
    val carouselView: Boolean = false,
    val setMyName: Boolean = true,
    val disableStatusUnderName: Boolean = false,
    val hideChatSortList: Boolean = false,
    val disableSearchBar: Boolean = false,
    val themesEnabled: Boolean = false,
    val showIconAddAccount: Boolean = false,
    val hideChatsDivider: Boolean = false,
    val hideUnsavedNumbers: Boolean = false,
    val hideFrequentlyContacted: Boolean = false,
    val hideOtherContacts: Boolean = false,
    val hideRecentChats: Boolean = false,
    val screenTextSize: Double = 17.0,
    val rowTextColor: Long? = null,
    val rowContactNameColor: Long? = null,
    val unreadCounterColor: Long? = null,
    val unreadCounterTextColor: Long? = null,
    val contactOnlineColor: Long? = null,
    val lastSeenColor: Long? = null,
    val mentionIndicatorBgColor: Long? = null,
    val mentionIconColor: Long? = null,
    val hideArchivedChats: Boolean = false,
    val archiveChatsOnTop: Boolean = true,
    val disableContactOnlineLastSeen: Boolean = false,
    val disableOnlineDot: Boolean = false,
    val onlineDotColor: Long? = null,
    val elapsedTime: Boolean = false,
    val hideFab: Boolean = false,
    val fabNormalColor: Long? = null,
    val fabPressedColor: Long? = null,
    val fabIconsColor: Long? = null,
    val showMetaAiIcon: Boolean = true,
    val hideNewMessageFab: Boolean = false,
    val hideLastSeenFab: Boolean = false,
    val hideCutEditFab: Boolean = false,
    val hidePluginsList: Boolean = false,
    val hideGbwaSettingsFab: Boolean = false,
    val pagerTransition3d: String = "Cube 3D",
    val tabBubbleStyle: String = "Capsule Pill",
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "homeStyle" to homeStyle,
        "enableStoriesStrip" to enableStoriesStrip,
        "storiesStyle" to storiesStyle,
        "separateChatsAndGroups" to separateChatsAndGroups,
        "myNameOverride" to myNameOverride,
        "avatarShape" to avatarShape,
        "ghostMode" to ghostMode,
        "airplaneModeSimulator" to airplaneModeSimulator,
        "showSearchBar" to showSearchBar,
        "searchPlacement" to searchPlacement,
        "showCameraIcon" to showCameraIcon,
        "showGhostPill" to showGhostPill,
        "showDesktopIcon" to showDesktopIcon,
        "carouselView" to carouselView,
        "setMyName" to setMyName,
        "disableStatusUnderName" to disableStatusUnderName,
        "hideChatSortList" to hideChatSortList,
        "disableSearchBar" to disableSearchBar,
        "themesEnabled" to themesEnabled,
        "showIconAddAccount" to showIconAddAccount,
        "hideChatsDivider" to hideChatsDivider,
        "hideUnsavedNumbers" to hideUnsavedNumbers,
        "hideFrequentlyContacted" to hideFrequentlyContacted,
        "hideOtherContacts" to hideOtherContacts,
        "hideRecentChats" to hideRecentChats,
        "screenTextSize" to screenTextSize,
        "rowTextColor" to rowTextColor,
        "rowContactNameColor" to rowContactNameColor,
        "unreadCounterColor" to unreadCounterColor,
        "unreadCounterTextColor" to unreadCounterTextColor,
        "contactOnlineColor" to contactOnlineColor,
        "lastSeenColor" to lastSeenColor,
        "mentionIndicatorBgColor" to mentionIndicatorBgColor,
        "mentionIconColor" to mentionIconColor,
        "hideArchivedChats" to hideArchivedChats,
        "archiveChatsOnTop" to archiveChatsOnTop,
        "disableContactOnlineLastSeen" to disableContactOnlineLastSeen,
        "disableOnlineDot" to disableOnlineDot,
        "onlineDotColor" to onlineDotColor,
        "elapsedTime" to elapsedTime,
        "hideFab" to hideFab,
        "fabNormalColor" to fabNormalColor,
        "fabPressedColor" to fabPressedColor,
        "fabIconsColor" to fabIconsColor,
        "showMetaAiIcon" to showMetaAiIcon,
        "hideNewMessageFab" to hideNewMessageFab,
        "hideLastSeenFab" to hideLastSeenFab,
        "hideCutEditFab" to hideCutEditFab,
        "hidePluginsList" to hidePluginsList,
        "hideGbwaSettingsFab" to hideGbwaSettingsFab,
        "pagerTransition3d" to pagerTransition3d,
        "tabBubbleStyle" to tabBubbleStyle,
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): HomePreferences = HomePreferences(
            homeStyle = map["homeStyle"] as String? ?: "Chaty Default",
            enableStoriesStrip = map["enableStoriesStrip"] as Boolean? ?: false,
            storiesStyle = map["storiesStyle"] as String? ?: "Circular",
            separateChatsAndGroups = map["separateChatsAndGroups"] as Boolean? ?: false,
            // Dart quirk: bundled placeholder names are coerced to empty.
            myNameOverride = if (map["myNameOverride"] == "Alex Rivera" || map["myNameOverride"] == "Bandi Maya") "" else (map["myNameOverride"] as String? ?: ""),
            avatarShape = map["avatarShape"] as String? ?: "circle",
            ghostMode = map["ghostMode"] as Boolean? ?: false,
            airplaneModeSimulator = map["airplaneModeSimulator"] as Boolean? ?: false,
            showSearchBar = map["showSearchBar"] as Boolean? ?: true,
            searchPlacement = map["searchPlacement"] as String? ?: "Header action",
            showCameraIcon = map["showCameraIcon"] as Boolean? ?: false,
            showGhostPill = map["showGhostPill"] as Boolean? ?: false,
            showDesktopIcon = map["showDesktopIcon"] as Boolean? ?: false,
            carouselView = map["carouselView"] as Boolean? ?: false,
            setMyName = map["setMyName"] as Boolean? ?: true,
            disableStatusUnderName = map["disableStatusUnderName"] as Boolean? ?: false,
            hideChatSortList = map["hideChatSortList"] as Boolean? ?: false,
            disableSearchBar = map["disableSearchBar"] as Boolean? ?: false,
            themesEnabled = map["themesEnabled"] as Boolean? ?: false,
            showIconAddAccount = map["showIconAddAccount"] as Boolean? ?: false,
            hideChatsDivider = map["hideChatsDivider"] as Boolean? ?: false,
            hideUnsavedNumbers = map["hideUnsavedNumbers"] as Boolean? ?: false,
            hideFrequentlyContacted = map["hideFrequentlyContacted"] as Boolean? ?: false,
            hideOtherContacts = map["hideOtherContacts"] as Boolean? ?: false,
            hideRecentChats = map["hideRecentChats"] as Boolean? ?: false,
            screenTextSize = (map["screenTextSize"] as Number?)?.toDouble() ?: 17.0,
            rowTextColor = (map["rowTextColor"] as Number?)?.toLong(),
            rowContactNameColor = (map["rowContactNameColor"] as Number?)?.toLong(),
            unreadCounterColor = (map["unreadCounterColor"] as Number?)?.toLong(),
            unreadCounterTextColor = (map["unreadCounterTextColor"] as Number?)?.toLong(),
            contactOnlineColor = (map["contactOnlineColor"] as Number?)?.toLong(),
            lastSeenColor = (map["lastSeenColor"] as Number?)?.toLong(),
            mentionIndicatorBgColor = (map["mentionIndicatorBgColor"] as Number?)?.toLong(),
            mentionIconColor = (map["mentionIconColor"] as Number?)?.toLong(),
            hideArchivedChats = map["hideArchivedChats"] as Boolean? ?: false,
            archiveChatsOnTop = map["archiveChatsOnTop"] as Boolean? ?: true,
            disableContactOnlineLastSeen = map["disableContactOnlineLastSeen"] as Boolean? ?: false,
            disableOnlineDot = map["disableOnlineDot"] as Boolean? ?: false,
            onlineDotColor = (map["onlineDotColor"] as Number?)?.toLong(),
            elapsedTime = map["elapsedTime"] as Boolean? ?: false,
            hideFab = map["hideFab"] as Boolean? ?: false,
            fabNormalColor = (map["fabNormalColor"] as Number?)?.toLong(),
            fabPressedColor = (map["fabPressedColor"] as Number?)?.toLong(),
            fabIconsColor = (map["fabIconsColor"] as Number?)?.toLong(),
            showMetaAiIcon = map["showMetaAiIcon"] as Boolean? ?: true,
            hideNewMessageFab = map["hideNewMessageFab"] as Boolean? ?: false,
            hideLastSeenFab = map["hideLastSeenFab"] as Boolean? ?: false,
            hideCutEditFab = map["hideCutEditFab"] as Boolean? ?: false,
            hidePluginsList = map["hidePluginsList"] as Boolean? ?: false,
            hideGbwaSettingsFab = map["hideGbwaSettingsFab"] as Boolean? ?: false,
            pagerTransition3d = map["pagerTransition3d"] as String? ?: "Cube 3D",
            tabBubbleStyle = map["tabBubbleStyle"] as String? ?: "Capsule Pill",
        )
    }
}

// ── ConversationPreferences ──────────────────────────────────────────────

/** Exact port including legacy abu_saleh_* fallbacks and dual-key color
 *  aliases from the Dart model. */
data class ConversationPreferences(
    val bubbleStyle: String = "Stock",
    val tickStyle: String = "Stock",
    val enableQuickContactSidebar: Boolean = false,
    val sidebarPosition: String = "Right",
    val sidebarOpacity: Double = 0.9,
    val iosStylePopupMenu: Boolean = true,
    val doubleTapReactionEmoji: String = "❤️",
    val wallpaperType: String = "Pattern",
    val voicePlaybackSpeed: Double = 1.0,
    val wallpaperPath: String = "",
    val enableAnimatedEmojis: Boolean = true,
    val switchDirectContactLink: Boolean = false,
    val confirmBeforeSendingSticker: Boolean = true,
    val newAttachmentPickerUi: Boolean = false,
    val hideDateAndName: Boolean = true,
    val hideAdminNameIcon: Boolean = false,
    val groupAdminIcon: String = "Default",
    val quickContactSidebarPosition: String = "Top",
    val quickContactBgColor: Long = 0xFF000000L,
    val quickContactTextColor: Long = 0xFF000000L,
    val hideChatFab: Boolean = true,
    val disableMoreOptionsFromBubble: Boolean = false,
    val disableDoubleTapReaction: Boolean = false,
    val translateOptionSettings: String = "Server",
    val hideMessageTranslationIcon: Boolean = false,
    val customWallpaperPerContact: Boolean = false,
    val profilePicWallpaper: Boolean = false,
    val enableProximitySensor: Boolean = true,
    val disableOutputSwitching: Boolean = false,
    val playVoiceNotes: Boolean = false,
    val forwardAsVoiceNote: Boolean = false,
    val incomingMessageRingtone: String = "Default",
    val sendMessageRingtone: String = "Default",
    val actionBarColor: Long? = null,
    val hideProfilePicture: Boolean = false,
    val hideContactName: Boolean = false,
    val hideCallButton: Boolean = false,
    val disableContactStatus: Boolean = false,
    val contactStatusBgColor: Long? = null,
    val contactStatusTextColor: Long? = null,
    val messageTextSize: Double = 16.0,
    val convBackgroundColor: Long? = null,
    val rightBubbleColor: Long? = null,
    val rightChatBubbleTextColor: Long? = null,
    val rightBubbleTimeColor: Long? = null,
    val leftBubbleColor: Long? = null,
    val leftChatBubbleTextColor: Long? = null,
    val leftBubbleTimeColor: Long? = null,
    val deletedMessageIconColor: Long? = null,
    val quotedDividerColor: Long? = null,
    val quotedNameColor: Long? = null,
    val quotedMessageColor: Long? = null,
    val quotedBackgroundColor: Long? = null,
    val makeTextSelectable: Boolean = true,
    val removeReadMore: Boolean = false,
    val entryStyle: String = "Stock",
    val uiEntryBackgroundColor: Long? = null,
    val uiButtonsColor: Long? = null,
    val emojiButtonColor: Long? = null,
    val sendButtonColor: Long? = null,
    val micSendBgCircleColor: Long? = null,
    val textEntryBackgroundColor: Long? = null,
    val textEntryColor: Long? = null,
    val emojiHeaderColor: Long? = null,
    val emojiHeaderIconsColor: Long? = null,
    val emojiPickerBgColor: Long? = null,
    val hyperlinksColor: Long? = null,
    val infoBalloonsTextColor: Long? = null,
    val infoBalloonsBgColor: Long? = null,
    val groupParticipantNameColor: Long? = null,
    val voiceNotePlayingBarColor: Long? = null,
    val voiceNotePlayButtonColor: Long? = null,
) {
    /** Dart alias getters. */
    val bubbleShape: String get() = bubbleStyle
    val bubbleRadius: Double get() = 16.0

    fun toMap(): Map<String, Any?> = mapOf(
        "bubbleStyle" to bubbleStyle,
        "tickStyle" to tickStyle,
        "enableQuickContactSidebar" to enableQuickContactSidebar,
        "sidebarPosition" to sidebarPosition,
        "sidebarOpacity" to sidebarOpacity,
        "iosStylePopupMenu" to iosStylePopupMenu,
        "doubleTapReactionEmoji" to doubleTapReactionEmoji,
        "wallpaperType" to wallpaperType,
        "voicePlaybackSpeed" to voicePlaybackSpeed,
        "wallpaperPath" to wallpaperPath,
        "enableAnimatedEmojis" to enableAnimatedEmojis,
        "switchDirectContactLink" to switchDirectContactLink,
        "confirmBeforeSendingSticker" to confirmBeforeSendingSticker,
        "newAttachmentPickerUi" to newAttachmentPickerUi,
        "hideDateAndName" to hideDateAndName,
        "hideAdminNameIcon" to hideAdminNameIcon,
        "groupAdminIcon" to groupAdminIcon,
        "quickContactSidebarPosition" to quickContactSidebarPosition,
        "quickContactBgColor" to quickContactBgColor,
        "quickContactTextColor" to quickContactTextColor,
        "hideChatFab" to hideChatFab,
        "disableMoreOptionsFromBubble" to disableMoreOptionsFromBubble,
        "disableDoubleTapReaction" to disableDoubleTapReaction,
        "translateOptionSettings" to translateOptionSettings,
        "hideMessageTranslationIcon" to hideMessageTranslationIcon,
        "customWallpaperPerContact" to customWallpaperPerContact,
        "profilePicWallpaper" to profilePicWallpaper,
        "enableProximitySensor" to enableProximitySensor,
        "disableOutputSwitching" to disableOutputSwitching,
        "playVoiceNotes" to playVoiceNotes,
        "forwardAsVoiceNote" to forwardAsVoiceNote,
        "incomingMessageRingtone" to incomingMessageRingtone,
        "sendMessageRingtone" to sendMessageRingtone,
        "actionBarColor" to actionBarColor,
        "hideProfilePicture" to hideProfilePicture,
        "hideContactName" to hideContactName,
        "hideCallButton" to hideCallButton,
        "disableContactStatus" to disableContactStatus,
        "contactStatusBgColor" to contactStatusBgColor,
        "contactStatusTextColor" to contactStatusTextColor,
        "messageTextSize" to messageTextSize,
        "convBackgroundColor" to convBackgroundColor,
        "rightBubbleColor" to rightBubbleColor,
        "rightChatBubbleTextColor" to rightChatBubbleTextColor,
        "rightBubbleTimeColor" to rightBubbleTimeColor,
        "leftBubbleColor" to leftBubbleColor,
        "leftChatBubbleTextColor" to leftChatBubbleTextColor,
        "leftBubbleTimeColor" to leftBubbleTimeColor,
        "deletedMessageIconColor" to deletedMessageIconColor,
        "quotedDividerColor" to quotedDividerColor,
        "quotedNameColor" to quotedNameColor,
        "quotedMessageColor" to quotedMessageColor,
        "quotedBackgroundColor" to quotedBackgroundColor,
        "makeTextSelectable" to makeTextSelectable,
        "removeReadMore" to removeReadMore,
        "entryStyle" to entryStyle,
        "uiEntryBackgroundColor" to uiEntryBackgroundColor,
        "uiButtonsColor" to uiButtonsColor,
        "emojiButtonColor" to emojiButtonColor,
        "sendButtonColor" to sendButtonColor,
        "micSendBgCircleColor" to micSendBgCircleColor,
        "textEntryBackgroundColor" to textEntryBackgroundColor,
        "textEntryColor" to textEntryColor,
        "emojiHeaderColor" to emojiHeaderColor,
        "emojiHeaderIconsColor" to emojiHeaderIconsColor,
        "emojiPickerBgColor" to emojiPickerBgColor,
        "hyperlinksColor" to hyperlinksColor,
        "infoBalloonsTextColor" to infoBalloonsTextColor,
        "infoBalloonsBgColor" to infoBalloonsBgColor,
        "groupParticipantNameColor" to groupParticipantNameColor,
        "voiceNotePlayingBarColor" to voiceNotePlayingBarColor,
        "voiceNotePlayButtonColor" to voiceNotePlayButtonColor,
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): ConversationPreferences = ConversationPreferences(
            // Dart: bubbleStyle ?? bubbleShape ?? conversation_style_row
            bubbleStyle = (map["bubbleStyle"] ?: map["bubbleShape"] ?: map["conversation_style_row"]) as String? ?: "Stock",
            tickStyle = map["tickStyle"] as String? ?: "Stock",
            // Dart legacy abu_saleh_quickcontact fallback
            enableQuickContactSidebar = map["enableQuickContactSidebar"] as Boolean?
                ?: (map["abu_saleh_quickcontact"] != null && map["abu_saleh_quickcontact"] != "Off"),
            sidebarPosition = map["sidebarPosition"] as String?
                ?: (if (map["abu_saleh_quickcontact"] == "Left") "Left" else "Right"),
            sidebarOpacity = (map["sidebarOpacity"] as Number?)?.toDouble() ?: 0.9,
            iosStylePopupMenu = map["iosStylePopupMenu"] as Boolean? ?: true,
            doubleTapReactionEmoji = map["doubleTapReactionEmoji"] as String? ?: "❤️",
            wallpaperType = map["wallpaperType"] as String? ?: "Pattern",
            voicePlaybackSpeed = (map["voicePlaybackSpeed"] as Number?)?.toDouble() ?: 1.0,
            wallpaperPath = map["wallpaperPath"] as String? ?: "",
            enableAnimatedEmojis = map["enableAnimatedEmojis"] as Boolean? ?: true,
            switchDirectContactLink = map["switchDirectContactLink"] as Boolean? ?: false,
            confirmBeforeSendingSticker = map["confirmBeforeSendingSticker"] as Boolean? ?: true,
            newAttachmentPickerUi = map["newAttachmentPickerUi"] as Boolean? ?: false,
            hideDateAndName = map["hideDateAndName"] as Boolean? ?: true,
            hideAdminNameIcon = map["hideAdminNameIcon"] as Boolean? ?: false,
            groupAdminIcon = map["groupAdminIcon"] as String? ?: "Default",
            quickContactSidebarPosition = (map["quickContactSidebarPosition"] ?: map["abu_saleh_quickcontact_pos"]) as String? ?: "Top",
            quickContactBgColor = ((map["quickContactBgColor"] ?: map["abu_saleh_quickcontact_bg"]) as Number?)?.toLong() ?: 0xFF000000L,
            quickContactTextColor = ((map["quickContactTextColor"] ?: map["abu_saleh_quickcontact_txt"]) as Number?)?.toLong() ?: 0xFF000000L,
            hideChatFab = map["hideChatFab"] as Boolean? ?: true,
            disableMoreOptionsFromBubble = map["disableMoreOptionsFromBubble"] as Boolean? ?: false,
            disableDoubleTapReaction = map["disableDoubleTapReaction"] as Boolean? ?: false,
            translateOptionSettings = map["translateOptionSettings"] as String? ?: "Server",
            hideMessageTranslationIcon = map["hideMessageTranslationIcon"] as Boolean? ?: false,
            customWallpaperPerContact = map["customWallpaperPerContact"] as Boolean? ?: false,
            profilePicWallpaper = map["profilePicWallpaper"] as Boolean? ?: false,
            enableProximitySensor = map["enableProximitySensor"] as Boolean? ?: true,
            disableOutputSwitching = map["disableOutputSwitching"] as Boolean? ?: false,
            playVoiceNotes = map["playVoiceNotes"] as Boolean? ?: false,
            forwardAsVoiceNote = map["forwardAsVoiceNote"] as Boolean? ?: false,
            incomingMessageRingtone = map["incomingMessageRingtone"] as String? ?: "Default",
            sendMessageRingtone = map["sendMessageRingtone"] as String? ?: "Default",
            actionBarColor = (map["actionBarColor"] as Number?)?.toLong(),
            hideProfilePicture = map["hideProfilePicture"] as Boolean? ?: false,
            hideContactName = map["hideContactName"] as Boolean? ?: false,
            hideCallButton = map["hideCallButton"] as Boolean? ?: false,
            disableContactStatus = map["disableContactStatus"] as Boolean? ?: false,
            contactStatusBgColor = ((map["contactStatusBgColor"] ?: map["contactStatusBackgroundColor"]) as Number?)?.toLong(),
            contactStatusTextColor = (map["contactStatusTextColor"] as Number?)?.toLong(),
            // Dart clamps to 10..30
            messageTextSize = ((map["messageTextSize"] as Number?)?.toDouble() ?: 16.0).coerceIn(10.0, 30.0),
            convBackgroundColor = ((map["convBackgroundColor"] ?: map["conversationBackgroundColor"]) as Number?)?.toLong(),
            rightBubbleColor = (map["rightBubbleColor"] as Number?)?.toLong(),
            rightChatBubbleTextColor = (map["rightChatBubbleTextColor"] as Number?)?.toLong(),
            rightBubbleTimeColor = (map["rightBubbleTimeColor"] as Number?)?.toLong(),
            leftBubbleColor = (map["leftBubbleColor"] as Number?)?.toLong(),
            leftChatBubbleTextColor = (map["leftChatBubbleTextColor"] as Number?)?.toLong(),
            leftBubbleTimeColor = (map["leftBubbleTimeColor"] as Number?)?.toLong(),
            deletedMessageIconColor = (map["deletedMessageIconColor"] as Number?)?.toLong(),
            quotedDividerColor = (map["quotedDividerColor"] as Number?)?.toLong(),
            quotedNameColor = (map["quotedNameColor"] as Number?)?.toLong(),
            quotedMessageColor = (map["quotedMessageColor"] as Number?)?.toLong(),
            quotedBackgroundColor = (map["quotedBackgroundColor"] as Number?)?.toLong(),
            makeTextSelectable = map["makeTextSelectable"] as Boolean? ?: true,
            removeReadMore = map["removeReadMore"] as Boolean? ?: false,
            entryStyle = map["entryStyle"] as String? ?: "Stock",
            uiEntryBackgroundColor = ((map["uiEntryBackgroundColor"] ?: map["entryUiBackgroundColor"]) as Number?)?.toLong(),
            uiButtonsColor = ((map["uiButtonsColor"] ?: map["entryUiButtonsColor"]) as Number?)?.toLong(),
            emojiButtonColor = ((map["emojiButtonColor"] ?: map["entryEmojiButtonColor"]) as Number?)?.toLong(),
            sendButtonColor = ((map["sendButtonColor"] ?: map["entrySendButtonColor"]) as Number?)?.toLong(),
            micSendBgCircleColor = ((map["micSendBgCircleColor"] ?: map["entryMicSendBackgroundCircle"]) as Number?)?.toLong(),
            textEntryBackgroundColor = ((map["textEntryBackgroundColor"] ?: map["entryTextBackground"]) as Number?)?.toLong(),
            textEntryColor = ((map["textEntryColor"] ?: map["entryTextColor"]) as Number?)?.toLong(),
            emojiHeaderColor = (map["emojiHeaderColor"] as Number?)?.toLong(),
            emojiHeaderIconsColor = (map["emojiHeaderIconsColor"] as Number?)?.toLong(),
            emojiPickerBgColor = (map["emojiPickerBgColor"] as Number?)?.toLong(),
            hyperlinksColor = (map["hyperlinksColor"] as Number?)?.toLong(),
            infoBalloonsTextColor = (map["infoBalloonsTextColor"] as Number?)?.toLong(),
            infoBalloonsBgColor = ((map["infoBalloonsBgColor"] ?: map["infoBalloonsBackgroundColor"]) as Number?)?.toLong(),
            groupParticipantNameColor = (map["groupParticipantNameColor"] as Number?)?.toLong(),
            voiceNotePlayingBarColor = ((map["voiceNotePlayingBarColor"] ?: map["voiceNotePlayingBar"]) as Number?)?.toLong(),
            voiceNotePlayButtonColor = ((map["voiceNotePlayButtonColor"] ?: map["voiceNotePlayButton"]) as Number?)?.toLong(),
        )
    }
}
