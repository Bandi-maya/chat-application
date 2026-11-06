package com.example.chat

import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.chat.core.theme.ThemePresets
import com.example.chat.data.transfer.ChatyDirectProtocol
import com.example.chat.data.transfer.ChunkReceiptLedger
import com.example.chat.data.transfer.NativeP2pTransferProtocol
import com.example.chat.ui.chats.detail.AttachmentSheetContent
import com.example.chat.ui.chats.detail.AttachmentAction
import com.example.chat.ui.theme.ChatyTheme
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** No server/network side effects. Two-device NAT/transport acceptance remains separate. */
@RunWith(AndroidJUnit4::class)
class ChatyDirectPhaseOneProofTest {
    // The UI acceptance proof is AttachmentInitialOpenProofTest: actual production
    // gallery sheet, partial anchor, existing dock, fonts1x/2x, not legacy action grid.
    @Test fun realAndroidLedgerReopensOnlyVerifiedOriginalChunks() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"direct-proof-${System.nanoTime()}").apply{mkdirs()}
        val source=File(root,"original.mp4").apply{writeBytes(ByteArray(130123){(it*29).toByte()})}
        val invite=NativeP2pTransferProtocol.createInvite()
        try {
            val m=NativeP2pTransferProtocol.createManifest(invite,source,"video/mp4",System.currentTimeMillis()/1000+600)
            val part=File(root,"receive.part")
            val state=File(root,"ledger.state")
            val ledger=ChunkReceiptLedger(state,m.chunkCount)
            RandomAccessFile(source,"r").use { f ->
                val index=ChatyDirectProtocol.writeChunk(part,m,invite.secret,ChatyDirectProtocol.encryptChunk(f.channel,m,invite.secret,0))
                NativeP2pTransferProtocol.syncFile(part); ledger.markReceived(index);ledger.flush()
            }
            val restored=ChunkReceiptLedger(state,m.chunkCount)
            assertTrue(restored.hasReceived(0)); assertFalse(restored.isComplete())
            RandomAccessFile(source,"r").use { f ->
                for(i in restored.missingIndices()) {
                    val index=ChatyDirectProtocol.writeChunk(part,m,invite.secret,ChatyDirectProtocol.encryptChunk(f.channel,m,invite.secret,i))
                    NativeP2pTransferProtocol.syncFile(part); restored.markReceived(index);restored.flush()
                }
            }
            assertTrue(ChunkReceiptLedger(state,m.chunkCount).isComplete())
            assertTrue(NativeP2pTransferProtocol.verifyCompletedFile(part,m))
            assertArrayEquals(source.readBytes(),part.readBytes())
        } finally { invite.secret.fill(0);root.deleteRecursively() }
    }
}
