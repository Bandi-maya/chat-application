package com.example.chat.ui.chats.detail

import com.example.chat.data.local.*
import com.example.chat.data.media.*
import com.example.chat.domain.models.ChatMessage

/** Session-bound port. The host replaces/dismisses consumers when generation changes. */
data class AttachmentConsumerBinding(
    val session: String,
    val resolver: AttachmentSourceResolver,
    val currentSession: () -> String?,
    /** Short synchronous state/player delivery only. No IO, suspend, callbacks that mutate account/config. */
    val deliverIfCurrent: ((() -> Unit) -> Boolean) = { _ -> false },
    val authorizedUrl: suspend (ChatMessage) -> String,
) {
    fun ref(m: ChatMessage) = AttachmentRef(m.conversationId, m.id, m.attachment?.id.orEmpty())
    fun deliver(source: ResolvedSource, block: () -> Unit): Boolean {
        if (session.isBlank() || currentSession() != session) { releaseConsumerSource(source); return false }
        val delivered = deliverIfCurrent { block() }
        if (!delivered) releaseConsumerSource(source)
        return delivered
    }
    suspend fun resolve(m: ChatMessage, explicit: Boolean): ResolvedSource {
        if (session.isBlank() || currentSession() != session || resolver.currentSession() != session) return ResolvedSource.Unavailable(ResolvedSource.Reason.SIGNED_OUT)
        val result = resolver.resolve(AttachmentSourceRequest(ref(m), explicit, session) {
            check(currentSession() == session) { "Session changed" }
            authorizedUrl(m).also { check(currentSession() == session) { "Session changed" } }
        })
        if (session.isBlank() || currentSession() != session || resolver.currentSession() != session) {
            releaseConsumerSource(result)
            return ResolvedSource.Unavailable(ResolvedSource.Reason.SIGNED_OUT)
        }
        return result
    }
}
internal fun releaseConsumerSource(source: ResolvedSource?) {
    when (source) {
        is ResolvedSource.Leased -> source.lease.release()
        is ResolvedSource.Bytes -> source.bytes.fill(0)
        else -> Unit
    }
}
internal fun ResolvedSource.consumerModel(): Any? = when (this) {
    is ResolvedSource.Remote -> url
    is ResolvedSource.Bytes -> bytes
    is ResolvedSource.Leased -> lease.file
    is ResolvedSource.Unavailable -> null
}
internal fun ResolvedSource.consumerUri(): String? = when (this) {
    is ResolvedSource.Remote -> url
    is ResolvedSource.Leased -> lease.file.toURI().toString()
    else -> null
}
