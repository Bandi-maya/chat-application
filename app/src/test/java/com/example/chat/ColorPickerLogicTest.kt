import com.example.chat.ui.kit.ColorPickerLogic as L
fun ck(c: Boolean, m: String) { if (!c) throw AssertionError(m) }
fun main() {
    ck(L.toHex(0xFF6366F1) == "6366F1", "hex")
    ck(L.fromHex("#6366f1") == 0xFF6366F1, "from")
    ck(L.fromHex("12345") == null && L.fromHex("zzzzzz") == null, "invalid")
    ck(Math.abs(L.contrastRatio(0xFF000000, 0xFFFFFFFF) - 21.0) < 1e-9, "21")
    ck(L.hasContrastWarning(4.4) && !L.hasContrastWarning(4.5) && !L.hasContrastWarning(null), "warn")
    ck(L.luminance(0xFFFFFFFF) > 0.99 && L.luminance(0xFF000000) == 0.0, "lum")
    ck(L.autoFix(0xFFFFFFFF, 1, 2) == 1L && L.autoFix(0xFF000000, 1, 2) == 2L, "fix")
    println("ColorPickerLogicTest OK")
}
