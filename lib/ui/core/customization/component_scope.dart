import 'package:flutter/foundation.dart';

/// Hierarchy of customization scopes from lowest to highest precedence.
enum CustomizationScopeType {
  defaultTheme,
  globalUser,
  screen,
  conversation,
  transientPreview,
}

@immutable
class ComponentScope {
  final CustomizationScopeType type;
  final String? identifier;

  const ComponentScope._(this.type, [this.identifier]);

  static const ComponentScope defaultTheme = ComponentScope._(CustomizationScopeType.defaultTheme);
  static const ComponentScope globalUser = ComponentScope._(CustomizationScopeType.globalUser);
  static const ComponentScope transientPreview = ComponentScope._(CustomizationScopeType.transientPreview);

  factory ComponentScope.screen(String screenName) =>
      ComponentScope._(CustomizationScopeType.screen, screenName);

  factory ComponentScope.conversation(String conversationId) =>
      ComponentScope._(CustomizationScopeType.conversation, conversationId);

  int get precedence => type.index;

  bool hasHigherPrecedenceThan(ComponentScope other) => precedence > other.precedence;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ComponentScope &&
          runtimeType == other.runtimeType &&
          type == other.type &&
          identifier == other.identifier;

  @override
  int get hashCode => type.hashCode ^ (identifier?.hashCode ?? 0);

  @override
  String toString() => 'ComponentScope(${type.name}${identifier != null ? ':$identifier' : ''})';
}
