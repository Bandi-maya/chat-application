package com.example.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chat.data.transfer.NativeP2pTransferProtocol
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Android Base64 protocol adapter, no transport/network activity. */
@RunWith(AndroidJUnit4::class)
class P2pInviteAndroidCodecProofTest {
    @Test fun invitationRoundtripPreservesCanonicalIdAndSecret() {
        val invite = NativeP2pTransferProtocol.createInvite()
        var parsed: NativeP2pTransferProtocol.Invite? = null
        try {
            val uri = invite.toUri()
            assertFalse(uri.contains("="))
            assertFalse(uri.contains("\n"))
            assertEquals(43, uri.substringAfter('#').length)
            parsed = NativeP2pTransferProtocol.parseInvite(uri)
            assertEquals(invite.transferId, parsed.transferId)
            assertArrayEquals(invite.secret, parsed.secret)
        } finally { invite.secret.fill(0); parsed?.secret?.fill(0) }
    }
}
