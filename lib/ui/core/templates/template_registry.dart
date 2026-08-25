import 'package:flutter/material.dart';
import 'template_models.dart';

/// Authoritative central registry containing the 6 original Chaty UI Templates.
class ChatyTemplateRegistry {
  const ChatyTemplateRegistry._();

  static const ChatyTemplateDefinition visualSocial = ChatyTemplateDefinition(
    id: ChatyTemplateId.visualSocial,
    name: 'Visual Social',
    subtitle: 'Media-First & Expressive Stories',
    description:
        'Prominent circular stories rail, floating pill navigation with action focus, and expressive message surfaces.',
    navigation: NavigationTemplate(
      layout: NavigationLayoutType.floatingPill,
      primaryDestinationIds: ['chats', 'updates', 'calls'],
      overflowDestinationIds: ['tasks', 'settings', 'desktop'],
      height: 68.0,
      bottomBarStyleName: 'Floating Pill',
    ),
    home: HomeTemplate(
      headerStyle: HomeHeaderStyle.storiesFirst,
      showStoriesStrip: true,
      storiesStyle: 'Circular',
      homeStylePreset: 'Stories First',
      separateChatsAndGroups: false,
    ),
    chatList: ChatListTemplate(
      density: ChatListDensity.comfortable,
      avatarShape: 'circle',
      showPresenceBadge: true,
      showDivider: false,
      itemHeight: 76.0,
    ),
    conversation: ConversationTemplate(
      bubbleStyle: 'Stock',
      tickStyle: 'RC iOS 11',
      bubbleCornerRadius: 18.0,
      enableQuickSidebar: true,
      wallpaperType: 'ProfileBlur',
    ),
    composer: ComposerTemplate(
      actionPlacement: ComposerActionPlacement.integrated,
      cornerRadius: 26.0,
      showVoiceLock: true,
      showCameraShortcut: true,
      enableSendMorph: true,
    ),
    updates: UpdatesTemplate(
      layoutMode: UpdatesLayoutMode.circularRail,
      enableStatusAudio: true,
      cardElevation: 2.0,
    ),
    profile: ProfileTemplate(
      headerStyle: ProfileHeaderStyle.bannerWithAvatar,
      avatarShape: 'circle',
      showStatsGrid: true,
    ),
    calls: CallTemplate(
      enableFloatingIsland: true,
      controlBarCornerRadius: 30.0,
    ),
  );

  static const ChatyTemplateDefinition stream = ChatyTemplateDefinition(
    id: ChatyTemplateId.stream,
    name: 'Stream',
    subtitle: 'Text-Forward Timeline & Fast Scanning',
    description:
        'High information density, compact avatars, minimal decorative padding, and text-prioritized chat rows.',
    navigation: NavigationTemplate(
      layout: NavigationLayoutType.flatTabs,
      primaryDestinationIds: ['chats', 'updates', 'calls'],
      overflowDestinationIds: ['tasks', 'settings', 'desktop'],
      height: 56.0,
      bottomBarStyleName: 'Classic Label Bar',
    ),
    home: HomeTemplate(
      headerStyle: HomeHeaderStyle.searchForward,
      showStoriesStrip: false,
      storiesStyle: 'Minimal',
      homeStylePreset: 'Minimal',
      separateChatsAndGroups: false,
    ),
    chatList: ChatListTemplate(
      density: ChatListDensity.compact,
      avatarShape: 'roundedSquare',
      showPresenceBadge: true,
      showDivider: true,
      itemHeight: 62.0,
    ),
    conversation: ConversationTemplate(
      bubbleStyle: 'Stock',
      tickStyle: 'Minimal',
      bubbleCornerRadius: 12.0,
      enableQuickSidebar: false,
      wallpaperType: 'Solid',
    ),
    composer: ComposerTemplate(
      actionPlacement: ComposerActionPlacement.split,
      cornerRadius: 14.0,
      showVoiceLock: false,
      showCameraShortcut: true,
      enableSendMorph: false,
    ),
    updates: UpdatesTemplate(
      layoutMode: UpdatesLayoutMode.minimalList,
      enableStatusAudio: false,
      cardElevation: 0.0,
    ),
    profile: ProfileTemplate(
      headerStyle: ProfileHeaderStyle.compactHeader,
      avatarShape: 'roundedSquare',
      showStatsGrid: false,
    ),
    calls: CallTemplate(
      enableFloatingIsland: true,
      controlBarCornerRadius: 16.0,
    ),
  );

