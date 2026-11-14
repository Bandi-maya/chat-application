package com.example.chat

import com.example.chat.data.api.ApiGroupMembers
import org.junit.Assert.*
import org.junit.Test

class ApiGroupMembersTest {
    @Test fun countsOnlyActiveUnknownRolesNeverBecomeAdmin() {
        val snapshot = ApiGroupMembers.decode("""{"success":true,"data":{"members":[
            {"user_id":"a","username":"alice","display_name":"","role":"owner","status":"active"},
            {"user_id":"b","username":"bob","role":"admin","status":"pending"},
            {"user_id":"c","username":"carol","role":"superadmin","status":"active"},
            {"user_id":"d","role":"admin","status":"future"}],"join_requires_approval":true,"permissions":{}}}""")
        assertEquals(2, snapshot.activeCount)
        assertEquals(listOf("a"), snapshot.adminIds)
        assertEquals("alice", snapshot.active.first().displayName)
        assertEquals("member", snapshot.active.last().role)
        assertTrue(snapshot.joinRequiresApproval)
    }
    @Test(expected = IllegalStateException::class) fun duplicateActiveIdsDoNotProduceCount() {
        ApiGroupMembers.decode("""{"success":true,"data":{"members":[{"user_id":"a","status":"active"},{"user_id":"a","status":"active"}],"join_requires_approval":false}}""")
    }
}
