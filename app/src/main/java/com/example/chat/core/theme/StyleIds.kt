package com.example.chat.core.theme

/** Exact ports of lib/ui/core/bubbles/bubble_style_id.dart and
 *  lib/ui/core/ticks/delivery_icon_style.dart: enum values, display names,
 *  GB vector-interpretation sets, and fromString alias migration. */

enum class BubbleStyleId {
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
    textra,
    goSms,
    cvIos,
    rcPal,
    rcIosLine,
    rcImLine,
    rcGoSms,
    rcTranLine,
    rcImess,
    gabiMess,
    rcFancy;

    val displayName: String
        get() = bubbleDisplayNames[this]!!
}

private val bubbleDisplayNames = mapOf<BubbleStyleId, String>(
    BubbleStyleId.crMessenger to "Cr Messenger",
    BubbleStyleId.rcBurbuja2 to "RC BURBUJA 2",
    BubbleStyleId.rcBurbuja5 to "RC BURBUJA 5",
    BubbleStyleId.threeDV2 to "3D V2",
    BubbleStyleId.kitty to "Kitty",
    BubbleStyleId.amor to "Amor",
    BubbleStyleId.rcSamsBord to "RC SAMS BORD",
    BubbleStyleId.rcIos11 to "RC IOS 11",
    BubbleStyleId.rcGoogleAssistan to "RC GOOGLE ASSISTAN",
    BubbleStyleId.gabiOutline to "Gabi Outline",
    BubbleStyleId.gabiSqua to "Gabi Squa",
    BubbleStyleId.gabiDot to "Gabi Dot",
    BubbleStyleId.rcLine to "Rc line",
    BubbleStyleId.gabiDot2 to "Gabi Dot 2",
    BubbleStyleId.roundle to "Roundle",
    BubbleStyleId.gabyRon to "Gaby Ron",
    BubbleStyleId.stock to "Stock",
    BubbleStyleId.facebookMessenger to "Facebook Messenger",
    BubbleStyleId.oldHangouts to "Old Hangouts",
    BubbleStyleId.threeD to "3D",
    BubbleStyleId.rounded to "Rounded",
    BubbleStyleId.materialized to "Materialized",
    BubbleStyleId.md to "MD",
    BubbleStyleId.samyadeepCrayonAlt to "Samyadeep Crayon Alt",
    BubbleStyleId.newHangouts4 to "New Hangouts 4.0",
    BubbleStyleId.waPaperRedesigned to "WA+ Paper Redesigned",
    BubbleStyleId.transparent to "Transparent",
    BubbleStyleId.twitter to "Twitter",
    BubbleStyleId.whatsappLb to "Whatsapp LB",
    BubbleStyleId.bbm to "BBM",
    BubbleStyleId.ios to "iOS",
    BubbleStyleId.hike to "Hike",
    BubbleStyleId.bubbleDrop to "Bubble Drop",
    BubbleStyleId.samyadeepDual to "Samyadeep Dual",
    BubbleStyleId.plusMessengerEd to "Plus Messenger ED",
    BubbleStyleId.waChatOn to "WA+ Chat On",
    BubbleStyleId.telegram to "Telegram",
    BubbleStyleId.inBubble to "In",
    BubbleStyleId.bryed to "Bryed",
    BubbleStyleId.fold to "Fold",
    BubbleStyleId.foldV2 to "Fold v2",
    BubbleStyleId.ilkhang to "Ilkhang",
    BubbleStyleId.line to "Line",
    BubbleStyleId.popzup to "Popzup",
    BubbleStyleId.doodleHang to "DoodleHang",
    BubbleStyleId.aranbor to "Aranbor",
    BubbleStyleId.win to "Win",
    BubbleStyleId.mood to "Mood",
    BubbleStyleId.textra to "Textra SMS",
    BubbleStyleId.goSms to "Go SMS",
    BubbleStyleId.cvIos to "CV ios",
    BubbleStyleId.rcPal to "Rc Pal",
    BubbleStyleId.rcIosLine to "Rc ios Line",
    BubbleStyleId.rcImLine to "Rc IM Line",
    BubbleStyleId.rcGoSms to "Rc Go SMS",
    BubbleStyleId.rcTranLine to "Rc tran Line",
    BubbleStyleId.rcImess to "Rc Imess",
    BubbleStyleId.gabiMess to "Gabi Mess",
    BubbleStyleId.rcFancy to "Rc Fancy",
)

val gbBubbleVectorInterpretations: Set<BubbleStyleId> = setOf(
    BubbleStyleId.textra,
    BubbleStyleId.goSms,
    BubbleStyleId.cvIos,
    BubbleStyleId.rcPal,
    BubbleStyleId.rcIosLine,
    BubbleStyleId.rcImLine,
    BubbleStyleId.rcGoSms,
    BubbleStyleId.rcTranLine,
    BubbleStyleId.rcImess,
    BubbleStyleId.gabiMess,
    BubbleStyleId.rcFancy,
)