  static const ChatyTemplateDefinition cameraFirst = ChatyTemplateDefinition(
    id: ChatyTemplateId.cameraFirst,
    name: 'Camera First',
    subtitle: 'Instant Capture & Fast Media Dispatch',
    description:
        'Elevated central capture action in the navigation bar with immediate camera access and visual feedback.',
    navigation: NavigationTemplate(
      layout: NavigationLayoutType.centerActionDock,
      primaryDestinationIds: ['chats', 'updates', 'calls'],
      overflowDestinationIds: ['tasks', 'settings', 'desktop'],
      height: 70.0,
      hasCenterAction: true,
      centerActionId: 'camera',
      centerActionIcon: Icons.camera_alt_rounded,
      bottomBarStyleName: 'Active Pill Chip',
    ),
    home: HomeTemplate(
      headerStyle: HomeHeaderStyle.compact,
      showStoriesStrip: true,
      storiesStyle: 'Squircle',
      homeStylePreset: 'Expressive',
      separateChatsAndGroups: false,
    ),
    chatList: ChatListTemplate(
      density: ChatListDensity.regular,
      avatarShape: 'squircle',
      showPresenceBadge: true,
      showDivider: false,
      itemHeight: 70.0,
    ),
    conversation: ConversationTemplate(
      bubbleStyle: '3D',
      tickStyle: 'Sticker',
      bubbleCornerRadius: 20.0,
      enableQuickSidebar: true,
      wallpaperType: 'Pattern',
    ),
    composer: ComposerTemplate(
      actionPlacement: ComposerActionPlacement.integrated,
      cornerRadius: 28.0,
      showVoiceLock: true,
      showCameraShortcut: true,
      enableSendMorph: true,
    ),
    updates: UpdatesTemplate(
      layoutMode: UpdatesLayoutMode.squircleCards,
      enableStatusAudio: true,
      cardElevation: 3.0,
    ),
    profile: ProfileTemplate(
      headerStyle: ProfileHeaderStyle.centeredIdentity,
      avatarShape: 'squircle',
      showStatsGrid: true,
    ),
    calls: CallTemplate(
      enableFloatingIsland: true,
      controlBarCornerRadius: 32.0,
    ),
  );

  static const ChatyTemplateDefinition messageFirst = ChatyTemplateDefinition(
    id: ChatyTemplateId.messageFirst,
    name: 'Message First',
    subtitle: 'Clean & Balanced Direct Messaging',
    description:
        'Straightforward, low-noise communication with standard 4-item bottom navigation and classic bubble geometry.',
    navigation: NavigationTemplate(
      layout: NavigationLayoutType.floatingPill,
      primaryDestinationIds: ['chats', 'updates', 'calls'],
      overflowDestinationIds: ['tasks', 'settings', 'desktop'],
      height: 64.0,
      bottomBarStyleName: 'Floating Pill',
    ),
    home: HomeTemplate(
      headerStyle: HomeHeaderStyle.compact,
      showStoriesStrip: false,
      storiesStyle: 'Circular',
      homeStylePreset: 'Chaty Default',
      separateChatsAndGroups: false,
    ),
    chatList: ChatListTemplate(
      density: ChatListDensity.regular,
      avatarShape: 'circle',
      showPresenceBadge: true,
      showDivider: true,
      itemHeight: 68.0,
    ),
    conversation: ConversationTemplate(
      bubbleStyle: 'Stock',
      tickStyle: 'RC iOS 11',
      bubbleCornerRadius: 16.0,
      enableQuickSidebar: false,
      wallpaperType: 'Pattern',
    ),
    composer: ComposerTemplate(
      actionPlacement: ComposerActionPlacement.integrated,
      cornerRadius: 22.0,
      showVoiceLock: true,
      showCameraShortcut: true,
      enableSendMorph: true,
    ),
    updates: UpdatesTemplate(
      layoutMode: UpdatesLayoutMode.circularRail,
      enableStatusAudio: true,
      cardElevation: 1.0,
    ),
    profile: ProfileTemplate(
      headerStyle: ProfileHeaderStyle.centeredIdentity,
      avatarShape: 'circle',
      showStatsGrid: true,
    ),
    calls: CallTemplate(
      enableFloatingIsland: true,
      controlBarCornerRadius: 24.0,
    ),
  );

