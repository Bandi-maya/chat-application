import com.example.chat.core.templates.*
import kotlinx.coroutines.*

class FakeStore(var ok: Boolean = true, var initial: Map<String, Any?> = emptyMap()) : TemplateStateStore {
    var saved: Map<String, Any?>? = null
    override suspend fun load() = initial
    override suspend fun save(data: Map<String, Any?>): Boolean { delay(5); if (ok) saved = data; return ok }
}
class FakeSink : TemplateRuntimeSink {
    val log = ArrayList<String>()
    override fun applyNavigationLayout(layout: NavigationLayoutType) { log += "nav:$layout" }
    override fun applyConversationTheme(conversation: ConversationTemplate) { log += "theme:${conversation.bubbleStyle}" }
    override suspend fun applyBottomBarStyle(name: String) { log += "bar:$name" }
    override fun applyHomePreferences(home: HomeTemplate) { log += "home:${home.homeStylePreset}" }
    override fun applyConversationPreferences(conversation: ConversationTemplate) { log += "conv:${conversation.wallpaperType}" }
}
class FakeAppearanceStore(var ok: Boolean = true) : AppearanceStore {
    val m = HashMap<String, String>()
    override suspend fun getString(key: String) = m[key]
    override suspend fun setString(key: String, value: String): Boolean { if (ok) m[key] = value; return ok }
}

fun main() = runBlocking {
    val st = FakeStore(); val sink = FakeSink(); val rt = TemplateRuntime(sink, sink, sink)
    val c = TemplateController(st)
    c.init(rt)
    check(sink.log == listOf("nav:floatingPill", "theme:Stock", "bar:Floating Pill", "home:Chaty Default", "conv:Pattern")) { sink.log.toString() }
    sink.log.clear()
    c.applyComponent(TemplateComponentType.composer, ChatyTemplateId.powerChat, rt)
    check(sink.log.isEmpty()) // composer-only override must not touch others
    check(c.composer.actionPlacement == ComposerActionPlacement.powerRow)
    // concurrent edits both survive (built from latest state in queue)
    coroutineScope {
        launch { c.applyComponent(TemplateComponentType.home, ChatyTemplateId.community, rt) }
        launch { c.applyComponent(TemplateComponentType.profile, ChatyTemplateId.stream, rt) }
    }
    check(c.componentOverrides.size == 3) { c.componentOverrides.toString() }
    // save failure: old config stays, queue still usable
    st.ok = false
    val before = c.config
    try { c.applyFullTemplate(ChatyTemplateId.stream, rt); error("no throw") } catch (e: IllegalStateException) {
        check(e.message == "Unable to persist the Chaty template configuration.")
    }
    check(c.config == before)
    st.ok = true
    c.applyFullTemplate(ChatyTemplateId.stream, rt)
    check(c.baseTemplate == ChatyTemplateId.stream && c.componentOverrides.isEmpty())
    check(sink.log.takeLast(5)[0] == "nav:flatTabs")
    // navigation validation
    suspend fun bad(p: List<String>, o: List<String>, msg: String) {
        try { c.setNavigationDestinations(p, o, rt); error("no throw") } catch (e: IllegalArgumentException) { check(e.message!!.startsWith(msg)) { "msg" } }
    }
    bad(emptyList(), listOf("chats","groups","updates","tasks","calls","settings","desktop"), "Choose 1")
    bad(listOf("chats","groups","updates","tasks","calls"), listOf("settings","desktop"), "Choose 1")
    bad(listOf("chats","chats"), listOf("groups","updates","tasks","calls","settings","desktop"), "Choose 1")
    bad(listOf("chats"), listOf("chats","groups","updates","tasks","calls","settings","desktop"), "Choose 1")
    bad(listOf("nope"), listOf("chats","groups","updates","tasks","calls","settings","desktop"), "Choose 1")
    bad(listOf("chats","calls"), listOf("groups","updates"), "Every Chaty")
    c.setNavigationDestinations(listOf("calls","chats"), listOf("groups","updates","tasks","settings","desktop"), rt)
    check(c.navigation.primaryDestinationIds == listOf("calls","chats") && c.hasCustomNavigationDestinations)
    check(st.saved!!["navPrimary"] == listOf("calls","chats"))
    c.resetNavigationDestinations(rt)
    check(!c.hasCustomNavigationDestinations)
    // restore from persisted map
    val c2 = TemplateController(FakeStore(initial = st.saved!!)); c2.init()
    check(c2.baseTemplate == ChatyTemplateId.stream)
    // appearance controller
    val a = FakeAppearanceStore(); val ac = AppearanceVariantController(a)
    a.m["appearance.bottomBar"] = "bogus"; ac.load()
    check(ac.bottomBarStyle == "Floating Pill" && ac.bottomBarIndex == 0)
    ac.setBottomBarStyle("Classic Label Bar"); check(ac.bottomBarIndex == 10 && a.m["appearance.bottomBar"] == "Classic Label Bar")
    ac.setBottomBarStyle("nope"); check(ac.bottomBarStyle == "Classic Label Bar")
    a.ok = false
    try { ac.setTypographyStyle("Large UI"); error("no throw") } catch (e: IllegalStateException) {}
    check(ac.typographyStyle == "System Default")
    a.ok = true; ac.setTypographyStyle("Large UI"); check(ac.textScale == 1.10)
    check(AppearanceVariantController.resolveTextScale(1.2, 0.9) == 1.2)
    check(AppearanceVariantController.resolveTextScale(1.0, 2.0) == 1.6)
    check(AppearanceVariantController.navigationStyles.size == 20 && AppearanceVariantController.bottomBarStyles.size == 12)
    println("OK")
}
