package com.example.chat.island

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Handles executing inline replies via RemoteInput and PendingIntent (fixing K-2, K-15).
 */
object IslandReplyManager {
    fun sendReply(
        context: Context,
        replyAction: IslandReplyActionItem,
        replyText: String,
        onComplete: (Boolean) -> Unit,
    ) {
        val bundle = Bundle()
        bundle.putCharSequence(replyAction.remoteInput.resultKey, replyText)

        val intent = Intent()
        RemoteInput.addResultsToIntent(arrayOf(replyAction.remoteInput), intent, bundle)

        try {
            replyAction.pendingIntent.send(context, 0, intent)
            onComplete(true)
        } catch (e: Exception) {
            onComplete(false)
        }
    }
}
