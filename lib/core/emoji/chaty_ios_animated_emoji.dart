import 'package:flutter/material.dart';

/// iOS-style animated emoji pack imported from the user's own Drive folder
/// ("Animated Emoji", 50 .mov smileys rendered by N.S Edits, converted to
/// bundled looping WebP with alpha). This pack is private user content: it is
/// bundled only into the user's own build and is never uploaded or shared.
class ChatyIosAnimatedEmoji {
  ChatyIosAnimatedEmoji._();

  static const String _dir = 'assets/emoji/ios_animated';

  /// Unicode emoji -> bundled WebP asset path (50 entries).
  static const Map<String, String> assets = <String, String>{
    '😀': '$_dir/1_grinning_face.webp',
    '😁': '$_dir/2_beaming_face.webp',
    '😂': '$_dir/3_face_with_tears.webp',
    '🤣': '$_dir/4_rolling_on_the_floor.webp',
    '😃': '$_dir/5_grinning_face_with_big_eyes.webp',
    '😄': '$_dir/6_grinning_face_with_smiling_eyes.webp',
    '😅': '$_dir/7_grinning_face_with_sweat.webp',
    '😆': '$_dir/8_grinning_squinting_face.webp',
    '😉': '$_dir/9_winking_face.webp',
    '😊': '$_dir/10_smiling_face_blushing.webp',
    '😋': '$_dir/11_face_savoring_food.webp',
    '😎': '$_dir/12_smiling_face_with_sunglasses.webp',
    '😍': '$_dir/13_smiling_face_with_heart_eyes.webp',
    '😘': '$_dir/14_face_blowing_a_kiss.webp',
    '😗': '$_dir/15_kissing_face.webp',
    '😙': '$_dir/16_kissing_face_with_smiling_eyes.webp',
    '😚': '$_dir/17_kissing_face_with_closed_eyes.webp',
    '☺️': '$_dir/18_blushing_closed_eyes.webp',
    '🙂': '$_dir/19_slightly_smiling_face.webp',
    '🤗': '$_dir/20_hugging_face.webp',
    '🤩': '$_dir/21_star_struck.webp',
    '🤔': '$_dir/22_thinking_face.webp',
    '🤨': '$_dir/23_face_with_raised_eyebrow.webp',
    '😐': '$_dir/24_neutral_face.webp',
    '😑': '$_dir/25_expressionless_face.webp',
    '😶': '$_dir/26_face_without_mouth.webp',
    '🙄': '$_dir/27_face_with_rolling_eyes.webp',
    '😏': '$_dir/28_smirking_face.webp',
    '😣': '$_dir/29_persevering_face.webp',
    '😥': '$_dir/30_sad_but_relieved_face.webp',
    '😮': '$_dir/31_face_with_open_mouth.webp',
    '🤐': '$_dir/32_zipper_mouth_face.webp',
    '😯': '$_dir/33_hushed_face.webp',
    '😪': '$_dir/34_sleepy_face.webp',
    '😫': '$_dir/35_tired_face.webp',
    '😴': '$_dir/36_sleeping_face.webp',
    '😌': '$_dir/37_relieved_face.webp',
    '😛': '$_dir/38_face_with_tongue.webp',
    '😜': '$_dir/39_winking_face_with_tongue.webp',
    '😝': '$_dir/40_squinting_face_with_tongue.webp',
    '🤤': '$_dir/41_drooling_face.webp',
    '😒': '$_dir/42_unamused_face.webp',
    '😓': '$_dir/43_downcast_face.webp',
    '😔': '$_dir/44_pensive_face.webp',
    '😕': '$_dir/45_confused_face.webp',
    '🙃': '$_dir/46_upside_down_face.webp',
    '🤑': '$_dir/47_money_mouth_face.webp',
    '😲': '$_dir/48_astonished_face.webp',
    '☹️': '$_dir/49_frowning_face.webp',
    '🙁': '$_dir/50_slightly_frowning_face.webp',
  };

  static String _normalize(String unicode) => unicode
      .trim()
      .replaceAll('\uFE0F', '')
      .replaceAll('\uFE0E', '');

  static bool isSupported(String unicode) {
    if (assets.containsKey(unicode.trim())) return true;
    return assets.containsKey(_normalize(unicode));
  }

  static String? assetFor(String unicode) {
    final direct = assets[unicode.trim()];
    if (direct != null) return direct;
    return assets[_normalize(unicode)];
  }
}

/// Renders one bundled iOS-style animated emoji (looping WebP with alpha).
/// Falls back to plain Unicode text when the emoji is not in the pack.
class IosAnimatedEmojiView extends StatelessWidget {
  final String unicode;
  final double size;
  final bool animate;

  const IosAnimatedEmojiView({
    super.key,
    required this.unicode,
    this.size = 64.0,
    this.animate = true,
  });

  @override
  Widget build(BuildContext context) {
    final asset = ChatyIosAnimatedEmoji.assetFor(unicode);
    if (asset == null) {
      return Text(unicode, style: TextStyle(fontSize: size * 0.82, height: 1.0));
    }
    return SizedBox(
      width: size,
      height: size,
      child: Image.asset(
        asset,
        width: size,
        height: size,
        fit: BoxFit.contain,
        gaplessPlayback: true,
        filterQuality: FilterQuality.medium,
        errorBuilder: (context, error, stackTrace) => Text(
          unicode,
          style: TextStyle(fontSize: size * 0.82, height: 1.0),
        ),
      ),
    );
  }
}
