package com.example.chat.data.api

import org.json.JSONObject

/** Snapshot only: pinned backend has no total, cursor, pagination or ordering guarantee. */
data class ApiGroupMember(val userId: String, val username: String, val displayName: String, val role: String)
data class ApiGroupMembers(val active: List<ApiGroupMember>, val joinRequiresApproval: Boolean) {
    val activeCount: Int get() = active.size
    val adminIds: List<String> get() = active.filter { it.role == "owner" || it.role == "admin" }.map { it.userId }
    companion object {
        fun decode(raw: String): ApiGroupMembers {
            val root = JSONObject(raw)
            check(root.getBoolean("success"))
            val data = root.getJSONObject("data")
            val rows = data.getJSONArray("members")
            val seen = HashSet<String>()
            val active = buildList {
                for (index in 0 until rows.length()) {
                    val row = rows.getJSONObject(index)
                    // Pending/unknown statuses are never counted or granted member/admin privileges.
                    if (row.opt("status") != "active") continue
                    val id = row.getString("user_id")
                    check(id.isNotBlank() && ApiPathSegments.safe(id) != null && seen.add(id))
                    val username = row.opt("username") as? String ?: ""
                    val display = (row.opt("display_name") as? String).orEmpty().ifBlank { username }
                    val role = (row.opt("role") as? String)?.takeIf { it in setOf("owner", "admin", "member") } ?: "member"
                    add(ApiGroupMember(id, username, display, role))
                }
            }
            // permissions:{} does not override backend role defaults. Do not infer mutation rights here.
            return ApiGroupMembers(active, data.getBoolean("join_requires_approval"))
        }
    }
}