fun bubbleStyleIdFromString(key: String?): BubbleStyleId {
    if (key.isNullOrEmpty()) return BubbleStyleId.stock
    val normalized = key.lowercase().replace(Regex("[^a-z0-9]"), "")

    for (id in BubbleStyleId.entries) {
        if (id.name.lowercase() == normalized ||
            id.displayName.lowercase().replace(Regex("[^a-z0-9]"), "") == normalized
        ) {
            return id
        }
    }

    // Legacy migrations and the labels exposed by Conversation > Bubbles Style.
    return when (normalized) {
        "classic", "default", "tail", "whatsappdefault" -> BubbleStyleId.stock
        "tailless", "compact", "pill", "roundedpill" -> BubbleStyleId.facebookMessenger
        "chatymodern" -> BubbleStyleId.rounded
        "squircle", "softsquare", "squirclesoft" -> BubbleStyleId.gabiSqua
        "paperchat" -> BubbleStyleId.waPaperRedesigned
        "glassmorphic" -> BubbleStyleId.transparent
        "iosflat" -> BubbleStyleId.ios
        "telegramarc" -> BubbleStyleId.telegram
        "3dshadow" -> BubbleStyleId.threeD
        "minimalborder" -> BubbleStyleId.gabiOutline
        "retrobubble" -> BubbleStyleId.oldHangouts
        "futuristicclip" -> BubbleStyleId.foldV2
        "card", "minimal" -> BubbleStyleId.materialized
        "sharptail" -> BubbleStyleId.oldHangouts
        else -> BubbleStyleId.stock
    }
}

enum class DeliveryIconStyle {
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
    flower;

    val displayName: String
        get() = tickDisplayNames[this]!!
}

