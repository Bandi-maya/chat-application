package com.example.chat.core.config

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Stable keys survive app upgrades and remote revisions. Config fetches have NO write path here. */
class GroupUiChoicesStore(context: Context, accountHash: String) {
    init { require(accountHash.matches(Regex("[a-fA-F0-9]{32,128}"))) }
    private val prefs = context.applicationContext.getSharedPreferences("group_ui_user_choices_v1_$accountHash", Context.MODE_PRIVATE)
    fun read(): GroupUiUserChoices = runCatching {
        val row = JSONObject(prefs.getString("choices", "{}") ?: "{}")
        val order = row.optJSONArray("order")?.let { array -> (0 until array.length()).map { array.getString(it) } }
        GroupUiUserChoices(order?.takeIf(GroupUiConfigPolicy::validOrder), (row.opt("selected_display") as? String)?.takeIf { it in setOf("chips", "list") },
            row.opt("show_selected_count") as? Boolean)
    }.getOrDefault(GroupUiUserChoices())
    /** Call only in response to a deliberate user settings change. Persist synchronously or report failure. */
    fun saveExplicitChoice(value: GroupUiUserChoices): Boolean {
        require(value.order == null || GroupUiConfigPolicy.validOrder(value.order))
        require(value.selectedDisplay == null || value.selectedDisplay in setOf("chips", "list"))
        val row = JSONObject()
        value.order?.let { row.put("order", JSONArray(it)) }
        value.selectedDisplay?.let { row.put("selected_display", it) }
        value.showSelectedCount?.let { row.put("show_selected_count", it) }
        return prefs.edit().putString("choices", row.toString()).commit()
    }
}