  static const ChatyTemplateDefinition powerChat = ChatyTemplateDefinition(
    id: ChatyTemplateId.powerChat,
    name: 'Power Chat',
    subtitle: 'Productivity, Folders & Utility Density',
    description:
        'Separate chat/group tabs, compact utility navigation with More panel, and rich action composer for power users.',
    navigation: NavigationTemplate(
      layout: NavigationLayoutType.utilityBar,
      primaryDestinationIds: ['chats', 'updates', 'calls'],
      overflowDestinationIds: ['tasks', 'settings', 'desktop'],
      height: 60.0,
      bottomBarStyleName: 'Segmented Glass Dock',
    ),
    home: HomeTemplate(
      headerStyle: HomeHeaderStyle.compact,
      showStoriesStrip: false,
      storiesStyle: 'Compact',
      homeStylePreset: 'Productivity',
      separateChatsAndGroups: true,
    ),
    chatList: ChatListTemplate(
      density: ChatListDensity.compact,
      avatarShape: 'squircle',
      showPresenceBadge: true,
      showDivider: true,
      itemHeight: 64.0,
    ),
    conversation: ConversationTemplate(
      bubbleStyle: 'Stock',
      tickStyle: 'RC iOS 11',
      bubbleCornerRadius: 14.0,
      enableQuickSidebar: true,
      wallpaperType: 'Gradient',
    ),
    composer: ComposerTemplate(
      actionPlacement: ComposerActionPlacement.powerRow,
      cornerRadius: 18.0,
      showVoiceLock: true,
      showCameraShortcut: true,
      enableSendMorph: true,
    ),
    updates: UpdatesTemplate(
      layoutMode: UpdatesLayoutMode.gridTiles,
      enableStatusAudio: true,
      cardElevation: 2.0,
    ),
    profile: ProfileTemplate(
      headerStyle: ProfileHeaderStyle.bannerWithAvatar,
      avatarShape: 'squircle',
      showStatsGrid: true,
    ),
    calls: CallTemplate(
      enableFloatingIsland: true,
      controlBarCornerRadius: 20.0,
    ),
  );

  static const ChatyTemplateDefinition community = ChatyTemplateDefinition(
    id: ChatyTemplateId.community,
    name: 'Community',
    subtitle: 'Spaces, Identity Bar & Group Dynamics',
    description:
        'Identity-forward navigation surface, distinct channel grouping, squircle badges, and community spaces focus.',
    navigation: NavigationTemplate(
      layout: NavigationLayoutType.identityBar,
      primaryDestinationIds: ['chats', 'updates', 'calls'],
      overflowDestinationIds: ['tasks', 'settings', 'desktop'],
      height: 66.0,
      bottomBarStyleName: 'Active Pill Chip',
    ),
    home: HomeTemplate(
      headerStyle: HomeHeaderStyle.prominentIdentity,
      showStoriesStrip: true,
      storiesStyle: 'Squircle',
      homeStylePreset: 'Expressive',
      separateChatsAndGroups: true,
    ),
    chatList: ChatListTemplate(
      density: ChatListDensity.regular,
      avatarShape: 'squircle',
      showPresenceBadge: true,
      showDivider: false,
      itemHeight: 72.0,
    ),
    conversation: ConversationTemplate(
      bubbleStyle: '3D',
      tickStyle: 'Sticker',
      bubbleCornerRadius: 18.0,
      enableQuickSidebar: false,
      wallpaperType: 'Pattern',
    ),
    composer: ComposerTemplate(
      actionPlacement: ComposerActionPlacement.split,
      cornerRadius: 24.0,
      showVoiceLock: true,
      showCameraShortcut: true,
      enableSendMorph: true,
    ),
    updates: UpdatesTemplate(
      layoutMode: UpdatesLayoutMode.squircleCards,
      enableStatusAudio: true,
      cardElevation: 2.0,
    ),
    profile: ProfileTemplate(
      headerStyle: ProfileHeaderStyle.bannerWithAvatar,
      avatarShape: 'squircle',
      showStatsGrid: true,
    ),
    calls: CallTemplate(
      enableFloatingIsland: true,
      controlBarCornerRadius: 28.0,
    ),
  );

  static const Map<ChatyTemplateId, ChatyTemplateDefinition> all = {
    ChatyTemplateId.visualSocial: visualSocial,
    ChatyTemplateId.stream: stream,
    ChatyTemplateId.cameraFirst: cameraFirst,
    ChatyTemplateId.messageFirst: messageFirst,
    ChatyTemplateId.powerChat: powerChat,
    ChatyTemplateId.community: community,
  };

  static ChatyTemplateDefinition get(ChatyTemplateId id) {
    return all[id] ?? messageFirst;
  }

  static List<ChatyTemplateDefinition> get list =>
      all.values.toList(growable: false);
}
