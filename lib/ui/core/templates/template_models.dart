import 'package:flutter/material.dart';

/// The 6 original Chaty UI templates derived from mobile UX archetypes.
enum ChatyTemplateId {
  visualSocial(
    'visual_social',
    'Visual Social',
    'Media-first layout with prominent updates and strong identity',
  ),
  stream(
    'stream',
    'Stream',
    'Information-dense, text-forward timeline layout for fast scanning',
  ),
  cameraFirst(
    'camera_first',
    'Camera First',
    'Capture-centric layout with prominent center camera action',
  ),
  messageFirst(
    'message_first',
    'Message First',
    'Clean, conversation-first communication layout with minimal visual noise',
  ),
  powerChat(
    'power_chat',
    'Power Chat',
    'Utility-dense layout with folders, rich composer and power menus',
  ),
  community(
    'community',
    'Community',
    'Spaces and identity-focused layout with persistent navigation surface',
  );

  final String key;
  final String displayName;
  final String description;

  const ChatyTemplateId(this.key, this.displayName, this.description);

  static ChatyTemplateId fromKey(String? key) {
    return ChatyTemplateId.values.firstWhere(
      (e) => e.key == key,
      orElse: () => ChatyTemplateId.messageFirst,
    );
  }
}

/// The major independently selectable and overridable component types.
enum TemplateComponentType {
  navigation(
    'Navigation',
    'Bottom bar / dock layout and destination arrangement',
    Icons.dock_rounded,
  ),
  home(
    'Home Structure',
    'Home feed composition, header, and stories rail',
    Icons.home_outlined,
  ),
  chatList(
    'Chat List',
    'Row density, avatar treatment, and unread badges',
    Icons.format_list_bulleted_rounded,
  ),
  conversation(
    'Conversation',
    'Bubble contours, timestamps, and message spacing',
    Icons.chat_bubble_outline_rounded,
  ),
  composer(
    'Composer',
    'Action density, attachment position, and send morph',
    Icons.input_rounded,
  ),
  updates(
    'Updates',
    'Status rails, story rings, and media presentation',
    Icons.auto_stories_outlined,
  ),
  profile(
    'Profile',
    'Identity hierarchy, banner, and statistics layout',
    Icons.person_outline_rounded,
  ),
  calls(
    'Calls',
    'Call controls, participant grid, and island styling',
    Icons.call_outlined,
  );

  final String title;
  final String description;
  final IconData icon;

  const TemplateComponentType(this.title, this.description, this.icon);
}

// -----------------------------------------------------------------------------
// Component Specifications
// -----------------------------------------------------------------------------

/// Stable IDs for real destinations that can be configured into the primary
/// navigation or the overflow menu. Unknown IDs are never rendered.
class ChatyNavigationDestinationIds {
  const ChatyNavigationDestinationIds._();

  static const chats = 'chats';
  static const groups = 'groups';
  static const updates = 'updates';
  static const tasks = 'tasks';
  static const calls = 'calls';
  static const settings = 'settings';
  static const desktop = 'desktop';

  static const List<String> all = <String>[
    chats,
    groups,
    updates,
    tasks,
    calls,
    settings,
    desktop,
  ];
  static const Set<String> known = <String>{
    chats,
    groups,
    updates,
    tasks,
    calls,
    settings,
    desktop,
  };
}

enum NavigationLayoutType {
  flatTabs,
  floatingPill,
  centerActionDock,
  utilityBar,
  identityBar,
}

class NavigationTemplate {
  final NavigationLayoutType layout;
  final List<String> primaryDestinationIds;
  final List<String> overflowDestinationIds;
  final double height;
  final bool hasCenterAction;
  final String centerActionId;
  final IconData? centerActionIcon;
  final String bottomBarStyleName;

  const NavigationTemplate({
    required this.layout,
    required this.primaryDestinationIds,
    this.overflowDestinationIds = const [],
    this.height = 64.0,
    this.hasCenterAction = false,
    this.centerActionId = '',
    this.centerActionIcon,
    required this.bottomBarStyleName,
  });

