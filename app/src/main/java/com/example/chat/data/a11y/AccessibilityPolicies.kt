package com.example.chat.data.a11y

/** Matrix 19 accessibility + matrix 14 picker favourites - pure logic,
 *  JVM-testable. Compose glue reads these; UI changes stay screenshot-gated. */

/** In-app text-size slider (Telegram): 80%-130% scale in 10% steps,
 *  layered over the system font scale, clamped so 320dp layouts survive. */
object TextScalePolicy {
    val STEPS = listOf(0.8f, 0.9f, 1.0f, 1.1f, 1.2f, 1.3f)
    const val MAX_COMBINED_SCALE = 1.6f

    fun nearestStep(value: Float): Float =
        STEPS.minByOrNull { kotlin.math.abs(it - value) } ?: 1.0f

    /** In-app scale x system scale, clamped; guards the large-font overflow. */
    fun combinedScale(inApp: Float, systemFontScale: Float): Float =
        (nearestStep(inApp) * systemFontScale).coerceIn(0.8f, MAX_COMBINED_SCALE)
}

/** Reduce-motion master switch (Discord): when on, or when the system
 *  animator duration scale is 0, all decorative animation disables. */
object ReduceMotionPolicy {
    fun animationsEnabled(appReduceMotion: Boolean, systemAnimatorScale: Float): Boolean =
        !appReduceMotion && systemAnimatorScale > 0f

    /** Duration multiplier the animation specs multiply by. */
    fun durationScale(appReduceMotion: Boolean, systemAnimatorScale: Float): Float =
        if (animationsEnabled(appReduceMotion, systemAnimatorScale)) 1.0f else 0.0f
}

/** Screen-reader label registry: every interactive element key maps to a
 *  spoken label, so the contentDescription pass is auditable and testable
 *  (a missing key fails the JVM test before the APK ever ships). */
class ContentDescriptionRegistry {
    private val labels = mutableMapOf<String, String>()

    fun register(elementKey: String, label: String) {
        require(label.isNotBlank()) { "empty label for $elementKey" }
        labels[elementKey] = label
    }

    fun labelFor(elementKey: String): String = labels[elementKey]
        ?: error("missing content description for $elementKey")

    fun unlabeled(requiredKeys: Set<String>): Set<String> = requiredKeys - labels.keys
}

/** M14 picker favourites + recents row (stickers/GIFs/emoji share it). */
class PickerFavoritesStore(private val maxRecents: Int = 24) {
    private val favorites = mutableSetOf<String>()
    private val recents = ArrayDeque<String>()

    fun toggleFavorite(id: String): Boolean =
        if (favorites.remove(id)) {
            // Un-favourited items drop to the end of recents (least recent),
            // so the picker row stays stable instead of jumping back to its
            // old recency slot.
            recents.remove(id)
            recents.addLast(id)
            false
        } else {
            favorites.add(id); true
        }

    fun isFavorite(id: String): Boolean = id in favorites

    fun recordUse(id: String) {
        recents.remove(id)
        recents.addFirst(id)
        while (recents.size > maxRecents) recents.removeLast()
    }

    /** Picker row: favourites first (stable), then recents not already shown. */
    fun row(): List<String> = favorites.toList() + recents.filter { it !in favorites }
}

/** Telugu + Hindi strings for the core shell (matrix 19 row 7). The compose
 *  UI is mostly hardcoded today; this catalog is the source the string
 *  extraction lands into, and the test proves the three locales stay in sync. */
object LocalizationCatalog {
    val KEYS = listOf(
        "new_chat", "search", "settings", "chats", "calls", "status",
        "app_lock", "locked_chats", "disappearing_messages", "backup",
    )

    private val en = mapOf(
        "new_chat" to "New chat", "search" to "Search", "settings" to "Settings",
        "chats" to "Chats", "calls" to "Calls", "status" to "Status",
        "app_lock" to "App lock", "locked_chats" to "Locked chats",
        "disappearing_messages" to "Disappearing messages", "backup" to "Backup",
    )
    private val te = mapOf(
        "new_chat" to "కొత్త చాట్", "search" to "వెతకండి", "settings" to "సెట్టింగ్‌లు",
        "chats" to "చాట్‌లు", "calls" to "కాల్‌లు", "status" to "స్టేటస్",
        "app_lock" to "యాప్ లాక్", "locked_chats" to "లాక్ చేసిన చాట్‌లు",
        "disappearing_messages" to "మాయమయ్యే సందేశాలు", "backup" to "బ్యాకప్",
    )
    private val hi = mapOf(
        "new_chat" to "नई चैट", "search" to "खोजें", "settings" to "सेटिंग्स",
        "chats" to "चैट", "calls" to "कॉल", "status" to "स्टेटस",
        "app_lock" to "ऐप लॉक", "locked_chats" to "लॉक की गई चैट",
        "disappearing_messages" to "गायब होने वाले संदेश", "backup" to "बैकअप",
    )

    private val locales = mapOf("en" to en, "te" to te, "hi" to hi)

    fun lookup(locale: String, key: String): String =
        locales[locale]?.get(key) ?: en.getValue(key)

    fun missingKeys(locale: String): List<String> = KEYS.filter { it !in (locales[locale] ?: emptyMap()) }
}
