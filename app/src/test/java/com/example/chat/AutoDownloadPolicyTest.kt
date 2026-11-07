package com.example.chat

import com.example.chat.data.media.AutoDownloadPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoDownloadPolicyTest {

    @Test fun `whatsapp parity defaults per network`() {
        val r = AutoDownloadPolicy.Rules()
        assertTrue(AutoDownloadPolicy.shouldAutoDownload(AutoDownloadPolicy.Network.WIFI, AutoDownloadPolicy.MediaKind.VIDEO, 50_000_000, r))
        assertTrue(AutoDownloadPolicy.shouldAutoDownload(AutoDownloadPolicy.Network.CELLULAR, AutoDownloadPolicy.MediaKind.IMAGE, 500_000, r))
        assertFalse(AutoDownloadPolicy.shouldAutoDownload(AutoDownloadPolicy.Network.CELLULAR, AutoDownloadPolicy.MediaKind.VIDEO, 500_000, r))
        assertFalse(AutoDownloadPolicy.shouldAutoDownload(AutoDownloadPolicy.Network.ROAMING, AutoDownloadPolicy.MediaKind.IMAGE, 1_000, r))
        assertFalse(AutoDownloadPolicy.shouldAutoDownload(AutoDownloadPolicy.Network.OFFLINE, AutoDownloadPolicy.MediaKind.IMAGE, 1_000, r))
    }

    @Test fun `data saver caps cellular by size and skips unknown sizes`() {
        val r = AutoDownloadPolicy.Rules(dataSaverEnabled = true, dataSaverMaxBytes = 100_000)
        assertTrue(AutoDownloadPolicy.shouldAutoDownload(AutoDownloadPolicy.Network.CELLULAR, AutoDownloadPolicy.MediaKind.IMAGE, 99_000, r))
        assertFalse(AutoDownloadPolicy.shouldAutoDownload(AutoDownloadPolicy.Network.CELLULAR, AutoDownloadPolicy.MediaKind.IMAGE, 101_000, r))
        assertFalse(AutoDownloadPolicy.shouldAutoDownload(AutoDownloadPolicy.Network.CELLULAR, AutoDownloadPolicy.MediaKind.IMAGE, null, r))
        // Wi-Fi unaffected by the saver
        assertTrue(AutoDownloadPolicy.shouldAutoDownload(AutoDownloadPolicy.Network.WIFI, AutoDownloadPolicy.MediaKind.IMAGE, 101_000, r))
    }

    @Test fun `per conversation override wins but never beats offline`() {
        val r = AutoDownloadPolicy.Rules()
        assertTrue(AutoDownloadPolicy.shouldAutoDownload(AutoDownloadPolicy.Network.ROAMING, AutoDownloadPolicy.MediaKind.VIDEO, 9_000_000, r, AutoDownloadPolicy.Override.ALWAYS))
        assertFalse(AutoDownloadPolicy.shouldAutoDownload(AutoDownloadPolicy.Network.WIFI, AutoDownloadPolicy.MediaKind.IMAGE, 1_000, r, AutoDownloadPolicy.Override.NEVER))
        assertFalse(AutoDownloadPolicy.shouldAutoDownload(AutoDownloadPolicy.Network.OFFLINE, AutoDownloadPolicy.MediaKind.IMAGE, 1_000, r, AutoDownloadPolicy.Override.ALWAYS))
    }
}