  NavigationTemplate copyWith({
    NavigationLayoutType? layout,
    List<String>? primaryDestinationIds,
    List<String>? overflowDestinationIds,
    double? height,
    bool? hasCenterAction,
    String? centerActionId,
    IconData? centerActionIcon,
    String? bottomBarStyleName,
  }) {
    return NavigationTemplate(
      layout: layout ?? this.layout,
      primaryDestinationIds:
          primaryDestinationIds ?? this.primaryDestinationIds,
      overflowDestinationIds:
          overflowDestinationIds ?? this.overflowDestinationIds,
      height: height ?? this.height,
      hasCenterAction: hasCenterAction ?? this.hasCenterAction,
      centerActionId: centerActionId ?? this.centerActionId,
      centerActionIcon: centerActionIcon ?? this.centerActionIcon,
      bottomBarStyleName: bottomBarStyleName ?? this.bottomBarStyleName,
    );
  }
}

enum HomeHeaderStyle { compact, prominentIdentity, searchForward, storiesFirst }

class HomeTemplate {
  final HomeHeaderStyle headerStyle;
  final bool showStoriesStrip;
  final String storiesStyle;
  final String homeStylePreset;
  final bool separateChatsAndGroups;

  const HomeTemplate({
    required this.headerStyle,
    required this.showStoriesStrip,
    required this.storiesStyle,
    required this.homeStylePreset,
    this.separateChatsAndGroups = false,
  });
}

enum ChatListDensity { compact, regular, comfortable }

class ChatListTemplate {
  final ChatListDensity density;
  final String avatarShape;
  final bool showPresenceBadge;
  final bool showDivider;
  final double itemHeight;

  const ChatListTemplate({
    required this.density,
    required this.avatarShape,
    this.showPresenceBadge = true,
    this.showDivider = true,
    this.itemHeight = 72.0,
  });
}

class ConversationTemplate {
  final String bubbleStyle;
  final String tickStyle;
  final double bubbleCornerRadius;
  final bool enableQuickSidebar;
  final String wallpaperType;
  final bool showAvatarInGroup;

  const ConversationTemplate({
    required this.bubbleStyle,
    required this.tickStyle,
    this.bubbleCornerRadius = 16.0,
    this.enableQuickSidebar = false,
    this.wallpaperType = 'Pattern',
    this.showAvatarInGroup = true,
  });
}

enum ComposerActionPlacement { integrated, split, powerRow }

class ComposerTemplate {
  final ComposerActionPlacement actionPlacement;
  final double cornerRadius;
  final bool showVoiceLock;
  final bool showCameraShortcut;
  final bool enableSendMorph;

  const ComposerTemplate({
    required this.actionPlacement,
    this.cornerRadius = 24.0,
    this.showVoiceLock = true,
    this.showCameraShortcut = true,
    this.enableSendMorph = true,
  });
}

enum UpdatesLayoutMode { circularRail, squircleCards, minimalList, gridTiles }

class UpdatesTemplate {
  final UpdatesLayoutMode layoutMode;
  final bool enableStatusAudio;
  final double cardElevation;

  const UpdatesTemplate({
    required this.layoutMode,
    this.enableStatusAudio = true,
    this.cardElevation = 0.0,
  });
}

enum ProfileHeaderStyle { bannerWithAvatar, centeredIdentity, compactHeader }

class ProfileTemplate {
  final ProfileHeaderStyle headerStyle;
  final String avatarShape;
  final bool showStatsGrid;

  const ProfileTemplate({
    required this.headerStyle,
    required this.avatarShape,
    this.showStatsGrid = true,
  });
}

class CallTemplate {
  final bool enableFloatingIsland;
  final double controlBarCornerRadius;

  const CallTemplate({
    this.enableFloatingIsland = true,
    this.controlBarCornerRadius = 28.0,
  });
}

/// The complete structural definition of a Chaty UI Template.
class ChatyTemplateDefinition {
  final ChatyTemplateId id;
  final String name;
  final String subtitle;
  final String description;
  final NavigationTemplate navigation;
  final HomeTemplate home;
  final ChatListTemplate chatList;
  final ConversationTemplate conversation;
  final ComposerTemplate composer;
  final UpdatesTemplate updates;
  final ProfileTemplate profile;
  final CallTemplate calls;

  const ChatyTemplateDefinition({
    required this.id,
    required this.name,
    required this.subtitle,
    required this.description,
    required this.navigation,
    required this.home,
    required this.chatList,
    required this.conversation,
    required this.composer,
    required this.updates,
    required this.profile,
    required this.calls,
  });
}

/// Persisted configuration representing the base template and any component-level overrides.
class UserTemplateConfiguration {
  final ChatyTemplateId baseTemplate;
  final Map<TemplateComponentType, ChatyTemplateId> componentOverrides;

