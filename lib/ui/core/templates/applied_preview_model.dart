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
  });

  final NavigationTemplate navigation;
  final HomeTemplate home;
  final ChatListTemplate chatList;
  final ConversationTemplate conversation;
  final ComposerTemplate composer;

  factory PreviewLayout.resolve(UserTemplateConfiguration config) {
    ChatyTemplateDefinition of(TemplateComponentType c) =>
        ChatyTemplateRegistry.get(config.resolveFor(c));
    return PreviewLayout(
      navigation: of(TemplateComponentType.navigation).navigation,
      home: of(TemplateComponentType.home).home,
      chatList: of(TemplateComponentType.chatList).chatList,
      conversation: of(TemplateComponentType.conversation).conversation,
      composer: of(TemplateComponentType.composer).composer,
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