private val tickDisplayNames = mapOf<DeliveryIconStyle, String>(
    DeliveryIconStyle.stock to "Stock",
    DeliveryIconStyle.sticker to "Sticker",
    DeliveryIconStyle.rcIos11 to "RC iOS 11",
    DeliveryIconStyle.bbmV2 to "BBM V2",
    DeliveryIconStyle.bwTicks to "B.W Ticks",
    DeliveryIconStyle.cirCheck to "CirCheck",
    DeliveryIconStyle.circlePrint to "Circle Print",
    DeliveryIconStyle.gabCircle to "Gab Circle",
    DeliveryIconStyle.gabFace to "Gab Face",
    DeliveryIconStyle.gabIflo to "Gab iflo",
    DeliveryIconStyle.greenTick to "Green Tick",
    DeliveryIconStyle.ios2 to "iOS 2",
    DeliveryIconStyle.letterCircle to "Letter Circle",
    DeliveryIconStyle.rcAlo to "RC Alo",
    DeliveryIconStyle.rcTick to "RC Tick",
    DeliveryIconStyle.triangle to "Triangle",
    DeliveryIconStyle.vantCircle to "VantCircle",
    DeliveryIconStyle.wings to "Wings",
    DeliveryIconStyle.trafficLights to "Traffic Lights",
    DeliveryIconStyle.stars to "Stars",
    DeliveryIconStyle.hearts to "Hearts",
    DeliveryIconStyle.batman to "Batman",
    DeliveryIconStyle.doubleSword to "Double Sword",
    DeliveryIconStyle.glowingDots to "Glowing Dots",
    DeliveryIconStyle.material3Rounded to "Material 3 Rounded",
    DeliveryIconStyle.allo to "Allo",
    DeliveryIconStyle.alien to "Alien",
    DeliveryIconStyle.altCrayonV2 to "Alt CrayonV2",
    DeliveryIconStyle.altCrayon to "Alt Crayon",
    DeliveryIconStyle.aran to "Aran",
    DeliveryIconStyle.bar to "Bar",
    DeliveryIconStyle.bat to "Bat",
    DeliveryIconStyle.bbm to "BBM",
    DeliveryIconStyle.blend to "Blend",
    DeliveryIconStyle.ngAndroidBot to "NG Android Bot",
    DeliveryIconStyle.bpgCircle to "BPG Circle",
    DeliveryIconStyle.gbCircles to "Circles",
    DeliveryIconStyle.samyadeepCircles to "Samyadeep Circles",
    DeliveryIconStyle.coalfield to "Coalfield",
    DeliveryIconStyle.colorSimple to "Color Simple",
    DeliveryIconStyle.ctoon to "Ctoon",
    DeliveryIconStyle.eclipsis to "Eclipsis",
    DeliveryIconStyle.faceSwitch to "Face Switch",
    DeliveryIconStyle.feet to "Feet",
    DeliveryIconStyle.flatCircles to "Flat Circles",
    DeliveryIconStyle.gifcon to "Gifcon",
    DeliveryIconStyle.googleV2 to "Google V2",
    DeliveryIconStyle.google to "Google",
    DeliveryIconStyle.grw to "GRW",
    DeliveryIconStyle.sidzyHd to "Sidzy HD",
    DeliveryIconStyle.svHeart to "SV Heart",
    DeliveryIconStyle.hike to "Hike",
    DeliveryIconStyle.inbox to "Inbox",
    DeliveryIconStyle.insect to "Insect",
    DeliveryIconStyle.gbIos to "iOS",
    DeliveryIconStyle.jalopy to "Jalopy",
    DeliveryIconStyle.jessy to "Jessy",
    DeliveryIconStyle.joaquins to "Joaquins",
    DeliveryIconStyle.jokerTraffic to "Joker Traffic",
    DeliveryIconStyle.letter to "Letter",
    DeliveryIconStyle.like to "Like",
    DeliveryIconStyle.loveSwitch to "Love Switch",
    DeliveryIconStyle.materialV2 to "Material V2",
    DeliveryIconStyle.materialSwitch to "Material Switch",
    DeliveryIconStyle.messenger to "Messenger",
    DeliveryIconStyle.mickeyHands to "Mickey Hands",
    DeliveryIconStyle.minions to "Minions",
    DeliveryIconStyle.newWaca to "New Waca",
    DeliveryIconStyle.neyHeart to "NEY Heart",
    DeliveryIconStyle.nhsc to "NHSC",
    DeliveryIconStyle.nazaNh to "Naza NH",
    DeliveryIconStyle.nlCircle to "NL Circle",
    DeliveryIconStyle.oldWaca to "Old Waca",
    DeliveryIconStyle.pacman to "Pacman",
    DeliveryIconStyle.pixelated to "Pixelated",
    DeliveryIconStyle.pokemon to "Pokemon",
    DeliveryIconStyle.quinn to "Quinn",
    DeliveryIconStyle.snapChat to "SnapChat",
    DeliveryIconStyle.sDot to "SDot",
    DeliveryIconStyle.seasons to "Seasons",
    DeliveryIconStyle.smile to "Smile",
    DeliveryIconStyle.star to "Star",
    DeliveryIconStyle.flatSwitch to "Flat Switch",
    DeliveryIconStyle.timeBomb to "Time Bomb",
    DeliveryIconStyle.jokerTraffic2 to "Joker Traffic 2",
    DeliveryIconStyle.treble to "Treble",
    DeliveryIconStyle.twitterConcept to "Twitter Concept",
    DeliveryIconStyle.wbg to "WBG",
    DeliveryIconStyle.x to "X",
    DeliveryIconStyle.pointer to "Pointer",
    DeliveryIconStyle.ticTicCircle2 to "TIC TIC circle 2",
    DeliveryIconStyle.iceCream to "Ice Cream",
    DeliveryIconStyle.pony to "Pony",
    DeliveryIconStyle.kitty to "Kitty",
    DeliveryIconStyle.flower to "Flower",
)

val gbTickVectorInterpretations: Set<DeliveryIconStyle> = setOf(
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
)

fun deliveryIconStyleFromString(key: String?): DeliveryIconStyle {
    if (key.isNullOrEmpty()) return DeliveryIconStyle.stock
    val normalized = key.lowercase().replace(Regex("[^a-z0-9]"), "")

    for (style in DeliveryIconStyle.entries) {
        if (style.name.lowercase() == normalized) {
            return style
        }
        // Dart quirk: gbIos and gbCircles are skipped in displayName matching.
        if (style == DeliveryIconStyle.gbIos || style == DeliveryIconStyle.gbCircles) {
            continue
        }
        if (style.displayName.lowercase().replace(Regex("[^a-z0-9]"), "") == normalized) {
            return style
        }
    }

    return when (normalized) {
        "default", "iosstyle", "ios" -> DeliveryIconStyle.rcIos11
        "iosticks" -> DeliveryIconStyle.ios2
        "wings" -> DeliveryIconStyle.wings
        "trafficlights" -> DeliveryIconStyle.trafficLights
        "circles" -> DeliveryIconStyle.cirCheck
        "stars" -> DeliveryIconStyle.stars
        "hearts" -> DeliveryIconStyle.hearts
        "batman" -> DeliveryIconStyle.batman
        "doublesword" -> DeliveryIconStyle.doubleSword
        "glowingdots" -> DeliveryIconStyle.glowingDots
        "material3rounded" -> DeliveryIconStyle.material3Rounded
        "doublecheck" -> DeliveryIconStyle.greenTick
        "minimal" -> DeliveryIconStyle.bwTicks
        "neon" -> DeliveryIconStyle.vantCircle
        else -> DeliveryIconStyle.rcIos11
    }
}
