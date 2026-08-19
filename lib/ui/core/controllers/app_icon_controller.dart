import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

enum LauncherIconVariant {
  original,
  minimal,
  bubble,
  midnight,
  ocean,
  violet,
}

enum BrandIconSource { bundled, custom }

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

  LauncherIconVariant _launcherIcon = LauncherIconVariant.original;
  BrandIconSource _brandIconSource = BrandIconSource.bundled;
  String? _customBrandIconPath;
  bool _initialized = false;
  bool _isApplyingLauncherIcon = false;
  bool _isSavingCustomBrandIcon = false;
  String? _lastError;

  LauncherIconVariant get launcherIcon => _launcherIcon;
  BrandIconSource get brandIconSource => _brandIconSource;
  String? get customBrandIconPath => _customBrandIconPath;
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

    if (_brandIconSource == BrandIconSource.custom) {
      final path = _customBrandIconPath;
      if (path == null || path.isEmpty || !File(path).existsSync()) {
        _brandIconSource = BrandIconSource.bundled;
        _customBrandIconPath = null;
        await prefs.setString(_brandSourcePreferenceKey, BrandIconSource.bundled.name);
        await prefs.remove(_customBrandPathPreferenceKey);
      }
    }

    if (!kIsWeb && Platform.isAndroid) {
      try {
        final activeAlias = await _channel.invokeMethod<String>('getCurrentLauncherIcon');
        if (activeAlias != null && activeAlias.isNotEmpty) {
          final nativeVariant = LauncherIconVariantMetadata.fromId(activeAlias);
          if (nativeVariant != _launcherIcon) {
            _launcherIcon = nativeVariant;
            await prefs.setString(_launcherPreferenceKey, nativeVariant.id);
          }
        } else {
          await _channel.invokeMethod<void>('setLauncherIcon', <String, dynamic>{
            'alias': _launcherIcon.androidAlias,
          });
        }
      } catch (error) {
        _lastError = 'Unable to verify the launcher icon on this device.';
        debugPrint('Launcher icon initialization failed: $error');
      }
    }

    _initialized = true;
    notifyListeners();
  }

  Future<bool> applyLauncherIcon(LauncherIconVariant variant) async {
    if (_isApplyingLauncherIcon) return false;
    _isApplyingLauncherIcon = true;
    _lastError = null;
    notifyListeners();

    final previous = _launcherIcon;
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
      await prefs.setString(_launcherPreferenceKey, variant.id);
      await prefs.setString(_brandSourcePreferenceKey, BrandIconSource.bundled.name);
      return true;
    } catch (error) {
      _launcherIcon = previous;
      _lastError = 'Could not apply that launcher icon. Your previous icon is still active.';
      debugPrint('Launcher icon change failed: $error');
      return false;
    } finally {
      _isApplyingLauncherIcon = false;
      notifyListeners();
    }
  }

  Future<bool> saveCustomBrandIcon(Uint8List pngBytes) async {
    if (_isSavingCustomBrandIcon) return false;
    _isSavingCustomBrandIcon = true;
    _lastError = null;
    notifyListeners();

    try {
      final root = await getApplicationSupportDirectory();
      final directory = Directory('${root.path}${Platform.pathSeparator}branding');
      if (!await directory.exists()) await directory.create(recursive: true);

      final target = File('${directory.path}${Platform.pathSeparator}custom_brand_icon.png');
      final temporary = File('${target.path}.tmp');
      await temporary.writeAsBytes(pngBytes, flush: true);
      if (await target.exists()) await target.delete();
      await temporary.rename(target.path);

      _customBrandIconPath = target.path;
      _brandIconSource = BrandIconSource.custom;
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_brandSourcePreferenceKey, BrandIconSource.custom.name);
      await prefs.setString(_customBrandPathPreferenceKey, target.path);
      return true;
    } catch (error) {
      _lastError = 'The custom brand icon could not be saved.';
      debugPrint('Custom brand icon save failed: $error');
      return false;
    } finally {
      _isSavingCustomBrandIcon = false;
      notifyListeners();
    }
  }

  Future<void> removeCustomBrandIcon() async {
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
    _brandIconSource = BrandIconSource.bundled;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_brandSourcePreferenceKey, BrandIconSource.bundled.name);
    await prefs.remove(_customBrandPathPreferenceKey);
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
}
