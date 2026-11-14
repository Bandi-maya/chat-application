package com.example.chat.data.media

/**
 * Pure decision for a ranged (resumable) download. [partLen] = bytes already in the account-private .part file,
 * [savedEtag] = the validator stored beside it, [newEtag] = the validator on the reply.
 *
 * A resume is trusted ONLY when the bytes are proven identical: both validators present, strong (no W/ prefix),
 * and equal, and the server answered exactly from our offset. Anything else (missing validator on either side,
 * weak validator, different validator, wrong offset, unparsable Content-Range) throws the part away and starts at
 * byte 0 ([Action.Restart] when the body is already a full 200, [Action.RestartFresh] when it must be re-requested
 * without a Range header). Last-Modified is deliberately not accepted as a substitute: it only has 1 s resolution.
 */
object ResumePlan {
    sealed class Action {
        /** Keep the part file and append the body. */
        data class Append(val total: Long?) : Action()
        /** This body is the whole object: delete the part file and write it from byte 0. */
        data class Restart(val total: Long?) : Action()
        /** The reply cannot be used (a 206/416 that does not prove same bytes): delete the part, ask again without Range. */
        object RestartFresh : Action()
        /** The part file already holds the whole object (proven identical). */
        object AlreadyComplete : Action()
        data class Fail(val reason: MediaDownloadState.FailReason) : Action()
    }

    private val contentRange = Regex("""bytes (\d+)-(\d+)/(\d+|\*)""")
    private val unsatisfied = Regex("""bytes \*/(\d+)""")

    fun isStrong(etag: String?): Boolean = etag != null && etag.length >= 2 && etag.startsWith("\"") && etag.endsWith("\"")

    /** True only when a Range request may be attempted at all. */
    fun canTryResume(partLen: Long, savedEtag: String?): Boolean = partLen > 0 && isStrong(savedEtag)

    private fun proven(savedEtag: String?, newEtag: String?): Boolean =
        isStrong(savedEtag) && isStrong(newEtag) && savedEtag == newEtag

    fun decide(partLen: Long, code: Int, contentRangeHeader: String?, contentLength: Long?, savedEtag: String?, newEtag: String?): Action {
        return when (code) {
            206 -> {
                val m = contentRangeHeader?.let { contentRange.matchEntire(it.trim()) } ?: return Action.RestartFresh
                val start = m.groupValues[1].toLong()
                val total = m.groupValues[3].toLongOrNull()
                if (partLen > 0 && start == partLen && proven(savedEtag, newEtag)) Action.Append(total) else Action.RestartFresh
            }
            200 -> Action.Restart(contentLength?.takeIf { it >= 0 })     // no range support or validator changed: body is whole
            416 -> {
                val size = contentRangeHeader?.let { unsatisfied.matchEntire(it.trim()) }?.groupValues?.get(1)?.toLong()
                if (size != null && size == partLen && partLen > 0 && proven(savedEtag, newEtag)) Action.AlreadyComplete else Action.RestartFresh
            }
            404, 410 -> Action.Fail(MediaDownloadState.FailReason.SERVER_GONE)
            401, 403 -> Action.Fail(MediaDownloadState.FailReason.NOT_ALLOWED)
            else -> Action.Fail(MediaDownloadState.FailReason.NETWORK)
        }
    }
}
