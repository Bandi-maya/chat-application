/// The complete catalog of all 16 discrete Delivery Tick Styles in Chaty.
enum DeliveryIconStyle {
  sticker,
  rcIos11,
  bbmV2,
  bwTicks,
  cirCheck,
  circlePrint,
  gabCircle,
  gabFace,
  gabIflo,
  greenTick,
  ios2,
  letterCircle,
  rcAlo,
  rcTick,
  triangle,
  vantCircle,
}

extension DeliveryIconStyleExtension on DeliveryIconStyle {
  String get displayName {
    switch (this) {
      case DeliveryIconStyle.sticker:
        return 'Sticker';
      case DeliveryIconStyle.rcIos11:
        return 'RC iOS 11';
      case DeliveryIconStyle.bbmV2:
        return 'BBM V2';
      case DeliveryIconStyle.bwTicks:
        return 'B.W Ticks';
      case DeliveryIconStyle.cirCheck:
        return 'CirCheck';
      case DeliveryIconStyle.circlePrint:
        return 'Circle Print';
      case DeliveryIconStyle.gabCircle:
        return 'Gab Circle';
      case DeliveryIconStyle.gabFace:
        return 'Gab Face';
      case DeliveryIconStyle.gabIflo:
        return 'Gab iflo';
      case DeliveryIconStyle.greenTick:
        return 'Green Tick';
      case DeliveryIconStyle.ios2:
        return 'iOS 2';
      case DeliveryIconStyle.letterCircle:
        return 'Letter Circle';
      case DeliveryIconStyle.rcAlo:
        return 'RC Alo';
      case DeliveryIconStyle.rcTick:
        return 'RC Tick';
      case DeliveryIconStyle.triangle:
        return 'Triangle';
      case DeliveryIconStyle.vantCircle:
        return 'VantCircle';
    }
  }

  /// Safe deserialization with legacy aliases migration
  static DeliveryIconStyle fromString(String? key) {
    if (key == null || key.isEmpty) return DeliveryIconStyle.rcIos11;
    final normalized = key.toLowerCase().replaceAll(RegExp(r'[^a-z0-9]'), '');

    for (final style in DeliveryIconStyle.values) {
      if (style.name.toLowerCase() == normalized ||
          style.displayName.toLowerCase().replaceAll(RegExp(r'[^a-z0-9]'), '') ==
              normalized) {
        return style;
      }
    }

    // Legacy migrations
    switch (normalized) {
      case 'default':
      case 'iosstyle':
      case 'ios':
        return DeliveryIconStyle.rcIos11;
      case 'doublecheck':
        return DeliveryIconStyle.greenTick;
      case 'minimal':
        return DeliveryIconStyle.bwTicks;
      case 'neon':
        return DeliveryIconStyle.vantCircle;
      default:
        return DeliveryIconStyle.rcIos11;
    }
  }
}
