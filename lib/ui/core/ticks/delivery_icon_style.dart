/// The complete catalog of discrete Delivery Tick Styles in Chaty, including
/// the dedicated styles offered by Conversation > Ticks Style.
enum DeliveryIconStyle {
  stock,
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

  // GB reference tick styles (GB picker order; 70 designs).
  allo,
  alien,
  altCrayonV2,
  altCrayon,
  aran,
  bar,
  bat,
  bbm,
  blend,
  ngAndroidBot,
  bpgCircle,
  gbCircles,
  samyadeepCircles,
  coalfield,
  colorSimple,
  ctoon,
  eclipsis,
  faceSwitch,
  feet,
  flatCircles,
  gifcon,
  googleV2,
  google,
  grw,
  sidzyHd,
  svHeart,
  hike,
  inbox,
  insect,
  gbIos,
  jalopy,
  jessy,
  joaquins,
  jokerTraffic,
  letter,
  like,
  loveSwitch,
  materialV2,
  materialSwitch,
  messenger,
  mickeyHands,
  minions,
  newWaca,
  neyHeart,
  nhsc,
  nazaNh,
  nlCircle,
  oldWaca,
  pacman,
  pixelated,
  pokemon,
  quinn,
  snapChat,
  sDot,
  seasons,
  smile,
  star,
  flatSwitch,
  timeBomb,
  jokerTraffic2,
  treble,
  twitterConcept,
  wbg,
  x,
  pointer,
  ticTicCircle2,
  iceCream,
  pony,
  kitty,
  flower,
}

extension DeliveryIconStyleExtension on DeliveryIconStyle {
  String get displayName {
    switch (this) {
      case DeliveryIconStyle.stock:
        return 'Stock';
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
      case DeliveryIconStyle.allo:
        return 'Allo';
      case DeliveryIconStyle.alien:
        return 'Alien';
      case DeliveryIconStyle.altCrayonV2:
        return 'Alt CrayonV2';
      case DeliveryIconStyle.altCrayon:
        return 'Alt Crayon';
      case DeliveryIconStyle.aran:
        return 'Aran';
      case DeliveryIconStyle.bar:
        return 'Bar';
      case DeliveryIconStyle.bat:
        return 'Bat';
      case DeliveryIconStyle.bbm:
        return 'BBM';
      case DeliveryIconStyle.blend:
        return 'Blend';
      case DeliveryIconStyle.ngAndroidBot:
        return 'NG Android Bot';
      case DeliveryIconStyle.bpgCircle:
        return 'BPG Circle';
      case DeliveryIconStyle.gbCircles:
        return 'Circles';
      case DeliveryIconStyle.samyadeepCircles:
        return 'Samyadeep Circles';
      case DeliveryIconStyle.coalfield:
        return 'Coalfield';
      case DeliveryIconStyle.colorSimple:
        return 'Color Simple';
      case DeliveryIconStyle.ctoon:
        return 'Ctoon';
      case DeliveryIconStyle.eclipsis:
        return 'Eclipsis';
      case DeliveryIconStyle.faceSwitch:
        return 'Face Switch';
      case DeliveryIconStyle.feet:
        return 'Feet';
      case DeliveryIconStyle.flatCircles:
        return 'Flat Circles';
      case DeliveryIconStyle.gifcon:
        return 'Gifcon';
      case DeliveryIconStyle.googleV2:
        return 'Google V2';
      case DeliveryIconStyle.google:
        return 'Google';
      case DeliveryIconStyle.grw:
        return 'GRW';
      case DeliveryIconStyle.sidzyHd:
        return 'Sidzy HD';
      case DeliveryIconStyle.svHeart:
        return 'SV Heart';
      case DeliveryIconStyle.hike:
        return 'Hike';
      case DeliveryIconStyle.inbox:
        return 'Inbox';
      case DeliveryIconStyle.insect:
        return 'Insect';
      case DeliveryIconStyle.gbIos:
        return 'iOS';
      case DeliveryIconStyle.jalopy:
        return 'Jalopy';
      case DeliveryIconStyle.jessy:
        return 'Jessy';
      case DeliveryIconStyle.joaquins:
        return 'Joaquins';
      case DeliveryIconStyle.jokerTraffic:
        return 'Joker Traffic';
      case DeliveryIconStyle.letter:
        return 'Letter';
      case DeliveryIconStyle.like:
        return 'Like';
      case DeliveryIconStyle.loveSwitch:
        return 'Love Switch';
      case DeliveryIconStyle.materialV2:
        return 'Material V2';
      case DeliveryIconStyle.materialSwitch:
        return 'Material Switch';
      case DeliveryIconStyle.messenger:
        return 'Messenger';
      case DeliveryIconStyle.mickeyHands:
        return 'Mickey Hands';
      case DeliveryIconStyle.minions:
        return 'Minions';
      case DeliveryIconStyle.newWaca:
        return 'New Waca';
      case DeliveryIconStyle.neyHeart:
        return 'NEY Heart';
      case DeliveryIconStyle.nhsc:
        return 'NHSC';
      case DeliveryIconStyle.nazaNh:
        return 'Naza NH';
      case DeliveryIconStyle.nlCircle:
        return 'NL Circle';
      case DeliveryIconStyle.oldWaca:
        return 'Old Waca';
      case DeliveryIconStyle.pacman:
        return 'Pacman';
      case DeliveryIconStyle.pixelated:
        return 'Pixelated';
      case DeliveryIconStyle.pokemon:
        return 'Pokemon';
      case DeliveryIconStyle.quinn:
        return 'Quinn';
      case DeliveryIconStyle.snapChat:
        return 'SnapChat';
      case DeliveryIconStyle.sDot:
        return 'SDot';
      case DeliveryIconStyle.seasons:
        return 'Seasons';
      case DeliveryIconStyle.smile:
        return 'Smile';
      case DeliveryIconStyle.star:
        return 'Star';
      case DeliveryIconStyle.flatSwitch:
        return 'Flat Switch';
      case DeliveryIconStyle.timeBomb:
        return 'Time Bomb';
      case DeliveryIconStyle.jokerTraffic2:
        return 'Joker Traffic 2';
      case DeliveryIconStyle.treble:
        return 'Treble';
      case DeliveryIconStyle.twitterConcept:
        return 'Twitter Concept';
      case DeliveryIconStyle.wbg:
        return 'WBG';
      case DeliveryIconStyle.x:
        return 'X';
      case DeliveryIconStyle.pointer:
        return 'Pointer';
      case DeliveryIconStyle.ticTicCircle2:
        return 'TIC TIC circle 2';
      case DeliveryIconStyle.iceCream:
        return 'Ice Cream';
      case DeliveryIconStyle.pony:
        return 'Pony';
      case DeliveryIconStyle.kitty:
        return 'Kitty';
      case DeliveryIconStyle.flower:
        return 'Flower';
    }
  }

