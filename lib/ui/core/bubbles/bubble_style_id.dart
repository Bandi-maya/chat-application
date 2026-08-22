/// The complete catalog of all 48 discrete Bubble Styles in Chaty.
///
/// Designed with original vector geometry and mathematical contours without
/// copying any third-party copyrighted assets.
enum BubbleStyleId {
  // Set A (1-16)
  crMessenger,
  rcBurbuja2,
  rcBurbuja5,
  threeDV2,
  kitty,
  amor,
  rcSamsBord,
  rcIos11,
  rcGoogleAssistan,
  gabiOutline,
  gabiSqua,
  gabiDot,
  rcLine,
  gabiDot2,
  roundle,
  gabyRon,

  // Set B (17-32)
  stock,
  facebookMessenger,
  oldHangouts,
  threeD,
  rounded,
  materialized,
  md,
  samyadeepCrayonAlt,
  newHangouts4,
  waPaperRedesigned,
  transparent,
  twitter,
  whatsappLb,
  bbm,
  ios,
  hike,

  // Set C (33-48)
  bubbleDrop,
  samyadeepDual,
  plusMessengerEd,
  waChatOn,
  telegram,
  inBubble,
  bryed,
  fold,
  foldV2,
  ilkhang,
  line,
  popzup,
  doodleHang,
  aranbor,
  win,
  mood,
}

extension BubbleStyleIdExtension on BubbleStyleId {
  String get displayName {
    switch (this) {
      // Set A
      case BubbleStyleId.crMessenger:
        return 'Cr Messenger';
      case BubbleStyleId.rcBurbuja2:
        return 'RC BURBUJA 2';
      case BubbleStyleId.rcBurbuja5:
        return 'RC BURBUJA 5';
      case BubbleStyleId.threeDV2:
        return '3D V2';
      case BubbleStyleId.kitty:
        return 'Kitty';
      case BubbleStyleId.amor:
        return 'Amor';
      case BubbleStyleId.rcSamsBord:
        return 'RC SAMS BORD';
      case BubbleStyleId.rcIos11:
        return 'RC IOS 11';
      case BubbleStyleId.rcGoogleAssistan:
        return 'RC GOOGLE ASSISTAN';
      case BubbleStyleId.gabiOutline:
        return 'Gabi Outline';
      case BubbleStyleId.gabiSqua:
        return 'Gabi Squa';
      case BubbleStyleId.gabiDot:
        return 'Gabi Dot';
      case BubbleStyleId.rcLine:
        return 'Rc line';
      case BubbleStyleId.gabiDot2:
        return 'Gabi Dot 2';
      case BubbleStyleId.roundle:
        return 'Roundle';
      case BubbleStyleId.gabyRon:
        return 'Gaby Ron';

      // Set B
      case BubbleStyleId.stock:
        return 'Stock';
      case BubbleStyleId.facebookMessenger:
        return 'Facebook Messenger';
      case BubbleStyleId.oldHangouts:
        return 'Old Hangouts';
      case BubbleStyleId.threeD:
        return '3D';
      case BubbleStyleId.rounded:
        return 'Rounded';
      case BubbleStyleId.materialized:
        return 'Materialized';
      case BubbleStyleId.md:
        return 'MD';
      case BubbleStyleId.samyadeepCrayonAlt:
        return 'Samyadeep Crayon Alt';
      case BubbleStyleId.newHangouts4:
        return 'New Hangouts 4.0';
      case BubbleStyleId.waPaperRedesigned:
        return 'WA+ Paper Redesigned';
      case BubbleStyleId.transparent:
        return 'Transparent';
      case BubbleStyleId.twitter:
        return 'Twitter';
      case BubbleStyleId.whatsappLb:
        return 'Whatsapp LB';
      case BubbleStyleId.bbm:
        return 'BBM';
      case BubbleStyleId.ios:
        return 'iOS';
      case BubbleStyleId.hike:
        return 'Hike';

      // Set C
      case BubbleStyleId.bubbleDrop:
        return 'Bubble Drop';
      case BubbleStyleId.samyadeepDual:
        return 'Samyadeep Dual';
      case BubbleStyleId.plusMessengerEd:
        return 'Plus Messenger ED';
      case BubbleStyleId.waChatOn:
        return 'WA+ Chat On';
      case BubbleStyleId.telegram:
        return 'Telegram';
      case BubbleStyleId.inBubble:
        return 'In';
      case BubbleStyleId.bryed:
        return 'Bryed';
      case BubbleStyleId.fold:
        return 'Fold';
      case BubbleStyleId.foldV2:
        return 'Fold v2';
      case BubbleStyleId.ilkhang:
        return 'Ilkhang';
      case BubbleStyleId.line:
        return 'Line';
      case BubbleStyleId.popzup:
        return 'Popzup';
      case BubbleStyleId.doodleHang:
        return 'DoodleHang';
      case BubbleStyleId.aranbor:
        return 'Aranbor';
      case BubbleStyleId.win:
        return 'Win';
      case BubbleStyleId.mood:
        return 'Mood';
    }
  }

  /// Safe deserialization with legacy aliases migration
  static BubbleStyleId fromString(String? key) {
    if (key == null || key.isEmpty) return BubbleStyleId.stock;
    final normalized = key.toLowerCase().replaceAll(RegExp(r'[^a-z0-9]'), '');

    for (final id in BubbleStyleId.values) {
      if (id.name.toLowerCase() == normalized ||
          id.displayName.toLowerCase().replaceAll(RegExp(r'[^a-z0-9]'), '') ==
              normalized) {
        return id;
      }
    }

    // Legacy migrations
    switch (normalized) {
      case 'classic':
      case 'default':
      case 'tail':
        return BubbleStyleId.stock;
      case 'tailless':
      case 'compact':
      case 'pill':
        return BubbleStyleId.facebookMessenger;
      case 'squircle':
      case 'softsquare':
        return BubbleStyleId.gabiSqua;
      case 'card':
      case 'minimal':
        return BubbleStyleId.materialized;
      case 'sharptail':
        return BubbleStyleId.oldHangouts;
      default:
        return BubbleStyleId.stock;
    }
  }
}
