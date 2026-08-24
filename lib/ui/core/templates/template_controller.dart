import 'dart:async';
import 'package:flutter/foundation.dart';
import '../controllers/appearance_variant_controller.dart';
import '../controllers/preferences_controller.dart';
import '../persistence/preferences_storage.dart';
import 'template_models.dart';
import 'template_registry.dart';

/// Central Template Controller that manages base template and component-level overrides.
///
/// Dispatches template parameters into underlying runtime controllers ([AppearanceVariantController], [ChatyPreferencesController])
/// while preserving the user's manual settings and persistence.
class TemplateController extends ChangeNotifier {
  UserTemplateConfiguration _config = const UserTemplateConfiguration(
    baseTemplate: ChatyTemplateId.messageFirst,
    componentOverrides: {},
  );
  bool _initialized = false;

  UserTemplateConfiguration get config => _config;
  ChatyTemplateId get baseTemplate => _config.baseTemplate;
  Map<TemplateComponentType, ChatyTemplateId> get componentOverrides => _config.componentOverrides;

  /// Resolves the effective template ID for a specific component.
  ChatyTemplateId resolveTemplateFor(TemplateComponentType component) {
    return _config.resolveFor(component);
  }

  /// Resolves the concrete component definition for a specific component.
  NavigationTemplate get navigation =>
      ChatyTemplateRegistry.get(resolveTemplateFor(TemplateComponentType.navigation)).navigation;

  HomeTemplate get home =>
      ChatyTemplateRegistry.get(resolveTemplateFor(TemplateComponentType.home)).home;

  ChatListTemplate get chatList =>
      ChatyTemplateRegistry.get(resolveTemplateFor(TemplateComponentType.chatList)).chatList;

  ConversationTemplate get conversation =>
      ChatyTemplateRegistry.get(resolveTemplateFor(TemplateComponentType.conversation)).conversation;

  ComposerTemplate get composer =>
      ChatyTemplateRegistry.get(resolveTemplateFor(TemplateComponentType.composer)).composer;

  UpdatesTemplate get updates =>
      ChatyTemplateRegistry.get(resolveTemplateFor(TemplateComponentType.updates)).updates;

  ProfileTemplate get profile =>
      ChatyTemplateRegistry.get(resolveTemplateFor(TemplateComponentType.profile)).profile;

  CallTemplate get calls =>
      ChatyTemplateRegistry.get(resolveTemplateFor(TemplateComponentType.calls)).calls;

  /// Initializes the template controller and restores saved configuration from storage.
  Future<void> init({
    AppearanceVariantController? appearanceController,
    ChatyPreferencesController? preferencesController,
  }) async {
    if (_initialized) return;
    _initialized = true;
    try {
      final state = await LocalPreferencesStorage.loadTemplateState();
      if (state.isNotEmpty) {
        _config = UserTemplateConfiguration.fromMap(state);
      }
    } catch (e) {
      debugPrint('TemplateController: failed to load state (), using defaults');
    }
    _syncToRuntimeControllers(
      appearanceController: appearanceController,
      preferencesController: preferencesController,
    );
  }

  /// Applies a full template across all components, resetting component overrides.
  Future<void> applyFullTemplate(
    ChatyTemplateId templateId, {
    AppearanceVariantController? appearanceController,
    ChatyPreferencesController? preferencesController,
  }) async {
    _config = UserTemplateConfiguration(
      baseTemplate: templateId,
      componentOverrides: const {},
    );
    notifyListeners();
    _persist();
    _syncToRuntimeControllers(
      appearanceController: appearanceController,
      preferencesController: preferencesController,
    );
  }

  /// Applies an override for a single component only.
  Future<void> applyComponent({
    required TemplateComponentType component,
    required ChatyTemplateId templateId,
    AppearanceVariantController? appearanceController,
    ChatyPreferencesController? preferencesController,
  }) async {
    _config = _config.copyWithOverride(component, templateId);
    notifyListeners();
    _persist();
    _syncToRuntimeControllers(
      appearanceController: appearanceController,
      preferencesController: preferencesController,
    );
  }

  /// Removes an override for a single component, falling back to base template.
  Future<void> removeComponentOverride(
    TemplateComponentType component, {
    AppearanceVariantController? appearanceController,
    ChatyPreferencesController? preferencesController,
  }) async {
    _config = _config.removeOverride(component);
    notifyListeners();
    _persist();
    _syncToRuntimeControllers(
      appearanceController: appearanceController,
      preferencesController: preferencesController,
    );
  }

  /// Resets everything back to default Message First template with zero overrides.
  Future<void> resetToDefaults({
    AppearanceVariantController? appearanceController,
    ChatyPreferencesController? preferencesController,
  }) async {
    _config = const UserTemplateConfiguration(
      baseTemplate: ChatyTemplateId.messageFirst,
      componentOverrides: {},
    );
    notifyListeners();
    _persist();
    _syncToRuntimeControllers(
      appearanceController: appearanceController,
      preferencesController: preferencesController,
    );
  }

  void _syncToRuntimeControllers({
    AppearanceVariantController? appearanceController,
    ChatyPreferencesController? preferencesController,
  }) {
    // 1. Sync Navigation bottom bar style
    if (appearanceController != null) {
      final targetBarStyle = navigation.bottomBarStyleName;
      if (appearanceController.bottomBarStyle != targetBarStyle) {
        appearanceController.setBottomBarStyle(targetBarStyle);
      }
    }

    // 2. Sync Home and Conversation models in PreferencesController
    if (preferencesController != null) {
      final h = home;
      final c = conversation;

      final currentHome = preferencesController.home;
      if (currentHome.homeStyle != h.homeStylePreset ||
          currentHome.enableStoriesStrip != h.showStoriesStrip ||
          currentHome.storiesStyle != h.storiesStyle ||
          currentHome.separateChatsAndGroups != h.separateChatsAndGroups) {
        preferencesController.updateHome(
          currentHome.copyWith(
            homeStyle: h.homeStylePreset,
            enableStoriesStrip: h.showStoriesStrip,
            storiesStyle: h.storiesStyle,
            separateChatsAndGroups: h.separateChatsAndGroups,
          ),
          logTitle: 'Template Home Sync',
        );
      }

      final currentConv = preferencesController.conversation;
      if (currentConv.bubbleStyle != c.bubbleStyle ||
          currentConv.tickStyle != c.tickStyle ||
          currentConv.enableQuickContactSidebar != c.enableQuickSidebar ||
          currentConv.wallpaperType != c.wallpaperType) {
        preferencesController.updateConversation(
          currentConv.copyWith(
            bubbleStyle: c.bubbleStyle,
            tickStyle: c.tickStyle,
            enableQuickContactSidebar: c.enableQuickSidebar,
            wallpaperType: c.wallpaperType,
          ),
          logTitle: 'Template Conversation Sync',
        );
      }
    }
  }

  void _persist() {
    unawaited(LocalPreferencesStorage.saveTemplateState(_config.toMap()));
  }
}
