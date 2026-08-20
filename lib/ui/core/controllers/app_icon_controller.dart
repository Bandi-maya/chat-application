import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'custom_app_icon_processor.dart';

enum LauncherIconVariant {
  original,
  minimal,
  bubble,
  midnight,
  ocean,
  violet,
}

enum BrandIconSource { bundled, custom }

enum CustomLauncherState {
  inactive,
  pending,
  active,
  failed,
  unsupported,
}

enum CustomIconInputSource { photos, camera }

class CustomIconPreset {
  final String id;
  final String path;
  final int createdAt;

  const CustomIconPreset({
    required this.id,
    required this.path,
    required this.createdAt,
  });

  bool get exists => path.isNotEmpty && File(path).existsSync();

  Map<String, dynamic> toJson() => <String, dynamic>{
        'id': id,
        'path': path,
        'createdAt': createdAt,
      };

  static CustomIconPreset? fromJson(dynamic value) {
    if (value is! Map) return null;
    final id = value['id'];
    final path = value['path'];
    final createdAt = value['createdAt'];
    if (id is! String || id.isEmpty || path is! String || path.isEmpty) {
      return null;
    }
    return CustomIconPreset(
      id: id,
      path: path,
      createdAt: createdAt is int ? createdAt : 0,
    );
  }
}

extension LauncherIconVariantMetadata on LauncherIconVariant {
  String get id => name;

  String get title {
    switch (this) {
      case LauncherIconVariant.original:
        return 'Original';
      case LauncherIconVariant.minimal:
        return 'Minimal';
      case LauncherIconVariant.bubble:
        return 'Bubble';
      case LauncherIconVariant.midnight:
        return 'Midnight';
      case LauncherIconVariant.ocean:
        return 'Ocean';
      case LauncherIconVariant.violet:
        return 'Violet';
    }
  }

  String get androidAlias => name;

  static LauncherIconVariant fromId(String? value) {
    return LauncherIconVariant.values.firstWhere(
      (variant) => variant.id == value,
      orElse: () => LauncherIconVariant.original,
    );
  }
}

class AppIconController extends ChangeNotifier {
  static const MethodChannel _channel = MethodChannel('chaty/launcher_icon');
  static const String _launcherPreferenceKey = 'chaty_launcher_icon_v1';
  static const String _brandSourcePreferenceKey = 'chaty_brand_source_v1';
  static const String _customBrandPathPreferenceKey = 'chaty_custom_brand_icon_path_v1';
  static const String _customHomeShortcutPreferenceKey = 'chaty_custom_home_shortcut_v1';
  static const String _customLibraryPreferenceKey = 'chaty_custom_icon_library_v2';
  static const String _activeCustomPresetPreferenceKey = 'chaty_active_custom_icon_v2';

  LauncherIconVariant _launcherIcon = LauncherIconVariant.original;
  BrandIconSource _brandIconSource = BrandIconSource.bundled;
  CustomLauncherState _customLauncherState = CustomLauncherState.inactive;
  final List<CustomIconPreset> _customIconPresets = <CustomIconPreset>[];
  String? _activeCustomPresetId;
  String? _customBrandIconPath;
  bool _customHomeShortcutApplied = false;
  bool _initialized = false;
  bool _isApplyingLauncherIcon = false;
  bool _isSavingCustomBrandIcon = false;
  bool _isRefreshingNativeState = false;
  String? _lastError;

  LauncherIconVariant get launcherIcon => _launcherIcon;
  BrandIconSource get brandIconSource => _brandIconSource;
  CustomLauncherState get customLauncherState => _customLauncherState;
  String? get customBrandIconPath => _customBrandIconPath;
  String? get activeCustomPresetId => _activeCustomPresetId;
  List<CustomIconPreset> get customIconPresets => List.unmodifiable(_customIconPresets);
  bool get customHomeShortcutApplied => _customHomeShortcutApplied;
  bool get customLauncherModeActive => _customLauncherState == CustomLauncherState.active;
  bool get hasSavedCustomIcon => _customIconPresets.any((preset) => preset.exists);
  bool get initialized => _initialized;
  bool get isApplyingLauncherIcon => _isApplyingLauncherIcon;
  bool get isSavingCustomBrandIcon => _isSavingCustomBrandIcon;
  bool get isBusy => _isApplyingLauncherIcon || _isSavingCustomBrandIcon;
  String? get lastError => _lastError;

  CustomIconPreset? get activeCustomPreset {
    final id = _activeCustomPresetId;
    if (id == null) return null;
    for (final preset in _customIconPresets) {
      if (preset.id == id && preset.exists) return preset;
    }
    return null;
  }

