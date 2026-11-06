package com.example.chat.core.messages

/** Chaty view-once surface rules, matching the GB/stock reference behaviour
 *  (GBWhatsApp v2.26.39.80 static spec): a view-once bubble starts LOCKED;
 *  image/video open in the full-screen viewer and then expire (unless the
 *  host retains the opened session view); a view-once VOICE note plays in
 *  the bubble, so it stays playable after the first tap and expires only
 *  when playback completes. Pure Kotlin: JVM-testable. */
object ViewOnceStatePolicy {

    enum class Kind { MEDIA, VOICE }

    enum class Surface { LOCKED, CONTENT, EXPIRED }

    fun surface(
        kind: Kind,
        opened: Boolean,
        retainOpenedMedia: Boolean,
        playbackCompleted: Boolean,
    ): Surface = when {
        !opened -> Surface.LOCKED
        kind == Kind.VOICE -> if (playbackCompleted) Surface.EXPIRED else Surface.CONTENT
        else -> if (retainOpenedMedia) Surface.CONTENT else Surface.EXPIRED
    }
}
