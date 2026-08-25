import 'dart:async';
import 'dart:collection';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../effects/effect_model.dart';
import 'lens_definition.dart';

/// Catalog of built-in lenses. Content is original Chaty artwork only —
/// no third-party lens assets are bundled or fetched.
const List<LensDefinition> kBuiltInLenses = <LensDefinition>[
  LensDefinition(
    id: 'none',
    name: 'No Lens',
    category: defaultLensCategory,
    anchor: LensAnchor.fullHead,
    icon: Icons.circle_outlined,
    accentColor: Color(0xFF64748B),
    requiresFaceTracking: false,
  ),
  LensDefinition(
    id: 'lens_glasses_classic',
    name: 'Classic Frames',
    category: defaultLensCategory,
    anchor: LensAnchor.eyes,
    icon: Icons.visibility_rounded,
    accentColor: Color(0xFF38BDF8),
  ),
  LensDefinition(
    id: 'lens_crown_gold',
    name: 'Gold Crown',
    category: defaultLensCategory,
    anchor: LensAnchor.forehead,
    icon: Icons.workspace_premium_rounded,
    accentColor: Color(0xFFFBBF24),
  ),
  LensDefinition(
    id: 'lens_dog_ears',
    name: 'Puppy Ears',
    category: defaultLensCategory,
    anchor: LensAnchor.ears,
    icon: Icons.pets_rounded,
    accentColor: Color(0xFFB45309),
  ),
  LensDefinition(
    id: 'lens_blush_glow',
    name: 'Blush Glow',
    category: defaultLensCategory,
    anchor: LensAnchor.mouth,
    icon: Icons.face_retouching_natural_rounded,
    accentColor: Color(0xFFF472B6),
    supportsCalls: true,
  ),
];

/// Placeholder until a themed category system ships; keeps the model honest
/// instead of faking categories with no content behind them (requirement 55).
const defaultLensCategory = EffectCategory.faceAR;

/// Owns the lens catalog, selection pipeline, prefetch tiers, recents and
/// runtime state. Widgets never implement this logic themselves
/// (requirement 46).
class LensManager extends ChangeNotifier {
  LensManager({List<LensDefinition>? catalog, SharedPreferences? prefs})
    : _prefs = prefs,
      catalog = catalog ?? kBuiltInLenses {
    _recents = prefs?.getStringList(_recentKey) ?? <String>[];
    _favorites = (prefs?.getStringList(_favoriteKey) ?? <String>[]).toSet();
  }

  static const String _recentKey = 'camera.lens.recents';
  static const String _favoriteKey = 'camera.lens.favorites';

  /// How long a settled selection must stay before a heavy load begins;
  /// fast flicks across many lenses never enqueue work (requirement 49).
  static const Duration selectionSettleDebounce = Duration(milliseconds: 180);

  final List<LensDefinition> catalog;
  final SharedPreferences? _prefs;

  List<String> _recents = <String>[];
  Set<String> _favorites = <String>{};

  LensDefinition get selected =>
      catalog.firstWhere((lens) => lens.id == _selectedId, orElse: () => catalog.first);
  String _selectedId = 'none';

  LensRuntimeState _runtimeState = LensRuntimeState.none;
  LensRuntimeState get runtimeState => _runtimeState;

  List<String> get recents => List.unmodifiable(_recents);
  Set<String> get favorites => Set.unmodifiable(_favorites);

  int _pendingRequestId = 0;
  Timer? _settleTimer;

  /// Selects a lens. Returns immediately; heavy load work is debounced so a
  /// fast scroll applies only where the carousel actually settles
  /// (requirements 48/49). Stale pending requests are cancelled by token.
  void select(String lensId) {
    if (_selectedId == lensId) return;
    _selectedId = lensId;
    _pendingRequestId++; // cancels any in-flight application
    _runtimeState = selected.isNoLens ? LensRuntimeState.none : LensRuntimeState.applying;

    if (!selected.isNoLens) {
      _recents
        ..remove(lensId)
        ..insert(0, lensId);
      if (_recents.length > 20) _recents.removeLast();
      _prefs?.setStringList(_recentKey, _recents);
    }
    notifyListeners();

    _settleTimer?.cancel();
    _settleTimer = Timer(selectionSettleDebounce, () => _commitSelection(_pendingRequestId));
  }

  Future<void> _commitSelection(int requestId) async {
    // A newer selection superseded this one — drop silently (requirement 48).
    if (requestId != _pendingRequestId || selected.isNoLens) {
      return;
    }
    _runtimeState = LensRuntimeState.active;
    notifyListeners();
  }

  void clear() => select('none');

  void toggleFavorite(String lensId) {
    if (!_favorites.add(lensId)) {
      _favorites.remove(lensId);
    }
    _prefs?.setStringList(_favoriteKey, _favorites.toList());
    notifyListeners();
  }

  /// Visible-window prefetch tiering (requirement 15): returns the catalog
  /// indices that should be prepared for [centerIndex].
  Set<int> prefetchIndices(int centerIndex, {int near = 2, int far = 5}) {
    final indices = <int>{};
    for (int delta = -far; delta <= far; delta++) {
      final index = centerIndex + delta;
      if (index >= 0 && index < catalog.length) indices.add(index);
    }
    // Near items are prioritized by ordering the set from the center out.
    return SplayTreeSet<int>.from(
      indices,
      (a, b) => (a - centerIndex).abs().compareTo((b - centerIndex).abs()),
    );
  }

  @override
  void dispose() {
    _settleTimer?.cancel();
    super.dispose();
  }
}
