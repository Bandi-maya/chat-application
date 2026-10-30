package com.example.chat

import com.example.chat.ui.chats.location.FriendMapConnection
import com.example.chat.ui.chats.location.FriendMapCopy
import com.example.chat.ui.chats.location.FriendMapOwnPin
import com.example.chat.ui.chats.location.FriendMapVisibilityPreview
import com.example.chat.ui.chats.location.LocationCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Honesty contract for the frontend-only friends map: no backend means no
 * connected state, no fabricated friend data, and copy that says so plainly.
 */
class FriendMapStateTest {

    @Test fun `not connected is a singleton state`() {
        // The sealed interface declares exactly one state today; if a Connected
        // state arrives with backend wiring this contract must be revisited.
        val conn: FriendMapConnection = FriendMapConnection.NotConnected
        assertSame(FriendMapConnection.NotConnected, conn)
    }

    @Test fun `status chip says friend locations are not connected`() {
        assertTrue(FriendMapCopy.MAP_STATUS_CHIP.contains("not connected"))
    }

    @Test fun `not-connected copy names the missing backend and the consent rule`() {
        assertTrue(FriendMapCopy.NOT_CONNECTED_BODY.contains("isn't connected yet"))
        assertTrue(FriendMapCopy.NOT_CONNECTED_BODY.contains("who choose to share"))
        assertTrue(FriendMapCopy.NOT_CONNECTED_TITLE.isNotBlank())
    }

    @Test fun `own pin caption promises nothing is shared`() {
        assertTrue(FriendMapCopy.OWN_LOCATION_CAPTION.contains("Only you can see"))
        assertTrue(FriendMapCopy.OWN_LOCATION_CAPTION.contains("Nothing is shared"))
    }

    @Test fun `visibility controls promise no automatic sharing`() {
        assertTrue(FriendMapCopy.VISIBILITY_INACTIVE_CAPTION.contains("never shared automatically"))
    }

    @Test fun `visibility previews keep Hidden first and fully described`() {
        val entries = FriendMapVisibilityPreview.entries
        assertEquals(3, entries.size)
        assertEquals(FriendMapVisibilityPreview.Hidden, entries.first())
        entries.forEach {
            assertTrue(it.label.isNotBlank())
            assertTrue(it.description.isNotBlank())
        }
        assertTrue(FriendMapVisibilityPreview.Hidden.description.contains("never appears"))
    }

    @Test fun `own pin stores exact coordinates and precision flag`() {
        val pin = FriendMapOwnPin(LocationCoordinate(12.3456, 78.9012), precise = true)
        assertEquals(12.3456, pin.coordinate.latitude, 1e-9)
        assertEquals(78.9012, pin.coordinate.longitude, 1e-9)
        assertTrue(pin.precise)
        assertEquals(true, pin.copy(precise = false).precise.not())
    }
}