  /// Tick styles added for GB catalog parity whose glyphs are original
  /// Chaty vector interpretations of the GB bitmap art (GB ships PNG
  /// drawables; we do not copy assets). The picker labels these as
  /// "vector redraw" so users can tell them from the 17 styles Chaty
  /// already rendered before parity work. Licensed-character designs
  /// (Mickey Hands, Minions, Pokemon, SnapChat, Kitty, Pony, Bat) are
  /// generic original stand-ins, never the licensed art.
  static const Set<DeliveryIconStyle> gbVectorInterpretations = {
    DeliveryIconStyle.allo,
    DeliveryIconStyle.alien,
    DeliveryIconStyle.altCrayonV2,
    DeliveryIconStyle.altCrayon,
    DeliveryIconStyle.aran,
    DeliveryIconStyle.bar,
    DeliveryIconStyle.bat,
    DeliveryIconStyle.bbm,
    DeliveryIconStyle.blend,
    DeliveryIconStyle.ngAndroidBot,
    DeliveryIconStyle.bpgCircle,
    DeliveryIconStyle.gbCircles,
    DeliveryIconStyle.samyadeepCircles,
    DeliveryIconStyle.coalfield,
    DeliveryIconStyle.colorSimple,
    DeliveryIconStyle.ctoon,
    DeliveryIconStyle.eclipsis,
    DeliveryIconStyle.faceSwitch,
    DeliveryIconStyle.feet,
    DeliveryIconStyle.flatCircles,
    DeliveryIconStyle.gifcon,
    DeliveryIconStyle.googleV2,
    DeliveryIconStyle.google,
    DeliveryIconStyle.grw,
    DeliveryIconStyle.sidzyHd,
    DeliveryIconStyle.svHeart,
    DeliveryIconStyle.hike,
    DeliveryIconStyle.inbox,
    DeliveryIconStyle.insect,
    DeliveryIconStyle.gbIos,
    DeliveryIconStyle.jalopy,
    DeliveryIconStyle.jessy,
    DeliveryIconStyle.joaquins,
    DeliveryIconStyle.jokerTraffic,
    DeliveryIconStyle.letter,
    DeliveryIconStyle.like,
    DeliveryIconStyle.loveSwitch,
    DeliveryIconStyle.materialV2,
    DeliveryIconStyle.materialSwitch,
    DeliveryIconStyle.messenger,
    DeliveryIconStyle.mickeyHands,
    DeliveryIconStyle.minions,
    DeliveryIconStyle.newWaca,
    DeliveryIconStyle.neyHeart,
    DeliveryIconStyle.nhsc,
    DeliveryIconStyle.nazaNh,
    DeliveryIconStyle.nlCircle,
    DeliveryIconStyle.oldWaca,
    DeliveryIconStyle.pacman,
    DeliveryIconStyle.pixelated,
    DeliveryIconStyle.pokemon,
    DeliveryIconStyle.quinn,
    DeliveryIconStyle.snapChat,
    DeliveryIconStyle.sDot,
    DeliveryIconStyle.seasons,
    DeliveryIconStyle.smile,
    DeliveryIconStyle.star,
    DeliveryIconStyle.flatSwitch,
    DeliveryIconStyle.timeBomb,
    DeliveryIconStyle.jokerTraffic2,
    DeliveryIconStyle.treble,
    DeliveryIconStyle.twitterConcept,
    DeliveryIconStyle.wbg,
    DeliveryIconStyle.x,
    DeliveryIconStyle.pointer,
    DeliveryIconStyle.ticTicCircle2,
    DeliveryIconStyle.iceCream,
    DeliveryIconStyle.pony,
    DeliveryIconStyle.kitty,
    DeliveryIconStyle.flower,
  };

  /// Safe deserialization with legacy aliases migration
  static DeliveryIconStyle fromString(String? key) {
    if (key == null || key.isEmpty) return DeliveryIconStyle.stock;
    final normalized = key.toLowerCase().replaceAll(RegExp(r'[^a-z0-9]'), '');

    for (final style in DeliveryIconStyle.values) {
      if (style.name.toLowerCase() == normalized) {
        return style;
      }
      // gbIos/gbCircles reuse GB display names that legacy Chaty aliases
      // already owned ('iOS' -> rcIos11, 'Circles' -> cirCheck). Their
      // display names intentionally do NOT resolve here so older saved
      // values and imported configs keep their original rendering; the new
      // GB styles persist by enum name instead (see the picker).
      if (style == DeliveryIconStyle.gbIos ||
          style == DeliveryIconStyle.gbCircles) {
        continue;
      }
      if (style.displayName.toLowerCase().replaceAll(
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
