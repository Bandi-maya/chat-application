package com.example.chat.data.local

import java.io.File

/**
 * Content validation of an editor's output, run INSIDE [AttachmentFileCache.commitEdit] (the authoritative
 * boundary) so no UI caller can skip it. It never authorizes access: ownership, session, policy, size and
 * deadline are checked by the store. Blocking (decodes/parses the file): commit must run off the main thread.
 */
fun interface EditedOutputValidator {
    fun isValid(file: File, kind: EditKind): Boolean

    companion object {
        /** Default for an unwired cache: every commit is refused (fail closed). */
        val DENY = EditedOutputValidator { _, _ -> false }
    }
}
