import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'chaty_component_spec.dart';
import 'component_scope.dart';
import 'customization_history.dart';
import 'customization_migration.dart';
import 'customization_snapshot.dart';

class CustomizationController extends ChangeNotifier {
  static const String _storageKey = 'chaty.customization.snapshot_v1';

  CustomizationSnapshot _snapshot = CustomizationSnapshot.defaultSnapshot;
  CustomizationSnapshot? _previewSnapshot;
  final CustomizationHistory _history = CustomizationHistory();
  bool _isLoaded = false;

  CustomizationController() {
    _load();
  }

  CustomizationSnapshot get snapshot => _snapshot;
  CustomizationSnapshot? get previewSnapshot => _previewSnapshot;
  CustomizationSnapshot get effectiveSnapshot => _previewSnapshot ?? _snapshot;
  bool get isLoaded => _isLoaded;
  bool get isPreviewing => _previewSnapshot != null;
  bool get isDirty => _previewSnapshot != null && _previewSnapshot != _snapshot;
  bool get canUndo => _history.canUndo;

  Future<void> _load() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_storageKey);
    if (raw != null && raw.isNotEmpty) {
      try {
        final decoded = CustomizationSnapshot.decode(raw);
        if (decoded != null) {
          _snapshot = CustomizationMigration.migrate(decoded.toJson());
        }
      } catch (_) {}
    }
    _isLoaded = true;
    notifyListeners();
  }

  void stage(CustomizationSnapshot preview) {
    _previewSnapshot = preview;
    notifyListeners();
  }

  void cancelPreview() {
    if (_previewSnapshot != null) {
      _previewSnapshot = null;
      notifyListeners();
    }
  }

  Future<void> commit({String description = 'Updated customization'}) async {
    final target = _previewSnapshot ?? _snapshot;
    final previous = _snapshot;
    _history.record(
      scope: ComponentScope.globalUser,
      previous: previous,
      next: target,
      description: description,
    );

    _snapshot = target;
    _previewSnapshot = null;
    notifyListeners();

    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_storageKey, _snapshot.encode());
  }

  Future<void> undo() async {
    final item = _history.popUndo();
    if (item == null) return;

    _snapshot = item.previous;
    _previewSnapshot = null;
    notifyListeners();

    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_storageKey, _snapshot.encode());
  }

  Future<void> setVariant(ComponentId componentId, String variantId, {bool immediate = false}) async {
    CustomizationSnapshot updated;
    switch (componentId) {
      case ComponentId.navigation:
        updated = effectiveSnapshot.copyWith(navigationVariant: variantId);
        break;
      case ComponentId.header:
        updated = effectiveSnapshot.copyWith(headerVariant: variantId);
        break;
      case ComponentId.composer:
        updated = effectiveSnapshot.copyWith(composerVariant: variantId);
        break;
      case ComponentId.bubble:
        updated = effectiveSnapshot.copyWith(bubbleVariant: variantId);
        break;
      case ComponentId.tick:
        updated = effectiveSnapshot.copyWith(tickVariant: variantId);
        break;
      case ComponentId.chatList:
        updated = effectiveSnapshot.copyWith(chatListVariant: variantId);
        break;
      case ComponentId.reactionRail:
        updated = effectiveSnapshot.copyWith(reactionVariant: variantId);
        break;
      case ComponentId.modal:
      case ComponentId.sheet:
      case ComponentId.actionSheet:
      case ComponentId.contextMenu:
        updated = effectiveSnapshot.copyWith(popupVariant: variantId);
        break;
      case ComponentId.button:
        updated = effectiveSnapshot.copyWith(buttonVariant: variantId);
        break;
      case ComponentId.textField:
        updated = effectiveSnapshot.copyWith(textFieldVariant: variantId);
        break;
      case ComponentId.avatar:
        updated = effectiveSnapshot.copyWith(avatarVariant: variantId);
        break;
      case ComponentId.wallpaper:
        updated = effectiveSnapshot.copyWith(wallpaperId: variantId);
        break;
      default:
        return;
    }

    if (immediate) {
      stage(updated);
      await commit();
    } else {
      stage(updated);
    }
  }

  Future<void> setConversationOverride({
    required String conversationId,
    required ComponentId componentId,
    required String variantId,
  }) async {
    final currentMap = Map<String, Map<String, String>>.from(_snapshot.conversationOverrides);
    final convMap = Map<String, String>.from(currentMap[conversationId] ?? {});
    convMap[componentId.name] = variantId;
    currentMap[conversationId] = convMap;

    _snapshot = _snapshot.copyWith(conversationOverrides: currentMap);
    notifyListeners();

    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_storageKey, _snapshot.encode());
  }

  Future<void> resetConversation(String conversationId) async {
    final currentMap = Map<String, Map<String, String>>.from(_snapshot.conversationOverrides);
    currentMap.remove(conversationId);

    _snapshot = _snapshot.copyWith(conversationOverrides: currentMap);
    notifyListeners();

    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_storageKey, _snapshot.encode());
  }

  Future<void> resetAll() async {
    final previous = _snapshot;
    _history.record(
      scope: ComponentScope.defaultTheme,
      previous: previous,
      next: CustomizationSnapshot.defaultSnapshot,
      description: 'Reset all customizations to defaults',
    );

    _snapshot = CustomizationSnapshot.defaultSnapshot;
    _previewSnapshot = null;
    notifyListeners();

    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_storageKey, _snapshot.encode());
  }
}
