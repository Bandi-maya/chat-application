import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Capability-gated bridge to Android's system picture-in-picture mode.
///
/// The OS renders the existing Flutter call activity, so the current remote
/// WebRTC surface remains visible without creating a second media pipeline.
class NativeCallPipService {
  static const MethodChannel _channel = MethodChannel('chaty/call_pip');
  static final StreamController<bool> _modeChanges =
      StreamController<bool>.broadcast();
  static bool _handlerInstalled = false;

  NativeCallPipService() {
    if (_handlerInstalled) return;
    _handlerInstalled = true;
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'pipModeChanged') {
        _modeChanges.add(call.arguments == true);
      }
    });
  }

  Stream<bool> get modeChanges => _modeChanges.stream;

  Future<bool> get isSupported async {
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) return false;
    try {
      return await _channel.invokeMethod<bool>('isSupported') ?? false;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }

  Future<bool> get isActive async {
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) return false;
    return await _channel.invokeMethod<bool>('isInPictureInPictureMode') ??
        false;
  }

  Future<bool> enter({int width = 9, int height = 16}) async {
    if (!await isSupported) return false;
    try {
      return await _channel.invokeMethod<bool>('enter', <String, int>{
            'width': width,
            'height': height,
          }) ??
          false;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }
}
