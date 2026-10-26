package com.example.chat.ui.chats.location

/** Stops a stale picker from sending into a different signed-in account or conversation. */
data class LocationSendScope(val accountId: String, val conversationId: String) {
    init { require(accountId.isNotBlank() && conversationId.isNotBlank()) }
    fun requireCurrent(account: String?, conversation: String?) {
        check(account == accountId && conversation == conversationId) { "Account or conversation changed. Reopen location." }
    }
}

/** No network permission or map code here. Caller binds send to the normal encrypted transport. */
class ConfirmedLocationSender(
    private val scope: LocationSendScope,
    private val currentScope: () -> Pair<String?, String?>,
    private val encryptedSend: suspend (LocationDraft) -> Unit,
) {
    private var submitted = false
    suspend fun submit(gate: LocationSendGate) {
        check(!submitted) { "Location was already submitted. Check the chat before retrying." }
        val (account, conversation) = currentScope()
        scope.requireCurrent(account, conversation)
        val confirmed = gate.consumeConfirmed() ?: error("Review and confirm a location first.")
        submitted = true // An uncertain failure must not silently duplicate a send.
        encryptedSend(confirmed)
    }
}
