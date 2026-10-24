import '../theme/theme_config.dart';
import '../bubbles/bubble_style_id.dart';
import '../ticks/delivery_icon_style.dart';
import 'template_models.dart';
import 'template_registry.dart';

/// The conversation fields a template writes into the theme when it is
/// applied (bubble style, tick style and bubble corner radius). Palette colours
/// are never touched. [TemplateController] uses this same function when it
/// syncs a template into the runtime theme, so previews and Apply agree.
ThemeConfig themeWithTemplateConversation(
  ThemeConfig current,
  ConversationTemplate conversation,
) {
  return current.copyWith(
    bubbleStyle: BubbleStyleIdExtension.fromString(conversation.bubbleStyle),
    deliveryTickStyle: DeliveryIconStyleExtension.fromString(
      conversation.tickStyle,
    ),
    tickStyle: conversation.tickStyle,
    cornerRadius: conversation.bubbleCornerRadius,
  );
}

/// The component specs a miniature needs, resolved with the same precedence
/// the app uses ([UserTemplateConfiguration.resolveFor]: a component override
/// wins over the base template).
class PreviewLayout {
  const PreviewLayout({
    required this.navigation,
    required this.home,
    required this.chatList,
    required this.conversation,
    required this.composer,
    required this.bottomBarStyle,
    required this.destinationIds,
    required this.homeStylePreset,
    required this.showStories,
  });

  final NavigationTemplate navigation;
  final HomeTemplate home;
  final ChatListTemplate chatList;
  final ConversationTemplate conversation;
  final ComposerTemplate composer;

  /// Name of the bottom bar style (see AppearanceVariantController).
  final String bottomBarStyle;

  /// Destination IDs shown in the bottom bar, after the user's hidden tabs
  /// (for example Tasks and Updates) are removed.
  final List<String> destinationIds;

  /// Home UI preset name and whether the stories strip is shown.
  final String homeStylePreset;
  final bool showStories;

  /// Resolves specs with the app's precedence. Optional values describe saved
  /// user state that the template does not own: pass the saved bottom bar
  /// style, Home UI preset and stories choice to preview the current setup;
  /// leave them null to preview what a template would write.
  factory PreviewLayout.resolve(
    UserTemplateConfiguration config, {
    String? bottomBarStyle,
    Set<String> hiddenDestinationIds = const <String>{},
    String? homeStylePreset,
    bool? showStories,
  }) {
    ChatyTemplateDefinition of(TemplateComponentType c) =>
        ChatyTemplateRegistry.get(config.resolveFor(c));
    final navigation = of(TemplateComponentType.navigation).navigation;
    final home = of(TemplateComponentType.home).home;
    final ids = <String>[
      for (final id
          in config.navigationPrimaryDestinationIds ??
              navigation.primaryDestinationIds)
        if (!hiddenDestinationIds.contains(id)) id,
    ];
    return PreviewLayout(
      navigation: navigation,
      home: home,
      chatList: of(TemplateComponentType.chatList).chatList,
      conversation: of(TemplateComponentType.conversation).conversation,
      composer: of(TemplateComponentType.composer).composer,
      bottomBarStyle: bottomBarStyle ?? navigation.bottomBarStyleName,
      destinationIds: ids.take(5).toList(),
      homeStylePreset: homeStylePreset ?? home.homeStylePreset,
      showStories: showStories ?? home.showStoriesStrip,
    );
  }
}

/// A pure, read-only description of what Home and Conversation look like for a
/// palette plus a template configuration. It reads and writes no stores.
class AppliedPreviewModel {
  const AppliedPreviewModel({required this.theme, required this.layout});

  final ThemeConfig theme;
  final PreviewLayout layout;

  /// [applyTemplateConversation] is true when previewing "Apply template":
  /// that action also writes the template's bubble style, tick style and
  /// corner radius into the theme. It is false for the state that is already
  /// active, or when previewing a theme preset (which replaces the theme).
  factory AppliedPreviewModel.resolve({
    required ThemeConfig palette,
    required UserTemplateConfiguration config,
    required bool applyTemplateConversation,
  }) {
    final layout = PreviewLayout.resolve(config);
    return AppliedPreviewModel(
      theme: applyTemplateConversation
          ? themeWithTemplateConversation(palette, layout.conversation)
          : palette,
      layout: layout,
    );
  }
}
