/// The complete catalog of discrete Delivery Tick Styles in Chaty, including
/// the dedicated styles offered by Conversation > Ticks Style.
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
  wings,
  trafficLights,
  stars,
  hearts,
  batman,
  doubleSword,
  glowingDots,
  material3Rounded,
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
      case DeliveryIconStyle.wings:
        return 'Wings';
      case DeliveryIconStyle.trafficLights:
        return 'Traffic Lights';
      case DeliveryIconStyle.stars:
        return 'Stars';
      case DeliveryIconStyle.hearts:
        return 'Hearts';
      case DeliveryIconStyle.batman:
        return 'Batman';
      case DeliveryIconStyle.doubleSword:
        return 'Double Sword';
      case DeliveryIconStyle.glowingDots:
        return 'Glowing Dots';
      case DeliveryIconStyle.material3Rounded:
        return 'Material 3 Rounded';
    }
  }

  /// Safe deserialization with legacy aliases migration
  static DeliveryIconStyle fromString(String? key) {
    if (key == null || key.isEmpty) return DeliveryIconStyle.rcIos11;
    final normalized = key.toLowerCase().replaceAll(RegExp(r'[^a-z0-9]'), '');

    for (final style in DeliveryIconStyle.values) {
      if (style.name.toLowerCase() == normalized ||
          style.displayName.toLowerCase().replaceAll(
                RegExp(r'[^a-z0-9]'),
                '',
              ) ==
              normalized) {
        return style;
      }
    }

    // Legacy aliases plus all labels offered by Conversation > Ticks Style.
    // These aliases intentionally resolve to distinct painters where the user
    // is offered a visibly distinct style in the picker.
    switch (normalized) {
      case 'default':
      case 'iosstyle':
      case 'ios':
        return DeliveryIconStyle.rcIos11;
      case 'iosticks':
        return DeliveryIconStyle.ios2;
      case 'wings':
        return DeliveryIconStyle.wings;
      case 'trafficlights':
        return DeliveryIconStyle.trafficLights;
      case 'circles':
        return DeliveryIconStyle.cirCheck;
      case 'stars':
        return DeliveryIconStyle.stars;
      case 'hearts':
        return DeliveryIconStyle.hearts;
      case 'batman':
        return DeliveryIconStyle.batman;
      case 'doublesword':
        return DeliveryIconStyle.doubleSword;
      case 'glowingdots':
        return DeliveryIconStyle.glowingDots;
      case 'material3rounded':
        return DeliveryIconStyle.material3Rounded;
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