  Future<void> initialize() async {
    if (_initialized) return;
    final prefs = await SharedPreferences.getInstance();
    _launcherIcon = LauncherIconVariantMetadata.fromId(
      prefs.getString(_launcherPreferenceKey),
    );
    _brandIconSource =
        prefs.getString(_brandSourcePreferenceKey) == BrandIconSource.custom.name
            ? BrandIconSource.custom
            : BrandIconSource.bundled;
    _customHomeShortcutApplied =
        prefs.getBool(_customHomeShortcutPreferenceKey) ?? false;

    await _loadCustomLibrary(prefs);
    await _migrateLegacyCustomIcon(prefs);
    await _cleanMissingPresets(prefs);

    _activeCustomPresetId = prefs.getString(_activeCustomPresetPreferenceKey);
    _syncActiveCustomPath();

    if (_brandIconSource == BrandIconSource.custom && activeCustomPreset == null) {
      _brandIconSource = BrandIconSource.bundled;
      _customLauncherState = CustomLauncherState.inactive;
      await prefs.setString(_brandSourcePreferenceKey, BrandIconSource.bundled.name);
    }

    await refreshNativeLauncherState(notify: false);
    _initialized = true;
    notifyListeners();
  }

  Future<void> _loadCustomLibrary(SharedPreferences prefs) async {
    _customIconPresets.clear();
    final raw = prefs.getString(_customLibraryPreferenceKey);
    if (raw == null || raw.isEmpty) return;
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! List) return;
      for (final item in decoded) {
        final preset = CustomIconPreset.fromJson(item);
        if (preset != null) _customIconPresets.add(preset);
      }
    } catch (error) {
      debugPrint('Custom icon library decode failed: $error');
    }
  }

  Future<void> _migrateLegacyCustomIcon(SharedPreferences prefs) async {
    if (_customIconPresets.isNotEmpty) return;
    final legacyPath = prefs.getString(_customBrandPathPreferenceKey);
    if (legacyPath == null || legacyPath.isEmpty || !File(legacyPath).existsSync()) {
      return;
    }
    final preset = CustomIconPreset(
      id: 'legacy_${DateTime.now().millisecondsSinceEpoch}',
      path: legacyPath,
      createdAt: DateTime.now().millisecondsSinceEpoch,
    );
    _customIconPresets.add(preset);
    _activeCustomPresetId = preset.id;
    await prefs.setString(_activeCustomPresetPreferenceKey, preset.id);
    await _persistLibrary(prefs);
  }

  Future<void> _cleanMissingPresets(SharedPreferences prefs) async {
    final before = _customIconPresets.length;
    _customIconPresets.removeWhere((preset) => !preset.exists);
    if (_customIconPresets.length != before) {
      await _persistLibrary(prefs);
    }
  }

  Future<void> _persistLibrary(SharedPreferences prefs) async {
    await prefs.setString(
      _customLibraryPreferenceKey,
      jsonEncode(_customIconPresets.map((preset) => preset.toJson()).toList()),
    );
  }

  void _syncActiveCustomPath() {
    _customBrandIconPath = activeCustomPreset?.path;
  }

  Future<void> refreshNativeLauncherState({bool notify = true}) async {
    if (_isRefreshingNativeState || kIsWeb || !Platform.isAndroid) return;
    _isRefreshingNativeState = true;
    try {
      final prefs = await SharedPreferences.getInstance();
      final activeAlias =
          await _channel.invokeMethod<String>('getCurrentLauncherIcon');
      if (activeAlias != null && activeAlias.isNotEmpty) {
        final nativeVariant = LauncherIconVariantMetadata.fromId(activeAlias);
        _launcherIcon = nativeVariant;
        await prefs.setString(_launcherPreferenceKey, nativeVariant.id);
      }

      final nativeState =
          await _channel.invokeMethod<String>('getCustomLauncherState') ?? 'inactive';
      _customLauncherState = _stateFromNative(nativeState);
      _customHomeShortcutApplied =
          await _channel.invokeMethod<bool>('isCustomHomeShortcutPinned') ?? false;
      await prefs.setBool(
        _customHomeShortcutPreferenceKey,
        _customHomeShortcutApplied,
      );

      if (_brandIconSource == BrandIconSource.custom && activeCustomPreset == null) {
        _brandIconSource = BrandIconSource.bundled;
        _customLauncherState = CustomLauncherState.inactive;
        await prefs.setString(
          _brandSourcePreferenceKey,
          BrandIconSource.bundled.name,
        );
      }
    } catch (error) {
      _lastError = 'Unable to verify the launcher icon on this device.';
      debugPrint('Launcher icon state refresh failed: $error');
    } finally {
      _isRefreshingNativeState = false;
      if (notify) notifyListeners();
    }
  }

  Future<bool> applyLauncherIcon(LauncherIconVariant variant) async {
    if (_isApplyingLauncherIcon) return false;
    _isApplyingLauncherIcon = true;
    _lastError = null;
    notifyListeners();

    final previous = _launcherIcon;
    final previousSource = _brandIconSource;
    final previousCustomState = _customLauncherState;
    try {
      if (!kIsWeb && Platform.isAndroid) {
        final applied = await _channel.invokeMethod<String>(
          'setLauncherIcon',
          <String, dynamic>{'alias': variant.androidAlias},
        );
        if (applied != variant.androidAlias) {
          throw PlatformException(
            code: 'launcher_icon_mismatch',
            message: 'Android did not confirm the selected launcher icon.',
          );
        }
      }

      final prefs = await SharedPreferences.getInstance();
      _launcherIcon = variant;
      _brandIconSource = BrandIconSource.bundled;
      _customLauncherState = CustomLauncherState.inactive;
      _customHomeShortcutApplied = false;
      await prefs.setString(_launcherPreferenceKey, variant.id);
      await prefs.setString(
        _brandSourcePreferenceKey,
        BrandIconSource.bundled.name,
      );
      await prefs.setBool(_customHomeShortcutPreferenceKey, false);
      return true;
    } catch (error) {
      _launcherIcon = previous;
      _brandIconSource = previousSource;
      _customLauncherState = previousCustomState;
      _lastError =
          'Could not apply that launcher icon. Your previous icon is still active.';
      debugPrint('Launcher icon change failed: $error');
      return false;
    } finally {
      _isApplyingLauncherIcon = false;
      notifyListeners();
    }
  }

  Future<String?> pickCustomIconImage(CustomIconInputSource source) async {
    if (kIsWeb || !Platform.isAndroid) return null;
    _lastError = null;
    try {
      return await _channel.invokeMethod<String>(
        'pickCustomIconImage',
        <String, dynamic>{'source': source.name},
      );
    } on PlatformException catch (error) {
      _lastError = error.code == 'camera_permission_denied'
          ? 'Camera permission is required only when you choose Camera.'
          : error.message ?? 'The image source could not be opened.';
      notifyListeners();
      return null;
    } catch (error) {
      _lastError = 'The image source could not be opened.';
      debugPrint('Custom icon image pick failed: $error');
      notifyListeners();
      return null;
    }
  }

  Future<bool> saveCustomBrandIcon(Uint8List pngBytes) async {
    if (_isSavingCustomBrandIcon) return false;
    _isSavingCustomBrandIcon = true;
    _lastError = null;
    notifyListeners();

    final id = 'custom_${DateTime.now().microsecondsSinceEpoch}';
    try {
      final target = await CustomAppIconProcessor.persistSquarePng(
        pngBytes,
        presetId: id,
      );
      final preset = CustomIconPreset(
        id: id,
        path: target.path,
        createdAt: DateTime.now().millisecondsSinceEpoch,
      );
      _customIconPresets.add(preset);
      _activeCustomPresetId = preset.id;
      _customBrandIconPath = preset.path;
      _brandIconSource = BrandIconSource.custom;

      final prefs = await SharedPreferences.getInstance();
      await _persistLibrary(prefs);
      await prefs.setString(_activeCustomPresetPreferenceKey, preset.id);
      await prefs.setString(_customBrandPathPreferenceKey, preset.path);
      await prefs.setString(
        _brandSourcePreferenceKey,
        BrandIconSource.custom.name,
      );

      await _activateCustomNativeRepresentation(preset.path);
      return true;
    } catch (error) {
      _customLauncherState = CustomLauncherState.failed;
      _lastError = 'The custom app icon could not be processed or saved.';
      debugPrint('Custom brand icon save failed: $error');
      return false;
    } finally {
      _isSavingCustomBrandIcon = false;
      notifyListeners();
    }
  }

  Future<bool> activateCustomPreset(String presetId) async {
    if (_isSavingCustomBrandIcon) return false;
    CustomIconPreset? preset;
    for (final candidate in _customIconPresets) {
      if (candidate.id == presetId && candidate.exists) {
        preset = candidate;
        break;
      }
    }
    if (preset == null) {
      _lastError = 'That custom icon is no longer available.';
      notifyListeners();
      return false;
    }

    _isSavingCustomBrandIcon = true;
    _lastError = null;
    notifyListeners();
    try {
      _activeCustomPresetId = preset.id;
      _customBrandIconPath = preset.path;
      _brandIconSource = BrandIconSource.custom;
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_activeCustomPresetPreferenceKey, preset.id);
      await prefs.setString(_customBrandPathPreferenceKey, preset.path);
      await prefs.setString(
        _brandSourcePreferenceKey,
        BrandIconSource.custom.name,
      );
      await _activateCustomNativeRepresentation(preset.path);
      return true;
    } catch (error) {
      _customLauncherState = CustomLauncherState.failed;
      _lastError = 'Android could not activate that custom launcher icon.';
      debugPrint('Custom preset activation failed: $error');
      return false;
    } finally {
      _isSavingCustomBrandIcon = false;
      notifyListeners();
    }
  }

  Future<bool> activateSavedCustomIcon() async {
    final preset = activeCustomPreset ??
        (_customIconPresets.where((preset) => preset.exists).isNotEmpty
            ? _customIconPresets.where((preset) => preset.exists).last
            : null);
    if (preset == null) return false;
    return activateCustomPreset(preset.id);
  }

  Future<void> _activateCustomNativeRepresentation(String path) async {
    if (kIsWeb || !Platform.isAndroid) {
      _customLauncherState = CustomLauncherState.unsupported;
      return;
    }

    final result = await _channel.invokeMethod<String>(
      'applyCustomHomeShortcut',
      <String, dynamic>{'imagePath': path, 'label': 'Chaty'},
    );
    _customLauncherState = _stateFromNative(result ?? 'failed');

    final prefs = await SharedPreferences.getInstance();
    _customHomeShortcutApplied =
        await _channel.invokeMethod<bool>('isCustomHomeShortcutPinned') ?? false;
    await prefs.setBool(
      _customHomeShortcutPreferenceKey,
      _customHomeShortcutApplied,
    );

    if (_customLauncherState == CustomLauncherState.unsupported) {
      _lastError =
          'This launcher cannot pin a runtime custom Home icon. The preset is still saved inside Chaty.';
    } else if (_customLauncherState == CustomLauncherState.failed) {
      _lastError =
          'The custom preset is saved, but Android could not activate the Home launcher entry.';
    }
  }

  Future<void> removeCustomPreset(String presetId) async {
    final index = _customIconPresets.indexWhere((preset) => preset.id == presetId);
    if (index < 0) return;
    final removed = _customIconPresets.removeAt(index);
    final wasActive = _activeCustomPresetId == presetId;

    try {
      final file = File(removed.path);
      if (await file.exists()) await file.delete();
    } catch (error) {
      debugPrint('Custom preset file cleanup failed: $error');
    }

    final prefs = await SharedPreferences.getInstance();
    await _persistLibrary(prefs);

    if (!wasActive) {
      notifyListeners();
      return;
    }

    final remaining = _customIconPresets.where((preset) => preset.exists).toList();
    if (remaining.isNotEmpty) {
      final next = remaining.last;
      _activeCustomPresetId = next.id;
      _customBrandIconPath = next.path;
      await prefs.setString(_activeCustomPresetPreferenceKey, next.id);
      await prefs.setString(_customBrandPathPreferenceKey, next.path);
      if (_brandIconSource == BrandIconSource.custom) {
        await activateCustomPreset(next.id);
      }
    } else {
      await removeCustomBrandIcon();
    }
  }

  Future<void> removeCustomBrandIcon() async {
    if (!kIsWeb && Platform.isAndroid) {
      try {
        await _channel.invokeMethod<void>('removeCustomHomeShortcut');
      } catch (error) {
        debugPrint('Custom Home Screen shortcut cleanup failed: $error');
      }
    }

    for (final preset in List<CustomIconPreset>.from(_customIconPresets)) {
      try {
        final file = File(preset.path);
        if (await file.exists()) await file.delete();
      } catch (error) {
        debugPrint('Custom preset cleanup failed: $error');
      }
    }

    _customIconPresets.clear();
    _activeCustomPresetId = null;
    _customBrandIconPath = null;
    _customHomeShortcutApplied = false;
    _customLauncherState = CustomLauncherState.inactive;
    _brandIconSource = BrandIconSource.bundled;

    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      _brandSourcePreferenceKey,
      BrandIconSource.bundled.name,
    );
    await prefs.remove(_customBrandPathPreferenceKey);
    await prefs.remove(_activeCustomPresetPreferenceKey);
    await prefs.remove(_customLibraryPreferenceKey);
    await prefs.setBool(_customHomeShortcutPreferenceKey, false);
    notifyListeners();
  }

  Future<void> resetLauncherIcon() async {
    await applyLauncherIcon(LauncherIconVariant.original);
  }

  void clearError() {
    if (_lastError == null) return;
    _lastError = null;
    notifyListeners();
  }

  static CustomLauncherState _stateFromNative(String value) {
    switch (value) {
      case 'active':
      case 'updated':
        return CustomLauncherState.active;
      case 'pending':
      case 'requested':
        return CustomLauncherState.pending;
      case 'unsupported':
        return CustomLauncherState.unsupported;
      case 'failed':
        return CustomLauncherState.failed;
      default:
        return CustomLauncherState.inactive;
    }
  }
}
