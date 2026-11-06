package com.example.chat.ui.calls

/** No Android or account dependency: the adapter owns actual audio output. */
internal class DialpadToneDispatcher(private val output: (Tone, Int) -> Boolean) {
    enum class Tone { ZERO, ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE, STAR, HASH }
    fun dispatch(key: String, dialpadSoundsEnabled: Boolean, normalRingerMode: Boolean): Boolean {
        if (!dialpadSoundsEnabled || !normalRingerMode) return false
        val tone = when (key) {
            "0", "+" -> Tone.ZERO
            "1" -> Tone.ONE; "2" -> Tone.TWO; "3" -> Tone.THREE
            "4" -> Tone.FOUR; "5" -> Tone.FIVE; "6" -> Tone.SIX
            "7" -> Tone.SEVEN; "8" -> Tone.EIGHT; "9" -> Tone.NINE
            "*" -> Tone.STAR; "#" -> Tone.HASH
            else -> return false
        }
        return runCatching { output(tone, 100) }.getOrDefault(false)
    }
}
