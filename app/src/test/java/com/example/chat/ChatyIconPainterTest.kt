import com.example.chat.ui.icons.*

class Rec : IconCanvas {
    var draws = 0; var saves = 0; var restores = 0
    var lastPath: Path? = null
    override fun drawPath(path: Path, paint: Paint) { draws++; lastPath = path }
    override fun drawLine(p1: Offset, p2: Offset, paint: Paint) { draws++ }
    override fun drawRect(rect: Rect, paint: Paint) { draws++ }
    override fun drawRRect(rrect: RRect, paint: Paint) { draws++ }
    override fun drawCircle(center: Offset, radius: Number, paint: Paint) { draws++ }
    override fun drawOval(rect: Rect, paint: Paint) { draws++ }
    override fun drawArc(rect: Rect, startAngle: Number, sweepAngle: Number, useCenter: Boolean, paint: Paint) { draws++ }
    override fun save() { saves++ }
    override fun restore() { restores++ }
    override fun translate(dx: Number, dy: Number) {}
    override fun scale(sx: Number, sy: Number) {}
    override fun rotate(radians: Number) {}
}

fun main() {
    val empty = ArrayList<String>()
    var total = 0
    for (id in ChatyIconId.values()) for (th in ChatyIconThemeId.values()) {
        val c = Rec()
        ChatyIconPainter.paint(c, 24.0, id, th, Colors.white)
        check(c.saves == c.restores) { "$id $th save/restore" }
        if (c.draws == 0) empty.add("$id/$th")
        total += c.draws
    }
    println("icons=${ChatyIconId.values().size} themes=${ChatyIconThemeId.values().size} draws=$total empty=${empty.size}")
    println(empty.groupBy { it.substringBefore('/') }.mapValues { it.value.size })
    check(ChatyIconThemeId.fromString("Chaty Orbit") == ChatyIconThemeId.chatySigil)
    check(ChatyIconThemeId.fromString("prism-edge") == ChatyIconThemeId.origamiGeometry)
    check(ChatyIconThemeId.fromString("Technical Blueprint") == ChatyIconThemeId.technicalBlueprint)
    check(ChatyIconThemeId.fromString(null) == ChatyIconThemeId.chatySigil)
    check(ChatyIconThemeId.fromString("zzz") == ChatyIconThemeId.chatySigil)
    // arcToPoint: half circle from (0,0) to (2,0) radius 1 clockwise ends at (2,0) and bulges upward (y<0)
    val p = Path(); p.moveTo(0, 0); p.arcToPoint(Offset(2, 0), radius = Radius.circular(1), clockwise = true)
    val last = p.ops.last() as PathOp.CubicTo
    check(Math.abs(last.x3 - 2.0) < 1e-9 && Math.abs(last.y3) < 1e-9)
    val mid = p.ops[1] as PathOp.CubicTo
    check(mid.y1 < 0.0) { "clockwise arc should bulge to -y first, got ${mid.y1}" }
    check(Colors.white.withValues(alpha = 0.28).a == 0.28)
    println("OK")
}
