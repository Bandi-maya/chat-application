import 'dart:io';

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

  LauncherIconVariant _launcherIcon = LauncherIconVariant.original;
  BrandIconSource _brandIconSource = BrandIconSource.bundled;
  CustomLauncherState _customLauncherState = CustomLauncherState.inactive;
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
  bool get customHomeShortcutApplied => _customHomeShortcutApplied;
  bool get customLauncherModeActive => _customLauncherState == CustomLauncherState.active;
  bool get hasSavedCustomIcon {
    final path = _customBrandIconPath;
    return path != null && path.isNotEmpty && File(path).existsSync();
  }

  bool get initialized => _initialized;
  bool get isApplyingLauncherIcon => _isApplyingLauncherIcon;
  bool get isSavingCustomBrandIcon => _isSavingCustomBrandIcon;
  bool get isBusy => _isApplyingLauncherIcon || _isSavingCustomBrandIcon;
  String? get lastError => _lastError;

  Future<void> initialize() async {
    if (_initialized) return;
    final prefs = await SharedPreferences.getInstance();
    _launcherIcon = LauncherIconVariantMetadata.fromId(prefs.getString(_launcherPreferenceKey));
    _brandIconSource = prefs.getString(_brandSourcePreferenceKey) == BrandIconSource.custom.name
        ? BrandIconSource.custom
        : BrandIconSource.bundled;
    _customBrandIconPath = prefs.getString(_customBrandPathPreferenceKey);
    _customHomeShortcutApplied = prefs.getBool(_customHomeShortcutPreferenceKey) ?? false;

    if (!hasSavedCustomIcon) {
      _customBrandIconPath = null;
      if (_brandIconSource == BrandIconSource.custom) {
        _brandIconSource = BrandIconSource.bundled;
      }
      await prefs.remove(_customBrandPathPreferenceKey);
      await prefs.setString(_brandSourcePreferenceKey, _brandIconSource.name);
      await prefs.setBool(_customHomeShortcutPreferenceKey, false);
    }

    await refreshNativeLauncherState(notify: false);
    _initialized = true;
    notifyListeners();
  }

  Future<void> refreshNativeLauncherState({bool notify = true}) async {
    if (_isRefreshingNativeState || kIsWeb || !Platform.isAndroid) return;
    _isRefreshingNativeState = true;
    try {
      final prefs = await SharedPreferences.getInstance();
      final activeAlias = await _channel.invokeMethod<String>('getCurrentLauncherIcon');
      if (activeAlias != null && activeAlias.isNotEmpty) {
        final nativeVariant = LauncherIconVariantMetadata.fromId(activeAlias);
        _launcherIcon = nativeVariant;
        await prefs.setString(_launcherPreferenceKey, nativeVariant.id);
      }

      final nativeState = await _channel.invokeMethod<String>('getCustomLauncherState') ?? 'inactive';
      _customLauncherState = _stateFromNative(nativeState);
      _customHomeShortcutApplied =
          await _channel.invokeMethod<bool>('isCustomHomeShortcutPinned') ?? false;
      await prefs.setBool(_customHomeShortcutPreferenceKey, _customHomeShortcutApplied);

      if (_brandIconSource == BrandIconSource.custom && !hasSavedCustomIcon) {
        _brandIconSource = BrandIconSource.bundled;
        _customLauncherState = CustomLauncherState.inactive;
        await prefs.setString(_brandSourcePreferenceKey, BrandIconSource.bundled.name);
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
        final applied = await _channel.invokeMethod<String>('setLauncherIcon', <String, dynamic>{
          'alias': variant.androidAlias,
        });
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
      await prefs.setString(_brandSourcePreferenceKey, BrandIconSource.bundled.name);
      await prefs.setBool(_customHomeShortcutPreferenceKey, false);
      return true;
    } catch (error) {
      _launcherIcon = previous;
      _brandIconSource = previousSource;
      _customLauncherState = previousCustomState;
      _lastError = 'Could not apply that launcher icon. Your previous icon is still active.';
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
      return await _channel.invokeMethod<String>('pickCustomIconImage', <String, dynamic>{
        'source': source.name,
      });
    } on PlatformException catch (error) {
      if (error.code == 'camera_permission_denied') {
        _lastError = 'Camera permission is required only when you choose Camera.';
      } else {
        _lastError = error.message ?? 'The image source could not be opened.';
      }
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

    try {
      final target = await CustomAppIconProcessor.persistSquarePng(pngBytes);
      _customBrandIconPath = target.path;
      _brandIconSource = BrandIconSource.custom;

      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_customBrandPathPreferenceKey, target.path);
      await prefs.setString(_brandSourcePreferenceKey, BrandIconSource.custom.name);

      await _activateCustomNativeRepresentation(target.path);
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

  Future<bool> activateSavedCustomIcon() async {
    if (!hasSavedCustomIcon || _isSavingCustomBrandIcon) return false;
    _isSavingCustomBrandIcon = true;
    _lastError = null;
    _brandIconSource = BrandIconSource.custom;
    notifyListeners();
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_brandSourcePreferenceKey, BrandIconSource.custom.name);
      await _activateCustomNativeRepresentation(_customBrandIconPath!);
      return true;
    } catch (error) {
      _customLauncherState = CustomLauncherState.failed;
      _lastError = 'Android could not activate the saved custom launcher icon.';
      debugPrint('Saved custom icon activation failed: $error');
      return false;
    } finally {
      _isSavingCustomBrandIcon = false;
      notifyListeners();
    }
  }

  Future<void> _activateCustomNativeRepresentation(String path) async {
    if (kIsWeb || !Platform.isAndroid) {
      _customLauncherState = CustomLauncherState.unsupported;
      return;
    }

    final result = await _channel.invokeMethod<String>('applyCustomHomeShortcut', <String, dynamic>{
      'imagePath': path,
      'label': 'Chaty',
    });
    _customLauncherState = _stateFromNative(result ?? 'failed');

    final prefs = await SharedPreferences.getInstance();
    _customHomeShortcutApplied =
        await _channel.invokeMethod<bool>('isCustomHomeShortcutPinned') ?? false;
    await prefs.setBool(_customHomeShortcutPreferenceKey, _customHomeShortcutApplied);

    if (_customLauncherState == CustomLauncherState.unsupported) {
      _lastError =
          'This launcher cannot pin a runtime custom Home icon. Chaty will still use the selected image inside the app.';
    } else if (_customLauncherState == CustomLauncherState.failed) {
      _lastError =
          'The custom image is saved, but Android could not activate the temporary Home launcher entry.';
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

    final path = _customBrandIconPath;
    if (path != null && path.isNotEmpty) {
      try {
        final file = File(path);
        if (await file.exists()) await file.delete();
      } catch (error) {
        debugPrint('Custom brand icon cleanup failed: $error');
      }
    }

    _customBrandIconPath = null;
    _customHomeShortcutApplied = false;
    _customLauncherState = CustomLauncherState.inactive;
    _brandIconSource = BrandIconSource.bundled;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_brandSourcePreferenceKey, BrandIconSource.bundled.name);
    await prefs.remove(_customBrandPathPreferenceKey);
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
