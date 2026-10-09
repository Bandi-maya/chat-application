import 'package:flutter/material.dart';
import 'chaty_component_registry.dart';
import 'chaty_component_spec.dart';
import 'component_capabilities.dart';
import 'component_scope.dart';
import 'customization_snapshot.dart';

/// Resolves the effective component variant based on scope hierarchy and device/window capabilities.
class ComponentVariantResolver {
  final ChatyComponentRegistry registry;

  ComponentVariantResolver({ChatyComponentRegistry? registry})
      : registry = registry ?? ChatyComponentRegistry.instance;

  ComponentSpec resolve({
    required ComponentId componentId,
    required CustomizationSnapshot snapshot,
    required VariantContext context,
    ComponentScope scope = ComponentScope.globalUser,
    CustomizationSnapshot? previewSnapshot,
  }) {
    String candidateId = '';

    // 1. Check transient preview snapshot if available
    if (scope == ComponentScope.transientPreview && previewSnapshot != null) {
      candidateId = _extractVariant(componentId, previewSnapshot);
    }

    // 2. Check conversation overrides
    if (candidateId.isEmpty &&
        scope.type == CustomizationScopeType.conversation &&
        scope.identifier != null) {
      final overrides = snapshot.conversationOverrides[scope.identifier!];
      if (overrides != null && overrides.containsKey(componentId.name)) {
        candidateId = overrides[componentId.name]!;
      }
    }

    // 3. Check screen overrides
    if (candidateId.isEmpty &&
        scope.type == CustomizationScopeType.screen &&
        scope.identifier != null) {
      final overrides = snapshot.screenOverrides[scope.identifier!];
      if (overrides != null && overrides.containsKey(componentId.name)) {
        candidateId = overrides[componentId.name]!;
      }
    }

    // 4. Check global snapshot
    if (candidateId.isEmpty) {
      candidateId = _extractVariant(componentId, snapshot);
    }

    // 5. Fallback to registry default if empty
    if (candidateId.isEmpty) {
      candidateId = registry.getDefaultVariantId(componentId);
    }

    // 6. Capability negotiation & fallback fallback
    return registry.nearestCompatible(componentId, candidateId, context);
  }

  String _extractVariant(ComponentId id, CustomizationSnapshot snapshot) {
    switch (id) {
      case ComponentId.navigation:
        return snapshot.navigationVariant;
      case ComponentId.header:
        return snapshot.headerVariant;
      case ComponentId.composer:
        return snapshot.composerVariant;
      case ComponentId.bubble:
        return snapshot.bubbleVariant;
      case ComponentId.tick:
        return snapshot.tickVariant;
      case ComponentId.chatList:
        return snapshot.chatListVariant;
      case ComponentId.reactionRail:
        return snapshot.reactionVariant;
      case ComponentId.modal:
      case ComponentId.sheet:
      case ComponentId.actionSheet:
      case ComponentId.contextMenu:
        return snapshot.popupVariant;
      case ComponentId.button:
        return snapshot.buttonVariant;
      case ComponentId.textField:
        return snapshot.textFieldVariant;
      case ComponentId.avatar:
        return snapshot.avatarVariant;
      case ComponentId.wallpaper:
        return snapshot.wallpaperId;
      default:
        return '';
    }
  }

  static ComponentSpec resolveForContext({
    required BuildContext context,
    required ComponentId componentId,
    required CustomizationSnapshot snapshot,
    ComponentScope scope = ComponentScope.globalUser,
    CustomizationSnapshot? previewSnapshot,
    BoxConstraints? localConstraints,
  }) {
    final variantContext = VariantContext.fromContext(
      context,
      localConstraints: localConstraints,
    );
    return ComponentVariantResolver().resolve(
      componentId: componentId,
      snapshot: snapshot,
      context: variantContext,
      scope: scope,
      previewSnapshot: previewSnapshot,
    );
  }
}
