package com.example.chat.ui.core.system

/**
 * Pure port of backend_service.dart _shouldSendReadReceipts(conversationId):
 *  ghost mode (home.ghostMode or gb 'yo_want_ghostmode') -> never send
 *  privacy.readReceipts == false -> never send
 *  privacy.showBlueTicksAfterReply -> send only once the current user has sent a message in that conversation
 *    (Dart: if the loaded list is non-empty, a user id exists, and none of the messages is from me, do NOT send)
 */
object ReadReceiptPolicy {
    fun shouldSend(
        ghostMode: Boolean, gbGhost: Boolean, readReceipts: Boolean, blueTicksAfterReply: Boolean,
        myId: String?, senderIds: List<String>?,
    ): Boolean {
        if (ghostMode || gbGhost) return false
        if (!readReceipts) return false
        if (blueTicksAfterReply) {
            if (myId != null && senderIds != null && senderIds.isNotEmpty() && senderIds.none { it == myId }) return false
        }
        return true
    }
}
