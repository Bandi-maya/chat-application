import com.example.chat.core.templates.*
fun main() {
    check(ChatyTemplateRegistry.list.size == 6)
    check(ChatyTemplateRegistry.get(ChatyTemplateId.cameraFirst).navigation.centerActionIcon == "camera_alt_rounded")
    check(ChatyTemplateRegistry.messageFirst.conversation.bubbleCornerRadius == 16.0)
    check(ChatyTemplateRegistry.powerChat.home.separateChatsAndGroups)
    for ((id, d) in ChatyTemplateRegistry.all) check(d.id == id)
    val c = UserTemplateConfiguration().copyWithOverride(TemplateComponentType.home, ChatyTemplateId.community)
    check(c.resolveFor(TemplateComponentType.home) == ChatyTemplateId.community)
    check(c.copyWithOverride(TemplateComponentType.home, ChatyTemplateId.messageFirst).componentOverrides.isEmpty())
    val rt = UserTemplateConfiguration.fromMap(c.copyWithNavigationDestinations(listOf("chats","chats","bogus","calls"), listOf("tasks")).toMap())
    check(rt.navigationPrimaryDestinationIds == listOf("chats","calls")) { "nav" }
    check(rt.isOverridden(TemplateComponentType.home))
    check(UserTemplateConfiguration.fromMap(mapOf("base" to "zzz")).baseTemplate == ChatyTemplateId.messageFirst)
    println("OK")
}