  /// Null means follow the active template's ordering. When set, the pair is
  /// persisted together and all supported destinations remain reachable.
  final List<String>? navigationPrimaryDestinationIds;
  final List<String>? navigationOverflowDestinationIds;

  const UserTemplateConfiguration({
    this.baseTemplate = ChatyTemplateId.messageFirst,
    this.componentOverrides = const {},
    this.navigationPrimaryDestinationIds,
    this.navigationOverflowDestinationIds,
  });

  ChatyTemplateId resolveFor(TemplateComponentType component) {
    return componentOverrides[component] ?? baseTemplate;
  }

  bool isOverridden(TemplateComponentType component) {
    return componentOverrides.containsKey(component);
  }

  bool get hasNavigationDestinationOverride =>
      navigationPrimaryDestinationIds != null ||
      navigationOverflowDestinationIds != null;

  UserTemplateConfiguration copyWithOverride(
    TemplateComponentType component,
    ChatyTemplateId template,
  ) {
    final next = Map<TemplateComponentType, ChatyTemplateId>.of(
      componentOverrides,
    );
    if (template == baseTemplate) {
      next.remove(component);
    } else {
      next[component] = template;
    }
    return UserTemplateConfiguration(
      baseTemplate: baseTemplate,
      componentOverrides: next,
      navigationPrimaryDestinationIds: navigationPrimaryDestinationIds,
      navigationOverflowDestinationIds: navigationOverflowDestinationIds,
    );
  }

  UserTemplateConfiguration removeOverride(TemplateComponentType component) {
    final next = Map<TemplateComponentType, ChatyTemplateId>.of(
      componentOverrides,
    );
    next.remove(component);
    return UserTemplateConfiguration(
      baseTemplate: baseTemplate,
      componentOverrides: next,
      navigationPrimaryDestinationIds: navigationPrimaryDestinationIds,
      navigationOverflowDestinationIds: navigationOverflowDestinationIds,
    );
  }

  UserTemplateConfiguration copyWithNavigationDestinations({
    required List<String> primaryDestinationIds,
    required List<String> overflowDestinationIds,
  }) {
    return UserTemplateConfiguration(
      baseTemplate: baseTemplate,
      componentOverrides: componentOverrides,
      navigationPrimaryDestinationIds:
          List<String>.unmodifiable(primaryDestinationIds),
      navigationOverflowDestinationIds:
          List<String>.unmodifiable(overflowDestinationIds),
    );
  }

  UserTemplateConfiguration withoutNavigationDestinationOverride() {
    return UserTemplateConfiguration(
      baseTemplate: baseTemplate,
      componentOverrides: componentOverrides,
    );
  }

  Map<String, dynamic> toMap() => <String, dynamic>{
    'version': 1,
    'base': baseTemplate.key,
    'overrides': componentOverrides.map((k, v) => MapEntry(k.name, v.key)),
    if (navigationPrimaryDestinationIds != null)
      'navPrimary': navigationPrimaryDestinationIds,
    if (navigationOverflowDestinationIds != null)
      'navOverflow': navigationOverflowDestinationIds,
  };

  factory UserTemplateConfiguration.fromMap(Map<String, dynamic>? map) {
    if (map == null) return const UserTemplateConfiguration();
    final base = ChatyTemplateId.fromKey(map['base'] as String?);
    final rawOverrides = map['overrides'] as Map<String, dynamic>? ?? {};
    final overrides = <TemplateComponentType, ChatyTemplateId>{};
    for (final entry in rawOverrides.entries) {
      final comp = TemplateComponentType.values
          .where((e) => e.name == entry.key)
          .firstOrNull;
      if (comp != null) {
        overrides[comp] = ChatyTemplateId.fromKey(entry.value as String?);
      }
    }

    List<String>? decodeDestinationIds(Object? raw) {
      if (raw is! List) return null;
      final seen = <String>{};
      final ids = <String>[];
      for (final item in raw) {
        if (item is! String ||
            !ChatyNavigationDestinationIds.known.contains(item) ||
            !seen.add(item)) {
          continue;
        }
        ids.add(item);
      }
      return ids.isEmpty ? null : List<String>.unmodifiable(ids);
    }

    return UserTemplateConfiguration(
      baseTemplate: base,
      componentOverrides: overrides,
      navigationPrimaryDestinationIds: decodeDestinationIds(map['navPrimary']),
      navigationOverflowDestinationIds: decodeDestinationIds(map['navOverflow']),
    );
  }
}
