import Flutter
import UIKit
import Vision
import ImageIO

@main
@objc class AppDelegate: FlutterAppDelegate, FlutterImplicitEngineDelegate {
  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }

  func didInitializeImplicitFlutterEngine(_ engineBridge: FlutterImplicitEngineBridge) {
    GeneratedPluginRegistrant.register(with: engineBridge.pluginRegistry)

    let registrar = engineBridge.pluginRegistry.registrar(forPlugin: "ChatyFaceTracking")
    let channel = FlutterMethodChannel(
      name: "chaty/camera_face_tracking",
      binaryMessenger: registrar.messenger()
    )
    channel.setMethodCallHandler { call, result in
      guard call.method == "detectFaces" else {
        result(FlutterMethodNotImplemented)
        return
      }
      guard
        let arguments = call.arguments as? [String: Any],
        let width = arguments["width"] as? Int,
        let height = arguments["height"] as? Int,
        let typedBytes = arguments["rgbBytes"] as? FlutterStandardTypedData,
        width > 0, height > 0, width <= 160, height <= 160,
        typedBytes.data.count >= width * height * 3
      else {
        result(FlutterError(
          code: "invalid_frame",
          message: "A valid downsampled RGB frame is required.",
          details: nil
        ))
        return
      }

      // The thumbnail is processed entirely on-device. Keep Vision off the
      // Flutter/UI thread and return only compact landmark coordinates.
      DispatchQueue.global(qos: .userInitiated).async {
        let detected = Self.detectFaces(
          rgbData: typedBytes.data,
          width: width,
          height: height
        )
        DispatchQueue.main.async { result(detected) }
      }
    }
  }

  private static func detectFaces(
    rgbData: Data,
    width: Int,
    height: Int
  ) -> [[String: Any]] {
    guard
      let provider = CGDataProvider(data: rgbData as CFData),
      let colorSpace = CGColorSpace(name: CGColorSpace.sRGB),
      let image = CGImage(
        width: width,
        height: height,
        bitsPerComponent: 8,
        bitsPerPixel: 24,
        bytesPerRow: width * 3,
        space: colorSpace,
        bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.none.rawValue),
        provider: provider,
        decode: nil,
        shouldInterpolate: false,
        intent: .defaultIntent
      )
    else {
      return []
    }

    let request = VNDetectFaceLandmarksRequest()
    request.usesCPUOnly = false
    do {
      try VNImageRequestHandler(cgImage: image, options: [:]).perform([request])
    } catch {
      return []
    }

    let observations = (request.results ?? []).sorted {
      $0.boundingBox.width > $1.boundingBox.width
    }
    return observations.enumerated().map { index, face in
      let box = face.boundingBox
      let left = box.minX * CGFloat(width)
      let right = box.maxX * CGFloat(width)
      let top = (1 - box.maxY) * CGFloat(height)
      let bottom = (1 - box.minY) * CGFloat(height)

      func center(of region: VNFaceLandmarkRegion2D?, fallback: CGPoint) -> CGPoint {
        guard let region, !region.normalizedPoints.isEmpty else { return fallback }
        let points = region.normalizedPoints
        let normalizedX = points.reduce(CGFloat.zero) { $0 + $1.x } / CGFloat(points.count)
        let normalizedY = points.reduce(CGFloat.zero) { $0 + $1.y } / CGFloat(points.count)
        return CGPoint(
          x: (box.minX + normalizedX * box.width) * CGFloat(width),
          y: (1 - (box.minY + normalizedY * box.height)) * CGFloat(height)
        )
      }

      let eyeY = top + (bottom - top) * 0.38
      let leftFallback = CGPoint(x: left + (right - left) * 0.32, y: eyeY)
      let rightFallback = CGPoint(x: left + (right - left) * 0.68, y: eyeY)
      let noseFallback = CGPoint(x: left + (right - left) * 0.5, y: top + (bottom - top) * 0.56)
      let mouthFallback = CGPoint(x: left + (right - left) * 0.5, y: top + (bottom - top) * 0.74)
      let landmarks = face.landmarks

      let leftEye = center(of: landmarks?.leftEye, fallback: leftFallback)
      let rightEye = center(of: landmarks?.rightEye, fallback: rightFallback)
      let nose = center(of: landmarks?.nose, fallback: noseFallback)
      let mouth = center(of: landmarks?.outerLips, fallback: mouthFallback)

      return [
        "trackingId": index + 1,
        "headLeft": Double(left),
        "headTop": Double(top),
        "headRight": Double(right),
        "headBottom": Double(bottom),
        "leftEyeX": Double(leftEye.x),
        "leftEyeY": Double(leftEye.y),
        "rightEyeX": Double(rightEye.x),
        "rightEyeY": Double(rightEye.y),
        "noseX": Double(nose.x),
        "noseY": Double(nose.y),
        "mouthX": Double(mouth.x),
        "mouthY": Double(mouth.y),
        "rollRadians": face.roll?.doubleValue ?? 0,
        "yawRadians": face.yaw?.doubleValue ?? 0,
        "pitchRadians": 0.0,
        "confidence": Double(face.confidence)
      ]
    }
  }
}
