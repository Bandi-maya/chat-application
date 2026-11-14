package com.example.chat.core.config

import org.json.JSONObject

/** Fixed vocabulary of shipped UI, never code, arbitrary actions, URLs or Android permissions. */
data class GroupUiConfig(
    val title: String = "Create group",
    val nameLabel: String = "Group name",
    val nameMaxLength: Int = 100,
    val order: List<String> = listOf("search", "selected", "contacts"),
    val selectedDisplay: String = "chips",
    val showSelectedCount: Boolean = true,
    val nextLabel: String = "Next",
    val createLabel: String = "Create",
    val searchLabel: String = "Search",
)
data class GroupUiConfigEnvelope(val revision: String, val group: GroupUiConfig)

object GroupUiConfigPolicy {
    val defaults = GroupUiConfig()
    val sections = setOf("search", "selected", "contacts")
    private fun keys(value: JSONObject, expected: Set<String>) = check(value.keys().asSequence().toSet() == expected) { "Unexpected UI config schema." }
    fun safeText(value: String, max: Int): Boolean = value.length in 1..max && value == value.trim() &&
        value.none { it.isISOControl() || it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069' || it == '<' || it == '>' || it == '`' } &&
        !value.contains(Regex("(?i)(https?://|javascript:|data:|file:|intent:)"))
    fun validOrder(order: List<String>): Boolean = order.size == 3 && order.toSet() == sections
    /** Invalid data rejects the entire affected screen, rather than mixing unvalidated fragments. */
    fun parse(body: String): GroupUiConfigEnvelope? = runCatching {
        check(body.toByteArray(Charsets.UTF_8).size <= 8192)
        val response = JSONObject(body)
        val root = response.optJSONObject("data") ?: response
        keys(root, setOf("schema_version", "revision", "screens"))
        check(root.opt("schema_version") is Int && root.getInt("schema_version") == 1)
        val revision = (root.opt("revision") as? String ?: error("Invalid revision.")).also { check(it.matches(Regex("[A-Za-z0-9._-]{1,64}"))) }
        val screens = root.getJSONObject("screens").also { keys(it, setOf("group_creation")) }
        val group = screens.getJSONObject("group_creation").also { keys(it, setOf("title", "name_label", "name_max_length", "member_selection", "labels")) }
        fun text(row: JSONObject, key: String, limit: Int) = (row.opt(key) as? String ?: error("Invalid label.")).also { check(safeText(it, limit)) }
        check(group.opt("name_max_length") is Int)
        val max = group.getInt("name_max_length").also { check(it in 1..100) }
        val member = group.getJSONObject("member_selection").also { keys(it, setOf("order", "selected_display", "show_selected_count")) }
        val rows = member.getJSONArray("order")
        val order = (0 until rows.length()).map { rows.getString(it) }.also { check(validOrder(it)) }
        val display = member.getString("selected_display").also { check(it in setOf("chips", "list")) }
        check(member.opt("show_selected_count") is Boolean)
        val labels = group.getJSONObject("labels").also { keys(it, setOf("next", "create", "search")) }
        GroupUiConfigEnvelope(revision, GroupUiConfig(text(group, "title", 80), text(group, "name_label", 80), max,
            order, display, member.getBoolean("show_selected_count"), text(labels, "next", 32), text(labels, "create", 32), text(labels, "search", 80)))
    }.getOrNull()
}

/** Only an explicit user's choice belongs here. Remote defaults/revisions are never copied into this record. */
data class GroupUiUserChoices(val order: List<String>? = null, val selectedDisplay: String? = null, val showSelectedCount: Boolean? = null) {
    fun applyTo(remoteDefaults: GroupUiConfig): GroupUiConfig = remoteDefaults.copy(
        order = order?.takeIf(GroupUiConfigPolicy::validOrder) ?: remoteDefaults.order,
        selectedDisplay = selectedDisplay?.takeIf { it in setOf("chips", "list") } ?: remoteDefaults.selectedDisplay,
        showSelectedCount = showSelectedCount ?: remoteDefaults.showSelectedCount,
    )
}
